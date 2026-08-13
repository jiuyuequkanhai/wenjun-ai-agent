<template>
  <div class="caishu-container">
    <header class="header">
      <button class="back-button" type="button" @click="router.push('/')">返回</button>
      <div class="header-title">
        <span class="mini-avatar" aria-hidden="true">🧠</span>
        <h1>数字菜叔</h1>
      </div>
      <button class="sync-button" type="button" :disabled="isIndexing" @click="syncKnowledge">
        {{ isIndexing ? '同步中' : '同步文章' }}
      </button>
    </header>

    <main class="content-wrapper">
      <section class="knowledge-status" :class="`status-${knowledgeStatus.state}`" aria-live="polite">
        <div class="status-dot"></div>
        <div class="status-copy">
          <strong>{{ statusTitle }}</strong>
          <span>{{ statusDescription }}</span>
        </div>
        <div v-if="isIndexing && knowledgeStatus.totalFiles" class="progress-track" aria-hidden="true">
          <span :style="{ width: `${progress}%` }"></span>
        </div>
      </section>

      <div class="conversation-workspace">
        <ConversationHistory
          :conversations="conversations"
          :active-id="chatId"
          :loading="historyLoading"
          @new-chat="startNewChat"
          @select-chat="loadConversation"
          @delete-chat="removeConversation"
        />
        <section class="chat-area">
          <ChatRoom
            :messages="messages"
            :connection-status="connectionStatus"
            :input-disabled="knowledgeStatus.state !== 'ready'"
            :placeholder="chatPlaceholder"
            input-hint="对话自动保存到本地，回答会标注文章来源"
            ai-type="caishu"
            @send-message="sendMessage"
          />
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import ConversationHistory from '../components/ConversationHistory.vue'
import {
  chatWithDigitalCaishu,
  deleteConversation,
  getCaishuKnowledgeStatus,
  getConversation,
  getConversationList,
  reindexCaishuKnowledge
} from '../api'

useHead({
  title: '数字菜叔 - 文俊的超级助手',
  meta: [{ name: 'description', content: '基于菜叔微信公众号文章构建的本地 RAG 知识助手' }]
})

const router = useRouter()
const messages = ref([])
const conversations = ref([])
const historyLoading = ref(false)
const connectionStatus = ref('disconnected')
const knowledgeStatus = ref({ state: 'idle', articleCount: 0, chunkCount: 0, message: '正在连接知识库' })
const chatId = ref('')
let streamConnection = null
let statusTimer = null

const generateChatId = () => `caishu_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`

const isIndexing = computed(() => ['idle', 'indexing'].includes(knowledgeStatus.value.state))
const progress = computed(() => {
  if (!knowledgeStatus.value.totalFiles) return 0
  return Math.min(100, Math.round(knowledgeStatus.value.processedFiles / knowledgeStatus.value.totalFiles * 100))
})
const statusTitle = computed(() => {
  if (knowledgeStatus.value.state === 'ready') return '本地知识库已就绪'
  if (knowledgeStatus.value.state === 'error') return '知识库同步失败'
  return `正在建立本地知识库 ${progress.value}%`
})
const statusDescription = computed(() => {
  if (knowledgeStatus.value.state === 'ready') {
    return `${knowledgeStatus.value.articleCount} 篇文章 · ${knowledgeStatus.value.chunkCount} 个知识片段 · ${knowledgeStatus.value.message}`
  }
  if (knowledgeStatus.value.state === 'error') return knowledgeStatus.value.message
  const current = knowledgeStatus.value.processedFiles || 0
  const total = knowledgeStatus.value.totalFiles || '…'
  return `${current} / ${total} 篇 · ${knowledgeStatus.value.message || '正在准备'}`
})
const chatPlaceholder = computed(() => knowledgeStatus.value.state === 'ready'
  ? '问问菜叔关于赚钱、认知、成长或选择的问题…'
  : '知识库首次同步完成后即可提问')

const addMessage = (content, isUser, type = '') => {
  messages.value.push({ content, isUser, type, time: Date.now() })
}

const welcomeMessage = () => knowledgeStatus.value.state === 'ready'
  ? `你好，我是数字菜叔。我已经读取本地 ${knowledgeStatus.value.articleCount} 篇菜叔公众号文章。你可以直接提问，我会检索相关文章后回答，并标注观点来源。`
  : '正在连接本地菜叔文章知识库，首次建立索引需要一些时间。完成后就可以开始提问。'

const startNewChat = () => {
  streamConnection?.close()
  streamConnection = null
  connectionStatus.value = 'disconnected'
  chatId.value = generateChatId()
  messages.value = [{ content: welcomeMessage(), isUser: false, type: 'ai-welcome', time: Date.now() }]
}

const refreshConversations = async () => {
  conversations.value = await getConversationList('digital-caishu')
}

const loadConversation = async (conversationId) => {
  if (!conversationId || conversationId === chatId.value && messages.value.some(message => message.type !== 'ai-welcome')) return
  streamConnection?.close()
  streamConnection = null
  connectionStatus.value = 'disconnected'
  const detail = await getConversation('digital-caishu', conversationId)
  chatId.value = conversationId
  messages.value = detail.messages.map(message => ({
    content: message.content,
    isUser: message.role === 'user',
    type: message.role === 'user' ? 'user-question' : 'ai-answer',
    time: message.createdAt
  }))
}

const removeConversation = async (conversationId) => {
  const target = conversations.value.find(item => item.id === conversationId)
  if (!window.confirm(`确定删除对话「${target?.title || '未命名对话'}」吗？删除后无法恢复。`)) return
  await deleteConversation('digital-caishu', conversationId)
  await refreshConversations()
  if (conversationId === chatId.value) {
    if (conversations.value.length) await loadConversation(conversations.value[0].id)
    else startNewChat()
  }
}

