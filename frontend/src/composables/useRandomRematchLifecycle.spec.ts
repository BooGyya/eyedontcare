import { describe, expect, it } from 'vitest'
import { useRandomRematchLifecycle } from './useRandomRematchLifecycle'
import type { WaitingRoomStateData } from '../types/waitingRoom'

function roomState(
  roomId: string,
  roomStatus: WaitingRoomStateData['roomStatus'],
): WaitingRoomStateData {
  return {
    roomId,
    roomType: 'RANDOM',
    gameName: 'EYEFIGHT',
    roomCode: '',
    roomStatus,
    countdownEndsAt: roomStatus === 'COUNTDOWN' ? '2026-08-05T00:00:03Z' : null,
    participants: [],
    createdAt: '2026-08-05T00:00:00Z',
  }
}

describe('useRandomRematchLifecycle', () => {
  it('moves only the active RANDOM room CLOSED event to REMATCHING', () => {
    const lifecycle = useRandomRematchLifecycle()
    lifecycle.initialize('room-1', 'EYEFIGHT')

    expect(lifecycle.handleRoomState(roomState('old-room', 'CLOSED'))).toBe(
      'IGNORED',
    )
    expect(lifecycle.flowState.value).toBe('CONNECTING_ROOM')
    expect(lifecycle.handleRoomState(roomState('room-1', 'CLOSED'))).toBe(
      'REMATCHING',
    )
    expect(lifecycle.flowState.value).toBe('REMATCHING')
    expect(lifecycle.matchmakingPending.value).toBe(true)
    expect(lifecycle.handleRoomState(roomState('room-1', 'CLOSED'))).toBe(
      'IGNORED',
    )
  })

  it('does not enter REMATCHING from MATCH_REQUEUED alone', () => {
    const lifecycle = useRandomRematchLifecycle()
    lifecycle.initialize('room-1', 'EYEFIGHT')

    expect(lifecycle.handleRequeued('EYEFIGHT')).toBe(false)
    expect(lifecycle.flowState.value).toBe('CONNECTING_ROOM')

    lifecycle.handleRoomState(roomState('room-1', 'CLOSED'))
    expect(lifecycle.handleRequeued('BLINK')).toBe(false)
    expect(lifecycle.handleRequeued('EYEFIGHT')).toBe(true)
    expect(lifecycle.flowState.value).toBe('REMATCHING')
  })

  it('accepts a different room MATCH_SUCCESS and ignores the same room', () => {
    const lifecycle = useRandomRematchLifecycle()
    lifecycle.initialize('room-1', 'EYEFIGHT')
    lifecycle.handleRoomState(roomState('room-1', 'CLOSED'))

    expect(lifecycle.handleMatchSuccess('room-1', 'EYEFIGHT')).toBeNull()
    expect(lifecycle.handleMatchSuccess('room-2', 'BLINK')).toBeNull()
    expect(lifecycle.handleMatchSuccess('room-2', 'EYEFIGHT')).toEqual({
      roomId: 'room-2',
      generation: 2,
    })
    expect(lifecycle.flowState.value).toBe('CONNECTING_NEW_ROOM')
  })

  it('adopts B while A is connecting and invalidates A generation', () => {
    const lifecycle = useRandomRematchLifecycle()
    lifecycle.initialize('room-1', 'EYEFIGHT')
    lifecycle.handleRoomState(roomState('room-1', 'CLOSED'))
    const roomA = lifecycle.handleMatchSuccess('room-a', 'EYEFIGHT')
    const roomB = lifecycle.handleMatchSuccess('room-b', 'EYEFIGHT')

    expect(roomA).toEqual({ roomId: 'room-a', generation: 2 })
    expect(roomB).toEqual({ roomId: 'room-b', generation: 3 })
    expect(lifecycle.isCurrentGeneration(roomA?.generation ?? 0)).toBe(false)
    expect(lifecycle.isCurrentGeneration(roomB?.generation ?? 0)).toBe(true)
    expect(lifecycle.activeRoomId.value).toBe('room-b')
  })

  it('accepts only current room state and game start after room switching', () => {
    const lifecycle = useRandomRematchLifecycle()
    lifecycle.initialize('room-1', 'EYEFIGHT')
    lifecycle.handleRoomState(roomState('room-1', 'CLOSED'))
    lifecycle.handleMatchSuccess('room-2', 'EYEFIGHT')

    expect(lifecycle.handleRoomState(roomState('room-1', 'WAITING'))).toBe(
      'IGNORED',
    )
    expect(lifecycle.canHandleGameStart('room-1')).toBe(false)
    expect(lifecycle.handleRoomState(roomState('room-2', 'WAITING'))).toBe(
      'WAITING_ROOM',
    )
    expect(lifecycle.canHandleGameStart('room-2')).toBe(true)
  })

  it('blocks room and match events while leaving', () => {
    const lifecycle = useRandomRematchLifecycle()
    lifecycle.initialize('room-1', 'EYEFIGHT')
    lifecycle.handleRoomState(roomState('room-1', 'CLOSED'))
    lifecycle.beginLeaving()

    expect(lifecycle.handleRequeued('EYEFIGHT')).toBe(false)
    expect(lifecycle.handleMatchSuccess('room-2', 'EYEFIGHT')).toBeNull()
    expect(lifecycle.handleRoomState(roomState('room-1', 'WAITING'))).toBe(
      'IGNORED',
    )
    expect(lifecycle.canHandleGameStart('room-1')).toBe(false)
    expect(lifecycle.flowState.value).toBe('LEAVING')
  })
})
