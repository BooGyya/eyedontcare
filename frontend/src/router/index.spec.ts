import { describe, expect, it } from 'vitest'
import router from './index'

describe('router', () => {
  it('resolves every primary navigation destination', () => {
    expect(router.resolve('/games').name).toBe('games')
    expect(router.resolve('/ranking').name).toBe('ranking')
    expect(router.resolve('/community').name).toBe('community')
    expect(router.resolve('/profile').name).toBe('profile')
    expect(router.resolve('/notifications').name).toBe('notifications')
    expect(router.resolve('/settings').name).toBe('settings')
  })
})
