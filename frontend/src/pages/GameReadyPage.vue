<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from '../composables/useToast'
import { gameDetails, isGameDetailId } from '../mocks/game-details'
import { useMediaSessionStore } from '../stores/mediaSession'
import { useWaitingRoomSocket } from '../composables/useWaitingRoomSocket'
import { useLiveKitRoom } from '../composables/useLiveKitRoom'
import { useEyeTracking } from '../composables/useEyeTracking'
import { useCalibrationStore } from '../stores/calibration'
import { createInviteRoom, joinInviteRoom } from '../api/waitingRoom'
import { ApiError } from '../api/http'
import { currentAccessToken, resolveIdentity } from '../api/identity'
import { GAME_NAME_BY_ID } from '../types/waitingRoom'
import type {
  WaitingRoomGameStartData,
  WaitingRoomIdentity,
  WaitingRoomParticipant,
} from '../types/waitingRoom'
import type {
  CalibrationEvaluation,
  Point,
} from '../lib/eye-tracking/gaze-calibration'

type CameraPermissionStatus =
  'idle' | 'requesting' | 'granted' | 'denied' | 'unavailable'

type RoomRole = 'host' | 'player'

/**
 * 실제 캘리브레이션 단계.
 * - open/closed: 눈 뜬/감은 기준(EAR) 샘플링 — 모든 게임 공통
 * - gaze: 화면 응시 좌표 보정(9점) — 좌표가 필요한 게임(그림그리기/에어하키)만 진행
 * - done: 보정 완료
 */
type CalibrationStage = 'open' | 'closed' | 'gaze' | 'done'

const route = useRoute()
const router = useRouter()
const { showToast } = useToast()
const permissionStatus = ref<CameraPermissionStatus>('idle')
const isCalibrated = ref(false)
const isReady = ref(false)
const isWebcamGuideOpen = ref(true)
const isCalibrationOpen = ref(false)
const isCameraErrorOpen = ref(false)
const isGameStartDialogOpen = ref(false)
const countdown = ref(3)
const isGamePlaybackPending = ref(false)
const cameraStream = ref<globalThis.MediaStream | null>(null)

// --- 실제 눈/시선 인식 (캘리브레이션) ---
const eyeTracking = useEyeTracking()
const calibrationStore = useCalibrationStore()
const isSamplingEyeStep = ref(false)
const eyeSampleFeedback = ref<'idle' | 'success' | 'insufficient'>('idle')
const gazeCalibrationTargetIndex = ref(0)
const gazeCalibrationEvaluation = ref<CalibrationEvaluation | null>(null)
const calibrationStageIndex = ref(0)

/** 화면 좌표(gaze)가 실제로 필요한 게임만 9점 시선 보정을 추가로 거친다. */
const needsGazeCalibration = computed(() =>
  ['draw', 'air'].includes(game.value?.id ?? ''),
)
const calibrationStages = computed<CalibrationStage[]>(() =>
  needsGazeCalibration.value
    ? ['open', 'closed', 'gaze', 'done']
    : ['open', 'closed', 'done'],
)
const calibrationStage = computed<CalibrationStage>(
  () => calibrationStages.value[calibrationStageIndex.value] ?? 'done',
)
/** 진행률 표시용 — "done" 단계는 세지 않는다. */
const totalCalibrationSteps = computed(() => calibrationStages.value.length - 1)
const calibrationStepNumber = computed(() =>
  Math.min(calibrationStageIndex.value + 1, totalCalibrationSteps.value),
)
const calibrationStageTitle = computed(() => {
  switch (calibrationStage.value) {
    case 'open':
      return '눈 뜬 상태 기록'
    case 'closed':
      return '눈 감은 상태 기록'
    case 'gaze':
      return '시선 좌표 보정'
    default:
      return '캘리브레이션 완료'
  }
})
const calibrationStageDescription = computed(() => {
  switch (calibrationStage.value) {
    case 'open':
      return '카메라를 정면으로 보고 눈을 편하게 뜬 상태를 유지해 주세요.'
    case 'closed':
      return '이번엔 눈을 감은 상태를 유지해 주세요.'
    case 'gaze':
      return '화면에 표시된 점을 눈으로 바라본 뒤 버튼을 눌러주세요.'
    default:
      return '보정이 완료되었습니다.'
  }
})
const calibrationPrimaryLabel = computed(() => {
  if (
    calibrationStage.value === 'open' ||
    calibrationStage.value === 'closed'
  ) {
    if (isSamplingEyeStep.value) return '기록 중…'
    if (eyeSampleFeedback.value === 'success') return '완료!'
    return '기록 시작'
  }
  if (calibrationStage.value === 'gaze') {
    const isLast =
      gazeCalibrationTargetIndex.value >=
      eyeTracking.gazeCalibrationTargets.length - 1
    return isLast
      ? '보정 완료'
      : `다음 지점 (${gazeCalibrationTargetIndex.value + 1}/${eyeTracking.gazeCalibrationTargets.length})`
  }
  return '완료'
})
const calibrationBackLabel = computed(() =>
  calibrationStageIndex.value === 0 ? '나중에 하기' : '이전',
)
const gazeCalibrationTargetStyle = computed(() => {
  const target =
    eyeTracking.gazeCalibrationTargets[gazeCalibrationTargetIndex.value]
  if (!target) return {}
  return { left: `${target.x * 100}%`, top: `${target.y * 100}%` }
})
// previewVideo는 캘리브레이션 다이얼로그에서만 쓰인다. eyeTracking.videoRef를 그대로 별칭으로 써서
// 사용자가 보는 영상과 MediaPipe가 실제로 분석하는 영상이 항상 같은 엘리먼트이도록 한다.
const previewVideo = eyeTracking.videoRef
const panelVideo = ref<globalThis.HTMLVideoElement | null>(null)
const dialogRef = ref<globalThis.HTMLElement | null>(null)
let previousBodyOverflow = ''
let isBodyScrollLocked = false
let countdownTimer: ReturnType<typeof globalThis.setInterval> | undefined

const game = computed(() => {
  const id = String(route.params.gameId ?? '')
  return isGameDetailId(id) ? gameDetails[id] : undefined
})
const mode = computed(() => String(route.query.mode ?? 'solo'))
const roomCode = computed(() => String(route.query.room ?? ''))
const role = computed<RoomRole>(() =>
  route.query.role === 'player' ? 'player' : 'host',
)
const isMultiplayer = computed(() => ['friends', 'random'].includes(mode.value))
const isFriendRoom = computed(() => mode.value === 'friends')
const isRandomRoom = computed(() => mode.value === 'random')
const isHost = computed(() => isFriendRoom.value && role.value === 'host')

// --- 친구(invite) 대결 실시간 세션 ---
// REST로 방을 만들거나 참가하면 실시간 세션이 켜지고, 대기방 WebSocket으로 상대 준비 상태와
// GAME_START(미디어 접속 정보)를 주고받는다. 백엔드가 없거나 실패하면 기존 mock 준비 화면을 유지한다.
const mediaSession = useMediaSessionStore()
const liveRoomId = ref<string | null>(null)
const liveRoomCode = ref<string | null>(null)
const liveIdentity = ref<WaitingRoomIdentity | null>(null)
/** 친구방 입장(생성/참가) 실패 메시지. 설정되면 유령 방 대신 실패 안내를 보여준다. */
const inviteError = ref<string | null>(null)
const isLiveSession = computed(() => liveRoomId.value !== null)

