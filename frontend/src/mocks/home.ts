import gameAirImage from '../assets/images/games/game-air.png'
import gameBlinkImage from '../assets/images/games/game-blink.png'
import gameDrawImage from '../assets/images/games/game-draw.png'
import gameHoldImage from '../assets/images/games/game-hold.png'
import gameRhythmImage from '../assets/images/games/game-rhythm-main.png'
import gameWaveImage from '../assets/images/games/game-wave.png'
import discordLogoImage from '../assets/images/illustrations/discord-logo.png'
import groupIllustrationImage from '../assets/images/illustrations/illustration-teamwork.png'
import type { QuickAction, WeeklyRankingGamePreset } from '../types/home'

/**
 * 이번 주 랭킹 카드의 고정 표시 정보(제목/이미지/톤 등). 실제 랭킹(records)과 내 순위(myRank)는
 * `GET /api/v1/rankings` 응답으로 채운다({@link ../api/ranking.ts}).
 */
export const weeklyRankingGamePresets: WeeklyRankingGamePreset[] = [
  {
    id: 'blink',
    title: 'Eye Show Speed (눈 깜빡이기)',
    mode: '1 vs 1',
    image: gameBlinkImage,
    tone: 'purple',
    unit: '회',
  },
  {
    id: 'draw',
    title: 'Eye Draw (눈으로 그리기)',
    mode: '1인',
    image: gameDrawImage,
    tone: 'mint',
    unit: '점',
  },
  {
    id: 'stare',
    title: 'Eye See (눈싸움)',
    mode: '1 vs 1',
    image: gameHoldImage,
    tone: 'blue',
    unit: '초',
  },
  {
    id: 'challenge',
    title: 'Blink the Beat (리듬 게임)',
    mode: '1인',
    image: gameRhythmImage,
    tone: 'orange',
    unit: '회',
  },
  {
    id: 'air',
    title: 'Eye Hockey (에어 하키)',
    mode: '1 vs 1',
    image: gameAirImage,
    tone: 'sky',
    unit: '점',
  },
]

export const homeQuickActions: QuickAction[] = [
  {
    id: 'discord',
    title: '디스코드로 함께하기',
    description: '커뮤니티에 참여하고 친구들과 소통해요!',
    image: discordLogoImage,
    tone: 'blue',
    externalUrl: 'https://discord.gg/8SyyCmGRC',
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
