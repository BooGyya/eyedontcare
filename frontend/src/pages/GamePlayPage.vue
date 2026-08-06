<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DrawPromptIcon from '../components/games/DrawPromptIcon.vue'
import GamePlayShell from '../components/games/GamePlayShell.vue'
import { createMockSession, gameModeLabels } from '../mocks/gameplay'
import { gameDetails, isGameDetailId } from '../mocks/game-details'
import type { GameSessionMode } from '../types/gameplay'
import { useLiveKitRoom } from '../composables/useLiveKitRoom'
import { useGameResultSubmission } from '../composables/useGameResultSubmission'
import { useMediaSessionStore } from '../stores/mediaSession'
import { useToast } from '../composables/useToast'
import { useEyeTracking } from '../composables/useEyeTracking'
import { useCalibrationStore } from '../stores/calibration'
import { useGameSessionSocket } from '../composables/useGameSessionSocket'
import { currentParticipantKey, resolveIdentity } from '../api/identity'
import { useLastGameResultStore } from '../stores/lastGameResult'
import type { LastGameOutcome } from '../stores/lastGameResult'
import type { GameSessionStateData } from '../types/gameSession'
import GameStartCountdownModal from '../components/games/GameStartCountdownModal.vue'
import {
  applyBlinkEvent,
  formatRemainingTime as formatBlinkRemainingTime,
  makeInitialBlinkState,
  startBlinkRound,
  updateBlinkTimer,
} from '../lib/games/blink-core'
import {
  finishStareRoundAsWinner,
  formatDuration as formatStareDuration,
  makeInitialStareState,
  STARE_AI_DURATIONS_MS,
  startStareRound,
  updateStareRound,
} from '../lib/games/stare-core'
import {
  applyRhythmInput,
  finishRhythmRound,
  formatRhythmTime,
  getRhythmAccuracy,
  makeInitialRhythmState,
  startRhythmRound,
  updateRhythmRound,
  type RhythmJudgement,
  type RhythmInput,
  type RhythmLane,
  type RhythmNote,
} from '../lib/games/rhythm-core'
import { analyzeAudioUrlToBeatmap } from '../lib/games/audio-beatmap'
import {
  addPointToStroke,
  applyDrawRoundResult,
  beginJudging,
  DRAWING_ALL_WORDS,
  DRAWING_DIFFICULTY_LABEL,
  DRAWING_TOTAL_ROUNDS,
  isDrawGameFinished,
  makeInitialDrawGameState,
  pickWordsForGame,
  reportDrawJudgingError,
  startDrawRound,
  tickDrawRoundTimer,
  type DrawStroke,
} from '../lib/games/draw-core'
import { recognizeDrawing } from '../api/draw'
import {
  AIR_HOCKEY_HEIGHT,
  AIR_HOCKEY_MATCH_DURATION_MS,
  AIR_HOCKEY_WIDTH,
  applyStrike,
  determineAirHockeyWinner,
  getGoalResult,
  launchPuck,
  makeInitialAirHockeyState,
  resetPuckForServe,
  resolveMalletCollision,
  scoreGoal,
  startAirHockeyMatch,
  updateAirHockeyMatch,
  type AirHockeyState,
  type Mallet,
} from '../lib/games/air-hockey-core'
import airAiRobotImage from '../assets/images/games/game-air-ai-robot.png'

const route = useRoute()
const router = useRouter()
const drawScoreOpen = ref(false)
const isReplayCountdownOpen = ref(false)
const replayCountdown = ref(3)
const selectedColor = ref('#161c2d')
const gameplayLayoutRef = ref<globalThis.HTMLElement | null>(null)
let airGameScrollTimer: ReturnType<typeof globalThis.setTimeout> | undefined
let replayCountdownTimer: ReturnType<typeof globalThis.setInterval> | undefined
let playStartedAt = ''

function scrollToAirGameStart() {
  const gameplayLayout = gameplayLayoutRef.value
  if (!gameplayLayout || typeof globalThis.scrollTo !== 'function') return

  const headerHeight =
    globalThis.document?.querySelector('.app-header')?.getBoundingClientRect()
      .height ?? 0
  const targetTop =
    globalThis.scrollY +
    gameplayLayout.getBoundingClientRect().top -
    headerHeight -
    28

  globalThis.scrollTo({ top: Math.max(0, targetTop), behavior: 'instant' })
}

// --- 그림그리기: 실제 시선 좌표 기반 캔버스 + AI 채점 연동 ---
// 기획 확정본 기준 모드는 'ai' 하나뿐이라(친구/랜덤 없음) 상대 동기화가 필요 없다. 대신
// 시선의 "좌표"(연속값)를 캔버스 위치로 써야 해서 에어하키처럼 매 프레임 screenGaze를 읽는다.
// AI 채점(recognizeDrawing)은 아직 없는 백엔드 엔드포인트를 호출한다 — 실패하면 재시도할 수
// 있게 라운드를 계속 진행 상태로 되돌린다(조용히 가짜 성공으로 넘기지 않는다).
const drawTracking = useEyeTracking()
const drawVideoRef = drawTracking.videoRef
const drawCameraActive = drawTracking.isActive
// vue-tsc가 문자열 템플릿 ref(ref="drawVideoRef")를 '사용'으로 인식하지 못해 noUnusedLocals가
// 오탐한다 — blinkVideoRef 등과 동일한 이유.
void drawVideoRef

const drawCanvasRef = ref<globalThis.HTMLCanvasElement | null>(null)
const DRAW_CANVAS_WIDTH = 1000
const DRAW_CANVAS_HEIGHT = 640
const drawGameState = ref(makeInitialDrawGameState())
const drawWords = ref<string[]>([])
let drawStrokes: DrawStroke[] = []
let drawActiveStroke: DrawStroke | null = null
const isDrawingActive = ref(true)
const drawCursor = ref<{ x: number; y: number } | null>(null)
const drawBrushWidth = 5
let drawRafHandle: number | undefined
let unsubscribeDrawKeydown: (() => void) | undefined
let drawShouldBridge = false
/**
 * 펜 속도 = 프레임당 커서가 시선을 따라 이동하는 최대 거리(0~1 정규화). 낮을수록 펜이 천천히
 * 따라오고(느림·안정), 높을수록 즉각 따라온다. 지수평활 계수와 달리 '지연'이 아니라 실제 '이동
 * 속도'를 제한하므로 슬라이더 조절이 눈에 띄게 반영된다.
 */
const drawPenSpeed = ref(0.03)
/** 커서 위치. 추적이 끊기면 null로 리셋해 다음 점부터 새로 시작(튐 방지). */
let drawSmoothedCursor: { x: number; y: number } | null = null

const drawTimeLabel = computed(() => {
  const totalSeconds = Math.max(
    Math.ceil(drawGameState.value.remainingMs / 1000),
    0,
  )
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})
const drawDifficultyLabel = computed(
  () => DRAWING_DIFFICULTY_LABEL[drawGameState.value.difficulty],
)
const currentDrawResult = computed(
  () => drawGameState.value.history[drawGameState.value.round - 1],
)
const drawAccumulatedScore = computed(() => drawGameState.value.score)

const { showToast } = useToast()

// --- 눈 깜빡이기: 실제 시선 인식 연동 ---
// 각 게임 로직에서 계산한 상태를 결과 저장소와 결과 API에 전달한다.
// 카메라는 useLocalCamera가 아니라 이 게임 전용 useEyeTracking 인스턴스가 직접 관리한다(중복 촬영
// 방지를 위해 blinkVideoRef를 화면에 보이는 <video>에도 그대로 바인딩한다 — GameReadyPage와 동일한 패턴).
const blinkTracking = useEyeTracking()
const calibrationStore = useCalibrationStore()
const blinkVideoRef = blinkTracking.videoRef
const blinkCameraActive = blinkTracking.isActive
// vue-tsc가 문자열 템플릿 ref(ref="blinkVideoRef")를 '사용'으로 인식하지 못해 noUnusedLocals가
// 오탐한다(remoteVideoRef/localCameraVideoRef와 동일한 이유). 실제로는 아래 <video ref="...">에
// 런타임 바인딩된다.
void blinkVideoRef
const blinkGameState = ref(makeInitialBlinkState())
const blinkCount = computed(() => blinkGameState.value.blinkCount)
const blinkTimeLabel = computed(() =>
  formatBlinkRemainingTime(blinkGameState.value.remainingMs),
)
const blinkProgressPercent = computed(() => {
  const { durationMs, remainingMs } = blinkGameState.value
  if (durationMs <= 0) return 0
  return Math.min(100, Math.max(0, 100 - (remainingMs / durationMs) * 100))
})
let blinkRafHandle: number | undefined
let unsubscribeBlinkEvents: (() => void) | undefined

const opponentNickname = ref<string | undefined>()

function resolveOpponentDisplayName(
  participant: GameSessionStateData['participants'][number] | undefined,
): string | undefined {
  if (!participant) return undefined
  if (participant.participantKey.startsWith('GUEST:')) {
    return '게스트 플레이어'
  }
  const displayName = participant.displayName?.trim()
  return displayName || undefined
}

function updateOpponentNickname(state: GameSessionStateData): void {
  const participantKey = currentParticipantKey()
  const opponent = state.participants.find(
    (participant) => participant.participantKey !== participantKey,
  )
  const displayName = resolveOpponentDisplayName(opponent)
  if (displayName) opponentNickname.value = displayName
}

// 대결 모드에서 상대방 실시간 상태를 보여주기 위한 게임 세션 소켓(중계 전용, 판정은 안 함).
const blinkGameSession = useGameSessionSocket({
  onSessionState: updateOpponentNickname,
  onPlayerEvent: (event) => {
    if (event.eventType === 'GAME_OVER') {
      opponentFinished = true
      return
    }
    if (event.eventType !== 'BLINK_COUNT') return
    const count = Number(event.payload?.count)
    if (Number.isFinite(count)) opponentBlinkCount.value = count
  },
  onParticipantLeft: handleOpponentLeft,
})
const opponentBlinkCount = ref<number | null>(null)

async function initBlinkGame() {
  // 대기방에서 이미 캘리브레이션을 마쳤다면 그 결과를 그대로 이어받는다(재보정 불필요).
  if (calibrationStore.eyeProfile) {
    blinkTracking.applyEyeProfile(calibrationStore.eyeProfile)
  }

  const started = await blinkTracking.start()
  if (!started) {
    showToast('카메라를 시작하지 못했어요. 카메라 권한을 확인해 주세요.')
    return
  }

  // 대결 모드면 상대에게 내 화면을 보내고 상대 화면을 구독한다 — 다른 게임의 initMedia()와 동일한 패턴.
  if (showsOpponentCamera.value && mediaSession.credentials) {
    await connectMedia(mediaSession.credentials, {
      localTrack: blinkTracking.stream.value?.getVideoTracks()[0] ?? null,
    })
  }

  // 대결 모드면 게임 세션 소켓에 접속해 내 깜빡임 횟수를 실시간으로 상대에게 전달한다.
  if (isCompetitive.value) {
    const roomId = String(route.query.roomId ?? '')
    const identity = resolveIdentity()
    if (roomId && identity) {
      blinkGameSession.connect(roomId, identity)
    }
  }

  unsubscribeBlinkEvents = blinkTracking.onEyeEvent((event) => {
    const changed = applyBlinkEvent(blinkGameState.value, event)
    if (changed) {
      blinkGameSession.sendPlayerEvent('BLINK_COUNT', {
        count: blinkGameState.value.blinkCount,
      })
    }
  })

  startBlinkRound(blinkGameState.value, globalThis.performance.now())
  runBlinkTimerLoop()
}

function runBlinkTimerLoop() {
  const tick = (now: number) => {
    const finished = updateBlinkTimer(blinkGameState.value, now)
    if (finished) {
      toResult()
      return
    }
    blinkRafHandle = globalThis.requestAnimationFrame(tick)
  }
  blinkRafHandle = globalThis.requestAnimationFrame(tick)
}

function stopBlinkGame() {
  if (blinkRafHandle !== undefined) {
    globalThis.cancelAnimationFrame(blinkRafHandle)
    blinkRafHandle = undefined
  }
  unsubscribeBlinkEvents?.()
  unsubscribeBlinkEvents = undefined
  blinkTracking.stop()
  blinkGameSession.close()
}

// --- 눈싸움: 실제 시선 인식 연동 ---
// blink와 같은 패턴으로 별도 useEyeTracking 인스턴스를 쓴다(카메라 중복 방지).
const stareTracking = useEyeTracking()
const stareVideoRef = stareTracking.videoRef
const stareCameraActive = stareTracking.isActive
// vue-tsc가 문자열 템플릿 ref(ref="stareVideoRef")를 '사용'으로 인식하지 못해 noUnusedLocals가
// 오탐한다 — blinkVideoRef와 동일한 이유.
void stareVideoRef

// AI 대결 난이도(15/30/60초). 선택 UI는 아직 없어서 쿼리 파라미터가 없으면 normal로 기본 처리한다.
const stareDifficulty = computed<'easy' | 'normal' | 'hard'>(() => {
  const value = String(route.query.difficulty ?? 'normal')
  return value === 'easy' || value === 'hard' ? value : 'normal'
})
const stareTargetMs = computed(() =>
  mode.value === 'ai'
    ? STARE_AI_DURATIONS_MS[
        stareDifficulty.value.toUpperCase() as 'EASY' | 'NORMAL' | 'HARD'
      ]
    : null,
)
const stareGameState = ref(makeInitialStareState(null))
const stareElapsedLabel = computed(() =>
  formatStareDuration(stareGameState.value.elapsedMs),
)
const stareStatusLabel = computed(() => {
  if (stareGameState.value.phase === 'finished') {
    return stareGameState.value.outcome === 'WIN' ? '승리!' : '패배'
  }
  return '눈싸움 진행 중'
})
const stareWarningTone = computed(() =>
  stareGameState.value.warning === '정상' ? 'ok' : 'warn',
)
let stareRafHandle: number | undefined

// 친구/랜덤 대결 실시간 동기화 — 상대 생존 시간을 보여주고, 상대가 먼저 눈을 감으면 내 라운드도
// 즉시 승리로 종료한다(눈싸움은 "먼저 감는 쪽이 패배"라 상대 패배 = 내 승리가 바로 확정된다).
const stareOpponentElapsedMs = ref(0)
const stareOpponentSynced = ref(false)
const opponentStareLostFirst = ref(false)
const stareGameSession = useGameSessionSocket({
  onSessionState: updateOpponentNickname,
  onPlayerEvent: (event) => {
    if (event.eventType === 'GAME_OVER') {
      opponentFinished = true
      return
    }
    if (event.eventType !== 'STARE_STATE') return
    const elapsedMs = Number(event.payload?.elapsedMs)
    if (Number.isFinite(elapsedMs)) stareOpponentElapsedMs.value = elapsedMs
    stareOpponentSynced.value = true
    if (event.payload?.lost === true && !opponentStareLostFirst.value) {
      opponentStareLostFirst.value = true
      finishStareDuelEarly()
    }
  },
  onParticipantLeft: handleOpponentLeft,
})
let stareLastStateSentAt = 0
const STARE_STATE_SEND_INTERVAL_MS = 150

function sendStareState(lost: boolean) {
  stareGameSession.sendPlayerEvent('STARE_STATE', {
    elapsedMs: stareGameState.value.elapsedMs,
    lost,
  })
}

async function initStareGame() {
  if (calibrationStore.eyeProfile) {
    stareTracking.applyEyeProfile(calibrationStore.eyeProfile)
  }

  const started = await stareTracking.start()
  if (!started) {
    showToast('카메라를 시작하지 못했어요. 카메라 권한을 확인해 주세요.')
    return
  }

  if (showsOpponentCamera.value && mediaSession.credentials) {
    await connectMedia(mediaSession.credentials, {
      localTrack: stareTracking.stream.value?.getVideoTracks()[0] ?? null,
    })
  }

  if (isStareDuel.value) {
    const roomId = String(route.query.roomId ?? '')
    const identity = resolveIdentity()
    if (roomId && identity) stareGameSession.connect(roomId, identity)
  }

  stareGameState.value = makeInitialStareState(stareTargetMs.value)
  startStareRound(stareGameState.value, globalThis.performance.now())
  runStareLoop()
}

