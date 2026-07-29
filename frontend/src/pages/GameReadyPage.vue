<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from '../composables/useToast'
import { gameDetails, isGameDetailId } from '../mocks/game-details'

type CameraPermissionStatus =
  'idle' | 'requesting' | 'granted' | 'denied' | 'unavailable'

type RoomRole = 'host' | 'player'

const route = useRoute()
const router = useRouter()
const { showToast } = useToast()
const permissionStatus = ref<CameraPermissionStatus>('idle')
const isCalibrated = ref(false)
const isReady = ref(false)
const isOpponentReady = ref(false)
const isWebcamGuideOpen = ref(true)
const isCalibrationOpen = ref(false)
const isCameraErrorOpen = ref(false)
const isGameStartDialogOpen = ref(false)
const countdown = ref(3)
const isGamePlaybackPending = ref(false)
const calibrationStep = ref(1)
const cameraStream = ref<globalThis.MediaStream | null>(null)
const previewVideo = ref<globalThis.HTMLVideoElement | null>(null)
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
const displayName = computed(
  () =>
    (game.value?.title ?? '').match(/\(([^)]+)\)/)?.[1] ??
    game.value?.title ??
    '게임',
)
const roomTitle = computed(() => {
  if (isRandomRoom.value) return '랜덤 매칭 대기방'
  if (isFriendRoom.value) return '친구와 대결 대기방'
  if (mode.value === 'ai') return `${displayName.value} AI 준비`
  return `${displayName.value} 준비`
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
const isCameraConnected = computed(() => permissionStatus.value === 'granted')
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
  if (!isCalibrated.value) return '캘리브레이션을 완료하면 준비할 수 있어요.'
  if (isHost.value && !canStartGame.value)
    return '다른 참가자의 준비를 기다리고 있어요.'
  if (isRandomRoom.value && isReady.value && !isOpponentReady.value)
    return '상대방의 준비를 기다리고 있어요.'
  if (isFriendRoom.value && !isHost.value && isReady.value)
    return '방장이 게임을 시작할 때까지 기다려 주세요.'
  if (!isMultiplayer.value && isReady.value)
    return '준비가 완료되었습니다. 게임 시작 기능을 연결할 예정이에요.'
  return ''
})

function stopCameraStream() {
  cameraStream.value?.getTracks().forEach((track) => track.stop())
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
      isGamePlaybackPending.value = true
      return
    }
    countdown.value -= 1
  }, 1000)
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

  if (!globalThis.navigator.mediaDevices?.getUserMedia) {
    permissionStatus.value = 'unavailable'
    isCameraErrorOpen.value = true
    return
  }

  try {
    cameraStream.value = await globalThis.navigator.mediaDevices.getUserMedia({
      video: true,
    })
    permissionStatus.value = 'granted'
    isWebcamGuideOpen.value = false
    isCalibrationOpen.value = true
  } catch (error) {
    const name = error instanceof globalThis.DOMException ? error.name : ''
    permissionStatus.value =
      name === 'NotAllowedError' ? 'denied' : 'unavailable'
    isCameraErrorOpen.value = true
  }
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
    if (canStartGame.value) openGameStartDialog()
    return
  }
  if (canMarkReady.value && !isReady.value) {
    isReady.value = true
    if (!isMultiplayer.value) openGameStartDialog()
    else showToast('내 준비 상태를 완료로 표시했어요.')
  }
}

function handleCalibrationNext() {
  if (calibrationStep.value < 3) {
    calibrationStep.value += 1
    return
  }
  isCalibrated.value = true
  isCalibrationOpen.value = false
  showToast('시선 캘리브레이션 준비가 완료되었어요.')
}

function handleCalibrationBack() {
  if (calibrationStep.value > 1) calibrationStep.value -= 1
  else isCalibrationOpen.value = false
}

function handleToggleOpponentReady() {
  isOpponentReady.value = !isOpponentReady.value
}

