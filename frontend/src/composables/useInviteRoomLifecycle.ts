import { computed, readonly, ref } from 'vue'
import type {
  GameName,
  RoomRole,
  WaitingRoomParticipant,
  WaitingRoomStateData,
} from '../types/waitingRoom'
import type { WaitingRoomSocketContext } from './useWaitingRoomSocket'

export type InviteRoomFlowState =
  'ENTERING' | 'CONNECTING' | 'JOINED' | 'FAILED' | 'LEAVING' | 'CLOSED'

export type InviteRoomStateTransition =
  'IGNORED' | 'JOINED' | 'UPDATED' | 'FAILED' | 'CLOSED'

export interface InviteRoomFailure {
  code: string
  message: string
  retryable: boolean
}

const INVITE_FAILURES: Record<string, InviteRoomFailure> = {
  'COMMON-001': {
    code: 'COMMON-001',
    message: '숫자 4자리 초대 코드를 입력해 주세요.',
    retryable: false,
  },
  'WAITING-003': {
    code: 'WAITING-003',
    message: '초대방 정보를 불러오지 못했어요.',
    retryable: true,
  },
  'WAITING-004': {
    code: 'WAITING-004',
    message: '존재하지 않거나 만료된 초대 코드예요.',
    retryable: false,
  },
  'WAITING-005': {
    code: 'WAITING-005',
    message: '초대방이 가득 찼어요.',
    retryable: false,
  },
  'WAITING-006': {
    code: 'WAITING-006',
    message: '이미 참여 중인 방이에요. 기존 화면을 확인해 주세요.',
    retryable: false,
  },
  'WAITING-007': {
    code: 'WAITING-007',
    message: '이미 시작되었거나 입장할 수 없는 방이에요.',
    retryable: false,
  },
  'WAITING-008': {
    code: 'WAITING-008',
    message: '종료되었거나 존재하지 않는 방이에요.',
    retryable: false,
  },
  'WAITING-009': {
    code: 'WAITING-009',
    message: '대기방 참가 정보를 확인할 수 없어요.',
    retryable: false,
  },
  'WAITING-010': {
    code: 'WAITING-010',
    message: '다른 화면에서 이미 연결된 방이에요.',
    retryable: false,
  },
}

export function resolveInviteRoomFailure(
  code: string,
  fallbackMessage?: string,
): InviteRoomFailure {
  return (
    INVITE_FAILURES[code] ?? {
      code,
      message:
        fallbackMessage?.trim() ||
        '초대방에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.',
      retryable: true,
    }
  )
}

