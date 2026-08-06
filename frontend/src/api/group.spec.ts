import { describe, expect, it } from 'vitest'
import { toCommunityGroup } from './group'
import type { GroupResponse } from './group'

function groupResponse(overrides: Partial<GroupResponse> = {}): GroupResponse {
  return {
    groupId: 1,
    name: '테스트 길드',
    description: null,
    members: 1,
    capacity: 10,
    visibility: 'PUBLIC',
    leader: '방장',
    isOwner: false,
    isJoined: false,
    joinCode: null,
    createdAt: '2026-08-05T00:00:00.000Z',
    ...overrides,
  }
}

describe('toCommunityGroup - 대표 이미지', () => {
  it('여러 groupId 모두 비어 있지 않은 대표 이미지를 배정한다', () => {
    for (let groupId = 0; groupId < 12; groupId += 1) {
      const group = toCommunityGroup(groupResponse({ groupId }))
      expect(group.image).toBeTruthy()
    }
  })

  it('groupId가 비정상(NaN)이어도 이미지가 undefined가 되지 않는다', () => {
    // 백엔드가 groupId를 주지 못한 응답 등 방어. IMAGE_POOL[NaN] === undefined 회귀 방지.
    const group = toCommunityGroup(
      groupResponse({ groupId: Number.NaN as unknown as number }),
    )
    expect(group.image).toBeTruthy()
  })
})
