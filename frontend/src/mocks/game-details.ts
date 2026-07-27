import gameAirMainImage from '../assets/images/games/game-air-main.png'
import gameBlinkImage from '../assets/images/games/game-blink.png'
import gameBlinkMainImage from '../assets/images/games/game-blink-main.png'
import gameDrawImage from '../assets/images/games/game-draw.png'
import gameDrawMainImage from '../assets/images/games/game-draw-main.png'
import gameRhythmMainImage from '../assets/images/games/game-rhythm-main.png'
import gameSnowMainImage from '../assets/images/games/game-snow-main.png'
import gameWaveImage from '../assets/images/games/game-wave.png'
import mascotImage from '../assets/images/brand/mascot.png'
import type {
  GameDetail,
  GameDetailId,
  GamePlayMode,
} from '../types/game-detail'

const standardModes: GamePlayMode[] = [
  { id: 'solo', label: '혼자 연습', description: '나의 기록에 도전해요' },
  { id: 'friends', label: '친구와 함께', description: '친구를 초대해요' },
  { id: 'random', label: '랜덤 매칭', description: '새로운 상대를 만나요' },
]

export const gameDetails: Record<GameDetailId, GameDetail> = {
  air: {
    id: 'air',
    title: '에어하키',
    subtitle: '시선으로 패들을 움직여 상대 골문에 퍽을 넣어보세요!',
    image: gameAirMainImage,
    mascotImage,
    people: '2명',
    duration: '1분',
    tags: ['# 1 vs 1', '# 시선 조작', '# 반응력'],
    steps: [
      '시선으로 내 패들을 움직여요.',
      '퍽을 상대 골문에 넣어 점수를 획득해요.',
      '제한 시간 1분 후 점수가 더 높은 사람이 승리해요.',
    ],
    modes: [
      { id: 'ai', label: 'AI와 대결', description: '가볍게 연습해요' },
      { id: 'friends', label: '친구와 함께', description: '친구를 초대해요' },
      { id: 'random', label: '랜덤 매칭', description: '새로운 상대를 만나요' },
    ],
  },
  hold: {
    id: 'hold',
    title: '눈싸움',
    subtitle: '상대와 눈을 마주치고, 마지막까지 눈을 깜빡이지 마세요!',
    image: gameSnowMainImage,
    mascotImage: gameBlinkImage,
    people: '1~2명',
    duration: '1분',
    tags: ['# 집중력', '# 순발력', '# 눈싸움'],
    steps: [
      '상대와 눈을 마주쳐요.',
      '1분 동안 눈을 크게 뜨고 버텨요.',
      '상대가 먼저 눈을 감으면 승리해요!',
    ],
    modes: standardModes,
  },
  draw: {
    id: 'draw',
    title: '눈으로 그리기',
    subtitle: '주어진 주제를 시선으로 따라가며 그림을 완성해보세요!',
    image: gameDrawMainImage,
    mascotImage: gameDrawImage,
    people: '1명',
    duration: '100초',
    tags: ['# 집중력', '# 창의력', '# 시선 드로잉'],
    steps: [
      '화면에 나타난 주제와 선을 확인해요.',
      '시선으로 선을 따라 그림을 완성해요.',
      '완성도와 정확도를 확인해 내 기록에 도전해요.',
    ],
    modes: [
      {
        id: 'solo',
        label: '혼자 시작하기',
        description: '나만의 그림을 완성해요',
      },
    ],
  },
  rhythm: {
    id: 'rhythm',
    title: '리듬 게임',
    subtitle: '리듬에 맞춰 시선 신호를 보내고 콤보를 이어가보세요!',
    image: gameRhythmMainImage,
    mascotImage: gameWaveImage,
    people: '1~2명',
    duration: '1분',
    tags: ['# 리듬', '# 콤보', '# 순발력'],
    steps: [
      '화면의 박자와 방향 신호를 확인해요.',
      '리듬에 맞춰 시선으로 신호를 따라가요.',
      '연속 성공으로 콤보를 쌓아 높은 점수에 도전해요.',
    ],
    modes: standardModes,
  },
  blink: {
    id: 'blink',
    title: '눈 깜빡이기',
    subtitle: '20초 안에 정확하게 깜빡이고 미션을 완수해요!',
    image: gameBlinkMainImage,
    mascotImage: gameBlinkImage,
    people: '1~2명',
    duration: '20초',
    tags: ['# 정확도', '# 순발력', '# 집중력'],
    steps: [
      '화면의 신호에 맞춰 눈을 깜빡여요.',
      '솔로에서는 20초 동안 정확한 기록에 도전해요.',
      '대결에서는 중간 미션으로 보너스 점수를 얻어요.',
    ],
    modes: standardModes,
  },
}

export function isGameDetailId(value: string): value is GameDetailId {
  return value in gameDetails
}
