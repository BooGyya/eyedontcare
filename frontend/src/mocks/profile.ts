import athleteAvatar from '../assets/images/profiles/profile-athlete.png'
import calmAvatar from '../assets/images/profiles/profile-calm.png'
import crownAvatar from '../assets/images/profiles/profile-crown.png'
import detectiveAvatar from '../assets/images/profiles/profile-detective.png'
import joyAvatar from '../assets/images/profiles/profile-joy.png'
import smileAvatar from '../assets/images/profiles/profile-smile.png'
import tiredAvatar from '../assets/images/profiles/profile-tired.png'
import winkAvatar from '../assets/images/profiles/profile-wink.png'
import type { UserProfile } from '../types/profile'

export const profileData: UserProfile = {
  nickname: '눈썹 최강자',
  level: 12,
  journeyDays: 18,
  weeklyScore: '12,850',
  weeklyChange: '+18%',
  avatar: joyAvatar,
  avatars: [
    { id: 'joy', name: '기쁨', image: joyAvatar },
    { id: 'athlete', name: '운동장', image: athleteAvatar },
    { id: 'crown', name: '왕관', image: crownAvatar },
    { id: 'calm', name: '차분한 휴식', image: calmAvatar },
    { id: 'detective', name: '탐정', image: detectiveAvatar },
    { id: 'wink', name: '윙크 챔피언', image: winkAvatar },
    { id: 'tired', name: '휴식 중', image: tiredAvatar },
    { id: 'smile', name: '미소', image: smileAvatar },
  ],
  stats: [
    { label: '총 플레이 시간', value: '4h 32m', caption: '이번 달' },
    { label: '완료한 루틴', value: '38', caption: '연속 6일' },
    { label: '친구 순위', value: '#04', caption: '상위 10%' },
  ],
  activities: [
    {
      id: 'draw-complete',
      icon: '✓',
      tone: 'mint',
      title: '눈으로 그리기',
      description: '를 완료했어요.',
      time: '오늘 오후 2:18',
      score: '+420',
    },
    {
      id: 'group-ranking',
      icon: '♛',
      tone: 'purple',
      title: '소모임 랭킹 4위',
      description: '를 달성했어요.',
      time: '어제 오후 8:40',
      score: '+180',
    },
    {
      id: 'rest-notice',
      icon: '☀',
      tone: 'yellow',
      title: '오늘의 눈 휴식 알림',
      description: '을 확인했어요.',
      time: '어제 오전 10:12',
      score: '+50',
    },
  ],
}
