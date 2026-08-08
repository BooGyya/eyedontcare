<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps<{
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

/**
 * 넓은 게임 화면임을 `body`에 알린다.
 *
 * 캔버스 크기를 실제로 깎고 있는 두 가지 — 사이트 공통 레이아웃 폭 제한(`--content-width`,
 * 1408px)과 화면 하단 푸터 — 는 모두 이 컴포넌트 **바깥**에 있어서 scoped 스타일로는 닿지
 * 않는다. body 클래스를 걸어 아래 `:global` 규칙이 이 화면에서만 두 제한을 풀게 한다.
 */
const WIDE_BODY_CLASS = 'is-play-wide'

function syncWideBodyClass(isWide: boolean | undefined) {
  const body = globalThis.document?.body
  if (!body) return
  body.classList.toggle(WIDE_BODY_CLASS, Boolean(isWide))
}

watch(() => props.wide, syncWideBodyClass, { immediate: true })
// 게임을 떠나면 반드시 되돌린다 — 남겨 두면 다른 페이지까지 넓어지고 푸터가 사라진다.
onBeforeUnmount(() => syncWideBodyClass(false))

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
 * 피드백을 반영해 폭을 넓힌다.
 *
 * ⚠️ `100vw`를 쓰면 안 된다. 이 셸은 `--layout-inline`(최대 1408px)로 제한된
 * `.app-layout__content` 안에 있는데, `100vw`는 그 컨테이너가 아니라 뷰포트 기준이라
 * 컨테이너보다 넓어진다. 넓은 자식은 `margin: 0 auto`가 0으로 풀려 오른쪽으로 삐져나가고
 * 가로 스크롤바가 생긴다(1920px 화면에서 실측 272px 초과). 게다가 `100vw`는 세로 스크롤바
 * 폭까지 포함해 초과분이 더 커진다. 컨테이너 기준인 `100%`를 써야 한다.
 */
.play-shell--wide {
  width: min(100%, 1680px);
  /**
   * 한 화면에 담으면서도 캔버스를 최대한 키우기 위해 세로 여백을 걷어낸다. 이 화면에서 세로
   * 공간은 곧 캔버스 크기다 — 캔버스는 높이에 맞춰 비율대로 커지므로, 여기서 아낀 1px이
   * 캔버스 높이 1px과 폭 1.56px로 그대로 돌아온다.
   */
  padding-block: 10px;
}
.play-shell--wide .play-shell__header {
  min-height: 0;
  padding-bottom: 10px;
}
/**
 * 넓은 게임 화면에서만 사이트 공통 제약을 푼다(`body.is-play-wide`는 이 컴포넌트가 붙였다 뗀다).
 *
 * 1. 레이아웃 폭 — 기본 `--content-width`(1408px)가 캔버스 폭의 실질 상한이었다.
 * 2. 푸터 — 게임 중에는 쓰이지 않으면서 세로 101px을 가져간다. 캔버스는 높이에 맞춰 커지므로
 *    이 101px이 캔버스 높이 101px과 폭 158px로 그대로 돌아온다.
 *
 * ⚠️ 폭은 반드시 컨테이너 기준(%)으로 계산한다. `vw`는 세로 스크롤바 폭을 포함해서, 스크롤바가
 * 생기는 순간 다시 가로 넘침을 만든다(이 파일 위쪽 주석 참고).
 */
/* ⚠️ 선택자 전체를 하나의 :global()로 감싼다. `:global(a) :global(b)`로 나눠 쓰면 후손 결합이
   사라지고 첫 부분만 남은 규칙(`body.is-play-wide { display: none }`)이 되어 화면 전체가 사라진다. */
:global(body.is-play-wide .app-layout__content) {
  width: calc(100% - 96px);
}
:global(body.is-play-wide .app-layout > footer) {
  display: none;
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
