<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import type { RankingRecord, WeeklyRankingGame } from '../../types/home'

const props = defineProps<{
  game: WeeklyRankingGame
}>()

const isReady = ref(false)

onMounted(() => {
  void nextTick(() => {
    globalThis.requestAnimationFrame(() => {
      isReady.value = true
    })
  })
})

const highestValue = computed(() =>
  props.game.records.length
    ? Math.max(...props.game.records.map((record) => record.value))
    : 0,
)

type PodiumSlot = { rank: number; record: RankingRecord | null }

const podiumSlots = computed<PodiumSlot[]>(() =>
  [1, 2, 3].map((rank) => ({
    rank,
    record: props.game.records.find((entry) => entry.rank === rank) ?? null,
  })),
)

function getPodiumHeight(rank: number, record: RankingRecord | null) {
  const scoreRatio =
    record && highestValue.value ? record.value / highestValue.value : 0
  const rankRange = {
    1: { minimum: 50, maximum: 56 },
    2: { minimum: 37, maximum: 43 },
    3: { minimum: 26, maximum: 32 },
  }[rank] ?? { minimum: 22, maximum: 28 }
  const height =
    rankRange.minimum + scoreRatio * (rankRange.maximum - rankRange.minimum)
  return `${height}%`
}
</script>

<template>
  <article
    class="weekly-ranking-card"
    :class="`weekly-ranking-card--${game.tone}`"
  >
    <header class="weekly-ranking-card__header">
      <img :src="game.image" :alt="`${game.title} 캐릭터`" draggable="false" />
      <h3>{{ game.title }}</h3>
    </header>

    <div
      class="weekly-ranking-card__chart"
      :aria-label="`${game.title} 이번 주 상위 3명 포디움`"
    >
      <div
        v-for="(slot, index) in podiumSlots"
        :key="slot.rank"
        class="weekly-ranking-card__record"
        :class="`weekly-ranking-card__record--${slot.rank}`"
      >
        <div
          class="weekly-ranking-card__podium"
          :style="{
            '--podium-height': getPodiumHeight(slot.rank, slot.record),
          }"
        >
          <div class="weekly-ranking-card__player">
            <span
              class="weekly-ranking-card__medal"
              :class="`weekly-ranking-card__medal--${slot.rank}`"
              >{{ slot.rank }}</span
            >
            <img
              v-if="slot.record"
              class="weekly-ranking-card__avatar"
              :src="slot.record.avatar"
              :alt="`${slot.record.nickname} 프로필`"
              draggable="false"
            />
            <span
              v-else
              class="weekly-ranking-card__avatar weekly-ranking-card__avatar--placeholder"
              aria-hidden="true"
            />
            <strong
              class="weekly-ranking-card__nickname"
              :class="{
                'weekly-ranking-card__nickname--placeholder': !slot.record,
              }"
              >{{ slot.record ? slot.record.nickname : '순위 없음' }}</strong
            >
          </div>
          <i
            class="weekly-ranking-card__bar"
            :class="{
              'is-ready': isReady,
              'weekly-ranking-card__bar--placeholder': !slot.record,
            }"
            :style="{ transitionDelay: `${index * 0.08}s` }"
          />
        </div>
      </div>
      <p v-if="!game.records.length" class="weekly-ranking-card__empty-overlay">
        <span class="weekly-ranking-card__empty-card">
          아직 이번주 랭커가 없어요.<br />참여해서 랭커가 되어보세요!
        </span>
      </p>
    </div>
  </article>
</template>

