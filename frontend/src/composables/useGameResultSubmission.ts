/**
 * 게임 종료 시 결과를 저장하는 제출 파이프라인.
 *
 * 게임 로직에서 계산한 실제 점수와 승패를 결과 API에 전달한다. 저장은 best-effort —
 * 신원이 없거나(게스트 미참여), gameId 매핑이 없거나, POST가 실패해도 게임 흐름을 막지 않는다.
 */
import { resolveGameId } from '../api/game'
import { submitGameResult } from '../api/gameResult'
import { currentParticipantKey, currentParticipantType } from '../api/identity'
import { useAuthStore } from '../stores/auth'
import { GAME_NAME_BY_ID } from '../types/waitingRoom'
import type { GameDetailId } from '../types/game-detail'
import type { GameSessionMode } from '../types/gameplay'
import type {
  GameOutcome,
  GameResultPlayMode,
  SubmitGameResultResponse,
} from '../types/gameResult'

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
  }): Promise<SubmitGameResultResponse | null> {
    const participantKey = currentParticipantKey()
    const participantType = currentParticipantType()
    if (!participantKey || !participantType) return null

    const gameId = await resolveGameId(
      GAME_NAME_BY_ID[options.gameSlug],
      MODE_TO_PLAY_MODE[options.mode],
    )
    if (gameId === null) return null

    const outcome: GameOutcome = options.outcome ?? 'COMPLETED'
    // displayName은 백엔드에서 @NotBlank 필수값이다. 닉네임이 아직 로드되지 않았거나 비어 있는
    // 인증 상태(예: 세션 복원 지연)에서 빈 값을 보내면 "요청 값이 올바르지 않습니다"(COMMON-001)로
    // 저장이 실패한다. 항상 공백이 아닌 이름을 넣는다.
    const resolvedName = auth.isAuthenticated
      ? auth.user.nickname
      : '게스트 플레이어'
    const displayName = resolvedName?.trim() ? resolvedName.trim() : '플레이어'

    try {
      const response = await submitGameResult({
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
      return response
    } catch {
      // best-effort: 저장 실패는 게임 흐름을 막지 않는다.
      return null
    }
  }

  return { submitPlayedResult }
}
