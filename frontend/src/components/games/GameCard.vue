<script setup lang="ts">
import type { GameCatalogItem } from '../../types/pages'

defineProps<{
  game: GameCatalogItem
}>()

const emit = defineEmits<{
  enter: [game: GameCatalogItem]
}>()
</script>

<template>
  <article class="game-card">
    <div class="game-card__image">
      <img :src="game.image" :alt="`${game.title} 대표 이미지`" />
      <span :class="`game-card__status--${game.status}`">
        {{ game.status === 'available' ? '플레이 가능' : '준비 중' }}
      </span>
    </div>
    <div class="game-card__content">
      <span>{{ game.category }}</span>
      <h2>{{ game.title }}</h2>
      <p>{{ game.description }}</p>
      <button type="button" @click="emit('enter', game)">게임 하기</button>
    </div>
  </article>
</template>

<style scoped>
.game-card {
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: #fff;
  box-shadow: var(--shadow-card);
}

.game-card__image {
  position: relative;
  height: 156px;
  background: var(--color-blue-soft);
}

.game-card__image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.game-card__image > span {
  position: absolute;
  top: 13px;
  right: 13px;
  padding: 5px 9px;
  border-radius: var(--radius-button);
  font-size: 11px;
  font-weight: 800;
}

.game-card__status--available {
  color: #277a64;
  background: var(--color-mint-soft);
}

.game-card__status--coming-soon {
  color: #7b669f;
  background: var(--color-purple-soft);
}

.game-card__content {
  padding: 17px;
}

.game-card__content > span {
  color: var(--color-accent-blue);
  font-size: 12px;
  font-weight: 800;
}

.game-card h2 {
  margin: 4px 0 7px;
  font-size: 20px;
}

.game-card p {
  min-height: 44px;
  margin: 0 0 16px;
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.6;
  word-break: keep-all;
}

.game-card button {
  width: 100%;
  padding: 10px;
  border: 1px solid var(--color-accent-blue);
  border-radius: 10px;
  color: var(--color-accent-blue);
  background: #fff;
  font-weight: 800;
  cursor: pointer;
}

.game-card button:hover {
  color: #fff;
  background: var(--color-accent-blue);
}
</style>
