/**
 * 길드 REST 호출 + 백엔드 응답 → 화면용 모델 변환.
 *
 * 백엔드(`/api/v1/groups`)는 공개 목록/검색·상세·생성·입장(코드/공개id)·나가기를 제공한다. 응답은
 * 프론트 CommunityGroup에 맞춰 인원수·리더·요청자 상태(isOwner/isJoined)·입장코드를 담지만,
 * 대표 이미지는 주지 않는다 → 랭킹 아바타와 같은 방식으로 groupId 기준 이미지 풀에서 결정적으로
 * 배정한다. (게임 종류 activity는 백엔드에 없어 화면 모델에서 제외됐다.)
 *
 * 모든 엔드포인트는 인증 사용자 전용이다(요청자 기준으로 상태 계산). 게스트는 호출하지 않고
 * 화면에서 로그인 유도로 처리한다.
 */
import { apiRequest } from './http'
import groupTeamworkImage from '../assets/images/illustrations/illustration-teamwork.png'
import groupJoinImage from '../assets/images/illustrations/illustration-group-join.png'
import mascotImage from '../assets/images/brand/mascot.png'
import gameBasicImage from '../assets/images/games/game-basic.png'
import gameBlinkImage from '../assets/images/games/game-blink.png'
import gameDrawImage from '../assets/images/games/game-draw.png'
import gameEyeImage from '../assets/images/games/game-eye.png'
import gameHoldImage from '../assets/images/games/game-hold.png'
import gamePlusFixedImage from '../assets/images/games/game-plus-fixed.png'
import gamePlusTransparentImage from '../assets/images/games/game-plus-transparent.png'
import gameWaveImage from '../assets/images/games/game-wave.png'
import profileAthleteImage from '../assets/images/profiles/profile-athlete.png'
import profileCalmImage from '../assets/images/profiles/profile-calm.png'
import profileCrownImage from '../assets/images/profiles/profile-crown.png'
import profileDetectiveImage from '../assets/images/profiles/profile-detective.png'
import profileJoyImage from '../assets/images/profiles/profile-joy.png'
import profileSmileImage from '../assets/images/profiles/profile-smile.png'
import profileTiredImage from '../assets/images/profiles/profile-tired.png'
import profileWinkImage from '../assets/images/profiles/profile-wink.png'
import type {
  CommunityComment,
  CommunityGroup,
  CommunityGroupVisibility,
  CommunityPost,
} from '../types/community'

export type GroupVisibilityCode = 'PUBLIC' | 'PRIVATE'
export type GroupRoleCode = 'OWNER' | 'MEMBER'

/** 길드 카드 응답(백엔드 원본). `joinCode`는 가입자에게만 채워진다. */
export interface GroupResponse {
  groupId: number
  name: string
  description: string | null
  members: number
  capacity: number
  visibility: GroupVisibilityCode
  leader: string | null
  isOwner: boolean
  isJoined: boolean
  joinCode: string | null
  createdAt: string
}

export interface GroupMemberResponse {
  userId: number
  nickname: string
  role: GroupRoleCode
  joinedAt: string
}

