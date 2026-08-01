<script setup lang="ts">
import type { GameCatalogItem } from '../../types/pages'

type GameCardData = GameCatalogItem & {
  people?: string
  duration?: string
}

defineProps<{
  game: GameCardData
}>()

const emit = defineEmits<{
  enter: [game: GameCatalogItem]
}>()
</script>

<template>
  <article class="game-card">
    <div class="game-card__image">
      <img
        :src="game.image"
        :alt="`${game.title} 대표 이미지`"
        loading="lazy"
      />
      <span :class="`game-card__status--${game.status}`">
        {{ game.status === 'available' ? '플레이 가능' : '준비 중' }}
      </span>
    </div>
    <div class="game-card__content">
      <span>{{ game.category }}</span>
      <h2>{{ game.title }}</h2>
      <p>{{ game.description }}</p>
      <div v-if="game.people || game.duration" class="game-card__meta">
        <span v-if="game.people" class="game-card__meta-item">
          <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <path
              d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm0 2c-4 0-7 2-7 4.5V20h14v-1.5c0-2.5-3-4.5-7-4.5Z"
              fill="currentColor"
            />
          </svg>
          {{ game.people }}
        </span>
        <span v-if="game.duration" class="game-card__meta-item">
          <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <circle
              cx="12"
              cy="12"
              r="8.5"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
            />
            <path
              d="M12 7.5V12l3 2"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          {{ game.duration }}
        </span>
      </div>
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
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.game-card:hover {
  border-color: #c8cff9;
  box-shadow: var(--shadow-float);
  transform: translateY(-4px);
}

.game-card__image {
  position: relative;
  height: 156px;
  overflow: hidden;
  background: var(--color-blue-soft);
}

.game-card__image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  transition: transform 0.22s ease;
}

.game-card:hover .game-card__image img {
  transform: scale(1.04);
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

.game-card__meta {
  display: flex;
  gap: 14px;
  margin: 0 0 14px;
}

.game-card__meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-muted);
  font-size: 12px;
}

.game-card__meta-item svg {
  width: 14px;
  height: 14px;
}

.game-card button {
  width: 100%;
  padding: 10px;
  border: 1px solid var(--color-accent-blue);
  border-radius: var(--radius-button);
  color: var(--color-accent-blue);
  background: #fff;
  font-weight: 800;
  cursor: pointer;
  transition:
    color 0.2s ease,
    background-color 0.2s ease;
}

.game-card button:hover {
  color: #fff;
  background: var(--color-accent-blue);
}
</style>
