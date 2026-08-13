<template>
  <div class="industry-research-container">
    <header class="header">
      <button class="back-button" type="button" @click="goBack">返回</button>
      <div class="title-group">
        <span class="eyebrow">FDE WORKSPACE</span>
        <h1 class="title">行业调研助手</h1>
      </div>
      <div class="chat-id">对话自动保存</div>
    </header>

    <main class="content-wrapper">
      <div class="conversation-workspace">
        <ConversationHistory
          :conversations="conversations"
          :active-id="chatId"
          :loading="historyLoading"
          @new-chat="startNewChat"
          @select-chat="loadConversation"
          @delete-chat="removeConversation"
        />

        <div class="research-workspace">
          <section class="workflow-card" aria-label="行业调研工作流程">
        <div
          v-for="(step, index) in workflowSteps"
          :key="step.title"
          class="workflow-step"
          :class="{
            active: workflowStage === index,
            completed: workflowStage > index
          }"
        >
          <span class="step-index">{{ workflowStage > index ? '✓' : index + 1 }}</span>
          <span>
            <strong>{{ step.title }}</strong>
            <small>{{ step.description }}</small>
          </span>
        </div>
          </section>

          <section class="document-uploader" aria-label="上传行业资料">
        <input
          ref="fileInput"
          class="file-input"
          type="file"
          hidden
          accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          multiple
          @change="handleFileSelection"
        />

        <div class="upload-main">
          <div class="upload-icon" aria-hidden="true">↑</div>
          <div class="upload-copy">
            <strong>{{ uploadedDocuments.length ? '已读取的资料' : '上传 PDF 或 Word' }}</strong>
            <span>支持 PDF、DOC、DOCX，单个不超过 20MB，最多 5 个</span>
          </div>
          <button
            class="upload-button"
            type="button"
            :disabled="uploading || workflowStage > 0"
            @click="chooseFiles"
          >
            {{ uploading ? '正在读取…' : uploadedDocuments.length ? '继续添加' : '选择文件' }}
          </button>
        </div>

        <div v-if="uploadedDocuments.length" class="document-list">
          <article v-for="document in uploadedDocuments" :key="document.id" class="document-chip">
            <span class="file-badge">{{ document.fileType.toUpperCase() }}</span>
            <span class="document-info">
              <strong :title="document.fileName">{{ document.fileName }}</strong>
              <small>
                {{ formatDocumentMeta(document) }}
                <span v-if="document.truncated" class="truncated-warning">文字过长，仅载入前 30 万字</span>
              </small>
            </span>
            <button
              class="remove-file"
              type="button"
              :disabled="workflowStage > 0"
              :aria-label="`移除 ${document.fileName}`"
              @click="removeDocument(document)"
            >×</button>
          </article>
          <button
            v-if="workflowStage === 0"
            class="analyze-files-button"
            type="button"
            :disabled="uploading || connectionStatus === 'connecting'"
            @click="startDocumentAnalysis"
          >
            开始分析已上传资料
            <span aria-hidden="true">→</span>
          </button>
        </div>

        <p v-if="uploadError" class="upload-error">{{ uploadError }}</p>
          </section>

          <div class="chat-area">
            <ChatRoom
              :messages="messages"
              :connection-status="connectionStatus"
              :placeholder="inputPlaceholder"
              :input-hint="inputHint"
              ai-type="research"
              multiline
              @send-message="sendMessage"
            />
          </div>
        </div>
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
  chatWithIndustryResearch,
  deleteConversation,
  deleteIndustryDocument,
  getConversation,
  getConversationList,
  uploadIndustryDocuments
} from '../api'

useHead({
  title: '行业调研助手 - 文俊的超级助手',
  meta: [
    {
      name: 'description',
      content: '面向 FDE 岗位的行业调研助手，通过理解规划和正式写作两个阶段，把专业资料转化为外行也能读懂的行业学习材料'
    },
    {
      name: 'keywords',
      content: '行业调研,FDE,行业研究,认知Gap,专业资料,AI助手,AI智能体'
    }
  ]
})

