import { computed, readonly, ref } from 'vue'
import type { GameName, WaitingRoomStateData } from '../types/waitingRoom'

export type RandomRoomFlowState =
  | 'CONNECTING_ROOM'
  | 'WAITING_ROOM'
  | 'COUNTDOWN'
  | 'REMATCHING'
  | 'CONNECTING_NEW_ROOM'
  | 'LEAVING'
  | 'ERROR'

export type RandomRoomStateTransition =
  'IGNORED' | 'WAITING_ROOM' | 'COUNTDOWN' | 'REMATCHING'

export interface RandomRoomSwitch {
  roomId: string
  generation: number
}

/** RANDOM 준비방의 화면 상태와 오래된 이벤트 수락 규칙만 관리한다. */
export function useRandomRematchLifecycle() {
  const flowState = ref<RandomRoomFlowState>('CONNECTING_ROOM')
  const activeRoomId = ref<string | null>(null)
  const roomGeneration = ref(0)
  const gameType = ref<GameName | null>(null)
  const errorMessage = ref<string | null>(null)
  const matchmakingPending = ref(false)

  const isLeaving = computed(() => flowState.value === 'LEAVING')
  const isSwitchingRoom = computed(
    () => flowState.value === 'CONNECTING_NEW_ROOM',
  )

  function initialize(roomId: string, nextGameType: GameName): number {
    roomGeneration.value += 1
    activeRoomId.value = roomId
    gameType.value = nextGameType
    errorMessage.value = null
    matchmakingPending.value = false
    flowState.value = 'CONNECTING_ROOM'
    return roomGeneration.value
  }

  function handleRoomState(
    state: WaitingRoomStateData,
  ): RandomRoomStateTransition {
    if (
      isLeaving.value ||
      state.roomId !== activeRoomId.value ||
      state.roomType !== 'RANDOM' ||
      state.gameName !== gameType.value
    ) {
      return 'IGNORED'
    }

    switch (state.roomStatus) {
      case 'WAITING':
        if (
          flowState.value !== 'CONNECTING_ROOM' &&
          flowState.value !== 'CONNECTING_NEW_ROOM' &&
          flowState.value !== 'WAITING_ROOM'
        ) {
          return 'IGNORED'
        }
        flowState.value = 'WAITING_ROOM'
        errorMessage.value = null
        matchmakingPending.value = false
        return 'WAITING_ROOM'
      case 'COUNTDOWN':
        if (
          flowState.value !== 'CONNECTING_ROOM' &&
          flowState.value !== 'CONNECTING_NEW_ROOM' &&
          flowState.value !== 'WAITING_ROOM' &&
          flowState.value !== 'COUNTDOWN'
        ) {
          return 'IGNORED'
        }
        flowState.value = 'COUNTDOWN'
        errorMessage.value = null
        matchmakingPending.value = false
        return 'COUNTDOWN'
      case 'CLOSED':
        if (
          flowState.value !== 'CONNECTING_ROOM' &&
          flowState.value !== 'CONNECTING_NEW_ROOM' &&
          flowState.value !== 'WAITING_ROOM' &&
          flowState.value !== 'COUNTDOWN'
        ) {
          return 'IGNORED'
        }
        flowState.value = 'REMATCHING'
        errorMessage.value = null
        matchmakingPending.value = true
        return 'REMATCHING'
      default:
        return 'IGNORED'
    }
  }

  function handleRequeued(eventGameType: GameName): boolean {
    return (
      flowState.value === 'REMATCHING' &&
      eventGameType === gameType.value &&
      !isLeaving.value
    )
  }

  function handleMatchSuccess(
    roomId: string,
    eventGameType: GameName,
  ): RandomRoomSwitch | null {
    if (
      (flowState.value !== 'REMATCHING' &&
        flowState.value !== 'CONNECTING_NEW_ROOM') ||
      isLeaving.value ||
      !roomId ||
      roomId === activeRoomId.value ||
      eventGameType !== gameType.value
    ) {
      return null
    }

    roomGeneration.value += 1
    activeRoomId.value = roomId
    errorMessage.value = null
    matchmakingPending.value = false
    flowState.value = 'CONNECTING_NEW_ROOM'
    return { roomId, generation: roomGeneration.value }
  }

  function isCurrentGeneration(generation: number): boolean {
    return generation === roomGeneration.value && !isLeaving.value
  }

  function canHandleGameStart(roomId: string): boolean {
    return (
      roomId === activeRoomId.value &&
      !isLeaving.value &&
      (flowState.value === 'WAITING_ROOM' || flowState.value === 'COUNTDOWN')
    )
  }

  function markRetryQueued(): void {
    if (isLeaving.value) return
    flowState.value = 'REMATCHING'
    errorMessage.value = null
    matchmakingPending.value = true
  }

  function fail(message: string): void {
    if (isLeaving.value) return
    flowState.value = 'ERROR'
    errorMessage.value = message
  }

  function beginLeaving(): void {
    flowState.value = 'LEAVING'
    errorMessage.value = null
  }

  return {
    flowState: readonly(flowState),
    activeRoomId: readonly(activeRoomId),
    roomGeneration: readonly(roomGeneration),
    gameType: readonly(gameType),
    errorMessage: readonly(errorMessage),
    isLeaving,
    isSwitchingRoom,
    matchmakingPending: readonly(matchmakingPending),
    initialize,
    handleRoomState,
    handleRequeued,
    handleMatchSuccess,
    isCurrentGeneration,
    canHandleGameStart,
    markRetryQueued,
    fail,
    beginLeaving,
  }
}
