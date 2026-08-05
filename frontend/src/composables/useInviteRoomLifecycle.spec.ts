import { describe, expect, it } from 'vitest'
import {
  resolveInviteRoomFailure,
  useInviteRoomLifecycle,
} from './useInviteRoomLifecycle'
import type {
  RoomRole,
  WaitingRoomParticipant,
  WaitingRoomStateData,
} from '../types/waitingRoom'

function participant(
  participantKey: string,
  roomRole: RoomRole,
): WaitingRoomParticipant {
  return {
    participantKey,
    displayName: participantKey,
    roomRole,
    slotNo: roomRole === 'HOST' ? 1 : 2,
    isReady: false,
    calibrationStatus: 'PENDING',
    joinedAt: '2026-08-05T00:00:00Z',
  }
}

function inviteState(
  participants: WaitingRoomParticipant[],
  overrides: Partial<WaitingRoomStateData> = {},
): WaitingRoomStateData {
  return {
    roomId: 'room-1',
    roomType: 'INVITE',
    gameName: 'EYEFIGHT',
    roomCode: '1234',
    roomStatus: 'WAITING',
    countdownEndsAt: null,
    participants,
    createdAt: '2026-08-05T00:00:00Z',
    ...overrides,
  }
}

function connectLifecycle() {
  const lifecycle = useInviteRoomLifecycle()
  const requestGeneration = lifecycle.beginEntry('EYEFIGHT')
  expect(requestGeneration).toBe(1)
  expect(lifecycle.markConnecting(1, 'room-1', 'EYEFIGHT')).toBe(true)
  expect(lifecycle.bindWaitingConnection('room-1', 3)).toBe(true)
  return lifecycle
}

