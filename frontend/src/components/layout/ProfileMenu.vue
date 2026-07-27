<script setup lang="ts">
import { ref } from 'vue'
import { profileData } from '../../mocks/profile'
import { useToast } from '../../composables/useToast'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const { showToast } = useToast()
const isOpen = ref(false)

function closeMenu() {
  isOpen.value = false
}

function handleLogout() {
  auth.signOut()
  closeMenu()
  showToast('로그아웃했어요.')
}
</script>

<template>
  <div class="profile-menu">
    <button
      class="profile-menu__button"
      type="button"
      aria-label="프로필 메뉴"
      :aria-expanded="isOpen"
      @click="isOpen = !isOpen"
    >
      <img class="profile-menu__avatar" :src="auth.user.avatar" alt="" />
      <span class="profile-menu__meta">
        <strong>{{ auth.user.nickname }}</strong
        ><small>Lv. {{ auth.user.level }}</small>
      </span>
    </button>
    <section
      v-if="isOpen"
      class="profile-menu__panel"
      aria-label="프로필 미리보기"
    >
      <div class="profile-menu__heading">
        <img :src="auth.user.avatar" :alt="`${auth.user.nickname} 프로필`" />
        <div>
          <strong>{{ auth.user.nickname }}</strong
          ><span>Lv. {{ auth.user.level }} · 눈 건강 챌린저</span>
        </div>
      </div>
      <RouterLink to="/profile" @click="closeMenu"
        >마이페이지 <span>→</span></RouterLink
      >
      <RouterLink to="/settings" @click="closeMenu"
        >설정 <span>→</span></RouterLink
      >
      <button type="button" @click="handleLogout">
        로그아웃 <span>↗</span>
      </button>
    </section>
  </div>
</template>

<style scoped>
.profile-menu {
  position: relative;
}
.profile-menu__button {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0;
  color: var(--color-ink);
  background: transparent;
  cursor: pointer;
}
.profile-menu__avatar {
  width: 48px;
  height: 48px;
  border: 2px solid #fff;
  border-radius: 50%;
  object-fit: cover;
  background: var(--color-blue-soft);
  box-shadow: 0 2px 7px rgba(26, 35, 78, 0.12);
}
.profile-menu__meta {
  display: grid;
  gap: 1px;
  text-align: left;
  font-size: 14px;
}
.profile-menu__meta small,
.profile-menu__heading span {
  color: var(--color-muted);
  font-size: 11px;
}
.profile-menu__panel {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  display: grid;
  width: 290px;
  gap: 12px;
  padding: 18px;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: #fff;
  box-shadow: var(--shadow-float);
}
.profile-menu__heading {
  display: flex;
  align-items: center;
  gap: 10px;
}
.profile-menu__heading img {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--color-blue-soft);
}
.profile-menu__heading div {
  display: grid;
  gap: 2px;
}
.profile-menu__panel a,
.profile-menu__panel button {
  display: flex;
  justify-content: space-between;
  padding: 0;
  border: 0;
  color: var(--color-ink);
  background: transparent;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.profile-menu__panel a span,
.profile-menu__panel button span {
  color: var(--color-accent-blue);
}
@media (max-width: 640px) {
  .profile-menu__avatar {
    width: 31px;
    height: 31px;
  }
  .profile-menu__meta {
    display: none;
  }
}
</style>