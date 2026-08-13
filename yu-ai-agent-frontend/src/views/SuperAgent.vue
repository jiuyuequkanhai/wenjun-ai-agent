<template>
  <div class="super-agent-container">
    <div class="header">
      <div class="back-button" @click="goBack">返回</div>
      <h1 class="title">AI超级智能体</h1>
      <div class="save-status">对话自动保存</div>
    </div>
    
    <div class="content-wrapper">
      <div class="conversation-workspace">
        <ConversationHistory
          :conversations="conversations"
          :active-id="chatId"
          :loading="historyLoading"
          @new-chat="startNewChat"
          @select-chat="loadConversation"
          @delete-chat="removeConversation"
        />
        <div class="chat-area">
          <ChatRoom
            :messages="messages"
            :connection-status="connectionStatus"
            input-hint="对话自动保存在本地，可从左侧历史记录继续"
            ai-type="super"
            @send-message="sendMessage"
          />
        </div>
      </div>
    </div>
    
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import ConversationHistory from '../components/ConversationHistory.vue'
import {
  chatWithManus,
  deleteConversation,
  getConversation,
  getConversationList
} from '../api'

// 设置页面标题和元数据
useHead({
  title: 'AI 超级智能体 - 文俊的超级助手',
  meta: [
    {
      name: 'description',
      content: 'AI 超级智能体是文俊的超级助手中的全能 AI 助手，能够处理专业问题并执行复杂任务'
    },
    {
      name: 'keywords',
      content: 'AI超级智能体,智能助手,专业问答,AI问答,专业建议,鱼皮,AI智能体'
    }
  ]
})

const router = useRouter()
const messages = ref([])
const conversations = ref([])
const historyLoading = ref(false)
const chatId = ref('')
const connectionStatus = ref('disconnected')
let eventSource = null

const generateChatId = () => `manus_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`
const welcomeMessage = '你好，我是文俊的超级助手。我可以解答各类问题、执行复杂任务并提供专业建议，请问有什么可以帮助你的吗？'

// 添加消息到列表
const addMessage = (content, isUser, type = '') => {
  messages.value.push({
    content,
    isUser,
    type,
    time: new Date().getTime()
  })
}

const startNewChat = () => {
  eventSource?.close()
  eventSource = null
  connectionStatus.value = 'disconnected'
  chatId.value = generateChatId()
  messages.value = [{ content: welcomeMessage, isUser: false, type: 'ai-welcome', time: Date.now() }]
}

const refreshConversations = async () => {
  conversations.value = await getConversationList('super-agent')
}

const loadConversation = async (conversationId) => {
  if (!conversationId || conversationId === chatId.value && messages.value.some(message => message.type !== 'ai-welcome')) return
  eventSource?.close()
  eventSource = null
  connectionStatus.value = 'disconnected'
  const detail = await getConversation('super-agent', conversationId)
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
  await deleteConversation('super-agent', conversationId)
  await refreshConversations()
  if (conversationId === chatId.value) {
    if (conversations.value.length) await loadConversation(conversations.value[0].id)
    else startNewChat()
  }
}

