<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useToast } from '../../composables/useToast'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const { showToast } = useToast()
const isOpen = ref(false)
const triggerButton = ref<globalThis.HTMLButtonElement | null>(null)
const panel = ref<globalThis.HTMLElement | null>(null)

function closeMenu() {
  isOpen.value = false
}

function handleLogout() {
  auth.signOut()
  closeMenu()
  showToast('로그아웃했어요.')
}

function handleDocumentClick(event: globalThis.MouseEvent) {
  const target = event.target as globalThis.Node | null
  if (!target) {
    return
  }
  if (panel.value?.contains(target) || triggerButton.value?.contains(target)) {
    return
  }
  closeMenu()
}

function handleKeydown(event: globalThis.KeyboardEvent) {
  if (event.key !== 'Escape') {
    return
  }
  closeMenu()
  triggerButton.value?.focus()
}

watch(isOpen, (open) => {
  if (open) {
    globalThis.document.addEventListener('click', handleDocumentClick)
    globalThis.document.addEventListener('keydown', handleKeydown)
  } else {
    globalThis.document.removeEventListener('click', handleDocumentClick)
    globalThis.document.removeEventListener('keydown', handleKeydown)
  }
})

onBeforeUnmount(() => {
  globalThis.document.removeEventListener('click', handleDocumentClick)
  globalThis.document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div class="profile-menu">
    <button
      ref="triggerButton"
      class="profile-menu__button"
      type="button"
      aria-label="프로필 메뉴"
      :aria-expanded="isOpen"
      @click="isOpen = !isOpen"
    >
      <img class="profile-menu__avatar" :src="auth.user.avatar" alt="" />
      <span class="profile-menu__meta">
        <strong>{{ auth.user.nickname }}</strong>
      </span>
    </button>
    <Transition name="profile-menu">
      <section
        v-if="isOpen"
        ref="panel"
        class="profile-menu__panel"
        aria-label="프로필 미리보기"
      >
        <div class="profile-menu__heading">
          <img :src="auth.user.avatar" :alt="`${auth.user.nickname} 프로필`" />
          <div>
            <strong>{{ auth.user.nickname }}</strong>
          </div>
        </div>
        <div class="profile-menu__actions">
          <RouterLink
            class="profile-menu__action profile-menu__action--primary"
            to="/profile"
            @click="closeMenu"
          >
            <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24">
              <circle cx="12" cy="8" r="3.5" />
              <path d="M4.5 20c.8-4 3.4-6 7.5-6s6.7 2 7.5 6" />
            </svg>
            마이페이지
          </RouterLink>
          <button
            class="profile-menu__action profile-menu__action--secondary"
            type="button"
            @click="handleLogout"
          >
            <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24">
              <path d="M10 5H5v14h5" />
              <path d="m14 8 4 4-4 4M18 12H9" />
            </svg>
            로그아웃
          </button>
        </div>
      </section>
    </Transition>
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
  padding: 6px 10px;
  border-radius: 999px;
  color: var(--color-ink);
  background: transparent;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.profile-menu__button:hover {
  background: var(--color-surface-soft);
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
.profile-menu__panel {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  display: grid;
  width: 290px;
  gap: 12px;
  padding: 18px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: #fff;
  box-shadow: var(--shadow-float);
  transform-origin: top right;
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
.profile-menu__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.profile-menu__action {
  display: inline-flex;
  min-height: 40px;
  flex: 1 1 118px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 9px 12px;
  border: 1px solid transparent;
  border-radius: var(--radius-button);
  color: var(--color-ink);
  font-size: 14px;
  font-weight: 700;
  text-align: center;
  cursor: pointer;
  transition:
    background-color 160ms ease,
    border-color 160ms ease,
    transform 160ms ease;
}
.profile-menu__action svg {
  width: 17px;
  height: 17px;
  flex: 0 0 17px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}
.profile-menu__action--primary {
  color: var(--color-accent-blue);
  background: var(--color-blue-soft);
}
.profile-menu__action--secondary {
  border-color: var(--color-line);
  color: var(--color-muted);
  background: #fff;
}
.profile-menu__action:hover {
  transform: translateY(-1px);
}
.profile-menu__action--primary:hover {
  background: #dfe7ff;
}
.profile-menu__action--secondary:hover {
  color: var(--color-ink);
  background: var(--color-surface-soft);
}
.profile-menu__action:active {
  transform: translateY(0);
}
.profile-menu__action:focus-visible {
  outline: 2px solid var(--color-accent-blue);
  outline-offset: 2px;
}
@media (max-width: 640px) {
  .profile-menu__avatar {
    width: 31px;
    height: 31px;
  }
  .profile-menu__meta {
    display: none;
  }
  .profile-menu__panel {
    width: min(290px, calc(100vw - 32px));
  }
}
.profile-menu-enter-active,
.profile-menu-leave-active {
  transition:
    opacity var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}
.profile-menu-enter-from,
.profile-menu-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.98);
}
</style>
