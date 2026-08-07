<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  title: string
  modeLabel: string
  timeLabel: string
  timeCaption?: string
  showElapsed?: boolean
  score: string
  showScore?: boolean
  showMetrics?: boolean
  roundProgress?: {
    current: number
    total: number
  }
  /** 그림그리기처럼 넓은 작업 공간이 필요한 게임에서 켠다 — 기본 1180px 대신 더 넓게 쓴다. */
  wide?: boolean
}>()
const emit = defineEmits<{ leave: [] }>()

const isLeaveConfirmOpen = ref(false)

function confirmLeave() {
  isLeaveConfirmOpen.value = false
  emit('leave')
}
</script>

<template>
  <section class="play-shell" :class="{ 'play-shell--wide': wide }">
    <header
      class="play-shell__header"
      :class="{
        'play-shell__header--with-round': roundProgress,
        'play-shell__header--with-elapsed': timeCaption,
      }"
    >
      <div class="play-shell__title-row">
        <h1>{{ title }}</h1>
        <span class="play-shell__mode">{{ modeLabel }}</span>
      </div>
      <div
        v-if="timeCaption && showElapsed !== false"
        class="play-shell__elapsed"
        aria-live="polite"
      >
        <span>{{ timeCaption }}</span>
        <strong>{{ timeLabel }}</strong>
      </div>
      <div
        v-else-if="roundProgress"
        class="play-shell__round"
        aria-label="라운드 진행 상태"
      >
        <b>ROUND {{ roundProgress.current }} / {{ roundProgress.total }}</b>
        <span class="play-shell__round-steps" aria-hidden="true">
          <i
            v-for="round in roundProgress.total"
            :key="round"
            :class="{ active: round <= roundProgress.current }"
          />
        </span>
      </div>
      <div v-else-if="showMetrics !== false" class="play-shell__metrics">
        <dl>
          <div>
            <dt>남은 시간</dt>
            <dd>{{ timeLabel }}</dd>
          </div>
          <div v-if="showScore !== false">
            <dt>점수</dt>
            <dd>{{ score }}</dd>
          </div>
        </dl>
      </div>
      <button
        class="play-shell__leave"
        type="button"
        aria-label="게임 나가기"
        @click="isLeaveConfirmOpen = true"
      >
        나가기
      </button>
    </header>
    <main class="play-shell__content"><slot /></main>
    <div
      v-if="isLeaveConfirmOpen"
      class="play-shell__confirm"
      @keydown.esc="isLeaveConfirmOpen = false"
    >
      <section
        class="play-shell__confirm-card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="leave-confirm-title"
      >
        <h2 id="leave-confirm-title">게임에서 나가시겠어요?</h2>
        <p>
          지금 나가면 중도 이탈로 처리되며<br />이번 경기는 몰수패로 기록됩니다.
        </p>
        <div class="play-shell__confirm-actions">
          <button type="button" @click="isLeaveConfirmOpen = false">
            계속하기
          </button>
          <button
            class="play-shell__confirm-leave"
            type="button"
            @click="confirmLeave"
          >
            나가기
          </button>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.play-shell {
  width: min(100%, 1180px);
  margin: 0 auto;
  padding: 28px 0 54px;
}
/**
 * 그림그리기처럼 넓은 작업 공간이 필요한 게임용. 캔버스가 작아서 눈으로 그리기 어렵다는
 * 피드백을 반영해, 화면이 허용하는 만큼(좌우 여백만 남기고) 폭을 넓힌다.
 */
