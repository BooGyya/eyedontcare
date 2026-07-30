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
  },
  {
    prompt: '우산',
    difficulty: '보통',
    score: 230,
    timeBonus: 50,
    confidenceBonus: 80,
  },
  {
    prompt: '고양이',
    difficulty: '어려움',
    score: 270,
    timeBonus: 60,
    confidenceBonus: 110,
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
          <span aria-hidden="true">✦</span>
          <div>
            <h1>게임이 종료되었습니다!</h1>
            <p>3개 라운드의 그림 인식 결과를 확인해 보세요.</p>
          </div>
          <span aria-hidden="true">✦</span>
        </header>

        <div class="draw-final-grid">
          <article class="draw-final-summary">
            <h2>최종 점수 요약</h2>
            <section class="draw-total-card">
              <span class="draw-trophy" aria-hidden="true">🏆</span>
              <div>
                <p>최종 총점</p>
                <strong>{{ drawTotalScore }}점</strong>
                <b>NEW RECORD!</b>
                <small>좋은 기록을 달성했어요! 축하드립니다.</small>
              </div>
            </section>
            <div class="draw-round-cards">
              <article
                v-for="(round, index) in drawRoundResults"
                :key="round.prompt"
              >
                <p>
                  ROUND {{ index + 1 }} <b>{{ round.difficulty }}</b>
                </p>
                <strong>{{ round.score }}점</strong>
                <dl>
                  <div>
                    <dt>◎ 기본 점수</dt>
                    <dd>100점</dd>
                  </div>
                  <div>
                    <dt>◷ 시간 보너스</dt>
                    <dd>+{{ round.timeBonus }}점</dd>
                  </div>
                  <div>
                    <dt>✦ AI Confidence</dt>
                    <dd>+{{ round.confidenceBonus }}점</dd>
                  </div>
                </dl>
                <small>정답 제시어: {{ round.prompt }} ✓</small>
              </article>
            </div>
            <p class="draw-total-equation">
              <span
                v-for="(round, index) in drawRoundResults"
                :key="round.prompt"
              >
                {{ round.score }}점<i v-if="index < drawRoundResults.length - 1"
                  >+</i
                >
              </span>
              <b>= {{ drawTotalScore }}점</b>
            </p>
            <small class="draw-confidence-note"
              >ⓘ AI Confidence는 제시어를 맞췄다고 판단한 mock 확신
              정보입니다.</small
            >
          </article>

          <aside class="draw-ranking-result" aria-label="mock 랭킹 결과">
            <h2>랭킹 결과</h2>
            <section class="draw-rank-highlight">
              <span aria-hidden="true">🥈</span>
              <div>
                <small>내 순위</small><strong>2위</strong><b>/ 154명</b>
              </div>
              <p>상위 1.3%</p>
            </section>
            <ol>
              <li><b>1</b><span>눈빛 마스터</span><strong>785점</strong></li>
              <li class="mine">
                <b>2</b><span>눈빛 좋은 플레이어 (나)</span
                ><strong>{{ drawTotalScore }}점</strong>
              </li>
              <li><b>3</b><span>시선의 지배자</span><strong>698점</strong></li>
              <li><b>4</b><span>집중하는 눈빛</span><strong>645점</strong></li>
            </ol>
            <button type="button" @click="viewRanking">전체 랭킹 보기 →</button>
          </aside>
        </div>
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
    <nav class="result-actions" aria-label="결과 화면 동작">
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
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 22px;
  text-align: center;
}
.draw-final-hero > span {
  color: #7462ea;
  font-size: 44px;
}
.draw-final-hero h1 {
  margin: 0;
  color: var(--color-ink);
  font-size: clamp(30px, 4vw, 43px);
  letter-spacing: -0.06em;
}
.draw-final-hero p {
  margin: 7px 0 0;
  color: var(--color-muted);
}
.draw-final-grid {
  display: grid;
  grid-template-columns: minmax(0, 760px);
  justify-content: center;
  gap: 18px;
  text-align: left;
}
.draw-final-summary,
.draw-ranking-result {
  padding: 22px;
  border: 1px solid #e0e3f1;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(36, 44, 95, 0.05);
}
.draw-final-summary h2,
.draw-ranking-result h2 {
  margin: 0 0 14px;
  color: var(--color-ink);
  font-size: 18px;
}
.draw-final-summary h2,
.draw-total-card b,
.draw-round-cards dl,
.draw-confidence-note,
.draw-ranking-result {
  display: none;
}
.draw-total-card {
  display: grid;
  grid-template-columns: 42% 1fr;
  align-items: center;
  min-height: 150px;
  padding: 14px 22px;
  border: 1px solid #e1e5f7;
  border-radius: 14px;
  background: linear-gradient(140deg, #fafaff, #fff);
}
.draw-trophy {
  display: grid;
  place-items: center;
  font-size: 86px;
}
.draw-total-card p,
.draw-total-card small {
  display: block;
  margin: 0;
  color: #68728d;
  font-size: 13px;
}
.draw-total-card strong {
  display: block;
  margin: 2px 0;
  color: #4539ed;
  font-size: clamp(42px, 6vw, 62px);
  line-height: 1;
}
.draw-total-card b {
  display: inline-block;
  margin: 3px 0 8px;
  padding: 5px 9px;
  border-radius: 8px;
  color: #805500;
  background: #ffe8a8;
  font-size: 11px;
}
.draw-round-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 9px;
  margin-top: 12px;
}
.draw-round-cards article {
  padding: 16px 12px;
  border: 1px solid #e4e7f4;
  border-radius: 12px;
}
.draw-round-cards article > p {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 5px;
  margin: 0;
  color: var(--color-ink);
  font-size: 12px;
  font-weight: 900;
}
.draw-round-cards article > p b {
  padding: 3px 7px;
  border-radius: 99px;
  color: #238952;
  background: #eaf8ef;
  font-size: 10px;
}
.draw-round-cards article > strong {
  display: block;
  margin: 12px 0 7px;
  color: #28206c;
  font-size: 26px;
}
.draw-round-cards dl {
  display: grid;
  gap: 6px;
  margin: 0;
}
.draw-round-cards dl div {
  display: flex;
  justify-content: space-between;
  gap: 6px;
  color: #5f6981;
  font-size: 10px;
}
.draw-round-cards dd {
  margin: 0;
  color: #382dde;
  font-weight: 900;
  white-space: nowrap;
}
.draw-round-cards article > small {
  display: block;
  margin-top: 0;
  padding-top: 0;
  border-top: 0;
  color: #397d59;
  font-size: 10px;
}
.draw-total-equation {
  display: flex;
  justify-content: center;
  gap: 18px;
  margin: 12px 0 8px;
  padding: 12px;
  border-radius: 10px;
  color: #3429de;
  background: #f3f2ff;
  font-weight: 900;
}
.draw-total-equation span {
  display: flex;
  gap: 18px;
}
.draw-total-equation i {
  color: #59627d;
  font-style: normal;
}
.draw-total-equation b {
  font-size: 22px;
}
.draw-confidence-note {
  color: #68728d;
  font-size: 11px;
}
.draw-ranking-result {
  display: flex;
  flex-direction: column;
}
.draw-rank-highlight {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(115deg, #4334ec, #7143e8);
}
.draw-rank-highlight > span {
  font-size: 42px;
}
.draw-rank-highlight div {
  display: grid;
  grid-template-columns: auto auto;
  column-gap: 5px;
}
.draw-rank-highlight small {
  grid-column: span 2;
  font-size: 11px;
}
.draw-rank-highlight strong {
  font-size: 39px;
  line-height: 1;
}
.draw-rank-highlight b {
  align-self: end;
  margin-bottom: 3px;
  font-size: 13px;
}
.draw-rank-highlight p {
  margin: 0 0 0 auto;
  font-size: 12px;
  font-weight: 900;
}
.draw-ranking-result ol {
  margin: 12px 0;
  padding: 0;
  overflow: hidden;
  border: 1px solid #e4e7f4;
  border-radius: 12px;
  list-style: none;
}
.draw-ranking-result li {
  display: grid;
  grid-template-columns: 26px 1fr auto;
  align-items: center;
  gap: 7px;
  padding: 11px 13px;
  border-top: 1px solid #edf0f7;
  color: #253050;
  font-size: 12px;
}
.draw-ranking-result li:first-child {
  border-top: 0;
}
.draw-ranking-result li.mine {
  color: #4539ed;
  background: #f4f3ff;
  font-weight: 900;
}
.draw-ranking-result li > b {
  text-align: center;
}
.draw-ranking-result li > strong {
  font-size: 13px;
}
.draw-ranking-result button {
  min-height: 42px;
  margin-top: auto;
  border: 1px solid #d9ddf2;
  border-radius: 10px;
  color: #4438e7;
  background: #fff;
  font-weight: 900;
  cursor: pointer;
}
.draw-ranking-result button:hover {
  background: #f7f6ff;
}
.missing {
  padding: 60px;
  text-align: center;
}
@media (max-width: 700px) {
  .result-grid {
    grid-template-columns: 1fr;
  }
  .draw-final-grid,
  .draw-round-cards {
    grid-template-columns: 1fr;
  }
  .draw-final-hero {
    gap: 8px;
  }
  .draw-final-hero > span {
    font-size: 24px;
  }
  .draw-total-equation {
    flex-wrap: wrap;
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
