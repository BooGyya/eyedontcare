<script setup lang="ts">
import { useRouter } from 'vue-router'
import mascotImage from '../assets/images/brand/mascot.png'
import rhythmImage from '../assets/images/games/game-rhythm-main.png'
import WeeklyRankingCard from '../components/home/WeeklyRankingCard.vue'
import { useToast } from '../composables/useToast'
import { homeQuickActions, weeklyRankingGames } from '../mocks/home'
import type { QuickAction } from '../types/home'

const router = useRouter()
const { showToast } = useToast()

function handleQuickAction(action: QuickAction) {
  if (action.destination) {
    void router.push(action.destination)
    return
  }

  if (action.notice) {
    showToast(action.notice)
  }
}
</script>

<template>
  <section class="home-page">
    <section class="hero-banner" aria-labelledby="home-title">
      <div class="hero-banner__copy">
        <h1 id="home-title" class="hero-title">
          <span>눈으로 놀고,</span>
          <span
            >잠깐의 <b class="hero-title__purple">휴식</b>, 큰
            <b class="hero-title__green">즐거움!</b></span
          >
        </h1>
        <p>눈 하나로 즐기는 소셜 브레이크 게임</p>
        <RouterLink
          class="hero-banner__cta"
          data-testid="start-games"
          to="/games"
        >
          <span>▶</span> 게임 시작하기
        </RouterLink>
      </div>

      <div
        class="hero-banner__visual"
        aria-label="눈 건강 게임을 즐기는 캐릭터와 게임 공간"
        role="img"
      >
        <span class="hero-banner__sparkle hero-banner__sparkle--one">✦</span>
        <span class="hero-banner__sparkle hero-banner__sparkle--two">✧</span>
        <span class="hero-banner__sparkle hero-banner__sparkle--three">⌁</span>
        <div class="hero-banner__bubble">
          오늘은<br /><b>눈으로 뭐 할래?</b>
        </div>
        <img
          class="hero-banner__mascot"
          :src="mascotImage"
          alt="눈 건강 게임을 즐기는 eye dont care 캐릭터"
        />
        <div class="hero-banner__arcade" aria-hidden="true">
          <span>PLAY!</span>
          <div><img :src="rhythmImage" alt="" /></div>
          <i />
          <i />
        </div>
      </div>

      <div class="hero-banner__indicators" aria-label="배너 위치">
        <i class="hero-banner__indicator--active" />
        <i />
        <i />
        <i />
      </div>
    </section>

    <section class="weekly-ranking" aria-labelledby="weekly-ranking-title">
      <div class="weekly-ranking__heading">
        <h2 id="weekly-ranking-title"><span>♜</span> 이번 주 랭킹 TOP 3</h2>
        <RouterLink to="/ranking">전체 랭킹 보기 <span>›</span></RouterLink>
      </div>

      <div class="weekly-ranking__viewport">
        <button
          class="weekly-ranking__scroll-control weekly-ranking__scroll-control--previous"
          type="button"
          aria-label="이전 랭킹 카드"
          disabled
        >
          ‹
        </button>
        <div class="weekly-ranking__cards">
          <WeeklyRankingCard
            v-for="game in weeklyRankingGames"
            :key="game.id"
            :game="game"
          />
        </div>
        <button
          class="weekly-ranking__scroll-control weekly-ranking__scroll-control--next"
          type="button"
          aria-label="다음 랭킹 카드"
          disabled
        >
          ›
        </button>
      </div>
    </section>

    <section class="quick-action-strip" aria-label="빠른 기능">
      <button
        v-for="action in homeQuickActions"
        :key="action.id"
        class="quick-action-strip__item"
        :data-testid="`quick-action-${action.id}`"
        type="button"
        @click="handleQuickAction(action)"
      >
        <span
          class="quick-action-strip__icon"
          :class="`quick-action-strip__icon--${action.tone}`"
        >
          <img :src="action.image" alt="" />
        </span>
        <span class="quick-action-strip__copy">
          <b>{{ action.title }}</b>
          <small>{{ action.description }}</small>
        </span>
        <strong>›</strong>
      </button>
    </section>
  </section>
</template>

<style scoped>
.home-page {
  padding: 0 0 44px;
}