const waitingSocket = useWaitingRoomSocket({
  onGameStart: handleGameStart,
  onError: (_code, message) => showToast(message),
})

const myRoomRole = computed(() => (isHost.value ? 'HOST' : 'PLAYER'))
const liveOpponent = computed<WaitingRoomParticipant | null>(
  () =>
    waitingSocket.roomState.value?.participants.find(
      (participant) => participant.roomRole !== myRoomRole.value,
    ) ?? null,
)
const isOpponentReady = computed(() => liveOpponent.value?.isReady ?? false)
// 실제 입장이 확정된 코드(liveRoomCode)만 노출한다. 참가자가 입력한 코드(roomCode)를 그대로
// 보여주면 입장 실패 시에도 방이 생긴 것처럼 보이므로 쓰지 않는다.
const displayRoomCode = computed(() => liveRoomCode.value ?? '')

// 서버 ROOM_STATE 기준 준비 현황(가이드의 "N/2명 준비 완료" 표시에 사용).
const readyCount = computed(
  () =>
    waitingSocket.roomState.value?.participants.filter((p) => p.isReady)
      .length ?? 0,
)
const participantCount = computed(
  () => waitingSocket.roomState.value?.participants.length ?? 2,
)

// 대기방 미디어(피어 웹캠): 방 참가 시 받은 토큰으로 OpenVidu에 연결해 상대 웹캠을 구독하고,
// 내 카메라가 준비되면 내 트랙을 송출한다. 내 웹캠은 아래 getUserMedia 프리뷰로 이미 보여준다.
const readyMedia = useLiveKitRoom()
const opponentVideoRef = readyMedia.remoteVideoRef
const hasPeerCamera = computed(() => readyMedia.hasRemoteVideo.value)
// vue-tsc가 문자열 템플릿 ref(ref="opponentVideoRef")를 '사용'으로 세지 못해 noUnusedLocals가
// 오탐한다. 실제로는 <video ref="opponentVideoRef">에 런타임 바인딩된다.
void opponentVideoRef
let readyMediaStarted = false

async function connectReadyMedia() {
  if (readyMediaStarted || !cameraStream.value || !mediaSession.credentials) {
    return
  }
  readyMediaStarted = true
  await readyMedia.connect(mediaSession.credentials, {
    localTrack: cameraStream.value.getVideoTracks()[0] ?? null,
  })
}

const roomTitle = computed(() => {
  if (isRandomRoom.value) return '랜덤 매칭 준비방'
  if (isFriendRoom.value) return '친구와 대결 준비방'
  if (mode.value === 'ai') return 'AI 대결 준비방'
  return '혼자하기 준비방'
})
const roomDescription = computed(() => {
  if (isRandomRoom.value)
    return '상대를 찾았습니다. 서로 준비를 완료하면 게임이 시작됩니다.'
  if (isFriendRoom.value)
    return isHost.value
      ? '참가자의 준비가 완료되면 게임을 시작할 수 있어요.'
      : '준비를 완료한 뒤 방장이 게임을 시작할 때까지 기다려 주세요.'
  return '게임 시작 전 카메라와 시선 인식 준비 상태를 확인해 주세요.'
})
// 권한 승인 + 트랙이 실제 라이브일 때만 연결로 본다. 브라우저/OS에서 캠을 끄면(track ended)
// cameraActive가 false로 떨어져 화면이 다시 "카메라 연결하기" 상태로 돌아간다.
const isCameraConnected = computed(
  () => permissionStatus.value === 'granted' && eyeTracking.cameraActive.value,
)
const canStartCalibration = computed(() => isCameraConnected.value)
const canMarkReady = computed(() => isCalibrated.value && !isHost.value)
const ownPreparationComplete = computed(() =>
  isHost.value ? isCalibrated.value : isReady.value,
)
const areAllPlayersReady = computed(() => {
  if (!isMultiplayer.value) return ownPreparationComplete.value
  return ownPreparationComplete.value && isOpponentReady.value
})
const canStartGame = computed(() => isHost.value && areAllPlayersReady.value)
const opponentName = computed(() =>
  isRandomRoom.value ? '매칭된 상대' : isHost.value ? '참가자' : '방장',
)
const actionLabel = computed(() => {
  if (!isCameraConnected.value) return '카메라 연결하기'
  if (!isCalibrated.value) return '캘리브레이션 시작'
  if (!isHost.value && !isReady.value) return '준비 완료'
  if (isHost.value) return '게임 시작'
  return ''
})
const actionDisabled = computed(() => {
  if (permissionStatus.value === 'requesting') return true
  if (!isCameraConnected.value) return false
  if (!isCalibrated.value) return !canStartCalibration.value
  if (isHost.value) return !canStartGame.value
  return !canMarkReady.value || isReady.value
})
const actionReason = computed(() => {
  if (!isCameraConnected.value)
    return '카메라 연결 후 다음 단계를 진행할 수 있어요.'
  if (!isCalibrated.value)
    return isHost.value
      ? '게임을 시작하려면 캘리브레이션을 완료해 주세요.'
      : '캘리브레이션을 완료하면 준비할 수 있어요.'
  if (isHost.value)
    return canStartGame.value
      ? '모든 참가자가 준비되었어요.'
      : `다른 참가자의 준비를 기다리고 있어요. ${readyCount.value}/${participantCount.value}명 준비 완료`
  if (isRandomRoom.value && isReady.value)
    return isOpponentReady.value
      ? '모든 참가자가 준비되었어요. 잠시 후 게임이 시작됩니다.'
      : '상대방의 준비를 기다리고 있어요.'
  if (isFriendRoom.value && !isHost.value && isReady.value)
    return '방장이 게임을 시작할 때까지 기다려 주세요.'
  if (!isMultiplayer.value && isReady.value) return '준비가 완료되었습니다.'
  return ''
})

function stopCameraStream() {
  // cameraStream은 eyeTracking.stream과 같은 MediaStream을 가리키므로, 트랙만 따로 멈추지 않고
  // eyeTracking.stop()으로 감지 루프까지 함께 정리한다(안 그러면 requestAnimationFrame 루프가
  // 끊긴 스트림을 계속 붙잡고 있게 된다).
  eyeTracking.stop()
  cameraStream.value = null
}

function clearGameStartCountdown() {
  if (!countdownTimer) return
  globalThis.clearInterval(countdownTimer)
  countdownTimer = undefined
}

function openGameStartDialog() {
  clearGameStartCountdown()
  countdown.value = 3
  isGamePlaybackPending.value = false
  isGameStartDialogOpen.value = true

  countdownTimer = globalThis.setInterval(() => {
    if (countdown.value === 1) {
      clearGameStartCountdown()
      if (game.value)
        router.push({
          name: 'game-play',
          params: { gameId: game.value.id },
          query: route.query,
        })
      return
    }
    countdown.value -= 1
  }, 1000)
}

