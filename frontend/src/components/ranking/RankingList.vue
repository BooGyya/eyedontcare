<script setup lang="ts">
import { computed } from 'vue'
import type { GameRanking, RankingPlayer } from '../../types/pages'

const props = defineProps<{
  ranking: GameRanking
}>()

const podiumPlayers = computed(() => {
  const podiumOrder = [2, 1, 3]
  return podiumOrder
    .map((rank) => props.ranking.players.find((player) => player.rank === rank))
    .filter((player): player is RankingPlayer => player !== undefined)
})

const generalPlayers = computed(() => {
  return props.ranking.players.filter(
    (player) => player.rank > 3 && player.rank <= 10,
  )
})

const hasRankingData = computed(() => props.ranking.players.length > 0)
const currentUser = computed(() => {
  return props.ranking.players.find((player) => player.isCurrentUser)
})
const isCurrentUserInTopTen = computed(() => props.ranking.myRank <= 10)

function getTrendLabel(trend?: RankingPlayer['trend']) {
  return trend === 'up' ? '▲ 상승' : trend === 'down' ? '▼ 하락' : '— 유지'
}
</script>

<template>
  <section class="ranking-list" :aria-label="`${ranking.gameName} 랭킹`">
    <header class="ranking-list__header">
      <div>
        <span class="ranking-list__eyebrow">
          {{
            ranking.gameId === 'all' ? 'THIS WEEK OVERALL' : 'THIS WEEK GAME'
          }}
        </span>
        <h2>{{ ranking.gameName }}</h2>
      </div>
      <p>
        <span
          >참여자
          <b>{{ ranking.totalPlayers ?? ranking.players.length }}명</b></span
        >
        <span
          >기록 단위 <b data-testid="ranking-unit">{{ ranking.unit }}</b></span
        >
      </p>
    </header>

    <template v-if="hasRankingData">
      <section class="ranking-list__podium" aria-label="상위 3명">
        <article
          v-for="player in podiumPlayers"
          :key="player.rank"
          :class="`ranking-list__podium-card--${player.rank}`"
          class="ranking-list__podium-card"
          :data-testid="`podium-rank-${player.rank}`"
        >
          <span class="ranking-list__crown" aria-hidden="true">{{
            player.rank === 1 ? '♛' : ''
          }}</span>
          <img :src="player.avatar" :alt="`${player.nickname} 프로필`" />
          <strong>{{ player.nickname }}</strong>
          <small>{{ player.score }}</small>
          <b aria-label="순위">{{ player.rank }}위</b>
        </article>
      </section>

      <section class="ranking-list__table" aria-label="일반 랭킹 목록">
        <div class="ranking-list__table-head" aria-hidden="true">
          <span>순위</span><span>플레이어</span><span>기록</span
          ><span data-testid="ranking-record-heading">{{
            ranking.gameId === 'all' ? '점수' : '최고 기록'
          }}</span
          ><span>변화</span>
        </div>
        <ol>
          <li
            v-for="player in generalPlayers"
            :key="player.rank"
            :class="{ 'ranking-list__row--current-user': player.isCurrentUser }"
            :data-testid="`ranking-row-${player.rank}`"
          >
            <span class="ranking-list__rank">{{ player.rank }}</span>
            <span class="ranking-list__player">
              <img :src="player.avatar" alt="" />
              <span
                ><b>{{ player.nickname }}</b
                ><small v-if="player.level">{{ player.level }}</small></span
              >
            </span>
            <span class="ranking-list__record">{{
              player.record ?? ranking.gameName
            }}</span>
            <strong class="ranking-list__score">{{ player.score }}</strong>
            <span
              :class="`ranking-list__trend--${player.trend ?? 'same'}`"
              class="ranking-list__trend"
            >
              {{ getTrendLabel(player.trend) }}
            </span>
          </li>
        </ol>
      </section>

      <footer
        v-if="!isCurrentUserInTopTen"
        class="ranking-list__my-rank"
        data-testid="ranking-current-user"
      >
        <div>
          <span>나의 현재 순위</span>
          <strong>{{ currentUser?.rank ?? ranking.myRank }}위</strong>
        </div>
        <div>
          <span>내 기록</span>
          <b>{{ currentUser?.score ?? ranking.myScore }}</b>
        </div>
      </footer>
    </template>

    <div
      v-else
      class="ranking-list__empty"
      data-testid="ranking-empty"
      role="status"
    >
      <strong>아직 등록된 랭킹이 없어요.</strong>
      <span>첫 번째 기록을 남겨 보세요.</span>
    </div>
  </section>
