<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ApiError } from '../../api/http'
import { joinMatch, cancelMatch } from '../../api/match'
import { currentAccessToken, resolveIdentity } from '../../api/identity'
import { useMatchSocket } from '../../composables/useMatchSocket'
import { GAME_NAME_BY_ID } from '../../types/waitingRoom'
import type { GameDetailId } from '../../types/game-detail'

type RoomFlow = 'friends' | 'random'

const props = defineProps<{
  open: boolean
  gameId: GameDetailId
  gameTitle: string
  flow: RoomFlow
}>()
const emit = defineEmits<{
  close: []
  enterRoom: [
    payload: {
      mode: RoomFlow
      roomCode?: string
      roomId?: string
    },
  ]
}>()
const dialogRef = ref<globalThis.HTMLElement | null>(null)
const roomCode = ref('')
const codeError = ref('')
const matchingSeconds = ref(0)
const matchError = ref('')
let matchInterval: ReturnType<typeof globalThis.setInterval> | undefined
let previousBodyOverflow = ''

// 랜덤 매칭 소켓: 성사되면 서버가 roomId를 푸시한다. 그 방으로 대기방에 접속하도록 위로 넘긴다.
const matchSocket = useMatchSocket({
  onMatchSuccess: (roomId) => enterRandomRoom(roomId),
  onError: (_code, message) => {
    matchError.value = message
    clearMatching()
  },
})

const title = computed(() =>
  props.flow === 'friends' ? '친구와 1:1 대결' : '랜덤 매칭',
)
const description = computed(() =>
  props.flow === 'friends'
    ? '방을 만들거나 친구에게 받은 코드로 참여해 보세요.'
    : '실력이 비슷한 상대를 찾고 있어요.',
)
const matchingTime = computed(() => {
  const minutes = Math.floor(matchingSeconds.value / 60)
  const seconds = matchingSeconds.value % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})

function clearMatching() {
  if (matchInterval) globalThis.clearInterval(matchInterval)
  matchInterval = undefined
}
function stopMatching() {
  clearMatching()
  matchSocket.close()
}
function closeDialog() {
  // 랜덤 매칭 중 닫기 = 매칭 취소. 소켓만 닫아도 서버가 정리하지만, 소켓 연결 전 취소도 커버한다.
  if (props.flow === 'random') {
    void cancelMatch(currentAccessToken()).catch(() => undefined)
  }
  stopMatching()
  emit('close')
}
function createRoom() {
  emit('enterRoom', { mode: 'friends' })
}
function joinRoom() {
  if (!/^\d{4}$/.test(roomCode.value.trim())) {
    codeError.value = '방 코드는 숫자 4자리로 입력해 주세요.'
    return
  }
  emit('enterRoom', {
    mode: 'friends',
    roomCode: roomCode.value.trim(),
  })
}
function enterRandomRoom(roomId: string) {
  stopMatching()
  emit('enterRoom', { mode: 'random', roomId })
}
async function startMatching() {
  clearMatching()
  matchError.value = ''
  matchingSeconds.value = 0
  matchInterval = globalThis.setInterval(() => {
    matchingSeconds.value += 1
  }, 1000)

  // 신규 게스트는 세션이 없으므로 join을 먼저 호출해 세션을 확보하고, 이미 성사된 경우
  // 응답의 waitingRoomId로 곧바로 입장한다. 아니면 소켓을 열어 MATCH_SUCCESS를 기다린다.
  try {
    const result = await joinMatch(
      GAME_NAME_BY_ID[props.gameId],
      currentAccessToken(),
    )
    if (!props.open) return
    if (result.waitingRoomId) {
      enterRandomRoom(result.waitingRoomId)
      return
    }
    const identity = resolveIdentity()
    if (!identity) {
      matchError.value = '매칭을 시작할 수 없어요. 잠시 후 다시 시도해 주세요.'
      clearMatching()
      return
    }
    matchSocket.connect(identity)
  } catch (error) {
    matchError.value =
      error instanceof ApiError
        ? error.message
        : '매칭 요청에 실패했어요. 잠시 후 다시 시도해 주세요.'
    clearMatching()
  }
}
function handleBackdropClick(event: globalThis.MouseEvent) {
  // 랜덤 매칭 중에는 바깥(배경)을 눌러도 닫히지 않게 한다 — 실수로 매칭이 취소되는 것을 막고,
  // 닫기는 X 버튼이나 '매칭 취소'로만 하게 한다.
  if (props.flow === 'random') return
  if (event.target === event.currentTarget) closeDialog()
}
function handleKeydown(event: globalThis.KeyboardEvent) {
  if (event.key === 'Escape') closeDialog()
}

