<template>
  <div class="chat-container" :class="{ 'chat-container--multiline': multiline }">
    <div class="chat-messages" ref="messagesContainer">
      <div v-for="(msg, index) in messages" :key="index" class="message-wrapper">
        <div v-if="!msg.isUser" class="message ai-message" :class="[msg.type]">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="message-bubble">
            <div class="message-content ai-content">
              <template v-for="(block, blockIndex) in formatAiMessage(msg.content)" :key="blockIndex">
                <component v-if="block.type === 'heading'" :is="block.tag" class="content-heading">
                  <template v-for="(part, partIndex) in formatInline(block.text)" :key="partIndex">
                    <strong v-if="part.bold">{{ part.text }}</strong>
                    <span v-else>{{ part.text }}</span>
                  </template>
                </component>
                <p v-else-if="block.type === 'bullet'" class="content-line content-list-item">
                  <span class="list-marker">•</span>
                  <span>
                    <template v-for="(part, partIndex) in formatInline(block.text)" :key="partIndex">
                      <strong v-if="part.bold">{{ part.text }}</strong>
                      <span v-else>{{ part.text }}</span>
                    </template>
                  </span>
                </p>
                <p v-else-if="block.type === 'numbered'" class="content-line content-list-item">
                  <span class="list-marker numbered">{{ block.marker }}</span>
                  <span>
                    <template v-for="(part, partIndex) in formatInline(block.text)" :key="partIndex">
                      <strong v-if="part.bold">{{ part.text }}</strong>
                      <span v-else>{{ part.text }}</span>
                    </template>
                  </span>
                </p>
                <hr v-else-if="block.type === 'divider'" class="content-divider" />
                <div v-else-if="block.type === 'space'" class="content-space" aria-hidden="true"></div>
                <p v-else class="content-line">
                  <template v-for="(part, partIndex) in formatInline(block.text)" :key="partIndex">
                    <strong v-if="part.bold">{{ part.text }}</strong>
                    <span v-else>{{ part.text }}</span>
                  </template>
                </p>
              </template>
              <span v-if="connectionStatus === 'connecting' && index === messages.length - 1" class="typing-indicator">▋</span>
            </div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
        </div>

        <div v-else class="message user-message" :class="[msg.type]">
          <div class="message-bubble">
            <div class="message-content">{{ msg.content }}</div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
          <div class="avatar user-avatar">
            <div class="avatar-placeholder">我</div>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-input-container">
      <div class="chat-input">
        <textarea
          v-model="inputMessage"
          @keydown="handleKeydown"
          :placeholder="placeholder"
          aria-label="聊天消息"
          class="input-box"
          :class="{ 'input-box--multiline': multiline }"
          :rows="multiline ? 4 : 1"
          :disabled="connectionStatus === 'connecting' || inputDisabled"
        ></textarea>
        <button
          @click="sendMessage"
          class="send-button"
          type="button"
          :disabled="connectionStatus === 'connecting' || inputDisabled || !inputMessage.trim()"
        >
          <span>发送</span>
          <span aria-hidden="true">↑</span>
        </button>
      </div>
      <p v-if="inputHint" class="input-hint">{{ inputHint }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import AiAvatarFallback from './AiAvatarFallback.vue'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  },
  connectionStatus: {
    type: String,
    default: 'disconnected'
  },
  aiType: {
    type: String,
    default: 'default'
  },
  placeholder: {
    type: String,
    default: '输入消息…'
  },
  inputHint: {
    type: String,
    default: ''
  },
  multiline: {
    type: Boolean,
    default: false
  },
  inputDisabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['send-message'])
const inputMessage = ref('')
const messagesContainer = ref(null)

const sendMessage = () => {
  if (!inputMessage.value.trim()) return
  emit('send-message', inputMessage.value)
  inputMessage.value = ''
}

const handleKeydown = (event) => {
  if (event.key !== 'Enter' || event.isComposing) return
  if (props.multiline && event.shiftKey) return
  event.preventDefault()
  sendMessage()
}

