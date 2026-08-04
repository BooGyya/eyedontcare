/**
 * 소모임 REST 호출 + 백엔드 응답 → 화면용 모델 변환.
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
import groupImage1 from '../assets/images/illustrations/illustration-teamwork.png'
import groupImage2 from '../assets/images/illustrations/illustration-group-join.png'
import groupImage3 from '../assets/images/games/game-wave.png'
import groupImage4 from '../assets/images/games/game-blink.png'
import type {
  CommunityGroup,
  CommunityGroupVisibility,
} from '../types/community'

export type GroupVisibilityCode = 'PUBLIC' | 'PRIVATE'
export type GroupRoleCode = 'OWNER' | 'MEMBER'

/** 소모임 카드 응답(백엔드 원본). `joinCode`는 가입자에게만 채워진다. */
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

const IMAGE_POOL = [groupImage1, groupImage2, groupImage3, groupImage4]

/** groupId로 대표 이미지를 결정적으로 고른다(같은 소모임은 항상 같은 이미지). */
function imageForGroupId(groupId: number): string {
  return IMAGE_POOL[groupId % IMAGE_POOL.length]
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

/** 코드로 입장(비공개 포함). 백엔드가 코드로 소모임을 찾아 가입시킨다. */
export async function joinGroupByCode(
  groupCode: string,
): Promise<GroupResponse> {
  return apiRequest<GroupResponse>('/groups/join', {
    method: 'POST',
    body: { groupCode },
  })
}

/** 공개 소모임을 id로 바로 입장(코드 없이). 비공개는 백엔드가 거부한다. */
export async function joinGroupById(groupId: string): Promise<GroupResponse> {
  return apiRequest<GroupResponse>(`/groups/${groupId}/join`, {
    method: 'POST',
  })
}

export async function leaveGroup(groupId: string): Promise<void> {
  await apiRequest<null>(`/groups/${groupId}/leave`, { method: 'POST' })
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
