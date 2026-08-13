import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '文俊的超级助手 - 首页',
      description: '文俊的超级助手提供行业调研助手和 AI 超级智能体，支持快速行业研究与复杂任务处理'
    }
  },
  {
    path: '/industry-research',
    alias: '/love-master',
    name: 'IndustryResearch',
    component: () => import('../views/IndustryResearch.vue'),
    meta: {
      title: '行业调研助手 - 文俊的超级助手',
      description: '面向 FDE 岗位的行业调研助手，帮助你在 30 分钟内快速建立对任意行业的系统认知'
    }
  },
  {
    path: '/super-agent',
    name: 'SuperAgent',
    component: () => import('../views/SuperAgent.vue'),
    meta: {
      title: 'AI 超级智能体 - 文俊的超级助手',
      description: 'AI 超级智能体是文俊的超级助手中的全能 AI 助手，能够处理专业问题并执行复杂任务'
    }
  },
  {
    path: '/digital-caishu',
    name: 'DigitalCaishu',
    component: () => import('../views/DigitalCaishu.vue'),
    meta: {
      title: '数字菜叔 - 文俊的超级助手',
      description: '基于菜叔微信公众号文章构建的本地 RAG 知识助手'
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局导航守卫，设置文档标题
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title
  }
  next()
})

export default router
