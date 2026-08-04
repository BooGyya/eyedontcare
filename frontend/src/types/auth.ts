export type AuthStatus = 'guest' | 'authenticated'

export type AuthDialogScreen = 'login' | 'signup'

/** 백엔드 `ProfileImageCode` enum. 프론트에서 아바타 이미지로 해석한다. */
export type ProfileImageCode =
  | 'PROFILE_1'
  | 'PROFILE_2'
  | 'PROFILE_3'
  | 'PROFILE_4'
  | 'PROFILE_5'
  | 'PROFILE_6'
  | 'PROFILE_7'
  | 'PROFILE_8'

/** 백엔드 `UserLoginType` enum. */
export type UserLoginType = 'LOCAL' | 'KAKAO'

/** `POST /api/v1/auth/*` 응답 data. 토큰은 body로만 오고 쿠키는 쓰지 않는다. */
export interface TokenResponse {
  accessToken: string
  refreshToken: string
}

/** `GET /api/v1/users/{userId}` 응답 data. */
export interface UserResponse {
  id: number
  /** 카카오 계정은 null일 수 있다. */
  email: string | null
  nickname: string
  profileImageCode: ProfileImageCode
  loginType: UserLoginType
  createdAt: string
}

/** `GET /api/v1/users/nickname/check` 응답 data. */
export interface NicknameCheckResponse {
  nickname: string
  available: boolean
}

/**
 * UI 전반에서 쓰는 현재 사용자 모델.
 *
 * 기존 컴포넌트가 `nickname`/`level`/`avatar`를 직접 읽으므로 그 형태를 유지하고 `id`/`email`/
 * `loginType`을 더한다. `avatar`는 `profileImageCode`를 이미지 URL로 해석한 값이다.
 */
export interface AuthUser {
  id: number | null
  nickname: string
  /** 백엔드에 레벨 개념이 없어 임시 placeholder다. Phase 4에서 실제 통계로 대체한다. */
  level: number
  avatar: string
  /** 현재 프로필 이미지 코드. 아바타 선택지 프리셀렉트에 쓴다. 게스트는 null. */
  profileImageCode: ProfileImageCode | null
  email: string | null
  loginType: UserLoginType | null
  /** 가입 시각(ISO). 게스트는 null. */
  createdAt: string | null
}
