import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget =
    process.env.VITE_PROXY_TARGET ||
    env.VITE_PROXY_TARGET ||
    'http://localhost:8080'

  // MediaPipe tasks-vision의 스레드 WASM(SharedArrayBuffer)이 동작하려면 cross-origin isolation이
  // 필요하다 — 이 두 헤더가 없으면 개발 서버에서 useEyeTracking() 카메라 시작이 조용히 실패한다.
  const crossOriginIsolationHeaders = {
    'Cross-Origin-Opener-Policy': 'same-origin',
    'Cross-Origin-Embedder-Policy': 'require-corp',
  }

  return {
    plugins: [vue()],
    server: {
      host: '0.0.0.0',
      headers: crossOriginIsolationHeaders,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
        '/ws': {
          target: proxyTarget,
          ws: true,
        },
      },
    },
    preview: {
      headers: crossOriginIsolationHeaders,
    },
  }
})
