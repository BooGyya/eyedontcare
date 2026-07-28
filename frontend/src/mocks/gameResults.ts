import type { GameResultDetail } from '../types/gameResult'

export const gameResultRecords: GameResultDetail[] = [
  {
    resultId: 5001,
    gameName: 'EYEFIGHT',
    playMode: 'MULTI',
    difficulty: null,
    startedAt: '2026-07-24T09:00:00',
    endedAt: '2026-07-24T09:03:00',
    participants: [
      {
        slotNo: 1,
        participantType: 'USER',
        displayName: '눈싸움장인',
        outcome: 'WIN',
        rank: 1,
        score: 5,
      },
      {
        slotNo: 2,
        participantType: 'USER',
        displayName: '반짝콩',
        outcome: 'LOSE',
        rank: 2,
        score: 3,
      },
    ],
    gameResult: { 1: { survivalTimeMs: 180000 } },
  },
  {
    resultId: 5002,
    gameName: '눈으로 그리기',
    playMode: 'SINGLE',
    difficulty: 2,
    startedAt: '2026-07-23T20:40:00',
    endedAt: '2026-07-23T20:42:18',
    participants: [
      {
        slotNo: 1,
        participantType: 'USER',
        displayName: '눈싸움장인',
        outcome: 'COMPLETED',
        rank: 2,
        score: 420,
      },
    ],
    gameResult: { 1: { survivalTimeMs: 138000 } },
  },
  {
    resultId: 5003,
    gameName: '눈싸움',
    playMode: 'MULTI',
    difficulty: null,
    startedAt: '2026-07-22T10:12:00',
    endedAt: '2026-07-22T10:14:11',
    participants: [
      {
        slotNo: 1,
        participantType: 'USER',
        displayName: '눈싸움장인',
        outcome: 'LOSE',
        rank: 4,
        score: 2,
      },
    ],
    gameResult: { 1: { survivalTimeMs: 131000 } },
  },
]
