/**
 * 백엔드 REST 호출 공용 래퍼.
 *
 * 모든 응답은 `{ code, message, data }` 엔벨로프(ApiResponse)로 감싸져 오므로 `data`만 반환한다.
 * 회원은 localStorage에 저장된 access 토큰으로 `Authorization: Bearer`를 자동으로 붙이고, 없으면
 * 게스트 세션 헤더를 붙인다. 게스트 세션은 `sessionStorage`에 저장한다(탭/창마다 독립).
 *
 * access 토큰이 만료돼 401이 나면, refresh 토큰으로 `/auth/reissue`를 한 번 시도해 새 토큰을 받고
 * 원요청을 재시도한다. 재발급까지 실패하면 토큰을 비우고 세션 만료를 알린다(게스트로 전환).
 */
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  notifySessionExpired,
  setTokens,
} from './authTokens'

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
  /** 명시적으로 지정할 JWT. 없으면 저장된 회원 토큰을, 그것도 없으면 게스트 세션 헤더를 붙인다. */
  accessToken?: string | null
}

function buildHeaders(accessToken: string | null): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  } else {
    const guestSessionId = getStoredGuestSessionId()
    if (guestSessionId) {
      headers[GUEST_HEADER] = guestSessionId
    }
  }
  return headers
}

/**
 * refresh 토큰으로 access 토큰을 재발급한다. 성공하면 저장하고 true를 반환한다.
 * 재발급 자체는 apiRequest를 다시 타지 않도록 fetch를 직접 쓴다(무한 재시도 방지).
 */
async function tryReissue(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false
  try {
    const response = await globalThis.fetch(`${API_BASE}/auth/reissue`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    const envelope = (await response.json().catch(() => null)) as ApiEnvelope<{
      accessToken: string
      refreshToken: string
    }> | null
    if (!response.ok || !envelope?.data?.accessToken) {
      clearTokens()
      notifySessionExpired()
      return false
    }
    setTokens(envelope.data)
    return true
  } catch {
    clearTokens()
    notifySessionExpired()
    return false
  }
}

async function requestWithRetry<T>(
  path: string,
  options: RequestOptions,
  allowRetry: boolean,
): Promise<T> {
  const accessToken = getAccessToken() ?? options.accessToken ?? null
  const response = await globalThis.fetch(`${API_BASE}${path}`, {
    method: options.method ?? 'GET',
    headers: buildHeaders(accessToken),
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  // access 토큰 만료로 보이면 한 번만 재발급 후 재시도한다.
  if (response.status === 401 && allowRetry && getRefreshToken()) {
    const refreshed = await tryReissue()
    if (refreshed) {
      return requestWithRetry<T>(path, options, false)
    }
  }

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

export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  return requestWithRetry<T>(path, options, true)
}
