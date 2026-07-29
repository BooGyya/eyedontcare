<script setup lang="ts">
import { computed } from 'vue'
import type { RankingRecord, WeeklyRankingGame } from '../../types/home'

const props = defineProps<{
  game: WeeklyRankingGame
}>()

const highestValue = computed(() =>
  Math.max(...props.game.records.map((record) => record.value)),
)

function getPodiumHeight(record: RankingRecord) {
  const scoreRatio = record.value / highestValue.value
  const rankRange = {
    1: { minimum: 50, maximum: 56 },
    2: { minimum: 37, maximum: 43 },
    3: { minimum: 26, maximum: 32 },
  }[record.rank] ?? { minimum: 22, maximum: 28 }
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
      <div>
        <h3>{{ game.title }}</h3>
      </div>
    </header>

    <div
      class="weekly-ranking-card__chart"
      :aria-label="`${game.title} 이번 주 상위 3명 포디움`"
    >
      <div
        v-for="record in game.records"
        :key="record.rank"
        class="weekly-ranking-card__record"
        :class="`weekly-ranking-card__record--${record.rank}`"
      >
        <div
          class="weekly-ranking-card__podium"
          :style="{ '--podium-height': getPodiumHeight(record) }"
        >
          <div class="weekly-ranking-card__player">
            <span
              class="weekly-ranking-card__medal"
              :class="`weekly-ranking-card__medal--${record.rank}`"
              >{{ record.rank }}</span
            >
            <img
              class="weekly-ranking-card__avatar"
              :src="record.avatar"
              :alt="`${record.nickname} 프로필`"
              draggable="false"
            />
            <strong class="weekly-ranking-card__nickname">{{
              record.nickname
            }}</strong>
          </div>
          <i class="weekly-ranking-card__bar" />
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.weekly-ranking-card {
  display: grid;
  min-width: 0;
  min-height: 334px;
  grid-template-rows: auto 1fr;
  padding: 18px;
  border: 1px solid var(--card-line, #dadbf9);
  border-radius: 18px;
  background: linear-gradient(145deg, var(--card-background, #fbfbff), #fff);
  box-shadow: 0 7px 19px rgba(39, 51, 93, 0.05);
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

.weekly-ranking-card__header div {
  display: grid;
  min-width: 0;
  align-items: center;
}

.weekly-ranking-card__header h3 {
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 16px;
  letter-spacing: -0.02em;
}

.weekly-ranking-card__chart {
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
  align-items: end;
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

.weekly-ranking-card__bar {
  position: absolute;
  bottom: 0;
  left: 16%;
  display: block;
  width: 68%;
  height: var(--podium-height);
  border-radius: 7px 7px 0 0;
  background: var(--bar-color);
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
