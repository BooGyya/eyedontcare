<script setup lang="ts">
defineProps<{
  message: string
  visible: boolean
}>()
</script>

<template>
  <Transition name="toast">
    <p v-if="visible" class="toast-message" role="status">
      {{ message }}
    </p>
  </Transition>
</template>

<style scoped>
.toast-message {
  position: fixed;
  bottom: 24px;
  left: 50%;
  z-index: 20;
  margin: 0;
  padding: 12px 18px;
  border-radius: var(--radius-button);
  color: #fff;
  background: #252d51;
  box-shadow: 0 12px 30px rgba(30, 36, 78, 0.22);
  font-size: 13px;
  transform: translateX(-50%);
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, 12px);
}
</style>
