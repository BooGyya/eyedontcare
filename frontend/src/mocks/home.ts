import gameBlinkImage from '../assets/images/games/game-blink.png'
import gameDrawImage from '../assets/images/games/game-draw.png'
import gameHoldImage from '../assets/images/games/game-hold.png'
import gameRhythmImage from '../assets/images/games/game-rhythm-main.png'
import gameWaveImage from '../assets/images/games/game-wave.png'
import discordLogoImage from '../assets/images/illustrations/discord-logo.png'
import groupIllustrationImage from '../assets/images/illustrations/illustration-teamwork.png'
import type { QuickAction, WeeklyRankingGame } from '../types/home'

export const weeklyRankingGames: WeeklyRankingGame[] = [
  {
    id: 'blink',
    title: '눈 깜빡이기',
    mode: '1 vs 1',
    image: gameBlinkImage,
    tone: 'purple',
    unit: '회',
    records: [
      { rank: 2, value: 116, label: '116' },
      { rank: 1, value: 128, label: '128' },
      { rank: 3, value: 103, label: '103' },
    ],
    myRank: 7,
  },
  {
    id: 'draw',
    title: '눈으로 그리기',
    mode: '1인',
    image: gameDrawImage,
    tone: 'mint',
    unit: '점',
    records: [
      { rank: 2, value: 2230, label: '2230' },
      { rank: 1, value: 2450, label: '2450' },
      { rank: 3, value: 1980, label: '1980' },
    ],
    myRank: 5,
  },
  {
    id: 'stare',
    title: '눈 오래 뜨기',
    mode: '1 vs 1',
    image: gameHoldImage,
    tone: 'blue',
    unit: '초',
    records: [
      { rank: 2, value: 79.3, label: '79.3' },
      { rank: 1, value: 87.5, label: '87.5' },
      { rank: 3, value: 72.1, label: '72.1' },
    ],
    myRank: 12,
  },
  {
    id: 'challenge',
    title: '깜빡이 챌린지',
    mode: '1인',
    image: gameRhythmImage,
    tone: 'orange',
    unit: '회',
    records: [
      { rank: 2, value: 487, label: '487' },
      { rank: 1, value: 532, label: '532' },
      { rank: 3, value: 421, label: '421' },
    ],
    myRank: 9,
  },
]

export const homeQuickActions: QuickAction[] = [
  {
    id: 'discord',
    title: '디스코드로 함께하기',
    description: '커뮤니티에 참여하고 친구들과 소통해요!',
    image: discordLogoImage,
    tone: 'blue',
    notice: '디스코드 커뮤니티 연결은 다음 단계에서 준비할 예정이에요.',
  },
  {
    id: 'ranking',
    title: '랭킹 경쟁',
    description: '전국의 플레이어들과 점수를 겨루고 내 순위를 확인해보세요!',
    image: gameWaveImage,
    tone: 'yellow',
    destination: '/ranking',
  },
  {
    id: 'group',
    title: '소모임 코드 입장',
    description: '친구들과 소모임을 만들고 함께 게임을 즐겨요!',
    image: groupIllustrationImage,
    tone: 'purple',
    notice: '소모임 기능은 다음 단계에서 준비할 예정이에요.',
  },
]