const workflowSteps = [
  { title: '提交资料', description: '粘贴或上传需要理解的内容' },
  { title: '分析与规划', description: '先梳理结构和认知 Gap' },
  { title: '确认后写作', description: '生成可与专家对话的学习材料' }
]

const router = useRouter()
const messages = ref([])
const conversations = ref([])
const historyLoading = ref(false)
const chatId = ref('')
const connectionStatus = ref('disconnected')
const workflowStage = ref(0)
const fileInput = ref(null)
const uploading = ref(false)
const uploadError = ref('')
const uploadedDocuments = ref([])
let streamConnection = null

const inputPlaceholder = computed(() => {
  if (workflowStage.value === 0) {
    return '粘贴文字资料，或先在上方上传 PDF、Word 文件…'
  }
  if (workflowStage.value === 1) {
    return '回复「确认」进入正式写作，或告诉我需要调整的规划…'
  }
  return '继续提出补充要求或修改意见…'
})

const inputHint = computed(() => {
  if (workflowStage.value === 0) {
    return '支持长文本粘贴和文件上传。Enter 发送，Shift + Enter 换行。助手会先输出第一阶段分析。'
  }
  if (workflowStage.value === 1) {
    return '请先检查主题、知识结构、案例、认知 Gap 和预期产出，确认后再进入第二阶段。'
  }
  return '当前会话会保留原始资料与规划，可以继续要求补充、精简或调整结构。'
})

const generateChatId = () => `research_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`

const addMessage = (content, isUser, type = '') => {
  messages.value.push({
    content,
    isUser,
    type,
    time: Date.now()
  })
}

const welcomeMessage = '欢迎使用行业调研助手。\n\n你可以直接粘贴文字，也可以上传 PDF 或 Word 文件。我会先读取资料，完成「主题识别、知识结构、关键例子、认知 Gap、预期产出」五项分析，等你确认后，再生成正式的行业学习材料。'

const startNewChat = () => {
  streamConnection?.close()
  streamConnection = null
  connectionStatus.value = 'disconnected'
  chatId.value = generateChatId()
  messages.value = [{ content: welcomeMessage, isUser: false, type: 'ai-welcome', time: Date.now() }]
  workflowStage.value = 0
  uploadedDocuments.value = []
  uploadError.value = ''
}

const refreshConversations = async () => {
  conversations.value = await getConversationList('industry-research')
}

const loadConversation = async (conversationId) => {
  if (!conversationId || conversationId === chatId.value && messages.value.some(message => message.type !== 'ai-welcome')) return
  streamConnection?.close()
  streamConnection = null
  connectionStatus.value = 'disconnected'
  const detail = await getConversation('industry-research', conversationId)
  chatId.value = conversationId
  messages.value = detail.messages.map(message => ({
    content: message.role === 'user' ? createDisplayMessage(message.content) : message.content,
    isUser: message.role === 'user',
    type: message.role === 'user' ? 'user-question' : 'ai-answer',
    time: message.createdAt
  }))
  const userMessages = detail.messages.filter(message => message.role === 'user')
  workflowStage.value = userMessages.some(message => isConfirmation(message.content)) ? 2 : 1
  uploadedDocuments.value = []
  uploadError.value = ''
}

const removeConversation = async (conversationId) => {
  const target = conversations.value.find(item => item.id === conversationId)
  if (!window.confirm(`确定删除对话「${target?.title || '未命名对话'}」吗？删除后无法恢复。`)) return
  await deleteConversation('industry-research', conversationId)
  await refreshConversations()
  if (conversationId === chatId.value) {
    if (conversations.value.length) await loadConversation(conversations.value[0].id)
    else startNewChat()
  }
}

const createDisplayMessage = (message) => {
  if (message.length <= 1400) return message
  return `${message.slice(0, 1000)}\n\n……\n\n（已提交 ${message.length.toLocaleString('zh-CN')} 字原始资料，界面仅展示部分内容）`
}