// 发送消息
const sendMessage = (message) => {
  addMessage(message, true, 'user-question')
  
  // 连接SSE
  if (eventSource) {
    eventSource.close()
  }
  
  // 设置连接状态
  connectionStatus.value = 'connecting'
  
  // 临时存储
  let messageBuffer = []; // 用于存储SSE消息的缓冲区
  let lastBubbleTime = Date.now(); // 上一个气泡的创建时间
  let isFirstResponse = true; // 是否是第一次响应
  
  const chineseEndPunctuation = ['。', '！', '？', '…']; // 中文句子结束标点
  const minBubbleInterval = 800; // 气泡最小间隔时间(毫秒)
  
  // 创建消息气泡的函数
  const createBubble = (content, type = 'ai-answer') => {
    if (!content.trim()) return;
    
    // 添加适当的延迟，使消息显示更自然
    const now = Date.now();
    const timeSinceLastBubble = now - lastBubbleTime;
    
    if (isFirstResponse) {
      // 第一条消息立即显示
      addMessage(content, false, type);
      isFirstResponse = false;
    } else if (timeSinceLastBubble < minBubbleInterval) {
      // 如果与上一气泡间隔太短，添加一个延迟
      setTimeout(() => {
        addMessage(content, false, type);
      }, minBubbleInterval - timeSinceLastBubble);
    } else {
      // 正常添加消息
      addMessage(content, false, type);
    }
    
    lastBubbleTime = now;
    messageBuffer = []; // 清空缓冲区
  };
  
  eventSource = chatWithManus(message, chatId.value)
  
  // 监听SSE消息
  eventSource.onmessage = (event) => {
    const data = event.data
    
    if (data && data !== '[DONE]') {
      messageBuffer.push(data);
      
      // 检查是否应该创建新气泡
      const combinedText = messageBuffer.join('');
      
      // 句子结束或消息长度达到阈值
      const lastChar = data.charAt(data.length - 1);
      const hasCompleteSentence = chineseEndPunctuation.includes(lastChar) || data.includes('\n\n');
      const isLongEnough = combinedText.length > 40;
      
      if (hasCompleteSentence || isLongEnough) {
        createBubble(combinedText);
      }
    }
    
    if (data === '[DONE]') {
      // 如果还有未显示的内容，创建最后一个气泡
      if (messageBuffer.length > 0) {
        const remainingContent = messageBuffer.join('');
        createBubble(remainingContent, 'ai-final');
      }
      
      // 完成后关闭连接
      connectionStatus.value = 'disconnected'
      eventSource.close()
      eventSource = null
      refreshConversations()
    }
  }
  
  // 监听SSE错误
  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    connectionStatus.value = 'error'
    eventSource.close()
    
    // 如果出错时有未显示的内容，也创建气泡
    if (messageBuffer.length > 0) {
      const remainingContent = messageBuffer.join('');
      createBubble(remainingContent, 'ai-error');
    }
  }
}

// 返回主页
const goBack = () => {
  router.push('/')
}

// 页面加载时添加欢迎消息
onMounted(async () => {
  historyLoading.value = true
  await refreshConversations()
  historyLoading.value = false
  if (conversations.value.length) await loadConversation(conversations.value[0].id)
  else startNewChat()
})

// 组件销毁前关闭SSE连接
onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
})
</script>

<style scoped>
.super-agent-container {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at 10% 10%, rgba(220, 235, 255, 0.86), transparent 36%),
    radial-gradient(circle at 90% 18%, rgba(235, 225, 255, 0.64), transparent 34%),
    linear-gradient(145deg, #f9fbff, #f5f7ff 58%, #fdfcff);
}

.header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  min-height: 64px;
  padding: 10px max(24px, calc((100vw - 1180px) / 2));
  color: #1d1d1f;
  background: rgba(255, 255, 255, 0.72);
  border-bottom: 1px solid rgba(29, 29, 31, 0.06);
  backdrop-filter: blur(24px) saturate(160%);
  position: sticky;
  top: 0;
  z-index: 20;
}

.back-button {
  padding: 8px 12px;
  color: #0066cc;
  font-size: 15px;
  font-weight: 520;
  cursor: pointer;
  display: flex;
  align-items: center;
  border-radius: 999px;
  transition: background 0.2s;
  justify-self: start;
}

.back-button:hover {
  background: rgba(0, 113, 227, 0.08);
}

.back-button:before {
  content: '←';
  margin-right: 8px;
}

.title {
  font-size: 18px;
  font-weight: 650;
  letter-spacing: -0.02em;
  margin: 0;
  text-align: center;
  justify-self: center;
}

.save-status {
  justify-self: end;
  color: #86868b;
  font-size: 12px;
}

.content-wrapper {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.conversation-workspace {
  --history-accent: #6657d9;
  display: grid;
  width: 100%;
  max-width: 1240px;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 14px;
  margin: 0 auto;
  padding: 30px;
}

.chat-area {
  min-width: 0;
  overflow: hidden;
  position: relative;
  min-height: calc(100vh - 64px);
}

/* 响应式样式 */
@media (max-width: 768px) {
  .header {
    min-height: 56px;
    padding: 8px 14px;
  }
  
  .title {
    font-size: 18px;
  }
  
  .conversation-workspace {
    grid-template-columns: 1fr;
    padding: 18px;
  }

  .chat-area {
    min-height: calc(100vh - 230px);
  }
}

@media (max-width: 480px) {
  .header {
    min-height: 52px;
    padding: 6px 8px;
  }
  
  .back-button {
    font-size: 14px;
  }
  
  .title {
    font-size: 16px;
  }
  
  .conversation-workspace {
    padding: 12px;
  }

  .chat-area {
    min-height: calc(100vh - 52px);
  }
}
</style>