function runStareLoop() {
  const tick = (now: number) => {
    updateStareRound(stareGameState.value, now, {
      faceDetected: stareTracking.faceDetected.value,
      combinedState: stareTracking.combinedState.value,
    })

    if (isStareDuel.value && stareGameState.value.phase === 'running') {
      if (now - stareLastStateSentAt >= STARE_STATE_SEND_INTERVAL_MS) {
        stareLastStateSentAt = now
        sendStareState(false)
      }
    }

    if (stareGameState.value.phase === 'finished') {
      // 내가 눈을 감아 패배한 경우에만 즉시 알린다 — finishStareDuelEarly()로 이미 승리 처리된
      // 경우(상대가 먼저 짐)까지 다시 "내가 졌다"고 잘못 알리면 안 되므로 outcome을 확인한다.
      if (isStareDuel.value && stareGameState.value.outcome === 'LOSE') {
        sendStareState(true)
      }
      toResult()
      return
    }
    stareRafHandle = globalThis.requestAnimationFrame(tick)
  }
  stareRafHandle = globalThis.requestAnimationFrame(tick)
}

/** 상대가 먼저 눈을 감았을 때 내 라운드도 승리로 종료 처리하고 결과 화면으로 넘어간다. */
function finishStareDuelEarly() {
  if (stareGameState.value.phase === 'finished') return
  finishStareRoundAsWinner(stareGameState.value, globalThis.performance.now())
  toResult()
}

function stopStareGame() {
  if (stareRafHandle !== undefined) {
    globalThis.cancelAnimationFrame(stareRafHandle)
    stareRafHandle = undefined
  }
  stareTracking.stop()
  stareGameSession.close()
}

// --- 리듬게임: 실제 시선 인식 연동 ---
const rhythmTracking = useEyeTracking()
const rhythmVideoRef = rhythmTracking.videoRef
const rhythmCameraActive = rhythmTracking.isActive
// vue-tsc가 문자열 템플릿 ref(ref="rhythmVideoRef")를 '사용'으로 인식하지 못해 noUnusedLocals가
// 오탐한다 — blinkVideoRef/stareVideoRef와 동일한 이유.
void rhythmVideoRef

const rhythmGameSession = useGameSessionSocket({
  onSessionState: updateOpponentNickname,
  onPlayerEvent: (event) => {
    if (event.eventType === 'GAME_OVER') {
      opponentFinished = true
      return
    }
    if (event.eventType !== 'RHYTHM_STATE') return
    const score = Number(event.payload?.score)
    const combo = Number(event.payload?.combo)
    const health = Number(event.payload?.health)
    if (Number.isFinite(score)) rhythmOpponent.value.score = score
    if (Number.isFinite(combo)) rhythmOpponent.value.combo = combo
    if (Number.isFinite(health)) rhythmOpponent.value.hearts = health
    rhythmOpponentSynced.value = true

    // 상대가 체력을 다 잃으면 승부가 이미 결정된 것이므로 내 게임도 바로 종료(승리 처리)한다.
    if (Number.isFinite(health) && health <= 0) {
      opponentHealthDepleted.value = true
      finishRhythmDuelEarly()
    }
  },
  onParticipantLeft: handleOpponentLeft,
})

const rhythmGameState = ref(makeInitialRhythmState())
// 상대 상태는 실시간 동기화로만 채워진다 — 대결 시작 직후 첫 이벤트가 오기 전까지는 초기값(만점 체력)을 보여준다.
const rhythmOpponent = ref({ score: 0, combo: 0, hearts: 5 })
const rhythmOpponentSynced = ref(false)
/** 상대가 먼저 체력을 다 잃어서 내가 이긴 경우를 표시한다(결과 화면 승패 판정에 사용). */
const opponentHealthDepleted = ref(false)
interface RhythmFeedback {
  id: number
  input: RhythmInput | 'NONE'
  judgement: RhythmJudgement
  lanes: RhythmLane[]
}

const rhythmFeedback = ref<RhythmFeedback | null>(null)
let rhythmFeedbackId = 0
let rhythmFeedbackTimer: ReturnType<typeof globalThis.setTimeout> | undefined

const rhythmMine = computed(() => ({
  score: rhythmGameState.value.score,
  combo: rhythmGameState.value.combo,
  hearts: rhythmGameState.value.health,
}))
const rhythmNow = ref(0)
const rhythmTimeLabel = computed(() =>
  formatRhythmTime(rhythmGameState.value.remainingMs),
)
const rhythmAccuracyPercent = computed(() =>
  Math.round(getRhythmAccuracy(rhythmGameState.value)),
)
const rhythmProgressPercent = computed(() => {
  const { durationMs, remainingMs } = rhythmGameState.value
  if (durationMs <= 0) return 0
  return Math.min(100, Math.max(0, 100 - (remainingMs / durationMs) * 100))
})
const rhythmLeftNotes = computed(() =>
  rhythmGameState.value.notes.filter(
    (note) => note.lane === 'LEFT_EYE' && note.status === 'PENDING',
  ),
)
const rhythmRightNotes = computed(() =>
  rhythmGameState.value.notes.filter(
    (note) => note.lane === 'RIGHT_EYE' && note.status === 'PENDING',
  ),
)
let rhythmRafHandle: number | undefined
let unsubscribeRhythmEvents: (() => void) | undefined

// 라운드 시작 후 첫 비트가 판정선에 닿기까지의 도입 시간(ms). 이 동안 비트가 오른쪽에서
// 밀려 들어와 갑자기 생성되지 않는다. 음악 모드는 비트맵을 이만큼 미루고 오디오도 이만큼 늦게 재생한다.
const RHYTHM_LEAD_IN_MS = 2000
const isRhythmStartCountdownOpen = ref(false)
const rhythmStartCountdown = ref(3)
let rhythmStartCountdownTimer:
  ReturnType<typeof globalThis.setInterval> | undefined
let rhythmAudioStartTimer: ReturnType<typeof globalThis.setTimeout> | undefined
const rhythmStageRef = ref<globalThis.HTMLElement | null>(null)
// vue-tsc가 템플릿 ref를 '사용'으로 인식하지 못해 noUnusedLocals에 걸리는 것을 막는다.
void rhythmStageRef

function rhythmInputToLanes(input: RhythmInput | 'NONE'): RhythmLane[] {
  if (input === 'BOTH_EYES') return ['LEFT_EYE', 'RIGHT_EYE']
  if (input === 'LEFT_EYE' || input === 'RIGHT_EYE') return [input]
  return []
}

function clearRhythmFeedback(): void {
  if (rhythmFeedbackTimer !== undefined) {
    globalThis.clearTimeout(rhythmFeedbackTimer)
    rhythmFeedbackTimer = undefined
  }
  rhythmFeedback.value = null
}

function showRhythmFeedback(
  input: RhythmInput | 'NONE',
  judgement: RhythmJudgement,
  lanes = rhythmInputToLanes(input),
): void {
  if (judgement === 'EMPTY' || lanes.length === 0) return

  if (rhythmFeedbackTimer !== undefined) {
    globalThis.clearTimeout(rhythmFeedbackTimer)
  }

  rhythmFeedback.value = {
    id: ++rhythmFeedbackId,
    input,
    judgement,
    lanes: [...new Set(lanes)],
  }
  rhythmFeedbackTimer = globalThis.setTimeout(() => {
    rhythmFeedback.value = null
    rhythmFeedbackTimer = undefined
  }, 650)
}

/**
 * 노트의 CSS `left`(%) 값을 계산한다. `.hit-zone`이 왼쪽 15% 지점에 고정돼 있어서(judge line),
 * 노트는 오른쪽 끝(100%)에서 나타나 왼쪽 15%로 이동해야 한다 — progress 0~100을 100~15로 매핑.
 */
function noteLeftPercent(note: RhythmNote): number {
  const { noteTravelMs } = rhythmGameState.value
  if (noteTravelMs <= 0) return 15
  const progress = (1 - (note.hitAt - rhythmNow.value) / noteTravelMs) * 100
  const clamped = Math.min(140, Math.max(-20, progress))
  return 100 - clamped * 0.85
}

// --- 배경 음악 + 실제 비트맵 ---
// 곡(assets/ssafy.mp3)을 분석해 실제 박자에 맞는 노트를 만든다. 분석/재생이 실패해도(느린 네트워크,
// 브라우저 자동재생 차단 등) 기존처럼 고정 BPM 랜덤 노트로 조용히 폴백한다 — 게임 자체는 항상 된다.
const RHYTHM_AUDIO_URL = '/audio/ssafy.mp3'
const rhythmIsAnalyzingAudio = ref(false)
/** 오디오 재생이 실제로 성공했을 때만 true — true면 게임 시계를 audio.currentTime 기준으로 돌린다. */
const rhythmHasMusic = ref(false)
let rhythmAudio: globalThis.HTMLAudioElement | undefined

async function prepareRhythmBeatmap(): Promise<void> {
  rhythmIsAnalyzingAudio.value = true
  try {
    const beatmap = await analyzeAudioUrlToBeatmap(RHYTHM_AUDIO_URL)
    if (beatmap.notes.length === 0) return // 분석은 됐지만 쓸 만한 비트가 없으면 랜덤 생성 폴백

    // 모든 비트를 lead-in만큼 뒤로 밀어 곡 시작 직후 비트도 오른쪽에서 온전히 밀려 들어오게 한다.
    // 오디오 재생도 라운드 시작 후 lead-in만큼 늦춰(beginRhythmRound) 소리와 비트를 맞춘다.
    const shiftedEntries = beatmap.notes.map((entry) => ({
      ...entry,
      timeMs: entry.timeMs + RHYTHM_LEAD_IN_MS,
    }))
    rhythmGameState.value = makeInitialRhythmState({
      // 기획 확정본 기준 제한 시간(30초)은 그대로 지킨다 — 곡이 더 길어도 30초를 넘는 노트는
      // rhythm-core의 generateBeatmapRhythmNotes가 자동으로 건너뛴다.
      durationMs: rhythmGameState.value.durationMs,
      bpm: beatmap.bpmEstimate || undefined,
      beatmapEntries: shiftedEntries,
    })

    // 오디오는 여기서 만들되 재생은 카운트다운 뒤 lead-in에 맞춰 시작한다.
    const audio = new globalThis.Audio(RHYTHM_AUDIO_URL)
    audio.volume = 0.6
    rhythmAudio = audio
    rhythmHasMusic.value = true
  } catch {
    // 네트워크 실패, 디코딩 실패 등 — 조용히 랜덤 노트로 진행한다.
    rhythmHasMusic.value = false
  } finally {
    rhythmIsAnalyzingAudio.value = false
  }
}

function stopRhythmAudio(): void {
  if (!rhythmAudio) return
  rhythmAudio.pause()
  rhythmAudio.currentTime = 0
  rhythmAudio = undefined
}

async function initRhythmGame() {
  // 대기방에서 이미 캘리브레이션을 마쳤다면 그 결과를 그대로 이어받는다(재보정 불필요).
  if (calibrationStore.eyeProfile) {
    rhythmTracking.applyEyeProfile(calibrationStore.eyeProfile)
  }

  const started = await rhythmTracking.start()
  if (!started) {
    showToast('카메라를 시작하지 못했어요. 카메라 권한을 확인해 주세요.')
    return
  }

  if (showsOpponentCamera.value && mediaSession.credentials) {
    await connectMedia(mediaSession.credentials, {
      localTrack: rhythmTracking.stream.value?.getVideoTracks()[0] ?? null,
    })
  }

  if (isRhythmDuel.value) {
    const roomId = String(route.query.roomId ?? '')
    const identity = resolveIdentity()
    if (roomId && identity) rhythmGameSession.connect(roomId, identity)
  }

  unsubscribeRhythmEvents = rhythmTracking.onEyeEvent((event) => {
    // 웹캠 영상은 거울처럼 좌우가 반전되어 보이므로, 감지된 왼쪽/오른쪽 눈을 뒤집어 매핑한다
    // (실제 사용자 테스트로 확인된 보정값 — ai_game 프로토타입에서도 동일하게 처리).
    let input: RhythmInput | null = null
    if (event.type === 'LEFT_WINK') input = 'RIGHT_EYE'
    else if (event.type === 'RIGHT_WINK') input = 'LEFT_EYE'
    else if (
      event.type === 'BLINK' ||
      event.type === 'FAST_BLINK' ||
      event.type === 'DOUBLE_BLINK'
    ) {
      input = 'BOTH_EYES'
    }
    if (!input) return

    // 노트 hitAt·게임 시계가 모두 performance.now() 도메인이므로 판정도 같은 도메인(occurredAt)을 쓴다.
    const now = event.occurredAt
    const inputResult = applyRhythmInput(rhythmGameState.value, input, now)
    if (inputResult.hit) {
      showRhythmFeedback(
        inputResult.input,
        inputResult.judgement,
        inputResult.notes.map((note) => note.lane),
      )
    }
    sendRhythmState()
  })

  await prepareRhythmBeatmap()
  // 멀티플레이는 준비방의 서버 카운트다운으로 양쪽 시작 시점을 맞추므로 게임 안에서 또 세지 않는다.
  // 솔로(다시하기 포함)는 준비방 카운트다운을 생략하고 여기 게임 화면 안에서만 3·2·1 후 시작한다.
  if (!isMultiplayerMode.value) {
    await runRhythmStartCountdown()
  }
  beginRhythmRound()
}

/** 3·2·1 카운트다운을 띄우고 끝나면 resolve한다. */
function runRhythmStartCountdown(): Promise<void> {
  return new Promise((resolve) => {
    scrollRhythmStageIntoView()
    rhythmStartCountdown.value = 3
    isRhythmStartCountdownOpen.value = true
    rhythmStartCountdownTimer = globalThis.setInterval(() => {
      if (rhythmStartCountdown.value <= 1) {
        globalThis.clearInterval(rhythmStartCountdownTimer)
        rhythmStartCountdownTimer = undefined
        isRhythmStartCountdownOpen.value = false
        resolve()
        return
      }
      rhythmStartCountdown.value -= 1
    }, 1000)
  })
}

