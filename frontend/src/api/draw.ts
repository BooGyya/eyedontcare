/**
 * 그림그리기 AI 채점 REST 호출.
 *
 * ⚠️ 이 엔드포인트(`POST /games/draw/recognize`)는 아직 백엔드에 없다. `ai_game` 프로토타입의
 * `server.py`(`/api/recognize-drawing`)가 하던 역할 — 그린 이미지를 Vision API(OpenAI/SSAFY GMS
 * gpt-4o-mini 등)에 넘겨 판정받는 것 — 을 그대로 Spring 엔드포인트로 옮겨야 한다. API 키를
 * 프론트에 둘 수 없어서(브라우저에 노출됨) 반드시 백엔드를 거쳐야 한다.
 *
 * 백엔드가 구현해야 할 계약:
 *   POST /api/v1/games/draw/recognize
 *   요청 body: { imageDataUrl: string(data URL), prompt: string, candidates: string[] }
 *   응답 data: { label: string, confidence: number(0~1), isTarget: boolean, reason: string,
 *              candidates: string[], model?: string }
 *
 * 백엔드가 아직 이 엔드포인트를 안 갖고 있는 동안엔 404/500이 나므로, 호출부
 * (`GamePlayPage.vue`)는 이 함수가 던지는 에러를 잡아 "AI 채점 서버 연결 실패" 안내를 보여주고
 * 재시도할 수 있게 해 둔다 — 조용히 가짜 성공으로 넘어가지 않는다.
 */
import { apiRequest } from './http'

export interface RecognizeDrawingRequest {
  imageDataUrl: string
  prompt: string
  candidates: string[]
}

export interface RecognizeDrawingResponse {
  label: string
  confidence: number
  isTarget: boolean
  reason: string
  candidates: string[]
  model?: string
}

export async function recognizeDrawing(
  request: RecognizeDrawingRequest,
): Promise<RecognizeDrawingResponse> {
  return apiRequest<RecognizeDrawingResponse>('/games/draw/recognize', {
    method: 'POST',
    body: request,
  })
}
