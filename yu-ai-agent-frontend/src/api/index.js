import axios from 'axios'

// 始终使用同源 API。开发服务器负责把 /api 代理到本地后端，
// 因此通过局域网或公网隧道访问时，不会错误请求访问者电脑上的 localhost。
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

// 创建axios实例
const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

const readApiError = async (response) => {
  const responseText = await response.text()
  if (!responseText) return `请求失败（${response.status}）`
  try {
    const body = JSON.parse(responseText)
    return body.detail || body.message || body.error || responseText
  } catch {
    return responseText
  }
}

// 封装SSE连接
export const connectSSE = (url, params, onMessage, onError) => {
  // 构建带参数的URL
  const queryString = Object.keys(params)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
  
  const fullUrl = `${API_BASE_URL}${url}?${queryString}`
  
  // 创建EventSource
  const eventSource = new EventSource(fullUrl)
  
  eventSource.onmessage = event => {
    let data = event.data
    
    // 检查是否是特殊标记
    if (data === '[DONE]') {
      if (onMessage) onMessage('[DONE]')
    } else {
      // 处理普通消息
      if (onMessage) onMessage(data)
    }
  }
  
  eventSource.onerror = error => {
    if (onError) onError(error)
    eventSource.close()
  }
  
  // 返回eventSource实例，以便后续可以关闭连接
  return eventSource
}

const parseSSEBlock = (block) => {
  let event = 'message'
  const dataLines = []

  block.split('\n').forEach((line) => {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  })

  return { event, data: dataLines.join('\n') }
}

// 使用 POST 建立 SSE 流，适合发送长篇行业资料
export const connectPostSSE = (url, payload, handlers = {}) => {
  const controller = new AbortController()
  let finished = false

  const finish = () => {
    if (finished) return
    finished = true
    handlers.onDone?.()
  }

  const handleBlock = (block) => {
    if (!block.trim()) return
    const { event, data } = parseSSEBlock(block)

    if (event === 'done' || data === '[DONE]') {
      finish()
      return
    }
    if (event === 'error') {
      throw new Error(data || '行业调研服务暂时不可用')
    }
    if (data) {
      handlers.onMessage?.(data)
    }
  }

  const run = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}${url}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream'
        },
        body: JSON.stringify(payload),
        signal: controller.signal
      })

      if (!response.ok) {
        throw new Error(await readApiError(response))
      }
      if (!response.body) {
        throw new Error('浏览器无法读取流式响应')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (!finished) {
        const { value, done } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        buffer = buffer.replace(/\r\n/g, '\n')

        let boundary = buffer.indexOf('\n\n')
        while (boundary !== -1) {
          const block = buffer.slice(0, boundary)
          buffer = buffer.slice(boundary + 2)
          handleBlock(block)
          boundary = buffer.indexOf('\n\n')
        }
      }

      buffer += decoder.decode()
      if (!finished && buffer.trim()) {
        handleBlock(buffer)
      }
      finish()
    } catch (error) {
      if (error.name !== 'AbortError' && !finished) {
        handlers.onError?.(error)
      }
    }
  }

  run()

  return {
    close: () => controller.abort()
  }
}

// 行业调研助手聊天
export const chatWithIndustryResearch = (message, chatId, documentIds = [], handlers) => {
  return connectPostSSE('/ai/industry_research/chat/sse', { message, chatId, documentIds }, handlers)
}

// 上传并解析 PDF、DOC、DOCX 文档
export const uploadIndustryDocuments = async (files, chatId) => {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  formData.append('chatId', chatId)

  const response = await fetch(`${API_BASE_URL}/ai/industry_research/files`, {
    method: 'POST',
    body: formData
  })
  if (!response.ok) {
    throw new Error(await readApiError(response))
  }
  return response.json()
}

export const deleteIndustryDocument = async (documentId, chatId) => {
  const query = new URLSearchParams({ chatId })
  const response = await fetch(
    `${API_BASE_URL}/ai/industry_research/files/${encodeURIComponent(documentId)}?${query}`,
    { method: 'DELETE' }
  )
  if (!response.ok) {
    throw new Error(await readApiError(response))
  }
}

// 数字菜叔本地 RAG 知识库状态
export const getCaishuKnowledgeStatus = async () => {
  const response = await fetch(`${API_BASE_URL}/ai/caishu/status`)
  if (!response.ok) throw new Error(await readApiError(response))
  return response.json()
}

export const reindexCaishuKnowledge = async () => {
  const response = await fetch(`${API_BASE_URL}/ai/caishu/reindex`, { method: 'POST' })
  if (!response.ok) throw new Error(await readApiError(response))
  return response.json()
}

export const chatWithDigitalCaishu = (message, chatId, handlers) => {
  return connectPostSSE('/ai/caishu/chat/sse', { message, chatId }, handlers)
}

// 三个智能体共用的本地会话历史
export const getConversationList = async (agentType) => {
  const response = await fetch(`${API_BASE_URL}/ai/conversations/${encodeURIComponent(agentType)}`)
  if (!response.ok) throw new Error(await readApiError(response))
  return response.json()
}

export const getConversation = async (agentType, conversationId) => {
  const response = await fetch(
    `${API_BASE_URL}/ai/conversations/${encodeURIComponent(agentType)}/${encodeURIComponent(conversationId)}`
  )
  if (!response.ok) throw new Error(await readApiError(response))
  return response.json()
}

export const deleteConversation = async (agentType, conversationId) => {
  const response = await fetch(
    `${API_BASE_URL}/ai/conversations/${encodeURIComponent(agentType)}/${encodeURIComponent(conversationId)}`,
    { method: 'DELETE' }
  )
  if (!response.ok) throw new Error(await readApiError(response))
}

// AI超级智能体聊天
export const chatWithManus = (message, chatId) => {
  return connectSSE('/ai/manus/chat', { message, chatId })
}

export default {
  chatWithIndustryResearch,
  uploadIndustryDocuments,
  deleteIndustryDocument,
  getCaishuKnowledgeStatus,
  reindexCaishuKnowledge,
  chatWithDigitalCaishu,
  getConversationList,
  getConversation,
  deleteConversation,
  chatWithManus
}