const loadStatus = async () => {
  try {
    const previousState = knowledgeStatus.value.state
    knowledgeStatus.value = await getCaishuKnowledgeStatus()
    if (previousState !== 'ready' && knowledgeStatus.value.state === 'ready'
      && messages.value.length === 1 && messages.value[0].type === 'ai-welcome') {
      messages.value[0].content = welcomeMessage()
    }
  } catch (error) {
    knowledgeStatus.value = { state: 'error', message: error.message, articleCount: 0, chunkCount: 0 }
  }
}

const syncKnowledge = async () => {
  try {
    knowledgeStatus.value = await reindexCaishuKnowledge()
    if (knowledgeStatus.value.state === 'ready') {
      knowledgeStatus.value = { ...knowledgeStatus.value, state: 'indexing', message: '正在检查文章变化' }
    }
  } catch (error) {
    knowledgeStatus.value = { ...knowledgeStatus.value, state: 'error', message: error.message }
  }
}

const sendMessage = (message) => {
  if (knowledgeStatus.value.state !== 'ready') return
  addMessage(message, true, 'user-question')
  if (streamConnection) streamConnection.close()
  connectionStatus.value = 'connecting'

  const answer = { content: '', isUser: false, type: 'ai-answer', time: Date.now() }
  messages.value.push(answer)
  streamConnection = chatWithDigitalCaishu(message, chatId.value, {
    onMessage: (chunk) => {
      answer.content += chunk
    },
    onDone: async () => {
      connectionStatus.value = 'disconnected'
      streamConnection = null
      await refreshConversations()
    },
    onError: (error) => {
      connectionStatus.value = 'error'
      answer.content = answer.content || `暂时无法回答：${error.message}`
      streamConnection = null
    }
  })
}

onMounted(async () => {
  historyLoading.value = true
  await Promise.all([loadStatus(), refreshConversations()])
  historyLoading.value = false
  if (conversations.value.length) await loadConversation(conversations.value[0].id)
  else startNewChat()
  statusTimer = window.setInterval(loadStatus, 2000)
})

onBeforeUnmount(() => {
  if (streamConnection) streamConnection.close()
  if (statusTimer) window.clearInterval(statusTimer)
})
</script>

<style scoped>
.caishu-container {
  min-height: 100vh;
  color: #1d1d1f;
  background:
    radial-gradient(circle at 12% 8%, rgba(218, 245, 226, 0.88), transparent 34%),
    radial-gradient(circle at 90% 18%, rgba(224, 235, 255, 0.72), transparent 34%),
    linear-gradient(145deg, #fbfefc, #f5f8ff 60%, #fffdf9);
}

.header {
  position: sticky;
  z-index: 20;
  top: 0;
  display: grid;
  min-height: 64px;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 10px max(24px, calc((100vw - 1180px) / 2));
  background: rgba(255, 255, 255, 0.76);
  border-bottom: 1px solid rgba(29, 29, 31, 0.06);
  backdrop-filter: blur(24px) saturate(160%);
}

.back-button,
.sync-button {
  width: fit-content;
  padding: 8px 13px;
  color: #087947;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  background: rgba(23, 154, 91, 0.08);
  border: 0;
  border-radius: 999px;
  cursor: pointer;
}

.back-button::before {
  margin-right: 7px;
  content: '←';
}

.sync-button {
  justify-self: end;
}

.sync-button:disabled {
  cursor: default;
  opacity: 0.55;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 9px;
}

.mini-avatar {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  background: linear-gradient(145deg, #f4fff7, #d8f3e1);
  border-radius: 50%;
}

.header-title h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 680;
  letter-spacing: -0.02em;
}

.content-wrapper {
  width: min(1180px, calc(100% - 40px));
  margin: 18px auto 0;
}

.knowledge-status {
  position: relative;
  display: flex;
  min-height: 54px;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  padding: 10px 18px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(29, 29, 31, 0.07);
  border-radius: 18px;
  box-shadow: 0 10px 30px rgba(43, 72, 59, 0.07);
  backdrop-filter: blur(20px);
}

.status-dot {
  width: 9px;
  height: 9px;
  flex: 0 0 auto;
  background: #f0a124;
  border-radius: 50%;
  box-shadow: 0 0 0 5px rgba(240, 161, 36, 0.12);
}

.status-ready .status-dot {
  background: #19a563;
  box-shadow: 0 0 0 5px rgba(25, 165, 99, 0.12);
}

.status-error .status-dot {
  background: #df4b4b;
  box-shadow: 0 0 0 5px rgba(223, 75, 75, 0.12);
}

.status-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.status-copy strong {
  font-size: 14px;
}

.status-copy span {
  overflow: hidden;
  color: #73777f;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-track {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 3px;
  background: rgba(25, 165, 99, 0.08);
}

.progress-track span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #36b978, #0a8f55);
  transition: width 280ms ease;
}

.chat-area :deep(.chat-container) {
  height: calc(100vh - 158px);
}

.conversation-workspace {
  --history-accent: #15945b;
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 14px;
}

.chat-area {
  min-width: 0;
}

@media (max-width: 640px) {
  .header {
    grid-template-columns: auto 1fr auto;
    padding: 9px 12px;
  }

  .back-button,
  .sync-button {
    padding: 7px 10px;
    font-size: 12px;
  }

  .header-title {
    justify-self: center;
  }

  .content-wrapper {
    width: calc(100% - 20px);
    margin-top: 10px;
  }

  .conversation-workspace {
    grid-template-columns: 1fr;
  }

  .chat-area :deep(.chat-container) {
    height: calc(100vh - 260px);
  }
}
</style>
