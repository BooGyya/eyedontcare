/**
 * 게임 결과 조회 REST 호출.
 *
 * 목록은 요약(승패/순위/모드/날짜)만, 상세는 참가자·게임별 결과(JSON)를 준다.
 * 제출(`submitGameResult`)은 게임 종료 시 실제 게임 결과를 저장한다.
 */
import { apiRequest } from './http'
import type {
  GameResultDetailResponse,
  MyGameResultPage,
  SubmitGameResultRequest,
  SubmitGameResultResponse,
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

export async function submitGameResult(
  request: SubmitGameResultRequest,
): Promise<SubmitGameResultResponse> {
  return apiRequest<SubmitGameResultResponse>('/game-results', {
    method: 'POST',
    body: request,
  })
}
