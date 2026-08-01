<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '../components/common/PageHeader.vue'
import SegmentedTabs from '../components/common/SegmentedTabs.vue'
import RankingList from '../components/ranking/RankingList.vue'
import { gameRankings } from '../mocks/pages'

const route = useRoute()
const router = useRouter()

function getRankingTabLabel(gameName: string) {
  return gameName.replace(/\s*\(.+\)$/, '')
}

const rankingGameTabs = gameRankings.map((ranking) =>
  getRankingTabLabel(ranking.gameName),
)
const initialRanking = gameRankings.find(
  (ranking) => ranking.gameId === route.query.game,
)

const selectedGameTab = ref(
  getRankingTabLabel(initialRanking?.gameName ?? gameRankings[0].gameName),
)
const selectedRanking = computed(() => {
  return (
    gameRankings.find(
      (ranking) =>
        getRankingTabLabel(ranking.gameName) === selectedGameTab.value,
    ) ?? gameRankings[0]
  )
})

watch(selectedGameTab, () => {
  void router.replace({
    query: { game: selectedRanking.value.gameId },
  })
})

watch(
  () => route.query,
  (query) => {
    const ranking = gameRankings.find((item) => item.gameId === query.game)
    if (ranking) {
      selectedGameTab.value = getRankingTabLabel(ranking.gameName)
    }
  },
)
</script>

<template>
  <section class="ranking-page">
    <PageHeader title="랭킹" description="게임별 최고 기록을 비교해 보세요." />

    <section class="ranking-page__game-filter" aria-label="게임별 랭킹 필터">
      <SegmentedTabs
        v-model="selectedGameTab"
        :items="rankingGameTabs"
        data-testid="ranking-game-tabs"
        label="게임별 랭킹 선택"
      />
    </section>

    <Transition name="ranking-swap" mode="out-in">
      <RankingList
        :key="selectedRanking.gameId"
        class="ranking-page__list"
        :ranking="selectedRanking"
      />
    </Transition>
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
