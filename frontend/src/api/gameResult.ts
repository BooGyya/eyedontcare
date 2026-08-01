/**
 * 게임 결과 조회 REST 호출.
 *
 * 목록은 요약(승패/순위/모드/날짜)만, 상세는 참가자·게임별 결과(JSON)를 준다.
 * 결과 제출(쓰기)은 게임 점수 계산과 얽혀 별도 작업이라 여기엔 없다.
 */
import { apiRequest } from './http'
import type {
  GameResultDetailResponse,
  MyGameResultPage,
} from '../types/gameResult'

export async function getMyResults(
  page = 1,
  size = 5,
): Promise<MyGameResultPage> {
  return apiRequest<MyGameResultPage>(
    `/game-results/me?page=${page}&size=${size}`,
  )
}

export async function getResult(
  resultId: number,
): Promise<GameResultDetailResponse> {
  return apiRequest<GameResultDetailResponse>(`/game-results/${resultId}`)
}
