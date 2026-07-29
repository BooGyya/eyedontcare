<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import GameCard from '../components/games/GameCard.vue'
import GameComingSoonCard from '../components/games/GameComingSoonCard.vue'
import PageHeader from '../components/common/PageHeader.vue'
import { gameDetails, isGameDetailId } from '../mocks/game-details'
import { gameCatalog } from '../mocks/pages'
import type { GameCatalogItem } from '../types/pages'

const router = useRouter()

const games = computed(() =>
  gameCatalog.reduce<GameCatalogItem[]>((details, game) => {
    const gameId = game.id
    if (!isGameDetailId(gameId)) return details

    const detail = gameDetails[gameId]
    details.push({ ...game, title: detail.title, description: detail.subtitle })
    return details
  }, []),
)

function handleEnterGame(game: GameCatalogItem) {
  if (!isGameDetailId(game.id)) return
  router.push({ name: 'game-detail', params: { gameId: game.id } })
}
</script>

<template>
  <section class="games-page">
    <PageHeader
      title="오락실"
      description="눈으로 즐길 수 있는 게임을 골라 가볍게 쉬어가세요."
    />
    <div class="games-page__grid">
      <GameCard
        v-for="game in games"
        :key="game.id"
        :game="game"
        @enter="handleEnterGame"
      />
      <GameComingSoonCard />
    </div>
  </section>
</template>

<style scoped>
.games-page {
  padding: 32px 0 54px;
}

.games-page__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 22px;
}

@media (max-width: 1000px) {
  .games-page__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .games-page {
    padding-top: 24px;
  }

  .games-page__grid {
    grid-template-columns: 1fr;
  }
}
</style>
