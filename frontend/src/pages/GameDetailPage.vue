<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GameRoomDialog from '../components/games/GameRoomDialog.vue'
import { useToast } from '../composables/useToast'
import { gameDetails, isGameDetailId } from '../mocks/game-details'
import { useAuthStore } from '../stores/auth'
import type { GamePlayMode } from '../types/game-detail'
import guideMascotImage from '../assets/images/brand/mascot-eye.png'
import profileJoyImage from '../assets/images/profiles/profile-joy.png'
import profileSmileImage from '../assets/images/profiles/profile-smile.png'
import profileWinkImage from '../assets/images/profiles/profile-wink.png'

const route = useRoute()
const router = useRouter()
const { showToast } = useToast()
const auth = useAuthStore()
const roomFlow = ref<'friends' | 'random'>('friends')
const isRoomDialogOpen = ref(false)

const isDescriptionOpen = ref(false)

const game = computed(() => {
  const gameId = String(route.params.gameId ?? '')
  return isGameDetailId(gameId) ? gameDetails[gameId] : undefined
})

const displayTitle = computed(
  () => game.value?.title.replace(/\s*\([^)]*\)\s*$/, '') ?? '',
)

const modeArtImages: Record<GamePlayMode['id'], string[]> = {
  solo: [profileJoyImage],
  ai: [profileWinkImage],
  friends: [profileJoyImage, profileWinkImage],
  random: [profileJoyImage, profileSmileImage],
}

watch(
  () => route.params.gameId,
  () => {
    isDescriptionOpen.value = false
  },
)

function handleSelectMode(mode: GamePlayMode) {
  if (!game.value) return
  if (mode.id === 'friends' || mode.id === 'random') {
    if (!auth.isAuthenticated) {
      auth.openLogin()
      showToast('친구 대결과 랜덤 매칭은 로그인 후 이용할 수 있어요.')
      return
    }

    roomFlow.value = mode.id
    isRoomDialogOpen.value = true
    return
  }

  router.push({
    name: 'game-ready',
    params: { gameId: game.value.id },
    query: { mode: mode.id },
  })
  showToast(`${game.value.title} ${mode.label} 모드는 준비 중이에요.`)
}

function toTokens(text: string) {
  return text
    .split('**')
    .map((part, index) => ({ text: part, highlight: index % 2 === 1 }))
}
function handleEnterRoom(payload: {
  mode: 'friends' | 'random'
  roomCode: string
  role?: 'host' | 'player'
}) {
  if (!game.value) return
  isRoomDialogOpen.value = false
  router.push({
    name: 'game-ready',
    params: { gameId: game.value.id },
    query: {
      mode: payload.mode,
      room: payload.roomCode,
      ...(payload.role ? { role: payload.role } : {}),
    },
  })
}
</script>

