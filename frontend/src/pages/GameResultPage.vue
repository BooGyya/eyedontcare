<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import opponentProfileImage from '../assets/images/profiles/profile-smile.png'
import GameResultShell from '../components/games/GameResultShell.vue'
import { gameModeLabels, getMockResult } from '../mocks/gameplay'
import { gameDetails, isGameDetailId } from '../mocks/game-details'
import type { GameSessionMode } from '../types/gameplay'

const route = useRoute()
const router = useRouter()
const game = computed(() => {
  const id = String(route.params.gameId ?? '')
  return isGameDetailId(id) ? gameDetails[id] : undefined
})
const mode = computed<GameSessionMode>(() => {
  const value = String(route.query.mode ?? 'solo')
  return ['solo', 'ai', 'friends', 'random'].includes(value)
    ? (value as GameSessionMode)
    : 'solo'
})
const result = computed(() =>
  game.value ? getMockResult(game.value.id) : undefined,
)
const title = computed(() =>
  game.value?.id === 'draw'
    ? '눈으로 그리기'
    : (game.value?.title.replace(/\s*\([^)]*\)\s*$/, '') ?? ''),
)
const isCompetitive = computed(
  () =>
    game.value?.id !== 'draw' &&
    ['ai', 'friends', 'random'].includes(mode.value),
)
const isDrawResult = computed(() => game.value?.id === 'draw')
const opponentResultImage = computed(() =>
  game.value?.id === 'hold' ? opponentProfileImage : game.value?.image,
)
const drawRoundResults = [
  {
    prompt: '안경',
    difficulty: '쉬움',
    score: 180,
    timeBonus: 40,
    confidenceBonus: 40,
    correct: true,
  },
  {
    prompt: '우산',
    difficulty: '보통',
    score: 230,
    timeBonus: 50,
    confidenceBonus: 80,
    correct: true,
  },
  {
    prompt: '강아지',
    difficulty: '어려움',
    score: 270,
    timeBonus: 60,
    confidenceBonus: 110,
    correct: true,
  },
] as const
const drawTotalScore = computed(() =>
  drawRoundResults.reduce((total, round) => total + round.score, 0),
)

function replay() {
  if (game.value)
    router.push({
      name: 'game-play',
      params: { gameId: game.value.id },
      query: route.query,
    })
}
function viewRanking() {
  router.push({ name: 'ranking', query: { game: game.value?.id } })
}
function goToGames() {
  router.push({ name: 'games' })
}
</script>

