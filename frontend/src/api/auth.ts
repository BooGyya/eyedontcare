/**
 * 인증 REST 호출.
 *
 * 로그인/가입/카카오/재발급은 `{accessToken, refreshToken}`을 body로 돌려준다(쿠키 없음).
 * 저장·부팅 복원·현재 사용자 로드는 스토어(`stores/auth.ts`)가 이 함수들을 조합해 처리한다.
 */
import { apiRequest } from './http'
import type { TokenResponse } from '../types/auth'

export async function login(
  email: string,
  password: string,
): Promise<TokenResponse> {
  return apiRequest<TokenResponse>('/auth/login', {
    method: 'POST',
    body: { email, password },
  })
}

export async function signup(
  email: string,
  password: string,
): Promise<TokenResponse> {
  return apiRequest<TokenResponse>('/auth/signup', {
    method: 'POST',
    body: { email, password },
  })
}

export async function loginWithKakao(
  authorizationCode: string,
): Promise<TokenResponse> {
  return apiRequest<TokenResponse>('/auth/login/kakao', {
    method: 'POST',
    body: { authorizationCode },
  })
}

/** 서버의 refresh 토큰을 폐기한다. access 토큰은 stateless라 클라에서 별도로 버린다. */
export async function logout(): Promise<void> {
  await apiRequest<null>('/auth/logout', { method: 'POST' })
}

/** 회원 탈퇴(소프트 삭제). 이후 클라는 토큰을 폐기하고 게스트로 돌아간다. */
export async function withdraw(): Promise<void> {
  await apiRequest<null>('/auth/withdraw', { method: 'DELETE' })
}