/** 리듬 게임 영역이 한눈에 보이도록 스크롤한다(스크롤해야 보이던 문제 해결). */
function scrollRhythmStageIntoView(): void {
  const stage = rhythmStageRef.value
  // jsdom 등 scrollIntoView 미구현 환경 대비로 함수 존재를 확인한다.
  if (stage && typeof stage.scrollIntoView === 'function') {
    stage.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

function beginRhythmRound() {
  scrollRhythmStageIntoView()
  // 벽시계(performance.now)로 시작한다. lead-in만큼 첫 비트가 오른쪽에서 들어온다.
  startRhythmRound(rhythmGameState.value, globalThis.performance.now(), {
    startDelayMs: RHYTHM_LEAD_IN_MS,
  })
  // 음악 모드는 비트맵을 lead-in만큼 밀어 뒀으므로, 오디오도 그만큼 늦게 재생해 소리와 비트를 맞춘다.
  if (rhythmHasMusic.value && rhythmAudio) {
    rhythmAudioStartTimer = globalThis.setTimeout(() => {
      rhythmAudio?.play().catch(() => {})
    }, RHYTHM_LEAD_IN_MS)
  }
  runRhythmLoop()
}

function sendRhythmState() {
  rhythmGameSession.sendPlayerEvent('RHYTHM_STATE', {
    score: rhythmGameState.value.score,
    combo: rhythmGameState.value.combo,
    health: rhythmGameState.value.health,
  })
}

function runRhythmLoop() {
  const tick = (rafNow: number) => {
    // 게임 시계는 벽시계(performance.now = rAF 타임스탬프)로 통일한다. 노트 hitAt·입력 판정(occurredAt)이
    // 모두 같은 도메인이라 판정이 일관되고, 음악은 lead-in만큼 늦게 재생해 소리와 비트를 맞춘다.
    const now = rafNow
    rhythmNow.value = now
    const previousNoteStatuses = new Map(
      rhythmGameState.value.notes.map((note) => [note.id, note.status]),
    )
    const result = updateRhythmRound(rhythmGameState.value, now)
    const missedLanes = rhythmGameState.value.notes
      .filter(
        (note) =>
          note.status === 'MISS' &&
          previousNoteStatuses.get(note.id) === 'PENDING',
      )
      .map((note) => note.lane)
    if (missedLanes.length > 0) {
      showRhythmFeedback(rhythmGameState.value.lastInput, 'MISS', missedLanes)
    }
    // UPDATED(노트를 놓쳐 체력이 깎임)뿐 아니라 FINISHED(체력 소진으로 종료)도 상대에게 알려야
    // 한다 — 안 그러면 내가 체력 0으로 죽었다는 마지막 상태가 상대에게 영영 전달되지 않는다.
    if (result === 'UPDATED' || result === 'FINISHED') sendRhythmState()
    if (result === 'FINISHED') {
      stopRhythmAudio()
      toResult()
      return
    }
    rhythmRafHandle = globalThis.requestAnimationFrame(tick)
  }
  rhythmRafHandle = globalThis.requestAnimationFrame(tick)
}

/** 상대가 먼저 체력을 다 잃었을 때 내 라운드도 정식으로 종료 처리하고 결과 화면으로 넘어간다. */
function finishRhythmDuelEarly() {
  if (rhythmGameState.value.phase === 'finished') return
  const now = globalThis.performance.now()
  finishRhythmRound(rhythmGameState.value, now, 'MANUAL')
  stopRhythmAudio()
  toResult()
}

function stopRhythmGame() {
  if (rhythmRafHandle !== undefined) {
    globalThis.cancelAnimationFrame(rhythmRafHandle)
    rhythmRafHandle = undefined
  }
  if (rhythmStartCountdownTimer !== undefined) {
    globalThis.clearInterval(rhythmStartCountdownTimer)
    rhythmStartCountdownTimer = undefined
  }
  if (rhythmAudioStartTimer !== undefined) {
    globalThis.clearTimeout(rhythmAudioStartTimer)
    rhythmAudioStartTimer = undefined
  }
  isRhythmStartCountdownOpen.value = false
  unsubscribeRhythmEvents?.()
  unsubscribeRhythmEvents = undefined
  clearRhythmFeedback()
  stopRhythmAudio()
  rhythmTracking.stop()
  rhythmGameSession.close()
}

// --- 에어하키: 실제 시선 인식 + 캔버스 물리엔진 연동 ---
// 스펙상 혼자하기가 없다(친구 대결/랜덤 매칭/AI 대결뿐) — 그래서 항상 상대(AI 또는 실제 플레이어)가
// 있다. 시선의 "좌표"(연속값)로 패들을 움직여야 해서 blink/hold/rhythm과 달리 이벤트가 아니라
// 매 프레임 screenGaze를 읽는다. 친구/랜덤 대결은 각자 자기 화면에서 독립적으로 물리 연산을 하고
// (상대 패들 위치·점수만 실시간으로 주고받는다) — 그래서 두 화면의 퍽 궤적이 아주 살짝 다르게 보일
// 수 있다(네트워크 지연 때문에 상대 패들 위치를 몇십 ms 늦게 반영하니까). 카지노급 판정 공정성이
// 필요한 게임이 아니라서 이 정도 오차는 허용한다 — 완벽한 서버 권위 동기화는 더 큰 작업이라 이후
// 과제로 남긴다.
const AIR_MALLET_SMOOTHING = 0.11
const AIR_MOVE_SEND_INTERVAL_MS = 50

const airTracking = useEyeTracking()
const airVideoRef = airTracking.videoRef
const airCameraActive = airTracking.isActive
// vue-tsc가 문자열 템플릿 ref(ref="airVideoRef")를 '사용'으로 인식하지 못해 noUnusedLocals가
// 오탐한다 — blinkVideoRef 등과 동일한 이유.
void airVideoRef

const airCanvasRef = ref<globalThis.HTMLCanvasElement | null>(null)
const airGameState = ref<AirHockeyState>(makeInitialAirHockeyState())
const isAirVsAi = computed(() => mode.value === 'ai')
const airOpponentScore = ref(0)
const airOpponentSynced = ref(false)
// 멀티플레이 퍽 권위자(host). 두 참가자 키 중 사전순으로 작은 쪽을 호스트로 정한다(양쪽이 동일하게
// 결정). 호스트만 퍽을 시뮬레이션·득점하고 상대에게 브로드캐스트하며, 비호스트는 받은 퍽을 렌더한다.
const airIsHost = ref(false)
const airRunsPuck = computed(() => isAirVsAi.value || airIsHost.value)
const airMyScore = computed(() => airGameState.value.bottom.score)
const airOpponentDisplayScore = computed(() =>
  airRunsPuck.value ? airGameState.value.top.score : airOpponentScore.value,
)
const airTimeLabel = computed(() => {
  const totalSeconds = Math.max(
    Math.ceil(airGameState.value.remainingMs / 1000),
    0,
  )
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})
let airRafHandle: number | undefined
let unsubscribeAirEvents: (() => void) | undefined
let airLastFrameAt: number | undefined
let airLastMoveSentAt = 0

const airGameSession = useGameSessionSocket({
  onSessionState: (state) => {
    updateOpponentNickname(state)
    // 두 참가자 키 중 사전순 최솟값을 호스트로 정한다(양쪽 클라이언트가 동일한 결론).
    const myKey = currentParticipantKey()
    const keys = state.participants
      .map((participant) => participant.participantKey)
      .filter((key): key is string => Boolean(key))
    if (myKey && keys.length >= 2) {
      airIsHost.value = keys.every((key) => key === myKey || myKey < key)
    }
  },
  onPlayerEvent: (event) => {
    if (event.eventType === 'GAME_OVER') {
      opponentFinished = true
      return
    }
    if (event.eventType === 'AIR_HOCKEY_MOVE') {
      const targetX = Number(event.payload?.targetX)
      if (Number.isFinite(targetX)) airGameState.value.top.targetX = targetX
      return
    }
    if (event.eventType === 'AIR_HOCKEY_ACTION') {
      // 비호스트가 보낸 타격. 호스트가 상대(top) 말렛으로 반영해 권위 퍽에 적용한다.
      applyStrike(airGameState.value, 'top', globalThis.performance.now())
      return
    }
    if (event.eventType === 'AIR_HOCKEY_PUCK') {
      // 호스트가 보낸 권위 퍽. 비호스트는 자기 시점(하단=나)으로 Y를 미러링해 렌더한다.
      applyAuthoritativePuck(event.payload ?? {})
      return
    }
    if (event.eventType === 'AIR_HOCKEY_SCORE') {
      const score = Number(event.payload?.score)
      if (Number.isFinite(score)) airOpponentScore.value = score
      airOpponentSynced.value = true
    }
  },
  onParticipantLeft: handleOpponentLeft,
})

/** 호스트가 보낸 권위 퍽 스냅샷을 비호스트 시점(Y 미러링)으로 적용한다. 점수도 호스트 기준으로 맞춘다. */
function applyAuthoritativePuck(payload: Record<string, unknown>): void {
  const state = airGameState.value
  const x = Number(payload.x)
  const y = Number(payload.y)
  const vx = Number(payload.vx)
  const vy = Number(payload.vy)
  if (Number.isFinite(x)) state.puck.x = x
  if (Number.isFinite(y)) state.puck.y = AIR_HOCKEY_HEIGHT - y
  if (Number.isFinite(vx)) state.puck.vx = vx
  if (Number.isFinite(vy)) state.puck.vy = -vy
  state.puck.held = Boolean(payload.held)
  state.server = payload.server === 'bottom' ? 'top' : 'bottom'
  // 호스트 프레임 기준 bottom=호스트 득점, top=비호스트(나) 득점 → 미러링해 매핑한다.
  const bottomScore = Number(payload.bottomScore)
  const topScore = Number(payload.topScore)
  if (Number.isFinite(topScore)) state.bottom.score = topScore
  if (Number.isFinite(bottomScore)) {
    state.top.score = bottomScore
    airOpponentScore.value = bottomScore
  }
  airOpponentSynced.value = true
}

async function initAirHockeyGame() {
  if (calibrationStore.eyeProfile) {
    airTracking.applyEyeProfile(calibrationStore.eyeProfile)
  }
  // 그림그리기와 마찬가지로 화면 좌표가 필요한 게임이라, 대기방에서 저장해 둔 9점 시선 보정
  // 결과가 있으면 그대로 이어받는다.
  if (calibrationStore.gazeProfile) {
    airTracking.applyGazeProfile(calibrationStore.gazeProfile)
  }

  const started = await airTracking.start()
  if (!started) {
    showToast('카메라를 시작하지 못했어요. 카메라 권한을 확인해 주세요.')
    return
  }

  if (showsOpponentCamera.value && mediaSession.credentials) {
    await connectMedia(mediaSession.credentials, {
      localTrack: airTracking.stream.value?.getVideoTracks()[0] ?? null,
    })
  }

  if (!isAirVsAi.value) {
    const roomId = String(route.query.roomId ?? '')
    const identity = resolveIdentity()
    if (roomId && identity) airGameSession.connect(roomId, identity)
  }

  unsubscribeAirEvents = airTracking.onEyeEvent((event) => {
    if (
      event.type === 'BLINK' ||
      event.type === 'FAST_BLINK' ||
      event.type === 'DOUBLE_BLINK'
    ) {
      // 퍽을 시뮬하는 쪽(AI 모드 또는 호스트)만 로컬에서 타격을 적용한다.
      // 비호스트는 호스트에게 타격을 알려 호스트의 상대(top) 말렛으로 반영시킨다(권위 퍽에만 적용).
      if (airRunsPuck.value) {
        applyStrike(airGameState.value, 'bottom', event.occurredAt)
      } else {
        airGameSession.sendPlayerEvent('AIR_HOCKEY_ACTION')
      }
    }
  })

  startAirHockeyMatch(airGameState.value, globalThis.performance.now())
  resetPuckForServe(airGameState.value)
  runAirHockeyLoop()
}

function updateAirMalletFromGaze() {
  // 눈을 감거나 깜빡이는 동안엔 시선 좌표가 튀어 말렛이 급가속하거나 반대 방향으로 움직인다.
  // 두 눈이 모두 뜬 프레임에서만 목표 위치를 갱신하고, 그 외(깜빡임·윙크·미검출)엔 직전 위치를
  // 유지한다. 서브용 깜빡임(applyStrike)은 별도 이벤트라 그대로 동작한다.
  if (airTracking.combinedState.value !== 'BOTH_OPEN') return
  const gaze = airTracking.screenGaze.value
  if (!gaze) return
  const desired = Math.min(1, Math.max(0, gaze.x)) * AIR_HOCKEY_WIDTH
  // 깜빡임 직전 반쯤 감긴 프레임의 튄 시선이 목표를 한 번에 확 옮겨(퍽까지) 튀게 하지 않도록
  // 한 프레임당 이동량을 제한한다. 정상 시선 이동은 프레임마다 조금씩이라 영향이 없다.
  const maxStep = AIR_HOCKEY_WIDTH * 0.12
  const current = airGameState.value.bottom.targetX
  const step = Math.max(-maxStep, Math.min(maxStep, desired - current))
  airGameState.value.bottom.targetX = current + step
}

function updateAirAiTarget(dt: number) {
  if (!isAirVsAi.value) return
  const state = airGameState.value
  const lead = state.puck.vy < 0 ? state.puck.vx * 0.18 : 0
  const desired = state.puck.x + lead
  const maxStep = 280 * dt
  const delta = Math.min(
    Math.max(desired - state.top.targetX, -maxStep),
    maxStep,
  )
  state.top.targetX += delta

  // AI가 서브를 쥐고 있으면 득점 0.7초 후 자동으로 발사한다(안 그러면 게임이 멈춘 채로 남는다).
  if (state.puck.held && state.server === 'top') {
    const now = globalThis.performance.now()
    if (now - state.lastGoalAt > 700) {
      launchPuck(state, 'top')
    }
  }
}

function clampMalletTarget(mallet: Mallet, now: number) {
  const radius = mallet.boostUntil > now ? mallet.r + 4 : mallet.r
  const min = radius + 24
  const max = AIR_HOCKEY_WIDTH - radius - 24
  mallet.targetX = Math.min(Math.max(mallet.targetX, min), max)
}

function updateAirMallet(mallet: Mallet, now: number) {
  mallet.lastX = mallet.x
  clampMalletTarget(mallet, now)
  mallet.x += (mallet.targetX - mallet.x) * AIR_MALLET_SMOOTHING
  clampMalletTarget(mallet, now)
  mallet.x = Math.min(
    Math.max(mallet.x, mallet.r + 24),
    AIR_HOCKEY_WIDTH - mallet.r - 24,
  )
}

function updateAirPuckPhysics(dt: number, now: number) {
  const state = airGameState.value
  if (state.puck.held) {
    const holder = state.server
    const mallet = state[holder]
    state.puck.x = mallet.x
    state.puck.y = holder === 'bottom' ? mallet.y - 68 : mallet.y + 68
    return
  }

  state.puck.x += state.puck.vx * dt
  state.puck.y += state.puck.vy * dt
  state.puck.vx *= 0.999
  state.puck.vy *= 0.999

  if (
    state.puck.x - state.puck.r <= 26 ||
    state.puck.x + state.puck.r >= AIR_HOCKEY_WIDTH - 26
  ) {
    state.puck.x = Math.min(
      Math.max(state.puck.x, 26 + state.puck.r),
      AIR_HOCKEY_WIDTH - 26 - state.puck.r,
    )
    state.puck.vx *= -1
  }

  const goalResult = getGoalResult(state)
  if (goalResult === 'wall') {
    state.puck.y = Math.min(
      Math.max(state.puck.y, state.puck.r + 2),
      AIR_HOCKEY_HEIGHT - state.puck.r - 2,
    )
    state.puck.vy *= -1
  } else if (goalResult === 'top' || goalResult === 'bottom') {
    // 퍽을 시뮬하는 쪽(AI/호스트)만 득점을 판정한다. 멀티 점수는 매 프레임 AIR_HOCKEY_PUCK로 함께 보낸다.
    scoreGoal(state, goalResult, now)
    return
  }

  resolveMalletCollision(state, 'bottom')
  resolveMalletCollision(state, 'top')
}

function drawAirHockey() {
  const canvas = airCanvasRef.value
  const ctx = canvas?.getContext('2d')
  if (!canvas || !ctx) return
  const state = airGameState.value

  ctx.clearRect(0, 0, AIR_HOCKEY_WIDTH, AIR_HOCKEY_HEIGHT)

  ctx.fillStyle = '#111827'
  ctx.fillRect(0, 0, AIR_HOCKEY_WIDTH, AIR_HOCKEY_HEIGHT)
  ctx.fillStyle = '#f8fafc'
  ctx.beginPath()
  ctx.roundRect(44, 32, AIR_HOCKEY_WIDTH - 88, AIR_HOCKEY_HEIGHT - 64, 28)
  ctx.fill()
  ctx.strokeStyle = '#cbd5e1'
  ctx.lineWidth = 3
  ctx.stroke()

  ctx.strokeStyle = '#d1d5db'
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo(58, AIR_HOCKEY_HEIGHT / 2)
  ctx.lineTo(AIR_HOCKEY_WIDTH - 58, AIR_HOCKEY_HEIGHT / 2)
  ctx.stroke()

  ctx.strokeStyle = '#ef4444'
  ctx.lineWidth = 3
  ctx.beginPath()
  ctx.arc(AIR_HOCKEY_WIDTH / 2, AIR_HOCKEY_HEIGHT / 2, 54, 0, Math.PI * 2)
  ctx.stroke()
  ctx.beginPath()
  ctx.arc(AIR_HOCKEY_WIDTH / 2, 138, 52, 0, Math.PI * 2)
  ctx.stroke()
  ctx.strokeStyle = '#14b8a6'
  ctx.beginPath()
  ctx.arc(AIR_HOCKEY_WIDTH / 2, AIR_HOCKEY_HEIGHT - 138, 52, 0, Math.PI * 2)
  ctx.stroke()

  ctx.fillStyle = '#111827'
  ctx.fillRect(AIR_HOCKEY_WIDTH / 2 - 138, 24, 276, 20)
  ctx.fillRect(AIR_HOCKEY_WIDTH / 2 - 138, AIR_HOCKEY_HEIGHT - 44, 276, 20)

  drawAirMallet(ctx, state.top, '#ef4444', '#fecaca')
  drawAirMallet(ctx, state.bottom, '#14b8a6', '#ccfbf1')

  ctx.fillStyle = 'rgba(15,23,42,0.24)'
  ctx.beginPath()
  ctx.ellipse(
    state.puck.x + 8,
    state.puck.y + 10,
    state.puck.r * 1.1,
    state.puck.r * 0.52,
    0,
    0,
    Math.PI * 2,
  )
  ctx.fill()
  ctx.fillStyle = '#0f172a'
  ctx.beginPath()
  ctx.arc(state.puck.x, state.puck.y, state.puck.r, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = '#475569'
  ctx.beginPath()
  ctx.arc(
    state.puck.x - 4,
    state.puck.y - 4,
    state.puck.r * 0.36,
    0,
    Math.PI * 2,
  )
  ctx.fill()

  if (state.puck.held || state.gameOver) {
    ctx.fillStyle = 'rgba(15,23,42,0.78)'
    ctx.fillRect(74, AIR_HOCKEY_HEIGHT / 2 - 40, AIR_HOCKEY_WIDTH - 148, 80)
    ctx.fillStyle = '#f8fafc'
    ctx.font = '20px system-ui, sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText(state.message, AIR_HOCKEY_WIDTH / 2, AIR_HOCKEY_HEIGHT / 2 + 8)
    ctx.textAlign = 'left'
  }
}

function drawAirMallet(
  ctx: globalThis.CanvasRenderingContext2D,
  mallet: Mallet,
  color: string,
  highlight: string,
) {
  ctx.fillStyle = 'rgba(15,23,42,0.24)'
  ctx.beginPath()
  ctx.ellipse(
    mallet.x + 10,
    mallet.y + 12,
    mallet.r * 0.92,
    mallet.r * 0.42,
    0,
    0,
    Math.PI * 2,
  )
  ctx.fill()

  ctx.fillStyle = color
  ctx.beginPath()
  ctx.arc(mallet.x, mallet.y, mallet.r, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = highlight
  ctx.beginPath()
  ctx.arc(mallet.x, mallet.y - 4, mallet.r * 0.54, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = color
  ctx.beginPath()
  ctx.arc(mallet.x, mallet.y - 12, mallet.r * 0.28, 0, Math.PI * 2)
  ctx.fill()
}

function runAirHockeyLoop() {
  const tick = (now: number) => {
    const dt =
      airLastFrameAt === undefined
        ? 0
        : Math.min((now - airLastFrameAt) / 1000, 0.033)
    airLastFrameAt = now

    updateAirMalletFromGaze()
    updateAirAiTarget(dt)
    updateAirMallet(airGameState.value.bottom, now)
    updateAirMallet(airGameState.value.top, now)
    // 퍽은 권위자(AI 모드 또는 멀티 호스트)만 시뮬·득점한다. 비호스트는 AIR_HOCKEY_PUCK로 받은 퍽을 렌더만 한다.
    if (airRunsPuck.value) {
      updateAirPuckPhysics(dt, now)
    }

    if (
      !isAirVsAi.value &&
      now - airLastMoveSentAt >= AIR_MOVE_SEND_INTERVAL_MS
    ) {
      airLastMoveSentAt = now
      airGameSession.sendPlayerEvent('AIR_HOCKEY_MOVE', {
        targetX: airGameState.value.bottom.targetX,
      })
    }

    // 호스트는 권위 퍽 상태와 양쪽 점수를 매 프레임 비호스트에게 보낸다(호스트 좌표 기준).
    if (!isAirVsAi.value && airIsHost.value) {
      const puck = airGameState.value.puck
      airGameSession.sendPlayerEvent('AIR_HOCKEY_PUCK', {
        x: puck.x,
        y: puck.y,
        vx: puck.vx,
        vy: puck.vy,
        held: puck.held,
        server: airGameState.value.server,
        bottomScore: airGameState.value.bottom.score,
        topScore: airGameState.value.top.score,
      })
    }

    const finished = updateAirHockeyMatch(airGameState.value, now)
    drawAirHockey()

    if (finished) {
      toResult()
      return
    }
    airRafHandle = globalThis.requestAnimationFrame(tick)
  }
  airRafHandle = globalThis.requestAnimationFrame(tick)
}

function stopAirHockeyGame() {
  if (airRafHandle !== undefined) {
    globalThis.cancelAnimationFrame(airRafHandle)
    airRafHandle = undefined
  }
  unsubscribeAirEvents?.()
  unsubscribeAirEvents = undefined
  airTracking.stop()
  airGameSession.close()
}

function clearReplayCountdown() {
  if (!replayCountdownTimer) return
  globalThis.clearInterval(replayCountdownTimer)
  replayCountdownTimer = undefined
}

// --- 게임플레이 배경음악(BGM) ---
// 게임 종류별 배경음악. 리듬은 음원 자체가 게임 요소(rhythmAudio)이므로 매핑에 넣지 않는다.
const GAME_BGM_URLS: Record<string, string> = {
  air: '/audio/bgm-air.mp3',
  draw: '/audio/bgm-draw.mp3',
  hold: '/audio/bgm-hold.mp3',
  blink: '/audio/bgm-blink.mp3',
}
let gameBgmAudio: globalThis.HTMLAudioElement | undefined

/** 게임 시작 시 해당 게임의 BGM을 반복 재생한다. 매핑에 없는 게임(rhythm 포함)은 아무 것도 하지 않는다. */
function playGameBgm(gameId: string): void {
  // startGame()이 중복 호출되어도 이미 재생 중인 오디오를 또 만들지 않는다.
  if (gameBgmAudio) return
  const url = GAME_BGM_URLS[gameId]
  if (!url) return

  const audio = new globalThis.Audio(url)
  audio.loop = true
  audio.volume = 0.5
  gameBgmAudio = audio
  // 브라우저 자동재생 정책으로 재생이 거부될 수 있어 실패는 조용히 무시한다. 테스트 환경(jsdom)처럼
  // play()가 Promise를 반환하지 않는 경우도 있어 옵셔널 체이닝으로 방어한다.
  audio.play()?.catch(() => {})
}

/** 게임 BGM을 멈추고 처음으로 되감는다. 결과 화면 전환·상대 이탈·게임 나가기 등
 * 여러 종료 경로에서 각각 호출되므로 중복 호출에도 안전해야 한다. */
function stopGameBgm(): void {
  if (!gameBgmAudio) return
  gameBgmAudio.pause()
  gameBgmAudio.currentTime = 0
  gameBgmAudio = undefined
}

function startGame() {
  if (!game.value) return
  playStartedAt = new Date().toISOString()

  if (game.value.id === 'hold') {
    void initStareGame()
  }
  if (game.value.id === 'blink') {
    void initBlinkGame()
  }
  if (game.value.id === 'rhythm') {
    void initRhythmGame()
  }
  if (game.value.id === 'air') {
    void initAirHockeyGame()
    void nextTick(scrollToAirGameStart)
    airGameScrollTimer = globalThis.setTimeout(scrollToAirGameStart, 250)
  }
  if (game.value.id === 'draw') {
    void initDrawGame()
  }
  playGameBgm(game.value.id)
  globalThis.window.addEventListener('beforeunload', handleBeforeUnload)
  cameraWatchdog = globalThis.setInterval(pollCameraFrames, 1000)
}

function finishReplayCountdown() {
  clearReplayCountdown()
  isReplayCountdownOpen.value = false
  const playQuery = { ...route.query }
  delete playQuery.replay
  void router.replace({ query: playQuery })
  startGame()
}

function openReplayCountdown() {
  clearReplayCountdown()
  // 리듬은 게임 화면 안에서 자체 카운트다운을 하므로, 다시하기도 리플레이 카운트다운을 건너뛰고
  // 바로 시작한다(게임 안 3·2·1이 대신 뜬다).
  if (game.value?.id === 'rhythm') {
    finishReplayCountdown()
    return
  }
  replayCountdown.value = 3
  isReplayCountdownOpen.value = true
  replayCountdownTimer = globalThis.setInterval(() => {
    if (replayCountdown.value <= 1) {
      finishReplayCountdown()
      return
    }
    replayCountdown.value -= 1
  }, 1000)
}

onMounted(() => {
  // 진행 중이던 게임을 새로고침한 경우: 정책대로 재시작하지 않고 종료한다(1라운드부터 다시 시작 방지).
  if (handleMidGameRefresh()) return

  if (route.query.replay === '1') openReplayCountdown()
  else startGame()
})

onUnmounted(() => {
  clearReplayCountdown()
  if (airGameScrollTimer) globalThis.clearTimeout(airGameScrollTimer)
  if (cameraWatchdog) globalThis.clearInterval(cameraWatchdog)
  // 정상적인 화면 이탈(라우터 이동)에서만 진행 표시를 지운다. 브라우저 새로고침은 컴포넌트
  // 언마운트를 트리거하지 않으므로 표시가 남아, 재진입 시 새로고침으로 감지된다.
  clearGameInProgress()
  globalThis.window.removeEventListener('beforeunload', handleBeforeUnload)
  // 게임 종류와 무관하게(rhythm 제외) BGM이 재생 중일 수 있으므로 언마운트 시 항상 정지를 시도한다.
  stopGameBgm()
  if (game.value?.id === 'hold') stopStareGame()
  if (game.value?.id === 'blink') stopBlinkGame()
  if (game.value?.id === 'rhythm') stopRhythmGame()
  if (game.value?.id === 'air') stopAirHockeyGame()
  if (game.value?.id === 'draw') stopDrawGame()
})

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
const isStareDuel = computed(
  () => game.value?.id === 'hold' && ['friends', 'random'].includes(mode.value),
)

// 눈싸움(hold) 대결: 상대 웹캠을 실제 미디어 서버로 주고받는다.
const mediaSession = useMediaSessionStore()
const {
  remoteVideoRef,
  hasRemoteVideo,
  connect: connectMedia,
} = useLiveKitRoom()
const hasPeerCamera = computed(() => hasRemoteVideo.value)

// vue-tsc는 컴포저블이 소유한 ref를 문자열 템플릿 ref(ref="...")에 바인딩할 때 '사용'으로
// 인식하지 못해 noUnusedLocals 오탐을 낸다. 실제로는 아래 <video ref="..."> 요소들에 런타임
// 바인딩되므로, 여기서 명시적으로 참조만 남겨 둔다.
void remoteVideoRef

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

// 게임 종료 시 결과를 저장하는 파이프라인. 실패해도 화면 전환은 막지 않는다.
const { submitPlayedResult } = useGameResultSubmission()
const lastGameResultStore = useLastGameResultStore()

async function toResult() {
  if (!game.value) return
  exiting = true
  clearGameInProgress()
  // 결과 제출(submitPlayedResult)이 끝날 때까지 화면 전환이 지연될 수 있으므로, 언마운트를
  // 기다리지 않고 결과 화면으로 넘어가기로 결정된 이 시점에 BGM을 바로 멈춘다.
  stopGameBgm()
  // 정상 종료를 상대에게 알린다(이후 소켓 종료를 상대가 이탈로 오인해 몰수 처리하지 않게).
  sendActiveGameOver()
  const score =
    game.value.id === 'blink'
      ? blinkGameState.value.score
      : game.value.id === 'hold'
        ? Math.round(stareGameState.value.elapsedMs / 1000)
        : game.value.id === 'rhythm'
          ? rhythmGameState.value.score
          : game.value.id === 'air'
            ? airGameState.value.bottom.score
            : game.value.id === 'draw'
              ? drawGameState.value.score
              : (session.value?.score ?? 0)

  if (game.value.id === 'blink') recordBlinkResult()
  if (game.value.id === 'hold') recordStareResult()
  if (game.value.id === 'rhythm') recordRhythmResult()
  if (game.value.id === 'air') recordAirHockeyResult()
  if (game.value.id === 'draw') recordDrawResult()

  const resultQuery = { ...route.query }
  if (
    game.value.id === 'rhythm' &&
    mode.value === 'solo' &&
    rhythmGameState.value.finishReason === 'HEALTH_EMPTY'
  ) {
    resultQuery.result = 'failed'
  } else {
    delete resultQuery.result
  }

  const storedOutcome = lastGameResultStore.current?.outcome
  const submissionOutcome =
    storedOutcome === 'UNKNOWN' ? undefined : storedOutcome
  // 멀티플레이(혼자·AI가 아닌 대결)에서만 상대 점수를 기록에 남긴다. 전적 상세의
  // "함께한 상대" 표에서 상대 점수 칸을 채우는 데 쓴다. 내 slot score와 같은 단위로 저장한다.
  const isMultiplayer = mode.value !== 'solo' && mode.value !== 'ai'
  const resultData =
    game.value.id === 'hold'
      ? {
          survivalTimeMs: Math.round(stareGameState.value.elapsedMs),
          opponentScore: isMultiplayer
            ? Math.round(stareOpponentElapsedMs.value / 1000)
            : undefined,
        }
      : game.value.id === 'blink'
        ? {
            blinkCount: blinkGameState.value.blinkCount,
            opponentScore:
              isMultiplayer && opponentBlinkCount.value !== null
                ? opponentBlinkCount.value
                : undefined,
          }
        : game.value.id === 'rhythm'
          ? {
              maxCombo: rhythmGameState.value.maxCombo,
              remainingHearts: rhythmGameState.value.health,
              opponentScore: rhythmOpponentSynced.value
                ? rhythmOpponent.value.score
                : undefined,
            }
          : game.value.id === 'air' && mode.value === 'ai'
            ? { opponentScore: airGameState.value.top.score }
            : game.value.id === 'air'
              ? { opponentScore: airOpponentDisplayScore.value }
              : game.value.id === 'draw'
                ? { drawRounds: [...drawGameState.value.history] }
                : undefined
  const persistedResultData =
    resultData && opponentNickname.value
      ? { ...resultData, opponentNickname: opponentNickname.value }
      : resultData
  const submission = await submitPlayedResult({
    gameSlug: game.value.id,
    mode: mode.value,
    startedAt: playStartedAt,
    score,
    outcome: submissionOutcome,
    resultData: persistedResultData,
  })
  if (submission !== null && lastGameResultStore.current) {
    lastGameResultStore.set({
      ...lastGameResultStore.current,
      resultId: submission.resultId,
      isNewRecord: submission.isNewRecord,
      previousBestScore: submission.previousBestScore,
    })
    resultQuery.resultId = String(submission.resultId)
  }
  router.push({
    name: 'game-result',
    params: { gameId: game.value.id },
    query: resultQuery,
  })
}

/** 눈 깜빡이기 실제 결과를 결과 화면용으로 기록한다. 대결 모드는 상대 실시간 횟수와 비교해 승패를 정한다. */
function recordBlinkResult() {
  const myCount = blinkGameState.value.blinkCount
  const outcome: LastGameOutcome =
    mode.value === 'solo'
      ? 'COMPLETED'
      : opponentBlinkCount.value === null
        ? 'UNKNOWN'
        : myCount > opponentBlinkCount.value
          ? 'WIN'
          : myCount < opponentBlinkCount.value
            ? 'LOSE'
            : 'DRAW'

  lastGameResultStore.set({
    gameId: 'blink',
    mode: mode.value,
    opponentNickname: opponentNickname.value,
    outcome,
    scoreLabel: '점수',
    score: `${myCount}`,
    opponentScore:
      opponentBlinkCount.value !== null
        ? `${opponentBlinkCount.value}`
        : undefined,
    headline:
      outcome === 'WIN'
        ? '승리했어요!'
        : outcome === 'LOSE'
          ? '아쉽게 졌어요'
          : outcome === 'DRAW'
            ? '무승부예요'
            : '집중력 대성공!',
    summary:
      outcome === 'WIN'
        ? '상대보다 더 많이 깜빡였어요!'
        : outcome === 'LOSE'
          ? '다음엔 더 빠르게 깜빡여 보세요!'
          : outcome === 'DRAW'
            ? '정말 팽팽한 대결이었어요!'
            : '20초 동안 정확하게 눈을 깜빡였어요.',
    stats: [],
  })
}

/** 눈싸움 실제 결과를 결과 화면용으로 기록한다. */
function recordStareResult() {
  const survivalSeconds = stareGameState.value.elapsedMs / 1000
  const scoreDisplay = `${survivalSeconds.toFixed(1)}초`
  const opponentSurvivalSeconds = stareOpponentElapsedMs.value / 1000
  const opponentScoreDisplay = `${opponentSurvivalSeconds.toFixed(1)}초`
  const outcome: LastGameOutcome =
    mode.value === 'solo'
      ? 'COMPLETED'
      : stareGameState.value.outcome === 'NONE'
        ? 'UNKNOWN'
        : stareGameState.value.outcome

  lastGameResultStore.set({
    gameId: 'hold',
    mode: mode.value,
    opponentNickname: opponentNickname.value,
    outcome,
    scoreLabel: '생존 시간',
    score: scoreDisplay,
    opponentScore:
      isStareDuel.value && stareOpponentSynced.value
        ? opponentScoreDisplay
        : undefined,
    headline:
      outcome === 'WIN'
        ? 'AI보다 오래 버텼어요!'
        : outcome === 'LOSE'
          ? '아쉽게 눈을 감았어요'
          : '기록 갱신!',
    summary:
      outcome === 'WIN'
        ? '눈을 뜬 채로 목표 시간을 버텨냈어요!'
        : outcome === 'LOSE'
          ? '다음엔 더 오래 버텨보세요!'
          : '시선을 끝까지 유지하며 기록을 만들었어요.',
    stats: [
      {
        label: '생존 시간',
        value: scoreDisplay,
        opponentValue:
          isStareDuel.value && stareOpponentSynced.value
            ? opponentScoreDisplay
            : undefined,
      },
      {
        label: '패배 사유',
        value:
          stareGameState.value.loseReason === 'FACE_LOST'
            ? '얼굴 인식 끊김'
            : stareGameState.value.loseReason === 'NONE'
              ? '-'
              : '눈 감음',
        // 상대가 왜 졌는지(혹은 안 졌는지)는 전송받지 않으므로 좌우 미러링하지 않는다.
        opponentValue: '-',
      },
    ],
  })
}

/** 리듬게임 실제 결과를 결과 화면용으로 기록한다. 대결 모드는 상대 실시간 점수와 비교해 승패를 정한다. */
function recordRhythmResult() {
  const myScore = rhythmGameState.value.score
  const outcome: LastGameOutcome =
    mode.value === 'solo'
      ? 'COMPLETED'
      : opponentHealthDepleted.value
        ? 'WIN'
        : rhythmGameState.value.finishReason === 'HEALTH_EMPTY'
          ? 'LOSE'
          : !rhythmOpponentSynced.value
            ? 'UNKNOWN'
            : myScore > rhythmOpponent.value.score
              ? 'WIN'
              : myScore < rhythmOpponent.value.score
                ? 'LOSE'
                : 'DRAW'

  lastGameResultStore.set({
    gameId: 'rhythm',
    mode: mode.value,
    opponentNickname: opponentNickname.value,
    outcome,
    scoreLabel: '점수',
    score: `${myScore.toLocaleString()}점`,
    opponentScore: rhythmOpponentSynced.value
      ? `${rhythmOpponent.value.score.toLocaleString()}점`
      : undefined,
    headline:
      outcome === 'WIN'
        ? '리듬 마스터!'
        : outcome === 'LOSE'
          ? '아쉽게 졌어요'
          : outcome === 'DRAW'
            ? '무승부예요'
            : 'RHYTHM CLEAR!',
    summary:
      outcome === 'WIN'
        ? '완벽한 박자 감각이었어요!'
        : outcome === 'LOSE'
          ? '다음엔 더 정확하게 맞춰보세요!'
          : outcome === 'DRAW'
            ? '정말 팽팽한 대결이었어요!'
            : '리듬을 놓치지 않고 끝까지 완주했어요.',
    stats: [
      { label: '최대 콤보', value: `${rhythmGameState.value.maxCombo}` },
      { label: '남은 하트', value: `${rhythmGameState.value.health}` },
    ],
  })
}

/** 에어하키 실제 결과를 결과 화면용으로 기록한다. */
function recordAirHockeyResult() {
  const myScore = airGameState.value.bottom.score
  const opponentScore = airOpponentDisplayScore.value
  const elapsedSeconds = Math.max(
    0,
    Math.round(
      (AIR_HOCKEY_MATCH_DURATION_MS - airGameState.value.remainingMs) / 1000,
    ),
  )
  const opponentKnown = airRunsPuck.value || airOpponentSynced.value
  const outcome: LastGameOutcome = isAirVsAi.value
    ? resolveAirHockeyAiOutcome()
    : !opponentKnown
      ? 'UNKNOWN'
      : myScore > opponentScore
        ? 'WIN'
        : myScore < opponentScore
          ? 'LOSE'
          : 'DRAW'

  lastGameResultStore.set({
    gameId: 'air',
    mode: mode.value,
    opponentNickname: opponentNickname.value,
    outcome,
    scoreLabel: '득점',
    score: `${myScore}`,
    opponentScore: opponentKnown ? `${opponentScore}` : undefined,
    headline:
      outcome === 'WIN'
        ? '승리!'
        : outcome === 'LOSE'
          ? '아쉽게 졌어요'
          : outcome === 'DRAW'
            ? '무승부예요'
            : '경기 종료!',
    summary:
      outcome === 'WIN'
        ? '마지막 골까지 집중력을 유지했어요.'
        : outcome === 'LOSE'
          ? '다음엔 더 정확하게 시선을 노려보세요!'
          : outcome === 'DRAW'
            ? '한 골 차이도 안 나는 접전이었어요!'
            : '1분 동안 최선을 다했어요.',
    stats: [
      {
        label: '득점',
        value: `${myScore}골`,
        opponentValue: `${opponentScore}골`,
      },
      {
        label: '실점',
        value: `${opponentScore}골`,
        opponentValue: `${myScore}골`,
      },
      {
        label: '경기 시간',
        value: `${String(Math.floor(elapsedSeconds / 60)).padStart(2, '0')}:${String(elapsedSeconds % 60).padStart(2, '0')}`,
      },
    ],
  })
}

function resolveAirHockeyAiOutcome(): 'WIN' | 'LOSE' | 'DRAW' {
  const winner = determineAirHockeyWinner(airGameState.value)
  if (winner === 'bottom') return 'WIN'
  if (winner === 'top') return 'LOSE'
  return 'DRAW'
}

async function initDrawGame() {
  if (calibrationStore.eyeProfile) {
    drawTracking.applyEyeProfile(calibrationStore.eyeProfile)
  }
  if (calibrationStore.gazeProfile) {
    drawTracking.applyGazeProfile(calibrationStore.gazeProfile)
  }

  const started = await drawTracking.start()
  if (!started) {
    showToast('카메라를 시작하지 못했어요. 카메라 권한을 확인해 주세요.')
    return
  }

  drawWords.value = pickWordsForGame()
  drawStrokes = []
  drawActiveStroke = null
  isDrawingActive.value = true
  startDrawRound(drawGameState.value, drawWords.value)

  unsubscribeDrawKeydown = onDrawKeydown()
  runDrawLoop()
}

/** Space 키로 그리기를 일시정지/재개한다(기획 확정본 조작 방식). */
function onDrawKeydown(): () => void {
  const handler = (event: globalThis.KeyboardEvent) => {
    if (event.code !== 'Space' && event.key !== ' ') return
    // 답 입력 등 다른 텍스트 입력 중엔 Space가 그 입력에 쓰이도록 두고 그리기 토글은 막는다.
    const target = event.target as globalThis.HTMLElement | null
    if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA'))
      return
    event.preventDefault()
    isDrawingActive.value = !isDrawingActive.value
  }
  globalThis.window.addEventListener('keydown', handler)
  return () => globalThis.window.removeEventListener('keydown', handler)
}

function runDrawLoop() {
  const tick = (now: number, previous?: number) => {
    const deltaMs = previous === undefined ? 0 : now - previous

    updateDrawCursorFromGaze()

    if (drawGameState.value.phase === 'running') {
      if (tickDrawRoundTimer(drawGameState.value, deltaMs)) {
        void submitDrawRound('시간 종료')
      }
    }

    renderDrawCanvas()
    drawRafHandle = globalThis.requestAnimationFrame((next) => tick(next, now))
  }
  drawRafHandle = globalThis.requestAnimationFrame((first) => tick(first))
}

function updateDrawCursorFromGaze() {
  const gaze = drawTracking.screenGaze.value
  const faceOk =
    drawTracking.faceDetected.value &&
    drawTracking.combinedState.value === 'BOTH_OPEN'
  if (!gaze || !faceOk) {
    drawCursor.value = null
    drawSmoothedCursor = null
    return
  }

  const raw = {
    x: Math.min(1, Math.max(0, gaze.x)),
    y: Math.min(1, Math.max(0, gaze.y)),
  }
  if (!drawSmoothedCursor) {
    drawSmoothedCursor = raw
  } else {
    const dx = raw.x - drawSmoothedCursor.x
    const dy = raw.y - drawSmoothedCursor.y
    const dist = Math.hypot(dx, dy)
    const maxStep = drawPenSpeed.value
    if (dist > maxStep && dist > 0) {
      // 시선이 멀리 있으면 펜 속도(maxStep)만큼만 따라가 '느린/빠른 펜'이 체감되게 한다.
      drawSmoothedCursor = {
        x: drawSmoothedCursor.x + (dx / dist) * maxStep,
        y: drawSmoothedCursor.y + (dy / dist) * maxStep,
      }
    } else {
      // 시선에 가까우면 가볍게 평활해 미세 떨림을 잡는다.
      drawSmoothedCursor = {
        x: drawSmoothedCursor.x + dx * 0.5,
        y: drawSmoothedCursor.y + dy * 0.5,
      }
    }
  }
  const point = drawSmoothedCursor
  drawCursor.value = point

  if (
    isDrawingActive.value &&
    drawGameState.value.phase === 'running' &&
    !drawScoreOpen.value
  ) {
    drawActiveStroke = addPointToStroke(drawStrokes, drawActiveStroke, point, {
      color: selectedColor.value,
      width: drawBrushWidth,
      allowBridge: drawShouldBridge,
    })
    drawShouldBridge = false
  }
}

function renderDrawCanvas() {
  const canvas = drawCanvasRef.value
  const ctx = canvas?.getContext('2d')
  if (!canvas || !ctx) return

  ctx.clearRect(0, 0, DRAW_CANVAS_WIDTH, DRAW_CANVAS_HEIGHT)
  ctx.fillStyle = '#f8fbff'
  ctx.fillRect(0, 0, DRAW_CANVAS_WIDTH, DRAW_CANVAS_HEIGHT)

  for (const stroke of drawStrokes) {
    if (stroke.points.length < 2) continue
    ctx.strokeStyle = stroke.color
    ctx.lineWidth = stroke.width
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
    ctx.beginPath()
    stroke.points.forEach((point, index) => {
      const x = point.x * DRAW_CANVAS_WIDTH
      const y = point.y * DRAW_CANVAS_HEIGHT
      if (index === 0) ctx.moveTo(x, y)
      else ctx.lineTo(x, y)
    })
    ctx.stroke()
  }
}

function undoDrawStroke() {
  drawStrokes.pop()
  drawActiveStroke = null
}

function clearDrawCanvas() {
  drawStrokes = []
  drawActiveStroke = null
}

function createDrawSubmissionImage(): string {
  return drawCanvasRef.value?.toDataURL('image/png') ?? ''
}

async function submitDrawRound(source: string) {
  if (drawGameState.value.phase !== 'running') return
  // 제출은 스페이스 일시정지 상태(isDrawingActive)를 건드리지 않는다. 채점 중 그리기 입력은
  // phase가 'running'이 아니게 되어(beginJudging) 자연히 막힌다.
  beginJudging(drawGameState.value)

  try {
    const recognition = await recognizeDrawing({
      imageDataUrl: createDrawSubmissionImage(),
      prompt: drawGameState.value.prompt,
      candidates: [...DRAWING_ALL_WORDS],
    })
    // 기획 확정본엔 정답을 직접 입력하는 UI가 없어서(원본 프로토타입에만 있던 기능) 빈 문자열을
    // 넘긴다 — AI 판정만으로 채점한다. draw-core.ts 쪽 로직 자체는 그대로 남겨 둬서, 나중에
    // 정답 입력 UI를 추가하면 바로 활용할 수 있다.
    applyDrawRoundResult(drawGameState.value, recognition, '')
    drawScoreOpen.value = true
  } catch (error) {
    const message =
      error instanceof Error
        ? error.message
        : 'AI 채점 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.'
    reportDrawJudgingError(drawGameState.value, message)
    showToast(message)
  }
  void source
}

function advanceDrawRound() {
  drawScoreOpen.value = false

  if (isDrawGameFinished(drawGameState.value)) {
    toResult()
    return
  }

  clearDrawCanvas()
  selectedColor.value = '#161c2d'
  isDrawingActive.value = true
  startDrawRound(drawGameState.value, drawWords.value)
}

function stopDrawGame() {
  if (drawRafHandle !== undefined) {
    globalThis.cancelAnimationFrame(drawRafHandle)
    drawRafHandle = undefined
  }
  unsubscribeDrawKeydown?.()
  unsubscribeDrawKeydown = undefined
  drawTracking.stop()
}

/** 그림그리기 실제 결과를 결과 화면용으로 기록한다. */
function recordDrawResult() {
  const successCount = drawGameState.value.history.filter(
    (round) => round.success,
  ).length

  lastGameResultStore.set({
    gameId: 'draw',
    mode: mode.value,
    outcome: 'COMPLETED',
    scoreLabel: '총점',
    score: `${drawGameState.value.score}점`,
    headline: 'AI 채점 완료!',
    summary: `${DRAWING_TOTAL_ROUNDS}라운드 중 ${successCount}개를 맞혔어요.`,
    stats: [
      {
        label: '맞힌 라운드',
        value: `${successCount}/${DRAWING_TOTAL_ROUNDS}`,
      },
      { label: '총점', value: `${drawGameState.value.score}점` },
    ],
    drawRounds: [...drawGameState.value.history],
  })
}

function leaveGame() {
  exiting = true
  clearGameInProgress()
  if (game.value)
    router.push({ name: 'game-detail', params: { gameId: game.value.id } })
}

// --- 이탈 처리: 게임 도중 카메라 종료·새로고침 시 진행 중이던 게임을 정리한다 ---
// 정책: 멀티(친구/랜덤)는 몰수패 — 세션 소켓을 닫아 상대에게 이탈을 알린다. 솔로/AI는 그냥 종료.
// `exiting`은 정상 종료(toResult/leaveGame) 중에 카메라가 꺼지며 중복 처리되는 것을 막는다.
let exiting = false
// 상대가 GAME_OVER(정상 종료)를 보냈는지. 이후 오는 소켓 종료(PARTICIPANT_LEFT)는 정상 종료의
// 일부이지 중도 이탈이 아니므로 몰수 처리하지 않기 위한 구분값.
let opponentFinished = false

// 진행 중 표시. 새로고침(브라우저 경고가 억제되는 경우 포함)으로 게임이 끊겼는지 판별한다.
// 마운트 시 표시를 남기고, 정상 종료 때만 지운다. 새로고침하면 표시가 남아 있어 재마운트에서 감지된다.
const GAME_IN_PROGRESS_KEY = 'edc-game-in-progress'

function clearGameInProgress() {
  globalThis.sessionStorage?.removeItem(GAME_IN_PROGRESS_KEY)
}

/**
 * 진행 중이던 게임을 새로고침했는지 판별한다. 그렇다면 정책대로 재시작하지 않고 종료하고 true를,
 * 처음 진입이면 진행 표시를 남기고 false를 반환한다.
 */
function handleMidGameRefresh(): boolean {
  const store = globalThis.sessionStorage
  if (!store?.getItem(GAME_IN_PROGRESS_KEY)) {
    store?.setItem(GAME_IN_PROGRESS_KEY, game.value?.id ?? 'y')
    return false
  }
  clearGameInProgress()
  exiting = true
  showToast('새로고침으로 게임이 종료되었어요.')
  if (game.value) {
    void router.replace({
      name: 'game-detail',
      params: { gameId: game.value.id },
    })
  }
  return true
}

const isMultiplayerMode = computed(() =>
  ['friends', 'random'].includes(mode.value),
)

/** 현재 게임의 카메라 활성 상태. 게임별 tracking.isActive를 하나로 모은다. */
const activeCameraActive = computed(() => {
  switch (game.value?.id) {
    case 'draw':
      return drawCameraActive.value
    case 'blink':
      return blinkCameraActive.value
    case 'hold':
      return stareCameraActive.value
    case 'rhythm':
      return rhythmCameraActive.value
    case 'air':
      return airCameraActive.value
    default:
      return false
  }
})

/** 현재 게임의 대결 세션 소켓을 닫아(있으면) 상대에게 이탈을 통지한다. */
function closeActiveSession() {
  switch (game.value?.id) {
    case 'blink':
      blinkGameSession.close()
      break
    case 'hold':
      stareGameSession.close()
      break
    case 'rhythm':
      rhythmGameSession.close()
      break
    case 'air':
      airGameSession.close()
      break
    default:
      break
  }
}

/** 현재 게임의 대결 세션으로 GAME_OVER를 보낸다(정상 종료 알림). 세션 없으면 무해한 no-op. */
function sendActiveGameOver() {
  switch (game.value?.id) {
    case 'blink':
      blinkGameSession.sendPlayerEvent('GAME_OVER')
      break
    case 'hold':
      stareGameSession.sendPlayerEvent('GAME_OVER')
      break
    case 'rhythm':
      rhythmGameSession.sendPlayerEvent('GAME_OVER')
      break
    case 'air':
      airGameSession.sendPlayerEvent('GAME_OVER')
      break
    default:
      break
  }
}

/**
 * 상대 소켓이 닫혔을 때. 상대가 GAME_OVER를 먼저 보냈으면 정상 종료의 일부이므로 무시하고
 * 내 게임은 내 흐름대로 끝난다. GAME_OVER 없이 끊겼으면 중도 이탈이므로 몰수승으로 종료한다.
 */
function handleOpponentLeft() {
  if (exiting || opponentFinished) return
  exiting = true
  clearGameInProgress()
  // game-result로 바로 이동하는 경로라 toResult()를 거치지 않으므로 여기서 별도로 멈춰야 한다.
  stopGameBgm()
  showToast('상대방이 나가 승리했어요.')
  recordForfeitWin()
  if (game.value) {
    void router.push({
      name: 'game-result',
      params: { gameId: game.value.id },
      query: { ...route.query, result: 'opponent-left' },
    })
  }
}

/** 상대 이탈 몰수승 결과를 결과 화면용으로 기록한다. */
function recordForfeitWin() {
  if (!game.value) return
  lastGameResultStore.set({
    gameId: game.value.id,
    mode: mode.value,
    outcome: 'WIN',
    headline: '상대방이 나갔어요',
    summary: '상대방이 게임을 나가 승리했어요.',
    scoreLabel: '결과',
    score: '상대 이탈',
    stats: [],
  })
}

function handleCameraLost() {
  if (exiting) return
  exiting = true
  if (isMultiplayerMode.value) {
    // 멀티: 세션을 닫아 상대에게 이탈(=상대 승)을 알리고, 나는 몰수패로 종료한다.
    closeActiveSession()
    showToast('카메라가 꺼져 몰수패로 게임을 종료합니다.')
  } else {
    showToast('카메라가 꺼져 게임을 종료합니다.')
  }
  leaveGame()
}

// 게임 도중 카메라가 꺼지면(켜졌다가 꺼짐) 정책대로 처리한다. 정상 종료 중이면 무시.
watch(activeCameraActive, (isOn, wasOn) => {
  if (wasOn && !isOn && !exiting) handleCameraLost()
})

// 프레임 정지 감시: 트랙 'ended' 이벤트가 나지 않는 경우(브라우저 사이트설정에서 카메라 차단,
// 다른 앱이 카메라 점유 등)에도 영상 프레임이 멈추면 카메라가 꺼진 것으로 보고 종료한다.
// currentTime이 몇 초간 그대로면 프레임이 들어오지 않는 것이다. 탭이 백그라운드면(렌더 정지)
// 오탐이므로 제외한다.
const CAMERA_STALL_MS = 3000
let lastVideoTime = -1
let videoStalledSince = 0
let cameraWatchdog: ReturnType<typeof globalThis.setInterval> | undefined

function activeVideoElement(): globalThis.HTMLVideoElement | null {
  switch (game.value?.id) {
    case 'draw':
      return drawVideoRef.value
    case 'blink':
      return blinkVideoRef.value
    case 'hold':
      return stareVideoRef.value
    case 'rhythm':
      return rhythmVideoRef.value
    case 'air':
      return airVideoRef.value
    default:
      return null
  }
}

function pollCameraFrames() {
  const video = activeVideoElement()
  if (
    exiting ||
    !activeCameraActive.value ||
    !video ||
    globalThis.document.hidden
  ) {
    videoStalledSince = 0
    lastVideoTime = video ? video.currentTime : -1
    return
  }
  const now = globalThis.performance.now()
  if (video.currentTime !== lastVideoTime) {
    lastVideoTime = video.currentTime
    videoStalledSince = 0
    return
  }
  if (videoStalledSince === 0) {
    videoStalledSince = now
  } else if (now - videoStalledSince >= CAMERA_STALL_MS) {
    handleCameraLost()
  }
}

// 게임 진행 중 새로고침·창 닫기는 게임이 종료된다고 먼저 경고한다(실수 방지).
// 크롬 등은 returnValue가 비어 있으면 경고를 띄우지 않으므로 비어 있지 않은 문자열을 넣는다
// (문구 자체는 브라우저가 자체 문구로 대체한다).
function handleBeforeUnload(event: globalThis.BeforeUnloadEvent) {
  if (exiting) return
  event.preventDefault()
  event.returnValue =
    '게임이 진행 중이에요. 페이지를 벗어나면 게임이 종료됩니다.'
}
</script>

<template>
  <GameStartCountdownModal
    :open="isReplayCountdownOpen"
    :countdown="replayCountdown"
    countdown-label="게임 다시 시작 카운트다운"
    :dismissible="false"
  />

  <GameStartCountdownModal
    :open="isRhythmStartCountdownOpen"
    :countdown="rhythmStartCountdown"
    countdown-label="리듬 게임 시작 카운트다운"
    :dismissible="false"
  />

  <GamePlayShell
    v-if="game && session"
    :title="game.id === 'draw' ? '눈으로 그리기' : displayTitle"
    :mode-label="gameModeLabels[mode]"
    :time-label="game.id === 'hold' ? stareElapsedLabel : session.timeLabel"
    :time-caption="game.id === 'hold' ? '현재 생존 시간' : undefined"
    :round-progress="
      game.id === 'draw'
        ? { current: drawGameState.round, total: DRAWING_TOTAL_ROUNDS }
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
      ref="gameplayLayoutRef"
      class="gameplay-layout"
      :class="[
        `gameplay-layout--${game.id}`,
        { 'gameplay-layout--hold-solo': game.id === 'hold' && mode === 'solo' },
        { 'gameplay-layout--air-ai': game.id === 'air' && mode === 'ai' },
        {
          'gameplay-layout--blink-solo': game.id === 'blink' && mode === 'solo',
        },
        { 'gameplay-layout--rhythm-duel': isRhythmDuel },
      ]"
    >
      <aside v-if="game.id === 'draw'" class="info-panel draw-info">
        <p class="eyebrow">제시어 · {{ drawDifficultyLabel }}</p>
        <strong>{{ drawGameState.prompt }}</strong>
        <DrawPromptIcon :prompt="drawGameState.prompt" />
        <ol aria-label="라운드 진행 상황">
          <li
            v-for="round in DRAWING_TOTAL_ROUNDS"
            :key="round"
            :class="{
              active: drawGameState.round === round,
              complete: drawGameState.round > round,
            }"
          >
            Round {{ round }}
            <b>{{
              drawGameState.round > round
                ? '완료'
                : drawGameState.round === round
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
        <header class="eye-see-camera-header">
          <p>나의 웹캠</p>
          <span
            :class="{
              'eye-see-camera-header__status--ready': stareCameraActive,
            }"
          >
            {{ stareCameraActive ? '연결됨' : '준비 중' }}
          </span>
        </header>
        <div class="eye-see-camera">
          <video
            ref="stareVideoRef"
            class="eye-see-camera__self"
            aria-label="내 웹캠 영상"
            autoplay
            muted
            playsinline
          ></video>
          <img
            v-if="!stareCameraActive"
            :src="game.mascotImage"
            alt="내 카메라 준비 마스코트"
            draggable="false"
          />
          <div class="eye-see-camera__timer" aria-live="polite">
            <span>현재 생존 시간</span>
            <strong>{{ stareElapsedLabel }}</strong>
          </div>
        </div>
        <p class="eye-see-camera-guide">눈을 오래 뜨고 시선을 유지해 보세요.</p>
      </aside>

      <aside v-else-if="game.id === 'air'" class="air-score-panel">
        <p class="air-score-label">SCORE</p>
        <div class="air-score-matchup">
          <div class="air-score-line opponent">
            <span>{{ mode === 'ai' ? 'AI' : '상대' }}</span
            ><strong>{{ airOpponentDisplayScore }}</strong>
          </div>
          <span class="air-score-vs">VS</span>
          <div class="air-score-line">
            <span>나</span><strong>{{ airMyScore }}</strong>
          </div>
        </div>
        <div class="air-time">
          <span>남은 시간</span>
          <strong>{{ airTimeLabel }}</strong>
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
            ref="rhythmVideoRef"
            class="self-camera"
            aria-label="내 웹캠 영상"
            autoplay
            muted
            playsinline
          ></video>
          <img
            v-if="!rhythmCameraActive"
            :src="game.mascotImage"
            alt="내 웹캠 대기 마스코트"
            draggable="false"
          />
          <span>내 웹캠</span>
        </div>
        <p class="rhythm-duel-player__camera-note">
          {{
            rhythmCameraActive
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
            ref="blinkVideoRef"
            class="self-camera"
            aria-label="내 웹캠 영상"
            autoplay
            muted
            playsinline
          ></video>
          <p v-if="!blinkCameraActive">내 카메라를 준비하고 있어요.</p>
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
            <span class="status-dot">{{
              isDrawingActive ? '그리기 중' : '일시정지'
            }}</span
            ><b>{{ drawTimeLabel }} 남음</b>
          </div>
          <div class="draw-canvas-wrap">
            <canvas
              ref="drawCanvasRef"
              class="draw-canvas-real"
              :width="DRAW_CANVAS_WIDTH"
              :height="DRAW_CANVAS_HEIGHT"
              aria-label="눈으로 그리는 캔버스"
            ></canvas>
            <i
              v-if="drawCursor"
              class="draw-gaze-cursor"
              :style="{
                left: `${drawCursor.x * 100}%`,
                top: `${drawCursor.y * 100}%`,
                background: selectedColor,
              }"
            />
            <p v-if="!isDrawingActive" class="draw-paused-badge">
              Space 키를 눌러 다시 시작하세요
            </p>
            <p v-if="drawGameState.errorMessage" class="draw-error-badge">
              {{ drawGameState.errorMessage }}
            </p>
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
            <label class="draw-pen-speed">
              <span>펜 속도</span>
              <input
                v-model.number="drawPenSpeed"
                type="range"
                min="0.01"
                max="0.08"
                step="0.005"
                aria-label="펜 반응 속도"
              />
            </label>
            <button type="button" @click="undoDrawStroke">되돌리기</button
            ><button type="button" @click="clearDrawCanvas">전체 지우기</button>
            <button
              type="button"
              class="primary"
              :disabled="drawGameState.phase === 'judging'"
              @click="submitDrawRound('수동 제출')"
            >
              {{
                drawGameState.phase === 'judging' ? 'AI 채점 중…' : '제출하기'
              }}
            </button>
          </div>
        </template>

        <template v-else-if="game.id === 'rhythm'">
          <div class="rhythm-top">
            <span>시간 {{ rhythmTimeLabel }} / 00:30</span
            ><progress :value="rhythmProgressPercent" max="100" />
          </div>
          <div
            ref="rhythmStageRef"
            class="rhythm-stage"
            :class="{
              'rhythm-stage--feedback': rhythmFeedback !== null,
              'rhythm-stage--miss': rhythmFeedback?.judgement === 'MISS',
            }"
          >
            <div
              v-if="rhythmIsAnalyzingAudio"
              class="rhythm-analyzing"
              aria-live="polite"
            >
              <span class="rhythm-analyzing__spinner" aria-hidden="true" />
              <p>곡을 분석하고 있어요…</p>
            </div>
            <Transition name="rhythm-judgement-pop">
              <div
                v-if="rhythmFeedback"
                :key="rhythmFeedback.id"
                class="rhythm-stage-feedback"
                :class="`rhythm-stage-feedback--${rhythmFeedback.judgement.toLowerCase()}`"
                aria-live="polite"
              >
                <strong>{{ rhythmFeedback.judgement }}</strong>
              </div>
            </Transition>
            <Transition name="rhythm-combo-pop">
              <div
                v-if="rhythmMine.combo >= 2"
                :key="rhythmMine.combo"
                class="rhythm-combo-display"
              >
                <strong>x{{ rhythmMine.combo }}</strong>
                <span>COMBO</span>
              </div>
            </Transition>
            <div
              class="rhythm-lane rhythm-lane--left"
              :class="{
                'rhythm-lane--feedback':
                  rhythmFeedback?.lanes.includes('LEFT_EYE'),
                'rhythm-lane--miss':
                  rhythmFeedback?.judgement === 'MISS' &&
                  rhythmFeedback?.lanes.includes('LEFT_EYE'),
              }"
            >
              <b>왼쪽 눈 감기</b>
              <span class="hit-zone" />
              <span
                v-if="rhythmFeedback?.lanes.includes('LEFT_EYE')"
                :key="`rhythm-left-feedback-${rhythmFeedback.id}`"
                class="rhythm-feedback-pulse"
                :class="{
                  'rhythm-feedback-pulse--miss':
                    rhythmFeedback.judgement === 'MISS',
                }"
                aria-hidden="true"
              />
              <i
                v-for="note in rhythmLeftNotes"
                :key="note.id"
                :class="{
                  'rhythm-note--near': noteLeftPercent(note) <= 30,
                }"
                :style="{ left: `${noteLeftPercent(note)}%` }"
                >●</i
              >
            </div>
            <div
              class="rhythm-lane rhythm-lane--right"
              :class="{
                'rhythm-lane--feedback':
                  rhythmFeedback?.lanes.includes('RIGHT_EYE'),
                'rhythm-lane--miss':
                  rhythmFeedback?.judgement === 'MISS' &&
                  rhythmFeedback?.lanes.includes('RIGHT_EYE'),
              }"
            >
              <b>오른쪽 눈 감기</b>
              <span class="hit-zone" />
              <span
                v-if="rhythmFeedback?.lanes.includes('RIGHT_EYE')"
                :key="`rhythm-right-feedback-${rhythmFeedback.id}`"
                class="rhythm-feedback-pulse"
                :class="{
                  'rhythm-feedback-pulse--miss':
                    rhythmFeedback.judgement === 'MISS',
                }"
                aria-hidden="true"
              />
              <i
                v-for="note in rhythmRightNotes"
                :key="note.id"
                :class="{
                  'rhythm-note--near': noteLeftPercent(note) <= 30,
                }"
                :style="{ left: `${noteLeftPercent(note)}%` }"
                >●</i
              >
            </div>
          </div>
          <div class="rhythm-controls" aria-live="polite">
            <span
              class="rhythm-judgement"
              :class="`rhythm-judgement--${rhythmGameState.lastJudgement.toLowerCase()}`"
            >
              {{
                rhythmGameState.lastJudgement === 'NONE'
                  ? '준비하세요'
                  : rhythmGameState.lastJudgement
              }}
            </span>
            <span class="rhythm-accuracy-badge"
              >정확도 {{ rhythmAccuracyPercent }}%</span
            ><span v-if="rhythmHasMusic" class="rhythm-music-badge"
              >🎵 실제 음악</span
            >
          </div>
        </template>

        <template v-else-if="game.id === 'blink' && isCompetitive">
          <section class="blink-duel-stage" aria-live="polite">
            <article
              class="blink-duel-event"
              :class="{
                'blink-duel-event--pending': opponentBlinkCount === null,
              }"
            >
              <span>상대방 실시간 현황</span>
              <strong v-if="opponentBlinkCount !== null"
                >{{ opponentBlinkCount }}회</strong
              >
              <strong v-else>연결 중…</strong>
              <p>
                랜덤 이벤트 보너스는 아직 준비 중이에요. 서로의 깜빡임 횟수는
                실시간으로 반영됩니다.
              </p>
            </article>
            <section class="blink-duel-timer" aria-label="남은 시간">
              <span>남은 시간</span>
              <strong>{{ blinkTimeLabel }}</strong>
              <div><i :style="{ width: `${blinkProgressPercent}%` }"></i></div>
              <small>20초</small>
            </section>
            <p class="blink-duel-rule">
              제한 시간 안에 더 많이 깜빡인 사람이 승리해요!
            </p>
            <section class="blink-duel-history" aria-label="이벤트 발동 내역">
              <b>이벤트 발동 내역</b>
              <p><span>대기</span> 실시간 동기화 연동 후 표시될 예정이에요.</p>
            </section>
          </section>
        </template>

        <template v-else-if="game.id === 'blink'">
          <section class="blink-stage" aria-label="눈 깜빡이기 플레이 영역">
            <video
              ref="blinkVideoRef"
              class="blink-stage__camera self-camera"
              aria-label="내 웹캠 영상"
              autoplay
              muted
              playsinline
            ></video>
            <p
              v-if="!blinkCameraActive"
              class="blink-stage__camera-placeholder"
            >
              내 카메라를 준비하고 있어요.
            </p>

            <section
              class="blink-stage__stat-card blink-stage__time-card"
              aria-label="남은 시간"
            >
              <span>남은 시간</span>
              <strong>{{ blinkTimeLabel }}</strong>
              <div class="blink-stage__progress" aria-label="제한 시간 진행률">
                <i :style="{ width: `${blinkProgressPercent}%` }"></i>
              </div>
              <small>총 20초</small>
            </section>

            <section
              class="blink-stage__stat-card blink-stage__count-card"
              aria-label="현재 깜빡임 횟수와 게임 팁"
            >
              <span>현재 깜빡임 횟수</span>
              <b>{{ blinkCount }}<em>회</em></b>
              <section class="blink-stage__tip" aria-label="게임 팁">
                <b>TIP</b>
                <p>눈을 자연스럽게 깜빡여요!</p>
              </section>
            </section>

            <footer class="blink-stage__footer">
              <p>20초가 끝나면 자동으로 기록이 저장돼요!</p>
            </footer>
          </section>
        </template>

        <template v-else-if="game.id === 'hold'">
          <div class="eye-see-status">
            <span>{{ stareStatusLabel }}</span>
            <p>{{ stareGameState.message }}</p>
          </div>
          <div class="hold-controls" aria-live="polite">
            <span
              class="hold-status-badge"
              :class="`hold-status-badge--${stareWarningTone}`"
            >
              {{ stareGameState.warning }}
            </span>
            <span v-if="stareTargetMs !== null" class="hold-target-hint">
              목표 시간 {{ formatStareDuration(stareTargetMs) }}
            </span>
          </div>
        </template>

        <template v-else>
          <div class="hockey-status">
            <b>{{ airMyScore }}</b
            ><span>VS</span><b>{{ airOpponentDisplayScore }}</b
            ><small>남은 시간 {{ airTimeLabel }}</small>
          </div>
          <canvas
            ref="airCanvasRef"
            class="hockey-canvas"
            width="720"
            height="900"
            aria-label="에어하키 게임 화면"
          ></canvas>
          <p class="tip">
            시선으로 패들을 움직이고, 눈을 깜빡이면 퍽을 발사·타격해요.
          </p>
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
      <aside v-else-if="game.id === 'draw'" class="info-panel webcam-panel">
        <p class="eyebrow">나의 웹캠</p>
        <div class="video-placeholder">
          <video
            ref="drawVideoRef"
            class="self-camera"
            aria-label="내 웹캠 영상"
            autoplay
            muted
            playsinline
          ></video
          ><img
            v-if="!drawCameraActive"
            :src="game.mascotImage"
            alt="웹캠 대기 마스코트"
            draggable="false"
          />
        </div>
        <p class="camera-state">
          {{ drawCameraActive ? '카메라 연결됨' : '카메라 준비 중' }}
        </p>
        <p class="webcam-panel__hint">
          얼굴과 눈이 화면 안에 함께 보이도록<br />카메라 위치를 조정해 주세요.
        </p>
      </aside>
      <aside v-else-if="game.id === 'rhythm'" class="info-panel webcam-panel">
        <p class="eyebrow">나의 웹캠</p>
        <div class="video-placeholder">
          <video
            ref="rhythmVideoRef"
            class="self-camera"
            aria-label="내 웹캠 영상"
            autoplay
            muted
            playsinline
          ></video
          ><img
            v-if="!rhythmCameraActive"
            :src="game.mascotImage"
            alt="웹캠 대기 마스코트"
            draggable="false"
          />
        </div>
        <p class="camera-state">
          {{ rhythmCameraActive ? '카메라 연결됨' : '카메라 준비 중' }}
        </p>
        <p class="webcam-panel__rhythm-tip">
          분홍 노트는 왼쪽,<br />파랑 노트는 오른쪽<br />눈 입력입니다.
        </p>
      </aside>
      <aside v-else-if="game.id === 'air'" class="air-players-panel">
        <article
          v-if="mode === 'ai'"
          class="air-player-card air-player-card--ai"
        >
          <div><strong>AI</strong><span>OPPONENT</span></div>
          <section class="air-player-card__ai-profile">
            <img
              :src="airAiRobotImage"
              alt="에어하키 AI 로봇 상대"
              draggable="false"
            />
            <b>AI HOCKEY BOT</b>
            <small>AI가 다음 움직임을 계산 중이에요.</small>
          </section>
        </article>
        <article v-else class="air-player-card">
          <div><strong>상대 웹캠</strong><span>OPPONENT</span></div>
          <div class="air-player-card__camera">
            <span class="air-player-card__camera-label">EYE CAMERA</span>
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
          <p
            class="air-player-card__camera-status"
            :class="{ 'air-player-card__camera-status--ready': hasPeerCamera }"
          >
            {{ hasPeerCamera ? '카메라 연결됨' : '카메라 연결 대기' }}
          </p>
        </article>
        <article class="air-player-card you">
          <div><strong>나의 웹캠</strong><span>YOU</span></div>
          <div class="air-player-card__camera">
            <span class="air-player-card__camera-label">EYE CAMERA</span>
            <video
              ref="airVideoRef"
              class="self-camera"
              aria-label="내 웹캠 영상"
              autoplay
              muted
              playsinline
            ></video>
            <img
              v-if="!airCameraActive"
              :src="game.mascotImage"
              alt="내 플레이어 마스코트"
              draggable="false"
            />
          </div>
          <p
            class="air-player-card__camera-status"
            :class="{
              'air-player-card__camera-status--ready': airCameraActive,
            }"
          >
            {{ airCameraActive ? '카메라 연결됨' : '카메라 연결 대기' }}
          </p>
        </article>
      </aside>
      <aside
        v-else-if="game.id === 'blink' && isCompetitive"
        class="blink-duel-player blink-duel-player--opponent"
      >
        <div class="blink-duel-player__score">
          <span>{{ mode === 'friends' ? '친구' : '매칭된 상대' }}</span>
          <strong v-if="opponentBlinkCount !== null"
            >{{ opponentBlinkCount }}<em>회</em></strong
          >
          <strong v-else class="blink-duel-player__score--pending"
            >연결 중…</strong
          >
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
        <template v-if="game.id === 'hold' && mode === 'ai'">
          <p class="eyebrow">AI</p>
          <div class="eye-see-camera eye-see-camera--ai">
            <img
              :src="airAiRobotImage"
              alt="눈싸움 AI 로봇 상대"
              draggable="false"
            />
          </div>
          <p class="camera-state">AI가 눈싸움에 집중하고 있어요.</p>
        </template>
        <template v-else-if="game.id === 'hold'">
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
          <p v-if="isStareDuel" class="hold-opponent-status">
            상대 생존 시간
            <strong v-if="stareOpponentSynced">{{
              formatStareDuration(stareOpponentElapsedMs)
            }}</strong>
            <strong v-else>연결 중…</strong>
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
    <!-- 라운드 진행은 버튼으로만 — 백드롭 클릭이 다음 라운드/최종 결과로 넘겨버리지 않게 한다. -->
    <Teleport to="body"
      ><Transition name="dialog-pop"
        ><div v-if="drawScoreOpen" class="score-backdrop">
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="draw-score-title"
          >
            <header class="draw-score-heading">
              <div>
                <p class="eyebrow">AI 채점 결과</p>
                <h2 id="draw-score-title">이번 라운드 그림을 분석했어요!</h2>
              </div>
            </header>

            <div v-if="currentDrawResult" class="draw-score-columns">
              <section class="draw-submission-card" aria-label="제출한 그림">
                <p>
                  제시어 ·
                  {{ DRAWING_DIFFICULTY_LABEL[currentDrawResult.difficulty] }}
                </p>
                <strong>{{ currentDrawResult.prompt }}</strong>
                <DrawPromptIcon
                  :prompt="currentDrawResult.prompt"
                  size="medium"
                />
                <p
                  class="draw-correct"
                  :class="{ 'draw-correct--fail': !currentDrawResult.success }"
                >
                  {{
                    currentDrawResult.success
                      ? `정답입니다! ${currentDrawResult.prompt}을 맞혔어요!`
                      : `AI는 "${currentDrawResult.aiGuess}"로 인식했어요. 다음엔 더 또렷하게 그려보세요!`
                  }}
                </p>
              </section>

              <section class="draw-score-detail" aria-label="라운드 점수 상세">
                <span class="draw-score-ribbon"
                  >ROUND {{ currentDrawResult.round }} 점수</span
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
                        >기본 점수<small>{{
                          currentDrawResult.success
                            ? '정답을 맞혀 기본 점수를 획득했어요!'
                            : '이번 라운드는 기본 점수를 못 받았어요.'
                        }}</small></span
                      >
                    </dt>
                    <dd>{{ currentDrawResult.baseScore }}점</dd>
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
                          >AI가 해당 그림을 얼마나 확신했는지에 따른
                          보너스예요.</small
                        ></span
                      >
                    </dt>
                    <dd>+{{ currentDrawResult.confidenceBonus }}점</dd>
                  </div>
                </dl>
                <div class="draw-round-sum">
                  <span>ROUND {{ currentDrawResult.round }} 총점</span>
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
                  AI Confidence:
                  <b>{{ Math.round(currentDrawResult.confidence * 100) }}%</b>
                  ({{
                    currentDrawResult.confidence >= 0.7
                      ? '높은 확신'
                      : currentDrawResult.confidence >= 0.4
                        ? '보통 확신'
                        : '낮은 확신'
                  }})
                </p>
              </section>
            </div>

            <section class="draw-cumulative" aria-label="누적 점수 현황">
              <h3>전체 점수 현황</h3>
              <div class="draw-score-equation">
                <template
                  v-for="round in drawGameState.history"
                  :key="round.round"
                >
                  <article
                    :class="{ current: round.round === drawGameState.round }"
                  >
                    <span>ROUND {{ round.round }}</span>
                    <strong>{{ round.score }}점</strong>
                  </article>
                  <b
                    v-if="round.round < drawGameState.history.length"
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
                isDrawGameFinished(drawGameState)
                  ? '최종 결과 보기'
                  : '다음 라운드로 이동'
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
.draw-info > .draw-prompt-icon {
  align-self: center;
  margin-block: auto;
}
.draw-info ol {
  margin: 0;
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
.rhythm-top {
  gap: 14px;
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}
.rhythm-top > span {
  flex: 0 0 auto;
  min-width: 112px;
}
.rhythm-top progress {
  width: min(260px, 48%);
  height: 8px;
  overflow: hidden;
  border: 0;
  border-radius: 999px;
  background: #e8eaf4;
  accent-color: #5ce2b7;
}
.rhythm-top progress::-webkit-progress-bar {
  border-radius: inherit;
  background: #e8eaf4;
}
.rhythm-top progress::-webkit-progress-value {
  border-radius: inherit;
  background: linear-gradient(90deg, #5ce2b7, #7aa8ff);
}
.rhythm-top progress::-moz-progress-bar {
  border-radius: inherit;
  background: linear-gradient(90deg, #5ce2b7, #7aa8ff);
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
.draw-canvas-wrap {
  position: relative;
  overflow: hidden;
  border: 1px solid #e2e4f3;
  border-radius: 16px;
  background: #fff;
}
.draw-canvas-real {
  display: block;
  width: 100%;
  height: auto;
  aspect-ratio: 1000 / 640;
  cursor: none;
}
.draw-pen-speed {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--color-muted, #6b7280);
}
.draw-pen-speed input {
  width: 96px;
}
.draw-gaze-cursor {
  position: absolute;
  width: 18px;
  height: 18px;
  margin: -9px 0 0 -9px;
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 1px rgba(15, 23, 42, 0.25);
  pointer-events: none;
}
.draw-paused-badge,
.draw-error-badge {
  position: absolute;
  bottom: 14px;
  left: 14px;
  padding: 6px 14px;
  border-radius: 999px;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}
.draw-paused-badge {
  background: rgba(15, 23, 42, 0.72);
}
.draw-error-badge {
  right: 14px;
  left: auto;
  background: #e14b5c;
}
.draw-tools,
.rhythm-controls,
.hold-controls {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 9px;
}
.hold-status-badge {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 800;
}
.hold-status-badge--ok {
  color: #278957;
  background: #e6f7eb;
}
.hold-status-badge--warn {
  color: #b75555;
  background: #fff0f0;
}
.hold-target-hint {
  padding: 6px 14px;
  border-radius: 999px;
  color: var(--color-muted);
  background: var(--color-surface-soft);
  font-size: 13px;
  font-weight: 800;
}
.hold-opponent-status {
  margin: 10px 0 0;
  color: var(--color-muted);
  font-size: 13px;
  text-align: center;
}
.hold-opponent-status strong {
  display: block;
  margin-top: 2px;
  color: var(--color-ink);
  font-size: 16px;
  font-weight: 900;
}
.rhythm-judgement {
  padding: 6px 14px;
  border-radius: 999px;
  color: var(--color-ink);
  background: var(--color-surface-soft);
  font-size: 13px;
  font-weight: 800;
}
.rhythm-judgement--perfect {
  color: #278957;
  background: #e6f7eb;
}
.rhythm-judgement--great {
  color: #2864df;
  background: var(--color-blue-soft);
}
.rhythm-judgement--good {
  color: #b8730f;
  background: #fff3e0;
}
.rhythm-judgement--miss {
  color: #b75555;
  background: #fff0f0;
}
.rhythm-accuracy-badge {
  padding: 6px 14px;
  border-radius: 999px;
  color: var(--color-muted);
  background: var(--color-surface-soft);
  font-size: 13px;
  font-weight: 800;
}
.rhythm-music-badge {
  padding: 6px 14px;
  border-radius: 999px;
  color: #278957;
  background: #e6f7eb;
  font-size: 13px;
  font-weight: 800;
}
.rhythm-analyzing {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: grid;
  place-items: center;
  gap: 12px;
  border-radius: 16px;
  background: rgba(21, 27, 77, 0.82);
  color: #fff;
  text-align: center;
}
.rhythm-analyzing p {
  margin: 0;
  font-size: 14px;
  font-weight: 800;
}
.rhythm-analyzing__spinner {
  width: 32px;
  height: 32px;
  border: 4px solid rgba(255, 255, 255, 0.25);
  border-top-color: #fff;
  border-radius: 50%;
  animation: rhythm-spin 0.8s linear infinite;
}
@keyframes rhythm-spin {
  to {
    transform: rotate(360deg);
  }
}
.draw-tools button,
.rhythm-controls button,
.hold-controls button {
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
.primary {
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
  overflow: hidden;
  isolation: isolate;
  border: 0;
  border-radius: 16px;
  color: #fff;
  background:
    radial-gradient(
      circle at 15% 28%,
      rgba(255, 112, 188, 0.12),
      transparent 23%
    ),
    radial-gradient(
      circle at 15% 76%,
      rgba(111, 157, 255, 0.12),
      transparent 23%
    ),
    #151b4d;
}
.rhythm-stage::before {
  position: absolute;
  inset: 0;
  z-index: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.045) 1px, transparent 1px);
  background-size: 54px 54px;
  content: '';
  pointer-events: none;
}
.rhythm-stage::after {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: linear-gradient(
    90deg,
    transparent 0,
    rgba(255, 255, 255, 0.07) 15%,
    transparent 31%,
    transparent 69%,
    rgba(255, 255, 255, 0.05) 85%,
    transparent 100%
  );
  content: '';
  opacity: 0.45;
  pointer-events: none;
  animation: rhythm-stage-beat 2.4s ease-in-out infinite;
}
.rhythm-stage--feedback {
  box-shadow: inset 0 0 48px rgba(103, 255, 209, 0.12);
}
.rhythm-stage--miss {
  box-shadow: inset 0 0 48px rgba(255, 127, 159, 0.16);
}
.rhythm-stage-feedback {
  position: absolute;
  top: 47%;
  left: 50%;
  z-index: 4;
  display: grid;
  padding: 4px 14px 6px;
  gap: 0;
  justify-items: center;
  border-radius: 999px;
  color: #fff;
  background: rgba(12, 16, 54, 0.32);
  pointer-events: none;
  text-align: center;
  text-shadow:
    0 0 8px currentColor,
    0 3px 14px rgba(0, 0, 0, 0.45);
  transform: translate(-50%, -50%);
}
.rhythm-stage-feedback strong {
  font-size: clamp(24px, 3.2vw, 42px);
  font-weight: 1000;
  letter-spacing: 0.06em;
}
.rhythm-stage-feedback--perfect {
  color: #67ffd1;
}
.rhythm-stage-feedback--great {
  color: #8db6ff;
}
.rhythm-stage-feedback--good {
  color: #ffd36b;
}
.rhythm-stage-feedback--miss {
  color: #ff7f9f;
}
.rhythm-combo-display {
  position: absolute;
  top: 20px;
  right: 24px;
  z-index: 4;
  display: grid;
  justify-items: end;
  color: #fff;
  pointer-events: none;
  text-align: right;
  text-shadow: 0 3px 14px rgba(0, 0, 0, 0.45);
}
.rhythm-combo-display strong {
  color: #a7ffdf;
  font-size: clamp(30px, 4vw, 52px);
  font-weight: 1000;
  line-height: 0.95;
}
.rhythm-combo-display span {
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.18em;
}
.rhythm-judgement-pop-enter-active,
.rhythm-judgement-pop-leave-active {
  animation: rhythm-judgement-pop 0.65s var(--ease-out, ease-out) both;
}
.rhythm-judgement-pop-leave-active {
  animation-direction: reverse;
}
@keyframes rhythm-judgement-pop {
  0% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.68);
  }
  18% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1.12);
  }
  72% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }
  100% {
    opacity: 0;
    transform: translate(-50%, -58%) scale(0.96);
  }
}
.rhythm-combo-pop-enter-active,
.rhythm-combo-pop-leave-active {
  animation: rhythm-combo-pop 0.35s var(--ease-out, ease-out) both;
}
.rhythm-combo-pop-leave-active {
  animation-direction: reverse;
}
@keyframes rhythm-combo-pop {
  0% {
    opacity: 0;
    transform: translateY(-8px) scale(0.72);
  }
  70% {
    opacity: 1;
    transform: translateY(0) scale(1.08);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
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
  z-index: 1;
  --rhythm-hit-line-offset: clamp(18px, 2vw, 26px);
  height: 110px;
  border-bottom: 0;
}
.rhythm-lane--left {
  --rhythm-lane-color: #ff78ba;
}
.rhythm-lane--right {
  --rhythm-lane-color: #77a7ff;
}
.rhythm-lane::after {
  position: absolute;
  right: 0;
  bottom: var(--rhythm-hit-line-offset);
  left: 0;
  z-index: 0;
  height: 3px;
  border-radius: 999px;
  background: var(--rhythm-lane-color, #ff78ba);
  box-shadow: 0 0 8px
    color-mix(in srgb, var(--rhythm-lane-color, #ff78ba) 70%, transparent);
  content: '';
  opacity: 0.95;
}
.rhythm-lane--feedback::after {
  background: #a7ffdf;
  box-shadow: 0 0 14px rgba(103, 255, 209, 0.95);
}
.rhythm-lane--miss::after {
  background: #ff7f9f;
  box-shadow: 0 0 14px rgba(255, 127, 159, 0.95);
}
.rhythm-lane b {
  position: absolute;
  top: 0;
  left: 0;
  padding: 7px 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
  background: #4a265d;
  font-size: 13px;
  letter-spacing: 0.02em;
}
.rhythm-lane--right b {
  background: #213f78;
}
.rhythm-lane i {
  position: absolute;
  right: auto;
  bottom: 20px;
  z-index: 3;
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  overflow: visible;
  border: 4px solid #fff;
  border-radius: 50%;
  color: #e84d98;
  background: #ffd3ea;
  box-shadow: 0 0 12px #fd5dab;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease;
  animation: rhythm-note-glow 1.8s ease-in-out infinite;
}
.rhythm-lane i::before {
  position: absolute;
  inset: 7px;
  border: 2px solid currentColor;
  border-radius: 50%;
  content: '';
  opacity: 0.65;
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
.rhythm-lane--right i {
  color: #396ad5;
  background: #c9dcff;
  box-shadow: 0 0 12px #609aff;
}
.rhythm-note--near {
  transform: scale(1.18);
}
.rhythm-lane--left .rhythm-note--near {
  box-shadow:
    0 0 16px #fd5dab,
    0 0 30px rgba(255, 93, 171, 0.65);
}
.rhythm-lane--right .rhythm-note--near {
  box-shadow:
    0 0 16px #609aff,
    0 0 30px rgba(96, 154, 255, 0.65);
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
@keyframes rhythm-stage-beat {
  0%,
  100% {
    opacity: 0.2;
    transform: translateX(-4%);
  }
  50% {
    opacity: 0.6;
    transform: translateX(4%);
  }
}
@keyframes rhythm-target-breathe {
  0%,
  100% {
    opacity: 0.48;
    transform: translate(-50%, 50%) scale(0.94);
  }
  50% {
    opacity: 0.86;
    transform: translate(-50%, 50%) scale(1.04);
  }
}
.hit-zone {
  position: absolute;
  bottom: var(--rhythm-hit-line-offset);
  left: 15%;
  z-index: 2;
  height: 80px;
  border-left: 2px solid currentColor;
  color: rgba(255, 255, 255, 0.75);
}
.hit-zone::before {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 76px;
  height: 76px;
  border: 2px solid currentColor;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    currentColor 0 5px,
    rgba(255, 255, 255, 0.08) 6px 15px,
    transparent 16px
  );
  box-shadow:
    0 0 16px currentColor,
    inset 0 0 12px rgba(255, 255, 255, 0.12);
  content: '';
  opacity: 0.7;
  pointer-events: none;
  transform: translate(-50%, 50%);
  animation: rhythm-target-breathe 1.6s ease-in-out infinite;
}
.hit-zone::after {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 10px;
  height: 10px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 10px currentColor;
  content: '';
  pointer-events: none;
  transform: translate(-50%, 50%);
}
.rhythm-lane--left .hit-zone {
  color: #ff8ec8;
}
.rhythm-lane--right .hit-zone {
  color: #8db6ff;
}
.rhythm-lane--feedback .hit-zone {
  border-left-color: #a7ffdf;
  color: #a7ffdf;
  box-shadow: 0 0 16px rgba(103, 255, 209, 0.95);
}
.rhythm-lane--miss .hit-zone {
  border-left-color: #ff7f9f;
  color: #ff7f9f;
  box-shadow: 0 0 16px rgba(255, 127, 159, 0.95);
}
.rhythm-feedback-pulse {
  position: absolute;
  bottom: var(--rhythm-hit-line-offset);
  left: 15%;
  z-index: 1;
  width: 82px;
  height: 82px;
  border: 3px solid #a7ffdf;
  border-radius: 50%;
  box-shadow:
    0 0 12px rgba(103, 255, 209, 0.95),
    inset 0 0 18px rgba(103, 255, 209, 0.35);
  pointer-events: none;
  transform: translate(-50%, 50%);
  animation: rhythm-hit-ripple 0.65s ease-out both;
}
.rhythm-feedback-pulse::before,
.rhythm-feedback-pulse::after {
  position: absolute;
  inset: 10px;
  border: 2px solid currentColor;
  border-radius: inherit;
  content: '';
}
.rhythm-feedback-pulse::before {
  color: rgba(167, 255, 223, 0.9);
  animation: rhythm-hit-ripple-inner 0.65s ease-out both;
}
.rhythm-feedback-pulse::after {
  inset: -12px;
  color: rgba(167, 255, 223, 0.6);
  animation: rhythm-hit-ripple-outer 0.65s ease-out both;
}
.rhythm-feedback-pulse--miss {
  border-color: #ff7f9f;
  box-shadow:
    0 0 12px rgba(255, 127, 159, 0.95),
    inset 0 0 18px rgba(255, 127, 159, 0.35);
}
.rhythm-feedback-pulse--miss::before {
  color: rgba(255, 127, 159, 0.9);
}
.rhythm-feedback-pulse--miss::after {
  color: rgba(255, 127, 159, 0.6);
}
@keyframes rhythm-hit-ripple {
  0% {
    opacity: 1;
    transform: translate(-50%, 50%) scale(0.55);
  }
  100% {
    opacity: 0;
    transform: translate(-50%, 50%) scale(1.35);
  }
}
@keyframes rhythm-hit-ripple-inner {
  0% {
    opacity: 1;
    transform: scale(0.6);
  }
  100% {
    opacity: 0;
    transform: scale(1.45);
  }
}
@keyframes rhythm-hit-ripple-outer {
  0% {
    opacity: 0.7;
    transform: scale(0.8);
  }
  100% {
    opacity: 0;
    transform: scale(1.3);
  }
}
@media (prefers-reduced-motion: reduce) {
  .rhythm-stage::after,
  .rhythm-stage-feedback,
  .rhythm-combo-display,
  .hit-zone::before,
  .rhythm-feedback-pulse,
  .rhythm-feedback-pulse::before,
  .rhythm-feedback-pulse::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
  }
}
.webcam-panel .camera-state {
  color: #278957;
  font-size: 13px;
  font-weight: 800;
}
.webcam-panel__hint {
  margin: 5px 0 0;
  color: #75809b;
  font-size: 11px;
  line-height: 1.5;
  text-align: center;
}
.webcam-panel .camera-state {
  width: 100%;
  justify-content: center;
  text-align: center;
}
.webcam-panel .camera-state::before {
  display: none;
}
.webcam-panel__rhythm-tip {
  margin: 12px 0 0;
  padding: 10px 12px;
  border-radius: 10px;
  color: #5e6985;
  background: #f5f5ff;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.65;
  text-align: center;
}
.gameplay-layout--draw .webcam-panel .video-placeholder {
  width: 100%;
  min-height: 0;
  aspect-ratio: 4 / 3;
  margin-inline: auto;
  border-radius: 22px;
  background: transparent;
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
.blink-duel-player__score strong.blink-duel-player__score--pending {
  color: var(--color-muted);
  font-size: 17px;
  font-weight: 800;
  line-height: 1.3;
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
  height: clamp(420px, calc(100svh - 220px), 620px);
  min-height: 0;
  overflow: hidden;
  border: 1px solid #e1e4f1;
  border-radius: 20px;
  background: #f7f7ff;
}
.blink-stage__camera {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
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
.blink-stage__time-card,
.blink-stage__count-card {
  position: absolute;
  top: 20px;
  z-index: 2;
  width: min(200px, calc((100% - 72px) / 2));
  text-align: left;
}
.blink-stage__time-card {
  left: 20px;
}
.blink-stage__count-card {
  right: 20px;
}
.blink-stage__stat-card,
.blink-stage__tip {
  padding: 11px 13px;
  border: 1px solid rgba(223, 227, 244, 0.95);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 10px 24px rgba(14, 24, 60, 0.14);
  backdrop-filter: blur(8px);
}
.blink-stage__stat-card {
  display: grid;
  gap: 5px;
}
.blink-stage__stat-card > span {
  color: #59647f;
  font-size: 12px;
  font-weight: 800;
}
.blink-stage__stat-card > strong {
  color: var(--color-accent-blue);
  font-size: clamp(35px, 3.6vw, 48px);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.blink-stage__stat-card small {
  color: var(--color-ink);
  font-size: 11px;
  font-weight: 800;
}
.blink-stage__stat-card b {
  color: var(--color-accent-blue);
  font-size: clamp(30px, 3.2vw, 42px);
  line-height: 1;
}
.blink-stage__stat-card em {
  margin-left: 7px;
  color: var(--color-ink);
  font-size: 17px;
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
  margin-top: 5px;
  padding: 8px 0 0;
  border: 0;
  border-top: 1px solid #e5e8f2;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
}
.blink-stage__tip b {
  color: var(--color-accent-blue);
  font-size: 13px;
}
.blink-stage__tip p {
  margin: 5px 0 0;
  color: #5d6781;
  font-size: 11px;
  line-height: 1.5;
}
.blink-stage__footer {
  position: absolute;
  right: 20px;
  bottom: 20px;
  left: 20px;
  z-index: 2;
  display: flex;
  min-height: 50px;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 8px 16px;
  border: 1px solid rgba(225, 228, 241, 0.94);
  border-radius: 14px;
  background: rgba(251, 251, 255, 0.94);
  box-shadow: 0 8px 22px rgba(14, 24, 60, 0.12);
  backdrop-filter: blur(8px);
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
.eye-see-camera-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.eye-see-camera-header p {
  margin: 0;
  color: var(--color-accent-blue);
  font-family: var(--font-display);
  font-size: 18px;
}
.eye-see-camera-header span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 8px;
  border-radius: 999px;
  color: #75809b;
  background: #f1f3f8;
  font-size: 11px;
  font-weight: 800;
}
.eye-see-camera-header span::before {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  content: '';
}
.eye-see-camera-header .eye-see-camera-header__status--ready {
  color: #278957;
  background: #edf8f0;
}
.eye-see-camera-guide {
  margin: 12px 0 0;
  color: #68728d;
  font-size: 13px;
  line-height: 1.5;
  text-align: center;
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
.gameplay-layout--hold-solo .eye-see-camera {
  min-height: 0;
  height: clamp(400px, 52vh, 460px);
  background: #171a32;
}
.gameplay-layout--hold-solo .eye-see-camera video {
  object-fit: contain;
}
.eye-see-camera__timer {
  position: absolute;
  top: 16px;
  left: 50%;
  z-index: 2;
  display: grid;
  min-width: 156px;
  justify-items: center;
  gap: 2px;
  padding: 10px 18px;
  border: 1px solid rgba(222, 219, 255, 0.96);
  border-radius: 13px;
  color: var(--color-ink);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 7px 18px rgba(23, 26, 50, 0.18);
  transform: translateX(-50%);
  backdrop-filter: blur(8px);
}
.eye-see-camera__timer span {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 800;
}
.eye-see-camera__timer strong {
  color: #5144e8;
  font-size: 32px;
  font-weight: 900;
  line-height: 1;
  font-variant-numeric: tabular-nums;
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
.eye-see-camera--ai {
  background: #fff5f6;
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
.hockey-canvas {
  align-self: center;
  width: min(100%, 380px);
  height: auto;
  aspect-ratio: 720 / 900;
  border-radius: 20px;
  box-shadow: 0 16px 38px rgba(50, 69, 155, 0.16);
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
.gameplay-layout--air-ai {
  grid-template-columns: 190px minmax(0, 1fr) 190px;
  align-items: start;
}
.gameplay-layout--air-ai .gameplay-board,
.gameplay-layout--air-ai .air-score-panel {
  min-height: 460px;
}
.gameplay-layout--air-ai .gameplay-board {
  justify-content: flex-start;
  padding-top: 16px;
}
.gameplay-layout--air-ai .hockey-canvas {
  width: min(100%, 430px);
}
.gameplay-layout--air-ai .air-players-panel {
  min-height: 460px;
  grid-template-rows: 212px minmax(0, 1fr);
  gap: 12px;
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
.air-player-card--ai {
  min-height: 0;
  justify-content: flex-start;
  background: #fff5f6;
}
.air-player-card__ai-profile {
  display: grid;
  min-height: 0;
  height: 148px;
  box-sizing: border-box;
  align-content: center;
  justify-items: center;
  gap: 4px;
  padding: 0 14px 13px;
  text-align: center;
}
.air-player-card__ai-profile img {
  width: 92px;
  height: 84px;
  margin: 0;
  object-fit: contain;
}
.air-player-card__ai-profile b {
  color: #d94a5b;
  font-size: 10px;
  letter-spacing: 0.05em;
}
.air-player-card__ai-profile small {
  color: #7c7790;
  font-size: 10px;
  line-height: 1.35;
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
.air-player-card > .air-player-card__camera {
  position: relative;
  display: grid;
  width: calc(100% - 36px);
  aspect-ratio: 4 / 3;
  min-height: 0;
  flex: 0 0 auto;
  box-sizing: border-box;
  padding: 0;
  place-items: center;
  overflow: hidden;
  border-radius: 14px;
}
.air-player-card__camera video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 35%;
}
.air-player-card__camera img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  object-fit: cover;
  object-position: center;
}
.air-player-card .air-player-card__camera-label {
  position: absolute;
  z-index: 1;
  top: 9px;
  left: 9px;
  padding: 4px 7px;
  border: 0;
  border-radius: 999px;
  color: #17213b;
  background: rgba(255, 255, 255, 0.94);
  font-size: 8px;
  font-weight: 900;
}
.air-player-card__camera-status {
  margin: 9px 0 0;
  color: #8890a9;
  font-size: 10px;
  font-weight: 800;
}
.air-player-card__camera-status--ready {
  color: #45935f;
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
.draw-tools .primary {
  transition:
    transform var(--duration-fast) ease,
    box-shadow var(--duration-fast) ease,
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease,
    border-color var(--duration-fast) ease;
}
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
.draw-correct--fail {
  color: #b75555;
  background: #fff0f0;
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