<template>
  <GameResultShell
    v-if="game && result"
    :title="title"
    :mode-label="gameModeLabels[mode]"
    :headline="result.headline"
    :summary="result.summary"
    :hide-outcome-intro="isCompetitive"
    :hide-header="isCompetitive || isDrawResult"
    :hide-title="game.id === 'hold'"
  >
    <section
      class="result-grid"
      :class="[
        `result-grid--${game.id}`,
        { 'result-grid--competitive': isCompetitive },
      ]"
    >
      <template v-if="isDrawResult">
        <header class="draw-final-hero">
          <span
            class="draw-final-hero__sparkle draw-final-hero__sparkle--left"
            aria-hidden="true"
            >✦</span
          >
          <span
            class="draw-final-hero__sparkle draw-final-hero__sparkle--right"
            aria-hidden="true"
            >✧</span
          >
          <h1>
            <span class="draw-final-hero__deco" aria-hidden="true">🎉</span>
            게임이 종료되었습니다!
            <span
              class="draw-final-hero__deco draw-final-hero__deco--tail"
              aria-hidden="true"
              >🎊</span
            >
          </h1>
          <p>3개 라운드의 그림 인식 결과를 확인해보세요.</p>
        </header>

        <section class="draw-total-card" aria-label="최종 총점">
          <p class="draw-total-card__label">
            <span aria-hidden="true">🏆</span> 최종 총점
          </p>
          <strong class="draw-total-card__score">{{ drawTotalScore }}점</strong>
          <p class="draw-total-card__message">신기록을 달성했어요!</p>
          <p class="draw-total-card__meta">
            <b>NEW RECORD</b>
          </p>
        </section>

        <section class="draw-rounds" aria-label="라운드 결과">
          <h2>라운드 결과</h2>
          <div class="draw-rounds__grid">
            <article
              v-for="(round, index) in drawRoundResults"
              :key="round.prompt"
              class="draw-round-card"
            >
              <p class="draw-round-card__head">
                <span class="draw-round-card__head-group">
                  <span class="draw-round-card__label"
                    >ROUND {{ index + 1 }}</span
                  >
                  <span class="draw-round-card__difficulty">{{
                    round.difficulty
                  }}</span>
                </span>
              </p>
              <p class="draw-round-card__prompt">
                <b
                  class="draw-round-card__answer"
                  :class="
                    round.correct
                      ? 'draw-round-card__answer--correct'
                      : 'draw-round-card__answer--wrong'
                  "
                  >{{ round.correct ? '정답' : '오답' }}</b
                >
                {{ round.prompt }}
              </p>
              <button
                type="button"
                class="draw-round-card__score"
                :aria-describedby="`draw-score-detail-${index}`"
              >
                {{ round.score }}점
                <span
                  :id="`draw-score-detail-${index}`"
                  class="draw-score-tooltip"
                  role="tooltip"
                >
                  <b>상세 점수</b>
                  <span><i>기본 점수</i><em>100점</em></span>
                  <span
                    ><i>시간 보너스</i><em>{{ round.timeBonus }}점</em></span
                  >
                  <span
                    ><i>AI Confidence</i
                    ><em>{{ round.confidenceBonus }}점</em></span
                  >
                </span>
              </button>
            </article>
          </div>
        </section>

        <section class="draw-ranking" aria-label="랭킹 결과">
          <div class="draw-ranking__heading">
            <h2>랭킹 결과</h2>
            <button type="button" @click="viewRanking">전체 랭킹 보기 →</button>
          </div>
          <div class="draw-ranking__panel">
            <div class="draw-ranking__highlight">
              <span aria-hidden="true">🥈</span>
              <p>내 순위 <strong>2위</strong> <small>/ 154명</small></p>
              <b>상위 1.3%</b>
            </div>
            <ol class="draw-ranking__list">
              <li><b>1</b><span>눈빛 마스터</span><strong>785점</strong></li>
              <li class="draw-ranking__list-item--mine">
                <b>2</b><span>눈빛 좋은 플레이어 (나)</span
                ><strong>{{ drawTotalScore }}점</strong>
              </li>
              <li><b>3</b><span>시선의 지배자</span><strong>698점</strong></li>
              <li><b>4</b><span>집중하는 눈빛</span><strong>645점</strong></li>
            </ol>
          </div>
        </section>

        <section class="draw-final-footer">
          <button
            type="button"
            class="draw-final-footer__replay"
            @click="replay"
          >
            <span aria-hidden="true">↻</span> 다시 플레이
          </button>
          <button
            type="button"
            class="draw-final-footer__list"
            @click="goToGames"
          >
            게임 목록
          </button>
        </section>
      </template>

      <template v-else>
        <article v-if="!isCompetitive" class="result-hero">
          <img
            :src="game.mascotImage"
            :alt="`${title} 결과 마스코트`"
            draggable="false"
          />
          <div class="score-block">
            <span>{{ result.scoreLabel }}</span
            ><strong>{{ result.score }}</strong>
          </div>
        </article>

        <template v-if="isCompetitive">
          <section class="versus-hero" aria-label="대결 승패 결과">
            <strong>YOU WIN!</strong>
            <p>멋진 플레이로 이번 대결을 이겼어요.</p>
          </section>
          <article class="versus-result">
            <section class="player-result player-result--mine">
              <b>WIN</b
              ><img
                :src="game.mascotImage"
                alt="내 플레이어 마스코트"
                draggable="false"
              />
              <span>나 · PLAYER 1</span><strong>눈빛 좋은 플레이어</strong>
            </section>
            <section class="versus-stats">
              <div>
                <strong>{{ result.score }}</strong
                ><b>VS</b><strong>{{ result.opponentScore ?? '-' }}</strong>
              </div>
              <div v-for="stat in result.stats" :key="stat.label">
                <span>{{ stat.value }}</span
                ><b>{{ stat.label }}</b
                ><span>{{ stat.value }}</span>
              </div>
            </section>
            <section class="player-result player-result--opponent">
              <b>LOSE</b
              ><img
                :src="opponentResultImage"
                alt="상대 플레이어 프로필 이미지"
                draggable="false"
              />
              <span>{{ mode === 'ai' ? 'AI · BOT' : '상대 · PLAYER 2' }}</span
              ><strong>{{
                mode === 'ai' ? 'AI 플레이어' : '신나는 플레이어'
              }}</strong>
            </section>
          </article>
        </template>

        <article class="result-summary">
          <template v-if="!isCompetitive">
            <div class="result-summary__heading">
              <p class="result-summary__eyebrow">기록 요약</p>
              <span class="record-badge">NEW RECORD</span>
            </div>
            <h3>{{ title }} 플레이 기록</h3>
          </template>
          <div v-else class="versus-summary">
            <div>
              <b>정말 잘했어요!</b
              ><span
                >정확한 눈 컨트롤이 빛났어요. 다음에도 멋진 플레이를
                기대할게요!</span
              >
            </div>
            <dl>
              <div v-for="stat in result.stats" :key="stat.label">
                <dt>{{ stat.label }}</dt>
                <dd>{{ stat.value }}</dd>
              </div>
            </dl>
            <button type="button" class="primary" @click="replay">
              ↻ 다시 플레이
            </button>
          </div>
          <dl v-if="!isCompetitive">
            <div v-for="stat in result.stats" :key="stat.label">
              <dt>{{ stat.label }}</dt>
              <dd>{{ stat.value }}</dd>
            </div>
          </dl>
          <p class="mock-note">
            {{
              isCompetitive
                ? '대결 결과는 mock 데이터입니다.'
                : '좋은 기록이에요. 다음 게임에서도 도전해 보세요!'
            }}
          </p>
        </article>
      </template>
    </section>
    <nav
      v-if="!isDrawResult"
      class="result-actions"
      aria-label="결과 화면 동작"
    >
      <button type="button" class="primary" @click="replay">
        ↻ 다시 플레이</button
      ><button type="button" @click="viewRanking">랭킹 보기</button
      ><RouterLink to="/games">게임 목록</RouterLink>
    </nav>
  </GameResultShell>
  <section v-else class="missing">
    <h1>게임을 찾을 수 없어요.</h1>
    <RouterLink to="/games">게임 목록으로</RouterLink>
  </section>
