import gameAirImage from '../assets/images/games/game-air.png'
import gameBlinkImage from '../assets/images/games/game-blink.png'
import gameDrawImage from '../assets/images/games/game-draw.png'
import gameHoldImage from '../assets/images/games/game-hold.png'
import gameWaveImage from '../assets/images/games/game-wave.png'
import groupJoinImage from '../assets/images/illustrations/illustration-group-join.png'
import teamworkImage from '../assets/images/illustrations/illustration-teamwork.png'
import athleteProfile from '../assets/images/profiles/profile-athlete.png'
import calmProfile from '../assets/images/profiles/profile-calm.png'
import crownProfile from '../assets/images/profiles/profile-crown.png'
import detectiveProfile from '../assets/images/profiles/profile-detective.png'
import joyProfile from '../assets/images/profiles/profile-joy.png'
import smileProfile from '../assets/images/profiles/profile-smile.png'
import type {
  CommunityGroup,
  GameCatalogItem,
  GameRanking,
} from '../types/pages'

export const gameCatalog: GameCatalogItem[] = [
  {
    id: 'blink',
    title: '눈 깜빡이기',
    description: '제한 시간 동안 더 많은 눈 깜빡임을 기록해 보세요.',
    image: gameBlinkImage,
    category: '스피드',
    status: 'available',
  },
  {
    id: 'draw',
    title: '눈으로 그리기',
    description: '시선으로 선을 따라가며 그림을 완성하는 게임이에요.',
    image: gameDrawImage,
    category: '집중력',
    status: 'available',
  },
  {
    id: 'hold',
    title: '눈 오래 뜨기',
    description: '눈을 오래 뜨고 집중력을 겨뤄 보세요.',
    image: gameHoldImage,
    category: '집중력',
    status: 'available',
  },
  {
    id: 'rhythm',
    title: '눈빛 웨이브',
    description: '리듬에 맞춰 시선 신호를 보내는 준비 중 게임입니다.',
    image: gameWaveImage,
    category: '리듬',
    status: 'coming-soon',
  },
  {
    id: 'air',
    title: '바람 피하기',
    description: '다가오는 바람을 피해 시선을 움직이는 준비 중 게임입니다.',
    image: gameAirImage,
    category: '반응',
    status: 'coming-soon',
  },
]

export const gameRankings: GameRanking[] = [
  {
    gameId: 'blink',
    gameName: '눈 깜빡이기',
    unit: '회',
    myRank: 7,
    myScore: '98회',
    players: [
      { rank: 1, nickname: '눈빛왕', score: '128회', avatar: crownProfile },
      { rank: 2, nickname: '초롱이', score: '116회', avatar: joyProfile },
      { rank: 3, nickname: '반짝콩', score: '103회', avatar: smileProfile },
      { rank: 4, nickname: '눈사람', score: '96회', avatar: calmProfile },
    ],
  },
  {
    gameId: 'draw',
    gameName: '눈으로 그리기',
    unit: '점',
    myRank: 5,
    myScore: '2,050점',
    players: [
      {
        rank: 1,
        nickname: '선긋기달인',
        score: '2,450점',
        avatar: detectiveProfile,
      },
      { rank: 2, nickname: '몽글이', score: '2,230점', avatar: athleteProfile },
      { rank: 3, nickname: '보라콩', score: '1,980점', avatar: smileProfile },
      { rank: 4, nickname: '시선집중', score: '1,905점', avatar: calmProfile },
    ],
  },
  {
    gameId: 'hold',
    gameName: '눈 오래 뜨기',
    unit: '초',
    myRank: 12,
    myScore: '63.4초',
    players: [
      {
        rank: 1,
        nickname: '집중마스터',
        score: '87.5초',
        avatar: athleteProfile,
      },
      { rank: 2, nickname: '눈동자', score: '79.3초', avatar: joyProfile },
      { rank: 3, nickname: '별빛', score: '72.1초', avatar: crownProfile },
      {
        rank: 4,
        nickname: '바라보기',
        score: '68.2초',
        avatar: detectiveProfile,
      },
    ],
  },
]

export const communityGroups: CommunityGroup[] = [
  {
    id: 'night-owls',
    name: '야간 눈 건강 연구소',
    description: '늦은 시간에도 즐겁게 게임하고 기록을 나누는 소모임',
    image: teamworkImage,
    members: 18,
    capacity: 24,
    status: 'open',
  },
  {
    id: 'blink-club',
    name: '깜빡이 챌린지 클럽',
    description: '매주 눈 깜빡이기 기록에 도전하는 친구들이 모였어요.',
    image: gameBlinkImage,
    members: 12,
    capacity: 16,
    status: 'open',
  },
  {
    id: 'focus-mates',
    name: '집중력 메이트',
    description: '눈으로 그리기와 오래 뜨기를 함께 연습해요.',
    image: groupJoinImage,
    members: 20,
    capacity: 20,
    status: 'full',
  },
]