const isConfirmation = (message) => {
  return /^(确认|可以开始|开始写作|进入第二阶段|开始第二阶段)[。！!\s]*$/.test(message.trim())
}

const chooseFiles = () => {
  if (workflowStage.value > 0 || uploading.value) return
  fileInput.value?.click()
}

const handleFileSelection = async (event) => {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return

  uploadError.value = ''
  const remainingSlots = 5 - uploadedDocuments.value.length
  if (remainingSlots <= 0 || files.length > remainingSlots) {
    uploadError.value = `最多上传 5 个文件，你还可以添加 ${Math.max(remainingSlots, 0)} 个。`
    return
  }
  const invalidFile = files.find((file) => !/\.(pdf|doc|docx)$/i.test(file.name))
  if (invalidFile) {
    uploadError.value = `不支持 ${invalidFile.name}，请选择 PDF、DOC 或 DOCX 文件。`
    return
  }
  const oversizedFile = files.find((file) => file.size > 20 * 1024 * 1024)
  if (oversizedFile) {
    uploadError.value = `${oversizedFile.name} 超过 20MB，请压缩或拆分后重试。`
    return
  }

  uploading.value = true
  try {
    const uploaded = await uploadIndustryDocuments(files, chatId.value)
    uploadedDocuments.value.push(...uploaded)
  } catch (error) {
    uploadError.value = error.message || '文件读取失败，请稍后重试。'
  } finally {
    uploading.value = false
  }
}

const removeDocument = async (document) => {
  if (workflowStage.value > 0) return
  uploadError.value = ''
  try {
    await deleteIndustryDocument(document.id, chatId.value)
    uploadedDocuments.value = uploadedDocuments.value.filter((item) => item.id !== document.id)
  } catch (error) {
    uploadError.value = error.message || '移除文件失败，请稍后重试。'
  }
}

const formatFileSize = (bytes) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const formatDocumentMeta = (document) => {
  const parts = [formatFileSize(document.fileSize), `${document.characterCount.toLocaleString('zh-CN')} 字`]
  if (document.pageCount) parts.push(`${document.pageCount} 页`)
  return parts.join(' · ')
}

const startDocumentAnalysis = () => {
  if (!uploadedDocuments.value.length) return
  const fileNames = uploadedDocuments.value.map((document) => `「${document.fileName}」`).join('、')
  sendMessage(`请阅读并分析我上传的原始资料：${fileNames}`)
}

const sendMessage = (message) => {
  const confirmingPlan = workflowStage.value === 1 && isConfirmation(message)
  const documentIds = workflowStage.value === 0
    ? uploadedDocuments.value.map((document) => document.id)
    : []
  addMessage(createDisplayMessage(message), true, 'user-question')

  streamConnection?.close()

  const aiMessageIndex = messages.value.length
  addMessage('', false, 'ai-answer')
  connectionStatus.value = 'connecting'

  if (workflowStage.value === 0) {
    workflowStage.value = 1
  } else if (confirmingPlan) {
    workflowStage.value = 2
  }

  streamConnection = chatWithIndustryResearch(message, chatId.value, documentIds, {
    onMessage: (data) => {
      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].content += data
      }
    },
    onDone: async () => {
      connectionStatus.value = 'disconnected'
      streamConnection = null
      await refreshConversations()
    },
    onError: (error) => {
      console.error('Industry research SSE error:', error)
      connectionStatus.value = 'error'
      if (aiMessageIndex < messages.value.length && !messages.value[aiMessageIndex].content) {
        messages.value[aiMessageIndex].content = '处理资料时遇到问题，请稍后重试。你的原始资料仍保留在当前页面。'
        messages.value[aiMessageIndex].type = 'ai-error'
      }
      streamConnection = null
    }
  })
}

const goBack = () => router.push('/')

onMounted(async () => {
  historyLoading.value = true
  await refreshConversations()
  historyLoading.value = false
  if (conversations.value.length) await loadConversation(conversations.value[0].id)
  else startNewChat()
})