// 실시간 세션(초대/랜덤): 서버가 방을 COUNTDOWN으로 바꾸고 종료 시각을 주면 그에 맞춰 3-2-1을
// 그린다. 화면 전환은 서버의 GAME_START 수신 시에만 일어난다(handleGameStart).
function openServerCountdown(endsAtIso: string) {
  clearGameStartCountdown()
  const endsAt = new Date(endsAtIso).getTime()
  const sync = () => {
    const remaining = Math.ceil((endsAt - Date.now()) / 1000)
    countdown.value = Math.min(3, Math.max(1, remaining))
  }
  sync()
  isGamePlaybackPending.value = false
  isGameStartDialogOpen.value = true
  countdownTimer = globalThis.setInterval(() => {
    if (Date.now() >= endsAt) {
      clearGameStartCountdown()
      return
    }
    sync()
  }, 250)
}

function closeGameStartDialog() {
  clearGameStartCountdown()
  isGameStartDialogOpen.value = false
}

function handleLeaveRoom() {
  stopCameraStream()
  router.push({ name: 'game-detail', params: { gameId: game.value?.id } })
}

async function handleRequestCamera() {
  permissionStatus.value = 'requesting'

  // 세션은 살아있고 카메라 트랙만 끊긴 경우(브라우저/OS에서 캠을 껐다 켜기): start()의 재사용
  // 가드가 죽은 상태를 그대로 반환하므로 강제로 재획득한다. 보정은 유지한 채 프리뷰만 복구한다.
  if (eyeTracking.isActive.value && !eyeTracking.cameraActive.value) {
    const reacquired = await eyeTracking.restartCamera()
    if (!reacquired) {
      permissionStatus.value = 'unavailable'
      isCameraErrorOpen.value = true
      return
    }
    cameraStream.value = eyeTracking.stream.value
    permissionStatus.value = 'granted'
    isWebcamGuideOpen.value = false
    return
  }

  // eyeTracking.start()가 카메라 권한 요청과 MediaPipe Face Landmarker 로드(CDN, 수 초 소요)를
  // 동시에 진행한다. 모델 로드가 카메라 승인보다 먼저 끝나는 경우가 많아 체감 대기시간이 줄어든다.
  const started = await eyeTracking.start()
  if (!started) {
    const reason = eyeTracking.modelError.value
    permissionStatus.value =
      reason === 'NotAllowedError' ? 'denied' : 'unavailable'
    isCameraErrorOpen.value = true
    return
  }

  cameraStream.value = eyeTracking.stream.value
  permissionStatus.value = 'granted'
  isWebcamGuideOpen.value = false
  isCalibrationOpen.value = true
  calibrationStageIndex.value = 0
  eyeSampleFeedback.value = 'idle'
  // 백엔드 캘리브레이션 상태는 PENDING → IN_PROGRESS → COMPLETED 순서를 요구한다.
  // 캘리브레이션이 시작되는 이 시점에 IN_PROGRESS를 먼저 알려야 이후 COMPLETED가 수락된다.
  if (isLiveSession.value) waitingSocket.sendCalibrationStatus('IN_PROGRESS')
}

function handlePrimaryAction() {
  if (!isCameraConnected.value) {
    isWebcamGuideOpen.value = true
    return
  }
  if (!isCalibrated.value) {
    isCalibrationOpen.value = true
    return
  }
  if (isHost.value) {
    if (!canStartGame.value) return
    if (isLiveSession.value) waitingSocket.sendStartGame()
    else openGameStartDialog()
    return
  }
  if (canMarkReady.value && !isReady.value) {
    isReady.value = true
    if (isLiveSession.value) {
      waitingSocket.sendReady(true)
      showToast('내 준비 상태를 완료로 표시했어요.')
    } else if (!isMultiplayer.value) openGameStartDialog()
    else showToast('내 준비 상태를 완료로 표시했어요.')
  }
}

/**
 * 캘리브레이션 다이얼로그의 "다음" 버튼 하나로 현재 단계에 맞는 실제 동작을 실행한다.
 * - open/closed 단계: 1.2초간 눈 상태를 샘플링해 개인별 눈 뜬/감은 기준을 기록
 * - gaze 단계: 지금 바라보고 있는 지점을 현재 캘리브레이션 타깃과 짝지어 기록
 */
function handleCalibrationNext() {
  if (
    calibrationStage.value === 'open' ||
    calibrationStage.value === 'closed'
  ) {
    void runEyeSampleStep(calibrationStage.value)
    return
  }
  if (calibrationStage.value === 'gaze') {
    recordGazeCalibrationPoint()
  }
}

async function runEyeSampleStep(kind: 'open' | 'closed') {
  isSamplingEyeStep.value = true
  eyeSampleFeedback.value = 'idle'
  const result = await eyeTracking.recordEyeSample(kind)
  isSamplingEyeStep.value = false

  if (!result.success) {
    eyeSampleFeedback.value = 'insufficient'
    showToast(
      '얼굴이 잘 인식되지 않았어요. 카메라를 정면으로 보고 다시 시도해 주세요.',
    )
    return
  }

  eyeSampleFeedback.value = 'success'
  // 성공 표시를 잠깐 보여준 뒤 다음 단계로 넘어간다.
  globalThis.setTimeout(advanceCalibrationStage, 500)
}

function recordGazeCalibrationPoint() {
  const target: Point | undefined =
    eyeTracking.gazeCalibrationTargets[gazeCalibrationTargetIndex.value]
  if (!target) return

  const captured = eyeTracking.addGazeCalibrationSample(target)
  if (!captured) {
    showToast('시선이 감지되지 않았어요. 점을 계속 바라봐 주세요.')
    return
  }

  if (
    gazeCalibrationTargetIndex.value <
    eyeTracking.gazeCalibrationTargets.length - 1
  ) {
    gazeCalibrationTargetIndex.value += 1
    return
  }

  const result = eyeTracking.finishGazeCalibration()
  if (!result) {
    showToast('시선 보정에 실패했어요. 처음부터 다시 시도해 주세요.')
    gazeCalibrationTargetIndex.value = 0
    eyeTracking.beginGazeCalibration()
    return
  }

  gazeCalibrationEvaluation.value = result.evaluation
  calibrationStore.saveGazeProfile(result.profile)
  advanceCalibrationStage()
}

function advanceCalibrationStage() {
  eyeSampleFeedback.value = 'idle'
  if (calibrationStageIndex.value < calibrationStages.value.length - 1) {
    calibrationStageIndex.value += 1
  }
  if (calibrationStage.value === 'gaze') {
    eyeTracking.beginGazeCalibration()
    gazeCalibrationTargetIndex.value = 0
  }
  if (calibrationStage.value === 'done') {
    finishCalibration()
  }
}

function finishCalibration() {
  calibrationStore.saveEyeProfile(eyeTracking.eyeProfile.value)
  isCalibrated.value = true
  isCalibrationOpen.value = false
  if (isLiveSession.value) {
    waitingSocket.sendCalibrationStatus('COMPLETED')
    // 방장은 준비 버튼이 없으므로 캘리브레이션 완료 시 준비 상태도 함께 올린다.
    if (isHost.value) waitingSocket.sendReady(true)
  }
  showToast('시선 캘리브레이션이 완료되었어요.')
}

