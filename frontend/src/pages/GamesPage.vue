<script setup lang="ts">
import GameCard from '../components/games/GameCard.vue'
import PageHeader from '../components/common/PageHeader.vue'
import { useToast } from '../composables/useToast'
import { gameCatalog } from '../mocks/pages'
import type { GameCatalogItem } from '../types/pages'

const { showToast } = useToast()

function handleEnterGame(game: GameCatalogItem) {
  showToast(`${game.title} 게임은 다음 구현 단계에서 입장할 수 있어요.`)
}
</script>

<template>
  <section class="games-page">
    <PageHeader
      title="게임 놀이터"
      description="눈으로 즐길 수 있는 게임을 골라 가볍게 쉬어가세요."
    />
    <div class="games-page__grid">
      <GameCard
        v-for="game in gameCatalog"
        :key="game.id"
        :game="game"
        @enter="handleEnterGame"
      />
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
