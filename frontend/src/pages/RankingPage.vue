<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '../components/common/PageHeader.vue'
import SegmentedTabs from '../components/common/SegmentedTabs.vue'
import RankingList from '../components/ranking/RankingList.vue'
import { getGameRanking, toGameRanking } from '../api/ranking'
import { ApiError } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { GAME_ENGLISH_NAME, type GameName } from '../types/waitingRoom'
import type { GameId, GameRanking } from '../types/pages'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/** 랭킹 탭 = 랭킹 대상 게임 5종. 표시명은 GAME_ENGLISH_NAME으로 통일한다. */
const RANKING_GAMES: readonly { gameId: GameId; gameName: GameName }[] = [
  { gameId: 'blink', gameName: 'BLINK' },
  { gameId: 'draw', gameName: 'DRAWING' },
  { gameId: 'rhythm', gameName: 'RHYTHM' },
  { gameId: 'air', gameName: 'HOCKEY' },
  { gameId: 'hold', gameName: 'EYEFIGHT' },
]
const rankingTabs = RANKING_GAMES.map(
  (game) => GAME_ENGLISH_NAME[game.gameName],
)

function tabLabelForGameId(gameId: string): string | undefined {
  const found = RANKING_GAMES.find((game) => game.gameId === gameId)
  return found ? GAME_ENGLISH_NAME[found.gameName] : undefined
}
function gameByTabLabel(label: string) {
  return RANKING_GAMES.find(
    (game) => GAME_ENGLISH_NAME[game.gameName] === label,
  )
}

const initialGameId =
  typeof route.query.game === 'string' ? route.query.game : ''
const selectedTab = ref(tabLabelForGameId(initialGameId) ?? rankingTabs[0])
const selectedGame = computed(
  () => gameByTabLabel(selectedTab.value) ?? RANKING_GAMES[0],
)

const ranking = ref<GameRanking | null>(null)
const isLoading = ref(false)
const errorMessage = ref('')

async function loadRanking(gameName: GameName) {
  if (!auth.isAuthenticated) return
  isLoading.value = true
  errorMessage.value = ''
  ranking.value = null
  try {
    const response = await getGameRanking(gameName)
    ranking.value = toGameRanking(response, auth.user.id)
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : '랭킹을 불러오지 못했어요.'
  } finally {
    isLoading.value = false
  }
}

watch(selectedTab, () => {
  void router.replace({ query: { game: selectedGame.value.gameId } })
  void loadRanking(selectedGame.value.gameName)
})

watch(
  () => route.query.game,
  (game) => {
    if (typeof game !== 'string') return
    const label = tabLabelForGameId(game)
    if (label && label !== selectedTab.value) selectedTab.value = label
  },
)

// 로그인/로그아웃에 반응: 로그인하면 현재 탭 랭킹을 불러오고, 로그아웃하면 비운다.
watch(
  () => auth.isAuthenticated,
  (authenticated) => {
    if (authenticated) void loadRanking(selectedGame.value.gameName)
    else ranking.value = null
  },
)

onMounted(() => {
  void loadRanking(selectedGame.value.gameName)
})
</script>

<template>
  <section class="ranking-page">
    <PageHeader title="랭킹" description="게임별 최고 기록을 비교해 보세요." />

    <div v-if="!auth.isAuthenticated" class="ranking-page__guest">
      <p>랭킹은 로그인 후 확인할 수 있어요.</p>
      <button type="button" class="ranking-page__login" @click="auth.openLogin">
        로그인하기
      </button>
    </div>

    <template v-else>
      <section class="ranking-page__game-filter" aria-label="게임별 랭킹 필터">
        <SegmentedTabs
          v-model="selectedTab"
          :items="rankingTabs"
          data-testid="ranking-game-tabs"
          label="게임별 랭킹 선택"
        />
      </section>

      <p v-if="isLoading" class="ranking-page__status" role="status">
        랭킹을 불러오는 중이에요…
      </p>
      <div v-else-if="errorMessage" class="ranking-page__status" role="alert">
        <p>{{ errorMessage }}</p>
        <button
          type="button"
          class="ranking-page__retry"
          @click="loadRanking(selectedGame.gameName)"
        >
          다시 시도
        </button>
      </div>
      <Transition v-else name="ranking-swap" mode="out-in">
        <RankingList
          v-if="ranking"
          :key="ranking.gameId"
          class="ranking-page__list"
          :ranking="ranking"
        />
      </Transition>
    </template>
  </section>
</template>

<style scoped>
.ranking-page {
  padding: 32px 0 54px;
}
.ranking-page__list {
  width: 100%;
  margin-top: 16px;
}
.ranking-page__guest,
.ranking-page__status {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  margin-top: 32px;
  padding: 40px 20px;
  color: var(--color-ink-muted, #6b7280);
  text-align: center;
}
.ranking-page__login,
.ranking-page__retry {
  padding: 10px 20px;
  border: 0;
  border-radius: var(--radius-button);
  color: #fff;
  background: var(--color-ink);
  font: inherit;
  cursor: pointer;
}
.ranking-swap-leave-active {
  transition: opacity 0.12s ease;
}
.ranking-swap-leave-to {
  opacity: 0;
}
.ranking-swap-enter-active {
  transition:
    opacity 0.24s var(--ease-out),
    transform 0.24s var(--ease-out);
}
.ranking-swap-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
@media (max-width: 640px) {
  .ranking-page {
    padding-top: 24px;
  }
  .ranking-page__list {
    margin-top: 14px;
  }
}
</style>
