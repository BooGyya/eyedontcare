<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import mascotImage from '../assets/images/brand/mascot.png'
import rhythmImage from '../assets/images/games/game-rhythm-main.png'
import WeeklyRankingCard from '../components/home/WeeklyRankingCard.vue'
import { useToast } from '../composables/useToast'
import { homeQuickActions, weeklyRankingGames } from '../mocks/home'
import type { QuickAction } from '../types/home'

const router = useRouter()
const { showToast } = useToast()
const currentRankingIndex = ref(0)
const visibleRankingCount = ref(3)
const dragStartX = ref<number | null>(null)
const dragStartY = ref<number | null>(null)
const dragOffsetX = ref(0)
const isDragging = ref(false)
const didDrag = ref(false)
const horizontalDragThreshold = 8
const rankingClip = ref<InstanceType<typeof globalThis.HTMLElement> | null>(
  null,
)

const maxRankingIndex = computed(() =>
  Math.max(0, weeklyRankingGames.length - visibleRankingCount.value),
)
const rankingTrackStyle = computed(() => ({
  '--ranking-index': currentRankingIndex.value,
  '--visible-ranking-count': visibleRankingCount.value,
  '--drag-offset': `${dragOffsetX.value}px`,
}))

function updateVisibleRankingCount() {
  const width = globalThis.innerWidth
  visibleRankingCount.value = width < 600 ? 1 : width < 900 ? 2 : 3
  currentRankingIndex.value = Math.min(
    currentRankingIndex.value,
    maxRankingIndex.value,
  )
}

function moveRanking(direction: -1 | 1) {
  currentRankingIndex.value = Math.max(
    0,
    Math.min(maxRankingIndex.value, currentRankingIndex.value + direction),
  )
}

function handleDragStart(event: globalThis.PointerEvent) {
  if (event.pointerType === 'mouse' && event.button !== 0) return
  dragStartX.value = event.clientX
  dragStartY.value = event.clientY
  dragOffsetX.value = 0
  isDragging.value = false
  didDrag.value = false
}

function handleDragMove(event: globalThis.PointerEvent) {
  if (dragStartX.value === null || dragStartY.value === null) return
  const distance = event.clientX - dragStartX.value
  const verticalDistance = event.clientY - dragStartY.value

  if (!isDragging.value) {
    if (Math.abs(distance) < horizontalDragThreshold) return
    if (Math.abs(verticalDistance) >= Math.abs(distance)) {
      dragStartX.value = null
      dragStartY.value = null
      return
    }

    isDragging.value = true
    didDrag.value = true
    ;(
      event.currentTarget as InstanceType<typeof globalThis.HTMLElement>
    ).setPointerCapture?.(event.pointerId)
  }

  const isPastStart = currentRankingIndex.value === 0 && distance > 0
  const isPastEnd =
    currentRankingIndex.value === maxRankingIndex.value && distance < 0
  const boundaryResistance = isPastStart || isPastEnd ? 0.18 : 1
  const maximumOffset = (rankingClip.value?.clientWidth ?? 300) * 0.22
  dragOffsetX.value = Math.max(
    -maximumOffset,
    Math.min(maximumOffset, distance * boundaryResistance),
  )
}

function getRankingStep() {
  const clipWidth = rankingClip.value?.clientWidth ?? 0
  const gap = visibleRankingCount.value === 1 ? 18 : 26
  if (clipWidth === 0) return 300
  const cardWidth =
    (clipWidth - (visibleRankingCount.value - 1) * gap) /
    visibleRankingCount.value
  return cardWidth + gap
}

function handleDragEnd(event: globalThis.PointerEvent) {
  if (dragStartX.value === null) return
  if (isDragging.value) {
    const distance = event.clientX - dragStartX.value
    const movedCards = Math.round(-distance / getRankingStep())
    currentRankingIndex.value = Math.max(
      0,
      Math.min(maxRankingIndex.value, currentRankingIndex.value + movedCards),
    )
    ;(
      event.currentTarget as InstanceType<typeof globalThis.HTMLElement>
    ).releasePointerCapture?.(event.pointerId)
  }
  dragStartX.value = null
  dragStartY.value = null
  dragOffsetX.value = 0
  isDragging.value = false
}

function handleDragCancel(event: globalThis.PointerEvent) {
  if (isDragging.value) {
    ;(
      event.currentTarget as InstanceType<typeof globalThis.HTMLElement>
    ).releasePointerCapture?.(event.pointerId)
  }
  dragStartX.value = null
  dragStartY.value = null
  dragOffsetX.value = 0
  isDragging.value = false
}

function handleCarouselClick(event: globalThis.MouseEvent) {
  if (!didDrag.value) return
  event.preventDefault()
  event.stopPropagation()
  didDrag.value = false
}

onMounted(() => {
  updateVisibleRankingCount()
  globalThis.addEventListener('resize', updateVisibleRankingCount)
})

