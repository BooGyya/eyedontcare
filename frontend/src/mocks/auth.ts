import avatarImage from '../assets/images/profiles/profile-joy.png'
import type { MockAuthenticatedUser } from '../types/auth'

export const mockAuthenticatedUser: MockAuthenticatedUser = {
  nickname: '눈썹 최강자',
  level: 12,
  avatar: avatarImage,
}

const nicknamePrefixes = ['반짝', '맑은', '초롱', '기분 좋은', '눈부신']
const nicknameSuffixes = ['시선', '눈빛', '동공', '깜빡이', '플레이어']

export function generateMockNickname() {
  const prefix =
    nicknamePrefixes[Math.floor(Math.random() * nicknamePrefixes.length)]
  const suffix =
    nicknameSuffixes[Math.floor(Math.random() * nicknameSuffixes.length)]
  return `${prefix} ${suffix}`
}
