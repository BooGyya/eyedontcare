<script setup lang="ts">
import type { GameRanking } from '../../types/pages'

defineProps<{
  ranking: GameRanking
}>()

function getRankLabel(rank: number) {
  return rank <= 3 ? ['🥇', '🥈', '🥉'][rank - 1] : rank
}
</script>

<template>
  <section class="ranking-list" :aria-label="`${ranking.gameName} 랭킹`">
    <div class="ranking-list__top">
      <div>
        <span>이번 주 {{ ranking.gameName }}</span>
        <h2>전체 랭킹</h2>
      </div>
      <small>기록 단위: {{ ranking.unit }}</small>
    </div>
    <ol>
      <li v-for="player in ranking.players" :key="player.rank">
        <strong :class="{ 'ranking-list__rank--medal': player.rank <= 3 }">
          {{ getRankLabel(player.rank) }}
        </strong>
        <img :src="player.avatar" alt="" />
        <span>{{ player.nickname }}</span>
        <b>{{ player.score }}</b>
      </li>
    </ol>
    <footer>
      <span
        >내 순위 <b>{{ ranking.myRank }}위</b></span
      >
      <strong>{{ ranking.myScore }}</strong>
    </footer>
  </section>
</template>

<style scoped>
.ranking-list {
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: #fff;
  box-shadow: var(--shadow-card);
}

.ranking-list__top {
  display: flex;
  align-items: end;
  justify-content: space-between;
  padding: 22px 23px 17px;
  border-bottom: 1px solid var(--color-line);
}

.ranking-list__top span,
.ranking-list__top small {
  color: var(--color-muted);
  font-size: 12px;
}

.ranking-list h2 {
  margin: 3px 0 0;
  font-size: 22px;
}

.ranking-list ol {
  margin: 0;
  padding: 5px 23px;
  list-style: none;
}

.ranking-list li {
  display: grid;
  grid-template-columns: 38px 40px 1fr auto;
  gap: 12px;
  align-items: center;
  min-height: 62px;
  border-bottom: 1px solid #f0f1f5;
}

.ranking-list li:last-child {
  border-bottom: 0;
}

.ranking-list li > strong {
  color: var(--color-muted);
  text-align: center;
}

.ranking-list__rank--medal {
  font-size: 19px;
}

.ranking-list li img {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--color-blue-soft);
}

.ranking-list li span {
  font-size: 14px;
  font-weight: 700;
}

.ranking-list li b {
  color: var(--color-accent-blue);
  font-size: 14px;
}

.ranking-list footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 23px;
  border-radius: 0 0 var(--radius-card) var(--radius-card);
  background: var(--color-blue-soft);
  color: var(--color-ink);
  font-size: 14px;
}

.ranking-list footer b,
.ranking-list footer > strong {
  color: var(--color-accent-blue);
}
</style>