const formatAiMessage = (content = '') => {
  return content.split('\n').map((line) => {
    if (!line.trim()) return { type: 'space', text: '' }
    if (/^\s*---+\s*$/.test(line)) return { type: 'divider', text: '' }

    const heading = line.match(/^(#{1,4})\s*(.+)$/)
    if (heading) {
      const level = Math.min(heading[1].length + 1, 4)
      return { type: 'heading', tag: `h${level}`, text: heading[2] }
    }

    const bullet = line.match(/^\s*[-*]\s+(.+)$/)
    if (bullet) return { type: 'bullet', text: bullet[1] }

    const numbered = line.match(/^\s*(\d+[.、])\s*(.+)$/)
    if (numbered) {
      return { type: 'numbered', marker: numbered[1], text: numbered[2] }
    }

    return { type: 'paragraph', text: line }
  })
}

const formatInline = (text = '') => {
  return text
    .split(/(\*\*.+?\*\*)/g)
    .filter(Boolean)
    .map((part) => ({
      bold: part.startsWith('**') && part.endsWith('**'),
      text: part.startsWith('**') && part.endsWith('**') ? part.slice(2, -2) : part
    }))
}

const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

watch(() => props.messages.length, scrollToBottom)
watch(() => props.messages.map(message => message.content).join(''), scrollToBottom)
onMounted(scrollToBottom)
</script>

<style scoped>
.chat-container {
  position: relative;
  display: flex;
  height: calc(100vh - 126px);
  min-height: 560px;
  flex-direction: column;
  overflow: hidden;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  backdrop-filter: none;
}

.chat-messages {
  position: absolute;
  inset: 0 0 96px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  padding: 32px 34px 40px;
  scroll-behavior: smooth;
}

.message-wrapper {
  display: flex;
  width: 100%;
  flex-direction: column;
  margin-bottom: 20px;
}

.message {
  display: flex;
  max-width: min(78%, 780px);
  align-items: flex-start;
  margin-bottom: 6px;
}

.user-message {
  flex-direction: row;
  margin-left: auto;
}

.ai-message {
  margin-right: auto;
}

.avatar {
  display: flex;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  box-shadow: 0 6px 18px rgba(33, 46, 73, 0.12);
}

.user-avatar {
  margin-left: 10px;
}

.ai-avatar {
  margin-right: 10px;
}

.avatar-placeholder {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  font-weight: 650;
  background: linear-gradient(145deg, #3294ff, #0071e3);
}

.message-bubble {
  position: relative;
  min-width: 110px;
  padding: 15px 18px 11px;
  border-radius: 22px;
  word-wrap: break-word;
}

.user-message .message-bubble {
  color: #fff;
  text-align: left;
  background: linear-gradient(145deg, #238cff, #0071e3);
  border-bottom-right-radius: 7px;
  box-shadow: 0 10px 24px rgba(0, 113, 227, 0.22);
}

.ai-message .message-bubble {
  color: #25262a;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.message-content {
  font-size: 16px;
  line-height: 1.65;
  white-space: pre-wrap;
}

.ai-content {
  min-width: min(640px, 62vw);
}

.content-heading {
  margin: 18px 0 8px;
  color: #1d1d1f;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: -0.015em;
}

.content-heading:first-child {
  margin-top: 0;
}

h3.content-heading {
  font-size: 16px;
}

h4.content-heading {
  font-size: 15px;
}

.content-line {
  margin: 0;
}

.content-list-item {
  display: grid;
  grid-template-columns: 22px 1fr;
  align-items: start;
  margin: 4px 0;
}

.list-marker {
  color: #1674d1;
  font-weight: 700;
}

.list-marker.numbered {
  font-size: 14px;
}

.content-space {
  height: 10px;
}

.content-divider {
  height: 1px;
  margin: 18px 0;
  background: rgba(29, 29, 31, 0.09);
  border: 0;
}

.message-time {
  margin-top: 5px;
  font-size: 11px;
  opacity: 0.55;
  text-align: right;
}

.chat-input-container {
  position: absolute;
  z-index: 10;
  right: 0;
  bottom: 0;
  left: 0;
  height: 96px;
  padding: 16px 20px 20px;
  background: linear-gradient(to top, rgba(255, 255, 255, 0.98) 68%, rgba(255, 255, 255, 0.72));
  border-top: 1px solid rgba(29, 29, 31, 0.06);
  backdrop-filter: blur(20px);
}

.chat-container--multiline .chat-messages {
  bottom: 164px;
}

.chat-container--multiline .chat-input-container {
  height: 164px;
  padding-top: 12px;
}

.chat-container--multiline .chat-input {
  height: 112px;
  align-items: flex-end;
  padding: 10px 9px 10px 18px;
  border-radius: 24px;
}

.chat-input {
  display: flex;
  height: 100%;
  align-items: center;
  gap: 12px;
  padding: 6px 7px 6px 20px;
  background: #fff;
  border: 1px solid rgba(29, 29, 31, 0.12);
  border-radius: 999px;
  box-shadow: 0 8px 24px rgba(44, 55, 74, 0.08);
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

.chat-input:focus-within {
  border-color: rgba(0, 113, 227, 0.48);
  box-shadow: 0 8px 26px rgba(0, 113, 227, 0.12);
}

.input-box {
  min-width: 0;
  min-height: 24px;
  max-height: 44px;
  flex: 1;
  padding: 8px 0;
  color: #1d1d1f;
  font-family: inherit;
  font-size: 16px;
  line-height: 1.4;
  background: transparent;
  border: 0;
  outline: none;
  resize: none;
  scrollbar-width: none;
}

.input-box--multiline {
  min-height: 84px;
  max-height: 92px;
  line-height: 1.55;
}

.input-hint {
  margin: 5px 8px 0;
  color: #86868b;
  font-size: 12px;
  line-height: 1.35;
}

.input-box::placeholder {
  color: #9a9da4;
}

.input-box::-webkit-scrollbar {
  display: none;
}

.send-button {
  display: inline-flex;
  height: 46px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0 19px;
  color: #fff;
  font-family: inherit;
  font-size: 15px;
  font-weight: 600;
  background: #0071e3;
  border: 0;
  border-radius: 999px;
  box-shadow: 0 7px 16px rgba(0, 113, 227, 0.22);
  cursor: pointer;
  transition: background 180ms ease, transform 180ms ease, opacity 180ms ease;
}

.send-button:hover:not(:disabled) {
  background: #0077ed;
  transform: translateY(-1px);
}

.send-button:focus-visible {
  outline: 4px solid rgba(0, 113, 227, 0.2);
  outline-offset: 2px;
}

.input-box:disabled,
.send-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.typing-indicator {
  display: inline-block;
  margin-left: 2px;
  animation: blink 0.75s infinite;
}

.ai-answer {
  animation: fade-in 240ms ease-out;
}

.ai-error {
  opacity: 0.7;
}

.ai-message + .ai-message {
  margin-top: 3px;
}

.ai-message + .ai-message .avatar {
  visibility: hidden;
}

@keyframes blink {
  0%, 100% { opacity: 0; }
  50% { opacity: 1; }
}

@keyframes fade-in {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 768px) {
  .chat-container {
    height: calc(100vh - 92px);
    min-height: 500px;
    border-radius: 24px;
  }

  .chat-messages {
    bottom: 84px;
    padding: 22px 18px 30px;
  }

  .message {
    max-width: 92%;
  }

  .chat-input-container {
    height: 84px;
    padding: 12px 12px 16px;
  }

  .chat-container--multiline .chat-messages {
    bottom: 150px;
  }

  .chat-container--multiline .chat-input-container {
    height: 150px;
  }

  .chat-container--multiline .chat-input {
    height: 100px;
  }

  .input-box--multiline {
    min-height: 72px;
  }

  .message-content {
    font-size: 15px;
  }

  .ai-content {
    min-width: 0;
  }
}

@media (max-width: 480px) {
  .chat-container {
    height: calc(100vh - 76px);
    min-height: 460px;
    border-radius: 20px;
  }

  .chat-messages {
    padding: 18px 12px 24px;
  }

  .avatar {
    width: 34px;
    height: 34px;
  }

  .message-bubble {
    padding: 12px 14px 9px;
    border-radius: 18px;
  }

  .send-button {
    width: 44px;
    padding: 0;
  }

  .send-button span:first-child {
    display: none;
  }
}
</style>