function handleCalibrationBack() {
  if (isSamplingEyeStep.value) return
  if (calibrationStageIndex.value > 0) {
    calibrationStageIndex.value -= 1
    eyeSampleFeedback.value = 'idle'
  } else {
    isCalibrationOpen.value = false
  }
}

function handleGameStart(data: WaitingRoomGameStartData) {
  if (data.openviduUrl && data.token) {
    mediaSession.setCredentials({
      openviduUrl: data.openviduUrl,
      token: data.token,
    })
  }
  // 준비 화면 카메라를 놓아줘야 플레이 화면에서 다시 잡을 수 있다.
  stopCameraStream()
  if (game.value)
    router.push({
      name: 'game-play',
      params: { gameId: game.value.id },
      // 랜덤 매칭은 route.query에 이미 roomId(UUID)가 있지만, 초대(친구) 방은 host가 만들 때도
      // joiner가 참가할 때도 route.query엔 4자리 room 코드만 있을 뿐 실제 roomId가 없다.
      // 게임 화면에서 게임 세션 WebSocket(/ws/game-sessions/{roomId})에 접속하려면 필요하므로
      // 여기서 명시적으로 채워 넣는다.
      query: liveRoomId.value
        ? { ...route.query, roomId: liveRoomId.value }
        : route.query,
    })
}

async function initInviteSession() {
  if (!isFriendRoom.value || !game.value) return
  try {
    inviteError.value = null
    const token = currentAccessToken()
    const gameName = GAME_NAME_BY_ID[game.value.id]
    if (isHost.value) {
      const created = await createInviteRoom(gameName, token)
      liveRoomId.value = created.roomId
      liveRoomCode.value = created.roomCode
      if (created.openviduUrl && created.token) {
        mediaSession.setCredentials({
          openviduUrl: created.openviduUrl,
          token: created.token,
        })
      }
    } else {
      if (!roomCode.value) return
      const joined = await joinInviteRoom(roomCode.value, token)
      liveRoomId.value = joined.roomId
      liveRoomCode.value = joined.roomCode
      if (joined.openviduUrl && joined.token) {
        mediaSession.setCredentials({
          openviduUrl: joined.openviduUrl,
          token: joined.token,
        })
      }
    }
    // REST 응답에서 게스트 세션이 저장됐으므로, 신원을 공통 helper로 만든다(회원=토큰/게스트=세션).
    liveIdentity.value = resolveIdentity()
    if (liveRoomId.value && liveIdentity.value) {
      waitingSocket.connect(liveRoomId.value, liveIdentity.value)
    } else {
      liveRoomId.value = null
    }
  } catch (error) {
    // 실패 시 준비 화면은 유지하되, 원인을 사용자에게 알린다(조용히 삼키지 않는다).
    liveRoomId.value = null
    const message =
      error instanceof ApiError
        ? error.message
        : '대기방 연결에 실패했어요. 잠시 후 다시 시도해 주세요.'
    inviteError.value = message
    showToast(message)
  }
}

// 랜덤방: 매칭이 성사되면 방 멤버십이 이미 성립돼 있으므로 REST join 없이 roomId로 바로 접속한다.
// roomId는 GameRoomDialog의 매칭 결과가 쿼리(roomId)로 전달한다. 미디어 토큰은 GAME_START로만 온다.
function initRandomSession() {
  if (!isRandomRoom.value || !game.value) return
  const roomId = String(route.query.roomId ?? '')
  if (!roomId) return
  const identity = resolveIdentity()
  if (!identity) return
  liveRoomId.value = roomId
  liveIdentity.value = identity
  waitingSocket.connect(roomId, identity)
}

function handleDialogBackdrop(event: globalThis.MouseEvent, close: () => void) {
  if (event.target === event.currentTarget) close()
}

async function handleCopyRoomCode() {
  if (!displayRoomCode.value) return
  try {
    await globalThis.navigator.clipboard.writeText(displayRoomCode.value)
    showToast('방 코드를 복사했어요!')
  } catch {
    /* no-op */
  }
}

function handleKeydown(event: globalThis.KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (isGameStartDialogOpen.value) closeGameStartDialog()
  else if (isCalibrationOpen.value) isCalibrationOpen.value = false
  else if (isCameraErrorOpen.value) isCameraErrorOpen.value = false
  else if (isWebcamGuideOpen.value) isWebcamGuideOpen.value = false
}

function attachStreamTo(video: globalThis.HTMLVideoElement | null) {
  if (!video || !cameraStream.value) return
  video.srcObject = cameraStream.value
  void video.play().catch(() => undefined)
}

async function attachPreviewStream() {
  await nextTick()
  attachStreamTo(previewVideo.value)
  attachStreamTo(panelVideo.value)
}

watch(cameraStream, attachPreviewStream)
watch(panelVideo, (video) => attachStreamTo(video))
// 내 카메라와 접속 토큰이 모두 준비되면 대기방에서 피어 미디어 연결을 시작한다.
watch(
  [cameraStream, () => mediaSession.credentials?.token],
  () => void connectReadyMedia(),
)
watch(
  [
    isWebcamGuideOpen,
    isCalibrationOpen,
    isCameraErrorOpen,
    isGameStartDialogOpen,
  ],
  async ([guideOpen, calibrationOpen, errorOpen]) => {
    const hasOpenDialog = guideOpen || calibrationOpen || errorOpen
    if (typeof globalThis.document === 'undefined') return

    if (hasOpenDialog) {
      if (!isBodyScrollLocked) {
        previousBodyOverflow = globalThis.document.body.style.overflow
        isBodyScrollLocked = true
      }
      globalThis.document.body.style.overflow = 'hidden'
      await nextTick()
      const initialFocus =
        dialogRef.value?.querySelector<globalThis.HTMLElement>(
          '[data-dialog-initial-focus]',
        )
      ;(initialFocus ?? dialogRef.value)?.focus()
    } else {
      globalThis.document.body.style.overflow = previousBodyOverflow
      previousBodyOverflow = ''
      isBodyScrollLocked = false
    }
  },
  { immediate: true },
)

watch(isCalibrationOpen, (isOpen) => {
  if (isOpen) void attachPreviewStream()
})

// 실시간 세션의 자동/수동 시작은 모두 서버가 방을 COUNTDOWN으로 바꾸면서 시작된다.
watch(
  () => waitingSocket.roomState.value?.roomStatus,
  (roomStatus) => {
    const state = waitingSocket.roomState.value
    if (roomStatus === 'COUNTDOWN' && state?.countdownEndsAt) {
      openServerCountdown(state.countdownEndsAt)
    }
  },
)

if (typeof globalThis.window !== 'undefined')
  globalThis.window.addEventListener('keydown', handleKeydown)

onMounted(() => {
  if (isRandomRoom.value) initRandomSession()
  else void initInviteSession()
})

