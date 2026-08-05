<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import mascotImage from '../assets/images/brand/mascot.png'
import rhythmImage from '../assets/images/games/game-rhythm-main.png'
import drawArtImage from '../assets/images/games/game-draw-main.png'
import airArtImage from '../assets/images/games/game-air-main.png'
import calmFaceImage from '../assets/images/profiles/profile-calm.png'
import groupJoinImage from '../assets/images/illustrations/illustration-group-join.png'
import WeeklyRankingCard from '../components/home/WeeklyRankingCard.vue'
import { useToast } from '../composables/useToast'
import { getRankingSummary, toWeeklyRankingGames } from '../api/ranking'
import { homeQuickActions, weeklyRankingGamePresets } from '../mocks/home'
import type { QuickAction, WeeklyRankingGame } from '../types/home'

const router = useRouter()
const { showToast } = useToast()
const weeklyRankingGames = ref<WeeklyRankingGame[]>(
  weeklyRankingGamePresets.map((preset) => ({
    ...preset,
    records: [],
    myRank: 0,
  })),
)
const currentHeroSlide = ref(0)
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
const heroSlidesEl = ref<InstanceType<typeof globalThis.HTMLElement> | null>(
  null,
)
const heroDragStartX = ref<number | null>(null)
const heroDragStartY = ref<number | null>(null)
const heroDragOffsetX = ref(0)
const isHeroDragging = ref(false)
const heroDidDrag = ref(false)

