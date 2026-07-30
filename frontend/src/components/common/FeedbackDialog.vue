<script setup lang="ts">
import { ref } from 'vue'
import type { FeedbackCategory } from '../../types/footer'

defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: [payload: { category: FeedbackCategory; message: string }]
}>()

const categories: FeedbackCategory[] = ['버그 문의', '불편했던 점', '개선 제안']
const selectedCategory = ref<FeedbackCategory>('버그 문의')
const message = ref('')

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
</script>

<template>
  <Teleport to="body">
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
          ×
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
            @click="selectedCategory = category"
          >
            {{ category }}
          </button>
        </div>

        <textarea
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
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
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
}

.feedback-dialog__chip--active {
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
}

.feedback-dialog__submit:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
