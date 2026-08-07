/**
 * 게임별 랭킹 REST 호출 + 백엔드 응답 → 화면용 모델 변환.
 *
 * 백엔드(`GET /api/v1/rankings/{gameName}`)는 게임별 전체 랭킹을 페이지네이션으로 준다. 응답은
 * 순위/유저ID/닉네임/점수(value)/단위 코드만 담고, 아바타·순위변동(trend)은 주지 않는다. 화면
 * 모델({@link GameRanking})은 아바타와 표시용 점수 문자열을 요구하므로 여기서 보강한다.
 *   - 아바타: 응답에 없으므로 userId 기준으로 프로필 이미지 풀에서 결정적으로 배정한다(같은
 *     유저는 항상 같은 아바타). 본인 행은 화면단에서 실제 프로필로 덮어쓴다.
 *   - trend: 백엔드에 순위 변동 데이터가 없어 생략한다 → 화면에서 "유지"로 표시된다.
 *   - 점수: `value`(정수) + 단위 코드(count/point/win/second)를 "1,234점"처럼 포맷한다.
 *
 * 게임별 상세 랭킹(`/rankings/{gameName}`)은 인증 사용자 전용이다(백엔드가 요청자 userId로 내
 * 순위를 계산). 게스트는 호출하지 않고 화면에서 로그인 유도로 처리한다.
 * 홈 요약(`GET /rankings`)은 게스트도 호출할 수 있다 — 로그인하지 않았으면 내 순위(myRank)만 없다.
 */
import { apiRequest } from './http'
import { PROFILE_OPTIONS } from './user'
import {
  GAME_DISPLAY_NAME,
  GAME_ENGLISH_NAME,
  type GameName,
} from '../types/waitingRoom'
import type { GameId, GameRanking, RankingPlayer } from '../types/pages'
import type {
  RankingRecord,
  WeeklyRankingGame,
  WeeklyRankingGamePreset,
} from '../types/home'

export type RankType = 'WIN_COUNT' | 'BEST_SCORE'

/** 랭킹 한 줄(백엔드 원본). `achievedAt`은 상세 조회에만, `trend`는 변동 기록이 있을 때만 온다. */
export interface RankingEntryResponse {
  rank: number
  userId: number | null
  nickname: string
  value: number
  achievedAt?: string | null
  trend?: 'up' | 'down' | 'same' | null
}

/** 요청자 본인 순위. 이번 주 기록이 없으면 백엔드가 null로 준다. */
export interface MyRankResponse {
  rank: number
  value: number
}