.hero-banner {
  position: relative;
  display: grid;
  min-height: 326px;
  grid-template-columns: 0.92fr 1.08fr;
  margin-top: 0;
  overflow: hidden;
  border: 1px solid #e5e2fa;
  border-radius: 24px;
  background: linear-gradient(112deg, #fff 0%, #fbfaff 58%, #f7f4ff 100%);
}

.hero-banner__copy {
  position: relative;
  z-index: 2;
  align-self: center;
  padding: 38px 30px 48px 104px;
}

.hero-title {
  display: grid;
  gap: 4px;
  margin: 0;
  color: var(--color-ink);
  font-size: clamp(35px, 3.35vw, 52px);
  font-weight: 800;
  line-height: 1.28;
  letter-spacing: -0.09em;
  word-break: keep-all;
}

.hero-title > span {
  display: block;
}

.hero-title b {
  font-weight: inherit;
}

.hero-title__purple {
  color: #7451dd;
}

.hero-title__green {
  color: #35b784;
}

.hero-banner__copy > p {
  margin: 13px 0 23px;
  color: #69738f;
  font-size: 17px;
  font-weight: 600;
}

.hero-banner__cta {
  display: inline-flex;
  align-items: center;
  gap: 11px;
  padding: 14px 28px;
  border-radius: var(--radius-button);
  color: #fff;
  background: linear-gradient(90deg, #7451dc, #9a6ced);
  box-shadow: 0 9px 19px rgba(116, 81, 220, 0.22);
  font-size: 16px;
  font-weight: 800;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.hero-banner__cta:hover {
  box-shadow: 0 12px 23px rgba(116, 81, 220, 0.32);
  transform: translateY(-2px);
}

.hero-banner__cta span {
  font-size: 17px;
}

.hero-banner__visual {
  position: relative;
  min-height: 326px;
}

.hero-banner__mascot {
  position: absolute;
  bottom: -48px;
  left: 10%;
  z-index: 2;
  width: min(45%, 330px);
  height: 355px;
  object-fit: contain;
}

.hero-banner__bubble {
  position: absolute;
  top: 40px;
  right: 32%;
  z-index: 3;
  width: 186px;
  padding: 20px 10px;
  border: 2px solid var(--color-ink);
  border-radius: 54% 46% 51% 49%;
  background: #fff;
  font-size: 17px;
  line-height: 1.5;
  text-align: center;
  transform: rotate(4deg);
}

.hero-banner__bubble::after {
  position: absolute;
  bottom: -11px;
  left: 19px;
  width: 18px;
  height: 16px;
  border-bottom: 2px solid var(--color-ink);
  border-left: 2px solid var(--color-ink);
  background: #fff;
  content: '';
  transform: skew(-28deg) rotate(-20deg);
}

.hero-banner__bubble b {
  color: #754ddd;
}

.hero-banner__arcade {
  position: absolute;
  right: 7%;
  bottom: 24px;
  display: grid;
  width: 178px;
  height: 190px;
  align-items: start;
  padding: 15px 13px;
  border: 7px solid #50338e;
  border-radius: 20px 20px 12px 12px;
  background: linear-gradient(145deg, #7950c6, #422779 80%);
  box-shadow:
    inset -11px -9px rgba(30, 15, 71, 0.25),
    0 15px 25px rgba(75, 43, 139, 0.2);
  transform: perspective(700px) rotateY(-9deg) rotateZ(2deg);
}

.hero-banner__arcade > span {
  color: #ffec6e;
  font-size: 23px;
  font-style: italic;
  font-weight: 900;
  letter-spacing: 0.04em;
  text-align: center;
}

.hero-banner__arcade > div {
  height: 88px;
  overflow: hidden;
  border: 4px solid #251449;
  border-radius: 8px;
  background: #1f1241;
}

.hero-banner__arcade img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.8;
}

.hero-banner__arcade i {
  position: absolute;
  bottom: 14px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #f46e88;
}

.hero-banner__arcade i:first-of-type {
  right: 28px;
}

.hero-banner__arcade i:last-of-type {
  right: 51px;
  background: #ffbd43;
}

.hero-banner__sparkle {
  position: absolute;
  z-index: 1;
  color: #c0a9ff;
  font-size: 28px;
}

.hero-banner__sparkle--one {
  top: 18%;
  left: 5%;
}

.hero-banner__sparkle--two {
  top: 9%;
  right: 7%;
}

.hero-banner__sparkle--three {
  right: 40%;
  bottom: 16%;
  color: #b8a4ed;
  font-size: 38px;
}

.hero-banner__indicators {
  position: absolute;
  bottom: 20px;
  left: 50%;
  z-index: 4;
  display: flex;
  gap: 9px;
  transform: translateX(-50%);
}

.hero-banner__indicators i {
  width: 12px;
  height: 12px;
  border: 2px solid #6d61b8;
  border-radius: 50%;
  background: #fff;
}

.hero-banner__indicators .hero-banner__indicator--active {
  background: #5941c8;
}

.weekly-ranking {
  margin-top: 25px;
}

.weekly-ranking__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 4px 16px;
}

.weekly-ranking__heading h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: 20px;
  letter-spacing: -0.05em;
}

.weekly-ranking__heading h2 span {
  margin-right: 10px;
  color: #805dde;
}

.weekly-ranking__heading a {
  color: #6244ce;
  font-size: 13px;
  font-weight: 800;
}

.weekly-ranking__heading a span {
  margin-left: 7px;
  font-size: 22px;
  vertical-align: -1px;
}

.weekly-ranking__viewport {
  position: relative;
}

.weekly-ranking__cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 24px;
}