onBeforeUnmount(() => {
  stopCameraStream()
  clearGameStartCountdown()
  waitingSocket.close()
  if (typeof globalThis.document !== 'undefined')
    globalThis.document.body.style.overflow = previousBodyOverflow
  isBodyScrollLocked = false
  if (typeof globalThis.window !== 'undefined')
    globalThis.window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <section v-if="game" class="game-room-page">
    <RouterLink class="back" :to="`/games/${game.id}`"
      >← 게임 목록으로</RouterLink
    >

    <header class="room-header">
      <div>
        <span class="room-header__mode" aria-hidden="true">
          <svg
            v-if="mode === 'random'"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <rect x="3.5" y="3.5" width="17" height="17" rx="5" />
            <circle
              cx="8.5"
              cy="8.5"
              r="1.1"
              fill="currentColor"
              stroke="none"
            />
            <circle
              cx="15.5"
              cy="8.5"
              r="1.1"
              fill="currentColor"
              stroke="none"
            />
            <circle cx="12" cy="12" r="1.1" fill="currentColor" stroke="none" />
            <circle
              cx="8.5"
              cy="15.5"
              r="1.1"
              fill="currentColor"
              stroke="none"
            />
            <circle
              cx="15.5"
              cy="15.5"
              r="1.1"
              fill="currentColor"
              stroke="none"
            />
          </svg>
          <svg
            v-else-if="mode === 'friends'"
            viewBox="0 0 24 24"
            fill="currentColor"
          >
            <circle cx="8.5" cy="8" r="3" />
            <path d="M2.5 19c.6-3.4 3-5.2 6-5.2s5.4 1.8 6 5.2" />
            <circle cx="16.5" cy="8.5" r="2.6" opacity="0.7" />
            <path
              d="M13.6 13.6c1-.6 2-.9 3-.9 2.6 0 4.7 1.6 5.3 4.6"
              opacity="0.7"
            />
          </svg>
          <svg
            v-else
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <rect x="2.5" y="7" width="19" height="10" rx="5" />
            <path d="M7 10v4M5 12h4" />
            <circle cx="16" cy="10.5" r="1" fill="currentColor" stroke="none" />
            <circle
              cx="18.2"
              cy="12.7"
              r="1"
              fill="currentColor"
              stroke="none"
            />
          </svg>
        </span>
        <h1>{{ roomTitle }}</h1>
        <p>{{ roomDescription }}</p>
      </div>
      <div class="room-header__actions">
        <div v-if="isFriendRoom" class="room-code" aria-label="방 코드">
          <span>방 코드</span>
          <b
            v-for="(digit, index) in displayRoomCode.padEnd(4, '•').slice(0, 4)"
            :key="index"
            >{{ digit }}</b
          >
          <button
            v-if="displayRoomCode"
            type="button"
            class="room-code__copy"
            aria-label="방 코드 복사"
            @click="handleCopyRoomCode"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <rect x="8.5" y="8.5" width="11" height="11" rx="2.4" />
              <path
                d="M15.5 8.5V6.4A1.9 1.9 0 0 0 13.6 4.5H6.4A1.9 1.9 0 0 0 4.5 6.4v7.2a1.9 1.9 0 0 0 1.9 1.9h2.1"
              />
            </svg>
          </button>
        </div>
        <button
          type="button"
          :aria-label="isRandomRoom ? '매칭 취소' : '방 나가기'"
          @click="handleLeaveRoom"
        >
          {{ isRandomRoom ? '매칭 취소' : '방 나가기' }}
        </button>
      </div>
    </header>

    <section
      v-if="isFriendRoom && inviteError && !isLiveSession"
      class="room-join-error"
      role="alert"
    >
      <strong>입장하지 못했어요</strong>
      <p>{{ inviteError }}</p>
      <div class="room-join-error__actions">
        <button
          type="button"
          class="room-join-error__retry"
          @click="initInviteSession"
        >
          다시 시도
        </button>
        <button
          type="button"
          class="room-join-error__back"
          @click="handleLeaveRoom"
        >
          돌아가기
        </button>
      </div>
    </section>

    <section
      class="participant-grid"
      :class="{ 'participant-grid--solo': !isMultiplayer }"
      aria-label="참가자 준비 상태"
    >
      <article class="participant-card participant-card--me">
        <header>
          <div>
            <span class="participant-role">{{
              isHost ? 'HOST' : 'PLAYER'
            }}</span>
            <h2>나</h2>
          </div>
          <span v-if="ownPreparationComplete" class="complete-badge">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M5 13l4 4L19 7"
                fill="none"
                stroke="currentColor"
                stroke-width="2.4"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            완료
          </span>
        </header>
        <div class="participant-visual">
          <video
            v-if="isCameraConnected"
            ref="panelVideo"
            class="participant-visual__camera"
            autoplay
            muted
            playsinline
            aria-label="내 웹캠 미리보기"
          ></video>
          <img
            v-else
            :src="game?.mascotImage ?? ''"
            alt="내 게임 준비 상태 마스코트"
            draggable="false"
          />
          <span>{{ isCameraConnected ? '내 웹캠' : '내 준비 상태' }}</span>
        </div>
        <ol class="my-progress" aria-label="나의 게임 준비 진행 단계">
          <li :class="{ complete: isCameraConnected }">
            <b aria-hidden="true">
              <svg v-if="isCameraConnected" viewBox="0 0 24 24">
                <path
                  d="M5 13l4 4L19 7"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.4"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              <template v-else>1</template>
            </b>
            <span>카메라</span>
            <small>{{ isCameraConnected ? '완료' : '확인 필요' }}</small>
          </li>
          <li :class="{ complete: isCalibrated }">
            <b aria-hidden="true">
              <svg v-if="isCalibrated" viewBox="0 0 24 24">
                <path
                  d="M5 13l4 4L19 7"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.4"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              <template v-else>2</template>
            </b>
            <span>캘리브레이션</span>
            <small>{{ isCalibrated ? '완료' : '미완료' }}</small>
          </li>
          <li :class="{ complete: ownPreparationComplete }">
            <b aria-hidden="true">
              <svg v-if="ownPreparationComplete" viewBox="0 0 24 24">
                <path
                  d="M5 13l4 4L19 7"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.4"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              <template v-else>3</template>
            </b>
            <span>준비 상태</span>
            <small>{{ ownPreparationComplete ? '완료' : '미완료' }}</small>
          </li>
        </ol>
        <button
          v-if="actionLabel"
          class="participant-action"
          type="button"
          :disabled="actionDisabled"
          @click="handlePrimaryAction"
        >
          <template v-if="permissionStatus === 'requesting'">
            <i class="participant-action__spinner" aria-hidden="true" />
            카메라 권한 요청 중
          </template>
          <template v-else>{{ actionLabel }}</template>
        </button>
        <p v-if="actionReason" class="action-reason">{{ actionReason }}</p>
      </article>

      <article
        v-if="isMultiplayer"
        class="participant-card participant-card--opponent"
      >
        <header>
          <div>
            <span class="participant-role">{{
              isFriendRoom && !isHost ? 'HOST' : 'PLAYER'
            }}</span>
            <h2>{{ opponentName }}</h2>
          </div>
          <span v-if="isOpponentReady" class="complete-badge">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M5 13l4 4L19 7"
                fill="none"
                stroke="currentColor"
                stroke-width="2.4"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            완료
          </span>
        </header>
        <div class="participant-visual participant-visual--opponent">
          <video
            v-if="hasPeerCamera"
            ref="opponentVideoRef"
            class="participant-visual__camera participant-visual__camera--peer"
            autoplay
            playsinline
            :aria-label="`${opponentName} 웹캠 영상`"
          ></video>
          <img
            v-else
            :src="game.image"
            :alt="`${opponentName} 준비 상태 안내 이미지`"
            draggable="false"
          />
          <span>{{
            hasPeerCamera
              ? isOpponentReady
                ? '상대 준비 완료'
                : '상대 접속됨'
              : '상대 준비 대기'
          }}</span>
        </div>
        <p class="opponent-note">
          {{
            isOpponentReady
              ? `${opponentName}의 준비가 완료되었습니다.`
              : `${opponentName}의 준비 상태를 기다리고 있어요.`
          }}
        </p>
      </article>
    </section>
  </section>
  <section v-else class="missing">
    <h1>게임을 찾을 수 없어요.</h1>
    <RouterLink to="/games">게임 목록으로</RouterLink>
  </section>

  <Teleport to="body">
    <Transition name="dialog-pop">
      <div
        v-if="isWebcamGuideOpen"
        class="ready-dialog-backdrop"
        @click="handleDialogBackdrop($event, () => (isWebcamGuideOpen = false))"
      >
        <section
          ref="dialogRef"
          class="ready-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="webcam-guide-title"
          aria-describedby="webcam-guide-description"
        >
          <span class="ready-dialog__icon" aria-hidden="true">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path
                d="M4 8a2 2 0 0 1 2-2h2l1.2-1.6A2 2 0 0 1 10.8 3.6h2.4a2 2 0 0 1 1.6.8L16 6h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z"
              />
              <circle cx="12" cy="13" r="3.2" />
            </svg>
          </span>
          <h2 id="webcam-guide-title">게임 준비를 위해 웹캠을 켜주세요</h2>
          <p id="webcam-guide-description">
            게임 중 시선 인식 기능을 사용하기 위해 카메라 권한이 필요해요.
          </p>
          <p class="ready-dialog__notice">
            카메라 영상은 게임 진행에 필요한 경우에만 사용됩니다.
          </p>
          <div class="ready-dialog__actions">
            <button
              type="button"
              class="primary"
              data-dialog-initial-focus
              @click="handleRequestCamera"
            >
              웹캠 켜고 준비하기</button
            ><button type="button" class="secondary" @click="handleLeaveRoom">
              게임 상세로 돌아가기
            </button>
          </div>
        </section>
      </div>
    </Transition>

    <Transition name="dialog-pop">
      <div
        v-if="isCameraErrorOpen"
        class="ready-dialog-backdrop"
        @click="handleDialogBackdrop($event, () => (isCameraErrorOpen = false))"
      >
        <section
          ref="dialogRef"
          class="ready-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="camera-error-title"
          aria-describedby="camera-error-description"
        >
          <span
            class="ready-dialog__icon ready-dialog__icon--error"
            aria-hidden="true"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M12 3.5l9.3 16a1 1 0 0 1-.9 1.5H3.6a1 1 0 0 1-.9-1.5z" />
              <path d="M12 9.5v4.2M12 17h.01" />
            </svg>
          </span>
          <h2 id="camera-error-title">카메라 권한을 확인해 주세요</h2>
          <p id="camera-error-description">
            {{
              permissionStatus === 'denied'
                ? '카메라 권한이 허용되지 않아 게임을 시작할 수 없어요. 브라우저 주소창의 카메라 권한 설정을 확인한 뒤 다시 시도해 주세요.'
                : '카메라를 사용할 수 없어요. 연결된 카메라와 브라우저 지원 여부를 확인해 주세요.'
            }}
          </p>
          <div class="ready-dialog__actions">
            <button
              type="button"
              class="primary"
              data-dialog-initial-focus
              @click="handleRequestCamera"
            >
              다시 요청</button
            ><button type="button" class="secondary" @click="handleLeaveRoom">
              게임 상세로 돌아가기
            </button>
          </div>
        </section>
      </div>
    </Transition>

    <Transition name="dialog-pop">
      <div
        v-if="isCalibrationOpen"
        class="ready-dialog-backdrop ready-dialog-backdrop--calibration"
        @click="handleDialogBackdrop($event, () => (isCalibrationOpen = false))"
      >
        <section
          ref="dialogRef"
          class="calibration-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="calibration-title"
          aria-describedby="calibration-description"
        >
          <header>
            <div>
              <span
                >STEP {{ calibrationStepNumber }} /
                {{ totalCalibrationSteps }}</span
              >
              <h2 id="calibration-title">{{ calibrationStageTitle }}</h2>
              <p id="calibration-description">
                {{ calibrationStageDescription }}
              </p>
            </div>
            <button
              type="button"
              aria-label="캘리브레이션 닫기"
              @click="isCalibrationOpen = false"
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
          </header>
          <div class="calibration-progress" aria-label="캘리브레이션 진행률">
            <span
              :style="{
                transform: `scaleX(${calibrationStepNumber / totalCalibrationSteps})`,
              }"
            />
          </div>
          <div class="calibration-stage">
            <video
              v-if="cameraStream"
              ref="previewVideo"
              autoplay
              muted
              playsinline
              aria-label="내 카메라 로컬 프리뷰"
            />
            <img
              v-else
              :src="game?.mascotImage ?? ''"
              alt="카메라 연결 전 안내 마스코트"
              draggable="false"
            />

            <!-- 눈 뜬/감은 기준 기록 단계: 실시간 얼굴 인식 상태와 기록 진행 상황을 보여준다 -->
            <template
              v-if="
                calibrationStage === 'open' || calibrationStage === 'closed'
              "
            >
              <div class="calibration-live-status" role="status">
                <span v-if="!eyeTracking.faceDetected.value" class="warn">
                  얼굴이 인식되지 않았어요. 카메라를 정면으로 봐 주세요.
                </span>
                <span v-else-if="isSamplingEyeStep" class="recording">
                  기록 중입니다 · 자세를 유지해 주세요
                </span>
                <span
                  v-else-if="eyeSampleFeedback === 'success'"
                  class="success"
                >
                  기록 완료!
                </span>
                <span
                  v-else-if="eyeSampleFeedback === 'insufficient'"
                  class="warn"
                >
                  샘플이 부족했어요. 다시 시도해 주세요.
                </span>
                <span v-else>준비되면 아래 버튼을 눌러 기록을 시작하세요.</span>
              </div>
            </template>

            <!-- 시선 좌표 보정 단계: 9개 지점을 순서대로 응시하며 진행한다 -->
            <template v-else-if="calibrationStage === 'gaze'">
              <i
                class="calibration-target"
                :style="gazeCalibrationTargetStyle"
                aria-hidden="true"
              />
              <p>
                지점 {{ gazeCalibrationTargetIndex + 1 }} /
                {{ eyeTracking.gazeCalibrationTargets.length }}
              </p>
            </template>
          </div>
          <footer>
            <button
              type="button"
              class="secondary"
              :disabled="isSamplingEyeStep"
              @click="handleCalibrationBack"
            >
              {{ calibrationBackLabel }}</button
            ><button
              type="button"
              class="primary"
              data-dialog-initial-focus
              :disabled="isSamplingEyeStep"
              @click="handleCalibrationNext"
            >
              {{ calibrationPrimaryLabel }}
            </button>
          </footer>
        </section>
      </div>
    </Transition>

    <Transition name="dialog-pop">
      <div
        v-if="isGameStartDialogOpen"
        class="ready-dialog-backdrop"
        @click="handleDialogBackdrop($event, closeGameStartDialog)"
      >
        <section
          ref="dialogRef"
          class="ready-dialog ready-dialog--game-start"
          role="dialog"
          aria-modal="true"
          aria-labelledby="game-start-title"
          aria-describedby="game-start-description"
          tabindex="-1"
        >
          <span class="ready-dialog__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <path
                d="M5 13l4 4L19 7"
                fill="none"
                stroke="currentColor"
                stroke-width="2.4"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </span>
          <h2 id="game-start-title">
            {{
              isGamePlaybackPending
                ? '게임 플레이 준비 중'
                : '게임이 시작됩니다'
            }}
          </h2>
          <p id="game-start-description">
            <template v-if="isGamePlaybackPending">
              게임 플레이 화면은 준비 중이에요.
            </template>
            <template v-else>
              게임 준비가 완료되었습니다.<br />
              카운트다운이 끝나면 게임을 시작할 예정이에요.
            </template>
          </p>
          <div
            v-if="!isGamePlaybackPending"
            class="game-start-countdown"
            aria-label="게임 시작 예정 카운트다운"
            aria-live="assertive"
          >
            <Transition name="count-tick">
              <b :key="countdown">{{ countdown }}</b>
            </Transition>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.game-room-page {
  width: min(100%, 1040px);
  margin: 0 auto;
  padding: 22px 0 52px;
}
.back {
  color: var(--color-muted);
  font-size: 13px;
  font-weight: 700;
}
.room-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22px;
  margin: 22px 0 18px;
}
.room-header > div:first-child {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 3px 12px;
  align-items: center;
}
.room-header__mode {
  grid-row: span 2;
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 12px;
  background: var(--color-blue-soft);
  font-size: 19px;
}
.room-header__mode svg {
  width: 20px;
  height: 20px;
}
.room-header h1 {
  margin: 0;
  color: var(--color-ink);
  font-size: clamp(25px, 3vw, 34px);
}
.room-header p {
  grid-column: 2;
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
}
.room-header__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.room-header__actions > button,
.secondary {
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid var(--color-line);
  border-radius: 10px;
  color: var(--color-ink);
  background: #fff;
  font-weight: 800;
  cursor: pointer;
}
.room-header__actions > button:hover,
.secondary:hover {
  border-color: var(--color-accent-blue);
  color: var(--color-accent-blue);
}
.room-code {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px;
  border: 1px solid var(--color-line);
  border-radius: 11px;
  background: var(--color-surface-soft);
}
.room-code span {
  margin-right: 4px;
  color: var(--color-muted);
  font-size: 11px;
  font-weight: 800;
}
.room-code b {
  display: grid;
  width: 28px;
  height: 30px;
  place-items: center;
  border: 1px solid var(--color-line);
  border-radius: 7px;
  color: var(--color-accent-blue);
  background: #fff;
  font-size: 18px;
}
.room-code__copy {
  display: grid;
  width: 32px;
  height: 32px;
  margin-left: 4px;
  place-items: center;
  border: 1px solid var(--color-line);
  border-radius: 50%;
  color: var(--color-muted);
  background: #fff;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease,
    border-color var(--duration-fast) ease;
}
.room-code__copy svg {
  width: 16px;
  height: 16px;
}
.room-code__copy:hover {
  border-color: var(--color-accent-blue);
  color: var(--color-accent-blue);
  background: var(--color-blue-soft);
}
.participant-role {
  color: var(--color-accent-blue);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.05em;
}
.complete {
  color: #278957;
}
.room-join-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 28px 20px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: #fff;
  text-align: center;
}
.room-join-error strong {
  font-size: 18px;
}
.room-join-error p {
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
}
.room-join-error__actions {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}
.room-join-error__retry,
.room-join-error__back {
  padding: 10px 18px;
  border-radius: 10px;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}
