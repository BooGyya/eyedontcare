<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import type { PolicyDocument } from '../../types/footer'

const props = defineProps<{
  document: PolicyDocument | null
}>()

const emit = defineEmits<{
  close: []
  'open-feedback': []
}>()

let previousBodyOverflow = ''

function handleBackdropClick(event: {
  target: unknown
  currentTarget: unknown
}) {
  if (event.target === event.currentTarget) {
    emit('close')
  }
}

function handleKeydown(event: globalThis.KeyboardEvent) {
  if (event.key === 'Escape') emit('close')
}

watch(
  () => props.document,
  (document) => {
    if (document) {
      previousBodyOverflow = globalThis.document.body.style.overflow
      globalThis.document.body.style.overflow = 'hidden'
      globalThis.addEventListener('keydown', handleKeydown)
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
        v-if="document"
        class="policy-dialog-backdrop"
        @click="handleBackdropClick"
      >
        <section
          class="policy-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="policy-dialog-title"
        >
          <button
            class="policy-dialog__close"
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

          <h2 id="policy-dialog-title">{{ document.title }}</h2>

          <div class="policy-dialog__sections">
            <section
              v-for="section in document.sections"
              :key="section.heading"
              class="policy-dialog__section"
            >
              <h3>{{ section.heading }}</h3>
              <p v-for="paragraph in section.paragraphs" :key="paragraph">
                {{ paragraph }}
              </p>
              <ul v-if="section.bullets">
                <li v-for="bullet in section.bullets" :key="bullet">
                  {{ bullet }}
                </li>
              </ul>
            </section>
          </div>

          <button
            v-if="document.id === 'support'"
            class="policy-dialog__feedback"
            type="button"
            @click="emit('open-feedback')"
          >
            피드백 보내기
          </button>

          <button
            class="policy-dialog__confirm"
            type="button"
            @click="emit('close')"
          >
            확인
          </button>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.policy-dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: 16px;
  background: rgba(23, 36, 61, 0.45);
}

.policy-dialog {
  position: relative;
  width: min(560px, calc(100% - 32px));
  max-height: 82vh;
  overflow-y: auto;
  padding: 30px 28px 24px;
  border-radius: 22px;
  background: #fff;
  box-shadow: var(--shadow-float);
}

.policy-dialog__close {
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
.policy-dialog__close svg {
  width: 18px;
  height: 18px;
}
.policy-dialog__close:hover {
  background: var(--color-blue-soft);
}

.policy-dialog h2 {
  margin: 0 32px 18px 0;
  color: var(--color-ink);
  font-size: 22px;
}

.policy-dialog__sections {
  display: grid;
  gap: 26px;
}

.policy-dialog__section h3 {
  margin: 0 0 10px;
  color: var(--color-ink);
  font-size: 16px;
}

.policy-dialog__section p {
  margin: 0 0 6px;
  color: var(--color-ink);
  font-size: 14px;
  line-height: 1.7;
}

.policy-dialog__section ul {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.policy-dialog__section li {
  position: relative;
  padding-left: 16px;
  color: var(--color-ink);
  font-size: 14px;
  line-height: 1.7;
}

.policy-dialog__section li::before {
  position: absolute;
  top: 9px;
  left: 2px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--color-accent-blue);
  content: '';
}

.policy-dialog__feedback {
  width: 100%;
  margin-top: 22px;
  padding: 13px;
  border: 1px solid var(--color-accent-blue);
  border-radius: 12px;
  color: var(--color-accent-blue);
  background: transparent;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease;
}
.policy-dialog__feedback:hover {
  color: #fff;
  background: var(--color-accent-blue);
}

.policy-dialog__confirm {
  width: 100%;
  margin-top: 10px;
  padding: 14px;
  border-radius: 12px;
  color: #fff;
  background: var(--color-accent-blue);
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.policy-dialog__confirm:hover {
  background: color-mix(in srgb, var(--color-accent-blue) 85%, black);
}
.dialog-pop-enter-active,
.dialog-pop-leave-active {
  transition: opacity 200ms ease;
}
.dialog-pop-enter-from,
.dialog-pop-leave-to {
  opacity: 0;
}
.dialog-pop-enter-active .policy-dialog,
.dialog-pop-leave-active .policy-dialog {
  transition:
    transform 240ms var(--ease-out),
    opacity 240ms var(--ease-out);
}
.dialog-pop-enter-from .policy-dialog,
.dialog-pop-leave-to .policy-dialog {
  opacity: 0;
  transform: scale(0.96) translateY(8px);
}
</style>
