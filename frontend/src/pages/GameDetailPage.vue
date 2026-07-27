<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from '../composables/useToast'
import { gameDetails, isGameDetailId } from '../mocks/game-details'
import type { GamePlayMode } from '../types/game-detail'

const route = useRoute()
const { showToast } = useToast()

const game = computed(() => {
  const gameId = String(route.params.gameId ?? '')
  return isGameDetailId(gameId) ? gameDetails[gameId] : undefined
})

function handleSelectMode(mode: GamePlayMode) {
  if (!game.value) return
  showToast(`${game.value.title} ${mode.label} 모드는 준비 중이에요.`)
}
</script>

<template>
  <section v-if="game" class="game-detail-page">
    <RouterLink class="game-detail-page__back" to="/games"
      >← 게임 목록으로</RouterLink
    >

    <section class="game-detail-page__hero">
      <div class="game-detail-page__art">
        <img :src="game.image" :alt="`${game.title} 게임 일러스트`" />
      </div>
      <div class="game-detail-page__intro">
        <span class="game-detail-page__eyebrow">EYE PLAY</span>
        <h1>{{ game.title }}</h1>
        <p>{{ game.subtitle }}</p>
        <dl>
          <div>
            <dt>권장 인원</dt>
            <dd>{{ game.people }}</dd>
          </div>
          <div>
            <dt>예상 플레이 시간</dt>
            <dd>{{ game.duration }}</dd>
          </div>
        </dl>
        <div class="game-detail-page__tags" aria-label="게임 태그">
          <span v-for="tag in game.tags" :key="tag">{{ tag }}</span>
        </div>
      </div>
    </section>

    <section
      class="game-detail-page__method"
      aria-labelledby="game-method-title"
    >
      <div class="game-detail-page__steps">
        <span class="game-detail-page__eyebrow">HOW TO PLAY</span>
        <h2 id="game-method-title">게임 방법</h2>
        <ol>
          <li v-for="(step, index) in game.steps" :key="step">
            <b>{{ index + 1 }}</b>
            <span>{{ step }}</span>
          </li>
        </ol>
      </div>
      <div class="game-detail-page__mascot">
        <img :src="game.mascotImage" :alt="`${game.title} 캐릭터`" />
      </div>
      <div class="game-detail-page__modes">
        <span class="game-detail-page__eyebrow">PLAY MODE</span>
        <h2>어떤 방식으로 즐길까요?</h2>
        <div>
          <button
            v-for="mode in game.modes"
            :key="mode.id"
            type="button"
            @click="handleSelectMode(mode)"
          >
            <strong>{{ mode.label }}</strong>
            <small>{{ mode.description }}</small>
            <span aria-hidden="true">→</span>
          </button>
        </div>
      </div>
    </section>
  </section>

  <section v-else class="game-detail-page__missing">
    <h1>게임을 찾을 수 없어요.</h1>
    <p>게임 목록에서 다시 선택해주세요.</p>
    <RouterLink to="/games">게임 목록으로</RouterLink>
  </section>
</template>

