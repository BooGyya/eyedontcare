import type {
  GameResult,
  GameSession,
  GameSessionMode,
} from '../types/gameplay'
import type { GameDetailId } from '../types/game-detail'

export const gameModeLabels: Record<GameSessionMode, string> = {
  solo: '솔로 모드',
  ai: 'AI 대결',
  friends: '친구와 대결',
  random: '랜덤 매칭',
}

const sessions: Record<GameDetailId, Omit<GameSession, 'mode' | 'roomCode'>> = {
  draw: {
    gameId: 'draw',
    round: 2,
    totalRounds: 3,
    score: 185,
    timeLabel: '01:12',
  },
  rhythm: {
    gameId: 'rhythm',
    round: 1,
    totalRounds: 1,
    score: 1240,
    timeLabel: '00:18',
  },
  blink: {
    gameId: 'blink',
    round: 1,
    totalRounds: 1,
    score: 28,
    timeLabel: '00:12',
  },
  hold: {
    gameId: 'hold',
    round: 1,
    totalRounds: 1,
    score: 18,
    timeLabel: '00:18.6',
  },
  air: {
    gameId: 'air',
    round: 1,
    totalRounds: 1,
    score: 3,
    opponentScore: 2,
    timeLabel: '00:34',
  },
}

const results: Record<GameDetailId, Omit<GameResult, 'gameId'>> = {
  draw: {
    headline: 'AI 채점 완료!',
    summary: '그림의 핵심 특징을 잘 표현했어요.',
    scoreLabel: '누적 점수',
    score: '278점',
    stats: [
      { label: 'Round 1', value: '92점' },
      { label: 'Round 2', value: '93점' },
      { label: 'AI Confidence', value: '93%' },
    ],
  },
  rhythm: {
    headline: 'RHYTHM CLEAR!',
    summary: '리듬을 놓치지 않고 끝까지 완주했어요.',
    scoreLabel: '최종 점수',
    score: '1,860점',
    stats: [
      { label: '최대 콤보', value: '24' },
      { label: '남은 하트', value: '3' },
      { label: '정확도', value: '91%' },
    ],
  },
  blink: {
    headline: '집중력 대성공!',
    summary: '20초 동안 정확하게 눈을 깜빡였어요.',
    scoreLabel: '깜빡임',
    score: '36회',
    stats: [
      { label: '정확도', value: '94%' },
      { label: '보너스', value: '+3회' },
      { label: '플레이 시간', value: '00:20' },
    ],
  },
  hold: {
    headline: '기록 갱신!',
    summary: '시선을 끝까지 유지하며 새로운 기록을 만들었어요.',
    scoreLabel: '생존 시간',
    score: '28.4초',
    stats: [
      { label: '이전 최고', value: '25.1초' },
      { label: '기록 차이', value: '+3.3초' },
      { label: '랭킹 반영', value: '예정' },
    ],
  },
  air: {
    headline: '승리!',
    summary: '마지막 골까지 집중력을 유지했어요.',
    scoreLabel: '최종 스코어',
    score: '5',
    opponentScore: '3',
    stats: [
      { label: '득점', value: '5골' },
      { label: '막은 슛', value: '7회' },
      { label: '경기 시간', value: '01:00' },
    ],
  },
}

export function createMockSession(
  gameId: GameDetailId,
  mode: GameSessionMode,
  roomCode?: string,
): GameSession {
  return { ...sessions[gameId], mode, roomCode }
}

export function getMockResult(gameId: GameDetailId): GameResult {
  return { gameId, ...results[gameId] }
}
