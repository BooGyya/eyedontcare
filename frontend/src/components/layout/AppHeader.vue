<script setup lang="ts">
import logoImage from '../../assets/images/brand/logo.png'
import PrimaryNavigation from './PrimaryNavigation.vue'
import ProfileMenu from './ProfileMenu.vue'
</script>

<template>
  <header class="app-header">
    <div class="app-header__inner">
      <RouterLink
        class="app-header__brand"
        to="/"
        aria-label="eye dont care 홈"
      >
        <img :src="logoImage" alt="eye dont care" />
      </RouterLink>
      <PrimaryNavigation />
      <div class="app-header__actions">
        <span class="app-header__coin"><i>●</i><b>1,250</b></span>
        <RouterLink
          class="app-header__icon-link"
          to="/notifications"
          aria-label="알림"
          >🔔</RouterLink
        >
        <RouterLink
          class="app-header__icon-link"
          to="/settings"
          aria-label="설정"
          >⚙</RouterLink
        >
        <ProfileMenu />
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
  gap: 22px;
  margin-left: auto;
}
.app-header__icon-link {
  position: relative;
  color: var(--color-ink);
  font-size: 21px;
  line-height: 1;
}
.app-header__icon-link.router-link-active {
  color: var(--color-accent-blue);
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
    gap: 8px;
  }
  .app-header__coin,
  .app-header__icon-link:last-of-type {
    display: none;
  }
}
</style>