<style scoped>
.game-detail-page {
  padding: 28px 0 58px;
}
.game-detail-page__back {
  display: inline-flex;
  margin-bottom: 19px;
  color: var(--color-muted);
  font-size: 13px;
  font-weight: 700;
}
.game-detail-page__back:hover {
  color: var(--color-accent-blue);
}
.game-detail-page__hero {
  display: grid;
  grid-template-columns: minmax(300px, 0.95fr) minmax(0, 1.05fr);
  gap: clamp(30px, 5vw, 78px);
  align-items: center;
  padding: clamp(28px, 4vw, 52px);
  border: 1px solid var(--color-line);
  border-radius: 24px;
  background: linear-gradient(135deg, var(--color-surface-soft), #fff);
  box-shadow: var(--shadow-card);
}
.game-detail-page__art {
  display: grid;
  place-items: center;
  min-height: 300px;
  border-radius: 20px;
  background: var(--color-purple-soft);
}
.game-detail-page__art img {
  width: min(100%, 380px);
  height: 300px;
  object-fit: contain;
}
.game-detail-page__eyebrow {
  display: block;
  color: var(--color-accent-blue);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.1em;
}
.game-detail-page__intro h1,
.game-detail-page__steps h2,
.game-detail-page__modes h2 {
  margin: 7px 0;
  color: var(--color-ink);
  letter-spacing: -0.05em;
  word-break: keep-all;
}
.game-detail-page__intro h1 {
  font-size: clamp(36px, 4vw, 52px);
}
.game-detail-page__intro > p {
  margin: 0;
  color: var(--color-muted);
  font-size: 16px;
  line-height: 1.65;
  word-break: keep-all;
}
.game-detail-page__intro dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 28px 0 18px;
}
.game-detail-page__intro dl div {
  padding: 14px 15px;
  border: 1px solid var(--color-line);
  border-radius: 13px;
  background: #fff;
}
.game-detail-page__intro dt {
  color: var(--color-muted);
  font-size: 12px;
}
.game-detail-page__intro dd {
  margin: 5px 0 0;
  color: var(--color-ink);
  font-size: 17px;
  font-weight: 800;
}
.game-detail-page__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}
.game-detail-page__tags span {
  padding: 6px 10px;
  border-radius: var(--radius-button);
  color: #4b639c;
  background: var(--color-blue-soft);
  font-size: 12px;
  font-weight: 700;
}
.game-detail-page__method {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(180px, 0.55fr) minmax(
      260px,
      0.95fr
    );
  gap: 28px;
  align-items: stretch;
  margin-top: 22px;
  padding: clamp(25px, 4vw, 42px);
  border: 1px solid var(--color-line);
  border-radius: 24px;
  background: #fff;
  box-shadow: var(--shadow-card);
}
.game-detail-page__steps h2,
.game-detail-page__modes h2 {
  font-size: 23px;
}
.game-detail-page__steps ol {
  display: grid;
  gap: 14px;
  margin: 21px 0 0;
  padding: 0;
  list-style: none;
}
.game-detail-page__steps li {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.6;
  word-break: keep-all;
}
.game-detail-page__steps li b {
  display: grid;
  flex: 0 0 25px;
  width: 25px;
  height: 25px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: var(--color-accent-blue);
  font-size: 12px;
}
.game-detail-page__mascot {
  display: grid;
  place-items: center;
  min-height: 190px;
  border-right: 1px dashed var(--color-line);
  border-left: 1px dashed var(--color-line);
}
.game-detail-page__mascot img {
  width: min(160px, 100%);
  height: 160px;
  object-fit: contain;
}
.game-detail-page__modes > div {
  display: grid;
  gap: 9px;
  margin-top: 20px;
}
.game-detail-page__modes button {
  position: relative;
  display: grid;
  gap: 3px;
  width: 100%;
  padding: 13px 38px 13px 14px;
  border: 1px solid var(--color-line);
  border-radius: 12px;
  color: var(--color-ink);
  background: var(--color-surface-soft);
  text-align: left;
  cursor: pointer;
}
.game-detail-page__modes button:hover {
  border-color: var(--color-accent-blue);
  background: var(--color-blue-soft);
}
.game-detail-page__modes button:active {
  transform: translateY(1px);
}
.game-detail-page__modes button strong {
  font-size: 13px;
}
.game-detail-page__modes button small {
  color: var(--color-muted);
  font-size: 11px;
}
.game-detail-page__modes button span {
  position: absolute;
  top: 50%;
  right: 14px;
  color: var(--color-accent-blue);
  font-size: 18px;
  transform: translateY(-50%);
}
.game-detail-page__missing {
  display: grid;
  place-items: center;
  min-height: 380px;
  padding: 32px;
  text-align: center;
}
.game-detail-page__missing h1 {
  margin: 0;
  font-size: 28px;
}
.game-detail-page__missing p {
  color: var(--color-muted);
}
.game-detail-page__missing a {
  margin-top: 12px;
  color: var(--color-accent-blue);
  font-weight: 800;
}

@media (max-width: 900px) {
  .game-detail-page__hero {
    grid-template-columns: 280px 1fr;
    gap: 30px;
  }
  .game-detail-page__art,
  .game-detail-page__art img {
    min-height: 260px;
    height: 260px;
  }
  .game-detail-page__method {
    grid-template-columns: 1fr 0.7fr;
  }
  .game-detail-page__modes {
    grid-column: 1 / -1;
  }
  .game-detail-page__modes > div {
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  }
}
@media (max-width: 640px) {
  .game-detail-page {
    padding-top: 20px;
  }
  .game-detail-page__hero {
    display: block;
    padding: 20px;
  }
  .game-detail-page__art {
    min-height: 230px;
    margin-bottom: 24px;
  }
  .game-detail-page__art img {
    height: 230px;
  }
  .game-detail-page__intro h1 {
    font-size: 36px;
  }
  .game-detail-page__intro > p {
    font-size: 14px;
  }
  .game-detail-page__intro dl {
    margin-top: 22px;
  }
  .game-detail-page__method {
    display: block;
    padding: 24px 20px;
  }
  .game-detail-page__mascot {
    min-height: 150px;
    margin: 25px 0;
    border: 0;
    border-top: 1px dashed var(--color-line);
    border-bottom: 1px dashed var(--color-line);
  }
  .game-detail-page__mascot img {
    height: 135px;
  }
  .game-detail-page__modes > div {
    grid-template-columns: 1fr;
  }
}
</style>
