<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { FeedbackCategory } from '../../types/footer'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: [payload: { category: FeedbackCategory; message: string }]
}>()

const categories: FeedbackCategory[] = ['버그 문의', '불편했던 점', '개선 제안']
const selectedCategory = ref<FeedbackCategory>('버그 문의')
const message = ref('')
const textareaRef = ref<globalThis.HTMLTextAreaElement | null>(null)
let previousBodyOverflow = ''

function resetState() {
  selectedCategory.value = '버그 문의'
  message.value = ''
}

function handleBackdropClick(event: {
  target: unknown
  currentTarget: unknown
}) {
  if (event.target === event.currentTarget) {
    emit('close')
  }
}

function handleSubmit() {
  if (!message.value.trim()) return
  emit('submit', {
    category: selectedCategory.value,
    message: message.value,
  })
  resetState()
}

function handleKeydown(event: globalThis.KeyboardEvent) {
  if (event.key === 'Escape') emit('close')
}

watch(
  () => props.open,
  async (isOpen) => {
    if (isOpen) {
      previousBodyOverflow = globalThis.document.body.style.overflow
      globalThis.document.body.style.overflow = 'hidden'
      globalThis.addEventListener('keydown', handleKeydown)
      await nextTick()
      textareaRef.value?.focus()
      return
    }

    globalThis.document.body.style.overflow = previousBodyOverflow
    globalThis.removeEventListener('keydown', handleKeydown)
  },
)

onBeforeUnmount(() => {
  globalThis.document.body.style.overflow = previousBodyOverflow
  globalThis.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog-pop" appear>
      <div
        v-if="open"
        class="feedback-dialog-backdrop"
        @click="handleBackdropClick"
      >
        <section
          class="feedback-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="feedback-dialog-title"
        >
          <button
            class="feedback-dialog__close"
            type="button"
            aria-label="닫기"
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

          <h2 id="feedback-dialog-title">피드백 보내기</h2>
          <p class="feedback-dialog__helper">
            버그, 불편했던 점, 아이디어 무엇이든 편하게 남겨주세요.
          </p>

          <div class="feedback-dialog__categories">
            <button
              v-for="category in categories"
              :key="category"
              type="button"
              class="feedback-dialog__chip"
              :class="{
                'feedback-dialog__chip--active': selectedCategory === category,
              }"
              :aria-pressed="selectedCategory === category"
              @click="selectedCategory = category"
            >
              {{ category }}
            </button>
          </div>

          <textarea
            ref="textareaRef"
            v-model="message"
            class="feedback-dialog__textarea"
            placeholder="겪으신 문제나 의견을 자세히 적어주세요."
            required
          ></textarea>

          <button
            class="feedback-dialog__submit"
            type="button"
            :disabled="!message.trim()"
            @click="handleSubmit"
          >
            보내기
          </button>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.feedback-dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: 16px;
  background: rgba(23, 36, 61, 0.45);
}

.feedback-dialog {
  position: relative;
  width: min(480px, calc(100% - 32px));
  max-height: 82vh;
  overflow-y: auto;
  padding: 30px 28px 24px;
  border-radius: 22px;
  background: #fff;
  box-shadow: var(--shadow-float);
}

.feedback-dialog__close {
  position: absolute;
  top: 16px;
  right: 16px;
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 50%;
  color: var(--color-ink);
  background: var(--color-surface-soft);
  line-height: 1;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.feedback-dialog__close svg {
  width: 18px;
  height: 18px;
}
.feedback-dialog__close:hover {
  background: var(--color-blue-soft);
}

.feedback-dialog h2 {
  margin: 0 32px 6px 0;
  color: var(--color-ink);
  font-size: 22px;
}

.feedback-dialog__helper {
  margin: 0 0 18px;
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.6;
  word-break: keep-all;
}

.feedback-dialog__categories {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.feedback-dialog__chip {
  padding: 9px 16px;
  border: 1px solid var(--color-accent-blue);
  border-radius: var(--radius-button);
  color: var(--color-accent-blue);
  background: transparent;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    border-color var(--duration-fast) ease,
    color var(--duration-fast) ease;
}

.feedback-dialog__chip:hover {
  border-color: color-mix(in srgb, var(--color-accent-blue) 85%, black);
  background: var(--color-blue-soft);
}

.feedback-dialog__chip--active,
.feedback-dialog__chip--active:hover {
  color: #fff;
  background: var(--color-accent-blue);
}

.feedback-dialog__textarea {
  width: 100%;
  min-height: 120px;
  padding: 12px 14px;
  border: 1px solid var(--color-line);
  border-radius: 12px;
  color: var(--color-ink);
  background: var(--color-surface-soft);
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
}

.feedback-dialog__submit {
  width: 100%;
  margin-top: 16px;
  padding: 14px;
  border-radius: 12px;
  color: #fff;
  background: var(--color-accent-blue);
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}

.feedback-dialog__submit:hover:not(:disabled) {
  background: color-mix(in srgb, var(--color-accent-blue) 85%, black);
}

.feedback-dialog__submit:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.dialog-pop-enter-active,
.dialog-pop-leave-active {
  transition: opacity 200ms ease;
}
.dialog-pop-enter-from,
.dialog-pop-leave-to {
  opacity: 0;
}
.dialog-pop-enter-active .feedback-dialog,
.dialog-pop-leave-active .feedback-dialog {
  transition:
    transform 240ms var(--ease-out),
    opacity 240ms var(--ease-out);
}
.dialog-pop-enter-from .feedback-dialog,
.dialog-pop-leave-to .feedback-dialog {
  opacity: 0;
  transform: scale(0.96) translateY(8px);
}
</style>
