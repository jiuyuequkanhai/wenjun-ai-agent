<template>
  <aside class="history-panel" aria-label="历史对话">
    <div class="history-header">
      <div>
        <span>CONVERSATIONS</span>
        <h2>历史对话</h2>
      </div>
      <button class="new-chat-button" type="button" @click="$emit('new-chat')" aria-label="新建对话">＋</button>
    </div>

    <div v-if="loading" class="history-empty">正在读取…</div>
    <div v-else-if="!conversations.length" class="history-empty">
      <strong>还没有保存的对话</strong>
      <span>发送第一条消息后会自动保存在这里</span>
    </div>
    <div v-else class="history-list">
      <article
        v-for="conversation in conversations"
        :key="conversation.id"
        class="history-item"
        :class="{ active: conversation.id === activeId }"
      >
        <button class="history-select" type="button" @click="$emit('select-chat', conversation.id)">
          <strong>{{ conversation.title }}</strong>
          <span>{{ conversation.lastMessage || '新对话' }}</span>
          <small>{{ formatDate(conversation.updatedAt) }} · {{ conversation.messageCount }} 条消息</small>
        </button>
        <button
          class="delete-chat-button"
          type="button"
          :aria-label="`删除对话：${conversation.title}`"
          title="删除对话"
          @click="$emit('delete-chat', conversation.id)"
        >×</button>
      </article>
    </div>
  </aside>
</template>

<script setup>
defineProps({
  conversations: {
    type: Array,
    default: () => []
  },
  activeId: {
    type: String,
    default: ''
  },
  loading: {
    type: Boolean,
    default: false
  }
})

defineEmits(['new-chat', 'select-chat', 'delete-chat'])

const formatDate = (value) => {
  const date = new Date(value)
  const today = new Date()
  if (date.toDateString() === today.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}
</script>

<style scoped>
.history-panel {
  display: flex;
  width: 250px;
  min-width: 250px;
  flex-direction: column;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(255, 255, 255, 0.94);
  border-radius: 24px;
  box-shadow: 0 16px 46px rgba(52, 66, 91, 0.09);
  backdrop-filter: blur(24px) saturate(145%);
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 17px 16px 13px;
  border-bottom: 1px solid rgba(29, 29, 31, 0.06);
}

.history-header span {
  color: #96989e;
  font-size: 8px;
  font-weight: 760;
  letter-spacing: 0.15em;
}

.history-header h2 {
  margin: 2px 0 0;
  color: #25262a;
  font-size: 16px;
  letter-spacing: -0.015em;
}

.new-chat-button {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  padding: 0;
  color: #fff;
  font: inherit;
  font-size: 20px;
  background: var(--history-accent, #0071e3);
  border: 0;
  border-radius: 50%;
  cursor: pointer;
  box-shadow: 0 7px 16px color-mix(in srgb, var(--history-accent, #0071e3) 28%, transparent);
}

.history-list {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 7px;
  overflow-y: auto;
  padding: 10px;
}

.history-item {
  position: relative;
  flex: 0 0 auto;
  overflow: hidden;
  background: rgba(246, 248, 252, 0.8);
  border: 1px solid transparent;
  border-radius: 16px;
  transition: background 160ms ease, border-color 160ms ease;
}

.history-item:hover,
.history-item.active {
  background: #fff;
  border-color: color-mix(in srgb, var(--history-accent, #0071e3) 22%, transparent);
}

.history-item.active::before {
  position: absolute;
  top: 12px;
  bottom: 12px;
  left: 0;
  width: 3px;
  background: var(--history-accent, #0071e3);
  border-radius: 0 3px 3px 0;
  content: '';
}

.history-select {
  display: block;
  width: 100%;
  padding: 12px 34px 11px 13px;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.history-select strong,
.history-select span,
.history-select small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-select strong {
  color: #303136;
  font-size: 13px;
  font-weight: 650;
}

.history-select span {
  margin-top: 5px;
  color: #858890;
  font-size: 11px;
}

.history-select small {
  margin-top: 7px;
  color: #aaaeb6;
  font-size: 9px;
}

.delete-chat-button {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  padding: 0;
  color: #9a9da4;
  font: inherit;
  font-size: 17px;
  line-height: 1;
  background: transparent;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
}

.delete-chat-button:hover {
  color: #d33b3b;
  background: rgba(211, 59, 59, 0.08);
}

.history-empty {
  display: flex;
  min-height: 180px;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 24px;
  color: #999ca3;
  font-size: 11px;
  text-align: center;
}

.history-empty strong {
  color: #62656c;
  font-size: 12px;
}

@media (max-width: 820px) {
  .history-panel {
    width: 100%;
    min-width: 0;
    max-height: 168px;
  }

  .history-header {
    padding: 10px 12px;
  }

  .history-list {
    flex-direction: row;
    overflow-x: auto;
    overflow-y: hidden;
  }

  .history-item {
    width: 220px;
    min-width: 220px;
  }

  .history-empty {
    min-height: 80px;
  }
}
</style>
