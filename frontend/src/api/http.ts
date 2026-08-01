/**
 * 백엔드 REST 호출 공용 래퍼.
 *
 * 모든 응답은 `{ code, message, data }` 엔벨로프(ApiResponse)로 감싸져 오므로 `data`만 반환한다.
 * 게스트 사용자는 최초 방 생성/참가 응답에서 받은 `guestSessionId`를 저장해 이후 요청 헤더로 재사용한다.
 *
 * 게스트 세션은 `sessionStorage`에 저장한다(탭/창마다 독립). 이렇게 하면 같은 브라우저의 서로 다른
 * 창/탭이 각자 다른 게스트 신원을 가져, 1:1 대결에서 identity가 충돌하지 않는다.
 */
const API_BASE = '/api/v1'
const GUEST_HEADER = 'X-Guest-Session-Id'
const GUEST_STORAGE_KEY = 'eye-dont-care.guestSessionId'

function guestStorage(): globalThis.Storage | null {
  return globalThis.sessionStorage ?? null
}

export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
}

/** REST 호출 실패. 백엔드 공통 에러 코드/메시지를 그대로 담는다. */
export class ApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export function getStoredGuestSessionId(): string | null {
  return guestStorage()?.getItem(GUEST_STORAGE_KEY) ?? null
}

export function storeGuestSessionId(guestSessionId: string): void {
  guestStorage()?.setItem(GUEST_STORAGE_KEY, guestSessionId)
}

interface RequestOptions {
  method?: string
  body?: unknown
  /** JWT 로그인 사용자의 access token. 없으면 게스트 세션 헤더를 붙인다. */
  accessToken?: string | null
}

export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (options.accessToken) {
    headers.Authorization = `Bearer ${options.accessToken}`
  } else {
    const guestSessionId = getStoredGuestSessionId()
    if (guestSessionId) {
      headers[GUEST_HEADER] = guestSessionId
    }
  }

  const response = await globalThis.fetch(`${API_BASE}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  const envelope = (await response
    .json()
    .catch(() => null)) as ApiEnvelope<T> | null

  if (!response.ok || envelope === null) {
    throw new ApiError(
      envelope?.code ?? 'UNKNOWN',
      envelope?.message ?? response.statusText,
      response.status,
    )
  }
  return envelope.data
}