</template>

<style scoped>
.ranking-list {
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: #fff;
  box-shadow: var(--shadow-card);
}
.ranking-list__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  padding: 25px 28px 19px;
  border-bottom: 1px solid var(--color-line);
}
.ranking-list__eyebrow {
  color: var(--color-accent-blue);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}
.ranking-list h2 {
  margin: 4px 0 0;
  color: var(--color-ink);
  font-size: 24px;
  letter-spacing: -0.05em;
}
.ranking-list__header p {
  display: grid;
  gap: 3px;
  margin: 0;
  color: var(--color-muted);
  font-size: 12px;
  text-align: right;
}
.ranking-list__header p b {
  color: var(--color-ink);
}
.ranking-list__podium {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: end;
  gap: 12px;
  padding: 36px 28px 24px;
  background: var(--color-surface-soft);
}
.ranking-list__podium-card {
  position: relative;
  display: grid;
  min-width: 0;
  justify-items: center;
  padding: 18px 11px 13px;
  border: 1px solid var(--color-line);
  border-radius: 15px 15px 9px 9px;
  background: #fff;
  text-align: center;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease;
}
.ranking-list__podium-card:hover {
  box-shadow: var(--shadow-card);
  transform: translateY(-3px);
}
.ranking-list__podium-card--1 {
  min-height: 197px;
  border-color: #d9dcfa;
  background: #f7f8ff;
}
.ranking-list__podium-card--2 {
  min-height: 172px;
}
.ranking-list__podium-card--3 {
  min-height: 154px;
}
.ranking-list__crown {
  position: absolute;
  top: -25px;
  color: #ddad28;
  font-size: 28px;
  line-height: 1;
}
.ranking-list__podium-card img {
  width: 64px;
  height: 64px;
  margin-bottom: 9px;
  border: 3px solid #fff;
  border-radius: 50%;
  object-fit: cover;
  background: var(--color-blue-soft);
  box-shadow: 0 2px 8px rgba(23, 36, 61, 0.12);
}
.ranking-list__podium-card--1 img {
  width: 77px;
  height: 77px;
  box-shadow:
    0 0 0 4px #fff4c8,
    0 3px 10px rgba(23, 36, 61, 0.14);
}
.ranking-list__podium-card strong {
  max-width: 100%;
  overflow: hidden;
  color: var(--color-ink);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ranking-list__podium-card small {
  margin-top: 4px;
  color: var(--color-accent-blue);
  font-size: 12px;
  font-weight: 800;
}
.ranking-list__podium-card b {
  position: absolute;
  right: 9px;
  bottom: 8px;
  color: var(--color-muted);
  font-size: 11px;
}
.ranking-list__podium-card--1 b {
  color: #b68513;
}
.ranking-list__table {
  margin: 0 28px;
  padding-bottom: 24px;
}
.ranking-list__table-head,
.ranking-list li {
  display: grid;
  grid-template-columns:
    48px minmax(150px, 1.6fr) minmax(100px, 1fr) minmax(86px, 0.8fr)
    74px;
  gap: 12px;
  align-items: center;
}
.ranking-list__table-head {
  min-height: 40px;
  padding: 0 16px;
  border: 1px solid var(--color-line);
  border-bottom: 0;
  border-radius: 13px 13px 0 0;
  color: var(--color-muted);
  background: var(--color-surface-soft);
  font-size: 11px;
}
.ranking-list ol {
  margin: 0;
  padding: 0;
  border-inline: 1px solid var(--color-line);
  list-style: none;
}
.ranking-list li {
  min-height: 66px;
  padding: 0 16px;
  border-top: 1px solid #f0f1f5;
  color: var(--color-muted);
  font-size: 12px;
  transition: background 0.18s ease;
}
.ranking-list li:hover {
  background: #fafcff;
}
.ranking-list__row--current-user {
  border-top-color: #cfdcff !important;
  background: var(--color-blue-soft);
}
.ranking-list__row--current-user:hover {
  background: #e6efff;
}
.ranking-list__rank {
  color: var(--color-ink);
  font-size: 15px;
  font-weight: 800;
  text-align: center;
}
.ranking-list__player {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}
.ranking-list__player > img {
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border-radius: 50%;
  object-fit: cover;
  background: var(--color-blue-soft);
}
.ranking-list__player > span {
  display: grid;
  min-width: 0;
  gap: 2px;
}
.ranking-list__player b {
  overflow: hidden;
  color: var(--color-ink);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ranking-list__player small {
  font-size: 10px;
}
.ranking-list__record {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ranking-list__score {
  color: var(--color-accent-blue);
  font-size: 13px;
  text-align: right;
  white-space: nowrap;
}
.ranking-list__trend {
  font-size: 11px;
  text-align: right;
  white-space: nowrap;
}
.ranking-list__trend--up {
  color: #2eaa83;
}
.ranking-list__trend--down {
  color: #cc7181;
}
.ranking-list__trend--same {
  color: var(--color-muted);
}
.ranking-list__my-rank {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -6px 28px 28px;
  padding: 16px 19px;
  border: 1px solid #d7e2ff;
  border-radius: 13px;
  background: var(--color-blue-soft);
}
.ranking-list__my-rank div {
  display: grid;
  gap: 3px;
}
.ranking-list__my-rank span {
  color: var(--color-muted);
  font-size: 11px;
}
.ranking-list__my-rank strong {
  color: var(--color-ink);
  font-size: 21px;
}
.ranking-list__my-rank b {
  color: var(--color-accent-blue);
  font-size: 17px;
}
.ranking-list__empty {
  display: grid;
  min-height: 265px;
  place-content: center;
  gap: 7px;
  color: var(--color-muted);
  text-align: center;
}
.ranking-list__empty strong {
  color: var(--color-ink);
}
.ranking-list__empty span {
  font-size: 13px;
}
@media (max-width: 640px) {
  .ranking-list__header {
    padding: 20px;
  }
  .ranking-list h2 {
    font-size: 21px;
  }
  .ranking-list__podium {
    gap: 6px;
    padding: 31px 12px 17px;
  }
  .ranking-list__podium-card {
    padding-inline: 5px;
  }
  .ranking-list__podium-card--1 {
    min-height: 171px;
  }
  .ranking-list__podium-card--2 {
    min-height: 150px;
  }
  .ranking-list__podium-card--3 {
    min-height: 138px;
  }
  .ranking-list__podium-card img {
    width: 50px;
    height: 50px;
  }
  .ranking-list__podium-card--1 img {
    width: 60px;
    height: 60px;
  }
  .ranking-list__podium-card strong {
    font-size: 11px;
  }
  .ranking-list__podium-card small {
    font-size: 10px;
  }
  .ranking-list__table {
    margin: 0 12px;
  }
  .ranking-list__table-head {
    display: none;
  }
  .ranking-list ol {
    border-top: 1px solid var(--color-line);
    border-radius: 13px 13px 0 0;
  }
  .ranking-list li {
    grid-template-columns: 28px minmax(0, 1fr) auto;
    gap: 9px;
    min-height: 63px;
    padding: 0 10px;
  }
  .ranking-list__record,
  .ranking-list__trend {
    display: none;
  }
  .ranking-list__score {
    font-size: 12px;
  }
  .ranking-list__my-rank {
    margin: 14px 12px 16px;
    padding: 14px;
  }
}
</style>