.weekly-ranking__scroll-control {
  position: absolute;
  top: 50%;
  z-index: 2;
  display: none;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 50%;
  color: var(--color-ink);
  background: #fff;
  box-shadow: var(--shadow-float);
  font-size: 34px;
  line-height: 1;
}

.weekly-ranking__scroll-control--previous {
  left: -58px;
}

.weekly-ranking__scroll-control--next {
  right: -58px;
}

.quick-action-strip {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0;
  margin-top: 25px;
  padding: 18px 13px;
  border: 1px solid var(--color-line);
  border-radius: 19px;
  background: #fff;
  box-shadow: var(--shadow-card);
}

.quick-action-strip__item {
  display: flex;
  min-width: 0;
  min-height: 77px;
  align-items: center;
  gap: 17px;
  padding: 0 29px;
  border-right: 1px solid #dde1ea;
  background: transparent;
  color: var(--color-ink);
  text-align: left;
  cursor: pointer;
}

.quick-action-strip__item:last-child {
  border-right: 0;
}

.quick-action-strip__icon {
  display: grid;
  width: 68px;
  height: 68px;
  flex: 0 0 auto;
  place-items: center;
  overflow: hidden;
  border-radius: 17px;
}

.quick-action-strip__icon--blue {
  background: linear-gradient(145deg, #6f8aff, #525ad8);
}

.quick-action-strip__icon--yellow {
  background: var(--color-yellow-soft);
}

.quick-action-strip__icon--purple {
  background: #fff0d9;
}

.quick-action-strip__icon img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.quick-action-strip__copy {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.quick-action-strip__copy b {
  overflow-wrap: anywhere;
  font-size: 16px;
}

.quick-action-strip__copy small {
  color: #68728b;
  font-size: 12px;
  line-height: 1.45;
  word-break: keep-all;
}

.quick-action-strip__item > strong {
  margin-left: auto;
  font-size: 30px;
}

@media (max-width: 1440px) {
  .hero-banner__copy {
    padding-left: 72px;
  }

  .weekly-ranking__cards {
    gap: 18px;
  }

  .quick-action-strip__item {
    padding-inline: 20px;
  }
}

@media (max-width: 1280px) {
  .hero-banner__copy {
    padding-left: 48px;
  }

  .hero-banner__mascot {
    left: 4%;
  }

  .hero-banner__arcade {
    right: 3%;
    transform: scale(0.88) perspective(700px) rotateY(-9deg) rotateZ(2deg);
    transform-origin: right bottom;
  }

  .weekly-ranking__cards {
    gap: 14px;
  }

  .quick-action-strip__item {
    padding-inline: 16px;
  }
}

@media (max-width: 1100px) {
  .hero-banner__copy {
    padding-left: 42px;
  }

  .weekly-ranking__cards {
    grid-auto-columns: minmax(254px, 1fr);
    grid-auto-flow: column;
    grid-template-columns: unset;
    overflow-x: auto;
    padding: 1px;
    scroll-snap-type: x mandatory;
  }

  .weekly-ranking__cards > * {
    scroll-snap-align: start;
  }

  .weekly-ranking__scroll-control {
    display: grid;
  }

  .quick-action-strip__copy small {
    font-size: 11px;
  }
}

@media (max-width: 700px) {
  .home-page {
    padding-bottom: 32px;
  }

  .hero-banner {
    display: block;
    min-height: 580px;
    border-radius: 20px;
  }

  .hero-banner__copy {
    padding: 35px 26px 0;
  }

  .hero-title {
    font-size: clamp(34px, 10vw, 43px);
  }

  .hero-banner__copy > p {
    font-size: 14px;
  }

  .hero-banner__visual {
    min-height: 304px;
  }

  .hero-banner__mascot {
    bottom: -24px;
    left: 5%;
    width: 48%;
    height: 285px;
  }

  .hero-banner__bubble {
    top: 25px;
    right: 10%;
    width: 150px;
    padding: 15px 8px;
    font-size: 13px;
  }

  .hero-banner__arcade {
    right: 6%;
    bottom: 27px;
    transform: scale(0.72) perspective(700px) rotateY(-9deg) rotateZ(2deg);
    transform-origin: right bottom;
  }

  .weekly-ranking__heading h2 {
    font-size: 17px;
  }

  .weekly-ranking__heading a {
    font-size: 11px;
  }

  .weekly-ranking__scroll-control {
    display: none;
  }

  .quick-action-strip {
    grid-template-columns: 1fr;
    padding: 8px 15px;
  }

  .quick-action-strip__item {
    min-height: 72px;
    padding: 9px 0;
    border-right: 0;
    border-bottom: 1px solid var(--color-line);
  }

  .quick-action-strip__item:last-child {
    border-bottom: 0;
  }

  .quick-action-strip__icon {
    width: 50px;
    height: 50px;
  }
}
</style>