<template>
  <section v-if="game" class="game-detail-page">
    <RouterLink class="game-detail-page__back" to="/games"
      >← 게임 목록으로</RouterLink
    >

    <section class="game-detail-page__hero">
      <div class="game-detail-page__art">
        <span
          v-if="game.artCaption"
          class="game-detail-page__art-caption"
          aria-hidden="true"
          >{{ game.artCaption }}</span
        >
        <img :src="game.image" :alt="`${displayTitle} 게임 일러스트`" />
      </div>

      <div class="game-detail-page__intro">
        <div class="game-detail-page__title-row">
          <h1>{{ displayTitle }}</h1>
          <button
            type="button"
            class="game-detail-page__description-button"
            @click="isDescriptionOpen = true"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="9" />
              <path d="M12 8h.01M12 11.5V16" />
            </svg>
            게임 설명
          </button>
        </div>
        <p class="game-detail-page__subtitle">{{ game.subtitle }}</p>

        <dl class="game-detail-page__stats">
          <div class="game-detail-page__stat">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="8" r="4" />
              <path d="M4.5 20c1-4 3.8-6 7.5-6s6.5 2 7.5 6" />
            </svg>
            <div>
              <dt>권장 인원</dt>
              <dd>{{ game.people }}</dd>
            </div>
          </div>
          <div class="game-detail-page__stat">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="9" />
              <path d="M12 7v5l3.5 2" />
            </svg>
            <div>
              <dt>{{ game.durationLabel ?? '제한 시간' }}</dt>
              <dd>{{ game.duration }}</dd>
            </div>
          </div>
        </dl>

        <div class="game-detail-page__tags" aria-label="게임 태그">
          <span
            v-for="(tag, index) in game.tags"
            :key="tag"
            :class="`game-detail-page__tag--${['blue', 'mint', 'yellow'][index % 3]}`"
            >{{ tag }}</span
          >
        </div>
      </div>
    </section>

    <section
      class="game-detail-page__modes"
      :class="{ 'game-detail-page__modes--compact': game.modes.length >= 4 }"
      aria-label="게임 모드 선택"
    >
      <button
        v-for="mode in game.modes"
        :key="mode.id"
        type="button"
        class="game-detail-page__mode"
        :class="`game-detail-page__mode--${mode.id}`"
        @click="handleSelectMode(mode)"
      >
        <span class="game-detail-page__mode-art" aria-hidden="true">
          <template v-if="mode.id === 'friends'">
            <svg
              class="game-detail-page__mode-deco game-detail-page__mode-deco--line"
              viewBox="0 0 100 6"
            >
              <path
                class="game-detail-page__deco-line"
                d="M3 3h94"
                stroke-dasharray="8 7"
              />
            </svg>
            <svg
              class="game-detail-page__mode-deco game-detail-page__mode-deco--heart"
              viewBox="0 0 60 52"
            >
              <ellipse cx="30" cy="20" rx="17" ry="15" fill="#dbe2fb" />
              <path d="M26 37l4 8 4-8z" fill="#dbe2fb" />
              <path
                d="M30 28s-7.2-4.4-9-8.6c-1.2-2.8.6-6 3.7-6 2.2 0 4 1.6 5.3 3.1 1.3-1.5 3.1-3.1 5.3-3.1 3.1 0 4.9 3.2 3.7 6-1.8 4.2-9 8.6-9 8.6z"
                fill="#6c79f0"
              />
            </svg>
          </template>
          <svg
            v-else-if="mode.id === 'random'"
            class="game-detail-page__mode-deco game-detail-page__mode-deco--globe"
            viewBox="0 0 64 64"
          >
            <circle cx="32" cy="32" r="26" />
            <ellipse cx="32" cy="32" rx="26" ry="11" />
            <ellipse cx="32" cy="32" rx="11" ry="26" />
            <path d="M6 32h52" />
          </svg>
          <svg
            v-else-if="mode.id === 'solo'"
            class="game-detail-page__mode-deco game-detail-page__mode-deco--sparkle"
            viewBox="0 0 64 64"
          >
            <path
              d="M40 6l3 10 10 3-10 3-3 10-3-10-10-3 10-3z"
              fill="#f6c443"
            />
            <path d="M16 36l2 6 6 2-6 2-2 6-2-6-6-2 6-2z" fill="#8f9bf0" />
          </svg>
          <img
            v-for="src in modeArtImages[mode.id]"
            :key="src"
            :src="src"
            alt=""
          />
          <svg
            v-if="mode.id === 'ai'"
            class="game-detail-page__mode-robot"
            viewBox="0 0 104 108"
            aria-hidden="true"
          >
            <path
              d="M66 12c5-5 11-6 16-3"
              fill="none"
              stroke="#17243d"
              stroke-width="3"
              stroke-linecap="round"
            />
            <ellipse
              cx="18"
              cy="42"
              rx="7"
              ry="10"
              fill="#6b7cf5"
              stroke="#17243d"
              stroke-width="2.6"
            />
            <ellipse
              cx="86"
              cy="42"
              rx="7"
              ry="10"
              fill="#6b7cf5"
              stroke="#17243d"
              stroke-width="2.6"
            />
            <ellipse
              cx="52"
              cy="40"
              rx="32"
              ry="28"
              fill="#fff"
              stroke="#17243d"
              stroke-width="3.2"
            />
            <rect x="30" y="24" width="44" height="28" rx="13" fill="#23272f" />
            <path
              d="M41 38c2.2-3.2 5.8-3.2 8 0M57 38c2.2-3.2 5.8-3.2 8 0"
              fill="none"
              stroke="#fff"
              stroke-width="2.8"
              stroke-linecap="round"
            />
            <path
              d="M47 44c3.2 2.6 6.8 2.6 10 0"
              fill="none"
              stroke="#fff"
              stroke-width="2.8"
              stroke-linecap="round"
            />
            <ellipse
              cx="52"
              cy="84"
              rx="24"
              ry="19"
              fill="#fff"
              stroke="#17243d"
              stroke-width="3.2"
            />
            <rect
              x="41"
              y="76"
              width="22"
              height="17"
              rx="5.5"
              fill="#6b7cf5"
            />
            <text
              x="52"
              y="88.5"
              text-anchor="middle"
              fill="#fff"
              font-size="11"
              font-weight="800"
            >
              AI
            </text>
            <ellipse
              cx="17"
              cy="72"
              rx="8"
              ry="7"
              fill="#fff"
              stroke="#17243d"
              stroke-width="3"
            />
            <ellipse
              cx="90"
              cy="80"
              rx="7.5"
              ry="7"
              fill="#fff"
              stroke="#17243d"
              stroke-width="3"
            />
            <circle cx="88" cy="18" r="9" fill="#7fd4a8" />
            <path
              d="M84.5 18l2.6 2.6 4.4-5"
              fill="none"
              stroke="#fff"
              stroke-width="2.4"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </span>
        <strong>{{ mode.label }}</strong>
        <span v-if="mode.badge" class="game-detail-page__mode-badge">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M7 4h10v5a5 5 0 0 1-10 0z" />
            <path d="M7 5H4a3 3 0 0 0 3 5M17 5h3a3 3 0 0 1-3 5" />
            <path d="M12 14v3M8.5 20h7" />
          </svg>
          {{ mode.badge }}
        </span>
        <small>{{ mode.description }}</small>
        <span class="game-detail-page__mode-arrow" aria-hidden="true">→</span>
      </button>
    </section>

    <div
      v-if="isDescriptionOpen"
      class="game-detail-page__dialog-backdrop"
      @click.self="isDescriptionOpen = false"
    >
      <div
        v-if="game.guide"
        class="game-detail-page__dialog game-detail-page__dialog--guide"
        role="dialog"
        aria-modal="true"
        aria-labelledby="game-description-title"
      >
        <button
          type="button"
          class="game-detail-page__dialog-x"
          aria-label="게임 설명 닫기"
          @click="isDescriptionOpen = false"
        >
          ✕
        </button>
        <h2 id="game-description-title" class="game-detail-page__guide-title">
          게임 설명
        </h2>

        <div class="game-detail-page__guide-intro">
          <img :src="guideMascotImage" alt="" />
          <div class="game-detail-page__guide-bubble">
            <p v-for="paragraph in game.guide.intro" :key="paragraph">
              <span
                v-for="(token, tokenIndex) in toTokens(paragraph)"
                :key="tokenIndex"
                :class="{ 'game-detail-page__highlight': token.highlight }"
                >{{ token.text }}</span
              >
            </p>
          </div>
        </div>

        <div
          v-if="game.guide.highlights?.length"
          class="game-detail-page__guide-highlights"
        >
          <div
            v-for="highlight in game.guide.highlights"
            :key="highlight.text"
            class="game-detail-page__guide-highlight"
          >
            <span
              class="game-detail-page__guide-highlight-icon"
              aria-hidden="true"
            >
              <svg v-if="highlight.icon === 'trophy'" viewBox="0 0 64 64">
                <path
                  d="M20 13h-9a9 9 0 0 0 10 9M44 13h9a9 9 0 0 1-10 9"
                  fill="none"
                  stroke="#e0a51f"
                  stroke-width="3.4"
                />
                <path
                  d="M20 8h24v14a12 12 0 0 1-24 0z"
                  fill="#f6c443"
                  stroke="#e0a51f"
                  stroke-width="2.4"
                />
                <path
                  d="M32 13l1.7 3.6 4 .5-2.9 2.7.7 3.9-3.5-1.9-3.5 1.9.7-3.9-2.9-2.7 4-.5z"
                  fill="#fff"
                />
                <path d="M29 34h6v8h-6z" fill="#e0a51f" />
                <path
                  d="M21 48c0-4 5-6 11-6s11 2 11 6v3H21z"
                  fill="#f6c443"
                  stroke="#e0a51f"
                  stroke-width="2.4"
                />
              </svg>
              <svg v-else-if="highlight.icon === 'timer'" viewBox="0 0 64 64">
                <path
                  d="M26 5h12M32 5v6M50 15l4-4"
                  fill="none"
                  stroke="#6b7cf5"
                  stroke-width="3.4"
                  stroke-linecap="round"
                />
                <circle
                  cx="32"
                  cy="37"
                  r="21"
                  fill="#fff"
                  stroke="#6b7cf5"
                  stroke-width="3.4"
                />
                <path
                  d="M32 24v13l8 5"
                  fill="none"
                  stroke="#6b7cf5"
                  stroke-width="3.2"
                  stroke-linecap="round"
                />
              </svg>
              <svg v-else-if="highlight.icon === 'goal'" viewBox="0 0 64 64">
                <path
                  d="M10 16v32M54 16v32M10 16h44"
                  fill="none"
                  stroke="#8f9bf0"
                  stroke-width="3.6"
                  stroke-linecap="round"
                />
                <path
                  d="M10 25h44M10 34h44M21 16v27M32 16v27M43 16v27"
                  fill="none"
                  stroke="#c9cef1"
                  stroke-width="2"
                />
                <ellipse
                  cx="32"
                  cy="51"
                  rx="11"
                  ry="5.5"
                  fill="#6b7cf5"
                  stroke="#17243d"
                  stroke-width="2.2"
                />
              </svg>
              <svg v-else viewBox="0 0 64 64">
                <path
                  d="M32 10v-5M18 14l-3-4M46 14l3-4M10 24l-4-2M54 24l4-2"
                  fill="none"
                  stroke="#17243d"
                  stroke-width="3"
                  stroke-linecap="round"
                />
                <path
                  d="M6 38c6-11 15-17 26-17s20 6 26 17c-6 11-15 17-26 17S12 49 6 38z"
                  fill="#fff"
                  stroke="#17243d"
                  stroke-width="3"
                />
                <circle cx="32" cy="38" r="11" fill="#8f9bf0" />
                <circle cx="32" cy="38" r="5" fill="#17243d" />
              </svg>
            </span>
            <p>
              <span
                v-for="(token, tokenIndex) in toTokens(highlight.text)"
                :key="tokenIndex"
                :class="{ 'game-detail-page__highlight': token.highlight }"
                >{{ token.text }}</span
              >
            </p>
          </div>
        </div>

        <div
          v-if="game.guide.difficulties"
          class="game-detail-page__guide-difficulties"
        >
          <h3>{{ game.guide.difficulties.title }}</h3>
          <div class="game-detail-page__guide-difficulty-grid">
            <div
              v-for="item in game.guide.difficulties.items"
              :key="item.label"
              class="game-detail-page__guide-difficulty"
              :class="`game-detail-page__guide-difficulty--${item.color}`"
            >
              <b>{{ item.label }}</b>
              <strong>{{ item.duration }}</strong>
            </div>
          </div>
        </div>

        <div
          v-if="game.guide.stepIcons?.length"
          class="game-detail-page__guide-bottom"
          :class="{
            'game-detail-page__guide-bottom--single':
              !game.guide.events?.length &&
              !game.guide.notes &&
              !game.guide.formula,
          }"
        >
          <div class="game-detail-page__steps game-detail-page__steps--guide">
            <h3>게임 방법</h3>
            <ol>
              <li v-for="(step, index) in game.steps" :key="step">
                <span class="game-detail-page__step-icon" aria-hidden="true">
                  <svg
                    v-if="(game.guide.stepIcons ?? [])[index] === 'eye'"
                    viewBox="0 0 24 24"
                  >
                    <path
                      d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7z"
                      fill="none"
                      stroke="#17243d"
                      stroke-width="1.8"
                    />
                    <circle cx="12" cy="12" r="3.4" fill="#17243d" />
                  </svg>
                  <svg
                    v-else-if="(game.guide.stepIcons ?? [])[index] === 'tally'"
                    viewBox="0 0 24 24"
                  >
                    <path
                      d="M4.5 5v14M9 5v14M13.5 5v14M18 5v14M2.5 16.5L21 8"
                      fill="none"
                      stroke="#4f74db"
                      stroke-width="1.9"
                      stroke-linecap="round"
                    />
                  </svg>
                  <svg
                    v-else-if="(game.guide.stepIcons ?? [])[index] === 'trophy'"
                    viewBox="0 0 24 24"
                  >
                    <path
                      d="M7 3h10v5a5 5 0 0 1-10 0z"
                      fill="#f6c443"
                      stroke="#e0a51f"
                      stroke-width="1.4"
                    />
                    <path
                      d="M7 4H3.5A3.5 3.5 0 0 0 7 8M17 4h3.5A3.5 3.5 0 0 1 17 8"
                      fill="none"
                      stroke="#e0a51f"
                      stroke-width="1.6"
                    />
                    <path d="M11 13h2v3h-2z" fill="#e0a51f" />
                    <path
                      d="M7.5 19c0-1.8 2-2.7 4.5-2.7s4.5.9 4.5 2.7v1h-9z"
                      fill="#f6c443"
                    />
                  </svg>
                  <svg
                    v-else-if="(game.guide.stepIcons ?? [])[index] === 'pencil'"
                    viewBox="0 0 24 24"
                  >
                    <path
                      d="M5 19l1-4L16.5 4.5a2.1 2.1 0 0 1 3 3L9 18z"
                      fill="#f6c443"
                      stroke="#e0a51f"
                      stroke-width="1.6"
                      stroke-linejoin="round"
                    />
                    <path
                      d="M14.5 6.5l3 3"
                      fill="none"
                      stroke="#e0a51f"
                      stroke-width="1.6"
                    />
                    <path
                      d="M4 21c2-.5 3.5-.5 5.5 0"
                      fill="none"
                      stroke="#4f74db"
                      stroke-width="1.8"
                      stroke-linecap="round"
                    />
                  </svg>
                  <svg
                    v-else-if="(game.guide.stepIcons ?? [])[index] === 'clock'"
                    viewBox="0 0 24 24"
                  >
                    <circle
                      cx="12"
                      cy="12"
                      r="8.5"
                      fill="#fff"
                      stroke="#17243d"
                      stroke-width="1.8"
                    />
                    <path
                      d="M12 7.5V12l3.2 2.2"
                      fill="none"
                      stroke="#17243d"
                      stroke-width="1.8"
                      stroke-linecap="round"
                    />
                  </svg>
                  <svg
                    v-else-if="(game.guide.stepIcons ?? [])[index] === 'space'"
                    viewBox="0 0 24 24"
                  >
                    <rect
                      x="3"
                      y="7"
                      width="18"
                      height="11"
                      rx="2.4"
                      fill="#fff"
                      stroke="#4f74db"
                      stroke-width="1.7"
                    />
                    <path
                      d="M8 14.5h8"
                      stroke="#4f74db"
                      stroke-width="1.7"
                      stroke-linecap="round"
                    />
                  </svg>
                  <svg
                    v-else-if="(game.guide.stepIcons ?? [])[index] === 'mouse'"
                    viewBox="0 0 24 24"
                  >
                    <rect
                      x="7"
                      y="3.5"
                      width="10"
                      height="17"
                      rx="5"
                      fill="#fff"
                      stroke="#17243d"
                      stroke-width="1.7"
                    />
                    <path
                      d="M12 7v3.5"
                      stroke="#17243d"
                      stroke-width="1.7"
                      stroke-linecap="round"
                    />
                  </svg>
                  <svg
                    v-else-if="(game.guide.stepIcons ?? [])[index] === 'list'"
                    viewBox="0 0 24 24"
                  >
                    <rect
                      x="4"
                      y="4"
                      width="16"
                      height="16"
                      rx="3"
                      fill="#fff"
                      stroke="#4f74db"
                      stroke-width="1.7"
                    />
                    <path
                      d="M8.5 9h7M8.5 12.5h7M8.5 16h4.5"
                      stroke="#4f74db"
                      stroke-width="1.6"
                      stroke-linecap="round"
                    />
                  </svg>
                  <svg
                    v-else-if="(game.guide.stepIcons ?? [])[index] === 'robot'"
                    viewBox="0 0 24 24"
                  >
                    <rect
                      x="5"
                      y="8"
                      width="14"
                      height="11"
                      rx="3.5"
                      fill="#7c88ec"
                    />
                    <path
                      d="M12 5v3M9.5 13h.01M14.5 13h.01"
                      stroke="#fff"
                      stroke-width="1.8"
                      stroke-linecap="round"
                    />
                    <circle cx="12" cy="4.5" r="1.3" fill="#7c88ec" />
                    <path
                      d="M9.5 16.2c1.6 1.1 3.4 1.1 5 0"
                      stroke="#fff"
                      stroke-width="1.6"
                      stroke-linecap="round"
                      fill="none"
                    />
                  </svg>
                  <svg
                    v-else-if="(game.guide.stepIcons ?? [])[index] === 'note'"
                    viewBox="0 0 24 24"
                  >
                    <path
                      d="M9.5 5v10"
                      stroke="#6b7cf5"
                      stroke-width="1.8"
                      stroke-linecap="round"
                      fill="none"
                    />
                    <path
                      d="M9.5 5c2.5-1.7 5-1.7 7.5 0v4c-2.5-1.7-5-1.7-7.5 0z"
                      fill="#6b7cf5"
                    />
                    <circle cx="7.5" cy="16.8" r="2.6" fill="#6b7cf5" />
                  </svg>
                  <svg
                    v-else-if="
                      (game.guide.stepIcons ?? [])[index] === 'heartbreak'
                    "
                    viewBox="0 0 24 24"
                  >
                    <path
                      d="M12 20S4.8 15.2 3.7 10.2C3 6.9 5.4 4.2 8.4 4.2c1.5 0 2.9.8 3.6 1.9.7-1.1 2.1-1.9 3.6-1.9 3 0 5.4 2.7 4.7 6-1.1 5-8.3 9.8-8.3 9.8z"
                      fill="#f06d7d"
                      stroke="#d94f60"
                      stroke-width="1.2"
                    />
                    <path
                      d="M12 6.5l-1.7 3.7 2.8 1.9-1.7 3.7"
                      fill="none"
                      stroke="#fff"
                      stroke-width="1.5"
                      stroke-linejoin="round"
                    />
                  </svg>
                  <svg
                    v-else-if="(game.guide.stepIcons ?? [])[index] === 'flame'"
                    viewBox="0 0 24 24"
                  >
                    <path
                      d="M12 3.2c3 3.1 5.8 5.6 5.8 9.6a5.8 5.8 0 1 1-11.6 0c0-2.2 1-3.7 2.2-5.2 0 1.5.7 2.6 1.8 3.1-.4-2.9.3-5.3 1.8-7.5z"
                      fill="#f59e2e"
                      stroke="#e07a12"
                      stroke-width="1.4"
                    />
                  </svg>
                  <svg v-else viewBox="0 0 24 24">
                    <path
                      d="M12 6c-1.5-3-6-3.4-6-1s4 2 6 1zM12 6c1.5-3 6-3.4 6-1s-4 2-6 1z"
                      fill="#f5a623"
                    />
                    <rect
                      x="4"
                      y="6.5"
                      width="16"
                      height="4"
                      rx="1.2"
                      fill="#7c88ec"
                    />
                    <rect
                      x="5.5"
                      y="10.5"
                      width="13"
                      height="9.5"
                      rx="1.6"
                      fill="#8f9bf0"
                    />
                    <rect
                      x="10.5"
                      y="6.5"
                      width="3"
                      height="13.5"
                      fill="#fff"
                    />
                  </svg>
                </span>
                <span class="game-detail-page__step-text">
                  <span
                    v-for="(token, tokenIndex) in toTokens(step)"
                    :key="tokenIndex"
                    :class="{ 'game-detail-page__highlight': token.highlight }"
                    >{{ token.text }}</span
                  >
                </span>
              </li>
            </ol>
          </div>

          <div
            v-if="game.guide.events?.length"
            class="game-detail-page__guide-events"
          >
            <h3>이벤트 예시</h3>
            <div>
              <article
                v-for="event in game.guide.events"
                :key="event.label"
                class="game-detail-page__guide-event"
                :class="`game-detail-page__guide-event--${event.color}`"
              >
                <span
                  class="game-detail-page__guide-event-icon"
                  aria-hidden="true"
                >
                  <svg v-if="event.icon === 'clock'" viewBox="0 0 48 48">
                    <circle
                      cx="24"
                      cy="24"
                      r="17"
                      fill="#fff"
                      stroke="#17243d"
                      stroke-width="2.6"
                    />
                    <path
                      d="M24 14v10l7 5"
                      fill="none"
                      stroke="#17243d"
                      stroke-width="2.6"
                      stroke-linecap="round"
                    />
                  </svg>
                  <svg v-else-if="event.icon === 'star'" viewBox="0 0 48 48">
                    <path
                      d="M24 6l4.8 9.7L39.5 17.3l-7.7 7.5 1.8 10.7L24 30.4l-9.6 5.1 1.8-10.7-7.7-7.5L19.2 15.7z"
                      fill="#f6c443"
                      stroke="#e0a51f"
                      stroke-width="1.8"
                      stroke-linejoin="round"
                    />
                    <path
                      d="M8 8l1 2.4L11.4 11.4l-2.4 1L8 14.8l-1-2.4-2.4-1L7 10.4zM41 34l.9 2.1 2.1.9-2.1.9-.9 2.1-.9-2.1-2.1-.9 2.1-.9z"
                      fill="#7fd4a8"
                    />
                  </svg>
                  <svg v-else viewBox="0 0 60 32">
                    <path
                      d="M8 8l10 8-10 8"
                      fill="none"
                      stroke="#17243d"
                      stroke-width="3"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                    <circle
                      cx="40"
                      cy="16"
                      r="12"
                      fill="#fff"
                      stroke="#17243d"
                      stroke-width="2.6"
                    />
                    <circle cx="42.5" cy="16" r="6" fill="#17243d" />
                    <circle cx="44.5" cy="13.5" r="2" fill="#fff" />
                  </svg>
                </span>
                <p>{{ event.label }}</p>
                <b>성공 시 보너스!</b>
              </article>
            </div>
          </div>
          <div
            v-else-if="game.guide.notes"
            class="game-detail-page__guide-notes"
          >
            <h3>{{ game.guide.notes.title }}</h3>
            <ul>
              <li v-for="item in game.guide.notes.items" :key="item">
                {{ item }}
              </li>
            </ul>
          </div>
          <div
            v-else-if="game.guide.formula"
            class="game-detail-page__guide-formula"
          >
            <h3>{{ game.guide.formula.title }}</h3>
            <div class="game-detail-page__guide-formula-parts">
              <template
                v-for="(part, partIndex) in game.guide.formula.parts"
                :key="part.label"
              >
                <span
                  v-if="partIndex > 0"
                  class="game-detail-page__guide-formula-plus"
                  aria-hidden="true"
                  >+</span
                >
                <span
                  class="game-detail-page__guide-formula-part"
                  :class="`game-detail-page__guide-formula-part--${part.color}`"
                >
                  <svg
                    v-if="part.icon === 'star'"
                    viewBox="0 0 24 24"
                    aria-hidden="true"
                  >
                    <path
                      d="M12 3l2.5 5.3 5.8.8-4.2 4 1 5.7-5.1-2.7-5.1 2.7 1-5.7-4.2-4 5.8-.8z"
                      fill="#8f9bf0"
                    />
                  </svg>
                  <svg
                    v-else-if="part.icon === 'flame'"
                    viewBox="0 0 24 24"
                    aria-hidden="true"
                  >
                    <path
                      d="M12 3.2c3 3.1 5.8 5.6 5.8 9.6a5.8 5.8 0 1 1-11.6 0c0-2.2 1-3.7 2.2-5.2 0 1.5.7 2.6 1.8 3.1-.4-2.9.3-5.3 1.8-7.5z"
                      fill="#f59e2e"
                    />
                  </svg>
                  <svg v-else viewBox="0 0 24 24" aria-hidden="true">
                    <path
                      d="M12 20S4.8 15.2 3.7 10.2C3 6.9 5.4 4.2 8.4 4.2c1.5 0 2.9.8 3.6 1.9.7-1.1 2.1-1.9 3.6-1.9 3 0 5.4 2.7 4.7 6-1.1 5-8.3 9.8-8.3 9.8z"
                      fill="#f06d7d"
                    />
                  </svg>
                  {{ part.label }}
                </span>
              </template>
            </div>
            <div class="game-detail-page__guide-formula-total">
              = {{ game.guide.formula.total }}
            </div>
          </div>
        </div>

        <div
          v-if="game.guide.cards?.length"
          class="game-detail-page__guide-cards"
        >
          <article
            v-for="(card, index) in game.guide.cards"
            :key="card.title"
            class="game-detail-page__guide-card"
            :class="`game-detail-page__guide-card--${card.color}`"
          >
            <h3>
              <b>{{ index + 1 }}</b>
              {{ card.title }}
              <small v-if="card.suffix">{{ card.suffix }}</small>
            </h3>
            <span class="game-detail-page__guide-icon" aria-hidden="true">
              <svg v-if="card.icon === 'timer'" viewBox="0 0 64 64">
                <path
                  d="M26 5h12M32 5v6M50 15l4-4"
                  fill="none"
                  stroke="#2f9e63"
                  stroke-width="3.4"
                  stroke-linecap="round"
                />
                <circle
                  cx="32"
                  cy="37"
                  r="21"
                  fill="#fff"
                  stroke="#2f9e63"
                  stroke-width="3.4"
                  stroke-dasharray="9 5"
                />
                <text
                  x="32"
                  y="45"
                  text-anchor="middle"
                  fill="#2f9e63"
                  font-size="22"
                  font-weight="800"
                >
                  {{ card.iconText }}
                </text>
              </svg>
              <svg v-else-if="card.icon === 'trophy'" viewBox="0 0 64 64">
                <path
                  d="M20 13h-9a9 9 0 0 0 10 9M44 13h9a9 9 0 0 1-10 9"
                  fill="none"
                  stroke="#e0a51f"
                  stroke-width="3.4"
                />
                <path
                  d="M20 8h24v14a12 12 0 0 1-24 0z"
                  fill="#f6c443"
                  stroke="#e0a51f"
                  stroke-width="2.4"
                />
                <path
                  d="M32 13l1.7 3.6 4 .5-2.9 2.7.7 3.9-3.5-1.9-3.5 1.9.7-3.9-2.9-2.7 4-.5z"
                  fill="#fff"
                />
                <path d="M29 34h6v8h-6z" fill="#e0a51f" />
                <path
                  d="M21 48c0-4 5-6 11-6s11 2 11 6v3H21z"
                  fill="#f6c443"
                  stroke="#e0a51f"
                  stroke-width="2.4"
                />
              </svg>
              <svg v-else-if="card.icon === 'gift'" viewBox="0 0 64 64">
                <path
                  d="M32 17c-4-8-15-9-15-3s10 5 15 3zM32 17c4-8 15-9 15-3s-10 5-15 3z"
                  fill="#f5a623"
                />
                <rect
                  x="12"
                  y="18"
                  width="40"
                  height="11"
                  rx="3"
                  fill="#7c88ec"
                />
                <rect
                  x="15"
                  y="29"
                  width="34"
                  height="24"
                  rx="4"
                  fill="#8f9bf0"
                />
                <rect x="28" y="18" width="8" height="35" fill="#fff" />
              </svg>
              <svg v-else-if="card.icon === 'heart'" viewBox="0 0 64 64">
                <path
                  d="M32 52S10 39.5 7.5 26.5C6 18.5 12 12 19.5 12c5 0 9.5 3 12.5 7 3-4 7.5-7 12.5-7C52 12 58 18.5 56.5 26.5 54 39.5 32 52 32 52z"
                  fill="#f06d7d"
                  stroke="#d94f60"
                  stroke-width="2.4"
                  stroke-linejoin="round"
                />
                <path
                  d="M50 8l1.4 3.3L54.7 12.7l-3.3 1.4L50 17.4l-1.4-3.3-3.3-1.4 3.3-1.4z"
                  fill="#f6c443"
                />
              </svg>
              <svg v-else-if="card.icon === 'robot'" viewBox="0 0 64 64">
                <path
                  d="M32 8v7M32 6.5a2.5 2.5 0 1 0 0-.01"
                  stroke="#6b7cf5"
                  stroke-width="3"
                  stroke-linecap="round"
                  fill="none"
                />
                <rect
                  x="12"
                  y="15"
                  width="40"
                  height="32"
                  rx="12"
                  fill="#fff"
                  stroke="#6b7cf5"
                  stroke-width="3"
                />
                <rect
                  x="19"
                  y="24"
                  width="26"
                  height="14"
                  rx="7"
                  fill="#17243d"
                />
                <circle cx="26.5" cy="31" r="2.6" fill="#7fd4ff" />
                <circle cx="37.5" cy="31" r="2.6" fill="#7fd4ff" />
                <path
                  d="M26 42.5c4 2.4 8 2.4 12 0"
                  stroke="#6b7cf5"
                  stroke-width="2.6"
                  stroke-linecap="round"
                  fill="none"
                />
                <path
                  d="M12 28H7M57 28h-5"
                  stroke="#6b7cf5"
                  stroke-width="3"
                  stroke-linecap="round"
                />
              </svg>
              <svg v-else-if="card.icon === 'rounds'" viewBox="0 0 64 64">
                <circle
                  cx="12"
                  cy="32"
                  r="8.5"
                  fill="#fff"
                  stroke="#f5a623"
                  stroke-width="2.6"
                />
                <circle
                  cx="32"
                  cy="32"
                  r="8.5"
                  fill="#fff"
                  stroke="#f5a623"
                  stroke-width="2.6"
                />
                <circle
                  cx="52"
                  cy="32"
                  r="8.5"
                  fill="#fff"
                  stroke="#f5a623"
                  stroke-width="2.6"
                />
                <text
                  x="12"
                  y="36.5"
                  text-anchor="middle"
                  fill="#f5a623"
                  font-size="12"
                  font-weight="800"
                >
                  1
                </text>
                <text
                  x="32"
                  y="36.5"
                  text-anchor="middle"
                  fill="#f5a623"
                  font-size="12"
                  font-weight="800"
                >
                  2
                </text>
                <text
                  x="52"
                  y="36.5"
                  text-anchor="middle"
                  fill="#f5a623"
                  font-size="12"
                  font-weight="800"
                >
                  3
                </text>
                <path
                  d="M22 32h1.5M40.5 32H42"
                  stroke="#f5a623"
                  stroke-width="2.4"
                  stroke-linecap="round"
                />
              </svg>
              <svg v-else-if="card.icon === 'rhythm'" viewBox="0 0 64 64">
                <path
                  d="M22 10v16"
                  fill="none"
                  stroke="#6b7cf5"
                  stroke-width="3"
                  stroke-linecap="round"
                />
                <path
                  d="M22 10c4.5-3 9-3 13.5 0v7C31 14 26.5 14 22 17z"
                  fill="#6b7cf5"
                />
                <circle cx="18" cy="27" r="5" fill="#6b7cf5" />
                <path
                  d="M12 46c5.5-8.5 12.5-13 20-13s14.5 4.5 20 13c-5.5 8.5-12.5 13-20 13s-14.5-4.5-20-13z"
                  fill="#fff"
                  stroke="#17243d"
                  stroke-width="2.8"
                />
                <circle cx="32" cy="46" r="7.5" fill="#8f9bf0" />
                <circle cx="32" cy="46" r="3.4" fill="#17243d" />
              </svg>
              <svg v-else-if="card.icon === 'combo'" viewBox="0 0 64 64">
                <path
                  d="M32 6c8.5 8.5 16 15.5 16 26.5a16 16 0 1 1-32 0c0-6 2.8-10.2 6-14.5 0 4.2 2 7.2 5 8.5-1.2-8 1-14.5 5-20.5z"
                  fill="#f59e2e"
                  stroke="#e07a12"
                  stroke-width="2.2"
                />
                <circle cx="32" cy="38" r="9.5" fill="#fff" />
                <text
                  x="32"
                  y="42.5"
                  text-anchor="middle"
                  fill="#e07a12"
                  font-size="12"
                  font-weight="800"
                >
                  10
                </text>
              </svg>
              <svg v-else-if="card.icon === 'hearts'" viewBox="0 0 64 64">
                <path
                  d="M16 34s-9-6-10.2-12C5 18 8 14.5 11.7 14.5c2 0 3.7 1.1 4.3 2.4.6-1.3 2.3-2.4 4.3-2.4 3.7 0 6.7 3.5 5.9 7.5C25 28 16 34 16 34z"
                  fill="#f06d7d"
                  stroke="#d94f60"
                  stroke-width="1.8"
                />
                <path
                  d="M40 30s-9-6-10.2-12C29 14 32 10.5 35.7 10.5c2 0 3.7 1.1 4.3 2.4.6-1.3 2.3-2.4 4.3-2.4 3.7 0 6.7 3.5 5.9 7.5C49 24 40 30 40 30z"
                  fill="#f06d7d"
                  stroke="#d94f60"
                  stroke-width="1.8"
                />
                <path
                  d="M30 54s-9-6-10.2-12C19 38 22 34.5 25.7 34.5c2 0 3.7 1.1 4.3 2.4.6-1.3 2.3-2.4 4.3-2.4 3.7 0 6.7 3.5 5.9 7.5C39 48 30 54 30 54z"
                  fill="#e3e5f0"
                  stroke="#c2c6da"
                  stroke-width="1.8"
                />
              </svg>
              <svg v-else viewBox="0 0 64 64">
                <path
                  d="M32 8l6.4 13 14.3 2.1-10.3 10 2.4 14.2L32 40.6 19.2 47.3l2.4-14.2-10.3-10L25.6 21z"
                  fill="#f6c443"
                  stroke="#e0a51f"
                  stroke-width="2.4"
                  stroke-linejoin="round"
                />
                <path
                  d="M9 12l1.5 3.5L14 17l-3.5 1.5L9 22l-1.5-3.5L4 17l3.5-1.5zM55 40l1.2 2.8 2.8 1.2-2.8 1.2L55 48l-1.2-2.8L51 44l2.8-1.2z"
                  fill="#7fd4a8"
                />
              </svg>
            </span>
            <p>{{ card.description }}</p>
            <span
              v-if="card.badge"
              class="game-detail-page__guide-badge"
              :class="`game-detail-page__guide-badge--${card.badgeColor ?? card.color}`"
              >{{ card.badge }}</span
            >
          </article>
        </div>

        <button
          type="button"
          class="game-detail-page__dialog-close"
          @click="isDescriptionOpen = false"
        >
          확인
        </button>
      </div>

      <div
        v-else
        class="game-detail-page__dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="game-description-title"
      >
        <div class="game-detail-page__steps">
          <h2 id="game-description-title">게임 방법</h2>
          <ol>
            <li v-for="(step, index) in game.steps" :key="step">
              <b>{{ index + 1 }}</b>
              <span>{{ step }}</span>
            </li>
          </ol>
        </div>
        <img
          class="game-detail-page__dialog-mascot"
          :src="game.mascotImage"
          alt=""
        />
        <button
          type="button"
          class="game-detail-page__dialog-close"
          @click="isDescriptionOpen = false"
        >
          닫기
        </button>
      </div>
    </div>

    <GameRoomDialog
      :open="isRoomDialogOpen"
      :game-title="game.title"
      :flow="roomFlow"
      @close="isRoomDialogOpen = false"
      @enter-room="handleEnterRoom"
    />
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
  grid-template-columns: minmax(320px, 0.9fr) minmax(0, 1.1fr);
  gap: clamp(30px, 5vw, 72px);
  align-items: center;
}
.game-detail-page__art {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 340px;
  padding: 30px;
  border-radius: 28px;
  background: #eeeffb;
}
.game-detail-page__art img {
  width: min(100%, 460px);
  height: 280px;
  object-fit: contain;
}
.game-detail-page__art-caption {
  position: absolute;
  top: 34px;
  left: 60%;
  color: #33b579;
  font-family: 'Jua', 'Noto Sans KR', sans-serif;
  font-size: 26px;
  transform: rotate(-8deg);
}
.game-detail-page__title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.game-detail-page__title-row h1 {
  margin: 0;
  color: var(--color-ink);
  font-size: clamp(38px, 4.2vw, 58px);
  word-break: keep-all;
}
.game-detail-page__description-button {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 7px;
  margin-top: 10px;
  padding: 11px 18px;
  border: 1.5px solid var(--color-accent-blue);
  border-radius: 13px;
  color: var(--color-accent-blue);
  background: #fff;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
}
.game-detail-page__description-button:hover {
  background: var(--color-blue-soft);
}
.game-detail-page__description-button svg {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
}
.game-detail-page__subtitle {
  margin: 16px 0 0;
  color: var(--color-ink);
  font-size: 18px;
  line-height: 1.6;
  word-break: keep-all;
}
.game-detail-page__stats {
  display: flex;
  align-items: center;
  gap: clamp(24px, 3vw, 44px);
  margin: 34px 0 30px;
}
.game-detail-page__stat {
  display: flex;
  align-items: center;
  gap: 14px;
}
.game-detail-page__stat + .game-detail-page__stat {
  padding-left: clamp(24px, 3vw, 44px);
  border-left: 1px solid var(--color-line);
}
.game-detail-page__stat svg {
  width: 34px;
  height: 34px;
  fill: none;
  stroke: var(--color-ink);
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.game-detail-page__stat dt {
  color: var(--color-muted);
  font-size: 13px;
}
.game-detail-page__stat dd {
  margin: 3px 0 0;
  color: var(--color-ink);
  font-size: 20px;
  font-weight: 800;
}
.game-detail-page__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.game-detail-page__tags span {
  padding: 9px 16px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 800;
}
.game-detail-page__tag--blue {
  color: #4a5fd3;
  background: #e9edfd;
}
.game-detail-page__tag--mint {
  color: #2f9e63;
  background: #e4f6ec;
}
.game-detail-page__tag--yellow {
  color: #c08a2d;
  background: #fdf3dc;
}
.game-detail-page__modes {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
  margin-top: 30px;
  padding: clamp(24px, 3vw, 40px);
  border: 1px solid var(--color-line);
  border-radius: 24px;
  background: #fff;
  box-shadow: var(--shadow-card);
}
.game-detail-page__modes--compact {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  padding: clamp(18px, 2.2vw, 26px);
}
.game-detail-page__modes--compact .game-detail-page__mode {
  gap: 8px;
  padding: 18px 16px 22px;
}
.game-detail-page__modes--compact .game-detail-page__mode-art {
  min-height: 76px;
  margin-bottom: 4px;
}
.game-detail-page__modes--compact .game-detail-page__mode-art img {
  width: 62px;
  height: 62px;
}
.game-detail-page__modes--compact .game-detail-page__mode-robot {
  width: 66px;
  margin-left: 12px;
}
.game-detail-page__modes--compact .game-detail-page__mode-deco--sparkle {
  top: -6px;
  left: 42px;
  width: 38px;
  height: 38px;
}
.game-detail-page__modes--compact .game-detail-page__mode strong {
  font-size: 19px;
}
.game-detail-page__modes--compact .game-detail-page__mode small {
  padding-right: 34px;
  font-size: 13px;
}
.game-detail-page__modes--compact .game-detail-page__mode-badge {
  top: 12px;
  right: 12px;
  padding: 6px 10px;
  font-size: 11.5px;
}
.game-detail-page__modes--compact .game-detail-page__mode-arrow {
  right: 14px;
  bottom: 20px;
  width: 34px;
  height: 34px;
  font-size: 16px;
}
.game-detail-page__modes--compact .game-detail-page__mode-deco--heart {
  top: -12px;
  width: 40px;
  height: 35px;
}
.game-detail-page__modes--compact .game-detail-page__mode-deco--line {
  bottom: 28px;
  width: 76px;
}
.game-detail-page__modes--compact
  .game-detail-page__mode--friends
  .game-detail-page__mode-art
  img
  + img {
  margin-left: 34px;
}
.game-detail-page__modes--compact .game-detail-page__mode-deco--globe {
  top: -14px;
  width: 60px;
  height: 60px;
}
.game-detail-page__mode {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 26px 24px 30px;
  border: 0;
  border-radius: 20px;
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease;
}
.game-detail-page__mode:hover {
  box-shadow: var(--shadow-float);
  transform: translateY(-2px);
}
.game-detail-page__mode:active {
  transform: translateY(0);
}
.game-detail-page__mode-art {
  position: relative;
  display: flex;
  align-items: flex-end;
  min-height: 108px;
  margin-bottom: 8px;
}
.game-detail-page__mode-art img {
  position: relative;
  z-index: 1;
  width: 88px;
  height: 88px;
  object-fit: contain;
}
.game-detail-page__mode-art img + img {
  margin-left: 26px;
}
.game-detail-page__mode-robot {
  position: relative;
  z-index: 1;
  width: 96px;
  height: auto;
  margin-left: 20px;
}
.game-detail-page__mode--friends .game-detail-page__mode-art img + img {
  margin-left: 54px;
}
.game-detail-page__mode-deco {
  position: absolute;
  z-index: 0;
}
.game-detail-page__mode-deco--sparkle {
  top: -8px;
  left: 62px;
  width: 52px;
  height: 52px;
}
.game-detail-page__mode-deco--heart {
  top: -18px;
  left: 50%;
  z-index: 2;
  width: 54px;
  height: 47px;
  transform: translateX(-50%);
}
.game-detail-page__mode-deco--line {
  bottom: 40px;
  left: 50%;
  width: 104px;
  height: 6px;
  transform: translateX(-50%);
}
.game-detail-page__deco-line {
  fill: none;
  stroke: #6c79f0;
  stroke-width: 3.5;
  stroke-linecap: round;
}
.game-detail-page__mode-deco--globe {
  top: -22px;
  left: 50%;
  width: 86px;
  height: 86px;
  fill: none;
  stroke: #8f9bf0;
  stroke-width: 1.6;
  transform: translateX(-50%);
}
.game-detail-page__mode strong {
  font-size: 25px;
  font-weight: 800;
  letter-spacing: -0.03em;
}
.game-detail-page__mode small {
  padding-right: 64px;
  color: #2c3a55;
  font-size: 15px;
  font-weight: 500;
  line-height: 1.55;
  word-break: keep-all;
}
.game-detail-page__mode-badge {
  position: absolute;
  top: 18px;
  right: 18px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 800;
}
.game-detail-page__mode-badge svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.game-detail-page__mode-arrow {
  position: absolute;
  right: 22px;
  bottom: 32px;
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  color: #fff;
  font-size: 21px;
  font-weight: 700;
}
.game-detail-page__mode--solo {
  background: #edf9f1;
}
.game-detail-page__mode--solo strong {
  color: #22a06b;
}
.game-detail-page__mode--solo .game-detail-page__mode-badge {
  color: #fff;
  background: #22a06b;
  box-shadow: 0 4px 12px rgba(34, 160, 107, 0.35);
}
.game-detail-page__mode--solo .game-detail-page__mode-arrow {
  background: #1fa15e;
}
.game-detail-page__mode--ai {
  background: #edf3fd;
}
.game-detail-page__mode--ai strong {
  color: #4f74db;
}
.game-detail-page__mode--ai .game-detail-page__mode-badge {
  color: #fff;
  background: #4f74db;
  box-shadow: 0 4px 12px rgba(79, 116, 219, 0.35);
}
.game-detail-page__mode--ai .game-detail-page__mode-arrow {
  background: #4f86f7;
}
.game-detail-page__mode--friends {
  background: var(--color-yellow-soft);
}
.game-detail-page__mode--friends strong {
  color: #f5a623;
}
.game-detail-page__mode--friends .game-detail-page__mode-arrow {
  background: #f5a623;
}
.game-detail-page__mode--random {
  background: #efeffc;
}
.game-detail-page__mode--random strong {
  color: #6b7cf5;
}
.game-detail-page__mode--random .game-detail-page__mode-badge {
  color: #fff;
  background: #6b7cf5;
  box-shadow: 0 4px 12px rgba(107, 124, 245, 0.35);
}
.game-detail-page__mode--random .game-detail-page__mode-arrow {
  background: #6b7cf5;
}
.game-detail-page__dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(23, 36, 61, 0.45);
}
.game-detail-page__dialog {
  position: relative;
  width: min(480px, 100%);
  padding: 34px 32px 28px;
  border-radius: 24px;
  background: #fff;
  box-shadow: var(--shadow-float);
}
.game-detail-page__dialog--guide {
  width: min(1100px, 100%);
  max-height: 92vh;
  padding: 42px clamp(24px, 4vw, 52px) 34px;
  overflow-y: auto;
  border-radius: 28px;
}
.game-detail-page__dialog-x {
  position: absolute;
  top: 22px;
  right: 26px;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 10px;
  color: var(--color-ink);
  background: transparent;
  font-size: 22px;
  cursor: pointer;
}
.game-detail-page__dialog-x:hover {
  background: var(--color-surface-soft);
}
.game-detail-page__guide-title {
  position: relative;
  width: fit-content;
  margin: 0 auto;
  padding: 12px 48px;
  border: 1px solid #dcdff6;
  border-radius: 10px;
  color: #6b7cf5;
  background: #fbfbff;
  font-size: clamp(26px, 3vw, 36px);
}
.game-detail-page__guide-title::before,
.game-detail-page__guide-title::after {
  content: '';
  position: absolute;
  top: -11px;
  width: 46px;
  height: 18px;
  background: rgba(143, 155, 240, 0.35);
}
.game-detail-page__guide-title::before {
  left: -20px;
  transform: rotate(-38deg);
}
.game-detail-page__guide-title::after {
  right: -20px;
  transform: rotate(38deg);
}
.game-detail-page__guide-intro {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: clamp(18px, 3vw, 40px);
  align-items: center;
  margin: 30px 0 26px;
}
.game-detail-page__guide-intro > img {
  width: clamp(120px, 16vw, 190px);
  height: auto;
  object-fit: contain;
}
.game-detail-page__guide-bubble {
  position: relative;
  padding: 24px 28px;
  border: 1px solid #e4e6f8;
  border-radius: 20px;
  background: #f6f6fd;
}
.game-detail-page__guide-bubble::before {
  content: '';
  position: absolute;
  top: 50%;
  left: -9px;
  width: 16px;
  height: 16px;
  border-bottom: 1px solid #e4e6f8;
  border-left: 1px solid #e4e6f8;
  background: #f6f6fd;
  transform: translateY(-50%) rotate(45deg);
}
.game-detail-page__guide-bubble p {
  margin: 0;
  color: var(--color-ink);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.7;
  white-space: pre-line;
  word-break: keep-all;
}
.game-detail-page__guide-bubble p + p {
  margin-top: 14px;
}
.game-detail-page__highlight {
  color: var(--color-accent-blue);
  font-weight: 800;
}
.game-detail-page__guide-highlights {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  margin-top: 24px;
  padding: 28px 18px;
  border: 1px solid #dcdff6;
  border-radius: 18px;
  background: #fbfbff;
}
.game-detail-page__guide-highlight {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 6px 14px;
  text-align: center;
}
.game-detail-page__guide-highlight + .game-detail-page__guide-highlight {
  border-left: 1.5px dashed #c9cef1;
}
.game-detail-page__guide-highlight-icon svg {
  width: 64px;
  height: 64px;
}
.game-detail-page__guide-highlight p {
  margin: 0;
  color: var(--color-ink);
  font-size: 15px;
  font-weight: 700;
  line-height: 1.6;
  white-space: pre-line;
  word-break: keep-all;
}
.game-detail-page__guide-difficulties {
  margin-top: 18px;
  padding: 24px 26px;
  border: 1px solid #e9ebf7;
  border-radius: 18px;
  background: #f8f9fe;
}
.game-detail-page__guide-difficulties h3 {
  margin: 0;
  color: var(--color-ink);
  font-size: 19px;
}
.game-detail-page__guide-difficulty-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 13px;
  margin-top: 18px;
}
.game-detail-page__guide-difficulty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 18px 12px;
  border-radius: 14px;
  text-align: center;
}
.game-detail-page__guide-difficulty b {
  font-size: 14px;
  font-weight: 800;
}
.game-detail-page__guide-difficulty strong {
  color: var(--color-ink);
  font-size: 22px;
  font-weight: 800;
}
.game-detail-page__guide-difficulty--green {
  background: #eff9f2;
}
.game-detail-page__guide-difficulty--green b {
  color: #2f9e63;
}
.game-detail-page__guide-difficulty--orange {
  background: #fdf4e8;
}
.game-detail-page__guide-difficulty--orange b {
  color: #e8842e;
}
.game-detail-page__guide-difficulty--purple {
  background: #f1f2fd;
}
.game-detail-page__guide-difficulty--purple b {
  color: #6b7cf5;
}
.game-detail-page__guide-difficulty--blue {
  background: #f3f7fe;
}
.game-detail-page__guide-difficulty--blue b {
  color: #4f86f7;
}
.game-detail-page__guide-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 18px;
  margin-top: 18px;
}
.game-detail-page__guide-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 22px 16px 20px;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  text-align: center;
}
.game-detail-page__guide-card h3 {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  font-size: 18px;
}
.game-detail-page__guide-card h3 b {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: #fff;
  font-size: 13px;
}
.game-detail-page__guide-card h3 small {
  font-size: 13px;
  font-weight: 800;
}
.game-detail-page__guide-icon svg {
  width: 74px;
  height: 74px;
}
.game-detail-page__guide-card p {
  margin: 0;
  color: var(--color-ink);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.6;
  white-space: pre-line;
  word-break: keep-all;
}
.game-detail-page__guide-badge {
  padding: 7px 13px;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 800;
}
.game-detail-page__guide-badge--green {
  color: #1f9d5f;
  background: #ddf2e4;
}
.game-detail-page__guide-badge--orange {
  color: #e8842e;
  background: #fdeedd;
}
.game-detail-page__guide-badge--purple {
  color: #6b7cf5;
  background: #e7e9fc;
}
.game-detail-page__guide-badge--blue {
  color: #4f74db;
  background: #e4edfd;
}
.game-detail-page__guide-badge--pink {
  color: #e05a7a;
  background: #fbe3ea;
}
.game-detail-page__guide-card--green {
  background: #f2faf4;
}
.game-detail-page__guide-card--green h3 {
  color: #2f9e63;
}
.game-detail-page__guide-card--green h3 b {
  background: #2f9e63;
}
.game-detail-page__guide-card--purple {
  background: #f3f4fd;
}
.game-detail-page__guide-card--purple h3 {
  color: #6b7cf5;
}
.game-detail-page__guide-card--purple h3 b {
  background: #7c88ec;
}
.game-detail-page__guide-card--orange {
  background: #fdf6ec;
}
.game-detail-page__guide-card--orange h3 {
  color: #f5a623;
}
.game-detail-page__guide-card--orange h3 b {
  background: #f5a623;
}
.game-detail-page__guide-card--blue {
  background: #f3f7fe;
}
.game-detail-page__guide-card--blue h3 {
  color: #4f86f7;
}
.game-detail-page__guide-card--blue h3 b {
  background: #4f86f7;
}
.game-detail-page__guide-card--pink {
  background: #fdf1f5;
}
.game-detail-page__guide-card--pink h3 {
  color: #e05a7a;
}
.game-detail-page__guide-card--pink h3 b {
  background: #e05a7a;
}
.game-detail-page__guide-bottom {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: 18px;
  margin-top: 18px;
}
.game-detail-page__guide-bottom--single {
  grid-template-columns: 1fr;
}
.game-detail-page__guide-notes ul {
  display: grid;
  gap: 12px;
  margin: 18px 0 0;
  padding: 0;
  list-style: none;
}
.game-detail-page__guide-notes li {
  position: relative;
  padding-left: 16px;
  color: var(--color-ink);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.65;
  word-break: keep-all;
}
.game-detail-page__guide-notes li::before {
  content: '';
  position: absolute;
  top: 9px;
  left: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-accent-blue);
}
.game-detail-page__guide-formula {
  padding: 24px 26px;
  border: 1px solid #e9ebf7;
  border-radius: 18px;
  background: #f8f9fe;
}
.game-detail-page__guide-formula h3 {
  margin: 0;
  color: var(--color-ink);
  font-size: 19px;
}
.game-detail-page__guide-formula-parts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-top: 18px;
}
.game-detail-page__guide-formula-plus {
  color: var(--color-muted);
  font-size: 18px;
  font-weight: 800;
}
.game-detail-page__guide-formula-part {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 800;
}
.game-detail-page__guide-formula-part svg {
  width: 18px;
  height: 18px;
}
.game-detail-page__guide-formula-part--purple {
  color: #6b7cf5;
  background: #eef0fd;
}
.game-detail-page__guide-formula-part--orange {
  color: #e8842e;
  background: #fdf1e2;
}
.game-detail-page__guide-formula-part--pink {
  color: #e05a7a;
  background: #fdeef2;
}
.game-detail-page__guide-formula-total {
  margin-top: 16px;
  padding: 13px;
  border: 1px solid #dcdff6;
  border-radius: 12px;
  color: var(--color-accent-blue);
  background: #fff;
  font-size: 17px;
  font-weight: 800;
  text-align: center;
}
.game-detail-page__steps--guide,
.game-detail-page__guide-events,
.game-detail-page__guide-notes {
  padding: 24px 26px;
  border: 1px solid #e9ebf7;
  border-radius: 18px;
  background: #f8f9fe;
}
.game-detail-page__steps--guide h3,
.game-detail-page__guide-events h3,
.game-detail-page__guide-notes h3 {
  margin: 0;
  color: var(--color-ink);
  font-size: 19px;
}
.game-detail-page__steps--guide ol {
  display: grid;
  gap: 13px;
  margin: 18px 0 0;
  padding: 0;
  list-style: none;
}
.game-detail-page__steps--guide li {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  color: var(--color-ink);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.6;
}
.game-detail-page__step-icon {
  display: inline-flex;
  flex: 0 0 auto;
  margin-top: 2px;
}
.game-detail-page__step-icon svg {
  width: 20px;
  height: 20px;
}
.game-detail-page__step-text {
  white-space: pre-line;
  word-break: keep-all;
}
.game-detail-page__guide-events > div {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 13px;
  margin-top: 18px;
}
.game-detail-page__guide-event {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 18px 12px 16px;
  border-radius: 14px;
  text-align: center;
}
.game-detail-page__guide-event-icon svg {
  width: 44px;
  height: 44px;
}
.game-detail-page__guide-event p {
  margin: 0;
  color: var(--color-ink);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.5;
  white-space: pre-line;
  word-break: keep-all;
}
.game-detail-page__guide-event b {
  font-size: 13px;
  font-weight: 800;
}
.game-detail-page__guide-event--purple {
  background: #f1f2fd;
}
.game-detail-page__guide-event--purple b {
  color: #6b7cf5;
}
.game-detail-page__guide-event--green {
  background: #eff9f2;
}
.game-detail-page__guide-event--green b {
  color: #2f9e63;
}
.game-detail-page__guide-event--orange {
  background: #fdf4e8;
}
.game-detail-page__guide-event--orange b {
  color: #e8842e;
}
.game-detail-page__dialog--guide .game-detail-page__dialog-close {
  display: block;
  width: min(420px, 100%);
  margin: 28px auto 0;
}
.game-detail-page__steps h2 {
  margin: 0;
  color: var(--color-ink);
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
.game-detail-page__dialog-mascot {
  position: absolute;
  top: 24px;
  right: 28px;
  width: 62px;
  height: 62px;
  object-fit: contain;
}
.game-detail-page__dialog-close {
  width: 100%;
  margin-top: 26px;
  padding: 13px;
  border: 0;
  border-radius: 13px;
  color: #fff;
  background: var(--color-accent-blue);
  font-size: 15px;
  font-weight: 800;
  cursor: pointer;
}
.game-detail-page__dialog-close:hover {
  background: #4064c9;
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

@media (max-width: 1000px) {
  .game-detail-page__hero {
    grid-template-columns: minmax(280px, 1fr) 1fr;
    gap: 30px;
  }
  .game-detail-page__guide-bottom {
    grid-template-columns: 1fr;
  }
  .game-detail-page__modes--compact {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 820px) {
  .game-detail-page__hero {
    grid-template-columns: 1fr;
  }
  .game-detail-page__art {
    min-height: 280px;
  }
  .game-detail-page__stats {
    flex-wrap: wrap;
    row-gap: 14px;
  }
  .game-detail-page__guide-highlights {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    row-gap: 18px;
  }
  .game-detail-page__guide-highlight + .game-detail-page__guide-highlight {
    border-left: 0;
  }
  .game-detail-page__guide-highlight:nth-child(even) {
    border-left: 1.5px dashed #c9cef1;
  }
  .game-detail-page__guide-highlight:nth-child(n + 3) {
    border-top: 1.5px dashed #c9cef1;
  }
}
@media (max-width: 640px) {
  .game-detail-page {
    padding-top: 20px;
  }
  .game-detail-page__hero {
    display: block;
  }
  .game-detail-page__art {
    min-height: 250px;
    margin-bottom: 24px;
    padding: 20px;
  }
  .game-detail-page__art img {
    height: 210px;
  }
  .game-detail-page__title-row {
    flex-direction: column;
  }
  .game-detail-page__description-button {
    margin-top: 0;
  }
  .game-detail-page__stats {
    margin: 24px 0 20px;
  }
  .game-detail-page__guide-intro {
    grid-template-columns: 1fr;
    justify-items: center;
  }
  .game-detail-page__guide-bubble::before {
    display: none;
  }
  .game-detail-page__guide-highlights {
    grid-template-columns: 1fr;
  }
  .game-detail-page__guide-highlight + .game-detail-page__guide-highlight {
    border-top: 1.5px dashed #c9cef1;
    border-left: 0;
  }
  .game-detail-page__guide-highlight:nth-child(even) {
    border-left: 0;
  }
  .game-detail-page__guide-formula-parts {
    flex-direction: column;
    align-items: stretch;
  }
  .game-detail-page__guide-formula-plus {
    text-align: center;
  }
  .game-detail-page__guide-formula-part {
    justify-content: center;
  }
  .game-detail-page__modes--compact {
    grid-template-columns: 1fr;
  }
}
</style>