export function useInviteRoomLifecycle() {
  const flowState = ref<InviteRoomFlowState>('ENTERING')
  const requestGeneration = ref(0)
  const activeRoomId = ref<string | null>(null)
  const activeGameName = ref<GameName | null>(null)
  const waitingConnectionGeneration = ref<number | null>(null)
  const currentParticipant = ref<WaitingRoomParticipant | null>(null)
  const opponents = ref<WaitingRoomParticipant[]>([])
  const failure = ref<InviteRoomFailure | null>(null)
  let entryRequestPending = false

  const myRoomRole = computed<RoomRole | null>(
    () => currentParticipant.value?.roomRole ?? null,
  )

  function clearParticipantState(): void {
    currentParticipant.value = null
    opponents.value = []
  }

  function beginEntry(gameName: GameName): number | null {
    if (
      entryRequestPending ||
      ['CONNECTING', 'JOINED', 'LEAVING', 'CLOSED'].includes(flowState.value)
    ) {
      return null
    }

    requestGeneration.value += 1
    entryRequestPending = true
    flowState.value = 'ENTERING'
    activeRoomId.value = null
    activeGameName.value = gameName
    waitingConnectionGeneration.value = null
    failure.value = null
    clearParticipantState()
    return requestGeneration.value
  }

  function isCurrentRequest(generation: number): boolean {
    return (
      requestGeneration.value === generation &&
      flowState.value !== 'LEAVING' &&
      flowState.value !== 'CLOSED'
    )
  }

  function markConnecting(
    generation: number,
    roomId: string,
    gameName: GameName,
  ): boolean {
    if (!isCurrentRequest(generation) || flowState.value !== 'ENTERING') {
      return false
    }
    entryRequestPending = false
    activeRoomId.value = roomId
    activeGameName.value = gameName
    flowState.value = 'CONNECTING'
    return true
  }

  function bindWaitingConnection(roomId: string, generation: number): boolean {
    if (
      roomId !== activeRoomId.value ||
      !['CONNECTING', 'JOINED'].includes(flowState.value)
    ) {
      return false
    }
    waitingConnectionGeneration.value = generation
    return true
  }

  function isCurrentConnection(context: WaitingRoomSocketContext): boolean {
    return (
      context.roomId === activeRoomId.value &&
      context.generation === waitingConnectionGeneration.value
    )
  }

  function applyFailure(code: string, fallbackMessage?: string): void {
    entryRequestPending = false
    flowState.value = 'FAILED'
    failure.value = resolveInviteRoomFailure(code, fallbackMessage)
    waitingConnectionGeneration.value = null
    clearParticipantState()
  }

  function failRequest(
    generation: number,
    code: string,
    fallbackMessage?: string,
  ): boolean {
    if (!isCurrentRequest(generation)) return false
    applyFailure(code, fallbackMessage)
    return true
  }

  function failConnection(
    context: WaitingRoomSocketContext,
    code: string,
    fallbackMessage?: string,
  ): boolean {
    if (!isCurrentConnection(context)) return false
    applyFailure(code, fallbackMessage)
    return true
  }

  function failEntryConnection(
    context: WaitingRoomSocketContext,
    code: string,
    fallbackMessage?: string,
  ): boolean {
    if (flowState.value !== 'CONNECTING') return false
    return failConnection(context, code, fallbackMessage)
  }

  function handleRoomState(
    state: WaitingRoomStateData,
    participantKey: string | null,
    context: WaitingRoomSocketContext,
  ): InviteRoomStateTransition {
    if (
      !isCurrentConnection(context) ||
      !['CONNECTING', 'JOINED'].includes(flowState.value)
    ) {
      return 'IGNORED'
    }

    if (
      state.roomId !== activeRoomId.value ||
      state.roomType !== 'INVITE' ||
      state.gameName !== activeGameName.value
    ) {
      applyFailure('WAITING-003')
      return 'FAILED'
    }

    if (state.roomStatus === 'CLOSED') {
      flowState.value = 'CLOSED'
      waitingConnectionGeneration.value = null
      failure.value = null
      clearParticipantState()
      return 'CLOSED'
    }

    if (!['WAITING', 'COUNTDOWN'].includes(state.roomStatus)) {
      applyFailure('WAITING-007')
      return 'FAILED'
    }

    const participant = participantKey
      ? state.participants.find(
          (candidate) => candidate.participantKey === participantKey,
        )
      : undefined
    if (
      !participant ||
      (participant.roomRole !== 'HOST' && participant.roomRole !== 'PLAYER')
    ) {
      applyFailure('WAITING-009')
      return 'FAILED'
    }

    const wasConnecting = flowState.value === 'CONNECTING'
    currentParticipant.value = participant
    opponents.value = state.participants.filter(
      (candidate) => candidate.participantKey !== participantKey,
    )
    flowState.value = 'JOINED'
    failure.value = null
    return wasConnecting ? 'JOINED' : 'UPDATED'
  }

  function beginLeaving(): boolean {
    if (flowState.value === 'LEAVING') return false
    requestGeneration.value += 1
    entryRequestPending = false
    flowState.value = 'LEAVING'
    waitingConnectionGeneration.value = null
    clearParticipantState()
    return true
  }

  function retryConnection(): number | null {
    if (
      flowState.value !== 'FAILED' ||
      !activeRoomId.value ||
      !activeGameName.value
    ) {
      return null
    }

    requestGeneration.value += 1
    entryRequestPending = false
    flowState.value = 'CONNECTING'
    waitingConnectionGeneration.value = null
    failure.value = null
    clearParticipantState()
    return requestGeneration.value
  }

  function invalidateRequest(): void {
    requestGeneration.value += 1
    entryRequestPending = false
  }

  return {
    flowState: readonly(flowState),
    requestGeneration: readonly(requestGeneration),
    activeRoomId: readonly(activeRoomId),
    activeGameName: readonly(activeGameName),
    currentParticipant: readonly(currentParticipant),
    opponents: readonly(opponents),
    myRoomRole,
    failure: readonly(failure),
    beginEntry,
    isCurrentRequest,
    markConnecting,
    bindWaitingConnection,
    isCurrentConnection,
    failRequest,
    failConnection,
    failEntryConnection,
    handleRoomState,
    retryConnection,
    beginLeaving,
    invalidateRequest,
  }
}