onBeforeUnmount(() => streamConnection?.close())
</script>

<style scoped>
.industry-research-container {
  position: relative;
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  overflow: hidden;
  background:
    radial-gradient(circle at 8% 4%, rgba(220, 235, 255, 0.82), transparent 32%),
    radial-gradient(circle at 92% 14%, rgba(231, 241, 255, 0.9), transparent 35%),
    linear-gradient(145deg, #fbfdff, #f4f8ff 56%, #fcfdff);
}

.header {
  position: sticky;
  z-index: 20;
  top: 0;
  display: grid;
  min-height: 72px;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 10px max(24px, calc((100vw - 1180px) / 2));
  color: #1d1d1f;
  background: rgba(255, 255, 255, 0.76);
  border-bottom: 1px solid rgba(29, 29, 31, 0.06);
  backdrop-filter: blur(24px) saturate(160%);
}

.back-button {
  display: inline-flex;
  justify-self: start;
  align-items: center;
  padding: 8px 12px;
  color: #0066cc;
  font-family: inherit;
  font-size: 15px;
  font-weight: 520;
  background: transparent;
  border: 0;
  border-radius: 999px;
  cursor: pointer;
  transition: background 180ms ease;
}

.back-button::before {
  margin-right: 8px;
  content: '←';
}

.back-button:hover {
  background: rgba(0, 113, 227, 0.08);
}

.title-group {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.eyebrow {
  color: #86868b;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.title {
  margin: 2px 0 0;
  font-size: 19px;
  font-weight: 680;
  letter-spacing: -0.02em;
}

.chat-id {
  justify-self: end;
  color: #86868b;
  font-size: 12px;
}

.content-wrapper {
  width: 100%;
  max-width: 1240px;
  margin: 0 auto;
  padding: 20px 30px 28px;
}

.conversation-workspace {
  --history-accent: #0071e3;
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  align-items: stretch;
  gap: 14px;
}

.research-workspace {
  min-width: 0;
}

.workflow-card {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 16px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.68);
  border: 1px solid rgba(255, 255, 255, 0.94);
  border-radius: 22px;
  box-shadow: 0 12px 36px rgba(64, 78, 104, 0.08);
  backdrop-filter: blur(20px);
}

.workflow-step {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 11px;
  padding: 9px 12px;
  color: #8b8e95;
  border-radius: 15px;
  transition: background 180ms ease, color 180ms ease;
}

.workflow-step.active {
  color: #1d1d1f;
  background: #fff;
  box-shadow: 0 7px 22px rgba(45, 62, 91, 0.08);
}

.workflow-step.completed {
  color: #256fce;
}

.step-index {
  display: inline-flex;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  align-items: center;
  justify-content: center;
  color: inherit;
  font-size: 12px;
  font-weight: 700;
  background: rgba(123, 139, 167, 0.1);
  border-radius: 50%;
}

.active .step-index {
  color: #fff;
  background: #0071e3;
  box-shadow: 0 6px 14px rgba(0, 113, 227, 0.22);
}

.workflow-step strong,
.workflow-step small {
  display: block;
}

.workflow-step strong {
  font-size: 13px;
  font-weight: 650;
}

.workflow-step small {
  margin-top: 2px;
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-uploader {
  margin-bottom: 14px;
  padding: 11px 12px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(255, 255, 255, 0.96);
  border-radius: 22px;
  box-shadow: 0 12px 34px rgba(64, 78, 104, 0.07);
  backdrop-filter: blur(20px);
}

.file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
}

.upload-main {
  display: flex;
  align-items: center;
  gap: 11px;
}

.upload-icon {
  display: inline-flex;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  align-items: center;
  justify-content: center;
  color: #0066cc;
  font-size: 17px;
  font-weight: 700;
  background: #edf6ff;
  border-radius: 11px;
}

.upload-copy {
  min-width: 0;
  flex: 1;
}

.upload-copy strong,
.upload-copy span {
  display: block;
}

.upload-copy strong {
  color: #25262a;
  font-size: 13px;
  font-weight: 650;
}

.upload-copy span {
  margin-top: 2px;
  color: #86868b;
  font-size: 11px;
}

.upload-button,
.analyze-files-button {
  font-family: inherit;
  border: 0;
  cursor: pointer;
  transition: transform 180ms ease, opacity 180ms ease, background 180ms ease;
}

.upload-button {
  padding: 8px 13px;
  color: #0066cc;
  font-size: 12px;
  font-weight: 620;
  background: #eef6ff;
  border-radius: 999px;
}

.upload-button:hover:not(:disabled) {
  background: #e3f0ff;
}

.upload-button:disabled,
.analyze-files-button:disabled,
.remove-file:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.document-list {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(29, 29, 31, 0.07);
}

.document-chip {
  display: flex;
  min-width: min(300px, 100%);
  max-width: 420px;
  flex: 1 1 300px;
  align-items: center;
  gap: 9px;
  padding: 8px 9px;
  background: #f7f9fc;
  border: 1px solid rgba(29, 29, 31, 0.06);
  border-radius: 14px;
}

.file-badge {
  display: inline-flex;
  min-width: 39px;
  height: 28px;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
  color: #fff;
  font-size: 9px;
  font-weight: 760;
  background: linear-gradient(145deg, #318cff, #0066cc);
  border-radius: 8px;
}

.document-info {
  min-width: 0;
  flex: 1;
}

.document-info strong,
.document-info small {
  display: block;
}

.document-info strong {
  overflow: hidden;
  color: #34353a;
  font-size: 12px;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-info small {
  margin-top: 2px;
  color: #8a8d94;
  font-size: 10px;
}

.truncated-warning {
  display: block;
  margin-top: 2px;
  color: #b06400;
}

.remove-file {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  color: #777a81;
  font-size: 18px;
  line-height: 1;
  background: transparent;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
}

.remove-file:hover:not(:disabled) {
  color: #d12f2f;
  background: rgba(209, 47, 47, 0.08);
}

.analyze-files-button {
  display: inline-flex;
  min-height: 46px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  align-self: stretch;
  padding: 0 18px;
  color: #fff;
  font-size: 12px;
  font-weight: 650;
  background: #0071e3;
  border-radius: 14px;
  box-shadow: 0 7px 16px rgba(0, 113, 227, 0.18);
}

.analyze-files-button:hover:not(:disabled) {
  background: #0077ed;
  transform: translateY(-1px);
}

.upload-error {
  margin: 8px 3px 0;
  color: #c73535;
  font-size: 11px;
}

.chat-area :deep(.chat-container) {
  height: calc(100vh - 260px);
  min-height: 520px;
}

@media (max-width: 768px) {
  .header {
    min-height: 60px;
    padding: 8px 14px;
  }

  .eyebrow,
  .chat-id {
    display: none;
  }

  .content-wrapper {
    padding: 14px 16px 20px;
  }

  .conversation-workspace {
    grid-template-columns: 1fr;
  }

  .workflow-card {
    gap: 4px;
    margin-bottom: 12px;
    padding: 6px;
  }

  .document-uploader {
    padding: 9px 10px;
  }

  .upload-copy span {
    display: none;
  }

  .document-chip {
    min-width: 100%;
  }

  .analyze-files-button {
    width: 100%;
  }

  .workflow-step {
    justify-content: center;
    padding: 8px 5px;
  }

  .workflow-step small {
    display: none;
  }

  .chat-area :deep(.chat-container) {
    height: calc(100vh - 214px);
    min-height: 500px;
  }
}

@media (max-width: 480px) {
  .header {
    min-height: 54px;
    padding: 6px 8px;
  }

  .title {
    font-size: 16px;
  }

  .workflow-step {
    gap: 5px;
  }

  .step-index {
    width: 23px;
    height: 23px;
    flex-basis: 23px;
  }

  .workflow-step strong {
    font-size: 11px;
  }
}
</style>