export interface GroupListResponse {
  groups: GroupResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface MyGroupListResponse {
  groups: GroupResponse[]
}

export interface GroupDetailResponse extends GroupResponse {
  memberList: GroupMemberResponse[]
}

export interface CreateGroupBody {
  name: string
  description: string
  visibility: GroupVisibilityCode
  capacity: number
}

// 대표 이미지 풀: 게임 메인 배너류(game-air/-main 시리즈, game-plus, game-snow-main)와
// 로고류(logo/footer-logo/discord-logo/kakao-talk), 결과 연출용 이미지는 제외하고
// 캐릭터·일러스트·게임 아이콘을 골고루 쓴다.
const IMAGE_POOL = [
  groupTeamworkImage,
  groupJoinImage,
  mascotImage,
  gameBasicImage,
  gameBlinkImage,
  gameDrawImage,
  gameEyeImage,
  gameHoldImage,
  gamePlusFixedImage,
  gamePlusTransparentImage,
  gameWaveImage,
  profileAthleteImage,
  profileCalmImage,
  profileCrownImage,
  profileDetectiveImage,
  profileJoyImage,
  profileSmileImage,
  profileTiredImage,
  profileWinkImage,
]

/** groupId로 대표 이미지를 결정적으로 고른다(같은 길드는 항상 같은 이미지). */
function imageForGroupId(groupId: number): string {
  // groupId가 숫자가 아니면(undefined/NaN 등) IMAGE_POOL[NaN]이 undefined가 되어 이미지가
  // 통째로 사라진다. 유효한 정수가 아니면 첫 이미지로 고정해 항상 이미지를 보장한다.
  if (!Number.isFinite(groupId)) return IMAGE_POOL[0]
  return IMAGE_POOL[Math.abs(Math.trunc(groupId)) % IMAGE_POOL.length]
}

function toVisibility(code: GroupVisibilityCode): CommunityGroupVisibility {
  return code === 'PRIVATE' ? 'private' : 'public'
}

// --- REST ---

export async function getGroups(
  keyword = '',
  page = 1,
  size = 20,
): Promise<GroupListResponse> {
  const query = new globalThis.URLSearchParams({
    page: String(page),
    size: String(size),
  })
  if (keyword) query.set('keyword', keyword)
  return apiRequest<GroupListResponse>(`/groups?${query.toString()}`)
}

export async function getMyGroups(): Promise<MyGroupListResponse> {
  return apiRequest<MyGroupListResponse>('/groups/me')
}

export async function getGroup(groupId: string): Promise<GroupDetailResponse> {
  return apiRequest<GroupDetailResponse>(`/groups/${groupId}`)
}

export async function createGroup(
  body: CreateGroupBody,
): Promise<GroupResponse> {
  return apiRequest<GroupResponse>('/groups', { method: 'POST', body })
}

/** 코드로 입장(비공개 포함). 백엔드가 코드로 길드를 찾아 가입시킨다. */
export async function joinGroupByCode(
  groupCode: string,
): Promise<GroupResponse> {
  return apiRequest<GroupResponse>('/groups/join', {
    method: 'POST',
    body: { groupCode },
  })
}

/** 공개 길드를 id로 바로 입장(코드 없이). 비공개는 백엔드가 거부한다. */
export async function joinGroupById(groupId: string): Promise<GroupResponse> {
  return apiRequest<GroupResponse>(`/groups/${groupId}/join`, {
    method: 'POST',
  })
}

export async function leaveGroup(groupId: string): Promise<void> {
  await apiRequest<null>(`/groups/${groupId}/leave`, { method: 'POST' })
}

/** 길드 삭제(방장 전용). 길드원 전원과 함께 삭제된다. */
export async function deleteGroup(groupId: string): Promise<void> {
  await apiRequest<null>(`/groups/${groupId}`, { method: 'DELETE' })
}

/** 길드원 강퇴(방장 전용). */
export async function kickMember(
  groupId: string,
  userId: number,
): Promise<void> {
  await apiRequest<null>(`/groups/${groupId}/members/${userId}`, {
    method: 'DELETE',
  })
}

// --- 변환 ---

/** 백엔드 GroupResponse를 화면용 CommunityGroup으로 바꾼다(activity는 백엔드에 없어 제외). */
export function toCommunityGroup(response: GroupResponse): CommunityGroup {
  return {
    id: String(response.groupId),
    name: response.name,
    description: response.description ?? '',
    image: imageForGroupId(response.groupId),
    members: response.members,
    capacity: response.capacity,
    visibility: toVisibility(response.visibility),
    leader: response.leader ?? '',
    isJoined: response.isJoined,
    isOwner: response.isOwner,
    createdAt: new Date(response.createdAt).getTime(),
    ...(response.joinCode ? { joinCode: response.joinCode } : {}),
  }
}

// --- 후기 게시판(글·댓글) ---

/** 후기 댓글 응답(백엔드 원본). `mine`은 요청자 본인이 작성한 댓글인지를 나타낸다. */
export interface GroupCommentResponse {
  commentId: number
  author: string | null
  content: string
  createdAt: string
  mine: boolean
}

/** 후기 글 응답(백엔드 원본). 최신 글이 먼저 온다. `mine`은 요청자 본인이 작성한 글인지를 나타낸다. */
export interface GroupPostResponse {
  postId: number
  author: string | null
  isLeader: boolean
  content: string
  createdAt: string
  mine: boolean
  comments: GroupCommentResponse[]
}

export interface GroupPostListResponse {
  posts: GroupPostResponse[]
}

/** 후기 게시판 목록 조회(가입 여부와 무관하게 회원이면 열람). */
export async function getGroupPosts(
  groupId: string,
): Promise<GroupPostListResponse> {
  return apiRequest<GroupPostListResponse>(`/groups/${groupId}/posts`)
}

/** 후기 작성(가입자 전용). 저장된 글을 반환한다. */
export async function createGroupPost(
  groupId: string,
  content: string,
): Promise<GroupPostResponse> {
  return apiRequest<GroupPostResponse>(`/groups/${groupId}/posts`, {
    method: 'POST',
    body: { content },
  })
}

/** 후기 수정(작성자 본인 전용). 수정된 글을 반환한다. */
export async function updateGroupPost(
  groupId: string,
  postId: string,
  content: string,
): Promise<GroupPostResponse> {
  return apiRequest<GroupPostResponse>(`/groups/${groupId}/posts/${postId}`, {
    method: 'PATCH',
    body: { content },
  })
}

/** 후기 삭제(작성자 본인 전용). 댓글도 함께 삭제된다. */
export async function deleteGroupPost(
  groupId: string,
  postId: string,
): Promise<void> {
  await apiRequest<null>(`/groups/${groupId}/posts/${postId}`, {
    method: 'DELETE',
  })
}

/** 댓글 작성(가입자 전용). 저장된 댓글을 반환한다. */
export async function createGroupComment(
  groupId: string,
  postId: string,
  content: string,
): Promise<GroupCommentResponse> {
  return apiRequest<GroupCommentResponse>(
    `/groups/${groupId}/posts/${postId}/comments`,
    { method: 'POST', body: { content } },
  )
}

/** 댓글 수정(작성자 본인 전용). 수정된 댓글을 반환한다. */
export async function updateGroupComment(
  groupId: string,
  postId: string,
  commentId: string,
  content: string,
): Promise<GroupCommentResponse> {
  return apiRequest<GroupCommentResponse>(
    `/groups/${groupId}/posts/${postId}/comments/${commentId}`,
    { method: 'PATCH', body: { content } },
  )
}

/** 댓글 삭제(작성자 본인 전용). */
export async function deleteGroupComment(
  groupId: string,
  postId: string,
  commentId: string,
): Promise<void> {
  await apiRequest<null>(
    `/groups/${groupId}/posts/${postId}/comments/${commentId}`,
    { method: 'DELETE' },
  )
}

/** 작성 시각(ISO)을 화면용 상대 시간 라벨로 바꾼다. */
export function toTimeLabel(createdAt: string): string {
  const created = new Date(createdAt).getTime()
  const diffMs = Date.now() - created
  const minute = 60_000
  const hour = 60 * minute
  const day = 24 * hour
  if (diffMs < minute) return '방금'
  if (diffMs < hour) return `${Math.floor(diffMs / minute)}분 전`
  if (diffMs < day) return `${Math.floor(diffMs / hour)}시간 전`
  if (diffMs < 7 * day) return `${Math.floor(diffMs / day)}일 전`
  const date = new Date(created)
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}

export function toCommunityComment(
  response: GroupCommentResponse,
): CommunityComment {
  return {
    id: String(response.commentId),
    author: response.author ?? '알 수 없음',
    content: response.content,
    timeLabel: toTimeLabel(response.createdAt),
    mine: response.mine,
  }
}

export function toCommunityPost(
  response: GroupPostResponse,
  groupId: string,
): CommunityPost {
  return {
    id: String(response.postId),
    groupId,
    author: response.author ?? '알 수 없음',
    isLeader: response.isLeader,
    content: response.content,
    timeLabel: toTimeLabel(response.createdAt),
    mine: response.mine,
    comments: response.comments.map(toCommunityComment),
  }
}