watch(
  () => props.open,
  async (isOpen) => {
    stopMatching()
    roomCode.value = ''
    codeError.value = ''
    matchError.value = ''
    matchingSeconds.value = 0
    if (!isOpen) return
    if (props.flow === 'random') void startMatching()
    await nextTick()
    dialogRef.value
      ?.querySelector<globalThis.HTMLElement>('[data-dialog-initial-focus]')
      ?.focus()
  },
)
watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) globalThis.addEventListener('keydown', handleKeydown)
    else globalThis.removeEventListener('keydown', handleKeydown)
  },
)
watch(
  () => props.open,
  (isOpen) => {
    if (typeof globalThis.document === 'undefined') return
    if (isOpen) {
      previousBodyOverflow = globalThis.document.body.style.overflow
      globalThis.document.body.style.overflow = 'hidden'
    } else {
      globalThis.document.body.style.overflow = previousBodyOverflow
    }
  },
)
onBeforeUnmount(() => {
  stopMatching()
  globalThis.removeEventListener('keydown', handleKeydown)
  if (typeof globalThis.document !== 'undefined')
    globalThis.document.body.style.overflow = previousBodyOverflow
})
</script>

<template>
  <Teleport to="body"
    ><Transition name="dialog-pop"
      ><div
        v-if="open"
        class="game-room-dialog-backdrop"
        @click="handleBackdropClick"
      >
        <section
          ref="dialogRef"
          class="game-room-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="game-room-dialog-title"
          aria-describedby="game-room-dialog-description"
        >
          <header>
            <div>
              <span>{{ gameTitle }}</span>
              <h2 id="game-room-dialog-title">{{ title }}</h2>
              <p id="game-room-dialog-description">{{ description }}</p>
            </div>
            <button type="button" aria-label="모달 닫기" @click="closeDialog">
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
          <div v-if="flow === 'friends'" class="game-room-dialog__options">
            <article>
              <b aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <path
                    d="M12 5v14M5 12h14"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                  />
                </svg>
              </b>
              <h3>방 만들기</h3>
              <p>방 코드를 만들어 친구에게 공유해요.</p>
              <button
                class="primary"
                type="button"
                data-dialog-initial-focus
                @click="createRoom"
              >
                방 만들기
              </button>
            </article>
            <span>또는</span>
            <article>
              <b aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <path
                    d="M5 4v7a4 4 0 0 0 4 4h9M13 11l4 4-4 4"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </b>
              <h3>방 코드로 참여</h3>
              <p>친구가 공유한 숫자 4자리를 입력해요.</p>
              <label for="room-code">방 코드</label
              ><input
                id="room-code"
                v-model="roomCode"
                inputmode="numeric"
                maxlength="4"
                placeholder="예: 4827"
                :aria-invalid="Boolean(codeError)"
                aria-describedby="room-code-error"
                @input="codeError = ''"
              />
              <p
                v-if="codeError"
                id="room-code-error"
                class="error"
                role="alert"
              >
                {{ codeError }}
              </p>
              <button class="secondary" type="button" @click="joinRoom">
                참여하기
              </button>
            </article>
          </div>
          <div v-else class="game-room-dialog__matching">
            <i aria-hidden="true" /><strong>상대를 찾고 있어요</strong>
            <p>
              대기 시간 <b>{{ matchingTime }}</b>
            </p>
            <small v-if="!matchError"
              >실력이 비슷한 상대를 찾고 있어요. 잠시만 기다려 주세요.</small
            ><small v-else class="error" role="alert">{{ matchError }}</small
            ><button
              class="secondary"
              type="button"
              data-dialog-initial-focus
              @click="closeDialog"
            >
              매칭 취소
            </button>
          </div>
        </section>
      </div></Transition
    ></Teleport
  >
</template>

<style scoped>
.game-room-dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(23, 36, 61, 0.45);
}
.game-room-dialog {
  width: min(100%, 720px);
  max-height: calc(100vh - 48px);
  overflow: auto;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: #fff;
  box-shadow: var(--shadow-float);
}
.dialog-pop-enter-active,
.dialog-pop-leave-active {
  transition: background-color 200ms ease;
}
.dialog-pop-enter-active .game-room-dialog,
.dialog-pop-leave-active .game-room-dialog {
  transition:
    transform 240ms var(--ease-out),
    opacity 240ms var(--ease-out);
}
.dialog-pop-enter-from,
.dialog-pop-leave-to {
  background-color: rgba(23, 36, 61, 0);
}
.dialog-pop-enter-from .game-room-dialog,
.dialog-pop-leave-to .game-room-dialog {
  opacity: 0;
  transform: scale(0.96) translateY(8px);
}
header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 26px 28px 20px;
  border-bottom: 1px solid var(--color-line);
}
header span {
  color: var(--color-accent-blue);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}