.play-shell--wide {
  width: min(calc(100vw - 48px), 1680px);
}
.play-shell__header {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 28px;
  min-height: 78px;
  padding: 0 8px 20px;
  border: 0;
  border-bottom: 1px solid #eef0f6;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}
.play-shell__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.play-shell__mode {
  display: inline-block;
  padding: 6px 11px;
  border: 0;
  border-radius: 999px;
  color: var(--color-accent-blue);
  background: #f0efff;
  font-size: 12px;
  font-weight: 800;
}
.play-shell__header h1 {
  margin: 0;
  color: var(--color-ink);
  font-family: var(--font-display);
  font-size: 34px;
  letter-spacing: -0.04em;
}
.play-shell__round {
  position: absolute;
  top: 50%;
  left: 50%;
  display: grid;
  justify-items: center;
  gap: 9px;
  transform: translate(-50%, -50%);
}
.play-shell__round > b {
  padding: 8px 16px;
  border-radius: 999px;
  color: #5144e8;
  background: #f0efff;
  font-size: 14px;
}
.play-shell__round-steps {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0;
}
.play-shell__round-steps::before {
  position: absolute;
  width: 94px;
  height: 3px;
  border-radius: 99px;
  background: #d9d5ff;
  content: '';
}
.play-shell__round-steps i {
  position: relative;
  z-index: 1;
  display: block;
  width: 13px;
  height: 13px;
  margin: 0 17px;
  border-radius: 50%;
  background: #cdc8fa;
  transition:
    background-color var(--duration-fast) ease,
    box-shadow var(--duration-fast) ease;
}
.play-shell__round-steps i.active {
  background: #4f40ea;
  box-shadow: 0 0 0 4px #f0efff;
}
.play-shell__metrics {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}
.play-shell__elapsed {
  position: absolute;
  top: 50%;
  left: 50%;
  display: grid;
  min-width: 150px;
  justify-items: center;
  gap: 2px;
  padding: 8px 16px;
  border: 1px solid #dedbff;
  border-radius: 14px;
  background: #fbfaff;
  box-shadow: 0 7px 18px rgba(79, 68, 232, 0.1);
  transform: translate(-50%, -50%);
}
.play-shell__elapsed span {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 700;
}
.play-shell__elapsed strong {
  color: #5144e8;
  font-size: 27px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 0.01em;
  font-variant-numeric: tabular-nums;
}
.play-shell__header dl {
  display: flex;
  gap: 24px;
  margin: 0;
}
.play-shell__header dl div {
  min-width: 62px;
  text-align: center;
}
.play-shell__header dt {
  color: var(--color-muted);
  font-size: 12px;
}
.play-shell__header dd {
  margin: 4px 0 0;
  color: var(--color-ink);
  font-family: inherit;
  font-size: 25px;
  font-weight: 900;
  font-variant-numeric: tabular-nums;
}
.play-shell__header button {
  justify-self: end;
  min-height: 38px;
  padding: 0 13px;
  border: 1px solid #dfe3ee;
  border-radius: 10px;
  color: var(--color-ink);
  background: #fffef9;
  font-weight: 800;
  cursor: pointer;
  transition:
    transform var(--duration-fast) ease,
    border-color var(--duration-fast) ease,
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease;
}
.play-shell__header button:hover {
  border-color: #bfc7e9;
  color: var(--color-accent-blue);
  background: #f8f8ff;
}
.play-shell__header button:active {
  transform: translateY(1px);
  box-shadow: none;
}
.play-shell__content {
  margin-top: 18px;
}
@media (max-width: 720px) {
  .play-shell {
    padding: 18px 0 38px;
  }
  .play-shell__header {
    display: flex;
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .play-shell__header--with-round .play-shell__round {
    position: static;
    order: 3;
    width: 100%;
    margin-top: 3px;
    transform: none;
  }
  .play-shell__title-row {
    flex-wrap: wrap;
  }
  .play-shell__header h1 {
    font-size: 27px;
  }
  .play-shell__metrics {
    position: static;
    order: 3;
    width: 100%;
    margin-top: 4px;
    transform: none;
  }
  .play-shell__elapsed {
    position: static;
    order: 3;
    width: 100%;
    margin-top: 4px;
    transform: none;
  }
  .play-shell__leave {
    margin-left: auto;
  }
  .play-shell__header dl {
    gap: 11px;
  }
  .play-shell__header dd {
    font-size: 19px;
  }
  .play-shell__header button {
    margin-left: auto;
  }
}
.play-shell__confirm {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(23, 36, 61, 0.45);
}
.play-shell__confirm-card {
  display: grid;
  gap: 14px;
  width: min(400px, 100%);
  padding: 28px;
  border-radius: var(--radius-card, 18px);
  background: #fff;
  box-shadow: var(--shadow-float);
  text-align: center;
}
.play-shell__confirm-card h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: 20px;
}
.play-shell__confirm-card p {
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.6;
  word-break: keep-all;
}
.play-shell__confirm-actions {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}
.play-shell__confirm-actions button {
  flex: 1;
  min-height: 44px;
  padding: 0 13px;
  border: 1px solid #dfe3ee;
  border-radius: 10px;
  color: var(--color-ink);
  background: #fff;
  font-weight: 800;
  cursor: pointer;
  transition:
    transform var(--duration-fast) ease,
    border-color var(--duration-fast) ease,
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease;
}
.play-shell__confirm-actions button:hover {
  border-color: #bfc7e9;
  color: var(--color-accent-blue);
  background: #f8f8ff;
}
.play-shell__confirm-actions button:active {
  transform: translateY(1px);
}
.play-shell__confirm-leave {
  border: 0;
  color: #fff;
  background: var(--color-danger, #c2455a);
}
.play-shell__confirm-leave:hover {
  border-color: transparent;
  color: #fff;
  background: color-mix(in srgb, var(--color-danger, #c2455a) 82%, black);
}
</style>