onBeforeUnmount(() => {
  globalThis.removeEventListener('resize', updateVisibleRankingCount)
})

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
          draggable="false"
        />
        <div class="hero-banner__arcade" aria-hidden="true">
          <span>PLAY!</span>
          <div><img :src="rhythmImage" alt="" draggable="false" /></div>
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
      </div>

      <div class="weekly-ranking__viewport">
        <button
          class="weekly-ranking__scroll-control weekly-ranking__scroll-control--previous"
          type="button"
          aria-label="이전 랭킹 보기"
          :disabled="currentRankingIndex === 0"
          @click="moveRanking(-1)"
        >
          ‹
        </button>
        <div
          ref="rankingClip"
          class="weekly-ranking__clip"
          :class="{ 'weekly-ranking__clip--dragging': isDragging }"
          @click.capture="handleCarouselClick"
          @dragstart.prevent
          @pointerdown="handleDragStart"
          @pointermove="handleDragMove"
          @pointerup="handleDragEnd"
          @pointercancel="handleDragCancel"
        >
          <div
            class="weekly-ranking__cards"
            :class="{ 'weekly-ranking__cards--dragging': isDragging }"
            :style="rankingTrackStyle"
          >
            <WeeklyRankingCard
              v-for="game in weeklyRankingGames"
              :key="game.id"
              :game="game"
            />
          </div>
        </div>
        <button
          class="weekly-ranking__scroll-control weekly-ranking__scroll-control--next"
          type="button"
          aria-label="다음 랭킹 보기"
          :disabled="currentRankingIndex === maxRankingIndex"
          @click="moveRanking(1)"
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
          :class="[
            `quick-action-strip__icon--${action.tone}`,
            { 'quick-action-strip__icon--discord': action.id === 'discord' },
          ]"
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
  border: 2px solid #26334f;
  border-radius: 30px 27px 32px 25px;
  background: #fffefa;
  font-size: 17px;
  line-height: 1.5;
  text-align: center;
  transform: rotate(2deg);
}

.hero-banner__bubble::before,
.hero-banner__bubble::after {
  position: absolute;
  bottom: -18px;
  left: 30px;
  width: 0;
  height: 0;
  border-top: 20px solid #26334f;
  border-right: 14px solid transparent;
  content: '';
  transform: rotate(8deg);
  transform-origin: top left;
}

.hero-banner__bubble::after {
  bottom: -14px;
  left: 32px;
  border-top: 17px solid #fffefa;
  border-right-width: 11px;
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

.weekly-ranking__viewport {
  position: relative;
  padding-inline: 58px;
}

.weekly-ranking__clip {
  overflow: hidden;
  padding: 1px;
  touch-action: pan-y;
  user-select: none;
  cursor: grab;
}

.weekly-ranking__clip--dragging {
  cursor: grabbing;
}

.weekly-ranking__clip img {
  -webkit-user-drag: none;
  user-select: none;
  pointer-events: none;
}

.weekly-ranking__cards {
  --ranking-gap: 26px;
  display: flex;
  gap: var(--ranking-gap);
  transform: translateX(
    calc(
      var(--drag-offset) - var(--ranking-index) *
        (
          (100% - (var(--visible-ranking-count) - 1) * var(--ranking-gap)) /
            var(--visible-ranking-count) + var(--ranking-gap)
        )
    )
  );
  transition: transform 0.34s ease;
  will-change: transform;
}

.weekly-ranking__cards--dragging {
  transition: none;
}

.weekly-ranking__cards > * {
  flex: 0 0
    calc(
      (100% - (var(--visible-ranking-count) - 1) * var(--ranking-gap)) /
        var(--visible-ranking-count)
    );
}

.weekly-ranking__scroll-control {
  position: absolute;
  top: 50%;
  z-index: 2;
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 50%;
  color: var(--color-ink);
  background: #fff;
  box-shadow: var(--shadow-float);
  font-size: 34px;
  line-height: 1;
  cursor: pointer;
  transform: translateY(-50%);
  transition:
    opacity 0.2s ease,
    box-shadow 0.2s ease;
}

.weekly-ranking__scroll-control:disabled {
  opacity: 0.32;
  box-shadow: none;
  cursor: not-allowed;
}

.weekly-ranking__scroll-control--previous {
  left: 4px;
}

.weekly-ranking__scroll-control--next {
  right: 4px;
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

.quick-action-strip__icon--discord {
  width: 58px;
  height: 58px;
  background: transparent;
  border-radius: 0;
}

.quick-action-strip__icon--discord img {
  width: 100%;
  height: 100%;
  object-fit: contain;
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

  .quick-action-strip__item {
    padding-inline: 16px;
  }
}

@media (max-width: 1100px) {
  .hero-banner__copy {
    padding-left: 42px;
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

  .weekly-ranking__viewport {
    padding-inline: 42px;
  }

  .weekly-ranking__cards {
    --ranking-gap: 18px;
  }

  .weekly-ranking__scroll-control {
    width: 34px;
    height: 34px;
    font-size: 27px;
  }

  .weekly-ranking__scroll-control--previous {
    left: 0;
  }

  .weekly-ranking__scroll-control--next {
    right: 0;
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
