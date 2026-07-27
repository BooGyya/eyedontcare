<script setup lang="ts">
import { useToast } from '../../composables/useToast'
import ToastMessage from '../feedback/ToastMessage.vue'
import AppFooter from './AppFooter.vue'
import AppHeader from './AppHeader.vue'

const { isVisible, message } = useToast()
</script>

<template>
  <div class="app-layout">
    <AppHeader />
    <main class="app-layout__content">
      <slot />
    </main>
    <AppFooter />
    <ToastMessage :message="message" :visible="isVisible" />
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
}

.app-layout__content {
  flex: 1;
  width: min(var(--content-width), calc(100% - 120px));
  margin: 0 auto;
}

@media (max-width: 1100px) {
  .app-layout__content {
    width: min(900px, calc(100% - 52px));
  }
}

@media (max-width: 640px) {
  .app-layout__content {
    width: calc(100% - 32px);
  }
}
</style>
