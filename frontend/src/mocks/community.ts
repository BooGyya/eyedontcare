import gameBlinkImage from '../assets/images/games/game-blink.png'
import gameWaveImage from '../assets/images/games/game-wave.png'
import groupJoinImage from '../assets/images/illustrations/illustration-group-join.png'
import teamworkImage from '../assets/images/illustrations/illustration-teamwork.png'
import type { CommunityGroup, CommunityPost } from '../types/community'

export const communityGroups: CommunityGroup[] = [
  {
    id: 'eye-health-routine',
    name: '눈 건강 루틴 연구소',
    description: '매일 10분, 함께 눈 건강 루틴을 만들고 게임 기록을 나눠요.',
    image: teamworkImage,
    members: 18,
    capacity: 24,
    visibility: 'public',
    activity: 'Eye-See',
    leader: '눈썹 최강자',
    isJoined: true,
    isOwner: true,
    createdAt: 5,
  },
  {
    id: 'focus-champions',
    name: '집중력 챔피언스',
    description: '눈으로 그리기와 리듬 게임 기록을 함께 올려 보는 모임이에요.',
    image: gameWaveImage,
    members: 5,
    capacity: 10,
    visibility: 'private',
    activity: '리듬 게임',
    leader: '초록별',
    isJoined: false,
    isOwner: false,
    createdAt: 4,
    joinCode: 'FOCUS7',
  },
  {
    id: 'blink-friends',
    name: '깜빡이 동호회',
    description: '눈 깜빡이기 미션을 서로 응원하며 가볍게 즐겨요.',
    image: gameBlinkImage,
    members: 2,
    capacity: 6,
    visibility: 'private',
    activity: '눈 깜빡이기',
    leader: '방울 반짝',
    isJoined: false,
    isOwner: false,
    createdAt: 3,
    joinCode: 'BLINK9',
  },
  {
    id: 'night-players',
    name: '야간 플레이어즈',
    description: '늦은 시간에도 즐겁게 에어하키를 즐기고 기록을 나눠요.',
    image: groupJoinImage,
    members: 12,
    capacity: 12,
    visibility: 'public',
    activity: '에어하키',
    leader: '밤하늘',
    isJoined: false,
    isOwner: false,
    createdAt: 2,
  },
]

/**
 * 게임 후기 게시판 mock 데이터.
 * 백엔드에 게시판 API가 아직 없어 프론트에서만 임시로 관리한다(mock-first).
 * 실제 소모임은 백엔드가 숫자 id를 내려주므로 위 communityGroups의 문자열 id와 일치하지
 * 않을 수 있다 — 이 경우 CommunityDetailPage에서 앞쪽 몇 개를 기본값으로 보여준다.
 */
export const communityPosts: CommunityPost[] = [
  {
    id: 'post-eye-1',
    groupId: 'eye-health-routine',
    author: '눈썹 최강자',
    isLeader: true,
    content: '오늘 눈 깜빡이기 128회 신기록! 다들 도전해 보세요 👀',
    timeLabel: '10분 전',
    comments: [
      {
        id: 'comment-eye-1-1',
        author: '초록별',
        content: '축하해요!! 저도 도전할래요',
        timeLabel: '5분 전',
      },
    ],
  },
  {
    id: 'post-eye-2',
    groupId: 'eye-health-routine',
    author: '루틴러버',
    content: '루틴 3일차인데 눈이 훨씬 편해진 느낌이에요.',
    timeLabel: '어제',
    comments: [],
  },
  {
    id: 'post-focus-1',
    groupId: 'focus-champions',
    author: '초록별',
    isLeader: true,
    content: '리듬 게임 콤보 42 찍었어요! 다음엔 50 노려볼게요.',
    timeLabel: '2시간 전',
    comments: [
      {
        id: 'comment-focus-1-1',
        author: '집중왕',
        content: '대박이다 ㅋㅋ',
        timeLabel: '1시간 전',
      },
      {
        id: 'comment-focus-1-2',
        author: '또박이',
        content: '저는 아직 30대인데 화이팅!',
        timeLabel: '30분 전',
      },
    ],
  },
  {
    id: 'post-blink-1',
    groupId: 'blink-friends',
    author: '방울 반짝',
    isLeader: true,
    content:
      '오늘 깜빡이기 미션 다 같이 성공했어요! 다음 목표는 연속 7일이에요.',
    timeLabel: '어제',
    comments: [
      {
        id: 'comment-blink-1-1',
        author: '또랑또랑',
        content: '우리 팀 최고!',
        timeLabel: '어제',
      },
    ],
  },
  {
    id: 'post-night-1',
    groupId: 'night-players',
    author: '밤하늘',
    isLeader: true,
    content: '에어하키 신기록 세운 분 계신가요? 저는 어제 210점 찍었어요.',
    timeLabel: '3일 전',
    comments: [],
  },
]
