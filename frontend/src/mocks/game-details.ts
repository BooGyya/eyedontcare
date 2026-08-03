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
  {
    id: 'solo',
    label: '혼자하기',
    description: '혼자서 게임하기',
    badge: '랭킹에 반영',
  },
  {
    id: 'friends',
    label: '친구와 대결',
    description: '방 생성 및 초대코드를 입력 해 입장하기',
  },
  {
    id: 'random',
    label: '랜덤 매칭',
    description: '랜덤 매칭을 돌려 게임 유저와 함께하기',
  },
]

export const gameDetails: Record<GameDetailId, GameDetail> = {
  air: {
    id: 'air',
    title: 'Eye Hockey (에어 하키)',
    subtitle: '시선으로 패들을 움직여 상대 골문에 퍽을 넣어보세요!',
    image: gameAirMainImage,
    mascotImage,
    people: '2명',
    duration: '1분',
    durationLabel: '게임 시간',
    tags: ['#골 득점', '#시선 조작', '#반응력'],
    steps: [
      '시선으로 내 패들을 움직여요.',
      '퍽을 상대 골문에 넣어 점수를 획득해요.',
      '제한 시간 1분 후 점수가 더 높은 사람이 승리해요.',
    ],
    modes: [
      {
        id: 'friends',
        label: '친구와 대결',
        description: '초대코드로 방을 만들고 친구와 함께 대결해요!',
      },
      {
        id: 'random',
        label: '랜덤 매칭',
        description: '랜덤 매칭을 통해 다른 유저와 대결해요!',
        badge: '랭킹에 반영',
      },
      {
        id: 'ai',
        label: 'AI 대결',
        description: 'AI와 함께 대결하며 실력을 연습해요!',
      },
    ],
    guide: {
      intro: [
        '눈을 좌우로 움직여 퍽을 막고,\n상대의 골대에 퍽을 넣어 점수를 얻는 **아이하키** 게임이에요!',
        '**1분** 동안 더 많은 골을 넣어 승리하세요!',
      ],
      highlights: [
        { icon: 'goal', text: '골을 넣으면\n**1점씩** 점수를 얻어요!' },
        { icon: 'trophy', text: '랜덤 매칭 **승리 횟수**가\n랭킹에 기록돼요!' },
        { icon: 'timer', text: '게임 시간은\n**1분**이에요!' },
        { icon: 'eye', text: '눈을 **좌우**로 움직여\n퍽을 막아보세요!' },
      ],
    },
  },
  hold: {
    id: 'hold',
    title: 'Eye See (눈싸움)',
    subtitle: '상대와 눈을 마주치고, 마지막까지 눈을 깜빡이지 마세요!',
    image: gameSnowMainImage,
    mascotImage: gameBlinkImage,
    people: '1~2명',
    duration: '30초',
    durationLabel: '예상 시간',
    tags: ['#집중력', '#순발력', '#눈싸움'],
    steps: [
      '상대와 눈을 마주쳐요.',
      '1분 동안 눈을 크게 뜨고 버텨요.',
      '상대가 먼저 눈을 감으면 승리해요!',
    ],
    modes: [
      {
        id: 'solo',
        label: '혼자하기',
        description: '혼자서 눈싸움을 하고 기록을 랭킹에 반영해요!',
        badge: '랭킹에 반영',
      },
      {
        id: 'ai',
        label: 'AI 대결',
        description: 'AI가 버티는 시간보다 오래 버티면 승리해요!',
      },
      {
        id: 'friends',
        label: '친구와 대결',
        description: '초대코드, 방 생성을 해서 친구와 함께 대결해요!',
      },
      {
        id: 'random',
        label: '랜덤 매칭',
        description: '랜덤 매칭을 돌려 게임 유저와 함께 대결해요!',
      },
    ],
    aiDifficulties: [
      { value: 'easy', label: 'easy', duration: '15초' },
      { value: 'normal', label: 'normal', duration: '30초' },
      { value: 'hard', label: 'hard', duration: '1분' },
    ],
    guide: {
      intro: [
        '눈을 오래 뜨고 있는 사람이 승리하는 **눈싸움 게임**이에요!',
        '상대보다 먼저 눈을 감으면 지게 돼요.',
        '끝까지 눈을 뜨고 버텨보세요!',
        '게임 시작을 누르면 **3, 2, 1 카운트다운** 후 바로 시작!\n**한쪽 눈이라도 감기면** 바로 패배예요!',
      ],
      highlights: [
        { icon: 'trophy', text: '**혼자하기**가\n랭킹에 기록돼요!' },
        { icon: 'timer', text: '랭킹 기록은\n**초 단위**로 측정돼요!' },
        {
          icon: 'eye',
          text: '게임 시간은\n둘 중 **먼저 눈을 감는**\n사람의 시간이에요!',
        },
      ],
      difficulties: {
        title: 'AI 대결 난이도',
        items: [
          { label: 'easy', duration: '15초', color: 'green' },
          { label: 'normal', duration: '30초', color: 'orange' },
          { label: 'hard', duration: '1분', color: 'purple' },
        ],
      },
    },
  },
  draw: {
    id: 'draw',
    title: 'Eye Draw (눈으로 그리기)',
    subtitle: '눈으로 그림을 그리면 AI가 정답을 맞혀요!',
    image: gameDrawMainImage,
    mascotImage: gameDrawImage,
    people: '1명',
    duration: '100초',
    tags: ['#집중력', '#창의력', '#AI 채점'],
    steps: [
      'Space 키를 누르면 눈으로 그리기 기능이 일시 정지됩니다.\n다시 누르면 그림 그리기가 시작돼요.',
      'Space 키로 일시 정지 후, 마우스로 기본 색상 5개, 전체 지우기,\n되돌아가기, 제출하기 등을 사용할 수 있어요.',
      'Round 당 1개의 문제를 풀며, 제시어는 난이도에 따라 분류되어\n같은 난이도 카테고리의 제시어는 동일한 점수가 부여돼요.',
      'AI가 그림을 평가해 점수를 산정합니다.',
    ],
    modes: [
      {
        id: 'ai',
        label: 'AI 대결',
        description: 'AI가 점수를 매겨요',
        badge: '랭킹에 반영',
      },
    ],
    guide: {
      intro: [
        '눈으로 그림을 그려 **AI**가 정답을 맞혀요!',
        '제한 시간 안에 더 정확하게 그릴수록 높은 점수를 받을 수 있어요!',
        '3라운드의 점수 총합으로 **랭킹**이 결정돼요!',
      ],
      cards: [
        {
          title: '그림 그리기',
          color: 'green',
          icon: 'timer',
          iconText: '100',
          description: '제한 시간 100초 안에\n눈으로 그림을 그려요.',
        },
        {
          title: 'AI가 채점',
          color: 'purple',
          icon: 'robot',
          description: 'AI가 그림을 분석해\n점수를 매겨요.',
        },
        {
          title: '라운드 진행',
          color: 'orange',
          icon: 'rounds',
          description:
            'Round1 > Round2 > Round3\n순서로 진행되며,\n라운드가 올라갈수록\n그리기 난이도가 올라가요.',
        },
        {
          title: '점수 및 랭킹',
          color: 'blue',
          icon: 'trophy',
          description:
            '라운드 점수의 총합이\n최종 점수가 되고,\n랭킹에 기록돼요.',
        },
      ],
      stepIcons: ['space', 'mouse', 'list', 'robot'],
      notes: {
        title: 'AI 채점 방식',
        items: [
          '맞혔을 때 기본점수 100점 + 남은 시간 보너스 + AI Confidence 보너스',
          '남은 시간 보너스: 제한 시간(100초) 내에 남은 시간이 많을수록 추가 점수',
          'AI Confidence 보너스: AI가 해당 그림을 얼마나 정확하게 맞혔는지에 따라 추가 점수 (예: 80% 확신 vs 40% 확신 시 점수 차이 발생)',
          '3라운드 점수 총합을 기준으로 랭킹에 기록돼요.',
        ],
      },
    },
  },
  rhythm: {
    id: 'rhythm',
    title: 'Blink the Beat (리듬 게임)',
    subtitle: '눈으로 랜덤 비트를 따라가며 콤보를 완성해요!',
    image: gameRhythmMainImage,
    mascotImage: gameWaveImage,
    people: '1~2명',
    duration: '30초',
    tags: ['#집중력', '#콤보', '#순발력'],
    steps: [
      '음악 1곡 동안 진행됩니다.',
      '**왼쪽 눈 / 오른쪽 눈 / 양쪽 눈**을 비트에 맞춰 감아주세요.',
      '비트를 놓치면 하트가 1개 감소하고 콤보가 깨져요.',
      '**10콤보** 이후부터 **콤보 보너스**가 적용됩니다.',
    ],
    modes: [
      {
        id: 'solo',
        label: '혼자하기',
        description: '혼자서 리듬게임하기',
        badge: '랭킹에 반영',
      },
      {
        id: 'friends',
        label: '친구와 대결',
        description: '방 생성 및 초대코드를 입력해 입장하기',
      },
      {
        id: 'random',
        label: '랜덤 매칭',
        description: '랜덤 매칭을 돌려 게임 유저와 함께하기',
      },
    ],
    guide: {
      intro: [
        '음악에 맞춰 눈을 깜빡여 보세요! 🎵',
        '30초 동안 비트에 맞춰\n**왼쪽 눈 / 오른쪽 눈 / 양쪽 눈**을 감으면 점수를 얻어요!',
        '**콤보**를 이어갈수록 더 높은 점수를 획득할 수 있어요!',
      ],
      cards: [
        {
          title: '리듬 맞추기',
          color: 'blue',
          icon: 'rhythm',
          description: '30초 동안\n음악 비트에 맞춰\n눈을 깜빡여요.',
        },
        {
          title: '콤보',
          color: 'orange',
          icon: 'combo',
          description: '10콤보부터\n콤보 보너스가\n추가돼요.',
        },
        {
          title: '하트',
          color: 'pink',
          icon: 'hearts',
          description: '체력은 하트 5개!\n실수하면 하나씩\n감소해요.',
        },
        {
          title: '점수 · 랭킹',
          color: 'green',
          icon: 'trophy',
          description:
            '기본 점수 + 콤보 보너스\n+ 남은 하트 점수\n혼자하기 기록이\n랭킹에 등록돼요.',
        },
      ],
      stepIcons: ['note', 'eye', 'heartbreak', 'flame'],
      formula: {
        title: '점수 계산',
        parts: [
          { label: '기본 점수', color: 'purple', icon: 'star' },
          { label: '콤보 보너스', color: 'orange', icon: 'flame' },
          { label: '남은 하트 점수', color: 'pink', icon: 'heart' },
        ],
        total: '총 점수',
      },
    },
  },
  blink: {
    id: 'blink',
    title: 'Eye Show Speed (눈 깜빡이기)',
    subtitle: '20초 안에 정확하게 깜빡이고 미션을 완수해요!',
    image: gameBlinkMainImage,
    mascotImage: gameBlinkImage,
    artCaption: '깜빡!',
    people: '1~2명',
    duration: '20초',
    tags: ['#정확도', '#순발력', '#집중력'],
    steps: [
      '제한 시간 20초 동안 눈을 깜빡여요.',
      '깜빡이는 횟수가 많을수록 더 높은 점수를 얻어요.',
      '**혼자하기** 모드의 기록이 랭킹에 반영돼요.',
      '**친구와 대결**, **랜덤 매칭**에서는 랜덤 이벤트가 발동되고,\n성공 시 추가 보너스를 획득해요!',
    ],
    modes: standardModes,
    guide: {
      intro: [
        '20초 동안 눈을 깜빡여\n최대한 많이 깜빡여 보세요! 👀',
        '깜빡이는 횟수가 **랭킹**에 기록돼요!\n이벤트를 성공하면 추가 **보너스**를 얻을 수 있어요!',
        '게임 시작을 누르면 **3, 2, 1 카운트다운** 후 바로 시작되고,\n시간이 끝나면 자동으로 종료돼요!',
      ],
      cards: [
        {
          title: '제한 시간',
          color: 'green',
          icon: 'timer',
          iconText: '20',
          description: '20초 동안\n최대한 많이\n눈을 깜빡여요.',
        },
        {
          title: '랭킹 기록',
          color: 'purple',
          icon: 'trophy',
          description: '깜빡이는 횟수가\n랭킹에 기록돼요.',
          badge: '혼자하기 랭킹 반영',
          badgeColor: 'green',
        },
        {
          title: '이벤트',
          suffix: '(멀티 전용)',
          color: 'orange',
          icon: 'gift',
          description: '친구와 대결, 랜덤 매칭에서\n랜덤 이벤트가 발동돼요!',
          badge: '성공 시 보너스 획득',
          badgeColor: 'orange',
        },
        {
          title: '보너스',
          color: 'blue',
          icon: 'star',
          description: '이벤트를 성공하면\n추가 보너스를\n받을 수 있어요!',
        },
      ],
      stepIcons: ['eye', 'tally', 'trophy', 'gift'],
      events: [
        { icon: 'clock', color: 'purple', label: '빠르게 3번 깜빡이기' },
        { icon: 'star', color: 'green', label: '연속 깜빡이기\n(5회 이상)' },
        { icon: 'wink', color: 'orange', label: '한쪽 눈 윙크하기' },
      ],
    },
  },
}

export function isGameDetailId(value: string): value is GameDetailId {
  return value in gameDetails
}
