<script setup lang="ts">
import { computed, ref } from 'vue'
import PageHeader from '../components/common/PageHeader.vue'
import SegmentedTabs from '../components/common/SegmentedTabs.vue'
import RankingList from '../components/ranking/RankingList.vue'
import { gameRankings } from '../mocks/pages'

const selectedGame = ref(gameRankings[0].gameName)
const gameTabs = gameRankings.map((ranking) => ranking.gameName)

const selectedRanking = computed(() => {
  return (
    gameRankings.find((ranking) => ranking.gameName === selectedGame.value) ??
    gameRankings[0]
  )
})
</script>

<template>
  <section class="ranking-page">
    <PageHeader
      title="랭킹"
      description="이번 주 게임 기록을 확인하고 나의 순위를 살펴보세요."
    />
    <SegmentedTabs
      v-model="selectedGame"
      :items="gameTabs"
      label="게임별 랭킹 선택"
    />
    <RankingList class="ranking-page__list" :ranking="selectedRanking" />
  </section>
</template>

<style scoped>
.ranking-page {
  padding: 32px 0 54px;
}
.ranking-page__list {
  width: min(100%, 740px);
  margin-top: 23px;
}
@media (max-width: 640px) {
  .ranking-page {
    padding-top: 24px;
  }
}
</style>
