<template>
  <div class="ai-avatar-fallback" :class="type">
    <span v-if="type === 'research'">📊</span>
    <span v-else-if="type === 'love'">❤️</span>
    <span v-else-if="type === 'caishu'">🧠</span>
    <span v-else>🤖</span>
  </div>
</template>

<script setup>
defineProps({
  type: {
    type: String,
    default: 'default'
  }
})
</script>

<style scoped>
.ai-avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  border-radius: 50%;
}

.love {
  background: linear-gradient(145deg, #fff7f8, #ffe6eb);
}

.research {
  background: linear-gradient(145deg, #f7fbff, #e1edff);
}

.caishu {
  background: linear-gradient(145deg, #f5fff8, #dcf5e5);
}

.default, .super {
  background: linear-gradient(145deg, #f4f9ff, #dceaff);
}
</style>
