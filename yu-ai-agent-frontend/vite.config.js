import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    host: '127.0.0.1',
    port: 3001,
    strictPort: true,
    allowedHosts: ['.trycloudflare.com'],
    cors: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8123',
        changeOrigin: true
      }
    }
  },
  preview: {
    host: '127.0.0.1',
    port: 3001,
    strictPort: true,
    allowedHosts: ['.trycloudflare.com'],
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8123',
        changeOrigin: true
      }
    }
  }
})
