<template>
  <main class="home-container">
    <div class="ambient ambient-blue"></div>
    <div class="ambient ambient-rose"></div>

    <section class="hero">
      <p class="eyebrow">PERSONAL AI WORKSPACE</p>
      <h1 class="hero-title">文俊的超级助手</h1>
      <p class="subtitle">无限进步</p>
    </section>

    <section class="apps-container" aria-label="AI 应用">
      <button class="app-card research-card" type="button" @click="navigateTo('/industry-research')">
        <span class="icon-shell research-icon" aria-hidden="true">📊</span>
        <span class="app-info">
          <strong class="app-title">FDE 岗位调研助手</strong>
          <span class="app-desc">FDE 岗位 30 分钟了解任意行业</span>
        </span>
        <span class="app-button">
          立即体验
          <span class="btn-icon" aria-hidden="true">→</span>
        </span>
      </button>

      <button class="app-card agent-card" type="button" @click="navigateTo('/super-agent')">
        <span class="icon-shell robot-icon" aria-hidden="true">🤖</span>
        <span class="app-info">
          <strong class="app-title">AI 超级智能体</strong>
          <span class="app-desc">全能型 AI 助手，解决各类专业问题</span>
        </span>
        <span class="app-button">
          立即体验
          <span class="btn-icon" aria-hidden="true">→</span>
        </span>
      </button>

      <button class="app-card caishu-card" type="button" @click="navigateTo('/digital-caishu')">
        <span class="icon-shell caishu-icon" aria-hidden="true">🧠</span>
        <span class="app-info">
          <strong class="app-title">数字菜叔</strong>
          <span class="app-desc">基于菜叔公众号文章，回答认知与成长问题</span>
        </span>
        <span class="app-button">
          立即体验
          <span class="btn-icon" aria-hidden="true">→</span>
        </span>
      </button>
    </section>
  </main>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'

useHead({
  title: '文俊的超级助手 - 首页',
  meta: [
    {
      name: 'description',
      content: '文俊的超级助手提供 FDE 岗位调研助手、AI 超级智能体和数字菜叔知识库问答'
    },
    {
      name: 'keywords',
      content: 'AI智能体,行业调研,FDE,AI助手,行业研究,AI超级智能体,首页'
    }
  ]
})

const router = useRouter()

const navigateTo = (path) => {
  router.push(path)
}
</script>

