<script setup lang="ts">
defineProps<{
  title: string
  modeLabel: string
  timeLabel: string
  timeCaption?: string
  score: string
  showScore?: boolean
  showMetrics?: boolean
  roundProgress?: {
    current: number
    total: number
  }
}>()
defineEmits<{ leave: [] }>()
</script>

<template>
  <section class="play-shell">
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
      <div v-if="timeCaption" class="play-shell__elapsed" aria-live="polite">
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
        @click="$emit('leave')"
      >
        나가기
      </button>
    </header>
    <main class="play-shell__content"><slot /></main>
  </section>
</template>

<style scoped>
.play-shell {
  width: min(100%, 1180px);
  margin: 0 auto;
  padding: 28px 0 54px;
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
  justify-items: center;
  gap: 2px;
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
</style>