function handleDialogBackdrop(event: globalThis.MouseEvent, close: () => void) {
  if (event.target === event.currentTarget) close()
}

function handleKeydown(event: globalThis.KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (isGameStartDialogOpen.value) closeGameStartDialog()
  else if (isCalibrationOpen.value) isCalibrationOpen.value = false
  else if (isCameraErrorOpen.value) isCameraErrorOpen.value = false
  else if (isWebcamGuideOpen.value) isWebcamGuideOpen.value = false
}

async function attachPreviewStream() {
  await nextTick()
  if (!previewVideo.value || !cameraStream.value) return
  previewVideo.value.srcObject = cameraStream.value
  void previewVideo.value.play().catch(() => undefined)
}

watch(cameraStream, attachPreviewStream)
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

watch(areAllPlayersReady, (isReady) => {
  if (isReady && isRandomRoom.value) openGameStartDialog()
})

if (typeof globalThis.window !== 'undefined')
  globalThis.window.addEventListener('keydown', handleKeydown)

onBeforeUnmount(() => {
  stopCameraStream()
  clearGameStartCountdown()
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
        <span class="room-header__mode" aria-hidden="true">{{
          mode === 'random' ? '🎲' : mode === 'friends' ? '👥' : '🎮'
        }}</span>
        <h1>{{ roomTitle }}</h1>
        <p>{{ roomDescription }}</p>
      </div>
      <div class="room-header__actions">
        <div v-if="isFriendRoom" class="room-code" aria-label="방 코드">
          <span>방 코드</span>
          <b
            v-for="(digit, index) in roomCode.padEnd(4, '•').slice(0, 4)"
            :key="index"
            >{{ digit }}</b
          >
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
          <span v-if="ownPreparationComplete" class="complete-badge"
            >✓ 완료</span
          >
        </header>
        <div class="participant-visual">
          <img
            :src="game?.mascotImage ?? ''"
            alt="내 게임 준비 상태 마스코트"
            draggable="false"
          />
          <span>내 준비 상태</span>
        </div>
        <ol class="my-progress" aria-label="나의 게임 준비 진행 단계">
          <li :class="{ complete: isCameraConnected }">
            <b aria-hidden="true">{{ isCameraConnected ? '✓' : '1' }}</b>
            <span>카메라</span>
            <small>{{ isCameraConnected ? '완료' : '확인 필요' }}</small>
          </li>
          <li :class="{ complete: isCalibrated }">
            <b aria-hidden="true">{{ isCalibrated ? '✓' : '2' }}</b>
            <span>캘리브레이션</span>
            <small>{{ isCalibrated ? '완료' : '미완료' }}</small>
          </li>
          <li :class="{ complete: ownPreparationComplete }">
            <b aria-hidden="true">{{ ownPreparationComplete ? '✓' : '3' }}</b>
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
          {{ actionLabel }}
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
          <span v-if="isOpponentReady" class="complete-badge">✓ 완료</span>
        </header>
        <div class="participant-visual participant-visual--opponent">
          <img
            :src="game.image"
            :alt="`${opponentName} 준비 상태 안내 이미지`"
            draggable="false"
          />
          <span>{{
            isOpponentReady ? '상대 준비 완료' : '상대 준비 대기'
          }}</span>
        </div>
        <p class="opponent-note">
          {{
            isOpponentReady
              ? `${opponentName}의 준비가 완료되었습니다.`
              : `${opponentName}의 준비 상태를 기다리고 있어요.`
          }}
        </p>
        <button
          class="mock-toggle"
          type="button"
          @click="handleToggleOpponentReady"
        >
          mock: 상대 준비 {{ isOpponentReady ? '취소' : '완료' }}
        </button>
      </article>
    </section>
  </section>
  <section v-else class="missing">
    <h1>게임을 찾을 수 없어요.</h1>
    <RouterLink to="/games">게임 목록으로</RouterLink>
  </section>

  <Teleport to="body">
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
        <span class="ready-dialog__icon" aria-hidden="true">◉</span>
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
          >!</span
        >
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
            <span>STEP {{ calibrationStep }} / 3</span>
            <h2 id="calibration-title">시선 캘리브레이션</h2>
            <p id="calibration-description">
              화면의 안내 지점을 눈으로 따라봐 주세요.
            </p>
          </div>
          <button
            type="button"
            aria-label="캘리브레이션 닫기"
            @click="isCalibrationOpen = false"
          >
            ×
          </button>
        </header>
        <div class="calibration-progress" aria-label="캘리브레이션 진행률">
          <span :style="{ width: `${(calibrationStep / 3) * 100}%` }" />
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
          <i
            :class="`calibration-target calibration-target--${calibrationStep}`"
            aria-hidden="true"
          />
          <p>mock 캘리브레이션 {{ calibrationStep }}단계</p>
        </div>
        <footer>
          <button
            type="button"
            class="secondary"
            @click="handleCalibrationBack"
          >
            {{ calibrationStep === 1 ? '나중에 하기' : '이전' }}</button
          ><button
            type="button"
            class="primary"
            data-dialog-initial-focus
            @click="handleCalibrationNext"
          >
            {{ calibrationStep === 3 ? '완료' : '다음 안내 보기' }}
          </button>
        </footer>
      </section>
    </div>

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
        <span class="ready-dialog__icon" aria-hidden="true">✓</span>
        <h2 id="game-start-title">
          {{
            isGamePlaybackPending ? '게임 플레이 준비 중' : '게임이 시작됩니다'
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
        >
          <b>{{ countdown }}</b>
        </div>
      </section>
    </div>
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
.secondary,
.mock-toggle {
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
.secondary:hover,
.mock-toggle:hover {
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
.participant-role {
  color: var(--color-accent-blue);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.05em;
}
.complete {
  color: #278957;
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
  align-self: flex-start;
  padding: 6px 9px;
  border-radius: 99px;
  color: #278957;
  background: #e6f7eb;
  font-size: 11px;
  font-weight: 800;
}
.participant-visual {
  position: relative;
  display: grid;
  min-height: 165px;
  place-items: center;
  margin-top: 13px;
  overflow: hidden;
  border-radius: 13px;
  background: linear-gradient(135deg, var(--color-purple-soft), #fff);
}
.participant-visual--opponent {
  background: linear-gradient(135deg, #eef9f2, #fff);
}
.participant-visual img {
  width: min(46%, 145px);
  height: 125px;
  object-fit: contain;
  user-select: none;
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
.action-reason,
.opponent-note {
  min-height: 19px;
  margin: 9px 0 0;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.45;
}
.mock-toggle {
  min-height: 32px;
  margin-top: 9px;
  padding: 0 10px;
  color: var(--color-muted);
  font-size: 11px;
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
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  color: var(--color-ink);
  background: var(--color-surface-soft);
  font-size: 25px;
  cursor: pointer;
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
  height: 100%;
  border-radius: inherit;
  background: var(--color-accent-blue);
  transition: width 0.2s ease;
}
.calibration-stage {
  position: relative;
  display: grid;
  min-height: 330px;
  place-items: center;
  overflow: hidden;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--color-purple-soft), #f6fbf8);
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
  box-shadow: 0 0 0 6px rgba(85, 105, 220, 0.2);
  transition: all 0.2s ease;
}
.calibration-target--1 {
  top: 20%;
  left: 22%;
}
.calibration-target--2 {
  top: 32%;
  right: 20%;
}
.calibration-target--3 {
  bottom: 24%;
  left: 48%;
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
  display: flex;
  justify-content: center;
  gap: 10px;
  margin: 22px 0;
}
.game-start-countdown b {
  display: grid;
  width: 82px;
  height: 82px;
  place-items: center;
  border-radius: 50%;
  color: var(--color-accent-blue);
  background: var(--color-blue-soft);
  font-size: 42px;
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
  outline: 3px solid rgba(85, 105, 220, 0.4);
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
