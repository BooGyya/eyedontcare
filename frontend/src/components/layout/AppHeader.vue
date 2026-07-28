<script setup lang="ts">
import { useRouter } from 'vue-router'
import logoImage from '../../assets/images/brand/logo.png'
import { useToast } from '../../composables/useToast'
import { useAuthStore } from '../../stores/auth'
import PrimaryNavigation from './PrimaryNavigation.vue'
import ProfileMenu from './ProfileMenu.vue'

const router = useRouter()
const auth = useAuthStore()
const { showToast } = useToast()

function handleMemberNavigation(path: '/notifications' | '/settings') {
  if (auth.isAuthenticated) {
    router.push(path)
    return
  }

  showToast('이 기능은 로그인 후 이용할 수 있어요.')
  auth.openLogin()
}
</script>

<template>
  <header class="app-header">
    <RouterLink class="app-header__brand" to="/" aria-label="eye dont care 홈">
      <img :src="logoImage" alt="eye dont care" />
    </RouterLink>
    <PrimaryNavigation />
    <div class="app-header__actions">
      <template v-if="auth.isAuthenticated">
        <span class="app-header__coin"><i>●</i><b>1,250</b></span>
        <button
          class="app-header__icon-button"
          type="button"
          aria-label="알림"
          @click="handleMemberNavigation('/notifications')"
        >
          ♧
        </button>
        <button
          class="app-header__icon-button"
          type="button"
          aria-label="설정"
          @click="handleMemberNavigation('/settings')"
        >
          ⚙
        </button>
        <ProfileMenu />
      </template>
      <template v-else>
        <button
          class="app-header__auth-button app-header__auth-button--quiet"
          type="button"
          @click="auth.openSignup"
        >
          회원가입
        </button>
        <button
          class="app-header__auth-button"
          type="button"
          @click="auth.openLogin"
        >
          로그인
        </button>
      </template>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 105px;
  height: 118px;
  padding: 0 58px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(16px);
}
.app-header__brand {
  flex: 0 0 178px;
}
.app-header__brand img {
  width: 154px;
  height: 101px;
  object-fit: contain;
}
.app-header__actions {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-left: auto;
}
.app-header__icon-button {
  color: var(--color-ink);
  background: transparent;
  font-size: 21px;
  line-height: 1;
  cursor: pointer;
}
.app-header__coin {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--color-ink);
  font-size: 17px;
}
.app-header__coin i {
  display: grid;
  width: 25px;
  height: 25px;
  place-items: center;
  border-radius: 50%;
  color: #f6b928;
  background: #fff2c2;
  font-size: 15px;
  font-style: normal;
}
.app-header__auth-button {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-button);
  color: var(--color-accent-blue);
  background: #fff;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
  cursor: pointer;
}
.app-header__auth-button:hover {
  color: #fff;
  background: var(--color-accent-blue);
}
.app-header__auth-button--quiet {
  color: var(--color-muted);
}
.app-header__auth-button--quiet:hover {
  background: var(--color-muted);
}
.app-header__guest-status {
  padding: 6px 9px;
  border-radius: var(--radius-button);
  color: #536eb2;
  background: var(--color-blue-soft);
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}
@media (max-width: 1100px) {
  .app-header {
    gap: 35px;
    padding-inline: 26px;
  }
}
@media (max-width: 640px) {
  .app-header {
    flex-wrap: wrap;
    gap: 8px 16px;
    height: auto;
    padding: 8px 16px 0;
  }
  .app-header__brand {
    flex-basis: 105px;
  }
  .app-header__brand img {
    width: 96px;
    height: 63px;
  }
  .app-header__actions {
    gap: 7px;
  }
  .app-header__coin,
  .app-header__icon-button {
    display: none;
  }
  .app-header__auth-button {
    min-height: 30px;
    padding-inline: 9px;
    font-size: 11px;
  }
}
</style>