<style scoped>
.home-container {
  position: relative;
  isolation: isolate;
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  align-items: center;
  overflow: hidden;
  padding: 88px 24px 72px;
  color: #1d1d1f;
  background:
    radial-gradient(circle at 50% -10%, rgba(255, 255, 255, 0.98) 0, rgba(255, 255, 255, 0.58) 34%, transparent 62%),
    linear-gradient(145deg, #fbfdff 0%, #f4f7ff 48%, #fff8f8 100%);
}

.home-container::after {
  position: absolute;
  z-index: -2;
  inset: 0;
  background-image: linear-gradient(rgba(255, 255, 255, 0.42) 1px, transparent 1px);
  background-size: 100% 80px;
  content: '';
  mask-image: linear-gradient(to bottom, transparent, black 30%, transparent);
  pointer-events: none;
}

.ambient {
  position: absolute;
  z-index: -1;
  width: 560px;
  height: 560px;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.42;
  pointer-events: none;
}

.ambient-blue {
  top: 60px;
  right: -220px;
  background: #c7dcff;
}

.ambient-rose {
  bottom: -300px;
  left: -180px;
  background: #ffe0e6;
}

.hero {
  width: 100%;
  max-width: 980px;
  text-align: center;
}

.eyebrow {
  margin: 0 0 22px;
  color: #6e6e73;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.2em;
}

.hero-title {
  margin: 0;
  color: #172033;
  font-size: clamp(52px, 7vw, 92px);
  font-weight: 700;
  letter-spacing: -0.055em;
  line-height: 1.04;
  text-wrap: balance;
}

.subtitle {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin: 28px 0 0;
  color: #777d8d;
  font-size: clamp(18px, 2vw, 24px);
  font-weight: 450;
  letter-spacing: 0.22em;
}

.subtitle::before,
.subtitle::after {
  color: #a7adba;
  content: '/';
}

.apps-container {
  display: grid;
  width: 100%;
  max-width: 1180px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
  margin-top: 64px;
}

.app-card {
  appearance: none;
  display: flex;
  min-height: 500px;
  flex-direction: column;
  align-items: center;
  padding: 48px 28px 38px;
  color: inherit;
  font-family: inherit;
  text-align: center;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 36px;
  box-shadow:
    0 30px 80px rgba(52, 64, 84, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(28px) saturate(145%);
  cursor: pointer;
  transition: transform 320ms cubic-bezier(0.2, 0.8, 0.2, 1), box-shadow 320ms ease, border-color 320ms ease;
}

.app-card:hover {
  border-color: rgba(255, 255, 255, 1);
  box-shadow:
    0 38px 100px rgba(52, 64, 84, 0.16),
    inset 0 1px 0 #fff;
  transform: translateY(-8px);
}

.app-card:focus-visible {
  outline: 4px solid rgba(0, 113, 227, 0.22);
  outline-offset: 4px;
}

.icon-shell {
  display: grid;
  width: 148px;
  height: 148px;
  place-items: center;
  margin-bottom: 38px;
  font-size: 72px;
  border: 1px solid rgba(255, 255, 255, 0.86);
  border-radius: 50%;
  box-shadow:
    0 18px 42px rgba(65, 80, 110, 0.14),
    inset 0 1px 0 rgba(255, 255, 255, 0.95);
}

.research-icon {
  background: linear-gradient(145deg, #f7fbff, #e4efff);
}

.robot-icon {
  background: linear-gradient(145deg, #f5faff, #deecff);
}

.caishu-icon {
  background: linear-gradient(145deg, #f5fff8, #dcf5e5);
}

.app-info {
  display: flex;
  flex-direction: column;
}

.app-title {
  color: #192238;
  font-size: 31px;
  font-weight: 680;
  letter-spacing: -0.035em;
}

.app-desc {
  margin-top: 20px;
  color: #7a8190;
  font-size: 17px;
  line-height: 1.6;
}

.app-button {
  display: inline-flex;
  min-width: 224px;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: auto;
  padding: 15px 28px;
  color: #fff;
  font-size: 17px;
  font-weight: 560;
  background: linear-gradient(180deg, #1688ff 0%, #006ee6 100%);
  border-radius: 999px;
  box-shadow: 0 12px 28px rgba(0, 113, 227, 0.26);
  transition: transform 200ms ease, filter 200ms ease;
}

.app-card:hover .app-button {
  filter: brightness(1.04);
  transform: scale(1.02);
}

.btn-icon {
  font-size: 21px;
  transition: transform 200ms ease;
}

.app-card:hover .btn-icon {
  transform: translateX(4px);
}

@media (max-width: 820px) {
  .home-container {
    padding: 64px 18px 48px;
  }

  .apps-container {
    max-width: 560px;
    grid-template-columns: 1fr;
    gap: 22px;
    margin-top: 48px;
  }

  .app-card {
    min-height: 430px;
    padding: 40px 28px 32px;
    border-radius: 30px;
  }

  .icon-shell {
    width: 122px;
    height: 122px;
    margin-bottom: 30px;
    font-size: 58px;
  }
}

@media (max-width: 480px) {
  .home-container {
    padding: 48px 14px 32px;
  }

  .eyebrow {
    margin-bottom: 18px;
    font-size: 10px;
  }

  .hero-title {
    font-size: 42px;
  }

  .subtitle {
    gap: 10px;
    margin-top: 20px;
    font-size: 16px;
  }

  .apps-container {
    margin-top: 36px;
  }

  .app-card {
    min-height: 380px;
    padding: 32px 20px 24px;
  }

  .icon-shell {
    width: 104px;
    height: 104px;
    margin-bottom: 24px;
    font-size: 48px;
  }

  .app-title {
    font-size: 25px;
  }

  .app-desc {
    margin-top: 12px;
    font-size: 15px;
  }

  .app-button {
    min-width: 190px;
    padding: 13px 24px;
    font-size: 16px;
  }
}
</style>
