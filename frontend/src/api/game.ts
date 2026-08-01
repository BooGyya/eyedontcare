/**
 * 게임 카탈로그 조회.
 *
 * 프론트의 게임 슬롯(gameName × playMode)을 백엔드 `gameId`(Long)로 옮길 때 쓴다.
 * 목록은 시드 데이터라 자주 바뀌지 않으므로 모듈 단위로 한 번만 불러와 캐시한다.
 */
import { apiRequest } from './http'
import type { GameName } from '../types/waitingRoom'
import type { GameResultPlayMode } from '../types/gameResult'

export interface GameCatalogItem {
  gameId: number
  gameName: GameName
  playMode: GameResultPlayMode
}

interface GameListResponse {
  games: GameCatalogItem[]
}

let cachedGames: Promise<GameCatalogItem[]> | null = null

/** 테스트용: 모듈 캐시를 비운다. */
export function resetGamesCache(): void {
  cachedGames = null
}

export async function getGames(): Promise<GameCatalogItem[]> {
  if (!cachedGames) {
    cachedGames = apiRequest<GameListResponse>('/games')
      .then((response) => response.games)
      .catch((error) => {
        cachedGames = null // 실패는 캐시하지 않는다(다음 호출에서 재시도).
        throw error
      })
  }
  return cachedGames
}

/**
 * (gameName × playMode)에 해당하는 백엔드 gameId를 찾는다. 없으면 null.
 * 같은 조합에 난이도별 여러 항목이 있으면 첫 번째를 쓴다(placeholder).
 */
export async function resolveGameId(
  gameName: GameName,
  playMode: GameResultPlayMode,
): Promise<number | null> {
  try {
    const games = await getGames()
    const match = games.find(
      (game) => game.gameName === gameName && game.playMode === playMode,
    )
    return match?.gameId ?? null
  } catch {
    return null
  }
}
