/**
 * 최소한의 JWT 디코드.
 *
 * 백엔드에 현재 사용자 조회용 `/me`가 없어, access 토큰(JWT)의 `sub` 클레임에서 userId를 읽는다.
 * 서명 검증은 하지 않는다(그건 서버 몫) — payload를 base64url 디코드해 클레임만 꺼낸다.
 */

function decodeBase64Url(segment: string): string | null {
  try {
    const base64 = segment.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(
      base64.length + ((4 - (base64.length % 4)) % 4),
      '=',
    )
    const binary = globalThis.atob(padded)
    // UTF-8 안전 디코드(닉네임 등 멀티바이트 클레임 대비).
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
    return new globalThis.TextDecoder().decode(bytes)
  } catch {
    return null
  }
}

/** access 토큰에서 userId(`sub`)를 숫자로 반환한다. 실패 시 null. */
export function decodeUserId(accessToken: string): number | null {
  const parts = accessToken.split('.')
  if (parts.length < 2) return null
  const json = decodeBase64Url(parts[1])
  if (!json) return null
  try {
    const payload = JSON.parse(json) as { sub?: string | number }
    const sub = payload.sub
    if (sub === undefined || sub === null) return null
    const userId = Number(sub)
    return Number.isFinite(userId) ? userId : null
  } catch {
    return null
  }
}
