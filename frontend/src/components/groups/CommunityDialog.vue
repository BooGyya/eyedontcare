<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps<{
  open: boolean
  title: string
  description: string
  closeOnBackdrop?: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const dialogRef = ref<any>()

function handleKeydown(event: { key: string }) {
  if (event.key === 'Escape') {
    emit('close')
  }
}

function handleBackdropClick(event: {
  target: unknown
  currentTarget: unknown
}) {
  if (props.closeOnBackdrop && event.target === event.currentTarget) {
    emit('close')
  }
}

watch(
  () => props.open,
  async (isOpen) => {
    if (!isOpen) return

    await nextTick()
    dialogRef.value?.querySelector('[data-dialog-initial-focus]')?.focus()
  },
)

onBeforeUnmount(() => globalThis.removeEventListener('keydown', handleKeydown))

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) globalThis.addEventListener('keydown', handleKeydown)
    else globalThis.removeEventListener('keydown', handleKeydown)
  },
)
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="community-dialog-backdrop"
      @click="handleBackdropClick"
    >
      <section
        ref="dialogRef"
        class="community-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="community-dialog-title"
        aria-describedby="community-dialog-description"
      >
        <header>
          <div>
            <span>PLAY TOGETHER</span>
            <h2 id="community-dialog-title">{{ title }}</h2>
            <p id="community-dialog-description">{{ description }}</p>
          </div>
          <button type="button" aria-label="모달 닫기" @click="emit('close')">
            ×
          </button>
        </header>
        <div class="community-dialog__content">
          <slot />
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.community-dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(23, 36, 61, 0.45);
}

.community-dialog {
  width: min(100%, 560px);
  max-height: min(760px, calc(100vh - 48px));
  overflow: auto;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: #fff;
  box-shadow: var(--shadow-float);
}

.community-dialog header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 26px 28px 20px;
  border-bottom: 1px solid var(--color-line);
}

.community-dialog header span {
  color: var(--color-accent-blue);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.community-dialog h2 {
  margin: 5px 0 6px;
  font-size: 24px;
}
.community-dialog p {
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.55;
  word-break: keep-all;
}
.community-dialog header button {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  color: var(--color-ink);
  background: var(--color-surface-soft);
  font-size: 25px;
  line-height: 1;
  cursor: pointer;
}
.community-dialog__content {
  padding: 24px 28px 28px;
}

@media (max-width: 640px) {
  .community-dialog-backdrop {
    padding: 16px;
  }
  .community-dialog {
    max-height: calc(100vh - 32px);
  }
  .community-dialog header,
  .community-dialog__content {
    padding-right: 20px;
    padding-left: 20px;
  }
}
</style>
