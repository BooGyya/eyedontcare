<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'

const props = defineProps<{
  open: boolean
  title: string
  description: string
  closeOnBackdrop?: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const titleId = useId()
const descriptionId = useId()
const dialogRef = ref<any>()
let previousBodyOverflow = ''

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

onBeforeUnmount(() => {
  globalThis.removeEventListener('keydown', handleKeydown)
  globalThis.document.body.style.overflow = previousBodyOverflow
})

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      globalThis.addEventListener('keydown', handleKeydown)
      previousBodyOverflow = globalThis.document.body.style.overflow
      globalThis.document.body.style.overflow = 'hidden'
      return
    }

    globalThis.removeEventListener('keydown', handleKeydown)
    globalThis.document.body.style.overflow = previousBodyOverflow
  },
)
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog-pop" appear>
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
          :aria-labelledby="titleId"
          :aria-describedby="descriptionId"
        >
          <header>
            <div>
              <span>PLAY TOGETHER</span>
              <h2 :id="titleId">{{ title }}</h2>
              <p :id="descriptionId">{{ description }}</p>
            </div>
            <button
              class="community-dialog__close"
              type="button"
              aria-label="모달 닫기"
              @click="emit('close')"
            >
              <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path
                  d="M6 6l12 12M18 6 6 18"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                />
              </svg>
            </button>
          </header>
          <div class="community-dialog__content">
            <slot />
          </div>
        </section>
      </div>
    </Transition>
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
.community-dialog__close {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 50%;
  color: var(--color-ink);
  background: var(--color-surface-soft);
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.community-dialog__close svg {
  width: 18px;
  height: 18px;
}
.community-dialog__close:hover {
  background: var(--color-blue-soft);
}
.community-dialog__content {
  padding: 24px 28px 28px;
}
.dialog-pop-enter-active,
.dialog-pop-leave-active {
  transition: opacity 200ms ease;
}
.dialog-pop-enter-from,
.dialog-pop-leave-to {
  opacity: 0;
}
.dialog-pop-enter-active .community-dialog,
.dialog-pop-leave-active .community-dialog {
  transition:
    transform 240ms var(--ease-out),
    opacity 240ms var(--ease-out);
}
.dialog-pop-enter-from .community-dialog,
.dialog-pop-leave-to .community-dialog {
  opacity: 0;
  transform: scale(0.96) translateY(8px);
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
