<script setup lang="ts">
import logoImage from '../../assets/images/brand/logo.png'
import { useAuthStore } from '../../stores/auth'
import PrimaryNavigation from './PrimaryNavigation.vue'
import ProfileMenu from './ProfileMenu.vue'

const auth = useAuthStore()
</script>

<template>
  <header class="app-header">
    <div class="app-header__inner">
      <RouterLink
        class="app-header__brand"
        to="/"
        aria-label="eye dont care home"
      >
        <img :src="logoImage" alt="eye dont care" />
      </RouterLink>
      <PrimaryNavigation />
      <div class="app-header__actions">
        <template v-if="auth.isAuthenticated">
          <ProfileMenu />
        </template>
        <template v-else>
          <button
            class="app-header__auth-button app-header__auth-button--quiet"
            type="button"
            @click="auth.openSignup"
          >
            &#xD68C;&#xC6D0;&#xAC00;&#xC785;
          </button>
          <button
            class="app-header__auth-button"
            type="button"
            @click="auth.openLogin"
          >
            &#xB85C;&#xADF8;&#xC778;
          </button>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 10;
  height: 118px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(16px);
}
.app-header__inner {
  display: flex;
  width: min(1200px, calc(100% - 120px));
  height: 100%;
  align-items: center;
  gap: 105px;
  margin-inline: auto;
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
@media (max-width: 1100px) {
  .app-header__inner {
    width: min(900px, calc(100% - 52px));
    gap: 35px;
  }
}
@media (max-width: 640px) {
  .app-header {
    height: auto;
  }
  .app-header__inner {
    width: calc(100% - 32px);
    height: auto;
    flex-wrap: wrap;
    gap: 8px 16px;
    padding: 8px 0 0;
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
  .app-header__auth-button {
    min-height: 30px;
    padding-inline: 9px;
    font-size: 11px;
  }
}
</style>
