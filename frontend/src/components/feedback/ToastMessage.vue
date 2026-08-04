<script setup lang="ts">
defineProps<{
  message: string
  visible: boolean
}>()

defineEmits<{
  dismiss: []
}>()
</script>

<template>
  <Transition name="toast">
    <button
      v-if="visible"
      type="button"
      class="toast-message"
      role="status"
      aria-label="알림 닫기"
      @click="$emit('dismiss')"
    >
      {{ message }}
    </button>
  </Transition>
</template>

<style scoped>
.toast-message {
  position: fixed;
  bottom: 24px;
  left: 50%;
  z-index: 80;
  margin: 0;
  padding: 12px 18px;
  border: 0;
  border-radius: var(--radius-button);
  color: #fff;
  background: var(--color-ink);
  box-shadow: 0 12px 30px rgba(30, 36, 78, 0.22);
  font-family: inherit;
  font-size: 13px;
  transform: translateX(-50%);
  cursor: pointer;
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 240ms var(--ease-out),
    transform 240ms var(--ease-out);
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, 12px);
}
</style>
