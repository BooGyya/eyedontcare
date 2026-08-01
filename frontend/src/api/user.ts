/**
 * 사용자 조회 REST 호출 및 프로필 이미지 해석.
 *
 * 백엔드는 프로필 이미지를 enum(`ProfileImageCode`)으로 내려주므로, 프론트에서 실제 이미지 자산으로
 * 매핑한다. 현재 사용자 조회는 `/me`가 없어 access 토큰의 userId로 `GET /users/{userId}`를 부른다.
 */
import { apiRequest } from './http'
import profile1 from '../assets/images/profiles/profile-joy.png'
import profile2 from '../assets/images/profiles/profile-smile.png'
import profile3 from '../assets/images/profiles/profile-wink.png'
import profile4 from '../assets/images/profiles/profile-calm.png'
import type { ProfileImageCode, UserResponse } from '../types/auth'

const PROFILE_IMAGE_BY_CODE: Record<ProfileImageCode, string> = {
  PROFILE_1: profile1,
  PROFILE_2: profile2,
  PROFILE_3: profile3,
  PROFILE_4: profile4,
}

/** `ProfileImageCode`를 아바타 이미지 URL로 해석한다. 알 수 없으면 기본값. */
export function avatarForProfileCode(code: ProfileImageCode | null): string {
  return (code && PROFILE_IMAGE_BY_CODE[code]) ?? profile1
}

export async function getUser(userId: number): Promise<UserResponse> {
  return apiRequest<UserResponse>(`/users/${userId}`)
}
