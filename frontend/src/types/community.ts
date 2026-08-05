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

export type CommunityComment = {
  id: string
  author: string
  content: string
  timeLabel: string
}

export type CommunityPost = {
  id: string
  groupId: string
  author: string
  /** 작성자가 방장이면 true — 목록에서 왕관 표시 */
  isLeader?: boolean
  content: string
  timeLabel: string
  comments: CommunityComment[]
}
