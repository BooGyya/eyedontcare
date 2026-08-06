<script setup lang="ts">
// 카운트다운은 사용자가 닫을 수 없다 — 백드롭 클릭 등으로 닫히면
// 시작 타이머까지 취소돼 게임이 시작되지 않는 문제가 있었다.
withDefaults(
  defineProps<{
    open: boolean
    countdown: number
    title?: string
    countdownLabel?: string
  }>(),
  {
    title: '게임이 시작됩니다',
    countdownLabel: '게임 시작 예정 카운트다운',
  },
)
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog-pop">
      <div v-if="open" class="game-start-countdown-backdrop">
        <section
          class="game-start-countdown-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="game-start-countdown-title"
          aria-describedby="game-start-countdown-description"
          tabindex="-1"
        >
          <span class="game-start-countdown-dialog__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <path
                d="M5 13l4 4L19 7"
                fill="none"
                stroke="currentColor"
                stroke-width="2.4"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </span>
          <h2 id="game-start-countdown-title">{{ title }}</h2>
          <p id="game-start-countdown-description">
            <slot name="description">
              게임 준비가 완료되었습니다.<br />
              카운트다운이 끝나면 게임을 시작할 예정이에요.
            </slot>
          </p>
          <div
            class="game-start-countdown"
            :aria-label="countdownLabel"
            aria-live="assertive"
          >
            <Transition name="count-tick">
              <b :key="countdown">{{ countdown }}</b>
            </Transition>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.game-start-countdown-backdrop {
  position: fixed;
  z-index: 80;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(13, 26, 56, 0.52);
}

.game-start-countdown-dialog {
  width: min(100%, 470px);
  max-height: min(760px, calc(100vh - 40px));
  overflow: auto;
  box-sizing: border-box;
  padding: 28px;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 20px 60px rgba(9, 23, 55, 0.26);
  text-align: center;
}

.game-start-countdown-dialog__icon {
  display: grid;
  width: 42px;
  height: 42px;
  margin: 0 auto;
  place-items: center;
  border-radius: 50%;
  color: #278957;
  background: #e6f7eb;
}

.game-start-countdown-dialog__icon svg {
  width: 22px;
  height: 22px;
}

.game-start-countdown-dialog h2 {
  margin: 14px 0 8px;
  color: var(--color-ink);
  font-size: 24px;
}

.game-start-countdown-dialog p {
  margin: 0;
  color: var(--color-muted);
  line-height: 1.65;
}

.game-start-countdown {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
  min-height: 82px;
  margin: 22px 0;
}

.game-start-countdown b {
  position: absolute;
  display: grid;
  width: 82px;
  height: 82px;
  place-items: center;
  border-radius: 50%;
  color: var(--color-accent-blue);
  background: var(--color-blue-soft);
  font-size: 42px;
  animation: game-start-count-ring 1s ease-out infinite;
}

@keyframes game-start-count-ring {
  from {
    box-shadow: 0 0 0 0 rgba(79, 116, 219, 0.35);
  }
  to {
    box-shadow: 0 0 0 18px rgba(79, 116, 219, 0);
  }
}

.count-tick-enter-active {
  transition:
    transform 140ms var(--ease-out),
    opacity 140ms var(--ease-out);
}

.count-tick-leave-active {
  transition:
    transform 140ms ease,
    opacity 140ms ease;
}

.count-tick-enter-from {
  opacity: 0;
  transform: scale(0.85);
}

.count-tick-leave-to {
  opacity: 0;
  transform: scale(1.15);
}

.dialog-pop-enter-active,
.dialog-pop-leave-active {
  transition: background-color 200ms ease;
}

.dialog-pop-enter-active .game-start-countdown-dialog,
.dialog-pop-leave-active .game-start-countdown-dialog {
  transition:
    transform 240ms var(--ease-out),
    opacity 240ms var(--ease-out);
}

.dialog-pop-enter-from,
.dialog-pop-leave-to {
  background-color: rgba(13, 26, 56, 0);
}

.dialog-pop-enter-from .game-start-countdown-dialog,
.dialog-pop-leave-to .game-start-countdown-dialog {
  opacity: 0;
  transform: scale(0.96) translateY(8px);
}
</style>