describe('useInviteRoomLifecycle', () => {
  it('moves ENTERING to CONNECTING and waits for a valid first ROOM_STATE before JOINED', () => {
    const lifecycle = useInviteRoomLifecycle()
    const generation = lifecycle.beginEntry('EYEFIGHT')

    expect(lifecycle.flowState.value).toBe('ENTERING')
    expect(generation).toBe(1)
    expect(lifecycle.markConnecting(1, 'room-1', 'EYEFIGHT')).toBe(true)
    expect(lifecycle.flowState.value).toBe('CONNECTING')

    lifecycle.bindWaitingConnection('room-1', 2)
    expect(
      lifecycle.handleRoomState(
        inviteState([
          participant('GUEST:me', 'HOST'),
          participant('GUEST:other', 'PLAYER'),
        ]),
        'GUEST:me',
        { roomId: 'room-1', generation: 2 },
      ),
    ).toBe('JOINED')
    expect(lifecycle.flowState.value).toBe('JOINED')
    expect(lifecycle.myRoomRole.value).toBe('HOST')
  })

  it('blocks duplicate create/join requests and ignores stale REST responses', () => {
    const lifecycle = useInviteRoomLifecycle()
    const first = lifecycle.beginEntry('EYEFIGHT')

    expect(first).toBe(1)
    expect(lifecycle.beginEntry('EYEFIGHT')).toBeNull()
    lifecycle.beginLeaving()
    expect(lifecycle.markConnecting(1, 'stale-room', 'EYEFIGHT')).toBe(false)
    expect(lifecycle.flowState.value).toBe('LEAVING')
  })

  it('ignores stale connection events but fails mismatched snapshots from the active socket', () => {
    const lifecycle = connectLifecycle()
    const me = participant('USER:7', 'PLAYER')

    expect(
      lifecycle.handleRoomState(inviteState([me]), 'USER:7', {
        roomId: 'room-1',
        generation: 2,
      }),
    ).toBe('IGNORED')
    expect(lifecycle.flowState.value).toBe('CONNECTING')

    expect(
      lifecycle.handleRoomState(
        inviteState([me], { roomId: 'room-2' }),
        'USER:7',
        { roomId: 'room-1', generation: 3 },
      ),
    ).toBe('FAILED')
    expect(lifecycle.flowState.value).toBe('FAILED')
    expect(lifecycle.failure.value?.code).toBe('WAITING-003')
  })

  it.each([{ gameName: 'BLINK' as const }, { roomType: 'RANDOM' as const }])(
    'fails an active snapshot with invalid metadata: %o',
    (overrides) => {
      const lifecycle = connectLifecycle()
      expect(
        lifecycle.handleRoomState(
          inviteState([participant('USER:7', 'PLAYER')], overrides),
          'USER:7',
          { roomId: 'room-1', generation: 3 },
        ),
      ).toBe('FAILED')
      expect(lifecycle.flowState.value).toBe('FAILED')
    },
  )

  it('fails a current snapshot without my participant or with an unknown role', () => {
    const missing = connectLifecycle()
    expect(
      missing.handleRoomState(inviteState([]), 'USER:7', {
        roomId: 'room-1',
        generation: 3,
      }),
    ).toBe('FAILED')
    expect(missing.failure.value?.code).toBe('WAITING-009')

    const unknownRole = connectLifecycle()
    expect(
      unknownRole.handleRoomState(
        inviteState([
          { ...participant('USER:7', 'HOST'), roomRole: 'OWNER' as RoomRole },
        ]),
        'USER:7',
        { roomId: 'room-1', generation: 3 },
      ),
    ).toBe('FAILED')
    expect(unknownRole.myRoomRole.value).toBeNull()
  })

  it('derives USER and GUEST roles by participantKey regardless of participant order and resynchronizes them', () => {
    const lifecycle = connectLifecycle()
    const context = { roomId: 'room-1', generation: 3 }

    lifecycle.handleRoomState(
      inviteState([
        participant('GUEST:other', 'PLAYER'),
        participant('USER:7', 'HOST'),
      ]),
      'USER:7',
      context,
    )
    expect(lifecycle.myRoomRole.value).toBe('HOST')
    expect(lifecycle.opponents.value[0]?.participantKey).toBe('GUEST:other')

    lifecycle.handleRoomState(
      inviteState([
        participant('USER:7', 'PLAYER'),
        participant('GUEST:other', 'HOST'),
      ]),
      'USER:7',
      context,
    )
    expect(lifecycle.myRoomRole.value).toBe('PLAYER')
    expect(lifecycle.opponents.value[0]?.roomRole).toBe('HOST')
  })

  it('moves current CLOSED to CLOSED and ignores every later event', () => {
    const lifecycle = connectLifecycle()
    const context = { roomId: 'room-1', generation: 3 }
    const me = participant('GUEST:me', 'HOST')

    expect(
      lifecycle.handleRoomState(
        inviteState([me], { roomStatus: 'CLOSED' }),
        'GUEST:me',
        context,
      ),
    ).toBe('CLOSED')
    expect(lifecycle.flowState.value).toBe('CLOSED')
    expect(
      lifecycle.handleRoomState(inviteState([me]), 'GUEST:me', context),
    ).toBe('IGNORED')
  })

  it('retries an established room connection without starting another entry request', () => {
    const lifecycle = connectLifecycle()
    expect(
      lifecycle.failConnection(
        { roomId: 'room-1', generation: 3 },
        'WAITING-003',
      ),
    ).toBe(true)

    const retryGeneration = lifecycle.retryConnection()
    expect(retryGeneration).toBe(2)
    expect(lifecycle.flowState.value).toBe('CONNECTING')
    expect(lifecycle.activeRoomId.value).toBe('room-1')
    expect(lifecycle.failure.value).toBeNull()
    expect(lifecycle.bindWaitingConnection('room-1', 4)).toBe(true)
  })

  it('allows entry errors only while CONNECTING and keeps JOINED command errors non-terminal', () => {
    const lifecycle = connectLifecycle()
    const context = { roomId: 'room-1', generation: 3 }
    const state = inviteState([participant('GUEST:me', 'HOST')])

    lifecycle.handleRoomState(state, 'GUEST:me', context)
    expect(
      lifecycle.failEntryConnection(
        context,
        'WAITING-014',
        '현재 상태에서는 요청한 상태로 변경할 수 없습니다.',
      ),
    ).toBe(false)
    expect(lifecycle.flowState.value).toBe('JOINED')
    expect(lifecycle.activeRoomId.value).toBe('room-1')
  })

  it.each([
    ['COMMON-001', '숫자 4자리 초대 코드를 입력해 주세요.', false],
    ['WAITING-003', '초대방 정보를 불러오지 못했어요.', true],
    ['WAITING-004', '존재하지 않거나 만료된 초대 코드예요.', false],
    ['WAITING-005', '초대방이 가득 찼어요.', false],
    [
      'WAITING-006',
      '이미 참여 중인 방이에요. 기존 화면을 확인해 주세요.',
      false,
    ],
    ['WAITING-007', '이미 시작되었거나 입장할 수 없는 방이에요.', false],
    ['WAITING-008', '종료되었거나 존재하지 않는 방이에요.', false],
    ['WAITING-009', '대기방 참가 정보를 확인할 수 없어요.', false],
    ['WAITING-010', '다른 화면에서 이미 연결된 방이에요.', false],
  ])('maps %s to its message and retry policy', (code, message, retryable) => {
    expect(resolveInviteRoomFailure(code)).toEqual({
      code,
      message,
      retryable,
    })
  })

  it('keeps a network failure retryable and uses its safe fallback message', () => {
    expect(resolveInviteRoomFailure('UNKNOWN')).toEqual({
      code: 'UNKNOWN',
      message: '초대방에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.',
      retryable: true,
    })
  })
})
