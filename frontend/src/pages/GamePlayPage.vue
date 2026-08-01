<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DrawPromptIcon from '../components/games/DrawPromptIcon.vue'
import GamePlayShell from '../components/games/GamePlayShell.vue'
import { createMockSession, gameModeLabels } from '../mocks/gameplay'
import { gameDetails, isGameDetailId } from '../mocks/game-details'
import type { GameSessionMode } from '../types/gameplay'
import { useLiveKitRoom } from '../composables/useLiveKitRoom'
import { useLocalCamera } from '../composables/useLocalCamera'
import { useGameResultSubmission } from '../composables/useGameResultSubmission'
import { useMediaSessionStore } from '../stores/mediaSession'

const route = useRoute()
const router = useRouter()
const rhythmMine = ref({ score: 1240, combo: 12, hearts: 4 })
const rhythmOpponent = ref({ score: 1180, combo: 8, hearts: 4 })
const blinkCount = ref(28)
const drawScoreOpen = ref(false)
const drawRound = ref(1)
const selectedColor = ref('#4f74db')
const eyeSeeState = ref<'playing' | 'success' | 'failed'>('playing')
const holdElapsedTenths = ref(186)

const holdElapsedLabel = computed(() => {
  const totalSeconds = Math.floor(holdElapsedTenths.value / 10)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  const tenths = holdElapsedTenths.value % 10

  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}.${tenths}`
})

let holdTimer: ReturnType<typeof globalThis.setInterval> | undefined

async function initMedia() {
  // 내 웹캠은 항상 표시한다(솔로 포함).
  const stream = await startLocalCamera()
  // 대결(친구/랜덤)이고 대기방에서 받은 접속 정보가 있으면 내 트랙을 송출하고 상대 웹캠을 구독한다.
  if (showsOpponentCamera.value && mediaSession.credentials) {
    await connectMedia(mediaSession.credentials, {
      localTrack: stream?.getVideoTracks()[0] ?? null,
    })
  }
}

onMounted(() => {
  if (usesLocalCamera.value) void initMedia()
  if (game.value?.id === 'hold') {
    holdTimer = globalThis.setInterval(() => {
      holdElapsedTenths.value += 1
    }, 100)
  }
})

onUnmounted(() => {
  if (holdTimer) globalThis.clearInterval(holdTimer)
})

const drawRoundResults = [
  {
    prompt: '안경',
    score: 180,
    timeBonus: 40,
    confidenceBonus: 40,
    confidence: '82%',
  },
  {
    prompt: '우산',
    score: 230,
    timeBonus: 50,
    confidenceBonus: 80,
    confidence: '85%',
  },
  {
    prompt: '고양이',
    score: 270,
    timeBonus: 60,
    confidenceBonus: 110,
    confidence: '90%',
  },
] as const

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
const session = computed(() =>
  game.value
    ? createMockSession(
        game.value.id,
        mode.value,
        String(route.query.room ?? ''),
      )
    : undefined,
)
const displayTitle = computed(
  () => game.value?.title.replace(/\s*\([^)]*\)\s*$/, '') ?? '',
)
const isCompetitive = computed(() =>
  ['ai', 'friends', 'random'].includes(mode.value),
)
const isRhythmDuel = computed(
  () =>
    game.value?.id === 'rhythm' && ['friends', 'random'].includes(mode.value),
)

// 눈싸움(hold) 대결: 상대 웹캠을 실제 미디어 서버로 주고받는다.
const mediaSession = useMediaSessionStore()
const {
  remoteVideoRef,
  hasRemoteVideo,
  connect: connectMedia,
} = useLiveKitRoom()
const hasPeerCamera = computed(() => hasRemoteVideo.value)

// 내 웹캠(로컬 셀프뷰)은 미디어 서버와 무관하게 getUserMedia로 항상 보여준다(솔로 포함).
const {
  videoRef: localCameraVideoRef,
  isActive: isLocalCameraActive,
  start: startLocalCamera,
} = useLocalCamera()

// 웹캠을 쓰는 게임.
const usesLocalCamera = computed(() =>
  ['hold', 'rhythm', 'draw', 'blink', 'air'].includes(game.value?.id ?? ''),
)
// 상대 웹캠까지 주고받는 게임(대결 모드 한정).
const showsOpponentCamera = computed(
  () =>
    ['hold', 'rhythm', 'blink', 'air'].includes(game.value?.id ?? '') &&
    ['friends', 'random'].includes(mode.value),
)
const colorSwatchNames: Record<string, string> = {
  '#161c2d': '검정',
  '#e84d59': '빨강',
  '#2864df': '파랑',
  '#35a968': '초록',
  '#f2b11e': '노랑',
}

function heartStates(count: number) {
  return Array.from({ length: 5 }, (_, index) => index < count)
}

const currentDrawResult = computed(() => drawRoundResults[drawRound.value - 1])
const drawAccumulatedScore = computed(() =>
  drawRoundResults
    .slice(0, drawRound.value)
    .reduce((total, result) => total + result.score, 0),
)

// 게임 종료 시 결과를 저장하는 파이프라인(지금은 mock 값). 실패해도 화면 전환은 막지 않는다.
const { submitPlayedResult } = useGameResultSubmission()
const playStartedAt = new Date().toISOString()

function toResult() {
  if (!game.value) return
  void submitPlayedResult({
    gameSlug: game.value.id,
    mode: mode.value,
    startedAt: playStartedAt,
    score: session.value?.score ?? 0,
  })
  router.push({
    name: 'game-result',
    params: { gameId: game.value.id },
    query: route.query,
  })
}

function leaveGame() {
  if (game.value)
    router.push({ name: 'game-detail', params: { gameId: game.value.id } })
}

function handleRhythmInput(
  player: 'mine' | 'opponent' = 'mine',
  isMiss = false,
) {
  const playerStatus =
    player === 'mine' ? rhythmMine.value : rhythmOpponent.value

  if (isMiss) {
    playerStatus.hearts = Math.max(0, playerStatus.hearts - 1)
    playerStatus.combo = 0

    if (playerStatus.hearts === 0) toResult()

    return
  }

  playerStatus.combo += 1
  playerStatus.score += 100
}

function advanceDrawRound() {
  drawScoreOpen.value = false

  if (drawRound.value < drawRoundResults.length) {
    drawRound.value += 1
    selectedColor.value = '#4f74db'
    return
  }

  toResult()
}
</script>

<template>
  <GamePlayShell
    v-if="game && session"
    :title="game.id === 'draw' ? '눈으로 그리기' : displayTitle"
    :mode-label="gameModeLabels[mode]"
    :time-label="game.id === 'hold' ? holdElapsedLabel : session.timeLabel"
    :time-caption="game.id === 'hold' ? '현재 생존 시간' : undefined"
    :round-progress="
      game.id === 'draw'
        ? { current: drawRound, total: drawRoundResults.length }
        : undefined
    "
    :show-score="game.id !== 'draw' && game.id !== 'hold' && !isRhythmDuel"
    :score="
      game.id === 'blink'
        ? `${blinkCount}회`
        : game.id === 'rhythm'
          ? `${rhythmMine.score.toLocaleString()}점`
          : `${session.score}${game.id === 'draw' ? '점' : ''}`
    "
    @leave="leaveGame"
  >
    <section
      class="gameplay-layout"
      :class="[
        `gameplay-layout--${game.id}`,
        { 'gameplay-layout--hold-solo': game.id === 'hold' && mode === 'solo' },
        {
          'gameplay-layout--blink-solo': game.id === 'blink' && mode === 'solo',
        },
        { 'gameplay-layout--rhythm-duel': isRhythmDuel },
      ]"
    >
      <aside v-if="game.id === 'draw'" class="info-panel draw-info">
        <p class="eyebrow">제시어</p>
        <strong>{{ currentDrawResult.prompt }}</strong>
        <DrawPromptIcon :prompt="currentDrawResult.prompt" />
        <ol aria-label="라운드 진행 상황">
          <li
            v-for="round in drawRoundResults.length"
            :key="round"
            :class="{
              active: drawRound === round,
              complete: drawRound > round,
            }"
          >
            Round {{ round }}
            <b>{{
              drawRound > round
                ? '완료'
                : drawRound === round
                  ? '진행 중'
                  : '대기'
            }}</b>
          </li>
        </ol>
        <section class="draw-tip" aria-label="그리기 팁">
          <p>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M9 18h6M10 21h4M12 3a6 6 0 0 0-3.6 10.8c.6.46 1 .96 1.1 1.6l.1.6h5l.1-.6c.1-.64.5-1.14 1.1-1.6A6 6 0 0 0 12 3z"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            TIP
          </p>
          <span>Space 키를 눌러 그림 그리기를 일시정지할 수 있어요!</span>
          <kbd>Space</kbd>
          <small>일시정지 / 다시 시작</small>
        </section>
      </aside>

      <aside v-else-if="game.id === 'hold'" class="info-panel eye-see-info">
        <p class="eyebrow">나</p>
        <div class="eye-see-camera">
          <video
            ref="localCameraVideoRef"
            class="eye-see-camera__self"
            aria-label="내 웹캠 영상"
            autoplay
            muted
            playsinline
          ></video>
          <img
            v-if="!isLocalCameraActive"
            :src="game.mascotImage"
            alt="내 카메라 준비 마스코트"
            draggable="false"
          />
        </div>
        <p class="camera-state">
          {{
            isLocalCameraActive
              ? '내 카메라가 연결되었습니다.'
              : '내 카메라를 준비하고 있어요.'
          }}
        </p>
      </aside>

      <aside v-else-if="game.id === 'air'" class="air-score-panel">
        <p class="air-score-label">SCORE</p>
        <div class="air-score-line">
          <span>나</span><strong>{{ session.score }}</strong>
        </div>
        <span class="air-score-vs">VS</span>
        <div class="air-score-line opponent">
          <span>{{ mode === 'ai' ? 'AI' : '상대' }}</span
          ><strong>{{ session.opponentScore }}</strong>
        </div>
        <div class="air-time">
          <span>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle
                cx="12"
                cy="12"
                r="8.5"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
              />
              <path
                d="M12 7.5V12l3.2 2.2"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
              />
            </svg>
            남은 시간
          </span>
          <strong>{{ session.timeLabel }}</strong>
        </div>
        <p class="air-tip">
          <b>TIP</b> 시선으로 패들을 움직여 퍽을 상대 골대에 넣어보세요.
        </p>
      </aside>

      <aside
        v-else-if="isRhythmDuel"
        class="info-panel rhythm-duel-player rhythm-duel-player--mine"
      >
        <p class="rhythm-duel-player__label">나 · PLAYER 1</p>
        <div class="rhythm-duel-player__camera">
          <video
            ref="localCameraVideoRef"
            class="self-camera"
            aria-label="내 웹캠 영상"
            autoplay
            muted
            playsinline
          ></video>
          <img
            v-if="!isLocalCameraActive"
            :src="game.mascotImage"
            alt="내 웹캠 대기 마스코트"
            draggable="false"
          />
          <span>내 웹캠</span>
        </div>
        <p class="rhythm-duel-player__camera-note">
          {{
            isLocalCameraActive
              ? '내 카메라가 연결되었습니다.'
              : '카메라를 준비하고 있어요.'
          }}
        </p>
        <section class="rhythm-player-stats" aria-label="내 리듬 게임 상태">
          <div>
            <span>점수</span
            ><strong>{{ rhythmMine.score.toLocaleString() }}</strong>
          </div>
          <div>
            <span>콤보</span><strong>{{ rhythmMine.combo }} 콤보</strong>
          </div>
          <div class="rhythm-player-stats__hearts">
            <span>체력</span>
            <b aria-label="내 남은 체력">
              <svg
                v-for="(filled, index) in heartStates(rhythmMine.hearts)"
                :key="index"
                viewBox="0 0 24 24"
                :class="{ 'heart-empty': !filled }"
                aria-hidden="true"
              >
                <path
                  d="M12 21c-4.7-3-9-6.6-9-11a4.6 4.6 0 0 1 9-1.8A4.6 4.6 0 0 1 21 10c0 4.4-4.3 8-9 11z"
                  fill="currentColor"
                />
              </svg>
            </b>
          </div>
        </section>
      </aside>

      <aside
        v-else-if="game.id === 'rhythm'"
        class="info-panel rhythm-score-panel"
      >
        <p>Score</p>
        <strong>{{ rhythmMine.score.toLocaleString() }}</strong>
        <hr />
        <p>Combo</p>
        <b>{{ rhythmMine.combo }} 콤보!</b>
        <div class="hearts" aria-label="남은 체력">
          <svg
            v-for="(filled, index) in heartStates(rhythmMine.hearts)"
            :key="index"
            viewBox="0 0 24 24"
            :class="{ 'heart-empty': !filled }"
            aria-hidden="true"
          >
            <path
              d="M12 21c-4.7-3-9-6.6-9-11a4.6 4.6 0 0 1 9-1.8A4.6 4.6 0 0 1 21 10c0 4.4-4.3 8-9 11z"
              fill="currentColor"
            />
          </svg>
        </div>
      </aside>

      <aside
        v-else-if="game.id === 'blink' && isCompetitive"
        class="blink-duel-player blink-duel-player--mine"
      >
        <div class="blink-duel-player__score">
          <span>나</span>
          <strong>{{ blinkCount }}<em>회</em></strong>
        </div>
        <div class="blink-duel-player__camera">
          <video
            ref="localCameraVideoRef"
            class="self-camera"
            aria-label="내 웹캠 영상"
            autoplay
            muted
            playsinline
          ></video>
          <p v-if="!isLocalCameraActive">
            내 카메라를 준비하고 있어요.
          </p>
        </div>
        <small>눈이 감지되면 카운터가 올라가요!</small>
      </aside>

      <aside
        v-else-if="!(game.id === 'blink' && mode === 'solo')"
        class="info-panel player-panel"
      >
        <p class="eyebrow">나의 플레이 영역</p>
        <div class="video-placeholder">
          <video
            aria-label="향후 내 웹캠 영상이 표시될 영역"
            muted
            playsinline
          ></video>
          <img
            :src="game.mascotImage"
            alt="게임 준비 마스코트"
            draggable="false"
          />
          <span>실제 웹캠 영상은 게임 연동 후 표시됩니다.</span>
        </div>
        <p class="tip">20초 동안 정확하게 눈을 깜빡여 보세요.</p>
      </aside>

      <section class="gameplay-board" aria-live="polite">
        <template v-if="game.id === 'draw'">
          <div class="stage-toolbar">
            <span class="status-dot">그리기 중</span
            ><b>Round {{ drawRound }} / {{ drawRoundResults.length }}</b>
          </div>
          <div class="draw-canvas" aria-label="mock 드로잉 캔버스">
            <i /><i /><i /><i />
            <span>시선 입력과 AI 채점은 연결 준비 중입니다.</span>
          </div>
          <div class="draw-tools" aria-label="드로잉 도구">
            <button
              v-for="color in [
                '#161c2d',
                '#e84d59',
                '#2864df',
                '#35a968',
                '#f2b11e',
              ]"
              :key="color"
              type="button"
              :aria-label="`색상 선택 ${colorSwatchNames[color] ?? color}`"
              :class="{ selected: selectedColor === color }"
              :style="{ background: color }"
              @click="selectedColor = color"
            />
            <button type="button">되돌리기</button
            ><button type="button">전체 지우기</button>
            <button type="button" class="primary" @click="drawScoreOpen = true">
              제출하기
            </button>
          </div>
        </template>

        <template v-else-if="game.id === 'rhythm'">
          <div class="rhythm-top">
            <span>시간 {{ session.timeLabel }} / 00:30</span
            ><progress value="55" max="100">55%</progress>
          </div>
          <div class="rhythm-stage">
            <div
              v-for="lane in ['왼쪽 눈 감기', '오른쪽 눈 감기']"
              :key="lane"
              class="rhythm-lane"
            >
              <b>{{ lane }}</b
              ><span class="hit-zone" />
              <i
                v-for="note in 4"
                :key="note"
                :style="{ left: `${30 + note * 16}%` }"
                >●</i
              >
            </div>
          </div>
          <div class="rhythm-controls">
            <button type="button" @click="handleRhythmInput('mine')">
              왼쪽 눈 입력</button
            ><button type="button" @click="handleRhythmInput('mine')">
              오른쪽 눈 입력</button
            ><button type="button" @click="handleRhythmInput('mine', true)">
              miss (mock)
            </button>
            <button
              v-if="isRhythmDuel"
              type="button"
              class="rhythm-opponent-miss"
              data-testid="opponent-rhythm-miss"
              @click="handleRhythmInput('opponent', true)"
            >
              상대 miss (mock)
            </button>
          </div>
        </template>

        <template v-else-if="game.id === 'blink' && isCompetitive">
          <section class="blink-duel-stage" aria-live="polite">
            <article class="blink-duel-event">
              <span>이벤트 발동!</span>
              <strong>깜빡임 챌린지</strong>
              <p>지금 3회 더 깜빡이면 보너스를 받아요.</p>
            </article>
            <section class="blink-duel-timer" aria-label="남은 시간">
              <span>남은 시간</span>
              <strong>{{ session.timeLabel }}</strong>
              <div><i></i></div>
              <small>20초</small>
            </section>
            <p class="blink-duel-rule">
              제한 시간 안에 더 많이 깜빡인 사람이 승리해요!
            </p>
            <section class="blink-duel-history" aria-label="이벤트 발동 내역">
              <b>이벤트 발동 내역</b>
              <p>
                <span>성공</span> 눈빛 좋은 플레이어 (나) <strong>+3회</strong>
              </p>
              <p><span>대기</span> 상대 플레이어의 다음 입력을 기다려요.</p>
            </section>
            <button type="button" @click="blinkCount += 1">
              mock 깜빡임 +1
            </button>
          </section>
        </template>

        <template v-else-if="game.id === 'blink'">
          <section class="blink-stage" aria-label="눈 깜빡이기 플레이 영역">
            <video
              ref="localCameraVideoRef"
              class="blink-stage__camera self-camera"
              aria-label="내 웹캠 영상"
              autoplay
              muted
              playsinline
            ></video>
            <p v-if="!isLocalCameraActive" class="blink-stage__camera-placeholder">
              내 카메라를 준비하고 있어요.
            </p>

            <section class="blink-stage__metrics" aria-label="게임 진행 현황">
              <span>남은 시간</span>
              <strong>{{ session.timeLabel }}</strong>
              <div class="blink-stage__progress" aria-label="제한 시간 진행률">
                <i></i>
              </div>
              <small>20초</small>
              <span>현재 깜빡임 횟수</span>
              <b>{{ blinkCount }}<em>회</em></b>
            </section>

            <aside class="blink-stage__tip" aria-label="게임 팁">
              <b>TIP</b>
              <p>
                눈을 자연스럽게 깜빡여요!<br />너무 세게 감지 않아도 괜찮아요.
              </p>
            </aside>
          </section>
          <footer class="blink-stage__footer">
            <p>20초가 끝나면 자동으로 기록이 저장돼요!</p>
            <button type="button" @click="blinkCount += 1">
              mock 깜빡임 +1
            </button>
          </footer>
        </template>

        <template v-else-if="game.id === 'hold'">
          <div class="eye-see-status">
            <span>눈싸움 진행 중</span>
            <p>먼저 눈을 깜빡이면 지는 게임! 끝까지 시선을 유지해 보세요.</p>
          </div>
          <div class="hold-controls">
            <button type="button" @click="eyeSeeState = 'playing'">진행</button
            ><button type="button" @click="eyeSeeState = 'success'">
              성공 (mock)</button
            ><button type="button" @click="eyeSeeState = 'failed'">
              실패 (mock)
            </button>
          </div>
        </template>

        <template v-else>
          <div class="hockey-status">
            <b>{{ session.score }}</b
            ><span>VS</span><b>{{ session.opponentScore }}</b
            ><small>남은 시간 {{ session.timeLabel }}</small>
          </div>
          <div class="hockey-rink" aria-label="mock 에어하키 보드">
            <i class="goal goal--top" /><i class="goal goal--bottom" /><i
              class="puck"
            /><i class="paddle paddle--mine" /><i
              class="paddle paddle--opponent"
            />
          </div>
          <p class="tip">패들 이동과 물리 충돌은 게임 연동 후 적용됩니다.</p>
        </template>
      </section>

      <aside
        v-if="isRhythmDuel"
        class="info-panel rhythm-duel-player rhythm-duel-player--opponent"
      >
        <p class="rhythm-duel-player__label">
          {{ mode === 'friends' ? '친구' : '매칭된 상대' }} · PLAYER 2
        </p>
        <div class="rhythm-duel-player__camera">
          <video
            ref="remoteVideoRef"
            aria-label="상대 웹캠 영상"
            autoplay
            playsinline
          ></video>
          <img
            v-if="!hasPeerCamera"
            :src="game.mascotImage"
            alt="상대 웹캠 대기 마스코트"
            draggable="false"
          />
          <span>상대 웹캠</span>
        </div>
        <section
          class="rhythm-opponent-screen"
          aria-label="향후 상대 게임 화면이 표시될 영역"
        >
          <b>상대 게임 화면</b>
          <div aria-hidden="true"><i /><i /><i /><i /></div>
          <small>게임 화면 연동 후 표시됩니다.</small>
        </section>
        <section class="rhythm-player-stats" aria-label="상대 리듬 게임 상태">
          <div>
            <span>점수</span
            ><strong>{{ rhythmOpponent.score.toLocaleString() }}</strong>
          </div>
          <div>
            <span>콤보</span><strong>{{ rhythmOpponent.combo }} 콤보</strong>
          </div>
          <div class="rhythm-player-stats__hearts">
            <span>체력</span>
            <b aria-label="상대 남은 체력">
              <svg
                v-for="(filled, index) in heartStates(rhythmOpponent.hearts)"
                :key="index"
                viewBox="0 0 24 24"
                :class="{ 'heart-empty': !filled }"
                aria-hidden="true"
              >
                <path
                  d="M12 21c-4.7-3-9-6.6-9-11a4.6 4.6 0 0 1 9-1.8A4.6 4.6 0 0 1 21 10c0 4.4-4.3 8-9 11z"
                  fill="currentColor"
                />
              </svg>
            </b>
          </div>
        </section>
      </aside>
      <aside
        v-else-if="game.id === 'draw' || game.id === 'rhythm'"
        class="info-panel webcam-panel"
      >
        <p class="eyebrow">나의 웹캠</p>
        <div class="video-placeholder">
          <video
            ref="localCameraVideoRef"
            class="self-camera"
            aria-label="내 웹캠 영상"
            autoplay
            muted
            playsinline
          ></video
          ><img
            v-if="!isLocalCameraActive"
            :src="game.mascotImage"
            alt="웹캠 대기 마스코트"
            draggable="false"
          />
        </div>
        <p class="camera-state">
          {{ isLocalCameraActive ? '카메라 연결됨' : '카메라 준비 중' }}
        </p>
        <p v-if="game.id === 'rhythm'" class="tip">
          분홍 노트는 왼쪽, 파랑 노트는 오른쪽 눈 입력입니다.
        </p>
      </aside>
      <aside v-else-if="game.id === 'air'" class="air-players-panel">
        <article class="air-player-card">
          <div>
            <strong>{{ mode === 'ai' ? 'AI' : '상대' }}</strong
            ><span>OPPONENT</span>
          </div>
          <div class="air-player-card__camera">
            <video
              ref="remoteVideoRef"
              aria-label="상대 웹캠 영상"
              autoplay
              playsinline
            ></video>
            <img
              v-if="!hasPeerCamera"
              :src="game.image"
              alt="상대 플레이어 안내 이미지"
              draggable="false"
            />
          </div>
        </article>
        <article class="air-player-card you">
          <div><strong>나</strong><span>YOU</span></div>
          <div class="air-player-card__camera">
            <video
              ref="localCameraVideoRef"
              class="self-camera"
              aria-label="내 웹캠 영상"
              autoplay
              muted
              playsinline
            ></video>
            <img
              v-if="!isLocalCameraActive"
              :src="game.mascotImage"
              alt="내 플레이어 마스코트"
              draggable="false"
            />
          </div>
        </article>
      </aside>
      <aside
        v-else-if="game.id === 'blink' && isCompetitive"
        class="blink-duel-player blink-duel-player--opponent"
      >
        <div class="blink-duel-player__score">
          <span>{{ mode === 'friends' ? '친구' : '매칭된 상대' }}</span>
          <strong>{{ session.opponentScore }}<em>회</em></strong>
        </div>
        <div class="blink-duel-player__camera">
          <video
            ref="remoteVideoRef"
            aria-label="상대 웹캠 영상"
            autoplay
            playsinline
          ></video>
          <p v-if="!hasPeerCamera">상대 카메라를 기다리고 있어요.</p>
        </div>
        <small>상대보다 더 많이 깜빡여 보세요!</small>
      </aside>
      <aside v-else-if="isCompetitive" class="info-panel opponent-panel">
        <template v-if="game.id === 'hold'">
          <p class="eyebrow">친구</p>
          <div class="eye-see-camera eye-see-camera--friend">
            <video
              ref="remoteVideoRef"
              aria-label="친구 웹캠 영상"
              autoplay
              playsinline
            ></video>
            <img
              v-if="!hasPeerCamera"
              :src="game.image"
              alt="친구 카메라 준비 안내 이미지"
              draggable="false"
            />
          </div>
          <p class="camera-state">
            {{
              hasPeerCamera
                ? '친구 카메라가 연결되었습니다.'
                : '친구 카메라를 기다리고 있어요.'
            }}
          </p>
        </template>
        <template v-else>
          <p class="eyebrow">
            {{ mode === 'ai' ? 'AI 상대' : '상대 플레이어' }}
          </p>
          <img
            :src="game.image"
            alt="상대 플레이어 안내 이미지"
            draggable="false"
          />
          <b>{{ mode === 'ai' ? 'AI 집중 중' : '상대 연결 준비 중' }}</b>
        </template>
      </aside>
    </section>
    <button type="button" class="finish" @click="toResult">
      mock 게임 종료 · 결과 보기
    </button>

    <Teleport to="body"
      ><Transition name="dialog-pop"
        ><div
          v-if="drawScoreOpen"
          class="score-backdrop"
          @click.self="drawScoreOpen = false"
        >
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="draw-score-title"
          >
            <button
              class="dialog-close"
              type="button"
              aria-label="채점 결과 닫기"
              @click="drawScoreOpen = false"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M6 6l12 12M18 6L6 18"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                />
              </svg>
            </button>
            <header class="draw-score-heading">
              <div>
                <p class="eyebrow">AI 채점 결과 · mock</p>
                <h2 id="draw-score-title">이번 라운드 그림을 분석했어요!</h2>
              </div>
            </header>

            <div class="draw-score-columns">
              <section class="draw-submission-card" aria-label="제출한 그림">
                <p>제시어</p>
                <strong>{{ currentDrawResult.prompt }}</strong>
                <DrawPromptIcon
                  :prompt="currentDrawResult.prompt"
                  size="medium"
                />
                <div
                  class="draw-score-sketch"
                  aria-label="mock으로 표현한 제출 그림"
                >
                  <i /><i /><i /><i />
                </div>
                <p class="draw-correct">
                  정답입니다! {{ currentDrawResult.prompt }}을 맞혔어요!
                </p>
              </section>

              <section class="draw-score-detail" aria-label="라운드 점수 상세">
                <span class="draw-score-ribbon"
                  >ROUND {{ drawRound }} 점수</span
                >
                <strong class="draw-round-total"
                  >{{ currentDrawResult.score }}점</strong
                >
                <dl class="draw-score-breakdown">
                  <div>
                    <dt>
                      <b aria-hidden="true"
                        ><svg viewBox="0 0 24 24">
                          <circle
                            cx="12"
                            cy="12"
                            r="8"
                            fill="none"
                            stroke="currentColor"
                            stroke-width="1.8"
                          />
                          <circle
                            cx="12"
                            cy="12"
                            r="3"
                            fill="currentColor"
                          /></svg></b
                      ><span
                        >기본 점수<small
                          >정답을 맞혀 기본 점수를 획득했어요!</small
                        ></span
                      >
                    </dt>
                    <dd>100점</dd>
                  </div>
                  <div>
                    <dt>
                      <b aria-hidden="true"
                        ><svg viewBox="0 0 24 24">
                          <circle
                            cx="12"
                            cy="12"
                            r="8.5"
                            fill="none"
                            stroke="currentColor"
                            stroke-width="1.8"
                          />
                          <path
                            d="M12 7.5V12l3.2 2.2"
                            fill="none"
                            stroke="currentColor"
                            stroke-width="1.8"
                            stroke-linecap="round"
                          /></svg></b
                      ><span
                        >시간 보너스<small
                          >남은 시간에 따른 보너스 점수예요.</small
                        ></span
                      >
                    </dt>
                    <dd>+{{ currentDrawResult.timeBonus }}점</dd>
                  </div>
                  <div>
                    <dt>
                      <b aria-hidden="true"
                        ><svg viewBox="0 0 24 24">
                          <path
                            d="M12 2l2.4 7.4H22l-6.2 4.5 2.4 7.4-6.2-4.6-6.2 4.6 2.4-7.4L2 9.4h7.6z"
                            fill="currentColor"
                          /></svg></b
                      ><span
                        >AI Confidence 보너스<small
                          >mock 분석 결과에 따른 보너스예요.</small
                        ></span
                      >
                    </dt>
                    <dd>+{{ currentDrawResult.confidenceBonus }}점</dd>
                  </div>
                </dl>
                <div class="draw-round-sum">
                  <span>ROUND {{ drawRound }} 총점</span>
                  <b>{{ currentDrawResult.score }}점</b>
                </div>
                <p class="draw-confidence">
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <circle
                      cx="12"
                      cy="12"
                      r="9"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.6"
                    />
                    <path
                      d="M12 8h.01M12 11.5V16"
                      stroke="currentColor"
                      stroke-width="1.6"
                      stroke-linecap="round"
                    />
                  </svg>
                  AI Confidence: <b>{{ currentDrawResult.confidence }}</b> (높은
                  확신)
                </p>
              </section>
            </div>

            <section class="draw-cumulative" aria-label="누적 점수 현황">
              <h3>전체 점수 현황</h3>
              <div class="draw-score-equation">
                <template
                  v-for="(round, index) in drawRoundResults.slice(0, drawRound)"
                  :key="round.prompt"
                >
                  <article :class="{ current: index + 1 === drawRound }">
                    <span>ROUND {{ index + 1 }}</span>
                    <strong>{{ round.score }}점</strong>
                  </article>
                  <b
                    v-if="index < drawRound - 1"
                    class="equation-sign"
                    aria-hidden="true"
                    >+</b
                  >
                </template>
                <b class="equation-sign" aria-hidden="true">=</b>
                <article class="total">
                  <span>누적 총점</span>
                  <strong>{{ drawAccumulatedScore }}점</strong>
                </article>
              </div>
            </section>

            <button
              type="button"
              class="primary dialog-action"
              @click="advanceDrawRound"
            >
              {{
                drawRound < drawRoundResults.length
                  ? '다음 라운드로 이동'
                  : '최종 결과 보기'
              }}
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M5 12h14M13 6l6 6-6 6"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </button>
          </section></div></Transition
    ></Teleport>
  </GamePlayShell>
  <section v-else class="missing">
    <h1>게임을 찾을 수 없어요.</h1>
    <RouterLink to="/games">게임 목록으로</RouterLink>
  </section>
</template>

<style scoped>
.gameplay-layout {
  position: relative;
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr) 230px;
  gap: 18px;
  align-items: stretch;
}
.info-panel,
.gameplay-board {
  min-height: 500px;
  padding: 20px;
  border: 1px solid #e1e4f1;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(36, 44, 95, 0.05);
}
.info-panel {
  color: var(--color-ink);
}
.gameplay-layout > .info-panel:first-child {
  background: #fff;
}
.gameplay-layout > .info-panel:last-child {
  background: #fff;
}
.eyebrow {
  margin: 0 0 8px;
  color: var(--color-accent-blue);
  font-size: 13px;
  font-weight: 900;
}
.info-panel > .eyebrow {
  font-family: var(--font-display);
  font-size: 17px;
  transform: rotate(-1deg);
}
.tip {
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.55;
}
.video-placeholder {
  position: relative;
  display: grid;
  min-height: 230px;
  place-items: center;
  overflow: hidden;
  border: 0;
  border-radius: 14px;
  background: #f7f5ff;
}
.video-placeholder video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.video-placeholder img {
  width: 78%;
  height: 180px;
  object-fit: contain;
}
.video-placeholder::before {
  position: absolute;
  top: 12px;
  left: 14px;
  z-index: 1;
  padding: 5px 9px;
  border: 0;
  border-radius: 999px;
  color: var(--color-ink);
  background: #fffef9;
  font-size: 11px;
  font-weight: 900;
  content: 'EYE CAMERA';
}
.video-placeholder span {
  position: absolute;
  right: 10px;
  bottom: 10px;
  left: 10px;
  padding: 7px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.9);
  color: var(--color-muted);
  font-size: 11px;
  text-align: center;
}
.gameplay-board {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 18px;
}
.draw-info {
  display: flex;
  flex-direction: column;
  text-align: center;
}
.draw-info strong {
  display: block;
  font-family: inherit;
  font-size: 38px;
}
.draw-info ol {
  padding: 0;
  list-style: none;
  text-align: left;
}
.draw-info li {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  padding: 10px;
  border: 1px dashed #9baded;
  border-radius: 10px 14px 12px 15px;
  background: var(--color-surface-soft);
  font-size: 12px;
}
.draw-info li.active {
  border: 1px solid var(--color-accent-blue);
  background: var(--color-blue-soft);
}
.draw-info b {
  color: #278957;
}
.draw-tip {
  display: grid;
  justify-items: center;
  gap: 8px;
  margin: auto -4px -4px;
  padding: 18px 14px 16px;
  border: 1px solid #dbe0f1;
  border-radius: 20px;
  color: #66718d;
  background: #fbfbff;
}
.draw-tip p {
  margin: 0;
  color: #5d56d9;
  font-size: 17px;
  font-weight: 900;
}
.draw-tip p span {
  margin-right: 5px;
  font-size: 14px;
}
.draw-tip > span {
  max-width: 245px;
  font-size: 13px;
  line-height: 1.55;
}
.draw-tip kbd {
  min-width: 106px;
  padding: 8px 14px;
  border: 1px solid #aeb8de;
  border-bottom-width: 3px;
  border-radius: 8px;
  color: var(--color-ink);
  background: #fff;
  font-family: inherit;
  font-size: 16px;
  font-weight: 800;
}
.draw-tip small {
  font-size: 11px;
}
.stage-toolbar,
.rhythm-top,
.hockey-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.status-dot {
  padding: 7px 10px;
  border: 0;
  border-radius: 999px;
  color: #278957;
  background: #eaf7ef;
  font-size: 12px;
  font-weight: 900;
}
.draw-canvas {
  position: relative;
  min-height: 350px;
  overflow: hidden;
  border: 1px solid #e2e4f3;
  border-radius: 16px;
  background-color: #fff;
  background-image: radial-gradient(#d9def2 1px, transparent 1px);
  background-size: 18px 18px;
}
.draw-canvas i {
  position: absolute;
  width: 34%;
  height: 7px;
  border-radius: 99px;
  background: v-bind(selectedColor);
  transform: rotate(40deg);
}
.draw-canvas i:nth-child(1) {
  top: 36%;
  left: 18%;
}
.draw-canvas i:nth-child(2) {
  top: 36%;
  right: 18%;
  transform: rotate(-40deg);
}
.draw-canvas i:nth-child(3) {
  top: 57%;
  left: 25%;
  width: 50%;
  transform: rotate(0);
}
.draw-canvas i:nth-child(4) {
  top: 64%;
  left: 47%;
  width: 6%;
  transform: rotate(90deg);
}
.draw-canvas span {
  position: absolute;
  bottom: 14px;
  left: 14px;
  color: var(--color-muted);
  font-size: 12px;
}
.draw-tools,
.rhythm-controls,
.hold-controls {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 9px;
}
.draw-tools button,
.rhythm-controls button,
.hold-controls button,
.finish {
  min-height: 40px;
  padding: 0 13px;
  border: 1px solid var(--color-line);
  border: 1px solid #d9ddf2;
  border-radius: 9px;
  color: var(--color-ink);
  background: #fff;
  font-weight: 800;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease,
    border-color var(--duration-fast) ease;
}
.draw-tools button[aria-label^='색상'] {
  width: 30px;
  min-height: 30px;
  padding: 0;
  border-radius: 50%;
}
.draw-tools button[aria-label^='색상']:focus-visible {
  outline: 3px solid rgba(79, 116, 219, 0.5);
  outline-offset: 2px;
}
.draw-tools button.selected {
  outline: 3px solid var(--color-ink);
  outline-offset: 2px;
}
.primary,
.finish {
  border-color: var(--color-accent-blue) !important;
  color: #fff !important;
  background: var(--color-accent-blue) !important;
}
.rhythm-score-panel {
  text-align: center;
}
.rhythm-score-panel strong {
  display: block;
  color: var(--color-accent-blue);
  font-size: 42px;
  font-variant-numeric: tabular-nums;
}
.rhythm-score-panel b {
  color: #e44576;
  font-size: 22px;
}
.rhythm-score-panel hr {
  border: 0;
  border-top: 1px solid var(--color-line);
}
.gameplay-layout--rhythm-duel {
  grid-template-columns: minmax(210px, 0.72fr) minmax(420px, 1.8fr) minmax(
      210px,
      0.72fr
    );
  align-items: start;
}
.rhythm-duel-player {
  display: grid;
  gap: 12px;
  padding: 16px;
}
.rhythm-duel-player--mine {
  background: #fbfaff !important;
}
.rhythm-duel-player--opponent {
  background: #fffbfd !important;
}
.rhythm-duel-player__label {
  margin: 0;
  color: #5144e8;
  font-size: 14px;
  font-weight: 900;
}
.rhythm-duel-player--opponent .rhythm-duel-player__label {
  color: #db3d70;
}
.rhythm-duel-player__camera {
  position: relative;
  display: grid;
  min-height: 168px;
  place-items: center;
  overflow: hidden;
  border: 1px solid #e4e2ff;
  border-radius: 16px;
  background: #f2f1ff;
}
.rhythm-duel-player--opponent .rhythm-duel-player__camera {
  border-color: #f3d9e2;
  background: #fff4f7;
}
.rhythm-duel-player__camera video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.rhythm-duel-player__camera img {
  position: relative;
  z-index: 1;
  width: min(72%, 150px);
  max-height: 144px;
  object-fit: contain;
  user-select: none;
  -webkit-user-drag: none;
}
.rhythm-duel-player__camera span {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 2;
  padding: 5px 9px;
  border-radius: 999px;
  color: #5144e8;
  background: rgba(255, 255, 255, 0.9);
  font-size: 11px;
  font-weight: 900;
}
.rhythm-duel-player__camera-note {
  margin: -4px 0 0;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.45;
}
.rhythm-opponent-screen {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid #e3e3ef;
  border-radius: 13px;
  color: var(--color-ink);
  background: #111642;
}
.rhythm-opponent-screen > b {
  color: #fff;
  font-size: 12px;
}
.rhythm-opponent-screen > div {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}
.rhythm-opponent-screen i {
  height: 24px;
  border: 2px solid #ff8fc7;
  border-radius: 999px;
  background: #fae0f0;
  box-shadow: 0 0 8px rgba(255, 119, 183, 0.5);
}
.rhythm-opponent-screen i:nth-child(even) {
  border-color: #89b0ff;
  background: #dbe7ff;
  box-shadow: 0 0 8px rgba(111, 157, 255, 0.5);
}
.rhythm-opponent-screen small {
  color: #c9cff4;
  font-size: 10px;
}
.rhythm-player-stats {
  display: grid;
  gap: 0;
  border-top: 1px solid #e7e8f1;
}
.rhythm-player-stats > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #eef0f6;
}
.rhythm-player-stats span {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 800;
}
.rhythm-player-stats strong {
  color: var(--color-accent-blue);
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}
.rhythm-player-stats__hearts b {
  display: inline-flex;
  gap: 2px;
  color: #e44576;
}
.rhythm-player-stats__hearts b svg {
  width: 15px;
  height: 15px;
}
.rhythm-player-stats__hearts b svg.heart-empty {
  color: #e5e8ee;
}
.hearts {
  display: inline-flex;
  gap: 3px;
  margin-top: 18px;
  color: #e44576;
}
.hearts svg {
  width: 22px;
  height: 22px;
}
.hearts svg.heart-empty {
  color: #e5e8ee;
}
.rhythm-stage {
  position: relative;
  display: grid;
  gap: 18px;
  min-height: 310px;
  padding: 35px;
  border: 0;
  border-radius: 16px;
  color: #fff;
  background: #151b4d;
}
.gameplay-layout--rhythm .gameplay-board {
  display: flex;
  min-height: 620px;
  flex-direction: column;
}
.gameplay-layout--rhythm .rhythm-stage {
  min-height: 420px;
  flex: 1;
  grid-template-rows: repeat(2, minmax(138px, 1fr));
}
.gameplay-layout--rhythm .rhythm-lane {
  height: auto;
}
.rhythm-lane {
  position: relative;
  height: 110px;
  border-bottom: 3px solid #ff78ba;
}
.rhythm-lane:last-child {
  border-color: #77a7ff;
}
.rhythm-lane b {
  position: absolute;
  top: 0;
  left: 0;
  padding: 7px 10px;
  border-radius: 999px;
  background: #4a265d;
}
.rhythm-lane:last-child b {
  background: #213f78;
}
.rhythm-lane i {
  position: absolute;
  right: auto;
  bottom: 20px;
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border: 4px solid #fff;
  border-radius: 50%;
  color: #e84d98;
  background: #ffd3ea;
  box-shadow: 0 0 12px #fd5dab;
  animation: rhythm-note-glow 1.8s ease-in-out infinite;
}
.rhythm-lane i:nth-of-type(2) {
  animation-delay: 0.15s;
}
.rhythm-lane i:nth-of-type(3) {
  animation-delay: 0.3s;
}
.rhythm-lane i:nth-of-type(4) {
  animation-delay: 0.45s;
}
.rhythm-lane:last-child i {
  color: #396ad5;
  background: #c9dcff;
  box-shadow: 0 0 12px #609aff;
}
@keyframes rhythm-note-glow {
  0%,
  100% {
    opacity: 0.55;
  }
  50% {
    opacity: 1;
  }
}
.hit-zone {
  position: absolute;
  bottom: 0;
  left: 15%;
  height: 80px;
  border-left: 4px solid #fff;
}
.webcam-panel .camera-state {
  color: #278957;
  font-size: 13px;
  font-weight: 800;
}
.blink-stage,
.hold-stage {
  display: flex;
  min-height: 370px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.gameplay-layout--blink:not(.gameplay-layout--blink-solo) {
  grid-template-columns: minmax(220px, 0.8fr) minmax(420px, 1.35fr) minmax(
      220px,
      0.8fr
    );
  align-items: start;
}
.gameplay-layout--blink:not(.gameplay-layout--blink-solo) .gameplay-board {
  min-height: 0;
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}
.blink-duel-player {
  display: grid;
  gap: 16px;
  min-height: 500px;
  padding: 20px;
  border: 1px solid #dce1f3;
  border-radius: 20px;
  background: #fff;
}
.blink-duel-player--mine {
  border-color: #d6d9ff;
}
.blink-duel-player--opponent {
  border-color: #f3d5d9;
}
.blink-duel-player__score {
  display: grid;
  justify-items: center;
  gap: 7px;
}
.blink-duel-player__score span {
  padding: 6px 12px;
  border-radius: 999px;
  color: #fff;
  background: #5b58dc;
  font-size: 13px;
  font-weight: 900;
}
.blink-duel-player--opponent .blink-duel-player__score span {
  background: #e54d61;
}
.blink-duel-player__score strong {
  color: #5157db;
  font-size: clamp(46px, 5vw, 64px);
  line-height: 1;
}
.blink-duel-player--opponent .blink-duel-player__score strong {
  color: #e14b5c;
}
.blink-duel-player__score em {
  margin-left: 5px;
  color: var(--color-ink);
  font-size: 23px;
  font-style: normal;
}
.blink-duel-player__camera {
  position: relative;
  display: grid;
  min-height: 290px;
  place-items: center;
  overflow: hidden;
  border: 1px solid #dfe2f1;
  border-radius: 18px;
  background: #f7f7ff;
}
.blink-duel-player--opponent .blink-duel-player__camera {
  border-color: #f2dadd;
  background: #fff8f8;
}
.blink-duel-player__camera video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.blink-duel-player__camera p {
  position: relative;
  z-index: 1;
  max-width: 190px;
  margin: 0;
  padding: 10px 13px;
  border: 1px solid #e1e4f1;
  border-radius: 999px;
  color: #68728d;
  background: rgba(255, 255, 255, 0.88);
  font-size: 12px;
  font-weight: 800;
  line-height: 1.45;
  text-align: center;
}
.blink-duel-player > small {
  color: #66718b;
  font-size: 13px;
  font-weight: 800;
  text-align: center;
}
.blink-duel-stage {
  display: grid;
  gap: 16px;
  min-height: 500px;
  padding: 20px 0;
  text-align: center;
}
.blink-duel-event,
.blink-duel-history {
  border: 1px solid #d8d9ff;
  border-radius: 18px;
  background: #fbfaff;
}
.blink-duel-event {
  display: grid;
  gap: 5px;
  padding: 22px;
}
.blink-duel-event span,
.blink-duel-history > b {
  color: #6a46e8;
  font-size: 14px;
  font-weight: 900;
}
.blink-duel-event strong {
  color: var(--color-ink);
  font-size: clamp(26px, 3vw, 36px);
}
.blink-duel-event p,
.blink-duel-rule {
  margin: 0;
  color: #64708b;
  font-size: 14px;
}
.blink-duel-timer {
  display: grid;
  justify-items: center;
  gap: 7px;
}
.blink-duel-timer > span {
  color: #6b60e6;
  font-size: 14px;
  font-weight: 900;
}
.blink-duel-timer > strong {
  color: #5046df;
  font-size: clamp(56px, 7vw, 78px);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.blink-duel-timer > div {
  width: min(100%, 380px);
  height: 14px;
  overflow: hidden;
  border-radius: 999px;
  background: #e8e9fb;
}
.blink-duel-timer i {
  display: block;
  width: 58%;
  height: 100%;
  border-radius: inherit;
  background: #5447e5;
}
.blink-duel-timer small {
  color: #4f5a75;
  font-weight: 800;
}
.blink-duel-history {
  display: grid;
  gap: 9px;
  padding: 16px;
  text-align: left;
}
.blink-duel-history p {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 0;
  color: #59647e;
  font-size: 12px;
}
.blink-duel-history p span {
  padding: 3px 7px;
  border-radius: 999px;
  color: #278657;
  background: #edf8f0;
  font-weight: 900;
}
.blink-duel-history p:last-child span {
  color: #69758e;
  background: #eff1f6;
}
.blink-duel-history strong {
  color: #5647e6;
  white-space: nowrap;
}
.blink-duel-stage > button {
  justify-self: center;
  padding: 8px 13px;
  border: 1px solid #cfd5ed;
  border-radius: 999px;
  color: #52617e;
  background: #fff;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}
.gameplay-layout--blink-solo {
  grid-template-columns: minmax(0, 1fr);
  justify-content: center;
}
.gameplay-layout--blink-solo .gameplay-board {
  width: min(100%, 1120px);
  min-height: 0;
  justify-self: center;
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}
.gameplay-layout--blink-solo .blink-stage {
  position: relative;
  width: 100%;
  min-height: 560px;
  overflow: hidden;
  border: 1px solid #e1e4f1;
  border-radius: 20px 20px 0 0;
  background: #f7f7ff;
}
.blink-stage__camera {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.blink-stage__camera-placeholder {
  position: absolute;
  top: 50%;
  left: 50%;
  margin: 0;
  padding: 11px 16px;
  border: 1px solid #e0e3f2;
  border-radius: 999px;
  color: #59647f;
  background: rgba(255, 255, 255, 0.82);
  font-size: 13px;
  font-weight: 800;
  transform: translate(-50%, -50%);
}
.blink-stage__metrics {
  position: absolute;
  top: 44px;
  left: 42px;
  display: grid;
  width: 240px;
  gap: 8px;
  text-align: left;
}
.blink-stage__metrics > span {
  color: #59647f;
  font-size: 13px;
  font-weight: 800;
}
.blink-stage__metrics > strong {
  color: var(--color-accent-blue);
  font-size: clamp(52px, 7vw, 78px);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.blink-stage__metrics small {
  margin-top: -4px;
  color: var(--color-ink);
  font-size: 13px;
  font-weight: 800;
}
.blink-stage__metrics b {
  color: var(--color-accent-blue);
  font-size: clamp(42px, 5vw, 60px);
  line-height: 1;
}
.blink-stage__metrics em {
  margin-left: 7px;
  color: var(--color-ink);
  font-size: 24px;
  font-style: normal;
}
.blink-stage__progress {
  height: 12px;
  overflow: hidden;
  border-radius: 999px;
  background: #e8e9fb;
}
.blink-stage__progress i {
  display: block;
  width: 58%;
  height: 100%;
  border-radius: inherit;
  background: var(--color-accent-blue);
}
.blink-stage__tip {
  position: absolute;
  bottom: 34px;
  left: 32px;
  width: min(280px, calc(100% - 64px));
  padding: 18px 20px;
  border: 1px solid #e2e5f3;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
}
.blink-stage__tip b {
  color: var(--color-accent-blue);
  font-size: 15px;
}
.blink-stage__tip p {
  margin: 8px 0 0;
  color: #5d6781;
  font-size: 13px;
  line-height: 1.6;
}
.blink-stage__footer {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 10px 20px;
  border: 1px solid #e1e4f1;
  border-top: 0;
  border-radius: 0 0 20px 20px;
  background: #fbfbff;
}
.blink-stage__footer p {
  margin: 0;
  color: var(--color-ink);
  font-size: 14px;
  font-weight: 800;
}
.blink-stage__footer button {
  padding: 7px 10px;
  border: 1px solid #cfd5ed;
  border-radius: 999px;
  color: #52617e;
  background: #fff;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease,
    border-color var(--duration-fast) ease;
}
.blink-stage__footer button:hover,
.blink-stage__footer button:focus-visible {
  border-color: var(--color-accent-blue);
  color: var(--color-accent-blue);
}
.large {
  align-self: center;
  min-height: 54px !important;
  font-size: 16px;
}
.eye-see-info {
  display: flex;
  min-height: 500px;
  flex-direction: column;
  text-align: left;
}
.eye-see-time {
  display: grid;
  gap: 5px;
  margin: 12px 0 24px;
  padding: 16px;
  border: 1px solid #e2e6f4;
  border-radius: 14px;
  background: #f8f8ff;
}
.eye-see-time span,
.eye-see-info__rule b {
  color: #66718d;
  font-size: 12px;
  font-weight: 800;
}
.eye-see-time strong {
  color: #493be8;
  font-size: 34px;
  line-height: 1;
}
.eye-see-info__rule {
  padding-top: 17px;
  border-top: 1px solid #e8eaf2;
}
.eye-see-info__rule p {
  margin: 7px 0 0;
  color: var(--color-ink);
  font-size: 14px;
  line-height: 1.55;
}
.eye-see-info .tip {
  margin-top: auto;
  padding: 13px;
  border-radius: 12px;
  background: #f4f3ff;
}
.gameplay-layout--hold {
  position: relative;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  column-gap: 86px;
  row-gap: 24px;
}
.gameplay-layout--hold::after {
  position: absolute;
  top: 47%;
  left: 50%;
  z-index: 2;
  display: grid;
  width: 54px;
  height: 54px;
  place-items: center;
  border-radius: 50%;
  color: #5144e8;
  background: #fff;
  box-shadow: 0 0 0 12px #fff;
  content: 'VS';
  font-size: 22px;
  font-weight: 900;
  transform: translate(-50%, -50%);
}
.gameplay-layout--hold .info-panel,
.gameplay-layout--hold .opponent-panel {
  min-height: 430px;
}
.gameplay-layout--hold .gameplay-board {
  display: none;
  grid-column: 1 / -1;
  grid-row: 2;
  min-height: auto;
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}
.gameplay-layout--hold-solo {
  grid-template-columns: minmax(0, 1fr);
}
.gameplay-layout--hold-solo::after {
  display: none;
}
.gameplay-layout--hold-solo .eye-see-info {
  width: min(100%, 920px);
  min-height: 560px;
  justify-self: center;
}
.gameplay-layout--hold-solo .eye-see-camera {
  min-height: 460px;
}
.gameplay-layout--hold-solo .eye-see-camera img {
  width: min(72%, 380px);
  height: 385px;
}
.eye-see-camera {
  position: relative;
  display: grid;
  min-height: 350px;
  place-items: center;
  overflow: hidden;
  border-radius: 16px;
  background: #f6f5ff;
}
.eye-see-camera video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.eye-see-camera__self,
.self-camera {
  transform: scaleX(-1);
}
.eye-see-camera img {
  position: relative;
  z-index: 1;
  width: min(68%, 270px);
  height: 280px;
  object-fit: contain;
}
.eye-see-camera--friend {
  background: #fff6f6;
}
.eye-see-status {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 24px;
  border: 1px solid #e1e4f1;
  border-radius: 18px;
  background: #fbfbff;
  text-align: center;
}
.eye-see-status > span {
  color: var(--color-ink);
  font-size: 15px;
  font-weight: 900;
}
.eye-see-status > p {
  margin: 0;
  color: #59647f;
  font-size: 14px;
}
.eye-see-stage {
  position: relative;
  display: grid;
  min-height: 450px;
  place-items: center;
  overflow: hidden;
  border: 1px solid #e2e6f4;
  border-radius: 18px;
  background: #f7f8fe;
}
.eye-see-stage__video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.eye-see-stage__status {
  position: absolute;
  bottom: 22px;
  display: grid;
  justify-items: center;
  gap: 9px;
  width: min(90%, 440px);
  padding: 13px 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.9);
  text-align: center;
}
.eye-see-stage__status p {
  margin: 0;
  color: #68728d;
  font-size: 13px;
}
.focus-ring {
  display: grid;
  width: min(280px, 52vw);
  height: min(280px, 52vw);
  place-items: center;
  border: 7px dashed #cbd6fb;
  border-radius: 50%;
  background: #f6f8ff;
}
.focus-ring img {
  width: 80%;
  height: 80%;
  object-fit: contain;
}
.air-score-panel {
  display: flex;
  min-height: 620px;
  flex-direction: column;
  align-items: center;
  padding: 24px 18px;
  border: 1px solid #e3e7f1;
  border-radius: 18px;
  background: #fff;
}
.air-score-label {
  margin: 0;
  color: #8490aa;
  font-size: 14px;
  font-weight: 900;
  letter-spacing: 0.08em;
}
.air-score-line {
  width: 100%;
  padding: 18px 0 12px;
  border-top: 1px solid #e6e8f0;
  text-align: center;
}
.air-score-line:first-of-type {
  margin-top: 15px;
  border-top: 0;
}
.air-score-line span,
.air-score-line strong,
.air-time span,
.air-time strong {
  display: block;
}
.air-score-line span {
  color: var(--color-accent-blue);
  font-size: 14px;
  font-weight: 800;
}
.air-score-line strong {
  margin-top: 4px;
  color: #4d51dc;
  font-size: 55px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.air-score-line.opponent span,
.air-score-line.opponent strong {
  color: #e34848;
}
.air-score-vs {
  display: grid;
  width: 47px;
  height: 47px;
  place-items: center;
  border: 1px solid #e5e8f2;
  border-radius: 50%;
  color: #7d86a0;
  font-size: 12px;
  font-weight: 900;
}
.air-time {
  width: 100%;
  margin-top: auto;
  padding-top: 19px;
  border-top: 1px solid #e6e8f0;
  text-align: center;
}
.air-time span {
  color: var(--color-ink);
  font-size: 12px;
  font-weight: 800;
}
.air-time strong {
  margin-top: 8px;
  color: var(--color-ink);
  font-size: 28px;
}
.air-tip {
  width: 100%;
  margin: 20px 0 0;
  padding: 13px;
  border-radius: 12px;
  color: #596582;
  background: #f3f3ff;
  font-size: 11px;
  line-height: 1.55;
}
.air-tip b {
  display: block;
  margin-bottom: 5px;
  color: var(--color-accent-blue);
}
.hockey-status {
  justify-content: center;
  gap: 20px;
}
.gameplay-layout--air .hockey-status {
  display: none;
}
.hockey-status b {
  color: var(--color-accent-blue);
  font-size: 42px;
}
.hockey-status b:nth-of-type(2) {
  color: #e64d55;
}
.hockey-status small {
  margin-left: 25px;
  color: var(--color-muted);
}
.hockey-rink {
  position: relative;
  align-self: center;
  width: min(100%, 380px);
  height: clamp(400px, 50vh, 540px);
  overflow: hidden;
  border: 8px solid #4058b8;
  border-right-color: #e74747;
  border-radius: 30px;
  background:
    radial-gradient(
      circle at center,
      transparent 0 45px,
      #9abbef 46px 48px,
      transparent 49px
    ),
    #fbfcff;
  background-size: 13px 13px;
  box-shadow: 0 16px 38px rgba(50, 69, 155, 0.16);
}
.hockey-rink::before,
.hockey-rink::after {
  position: absolute;
  z-index: 0;
  content: '';
  pointer-events: none;
}
.hockey-rink::before {
  top: 50%;
  right: 0;
  left: 0;
  height: 4px;
  transform: translateY(-50%);
  background: #b8c9ef;
}
.hockey-rink::after {
  top: 50%;
  left: 50%;
  width: 112px;
  height: 112px;
  border: 4px solid #abc0ed;
  border-radius: 50%;
  transform: translate(-50%, -50%);
}
.goal {
  z-index: 1;
  position: absolute;
  left: 36%;
  width: 28%;
  height: 12px;
  border: 5px solid #b9c5e9;
  border-radius: 0 0 12px 12px;
}
.goal--top {
  top: 0;
}
.goal--bottom {
  bottom: 0;
  transform: rotate(180deg);
}
.puck,
.paddle {
  position: absolute;
  z-index: 2;
  border-radius: 50%;
}
.puck {
  top: 48%;
  left: 48%;
  width: 44px;
  height: 44px;
  background: #182138;
  animation: puck-drift 3s ease-in-out infinite alternate;
}
@keyframes puck-drift {
  from {
    transform: translate(0, 0);
  }
  to {
    transform: translate(2px, -2px);
  }
}
.paddle {
  width: clamp(48px, 5vw, 55px);
  height: clamp(48px, 5vw, 55px);
  border: 5px solid rgba(255, 255, 255, 0.35);
  box-shadow: 0 5px 9px rgba(0, 0, 0, 0.16);
}
.paddle--mine {
  bottom: 10%;
  left: 44%;
  background: #2056e9;
}
.paddle--opponent {
  top: 10%;
  right: 44%;
  background: #ec242b;
}
.air-players-panel {
  display: grid;
  min-height: 540px;
  grid-template-rows: 1fr 1fr;
  gap: 18px;
}
.gameplay-layout--air .gameplay-board,
.gameplay-layout--air .air-score-panel {
  min-height: 540px;
}
.air-player-card {
  display: flex;
  min-height: 0;
  flex-direction: column;
  align-items: center;
  overflow: hidden;
  border: 1px solid #f3c7cd;
  border-radius: 18px;
  background: #fff1f2;
}
.air-player-card.you {
  border-color: #cfd4ff;
  background: #f2f3ff;
}
.air-player-card > div {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  padding: 19px 18px 6px;
}
.air-player-card strong {
  color: #df2d31;
  font-size: 17px;
}
.air-player-card.you strong {
  color: #384eda;
}
.air-player-card span {
  padding: 4px 7px;
  border: 1px solid #f1a6ad;
  border-radius: 999px;
  color: #e65b65;
  font-size: 9px;
  font-weight: 900;
}
.air-player-card.you span {
  border-color: #b5bdf9;
  color: #5869e2;
}
.air-player-card img {
  width: min(90%, 205px);
  height: 235px;
  margin-top: auto;
  object-fit: contain;
  object-position: center bottom;
}
.air-player-card__camera {
  position: relative;
  display: grid;
  width: 100%;
  min-height: 235px;
  flex: 1;
  place-items: center;
  overflow: hidden;
}
.air-player-card__camera video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.opponent-panel {
  text-align: center;
}
.opponent-panel img {
  width: 100%;
  height: 220px;
  object-fit: contain;
}
.opponent-panel b {
  color: #e04b51;
}
.finish {
  display: block;
  margin: 18px auto 0;
}
.finish {
  border: 1.5px solid #68748c;
  border-radius: 14px 20px 15px 18px;
  font-family: var(--font-display);
  font-size: 19px;
}
.finish,
.draw-tools .primary {
  transition:
    transform var(--duration-fast) ease,
    box-shadow var(--duration-fast) ease,
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease,
    border-color var(--duration-fast) ease;
}
.finish:hover,
.draw-tools .primary:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-float);
}
.score-backdrop {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(13, 26, 56, 0.6);
}
.score-backdrop section {
  position: relative;
  width: min(100%, 1120px);
  max-height: calc(100vh - 40px);
  overflow-y: auto;
  padding: 34px;
  border: 1px solid #dfe3f3;
  border-radius: 24px;
  background: #fff;
  box-shadow: 0 24px 70px rgba(13, 26, 56, 0.24);
}
.draw-score-heading {
  margin-bottom: 22px;
  padding-right: 44px;
  text-align: left;
}
.draw-score-heading .eyebrow {
  margin-bottom: 5px;
}
.draw-score-heading h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: clamp(23px, 3vw, 31px);
}
.draw-score-columns {
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr);
  gap: 18px;
}
.draw-submission-card,
.draw-score-detail,
.draw-cumulative {
  border: 1px solid #e1e4f3;
  border-radius: 16px;
  background: #fcfcff;
}
.draw-submission-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 22px;
  text-align: center;
}
.draw-submission-card > p:first-child {
  margin: 0;
  color: #66728d;
  font-size: 14px;
  font-weight: 800;
}
.draw-submission-card > strong {
  margin-top: 7px;
  color: var(--color-ink);
  font-size: 36px;
}
.draw-score-sketch {
  position: relative;
  width: 100%;
  min-height: 220px;
  margin: 14px 0;
  overflow: hidden;
  border-top: 1px solid #edf0f7;
  border-bottom: 1px solid #edf0f7;
  background: #fff;
}
.draw-score-sketch i {
  position: absolute;
  width: 35%;
  height: 6px;
  border-radius: 999px;
  background: #171b27;
  transform: rotate(42deg);
}
.draw-score-sketch i:nth-child(1) {
  top: 39%;
  left: 14%;
}
.draw-score-sketch i:nth-child(2) {
  top: 39%;
  right: 14%;
  transform: rotate(-42deg);
}
.draw-score-sketch i:nth-child(3) {
  top: 59%;
  left: 22%;
  width: 56%;
  transform: rotate(0);
}
.draw-score-sketch i:nth-child(4) {
  top: 64%;
  left: 47%;
  width: 8%;
  transform: rotate(90deg);
}
.draw-correct {
  width: 100%;
  margin: 0;
  padding: 12px;
  border-radius: 10px;
  color: #198a57;
  background: #ecf8f0;
  font-size: 14px;
  font-weight: 800;
}
.draw-score-detail {
  padding: 28px;
  text-align: center;
}
.draw-score-ribbon {
  display: inline-block;
  padding: 7px 22px;
  color: #fff;
  background: #5d51eb;
  font-size: 14px;
  font-weight: 900;
}
.draw-round-total {
  display: block;
  margin: 12px 0 17px;
  color: #4539ed;
  font-size: clamp(54px, 7vw, 76px);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.draw-score-breakdown {
  display: grid;
  gap: 8px;
  margin: 0;
  text-align: left;
}
.draw-score-breakdown > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 65px;
  padding: 11px 14px;
  border: 1px solid #e5e8f4;
  border-radius: 11px;
  background: #fff;
}
.draw-score-breakdown dt {
  display: flex;
  align-items: center;
  gap: 11px;
}
.draw-score-breakdown dt > b {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 50%;
  color: #5144ec;
  background: #f0efff;
}
.draw-score-breakdown dt > b svg {
  width: 16px;
  height: 16px;
}
.draw-score-breakdown dt span {
  display: grid;
  gap: 2px;
  color: #1f2a49;
  font-size: 14px;
  font-weight: 900;
}
.draw-score-breakdown dt small {
  color: #79849b;
  font-size: 11px;
  font-weight: 500;
}
.draw-score-breakdown dd {
  margin: 0;
  color: #4539ed;
  font-size: 18px;
  font-weight: 900;
  white-space: nowrap;
}
.draw-round-sum {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
  padding: 15px;
  border-radius: 10px;
  color: #4438e9;
  background: #f1f0ff;
  font-weight: 900;
}
.draw-round-sum b {
  font-size: 24px;
}
.draw-confidence {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 13px 0 0;
  color: #65708c;
  font-size: 13px;
}
.draw-confidence svg {
  width: 15px;
  height: 15px;
  flex: 0 0 auto;
}
.draw-confidence b {
  color: #4539ed;
}
.draw-cumulative {
  margin-top: 18px;
  padding: 18px;
}
.draw-cumulative h3 {
  margin: 0 0 12px;
  color: var(--color-ink);
  font-size: 16px;
}
.draw-score-equation {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}
.draw-score-equation article {
  display: grid;
  min-width: 150px;
  gap: 4px;
  padding: 15px 18px;
  border: 1px solid #e1e5f4;
  border-radius: 12px;
  text-align: center;
}
.draw-score-equation article.current {
  border-color: #887eff;
  background: #fbfaff;
}
.draw-score-equation article.total {
  border-color: #d7d3ff;
  background: #f3f1ff;
}
.draw-score-equation article span {
  color: #59627d;
  font-size: 13px;
  font-weight: 800;
}
.draw-score-equation article strong {
  color: #3429de;
  font-size: 29px;
}
.equation-sign {
  color: #68718d;
  font-size: 25px;
}
.dialog-action {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 280px;
  min-height: 52px;
  margin: 18px auto 0;
  padding: 0 26px;
  border: 0;
  border-radius: 11px;
  font-size: 17px;
  font-weight: 900;
}
.dialog-action svg {
  width: 18px;
  height: 18px;
}
.dialog-close {
  position: absolute;
  top: 18px;
  right: 20px;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  color: #27345e;
  background: transparent;
  line-height: 1;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.dialog-close svg {
  width: 20px;
  height: 20px;
}
.dialog-close:hover {
  background: var(--color-surface-soft);
}
.missing {
  padding: 60px;
  text-align: center;
}
.dialog-pop-enter-active,
.dialog-pop-leave-active {
  transition: background-color 200ms ease;
}
.dialog-pop-enter-active section,
.dialog-pop-leave-active section {
  transition:
    transform 240ms var(--ease-out),
    opacity 240ms var(--ease-out);
}
.dialog-pop-enter-from,
.dialog-pop-leave-to {
  background-color: rgba(13, 26, 56, 0);
}
.dialog-pop-enter-from section,
.dialog-pop-leave-to section {
  opacity: 0;
  transform: scale(0.96) translateY(8px);
}
@media (max-width: 1000px) {
  .gameplay-layout {
    grid-template-columns: 190px minmax(0, 1fr);
  }
  .gameplay-layout > :last-child {
    grid-column: span 2;
    min-height: auto;
  }
  .gameplay-layout--draw > :last-child,
  .gameplay-layout--rhythm > :last-child {
    grid-column: auto;
  }
  .gameplay-layout--rhythm-duel {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .gameplay-layout--rhythm-duel .gameplay-board {
    grid-column: 1 / -1;
    grid-row: 1;
  }
  .gameplay-layout--rhythm-duel .rhythm-duel-player {
    grid-row: 2;
  }
  .gameplay-layout--rhythm .gameplay-board {
    min-height: 560px;
  }
  .webcam-panel {
    display: none;
  }
}
@media (max-width: 680px) {
  .gameplay-layout {
    grid-template-columns: 1fr;
  }
  .info-panel,
  .gameplay-board {
    min-height: auto;
  }
  .gameplay-layout > :last-child {
    grid-column: auto;
  }
  .gameplay-layout--rhythm-duel .gameplay-board,
  .gameplay-layout--rhythm-duel .rhythm-duel-player {
    grid-column: auto;
    grid-row: auto;
  }
  .rhythm-duel-player__camera {
    min-height: 205px;
  }
  .gameplay-layout--hold {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .gameplay-layout--hold::after {
    top: 46%;
  }
  .gameplay-layout--hold .gameplay-board {
    grid-column: auto;
    grid-row: auto;
  }
  .gameplay-layout--hold .info-panel,
  .gameplay-layout--hold .opponent-panel {
    min-height: auto;
  }
  .eye-see-camera {
    min-height: 260px;
  }
  .eye-see-camera img {
    height: 210px;
  }
  .player-panel,
  .opponent-panel {
    display: none;
  }
  .draw-canvas,
  .hockey-rink {
    min-height: 270px;
  }
  .gameplay-layout--blink-solo .blink-stage {
    min-height: 520px;
  }
  .blink-stage__metrics {
    top: 28px;
    left: 24px;
    width: min(215px, calc(100% - 48px));
  }
  .blink-stage__tip {
    right: 24px;
    bottom: 24px;
    left: 24px;
    width: auto;
  }
  .blink-stage__footer {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
  .rhythm-stage {
    min-height: 260px;
    padding: 20px;
  }
  .gameplay-layout--rhythm .gameplay-board {
    min-height: 430px;
  }
  .gameplay-layout--rhythm .rhythm-stage {
    min-height: 280px;
    grid-template-rows: repeat(2, minmax(104px, 1fr));
  }
  .score-backdrop {
    padding: 10px;
  }
  .score-backdrop section {
    max-height: calc(100vh - 20px);
    padding: 24px 16px;
  }
  .draw-score-columns {
    grid-template-columns: 1fr;
  }
  .draw-score-detail,
  .draw-submission-card,
  .draw-cumulative {
    padding: 16px;
  }
  .draw-score-sketch {
    min-height: 165px;
  }
  .draw-score-equation {
    flex-wrap: wrap;
  }
  .draw-score-equation article {
    min-width: 115px;
    padding: 12px 10px;
  }
  .draw-score-breakdown > div {
    align-items: flex-start;
  }
  .draw-score-breakdown dt small {
    line-height: 1.35;
  }
  .dialog-action {
    width: 100%;
    min-width: 0;
  }
  .hockey-status small {
    display: none;
  }
}
</style>
