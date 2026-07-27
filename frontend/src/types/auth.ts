export type AuthStatus = 'signed-out' | 'guest' | 'authenticated'

export type MockAuthenticatedUser = {
  nickname: string
  level: number
  avatar: string
}

export type AuthDialogScreen = 'login' | 'guest'
