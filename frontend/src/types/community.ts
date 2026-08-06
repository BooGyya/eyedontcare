export type CommunityGroupVisibility = 'public' | 'private'

export type CommunityGroupFilter = 'all' | 'owned' | 'joined'

export type CommunityGroupSort = 'latest' | 'members' | 'name'

export type CommunityGroup = {
  id: string
  name: string
  description: string
  image: string
  members: number
  capacity: number
  visibility: CommunityGroupVisibility
  leader: string
  isJoined: boolean
  isOwner: boolean
  createdAt: number
  joinCode?: string
  /** 게임 종류. 백엔드에 대응 필드가 없어 화면에서는 쓰지 않는다(레거시 mock 호환용). */
  activity?: string
}

export type CommunityGroupDraft = {
  name: string
  description: string
  capacity: number
  visibility: CommunityGroupVisibility
}

/**
 * 길드 댓글 최대 글자 수. 프론트 입력 제한의 기준값이며, 백엔드 댓글 API가 생기면
 * DTO/엔티티의 @Size(max)도 이 값과 동일하게 맞춰 서버 측 우회 요청까지 막아야 한다.
 */
export const COMMENT_MAX_LENGTH = 200

/**
 * 길드 게임 후기(post) 최대 글자 수. 프론트 입력 제한의 기준값이며, 백엔드 게시판 API가
 * 생기면 DTO/엔티티의 @Size(max)도 이 값과 동일하게 맞춰야 한다.
 */
export const POST_MAX_LENGTH = 500

/**
 * 길드 댓글 연속 작성 쿨다운(ms). 짧은 시간에 댓글을 난사하는 것을 막는 프론트 측 임시 방어값이다.
 * 백엔드 댓글 API가 생기면 서버 측 rate limit도 이 정책과 맞춰 우회 요청까지 막아야 한다.
 */
export const COMMENT_COOLDOWN_MS = 3000

export type CommunityComment = {
  id: string
  author: string
  content: string
  timeLabel: string
  /** 요청자 본인이 작성한 댓글이면 true — 수정/삭제 버튼 노출 기준 */
  mine?: boolean
}

export type CommunityPost = {
  id: string
  groupId: string
  author: string
  /** 작성자가 방장이면 true — 목록에서 왕관 표시 */
  isLeader?: boolean
  content: string
  timeLabel: string
  /** 요청자 본인이 작성한 글이면 true — 수정/삭제 버튼 노출 기준 */
  mine?: boolean
  comments: CommunityComment[]
}
