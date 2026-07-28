export type ProfileAvatar = {
  id: string
  name: string
  image: string
}

export type ProfileStat = {
  label: string
  value: string
  caption: string
}

export type ProfileActivity = {
  id: string
  icon: string
  tone: 'mint' | 'purple' | 'yellow'
  title: string
  description: string
  time: string
  score: string
}

export type UserProfile = {
  nickname: string
  level: number
  journeyDays: number
  weeklyScore: string
  weeklyChange: string
  avatar: string
  avatars: ProfileAvatar[]
  stats: ProfileStat[]
  activities: ProfileActivity[]
}
