<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import GameCard from '../components/games/GameCard.vue'
import GameComingSoonCard from '../components/games/GameComingSoonCard.vue'
import PageHeader from '../components/common/PageHeader.vue'
import SegmentedTabs from '../components/common/SegmentedTabs.vue'
import gameBasicImage from '../assets/images/games/game-basic.png'
import { gameDetails, isGameDetailId } from '../mocks/game-details'
import { gameCatalog } from '../mocks/pages'
import type { GameCatalogItem } from '../types/pages'

type GameCardData = GameCatalogItem & {
  people?: string
  duration?: string
}

const router = useRouter()

const games = computed(() =>
  gameCatalog.reduce<GameCardData[]>((details, game) => {
    const gameId = game.id
    if (!isGameDetailId(gameId)) return details

    const detail = gameDetails[gameId]
    details.push({
      ...game,
      title: detail.title,
      description: detail.subtitle,
      people: detail.people,
      duration: detail.duration,
    })
    return details
  }, []),
)

const categoryTabs = computed(() => [
  '전체',
  ...Array.from(new Set(games.value.map((game) => game.category))),
])

const selectedCategory = ref('전체')

const filteredGames = computed(() =>
  selectedCategory.value === '전체'
    ? games.value
    : games.value.filter((game) => game.category === selectedCategory.value),
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

    <div class="games-page__filter">
      <SegmentedTabs
        v-model="selectedCategory"
        :items="categoryTabs"
        label="게임 카테고리 선택"
      />
      <span class="games-page__count"
        >총 {{ filteredGames.length }}개의 게임</span
      >
    </div>

    <div v-if="filteredGames.length" class="games-page__grid">
      <GameCard
        v-for="(game, index) in filteredGames"
        :key="game.id"
        class="games-page__card"
        :style="{ animationDelay: `${index * 0.05}s` }"
        :game="game"
        @enter="handleEnterGame"
      />
      <GameComingSoonCard />
    </div>
    <template v-else>
      <div class="games-page__empty">
        <img :src="gameBasicImage" alt="" />
        <p>이 카테고리의 게임을 준비 중이에요</p>
      </div>
      <div class="games-page__grid games-page__grid--empty">
        <GameComingSoonCard />
      </div>
    </template>
  </section>
</template>

<style scoped>
.games-page {
  padding: 32px 0 54px;
}

.games-page__filter {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  margin-bottom: 22px;
}

.games-page__count {
  color: var(--color-muted);
  font-size: 13px;
}

.games-page__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 22px;
}

.games-page__card {
  animation: page-fade-up 0.4s var(--ease-out) both;
}

.games-page__empty {
  display: grid;
  justify-items: center;
  gap: 10px;
  padding: 36px 0;
  text-align: center;
}

.games-page__empty img {
  width: 96px;
  height: 96px;
  object-fit: contain;
}

.games-page__empty p {
  margin: 0;
  color: var(--color-ink-soft);
  font-size: 13px;
}

.games-page__grid--empty {
  grid-template-columns: minmax(0, 1fr);
  max-width: 360px;
  margin: 0 auto;
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