<style scoped>
.weekly-ranking-card {
  display: grid;
  min-width: 0;
  min-height: 334px;
  grid-template-rows: auto 1fr auto;
  padding: 18px;
  border: 1px solid var(--card-line, #dadbf9);
  border-radius: 18px;
  background: var(--card-background, #fbfbff);
  box-shadow: 0 7px 19px rgba(39, 51, 93, 0.05);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease;
}

.weekly-ranking-card:hover {
  box-shadow: var(--shadow-float);
  transform: translateY(-4px);
}

.weekly-ranking-card--purple {
  --card-line: #ddd5ff;
  --card-background: #fcfaff;
  --bar-color: #7d5ae8;
  --button-color: #6345d7;
}

.weekly-ranking-card--mint {
  --card-line: #c6eadf;
  --card-background: #f5fffb;
  --bar-color: #4cc69d;
  --button-color: #23876a;
}

.weekly-ranking-card--blue {
  --card-line: #d4dffd;
  --card-background: #f8faff;
  --bar-color: #5b82ed;
  --button-color: #315dce;
}

.weekly-ranking-card--orange {
  --card-line: #fae3c6;
  --card-background: #fffaf4;
  --bar-color: #ffa548;
  --button-color: #ae6822;
}

.weekly-ranking-card--sky {
  --card-line: #c9e7f3;
  --card-background: #f4fcff;
  --bar-color: #45a9d0;
  --button-color: #237b9d;
}

.weekly-ranking-card__header {
  display: flex;
  align-items: center;
  gap: 11px;
}

.weekly-ranking-card__header img {
  width: 76px;
  height: 58px;
  object-fit: contain;
}

.weekly-ranking-card__header h3 {
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 16px;
  letter-spacing: -0.02em;
}

.weekly-ranking-card__chart {
  position: relative;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  min-height: 238px;
  align-items: end;
  padding: 12px 5px 0;
}

.weekly-ranking-card__record {
  display: flex;
  min-width: 0;
  height: 100%;
  grid-row: 1;
  align-items: end;
}

.weekly-ranking-card__record--1 {
  grid-column: 2;
}

.weekly-ranking-card__record--2 {
  grid-column: 1;
}

.weekly-ranking-card__record--3 {
  grid-column: 3;
}

.weekly-ranking-card__empty-overlay {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: grid;
  margin: 0;
  padding: 0 16px;
  place-items: center;
}

.weekly-ranking-card__empty-card {
  display: block;
  padding: 14px 18px;
  border: 1px solid var(--card-line, #dadbf9);
  border-radius: 14px;
  background: #fff;
  box-shadow: var(--shadow-card);
  color: var(--color-ink);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.6;
  text-align: center;
  word-break: keep-all;
}

.weekly-ranking-card__podium {
  position: relative;
  width: 100%;
  height: 100%;
}

.weekly-ranking-card__player {
  position: absolute;
  bottom: calc(var(--podium-height) + 12px);
  left: 0;
  z-index: 1;
  display: grid;
  width: 100%;
  justify-items: center;
  gap: 3px;
}

.weekly-ranking-card__avatar {
  width: 42px;
  height: 42px;
  border: 3px solid #fff;
  border-radius: 50%;
  object-fit: cover;
  background: var(--card-background);
}

.weekly-ranking-card__avatar--placeholder {
  display: block;
  border-color: #fff;
  background: #e3e5ee;
}

.weekly-ranking-card__medal {
  display: grid;
  width: 23px;
  height: 23px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: #9da0a7;
  font-size: 12px;
  font-weight: 900;
}

.weekly-ranking-card__medal--1 {
  color: #614600;
  background: #f3c64f;
  box-shadow: inset 0 0 0 2px #dca819;
}

.weekly-ranking-card__medal--2 {
  color: #46505d;
  background: #d9dee6;
  box-shadow: inset 0 0 0 2px #aeb6c2;
}

.weekly-ranking-card__medal--3 {
  color: #fff;
  background: #c77b43;
  box-shadow: inset 0 0 0 2px #a65e2d;
}

.weekly-ranking-card__nickname {
  display: block;
  width: 100%;
  overflow: hidden;
  color: var(--color-ink);
  font-size: 12px;
  line-height: 1.3;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.weekly-ranking-card__nickname--placeholder {
  color: var(--color-muted);
}

.weekly-ranking-card__bar {
  position: absolute;
  bottom: 0;
  left: 16%;
  display: block;
  width: 68%;
  height: var(--podium-height);
  border-radius: 7px 7px 0 0;
  background: var(--bar-color);
  transform: scaleY(0);
  transform-origin: bottom;
  transition: transform 0.7s var(--ease-out);
}

.weekly-ranking-card__bar--placeholder {
  background: #e3e5ee;
}

.weekly-ranking-card__bar.is-ready {
  transform: scaleY(1);
}

.weekly-ranking-card__record--1 .weekly-ranking-card__avatar {
  width: 48px;
  height: 48px;
}

.weekly-ranking-card__record--2 .weekly-ranking-card__bar {
  opacity: 0.68;
}

.weekly-ranking-card__record--3 .weekly-ranking-card__bar {
  opacity: 0.38;
}
</style>