/** `GET /api/v1/rankings/{gameName}` 응답(백엔드 원본). */
export interface GameRankingResponse {
  gameName: GameName
  rankType: RankType
  unit: string
  period: string
  weekStart: string
  rankings: RankingEntryResponse[]
  myRank: MyRankResponse | null
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** 홈 요약의 게임 한 개 랭킹. `top`은 상위 N명(기본 3). */
export interface GameRankingSummaryResponse {
  gameName: GameName
  rankType: RankType
  unit: string
  top: RankingEntryResponse[]
  myRank?: MyRankResponse | null
}

/** `GET /api/v1/rankings` 응답(홈 요약). 게스트 호출 가능(내 순위는 로그인 시에만). */
export interface RankingSummaryResponse {
  period: string
  weekStart: string
  games: GameRankingSummaryResponse[]
}

/** 백엔드 `GameName` enum → 프론트 게임 id. */
export const GAME_ID_BY_NAME: Record<GameName, GameId> = {
  HOCKEY: 'air',
  EYEFIGHT: 'hold',
  DRAWING: 'draw',
  RHYTHM: 'rhythm',
  BLINK: 'blink',
}

/** 랭킹 단위 코드 → 화면 표시 단위. 알 수 없는 코드는 단위 없이 숫자만 보여준다. */
const UNIT_LABEL: Record<string, string> = {
  count: '회',
  point: '점',
  win: '승',
  second: '초',
}

function formatScore(value: number, unitCode: string): string {
  return `${value.toLocaleString('ko-KR')}${UNIT_LABEL[unitCode] ?? ''}`
}

/**
 * userId로 프로필 이미지 풀에서 아바타를 결정적으로 고른다. userId가 없으면(비정상) 첫 번째를 쓴다.
 * 응답에 아바타가 없어 넣는 대체값이므로, 본인 행은 화면단에서 실제 프로필로 교체된다.
 */
export function avatarForUserId(userId: number | null): string {
  const pool = PROFILE_OPTIONS
  if (userId === null) return pool[0].image
  return pool[userId % pool.length].image
}

export async function getGameRanking(
  gameName: GameName,
  page = 1,
  size = 10,
): Promise<GameRankingResponse> {
  return apiRequest<GameRankingResponse>(
    `/rankings/${gameName}?page=${page}&size=${size}`,
  )
}

/** 홈 요약 랭킹 조회. 게스트도 호출할 수 있다(로그인 시에만 게임별 myRank가 채워진다). */
export async function getRankingSummary(
  limit = 3,
): Promise<RankingSummaryResponse> {
  return apiRequest<RankingSummaryResponse>(`/rankings?limit=${limit}`)
}

/** 백엔드 `GameName` enum → 홈 랭킹 카드 id(`{@link WeeklyRankingGame.id}`). */
const WEEKLY_GAME_ID_BY_NAME: Record<GameName, WeeklyRankingGame['id']> = {
  HOCKEY: 'air',
  EYEFIGHT: 'stare',
  DRAWING: 'draw',
  RHYTHM: 'challenge',
  BLINK: 'blink',
}

/** 홈 요약의 `top` 목록을 카드 포디움용 {@link RankingRecord} 배열로 변환한다(순위 오름차순 유지). */
export function toWeeklyRecords(
  top: RankingEntryResponse[],
  unit: string,
): RankingRecord[] {
  return top.map((entry) => ({
    rank: entry.rank,
    value: entry.value,
    label: formatScore(entry.value, unit),
    nickname: entry.nickname,
    avatar: avatarForUserId(entry.userId),
  }))
}

/**
 * 홈 랭킹 카드 프리셋에 요약 응답을 합쳐 화면용 {@link WeeklyRankingGame} 목록을 만든다. 프리셋
 * 순서를 유지하고, 응답에 없는 게임은 빈 랭킹으로 남겨 카드 5개가 항상 렌더링되게 한다.
 */
export function toWeeklyRankingGames(
  presets: WeeklyRankingGamePreset[],
  summary: RankingSummaryResponse,
): WeeklyRankingGame[] {
  const gamesById = new Map(
    summary.games.map((game) => [WEEKLY_GAME_ID_BY_NAME[game.gameName], game]),
  )

  return presets.map((preset) => {
    const game = gamesById.get(preset.id)
    if (!game) {
      return { ...preset, records: [], myRank: 0 }
    }
    return {
      ...preset,
      records: toWeeklyRecords(game.top, game.unit),
      myRank: game.myRank?.rank ?? 0,
    }
  })
}

/**
 * 백엔드 응답을 화면용 {@link GameRanking}으로 변환한다. `currentUserId`(로그인 사용자 id)와 같은
 * 행에 `isCurrentUser`를 표시해, 화면에서 실제 닉네임/프로필로 강조·교체할 수 있게 한다.
 */
export function toGameRanking(
  response: GameRankingResponse,
  currentUserId: number | null,
): GameRanking {
  const unitLabel = UNIT_LABEL[response.unit] ?? ''
  const players: RankingPlayer[] = response.rankings.map((entry) => ({
    rank: entry.rank,
    nickname: entry.nickname,
    score: formatScore(entry.value, response.unit),
    avatar: avatarForUserId(entry.userId),
    isCurrentUser: currentUserId !== null && entry.userId === currentUserId,
    trend: entry.trend ?? undefined,
  }))

  return {
    gameId: GAME_ID_BY_NAME[response.gameName],
    gameName: `${GAME_ENGLISH_NAME[response.gameName]}(${GAME_DISPLAY_NAME[response.gameName]})`,
    unit: unitLabel,
    sortOrder: 'desc',
    players,
    myRank: response.myRank?.rank ?? 0,
    myScore: response.myRank
      ? formatScore(response.myRank.value, response.unit)
      : '-',
    totalPlayers: response.totalElements,
  }
}