.room-join-error__retry {
  border: 0;
  color: #fff;
  background: var(--color-accent-blue);
}
.room-join-error__back {
  border: 1px solid var(--color-line);
  color: var(--color-ink);
  background: #fff;
}
.participant-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 16px;
}
.participant-grid--solo {
  grid-template-columns: minmax(0, 560px);
  justify-content: center;
}
.participant-card {
  padding: 18px;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: #fff;
  box-shadow: var(--shadow-card);
}
.participant-card--me {
  border-color: #d9e8df;
}
.participant-card header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.participant-card h2 {
  margin: 3px 0 0;
  color: var(--color-ink);
  font-size: 20px;
}
.complete-badge {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: 4px;
  padding: 6px 9px;
  border-radius: 99px;
  color: #278957;
  background: #e6f7eb;
  font-size: 11px;
  font-weight: 800;
}
.complete-badge svg {
  width: 12px;
  height: 12px;
}
.participant-visual {
  position: relative;
  display: grid;
  min-height: 165px;
  place-items: center;
  margin-top: 13px;
  overflow: hidden;
  border-radius: 13px;
  background: #f7f4ff;
}
.participant-visual--opponent {
  background: #f2fbf5;
}
.participant-visual img {
  width: min(46%, 145px);
  height: 125px;
  object-fit: contain;
  user-select: none;
}
.participant-visual__camera {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scaleX(-1);
}
.participant-visual__camera--peer {
  transform: none;
}
.participant-visual span {
  position: absolute;
  bottom: 10px;
  left: 10px;
  padding: 5px 8px;
  border-radius: 99px;
  color: var(--color-muted);
  background: rgba(255, 255, 255, 0.9);
  font-size: 11px;
  font-weight: 800;
}
.my-progress {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: 14px 0;
  padding: 0;
  list-style: none;
}
.my-progress li {
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: auto auto;
  column-gap: 6px;
  padding: 9px;
  border-radius: 10px;
  color: var(--color-muted);
  background: var(--color-surface-soft);
  transition:
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease,
    border-color var(--duration-fast) ease;
}
.my-progress b {
  grid-row: span 2;
  display: grid;
  width: 21px;
  height: 21px;
  place-items: center;
  border: 1px solid var(--color-line);
  border-radius: 50%;
  font-size: 11px;
  transition:
    background-color var(--duration-fast) ease,
    border-color var(--duration-fast) ease;
}
.my-progress b svg {
  width: 12px;
  height: 12px;
}
.my-progress span {
  font-size: 11px;
  font-weight: 700;
}
.my-progress small {
  font-size: 11px;
  font-weight: 800;
}
.my-progress .complete {
  color: #278957;
}
.my-progress .complete b {
  border-color: #75c694;
  background: #e6f7eb;
}
.participant-action,
.primary {
  min-height: 43px;
  padding: 0 16px;
  border: 0;
  border-radius: 10px;
  color: #fff;
  background: var(--color-accent-blue);
  font-weight: 800;
  cursor: pointer;
}
.participant-action {
  width: 100%;
}
.participant-action:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.participant-action__spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  margin-right: 7px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  vertical-align: -2px;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.action-reason,
.opponent-note {
  min-height: 19px;
  margin: 9px 0 0;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.45;
}
.ready-dialog-backdrop {
  position: fixed;
  z-index: 30;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(13, 26, 56, 0.52);
}
.ready-dialog,
.calibration-dialog {
  width: min(100%, 470px);
  max-height: min(760px, calc(100vh - 40px));
  overflow: auto;
  box-sizing: border-box;
  padding: 28px;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 20px 60px rgba(9, 23, 55, 0.26);
}
.ready-dialog__icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 50%;
  color: var(--color-accent-blue);
  background: var(--color-blue-soft);
  font-weight: 900;
}
.ready-dialog__icon svg {
  width: 22px;
  height: 22px;
}
.ready-dialog__icon--error {
  color: #b75555;
  background: #fff0f0;
}
.ready-dialog h2 {
  margin: 14px 0 8px;
  color: var(--color-ink);
  font-size: 24px;
}
.ready-dialog p {
  color: var(--color-muted);
  line-height: 1.65;
}
.ready-dialog__notice {
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--color-surface-soft);
  font-size: 13px;
}
.ready-dialog__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 9px;
  margin-top: 20px;
}
.ready-dialog__actions button {
  width: 100%;
}
.calibration-dialog {
  width: min(100%, 760px);
}
.calibration-dialog header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}
.calibration-dialog header span {
  color: var(--color-accent-blue);
  font-size: 12px;
  font-weight: 800;
}
.calibration-dialog h2 {
  margin: 4px 0;
  color: var(--color-ink);
  font-size: 26px;
}
.calibration-dialog header p {
  margin: 0;
  color: var(--color-muted);
}
.calibration-dialog header button {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  color: var(--color-ink);
  background: var(--color-surface-soft);
  font-size: 25px;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.calibration-dialog header button svg {
  width: 16px;
  height: 16px;
}
.calibration-progress {
  height: 7px;
  margin: 20px 0;
  overflow: hidden;
  border-radius: 99px;
  background: var(--color-surface-soft);
}
.calibration-progress span {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: inherit;
  background: var(--color-accent-blue);
  transform-origin: left center;
  transition: transform 0.2s ease;
}
.calibration-stage {
  position: relative;
  display: grid;
  min-height: 330px;
  place-items: center;
  overflow: hidden;
  border-radius: 16px;
  background: var(--color-purple-soft);
}
.calibration-stage video,
.calibration-stage img {
  width: 100%;
  height: 330px;
  object-fit: contain;
}
.calibration-stage p {
  position: absolute;
  bottom: 12px;
  left: 50%;
  margin: 0;
  padding: 7px 10px;
  transform: translateX(-50%);
  border-radius: 99px;
  color: var(--color-muted);
  background: rgba(255, 255, 255, 0.9);
  font-size: 12px;
  font-weight: 800;
}
.calibration-target {
  position: absolute;
  width: 18px;
  height: 18px;
  border: 5px solid #fff;
  border-radius: 50%;
  background: var(--color-accent-blue);
  animation: calib-pulse 1.6s ease-in-out infinite;
  /* left/top은 gazeCalibrationTargetStyle이 0~1 좌표를 %로 변환해 인라인으로 넣어준다. */
  transform: translate(-50%, -50%);
}
@keyframes calib-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 6px rgba(79, 116, 219, 0.18);
  }
  50% {
    box-shadow: 0 0 0 12px rgba(79, 116, 219, 0.08);
  }
}
.calibration-live-status {
  position: absolute;
  bottom: 12px;
  left: 50%;
  max-width: calc(100% - 32px);
  margin: 0;
  padding: 9px 14px;
  transform: translateX(-50%);
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.92);
  font-size: 13px;
  font-weight: 800;
  text-align: center;
}
.calibration-live-status .warn {
  color: #b75555;
}
.calibration-live-status .success {
  color: #278957;
}
.calibration-live-status .recording {
  color: var(--color-accent-blue);
}
.calibration-dialog footer {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  margin-top: 18px;
}
.calibration-dialog footer button {
  min-width: 112px;
}
.ready-dialog--game-start {
  text-align: center;
}
.ready-dialog--game-start .ready-dialog__icon {
  margin: 0 auto;
  color: #278957;
  background: #e6f7eb;
}
.game-start-countdown {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
  min-height: 82px;
  margin: 22px 0;
}
.game-start-countdown b {
  position: absolute;
  display: grid;
  width: 82px;
  height: 82px;
  place-items: center;
  border-radius: 50%;
  color: var(--color-accent-blue);
  background: var(--color-blue-soft);
  font-size: 42px;
  animation: count-ring 1s ease-out infinite;
}
@keyframes count-ring {
  from {
    box-shadow: 0 0 0 0 rgba(79, 116, 219, 0.35);
  }
  to {
    box-shadow: 0 0 0 18px rgba(79, 116, 219, 0);
  }
}
.count-tick-enter-active {
  transition:
    transform 140ms var(--ease-out),
    opacity 140ms var(--ease-out);
}
.count-tick-leave-active {
  transition:
    transform 140ms ease,
    opacity 140ms ease;
}
.count-tick-enter-from {
  opacity: 0;
  transform: scale(0.85);
}
.count-tick-leave-to {
  opacity: 0;
  transform: scale(1.15);
}
.dialog-pop-enter-active,
.dialog-pop-leave-active {
  transition: background-color 200ms ease;
}
.dialog-pop-enter-active :is(.ready-dialog, .calibration-dialog),
.dialog-pop-leave-active :is(.ready-dialog, .calibration-dialog) {
  transition:
    transform 240ms var(--ease-out),
    opacity 240ms var(--ease-out);
}
.dialog-pop-enter-from,
.dialog-pop-leave-to {
  background-color: rgba(13, 26, 56, 0);
}
.dialog-pop-enter-from :is(.ready-dialog, .calibration-dialog),
.dialog-pop-leave-to :is(.ready-dialog, .calibration-dialog) {
  opacity: 0;
  transform: scale(0.96) translateY(8px);
}
.ready-dialog--game-start .primary {
  width: 100%;
}
.missing {
  padding: 60px;
  text-align: center;
}
button:focus-visible,
.back:focus-visible {
  outline: 3px solid rgba(79, 116, 219, 0.4);
  outline-offset: 3px;
}
@media (max-width: 720px) {
  .game-room-page {
    padding: 18px 0 36px;
  }
  .room-header {
    align-items: flex-start;
    flex-direction: column;
  }
  .room-header__actions {
    width: 100%;
    box-sizing: border-box;
  }
  .room-header__actions {
    justify-content: space-between;
  }
  .participant-grid {
    grid-template-columns: 1fr;
  }
  .my-progress {
    gap: 6px;
  }
  .my-progress li {
    grid-template-columns: 1fr;
    grid-template-rows: auto auto auto;
    place-items: center;
    gap: 3px;
    text-align: center;
  }
  .my-progress b {
    grid-row: auto;
  }
  .ready-dialog__actions {
    grid-template-columns: 1fr;
  }
  .calibration-dialog {
    padding: 20px;
  }
  .calibration-stage,
  .calibration-stage video,
  .calibration-stage img {
    min-height: 250px;
    height: 250px;
  }
}
</style>
