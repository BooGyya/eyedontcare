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
  activity: string
  leader: string
  isJoined: boolean
  isOwner: boolean
  createdAt: number
  joinCode?: string
}

export type CommunityGroupDraft = {
  name: string
  description: string
  capacity: number
  visibility: CommunityGroupVisibility
  joinCode: string
  activity: string
}
