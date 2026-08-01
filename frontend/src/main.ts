import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './assets/styles/tokens.css'
import './assets/styles/reset.css'
import './assets/styles/global.css'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 저장된 토큰이 있으면 새로고침 후에도 로그인을 복원한다(비동기 — 마운트를 막지 않는다).
void useAuthStore().restoreSession()

app.mount('#app')
