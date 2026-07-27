<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '../components/common/PageHeader.vue'
import SegmentedTabs from '../components/common/SegmentedTabs.vue'
import RankingList from '../components/ranking/RankingList.vue'
import { gameCatalog, gameRankings, overallRanking } from '../mocks/pages'

const route = useRoute()
const router = useRouter()
const rankingModes = ['전체 랭킹', '게임별 랭킹'] as const
const rankingGameCatalog = gameCatalog.filter((game) =>
  gameRankings.some((ranking) => ranking.gameId === game.id),
)
const rankingGameTabs = rankingGameCatalog.map((game) => game.title)
const initialGame = rankingGameCatalog.find(
  (game) => game.id === route.query.game,
)

const selectedMode = ref<(typeof rankingModes)[number]>(
  route.query.tab === 'games' ? '게임별 랭킹' : '전체 랭킹',
)
const selectedGameName = ref(initialGame?.title ?? rankingGameTabs[0])
const isGameRanking = computed(() => selectedMode.value === '게임별 랭킹')
const selectedGame = computed(() => {
  return (
    rankingGameCatalog.find((game) => game.title === selectedGameName.value) ??
    rankingGameCatalog[0]
  )
})
const selectedRanking = computed(() => {
  if (!isGameRanking.value) {
    return overallRanking
  }

  return (
    gameRankings.find((ranking) => ranking.gameId === selectedGame.value.id) ??
    gameRankings[0]
  )
})

watch([selectedMode, selectedGameName], () => {
  void router.replace({
    query: isGameRanking.value
      ? { tab: 'games', game: selectedRanking.value.gameId }
      : {},
  })
})

watch(
  () => route.query,
  (query) => {
    selectedMode.value = query.tab === 'games' ? '게임별 랭킹' : '전체 랭킹'
    const game = rankingGameCatalog.find((item) => item.id === query.game)
    if (game) {
      selectedGameName.value = game.title
    }
  },
)
</script>

<template>
  <section class="ranking-page">
    <PageHeader
      title="랭킹"
      description="이번 주 기록을 확인하고 나의 성장과 순위를 살펴보세요."
    />

    <section class="ranking-page__controls" aria-label="랭킹 필터">
      <SegmentedTabs
        v-model="selectedMode"
        :items="rankingModes"
        data-testid="ranking-mode-tabs"
        label="랭킹 종류"
      />
      <p>
        {{
          isGameRanking
            ? '게임별 최고 기록을 비교해 보세요.'
            : '모든 게임의 이번 주 누적 기록이에요.'
        }}
      </p>
    </section>

    <section
      v-if="isGameRanking"
      class="ranking-page__game-filter"
      aria-label="게임별 랭킹 필터"
    >
      <span>게임 선택</span>
      <SegmentedTabs
        v-model="selectedGameName"
        :items="rankingGameTabs"
        data-testid="ranking-game-tabs"
        label="게임별 랭킹 선택"
      />
    </section>

    <RankingList class="ranking-page__list" :ranking="selectedRanking" />
  </section>
</template>

<style scoped>
.ranking-page {
  width: min(100%, 1200px);
  margin-inline: auto;
  padding: 12px 0 48px;
}
.ranking-page :deep(.page-header) {
  margin: 0 0 18px;
}
.ranking-page__controls {
  display: grid;
  gap: 8px;
}
.ranking-page__controls p {
  margin: 0;
  color: var(--color-muted);
  font-size: 12px;
}
.ranking-page__game-filter {
  display: grid;
  gap: 8px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--color-line);
}
.ranking-page__game-filter > span {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 800;
}
.ranking-page__list {
  width: 100%;
  margin-top: 16px;
}
@media (max-width: 640px) {
  .ranking-page {
    width: 100%;
    padding-top: 16px;
  }
  .ranking-page__list {
    margin-top: 14px;
  }
}
</style>