</template>

<style scoped>
.result-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(340px, 0.9fr);
  gap: 20px;
  text-align: left;
}
.result-hero,
.result-summary,
.versus-result {
  border: 1px solid #e0e3f1;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(36, 44, 95, 0.05);
}
.result-hero {
  position: relative;
  display: grid;
  min-height: 410px;
  grid-template-rows: auto minmax(0, 1fr) auto;
  place-items: center;
  overflow: hidden;
  background: linear-gradient(145deg, #f1efff, #fff);
}
.result-hero img {
  width: min(62%, 300px);
  height: 255px;
  object-fit: contain;
  align-self: center;
}
.score-block {
  position: static;
  width: calc(100% - 40px);
  margin: 0 20px 20px;
  padding: 14px;
  border: 0;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.88);
  text-align: center;
}
.score-block span,
.rank-card span {
  display: block;
  color: var(--color-muted);
  font-size: 12px;
}
.score-block strong {
  display: block;
  color: var(--color-accent-blue);
  font-family: inherit;
  font-size: clamp(36px, 5vw, 58px);
  line-height: 1.05;
}
.result-summary {
  display: grid;
  align-content: center;
  gap: 18px;
  padding: 28px;
}
.result-summary__eyebrow {
  margin: 0;
  color: var(--color-accent-blue);
  font-size: 12px;
  font-weight: 900;
}
.result-summary__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.record-badge {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  color: #805500;
  background: #fff0bd;
  font-size: 11px;
  font-weight: 900;
}
.result-summary h3 {
  margin: -10px 0 0;
  color: var(--color-ink);
  font-size: 22px;
}
.rank-card {
  padding: 18px;
  border: 1.5px dashed #a8b3e9;
  border-radius: 16px;
  background: #f5f4ff;
  text-align: center;
}
.rank-card b {
  display: block;
  margin: 5px 0;
  color: var(--color-accent-blue);
  font-family: inherit;
  font-size: 42px;
}
.rank-card small {
  color: var(--color-muted);
}
.result-summary dl {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: 0;
}
.result-summary dl div {
  padding: 13px 8px;
  border-left: 1px solid var(--color-line);
  text-align: center;
}
.result-summary dl div:first-child {
  border-left: 0;
}
.result-summary dt {
  color: var(--color-muted);
  font-size: 12px;
}
.result-summary dd {
  margin: 6px 0 0;
  color: var(--color-ink);
  font-weight: 900;
}
.mock-note {
  margin: 0;
  color: var(--color-muted);
  font-size: 12px;
  padding: 12px;
  border-radius: 10px;
  background: #f6f5ff;
  text-align: left;
}
.result-grid--competitive {
  grid-template-columns: 1fr;
  gap: 18px;
}
.versus-hero {
  padding: 0 0 2px;
  text-align: center;
}
.versus-hero > span {
  display: inline-block;
  padding: 6px 15px;
  border-radius: 999px;
  color: #fff;
  background: #7b63e8;
  font-size: 12px;
  font-weight: 900;
}
.versus-hero > strong {
  display: block;
  margin: 7px 0 2px;
  color: #5a46e2;
  font-size: clamp(38px, 6vw, 62px);
  letter-spacing: -0.06em;
}
.versus-hero > p {
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
}
.versus-result {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 1.35fr) minmax(0, 1fr);
  align-items: center;
  gap: 18px;
  min-height: 330px;
  padding: 18px;
  overflow: hidden;
}
.player-result {
  display: grid;
  width: 100%;
  box-sizing: border-box;
  align-content: center;
  gap: 8px;
  place-items: center;
  min-height: 292px;
  padding: 16px;
  border: 1px solid #d5d9ff;
  border-radius: 14px;
  color: var(--color-accent-blue);
  background: #f2f4ff;
  text-align: center;
}
.player-result--opponent {
  border-color: #f2d4d8;
  color: #d64c52;
  background: #fff2f2;
}
.player-result > b {
  padding: 4px 12px;
  border-radius: 999px;
  color: #fff;
  background: #6c63e7;
  font-size: 11px;
}
.player-result--opponent > b {
  color: #333;
  background: #e5e5e5;
}
.player-result span {
  padding: 5px 11px;
  border-radius: 999px;
  color: #fff;
  background: #8276ed;
  font-size: 11px;
  font-weight: 900;
}
.player-result--opponent span {
  background: #989898;
}
.player-result img {
  width: min(100%, 165px);
  height: 160px;
  object-fit: contain;
  object-position: center bottom;
}
.result-grid--hold .player-result {
  grid-template-rows: auto 170px auto auto;
}
.result-grid--hold .player-result img {
  width: 170px;
  height: 170px;
  align-self: end;
}
.player-result > strong {
  color: var(--color-ink);
  font-size: 14px;
}
.player-result--opponent > strong {
  color: #4f5160;
}
.versus-stats {
  overflow: hidden;
  border: 1px solid #e0e3f1;
  border-radius: 14px;
  background: #fff;
}
.versus-stats > div {
  display: grid;
  grid-template-columns: 1fr 86px 1fr;
  align-items: center;
  min-height: 52px;
  padding: 0 18px;
  border-top: 1px solid #edf0f6;
  text-align: center;
}
.versus-stats > div:first-child {
  min-height: 100px;
  border-top: 0;
}
.versus-stats > div:first-child strong {
  color: #5b48e0;
  font-size: 39px;
}
.versus-stats > div:first-child strong:last-child {
  color: #d84171;
}
.versus-stats b {
  color: #737d9c;
  font-size: 12px;
}
.versus-stats span {
  color: var(--color-ink);
  font-size: 16px;
  font-weight: 900;
}
.versus-summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 0.75fr) 190px;
  align-items: center;
  gap: 20px;
  padding: 19px 24px;
  border: 1px solid #e0e3f1;
  border-radius: 16px;
  background: #f5f4ff;
}
.versus-summary > div {
  display: grid;
  gap: 7px;
}
.versus-summary > div b {
  color: #5747df;
  font-size: 22px;
}
.versus-summary > div span {
  color: #687294;
  font-size: 13px;
  line-height: 1.5;
}
.versus-summary dl {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: 0;
  text-align: center;
}
.versus-summary dt {
  color: #727c99;
  font-size: 11px;
}
.versus-summary dd {
  margin: 5px 0 0;
  color: var(--color-ink);
  font-weight: 900;
}
.versus-summary button {
  min-height: 48px;
  border: 0;
  border-radius: 10px;
  color: #fff;
  font-weight: 900;
  cursor: pointer;
}
.result-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 20px;
}
.result-actions button,
.result-actions a {
  min-height: 46px;
  padding: 0 20px;
  border: 1px solid var(--color-line);
  border-radius: 10px;
  color: var(--color-ink);
  background: #fff;
  font-weight: 900;
  line-height: 44px;
  text-decoration: none;
  cursor: pointer;
}
.result-actions .primary {
  border-color: var(--color-accent-blue);
  color: #fff;
  background: var(--color-accent-blue);
}
.result-actions button,
.result-actions a {
  border: 1px solid #d9ddf2;
  border-radius: 9px;
  font-family: inherit;
  font-size: inherit;
}
.result-actions .primary:hover {
  background: #564eda;
}
.result-grid--draw {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
}
.draw-final-hero {
  position: relative;
  text-align: center;
}
.draw-final-hero h1 {
  position: relative;
  display: inline-flex;
  gap: 14px;
  align-items: center;
  margin: 0;
  color: var(--color-ink);
  font-size: clamp(30px, 3.4vw, 42px);
}
.draw-final-hero p {
  margin: 8px 0 0;
  color: var(--color-muted);
  font-size: 16px;
}
.draw-final-hero__deco {
  font-size: 0.72em;
  transform: rotate(-10deg) translateY(-4px);
}
.draw-final-hero__deco--tail {
  transform: rotate(10deg) translateY(-4px);
}
.draw-final-hero__sparkle {
  position: absolute;
  top: 4px;
  color: #c0a9ff;
  font-size: 20px;
}
.draw-final-hero__sparkle--left {
  left: 18%;
}
.draw-final-hero__sparkle--right {
  top: auto;
  right: 18%;
  bottom: 4px;
  color: #8fd8b2;
  font-size: 16px;
}
.draw-total-card {
  display: grid;
  gap: 8px;
  justify-items: center;
  margin-top: 26px;
  padding: 34px 24px 28px;
  border: 1px solid #e5e2fa;
  border-radius: 24px;
  background: linear-gradient(135deg, #fbfaff, #f3f0fe);
  box-shadow: var(--shadow-card);
  text-align: center;
}
.draw-total-card__label {
  margin: 0;
  color: var(--color-ink);
  font-size: 17px;
  font-weight: 700;
}
.draw-total-card__score {
  color: #6b46e5;
  font-family: var(--font-display);
  font-size: clamp(52px, 6vw, 72px);
  line-height: 1.1;
  text-shadow: 0 6px 24px rgba(107, 70, 229, 0.25);
}
.draw-total-card__message {
  margin: 0;
  color: #71c191;
  font-size: 16px;
  font-weight: 700;
}
.draw-total-card__meta {
  display: flex;
  justify-content: center;
  margin: 10px 0 0;
}
.draw-total-card__meta b {
  padding: 7px 14px;
  border-radius: 999px;
  color: #fff;
  background: #71c191;
  box-shadow: 0 4px 12px rgba(113, 193, 145, 0.4);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.05em;
}
.draw-rounds {
  margin-top: 30px;
}
.draw-rounds h2 {
  margin: 0 0 14px;
  color: var(--color-ink);
  font-size: 22px;
}
.draw-rounds__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.draw-round-card {
  display: grid;
  gap: 6px;
  justify-items: center;
  padding: 22px 14px 20px;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: #fff;
  box-shadow: var(--shadow-card);
  text-align: center;
}
.draw-round-card__head {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: center;
  margin: 0;
}
.draw-round-card__head-group {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}
.draw-round-card__label {
  color: var(--color-accent-blue);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
}
.draw-round-card__difficulty {
  padding: 3px 10px;
  border-radius: 999px;
  color: var(--color-accent-blue);
  background: var(--color-blue-soft);
  font-size: 11.5px;
  font-weight: 800;
}
.draw-round-card__prompt {
  margin: 0;
  color: var(--color-ink);
  font-size: 20px;
  font-weight: 700;
  display: inline-flex;
  gap: 8px;
  align-items: center;
}
.draw-round-card__answer {
  padding: 3px 9px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 800;
}
.draw-round-card__answer--correct {
  color: #2f9e63;
  background: #e4f6ec;
}
.draw-round-card__answer--wrong {
  color: #e05a5a;
  background: #fdecec;
}
.draw-round-card__score {
  position: relative;
  padding: 6px 12px;
  border: 0;
  border-radius: 10px;
  color: var(--color-ink);
  background: var(--color-surface-soft);
  font-family: var(--font-display);
  font-size: 26px;
  cursor: help;
}
.draw-round-card__score:hover,
.draw-round-card__score:focus-visible {
  background: var(--color-blue-soft);
}
.draw-score-tooltip {
  position: absolute;
  bottom: calc(100% + 10px);
  left: 50%;
  z-index: 20;
  display: grid;
  gap: 7px;
  width: 208px;
  padding: 14px 15px;
  border-radius: 13px;
  color: #fff;
  background: #26334f;
  box-shadow: var(--shadow-float);
  font-family: 'Noto Sans KR', sans-serif;
  font-size: 12.5px;
  text-align: left;
  opacity: 0;
  visibility: hidden;
  transform: translateX(-50%) translateY(4px);
  transition:
    opacity 0.16s ease,
    transform 0.16s ease,
    visibility 0.16s;
  pointer-events: none;
}
.draw-score-tooltip::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  border: 6px solid transparent;
  border-top-color: #26334f;
  transform: translateX(-50%);
}
.draw-round-card__score:hover .draw-score-tooltip,
.draw-round-card__score:focus-visible .draw-score-tooltip {
  opacity: 1;
  visibility: visible;
  transform: translateX(-50%) translateY(0);
}
.draw-score-tooltip > b {
  justify-self: center;
  font-size: 12px;
  font-weight: 800;
  text-align: center;
  color: #ffd95e;
}
.draw-score-tooltip > span {
  display: flex;
  justify-content: space-between;
}
.draw-score-tooltip > span i {
  font-style: normal;
  color: #c3cbe2;
}
.draw-score-tooltip > span em {
  font-style: normal;
  font-weight: 700;
}
.draw-ranking {
  margin-top: 30px;
}
.draw-ranking__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 0 14px;
}
.draw-ranking__heading h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: 22px;
}
.draw-ranking__heading button {
  padding: 8px 13px;
  border: 0;
  border-radius: 9px;
  color: var(--color-accent-blue);
  background: transparent;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
}
.draw-ranking__heading button:hover {
  background: var(--color-blue-soft);
}
.draw-ranking__panel {
  display: grid;
  gap: 14px;
  padding: 22px 24px;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: #fff;
  box-shadow: var(--shadow-card);
}
.draw-ranking__highlight {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 14px 18px;
  border-radius: 14px;
  background: linear-gradient(135deg, #fbfaff, #f3f0fe);
}
.draw-ranking__highlight span {
  font-size: 26px;
}
.draw-ranking__highlight p {
  margin: 0;
  color: var(--color-ink);
  font-size: 15px;
  font-weight: 600;
}
.draw-ranking__highlight strong {
  font-family: var(--font-display);
  font-size: 22px;
  color: #7451dd;
}
.draw-ranking__highlight small {
  color: var(--color-muted);
}
.draw-ranking__highlight b {
  margin-left: auto;
  padding: 6px 12px;
  border-radius: 999px;
  color: #2f9e63;
  background: #e4f6ec;
  font-size: 12.5px;
  font-weight: 800;
}
.draw-ranking__list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.draw-ranking__list li {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 14px;
  border-radius: 11px;
  background: var(--color-surface-soft);
  font-size: 14px;
}
.draw-ranking__list li b {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: var(--color-muted);
  background: #fff;
  font-size: 12px;
}
.draw-ranking__list li span {
  color: var(--color-ink);
  font-weight: 600;
}
.draw-ranking__list li strong {
  margin-left: auto;
  color: var(--color-ink);
}
.draw-ranking__list li.draw-ranking__list-item--mine {
  border: 1px solid #cfd6f6;
  background: var(--color-blue-soft);
}
.draw-ranking__list li.draw-ranking__list-item--mine b {
  color: #fff;
  background: var(--color-accent-blue);
}
.draw-final-footer {
  display: flex;
  gap: 14px;
  justify-content: center;
  margin-top: 34px;
}
.draw-final-footer__replay {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 14px 30px;
  border: 0;
  border-radius: 14px;
  color: #fff;
  background: #7b81e3;
  box-shadow: 0 8px 18px rgba(123, 129, 227, 0.3);
  font-size: 16px;
  font-weight: 800;
  cursor: pointer;
}
.draw-final-footer__replay:hover {
  background: #6a70d6;
}
.draw-final-footer__list {
  padding: 14px 30px;
  border: 1.5px solid #dcdff6;
  border-radius: 14px;
  color: #26334f;
  background: #fff;
  font-size: 16px;
  font-weight: 800;
  cursor: pointer;
}
.draw-final-footer__list:hover {
  border-color: #7b81e3;
  color: #7b81e3;
}
.missing {
  padding: 60px;
  text-align: center;
}
@media (max-width: 700px) {
  .result-grid {
    grid-template-columns: 1fr;
  }
  .draw-rounds__grid {
    grid-template-columns: 1fr;
  }
  .draw-final-footer {
    justify-content: center;
    text-align: center;
  }
  .versus-result {
    grid-template-columns: 1fr;
  }
  .player-result {
    min-height: 200px;
  }
  .versus-summary {
    grid-template-columns: 1fr;
  }
  .result-summary dl {
    grid-template-columns: 1fr;
  }
  .result-summary dl div {
    border-top: 1px solid var(--color-line);
    border-left: 0;
  }
  .result-actions {
    flex-direction: column;
  }
  .result-actions button,
  .result-actions a {
    width: 100%;
    box-sizing: border-box;
  }
}
</style>
