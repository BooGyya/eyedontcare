<script setup lang="ts">
import { computed } from 'vue'
import type { RankingRecord, WeeklyRankingGame } from '../../types/home'

const props = defineProps<{
  game: WeeklyRankingGame
}>()

const highestValue = computed(() =>
  Math.max(...props.game.records.map((record) => record.value)),
)

function getBarHeight(record: RankingRecord) {
  return `${Math.max(28, (record.value / highestValue.value) * 100)}%`
}
</script>

<template>
  <article
    class="weekly-ranking-card"
    :class="`weekly-ranking-card--${game.tone}`"
  >
    <header class="weekly-ranking-card__header">
      <img :src="game.image" :alt="`${game.title} 캐릭터`" />
      <div>
        <h3>{{ game.title }}</h3>
        <span>{{ game.mode }}</span>
      </div>
    </header>

    <div
      class="weekly-ranking-card__chart"
      :aria-label="`${game.title} 이번 주 상위 3명 기록`"
    >
      <div
        v-for="record in game.records"
        :key="record.rank"
        class="weekly-ranking-card__record"
      >
        <span
          class="weekly-ranking-card__medal"
          :class="`weekly-ranking-card__medal--${record.rank}`"
          >{{ record.rank }}</span
        >
        <strong
          >{{ record.label }}<small>{{ game.unit }}</small></strong
        >
        <div class="weekly-ranking-card__bar-track">
          <i :style="{ height: getBarHeight(record) }" />
        </div>
      </div>
    </div>

    <footer class="weekly-ranking-card__actions">
      <RouterLink to="/ranking">내 순위 {{ game.myRank }}위</RouterLink>
    </footer>
  </article>
</template>

<style scoped>
.weekly-ranking-card {
  display: grid;
  min-width: 0;
  min-height: 288px;
  grid-template-rows: auto 1fr auto;
  padding: 18px 18px 14px;
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
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.weekly-ranking-card__header h3 {
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 16px;
  letter-spacing: -0.05em;
}

.weekly-ranking-card__header span {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 999px;
  color: var(--button-color);
  background: rgba(255, 255, 255, 0.74);
  font-size: 11px;
  font-weight: 800;
}

.weekly-ranking-card__chart {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  min-height: 142px;
  align-items: end;
  padding: 10px 8px 0;
}

.weekly-ranking-card__record {
  display: grid;
  min-width: 0;
  grid-template-rows: auto auto 1fr;
  justify-items: center;
  gap: 2px;
  height: 100%;
  text-align: center;
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
  background: #f3b515;
}

.weekly-ranking-card__medal--3 {
  background: #d87b2b;
}

.weekly-ranking-card__record strong {
  color: var(--color-ink);
  font-size: 14px;
  line-height: 1.1;
}

.weekly-ranking-card__record small {
  display: block;
  color: var(--color-muted);
  font-size: 10px;
}

.weekly-ranking-card__bar-track {
  display: flex;
  width: 100%;
  min-height: 70px;
  align-items: end;
  border-bottom: 1px solid rgba(104, 112, 139, 0.15);
}

.weekly-ranking-card__bar-track i {
  display: block;
  width: 100%;
  border-radius: 6px 6px 0 0;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--bar-color), white 10%),
    var(--bar-color)
  );
}

.weekly-ranking-card__actions {
  margin-top: 13px;
}

.weekly-ranking-card__actions a {
  display: grid;
  width: 100%;
  min-height: 33px;
  place-items: center;
  padding: 6px;
  border: 1px solid transparent;
  border-radius: 9px;
  color: var(--button-color);
  background: rgba(255, 255, 255, 0.54);
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}
</style>