const maxRankingIndex = computed(() =>
  Math.max(0, weeklyRankingGames.value.length - visibleRankingCount.value),
)
const rankingTrackStyle = computed(() => ({
  '--ranking-index': currentRankingIndex.value,
  '--visible-ranking-count': visibleRankingCount.value,
  '--drag-offset': `${dragOffsetX.value}px`,
}))
const heroSlidesStyle = computed(() => ({
  '--hero-drag-offset': `${heroDragOffsetX.value}px`,
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

function getHeroDragThreshold() {
  const width = heroSlidesEl.value?.clientWidth ?? 0
  return Math.min(60, width * 0.15)
}

function handleHeroDragStart(event: globalThis.PointerEvent) {
  if (event.pointerType === 'mouse' && event.button !== 0) return
  heroDragStartX.value = event.clientX
  heroDragStartY.value = event.clientY
  heroDragOffsetX.value = 0
  isHeroDragging.value = false
  heroDidDrag.value = false
}

function handleHeroDragMove(event: globalThis.PointerEvent) {
  if (heroDragStartX.value === null || heroDragStartY.value === null) return
  const distance = event.clientX - heroDragStartX.value
  const verticalDistance = event.clientY - heroDragStartY.value

  if (!isHeroDragging.value) {
    if (Math.abs(distance) < horizontalDragThreshold) return
    if (Math.abs(verticalDistance) >= Math.abs(distance)) {
      heroDragStartX.value = null
      heroDragStartY.value = null
      return
    }

    isHeroDragging.value = true
    heroDidDrag.value = true
    ;(
      event.currentTarget as InstanceType<typeof globalThis.HTMLElement>
    ).setPointerCapture?.(event.pointerId)
  }

  const maximumOffset = (heroSlidesEl.value?.clientWidth ?? 300) * 0.22
  heroDragOffsetX.value = Math.max(
    -maximumOffset,
    Math.min(maximumOffset, distance),
  )
}

function handleHeroDragEnd(event: globalThis.PointerEvent) {
  if (heroDragStartX.value === null) return
  if (isHeroDragging.value) {
    const distance = event.clientX - heroDragStartX.value
    if (Math.abs(distance) > getHeroDragThreshold()) {
      const direction = distance < 0 ? 1 : -1
      currentHeroSlide.value = (currentHeroSlide.value + direction + 4) % 4
      startHeroTimer()
    }
    ;(
      event.currentTarget as InstanceType<typeof globalThis.HTMLElement>
    ).releasePointerCapture?.(event.pointerId)
  }
  heroDragStartX.value = null
  heroDragStartY.value = null
  heroDragOffsetX.value = 0
  isHeroDragging.value = false
}

function handleHeroDragCancel(event: globalThis.PointerEvent) {
  if (isHeroDragging.value) {
    ;(
      event.currentTarget as InstanceType<typeof globalThis.HTMLElement>
    ).releasePointerCapture?.(event.pointerId)
  }
  heroDragStartX.value = null
  heroDragStartY.value = null
  heroDragOffsetX.value = 0
  isHeroDragging.value = false
}

function handleHeroClick(event: globalThis.MouseEvent) {
  if (!heroDidDrag.value) return
  event.preventDefault()
  event.stopPropagation()
  heroDidDrag.value = false
}

onMounted(() => {
  updateVisibleRankingCount()
  globalThis.addEventListener('resize', updateVisibleRankingCount)
  startHeroTimer()
  void loadWeeklyRanking()
})

async function loadWeeklyRanking() {
  try {
    const summary = await getRankingSummary(3)
    weeklyRankingGames.value = toWeeklyRankingGames(
      weeklyRankingGamePresets,
      summary,
    )
  } catch {
    // 게스트/네트워크 오류 등으로 조회에 실패하면 빈 카드(참여 유도 문구)를 그대로 둔다.
  }
}

onBeforeUnmount(() => {
  globalThis.removeEventListener('resize', updateVisibleRankingCount)
  stopHeroTimer()
})

const heroTimer = ref<ReturnType<typeof globalThis.setInterval> | null>(null)

function startHeroTimer() {
  stopHeroTimer()
  heroTimer.value = globalThis.setInterval(() => {
    currentHeroSlide.value = (currentHeroSlide.value + 1) % 4
  }, 5000)
}

function stopHeroTimer() {
  if (heroTimer.value !== null) {
    globalThis.clearInterval(heroTimer.value)
    heroTimer.value = null
  }
}

function selectHeroSlide(index: number) {
  currentHeroSlide.value = index
  startHeroTimer()
}

function handleQuickAction(action: QuickAction) {
  if (action.destination) {
    void router.push(action.destination)
    return
  }

  if (action.externalUrl) {
    globalThis.open(action.externalUrl, '_blank', 'noopener,noreferrer')
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
      <div
        ref="heroSlidesEl"
        class="hero-banner__slides"
        :class="{ 'hero-banner__slides--dragging': isHeroDragging }"
        :style="heroSlidesStyle"
        @click.capture="handleHeroClick"
        @dragstart.prevent
        @pointerdown="handleHeroDragStart"
        @pointermove="handleHeroDragMove"
        @pointerup="handleHeroDragEnd"
        @pointercancel="handleHeroDragCancel"
      >
        <div v-show="currentHeroSlide === 0" class="hero-banner__slide">
          <div class="hero-banner__copy">
            <h1 id="home-title" class="hero-title">
              <span><b class="hero-title__purple">눈</b>으로 놀고,</span>
              <span
                >잠깐의 휴식, 큰 <b class="hero-title__green">즐거움!</b></span
              >
            </h1>
            <p>눈 하나로 즐기는 소셜 브레이크 게임</p>
            <RouterLink
              class="hero-banner__cta"
              data-testid="start-games"
              to="/games"
            >
              <span class="hero-banner__cta-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" focusable="false">
                  <path d="M7 4.5v15l13-7.5-13-7.5Z" fill="currentColor" />
                </svg>
              </span>
              게임 시작하기
            </RouterLink>
          </div>

          <div
            class="hero-banner__visual"
            aria-label="눈 건강 게임을 즐기는 캐릭터와 게임 공간"
            role="img"
          >
            <span class="hero-banner__sparkle hero-banner__sparkle--one">
              <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path
                  d="M12 2l1.09 6.26L20 9l-6.91.74L12 16l-1.09-6.26L4 9l6.91-.74L12 2Z"
                  fill="currentColor"
                />
              </svg>
            </span>
            <span class="hero-banner__sparkle hero-banner__sparkle--two">
              <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path
                  d="M12 2l1.09 6.26L20 9l-6.91.74L12 16l-1.09-6.26L4 9l6.91-.74L12 2Z"
                  fill="currentColor"
                />
              </svg>
            </span>
            <span class="hero-banner__sparkle hero-banner__sparkle--three">
              <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path
                  d="M12 2l1.09 6.26L20 9l-6.91.74L12 16l-1.09-6.26L4 9l6.91-.74L12 2Z"
                  fill="currentColor"
                />
              </svg>
            </span>
            <div class="hero-banner__bubble">
              오늘은<br /><b>눈으로 뭐 할래?</b>
            </div>
            <img
              class="hero-banner__mascot"
              :src="mascotImage"
              alt="눈 건강 게임을 즐기는 eye dont care 캐릭터"
              draggable="false"
            />
            <div class="hero-banner__preview" aria-hidden="true">
              <span>PLAY!</span>
              <img :src="rhythmImage" alt="" draggable="false" />
            </div>
          </div>
        </div>

        <div v-show="currentHeroSlide === 1" class="hero-banner__slide">
          <div class="hero-banner__copy">
            <h1 class="hero-title">
              <span><b class="hero-title__purple">다섯 가지</b> 미니게임,</span>
              <span
                >오늘은
                <b class="hero-title__green">뭐부터</b> 놀아볼까요?</span
              >
            </h1>
            <p>눈 깜빡이기부터 에어하키까지, 전부 눈으로 즐겨요</p>
            <RouterLink class="hero-banner__cta" to="/games">
              <span class="hero-banner__cta-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" focusable="false">
                  <path d="M7 4.5v15l13-7.5-13-7.5Z" fill="currentColor" />
                </svg>
              </span>
              게임 시작하기
            </RouterLink>
          </div>
          <div class="hero-banner__visual" aria-hidden="true">
            <span class="hero-banner__sparkle hero-banner__sparkle--one">
              <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path
                  d="M12 2l1.09 6.26L20 9l-6.91.74L12 16l-1.09-6.26L4 9l6.91-.74L12 2Z"
                  fill="currentColor"
                />
              </svg>
            </span>
            <span class="hero-banner__sparkle hero-banner__sparkle--two">
              <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path
                  d="M12 2l1.09 6.26L20 9l-6.91.74L12 16l-1.09-6.26L4 9l6.91-.74L12 2Z"
                  fill="currentColor"
                />
              </svg>
            </span>
            <img
              class="hero-banner__art hero-banner__art--tilt-left"
              :src="drawArtImage"
              alt=""
              draggable="false"
            />
            <img
              class="hero-banner__art hero-banner__art--tilt-right"
              :src="airArtImage"
              alt=""
              draggable="false"
            />
          </div>
        </div>

        <div v-show="currentHeroSlide === 2" class="hero-banner__slide">
          <div class="hero-banner__copy">
            <h1 class="hero-title">
              <span><b class="hero-title__purple">20분</b> 집중했다면,</span>
              <span>20초는 <b class="hero-title__green">눈 휴식 시간</b>!</span>
            </h1>
            <p>멀리 바라보며 눈에게 쉬는 시간을 선물하세요</p>
            <RouterLink class="hero-banner__cta" to="/games">
              <span class="hero-banner__cta-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" focusable="false">
                  <path d="M7 4.5v15l13-7.5-13-7.5Z" fill="currentColor" />
                </svg>
              </span>
              게임 시작하기
            </RouterLink>
          </div>
          <div class="hero-banner__visual" aria-hidden="true">
            <span class="hero-banner__sparkle hero-banner__sparkle--one">
              <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path
                  d="M12 2l1.09 6.26L20 9l-6.91.74L12 16l-1.09-6.26L4 9l6.91-.74L12 2Z"
                  fill="currentColor"
                />
              </svg>
            </span>
            <span class="hero-banner__rest-zzz">z z Z</span>
            <img
              class="hero-banner__calm"
              :src="calmFaceImage"
              alt=""
              draggable="false"
            />
          </div>
        </div>

        <div v-show="currentHeroSlide === 3" class="hero-banner__slide">
          <div class="hero-banner__copy">
            <h1 class="hero-title">
              <span>친구들과 함께,</span>
              <span
                >이번 주 <b class="hero-title__purple">랭킹</b>에
                <b class="hero-title__green">도전!</b></span
              >
            </h1>
            <p>소모임에서 같이 놀고 TOP 3 기록을 노려보세요</p>
            <RouterLink class="hero-banner__cta" to="/games">
              <span class="hero-banner__cta-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" focusable="false">
                  <path d="M7 4.5v15l13-7.5-13-7.5Z" fill="currentColor" />
                </svg>
              </span>
              게임 시작하기
            </RouterLink>
          </div>
          <div class="hero-banner__visual" aria-hidden="true">
            <span class="hero-banner__sparkle hero-banner__sparkle--two">
              <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path
                  d="M12 2l1.09 6.26L20 9l-6.91.74L12 16l-1.09-6.26L4 9l6.91-.74L12 2Z"
                  fill="currentColor"
                />
              </svg>
            </span>
            <img
              class="hero-banner__group"
              :src="groupJoinImage"
              alt=""
              draggable="false"
            />
          </div>
        </div>
      </div>

      <div
        class="hero-banner__indicators"
        role="tablist"
        aria-label="배너 위치"
      >
        <button
          v-for="(_, index) in 4"
          :key="index"
          type="button"
          class="hero-banner__indicator"
          :class="{
            'hero-banner__indicator--active': currentHeroSlide === index,
          }"
          :aria-label="`${index + 1}번째 배너 보기`"
          :aria-selected="currentHeroSlide === index"
          role="tab"
          @click="selectHeroSlide(index)"
        ></button>
      </div>
    </section>

    <section class="weekly-ranking" aria-labelledby="weekly-ranking-title">
      <div class="weekly-ranking__heading">
        <h2 id="weekly-ranking-title">
          <span class="weekly-ranking__trophy">
            <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path
                d="M7 3h10v2h2a1 1 0 0 1 1 1c0 3.2-1.9 5.6-4.4 6.3a5.6 5.6 0 0 1-2.6 2.6V17h3v2H8v-2h3v-2.1a5.6 5.6 0 0 1-2.6-2.6C6 12.6 4 10.2 4 7a1 1 0 0 1 1-1h2V3Zm0 3H5.6c.3 1.7 1.3 3 2.7 3.6A9.6 9.6 0 0 1 7 6Zm10 0a9.6 9.6 0 0 1-1.3 3.6c1.4-.6 2.4-1.9 2.7-3.6H17Z"
                fill="var(--color-gold)"
              />
            </svg>
            <span class="visually-hidden">🏆</span>
          </span>
          이번주 랭킹 TOP3
        </h2>
      </div>

      <div class="weekly-ranking__viewport">
        <button
          class="weekly-ranking__scroll-control weekly-ranking__scroll-control--previous"
          type="button"
          aria-label="이전 랭킹 보기"
          :disabled="currentRankingIndex === 0"
          @click="moveRanking(-1)"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M14.5 6L9 12l5.5 6"
              fill="none"
              stroke="currentColor"
              stroke-width="2.4"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
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
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M9.5 6l5.5 6-5.5 6"
              fill="none"
              stroke="currentColor"
              stroke-width="2.4"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
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
        <strong class="quick-action-strip__chevron" aria-hidden="true">
          <svg viewBox="0 0 24 24" focusable="false">
            <path
              d="M9 5l7 7-7 7"
              fill="none"
              stroke="currentColor"
              stroke-width="2.4"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </strong>
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
  display: block;
  width: min(1320px, 100%);
  min-height: 326px;
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid #e5e2fa;
  border-radius: 24px;
  background: var(--color-surface-soft);
  animation: page-fade-up 0.45s var(--ease-out) both;
}

.hero-banner__slides {
  position: relative;
  touch-action: pan-y;
  user-select: none;
  cursor: grab;
  transform: translateX(var(--hero-drag-offset, 0px));
  transition: transform 0.3s ease;
}

.hero-banner__slides--dragging {
  cursor: grabbing;
  transition: none;
}

.hero-banner__slides img {
  -webkit-user-drag: none;
  user-select: none;
}

.hero-banner__slide {
  display: grid;
  grid-template-columns: 0.92fr 1.08fr;
  animation: hero-slide-fade 0.45s ease;
}

@keyframes hero-slide-fade {
  from {
    opacity: 0;
    transform: translateX(14px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
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
  line-height: 1.28;
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
  color: var(--color-ink-soft);
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
  background: var(--color-primary);
  box-shadow: 0 9px 19px rgba(16, 37, 68, 0.22);
  font-size: 16px;
  font-weight: 800;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    background-color 0.2s ease;
}

.hero-banner__cta:hover {
  background: var(--color-primary-hover);
  box-shadow: 0 12px 23px rgba(16, 37, 68, 0.32);
  transform: translateY(-2px);
}

.hero-banner__cta-icon {
  display: inline-flex;
  width: 15px;
  height: 15px;
}

.hero-banner__cta-icon svg {
  width: 100%;
  height: 100%;
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
  animation: hero-mascot-drift 5.5s ease-in-out infinite;
}

@keyframes hero-mascot-drift {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-7px);
  }
}

.hero-banner__bubble {
  position: absolute;
  top: 16px;
  left: 45%;
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

.hero-banner__preview {
  position: absolute;
  right: 6%;
  bottom: 26px;
  width: 224px;
  padding: 12px 12px 14px;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 16px 30px rgba(80, 62, 160, 0.18);
  transform: rotate(3.5deg);
}

.hero-banner__preview span {
  position: absolute;
  top: -12px;
  left: 16px;
  padding: 4px 12px;
  border-radius: 999px;
  color: #7a5a10;
  background: #ffd95e;
  box-shadow: 0 4px 10px rgba(122, 90, 16, 0.18);
  font-size: 13px;
  font-style: italic;
  font-weight: 900;
  letter-spacing: 0.04em;
}

.hero-banner__preview img {
  display: block;
  width: 100%;
  height: 128px;
  object-fit: cover;
  border-radius: 12px;
  background: #eeeffb;
}

.hero-banner__sparkle {
  position: absolute;
  z-index: 1;
  width: 28px;
  height: 28px;
  color: #c0a9ff;
  animation: sparkle-twinkle 3.2s ease-in-out infinite;
}

.hero-banner__sparkle svg {
  width: 100%;
  height: 100%;
}

@keyframes sparkle-twinkle {
  0%,
  100% {
    opacity: 0.45;
    transform: scale(0.92);
  }
  50% {
    opacity: 1;
    transform: scale(1.06);
  }
}

.hero-banner__sparkle--one {
  top: 18%;
  left: 5%;
  animation-delay: 0s;
}

.hero-banner__sparkle--two {
  top: 9%;
  right: 7%;
  animation-delay: 0.4s;
}

.hero-banner__sparkle--three {
  right: 40%;
  bottom: 16%;
  width: 38px;
  height: 38px;
  color: #b8a4ed;
  animation-delay: 0.8s;
}

.hero-banner__art {
  position: absolute;
  top: 50%;
  width: min(46%, 300px);
  border-radius: 18px;
  background: #eeeffb;
  padding: 14px;
  box-shadow: 0 14px 26px rgba(80, 62, 160, 0.16);
}

.hero-banner__art--tilt-left {
  left: 6%;
  transform: translateY(-56%) rotate(-5deg);
}

.hero-banner__art--tilt-right {
  right: 6%;
  transform: translateY(-40%) rotate(4deg);
}

.hero-banner__calm {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: min(52%, 300px);
  transform: translateX(-50%);
}

.hero-banner__rest-zzz {
  position: absolute;
  top: 16%;
  right: 22%;
  color: #7451dd;
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 0.2em;
  transform: rotate(-8deg);
}

.hero-banner__group {
  position: absolute;
  bottom: 12px;
  left: 50%;
  width: min(64%, 380px);
  transform: translateX(-50%);
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

.hero-banner__indicator {
  width: 12px;
  height: 12px;
  padding: 0;
  border: 2px solid #6d61b8;
  border-radius: 50%;
  background: #fff;
  cursor: pointer;
  transition:
    width var(--duration-base) var(--ease-out),
    background-color var(--duration-fast) ease;
}

.hero-banner__indicators .hero-banner__indicator--active {
  width: 26px;
  background: #5941c8;
}

.weekly-ranking {
  margin-top: 25px;
  animation: page-fade-up 0.45s var(--ease-out) both;
  animation-delay: 0.06s;
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
}

.weekly-ranking__trophy {
  display: inline-flex;
  width: 22px;
  height: 22px;
  margin-right: 10px;
  vertical-align: -4px;
}

.weekly-ranking__trophy svg {
  width: 100%;
  height: 100%;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
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
  cursor: pointer;
  transform: translateY(-50%);
  transition:
    opacity 0.2s ease,
    box-shadow 0.2s ease;
}

.weekly-ranking__scroll-control svg {
  width: 22px;
  height: 22px;
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
  width: min(1320px, 100%);
  grid-template-columns: repeat(3, 1fr);
  gap: 0;
  margin: 25px auto 0;
  padding: 18px 13px;
  border: 1px solid var(--color-line);
  border-radius: 19px;
  background: #fff;
  box-shadow: var(--shadow-card);
  animation: page-fade-up 0.45s var(--ease-out) both;
  animation-delay: 0.12s;
}

.quick-action-strip__item {
  display: flex;
  min-width: 0;
  min-height: 77px;
  align-items: center;
  gap: 17px;
  padding: 0 29px;
  border-right: 1px solid #dde1ea;
  border-radius: 12px;
  background: transparent;
  color: var(--color-ink);
  text-align: left;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.quick-action-strip__item:hover {
  background: var(--color-surface-soft);
}

.quick-action-strip__item:active {
  transform: scale(0.995);
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
  background: var(--color-blue-soft);
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

.quick-action-strip__chevron {
  display: inline-flex;
  width: 20px;
  height: 20px;
  margin-left: auto;
  color: var(--color-muted);
  transition: transform 0.2s ease;
}

.quick-action-strip__chevron svg {
  width: 100%;
  height: 100%;
}

.quick-action-strip__item:hover .quick-action-strip__chevron {
  transform: translateX(3px);
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

  .hero-banner__preview {
    right: 3%;
    width: 200px;
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

  .hero-banner__slide {
    display: block;
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
    top: 14px;
    left: 48%;
    width: 150px;
    padding: 15px 8px;
    font-size: 13px;
  }

  .hero-banner__preview {
    right: 5%;
    bottom: 22px;
    width: 160px;
  }

  .hero-banner__preview img {
    height: 92px;
  }

  .hero-banner__art {
    width: 52%;
  }

  .hero-banner__group {
    width: 78%;
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
  }

  .weekly-ranking__scroll-control svg {
    width: 18px;
    height: 18px;
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