h2 {
  margin: 5px 0 6px;
  color: var(--color-ink);
  font-size: 24px;
}
header p,
article p {
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.55;
}
header button {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: var(--color-surface-soft);
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
header button:hover {
  background: var(--color-line);
}
header button svg {
  width: 18px;
  height: 18px;
}
.game-room-dialog__options {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 18px;
  align-items: stretch;
  padding: 26px 28px 28px;
}
.game-room-dialog__options > span {
  align-self: center;
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 700;
}
article {
  display: grid;
  align-content: start;
  gap: 10px;
  padding: 20px;
  border: 1px solid var(--color-line);
  border-radius: 16px;
  background: var(--color-surface-soft);
}
.game-room-dialog__options article:first-child {
  border-color: #ddd9ff;
  background: #faf9ff;
}
.game-room-dialog__options article:last-child {
  border-color: #d5eadf;
  background: #f7fcf9;
}
.game-room-dialog__options > span {
  z-index: 1;
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border: 1px solid var(--color-line);
  border-radius: 50%;
  background: #fff;
}
article > b {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 50%;
  color: var(--color-accent-blue);
  background: var(--color-blue-soft);
}
article > b svg {
  width: 18px;
  height: 18px;
}
.game-room-dialog__options article:last-child > b {
  color: #318b57;
  background: #e8f7ee;
}
.game-room-dialog__options article:first-child h3 {
  color: #5a55dc;
}
.game-room-dialog__options article:last-child h3,
.game-room-dialog__options article:last-child label {
  color: #318b57;
}
h3 {
  margin: 2px 0;
  color: var(--color-ink);
  font-size: 18px;
}
label {
  color: var(--color-ink);
  font-size: 12px;
  font-weight: 800;
}
input {
  box-sizing: border-box;
  width: 100%;
  padding: 11px 12px;
  border: 1px solid var(--color-line);
  border-radius: 10px;
  background: #fff;
  font: inherit;
}
.error {
  color: #b64758 !important;
  font-size: 12px !important;
}
.primary,
.secondary {
  min-height: 43px;
  margin-top: auto;
  border-radius: 10px;
  font-weight: 800;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    filter var(--duration-fast) ease,
    color var(--duration-fast) ease;
}
.primary {
  border: 1px solid var(--color-accent-blue);
  color: #fff;
  background: var(--color-accent-blue);
}
.secondary {
  border: 1px solid var(--color-accent-blue);
  color: var(--color-accent-blue);
  background: #fff;
}
.game-room-dialog__options article:last-child .secondary {
  border-color: #319253;
  color: #fff;
  background: #319253;
}
.primary:hover {
  filter: brightness(0.96);
}
.secondary:hover {
  background: var(--color-blue-soft);
}
.game-room-dialog__options article:last-child .secondary:hover {
  background: #267a43;
}
button:focus-visible,
input:focus-visible {
  outline: 2px solid var(--color-accent-blue);
  outline-offset: 2px;
}
.game-room-dialog__matching {
  display: grid;
  justify-items: center;
  gap: 12px;
  padding: 38px 28px 30px;
  text-align: center;
}
.game-room-dialog__matching i {
  width: 60px;
  height: 60px;
  border: 4px solid var(--color-blue-soft);
  border-top-color: var(--color-accent-blue);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}
.game-room-dialog__matching strong {
  color: var(--color-ink);
  font-size: 21px;
}
.game-room-dialog__matching p,
.game-room-dialog__matching small {
  margin: 0;
  color: var(--color-muted);
}
.game-room-dialog__matching p b {
  color: var(--color-accent-blue);
}
.game-room-dialog__matching button {
  width: min(100%, 330px);
  margin-top: 9px;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 640px) {
  .game-room-dialog-backdrop {
    padding: 16px;
  }
  .game-room-dialog {
    max-height: calc(100vh - 32px);
  }
  header,
  .game-room-dialog__options {
    padding-right: 20px;
    padding-left: 20px;
  }
  .game-room-dialog__options {
    grid-template-columns: 1fr;
  }
  .game-room-dialog__options > span {
    justify-self: center;
  }
}
</style>
