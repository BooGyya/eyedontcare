<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '../../stores/auth'
import type { GameRanking, RankingPlayer } from '../../types/pages'

const props = defineProps<{
  ranking: GameRanking
}>()
const auth = useAuthStore()

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

function getPlayerNickname(player: RankingPlayer) {
  return player.isCurrentUser && auth.isAuthenticated
    ? auth.user.nickname
    : player.nickname
}

function getPlayerAvatar(player: RankingPlayer) {
  return player.isCurrentUser && auth.isAuthenticated
    ? auth.user.avatar
    : player.avatar
}
</script>

<template>
  <section class="ranking-list" :aria-label="`${ranking.gameName} 랭킹`">
    <header class="ranking-list__header">
      <h2>{{ ranking.gameName }}</h2>
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
          <img
            :src="getPlayerAvatar(player)"
            :alt="`${getPlayerNickname(player)} 프로필`"
          />
          <strong>{{ getPlayerNickname(player) }}</strong>
          <small>{{ player.score }}</small>
          <b aria-label="순위">{{ player.rank }}위</b>
        </article>
      </section>

      <section class="ranking-list__cards" aria-label="4위부터 10위 랭킹">
        <ol>
          <li
            v-for="player in generalPlayers"
            :key="player.rank"
            :class="{
              'ranking-list__row--current-user':
                auth.isAuthenticated && player.isCurrentUser,
            }"
            :data-testid="`ranking-row-${player.rank}`"
          >
            <span class="ranking-list__rank">{{ player.rank }}</span>
            <span class="ranking-list__player">
              <img :src="getPlayerAvatar(player)" alt="" />
              <b>{{ getPlayerNickname(player) }}</b>
            </span>
            <div class="ranking-list__result">
              <strong class="ranking-list__score">{{ player.score }}</strong>
              <span
                :class="`ranking-list__trend--${player.trend ?? 'same'}`"
                class="ranking-list__trend"
              >
                {{ getTrendLabel(player.trend) }}
              </span>
            </div>
          </li>
        </ol>
      </section>

      <footer
        v-if="auth.isAuthenticated && !isCurrentUserInTopTen"
        class="ranking-list__my-rank"
        data-testid="ranking-current-user"
      >
        <div class="ranking-list__my-rank-identity">
          <span>현재 순위</span>
          <div class="ranking-list__my-rank-player">
            <strong>{{ currentUser?.rank ?? ranking.myRank }}위</strong>
            <img
              v-if="currentUser"
              :src="getPlayerAvatar(currentUser)"
              :alt="`${getPlayerNickname(currentUser)} 프로필`"
            />
            <b>{{ currentUser ? getPlayerNickname(currentUser) : '나' }}</b>
          </div>
        </div>
        <div class="ranking-list__my-rank-score">
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
  padding: 22px 28px 18px;
  border-bottom: 1px solid var(--color-line);
}
.ranking-list h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: 24px;
  letter-spacing: -0.05em;
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
.ranking-list__cards {
  padding: 18px 28px 24px;
}
.ranking-list li {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}
.ranking-list ol {
  display: grid;
  gap: 9px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.ranking-list li {
  min-height: 70px;
  padding: 10px 14px;
  border: 1px solid var(--color-line);
  border-radius: 13px;
  color: var(--color-muted);
  background: #fff;
  font-size: 12px;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    transform 0.18s ease;
}
.ranking-list li:hover {
  border-color: #d7e2ff;
  background: #fafcff;
  transform: translateY(-1px);
}
.ranking-list__row--current-user {
  border-color: #cfdcff !important;
  background: var(--color-blue-soft);
}
.ranking-list__row--current-user:hover {
  background: #e6efff;
}
.ranking-list__rank {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 50%;
  color: var(--color-accent-blue);
  background: var(--color-blue-soft);
  font-size: 13px;
  font-weight: 800;
}
.ranking-list__player {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}
.ranking-list__player > img {
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  border-radius: 50%;
  object-fit: cover;
  background: var(--color-blue-soft);
}
.ranking-list__player b {
  min-width: 0;
  overflow: hidden;
  color: var(--color-ink);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ranking-list__result {
  display: grid;
  justify-items: end;
  gap: 2px;
}
.ranking-list__score {
  color: var(--color-accent-blue);
  font-size: 17px;
  line-height: 1.15;
  white-space: nowrap;
}
.ranking-list__trend {
  font-size: 11px;
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
.ranking-list__my-rank-identity {
  display: grid;
  min-width: 0;
  gap: 4px;
}
.ranking-list__my-rank-player {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 9px;
}
.ranking-list__my-rank-player img {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  border: 2px solid #fff;
  border-radius: 50%;
  object-fit: cover;
  background: #fff;
}
.ranking-list__my-rank-score {
  display: grid;
  min-width: 0;
  gap: 3px;
}
.ranking-list__my-rank-score {
  justify-items: end;
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
.ranking-list__my-rank-player b {
  overflow: hidden;
  color: var(--color-ink);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  .ranking-list__cards {
    padding: 14px 12px 18px;
  }
  .ranking-list li {
    grid-template-columns: 32px minmax(0, 1fr) auto;
    gap: 9px;
    min-height: 63px;
    padding: 0 10px;
  }
  .ranking-list__score {
    font-size: 16px;
  }
  .ranking-list__trend {
    display: none;
  }
  .ranking-list__result {
    right: 10px;
  }
  .ranking-list__my-rank {
    margin: 14px 12px 16px;
    padding: 14px;
  }
  .ranking-list__my-rank-player img {
    width: 36px;
    height: 36px;
    flex-basis: 36px;
  }
}
</style>
