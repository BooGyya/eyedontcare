/**
 * 사용자 조회·수정 REST 호출 및 프로필 이미지 해석.
 *
 * 백엔드는 프로필 이미지를 enum(`ProfileImageCode`)으로 내려주므로, 프론트에서 실제 이미지 자산으로
 * 매핑한다. 현재 사용자 조회는 `/me`가 없어 access 토큰의 userId로 `GET /users/{userId}`를 부른다.
 */
import { apiRequest } from './http'
import profile1 from '../assets/images/profiles/profile-joy.png'
import profile2 from '../assets/images/profiles/profile-smile.png'
import profile3 from '../assets/images/profiles/profile-wink.png'
import profile4 from '../assets/images/profiles/profile-calm.png'
import profile5 from '../assets/images/profiles/profile-athlete.png'
import profile6 from '../assets/images/profiles/profile-crown.png'
import profile7 from '../assets/images/profiles/profile-detective.png'
import profile8 from '../assets/images/profiles/profile-tired.png'
import type {
  NicknameCheckResponse,
  ProfileImageCode,
  UserResponse,
} from '../types/auth'

const PROFILE_IMAGE_BY_CODE: Record<ProfileImageCode, string> = {
  PROFILE_1: profile1,
  PROFILE_2: profile2,
  PROFILE_3: profile3,
  PROFILE_4: profile4,
  PROFILE_5: profile5,
  PROFILE_6: profile6,
  PROFILE_7: profile7,
  PROFILE_8: profile8,
}

/** `ProfileImageCode`를 아바타 이미지 URL로 해석한다. 알 수 없으면 기본값. */
export function avatarForProfileCode(code: ProfileImageCode | null): string {
  return (code && PROFILE_IMAGE_BY_CODE[code]) ?? profile1
}

/** 프로필 편집 화면의 아바타 선택지. 백엔드가 지원하는 8종에 1:1 대응한다. */
export const PROFILE_OPTIONS: {
  code: ProfileImageCode
  name: string
  image: string
}[] = [
  { code: 'PROFILE_1', name: '기쁨', image: profile1 },
  { code: 'PROFILE_2', name: '미소', image: profile2 },
  { code: 'PROFILE_3', name: '윙크', image: profile3 },
  { code: 'PROFILE_4', name: '차분', image: profile4 },
  { code: 'PROFILE_5', name: '활력', image: profile5 },
  { code: 'PROFILE_6', name: '왕관', image: profile6 },
  { code: 'PROFILE_7', name: '탐정', image: profile7 },
  { code: 'PROFILE_8', name: '휴식', image: profile8 },
]

export async function getUser(userId: number): Promise<UserResponse> {
  return apiRequest<UserResponse>(`/users/${userId}`)
}

/** 닉네임/프로필 이미지 부분 수정. 최소 한 필드는 지정해야 한다. */
export async function updateProfile(
  userId: number,
  patch: { nickname?: string; profileImageCode?: ProfileImageCode },
): Promise<UserResponse> {
  return apiRequest<UserResponse>(`/users/${userId}`, {
    method: 'PATCH',
    body: patch,
  })
}

export async function checkNickname(
  nickname: string,
): Promise<NicknameCheckResponse> {
  return apiRequest<NicknameCheckResponse>(
    `/users/nickname/check?nickname=${encodeURIComponent(nickname)}`,
  )
}

export async function updatePassword(
  userId: number,
  body: { currentPassword: string; newPassword: string },
): Promise<void> {
  await apiRequest<null>(`/users/${userId}/password`, {
    method: 'PUT',
    body,
  })
}
