<script setup lang="ts">
const navigationItems = [
  { label: '홈', to: '/' },
  { label: '게임', to: '/games', testId: 'nav-games' },
  { label: '랭킹', to: '/ranking' },
  { label: '길드', to: '/community' },
]
</script>

<template>
  <nav class="primary-navigation" aria-label="주요 메뉴">
    <RouterLink
      v-for="item in navigationItems"
      :key="item.to"
      v-slot="{ href, isExactActive, navigate }"
      :to="item.to"
      custom
    >
      <a
        :href="href"
        class="primary-navigation__link"
        :class="{ 'primary-navigation__link--active': isExactActive }"
        :data-testid="item.testId"
        :aria-current="isExactActive ? 'page' : undefined"
        @click="navigate"
      >
        {{ item.label }}
      </a>
    </RouterLink>
  </nav>
</template>

<style scoped>
.primary-navigation {
  display: flex;
  align-items: stretch;
  align-self: stretch;
  gap: 56px;
}

.primary-navigation__link {
  position: relative;
  display: grid;
  align-items: center;
  color: var(--color-ink);
  font-size: 16px;
  font-weight: 700;
  transition: color var(--duration-fast) ease;
}

.primary-navigation__link:hover {
  color: var(--color-primary);
}

.primary-navigation__link::after {
  position: absolute;
  right: 0;
  bottom: 23px;
  left: 0;
  height: 4px;
  border-radius: 999px;
  background: var(--color-accent-blue);
  content: '';
  transform: scaleX(0);
  transform-origin: center;
  transition: transform var(--duration-base) var(--ease-out);
}

.primary-navigation__link:hover::after,
.primary-navigation__link--active::after {
  transform: scaleX(1);
}

@media (max-width: 1100px) {
  .primary-navigation {
    gap: 24px;
  }
}

@media (max-width: 640px) {
  .primary-navigation {
    gap: 9px;
  }

  .primary-navigation__link {
    font-size: 11px;
  }

  .primary-navigation__link::after {
    bottom: 0;
    height: 3px;
  }
}
</style>
