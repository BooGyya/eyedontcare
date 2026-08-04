<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '../../stores/auth'
import emptyMascotImage from '../../assets/images/games/game-eye.png'
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

function getTrendInfo(trend?: RankingPlayer['trend']) {
  if (trend === 'up') return { direction: 'up', label: '상승' } as const
  if (trend === 'down') return { direction: 'down', label: '하락' } as const
  return { direction: 'same', label: '유지' } as const
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
          <span
            v-if="player.rank === 1"
            class="ranking-list__crown"
            aria-hidden="true"
          >
            <svg viewBox="0 0 24 24" focusable="false">
              <path
                d="M4 8l3 3 5-6 5 6 3-3-1.5 10h-13L4 8Z"
                fill="var(--color-gold)"
              />
            </svg>
          </span>
          <img
            :src="getPlayerAvatar(player)"
            :alt="`${getPlayerNickname(player)} 프로필`"
          />
          <strong>{{ getPlayerNickname(player) }}</strong>
          <small class="ranking-list__podium-score">{{ player.score }}</small>
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
                :class="`ranking-list__trend--${getTrendInfo(player.trend).direction}`"
                class="ranking-list__trend"
              >
                <svg
                  v-if="getTrendInfo(player.trend).direction === 'up'"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                  focusable="false"
                >
                  <path d="M12 5l6 7h-4v7h-4v-7H6l6-7Z" fill="currentColor" />
                </svg>
                <svg
                  v-else-if="getTrendInfo(player.trend).direction === 'down'"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                  focusable="false"
                >
                  <path d="M12 19l-6-7h4V5h4v7h4l-6 7Z" fill="currentColor" />
                </svg>
                <svg
                  v-else
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                  focusable="false"
                >
                  <path
                    d="M6 12h12"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2.2"
                    stroke-linecap="round"
                  />
                </svg>
                {{ getTrendInfo(player.trend).label }}
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
      <footer
        v-else-if="!auth.isAuthenticated"
        class="ranking-list__guest-prompt"
        data-testid="ranking-guest-prompt"
      >
        로그인하면 내 순위를 확인할 수 있어요
      </footer>
    </template>

    <div v-else class="ranking-list__empty" data-testid="ranking-empty">
      <img :src="emptyMascotImage" alt="" />
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
  letter-spacing: -0.02em;
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
  border-radius: 15px 15px 9px 9px;
  text-align: center;
  transition: transform 0.18s ease;
}
.ranking-list__podium-card:hover {
  transform: translateY(-3px);
}
.ranking-list__podium-card--1 {
  min-height: 197px;
  background: #f3f4fd;
}
.ranking-list__podium-card--2 {
  min-height: 172px;
  background: #fdf6ec;
}
.ranking-list__podium-card--3 {
  min-height: 154px;
  background: #f4fbf6;
}
.ranking-list__crown {
  position: absolute;
  top: -25px;
  width: 28px;
  height: 28px;
}
.ranking-list__crown svg {
  width: 100%;
  height: 100%;
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
.ranking-list__podium-score {
  margin-top: 5px;
  color: var(--color-ink);
  font-size: 15px;
  font-weight: 800;
}
.ranking-list__podium-card--1 .ranking-list__podium-score {
  font-size: 17px;
}
.ranking-list__podium-card b {
  position: absolute;
  top: -12px;
  left: -10px;
  display: grid;
  place-items: center;
  min-width: 34px;
  height: 34px;
  padding: 0 6px;
  border: 2px solid #fff;
  border-radius: 50%;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  box-shadow: 0 2px 8px rgba(23, 36, 61, 0.16);
}
.ranking-list__podium-card--1 b {
  background: #7c88ec;
}
.ranking-list__podium-card--2 b {
  background: #f0b13f;
}
.ranking-list__podium-card--3 b {
  background: #5fc492;
}
.ranking-list__cards {
  padding: 6px 28px 24px;
}
.ranking-list ol {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}
.ranking-list li {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  min-height: 70px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--color-line);
  color: var(--color-muted);
  background: transparent;
  font-size: 12px;
  transition: background-color 0.18s ease;
}
.ranking-list li:last-child {
  border-bottom: 0;
}
.ranking-list li:hover {
  background: var(--color-surface-soft);
}
.ranking-list__row--current-user {
  border-radius: 10px;
  background: #eef3ff;
  color: var(--color-ink);
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
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  white-space: nowrap;
}
.ranking-list__trend svg {
  width: 11px;
  height: 11px;
}
.ranking-list__trend--up {
  color: var(--color-trend-up);
}
.ranking-list__trend--down {
  color: var(--color-trend-down);
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
.ranking-list__guest-prompt {
  margin: -6px 28px 28px;
  padding: 16px 19px;
  border-radius: 13px;
  background: var(--color-surface-soft);
  color: var(--color-ink-soft);
  font-size: 12px;
  text-align: center;
}
.ranking-list__empty {
  display: grid;
  min-height: 265px;
  place-content: center;
  justify-items: center;
  gap: 7px;
  color: var(--color-muted);
  text-align: center;
}
.ranking-list__empty img {
  width: 96px;
  height: 96px;
  object-fit: contain;
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
  .ranking-list__podium-card b {
    top: -10px;
    left: -8px;
    min-width: 28px;
    height: 28px;
    font-size: 11px;
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
