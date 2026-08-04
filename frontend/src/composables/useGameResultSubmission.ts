/**
 * 게임 종료 시 결과를 저장하는 제출 파이프라인.
 *
 * 게임들이 아직 실제 점수/승패를 계산하지 않으므로 지금은 mock 값을 담아 보낸다. 게임 로직이
 * 실제화되면 outcome/score/participants만 실값으로 바꾸면 된다. 저장은 best-effort —
 * 신원이 없거나(게스트 미참여), gameId 매핑이 없거나, POST가 실패해도 게임 흐름을 막지 않는다.
 */
import { resolveGameId } from '../api/game'
import { submitGameResult } from '../api/gameResult'
import { currentParticipantKey, currentParticipantType } from '../api/identity'
import { useAuthStore } from '../stores/auth'
import { GAME_NAME_BY_ID } from '../types/waitingRoom'
import type { GameDetailId } from '../types/game-detail'
import type { GameSessionMode } from '../types/gameplay'
import type { GameOutcome, GameResultPlayMode } from '../types/gameResult'

const MODE_TO_PLAY_MODE: Record<GameSessionMode, GameResultPlayMode> = {
  solo: 'SOLO',
  ai: 'AI',
  friends: 'INVITE',
  random: 'RANDOM',
}

function newPlayId(): string {
  const uuid = globalThis.crypto?.randomUUID?.()
  if (uuid) return uuid
  // 구형 환경 대비 fallback(형식만 UUID). 실동작 환경엔 crypto.randomUUID가 있다.
  return '00000000-0000-4000-8000-000000000000'
}

export function useGameResultSubmission() {
  const auth = useAuthStore()

  async function submitPlayedResult(options: {
    gameSlug: GameDetailId
    mode: GameSessionMode
    startedAt: string
    score: number
    outcome?: GameOutcome
    resultData?: Record<string, unknown>
  }): Promise<void> {
    const participantKey = currentParticipantKey()
    const participantType = currentParticipantType()
    if (!participantKey || !participantType) return // 신원 없음 → 스킵

    const gameId = await resolveGameId(
      GAME_NAME_BY_ID[options.gameSlug],
      MODE_TO_PLAY_MODE[options.mode],
    )
    if (gameId === null) return // (gameName × playMode) 매핑 없음 → 스킵

    const outcome: GameOutcome =
      options.outcome ??
      (options.gameSlug === 'draw' || options.mode === 'solo'
        ? 'COMPLETED'
        : 'WIN')
    const displayName = auth.isAuthenticated
      ? auth.user.nickname
      : '게스트 플레이어'

    try {
      await submitGameResult({
        playId: newPlayId(),
        gameId,
        startedAt: options.startedAt,
        endedAt: new Date().toISOString(),
        participants: [
          {
            participantKey,
            participantType,
            slotNo: 1,
            displayName,
            outcome,
            rank: 1,
          },
        ],
        gameResult: {
          '1': {
            score: options.score,
            ...options.resultData,
          },
        },
      })
    } catch {
      // best-effort: 저장 실패는 게임 흐름을 막지 않는다.
    }
  }

  return { submitPlayedResult }
}
