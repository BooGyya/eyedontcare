import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CommunityDetailPage from './CommunityDetailPage.vue'
import { useAuthStore } from '../stores/auth'
import type { GroupDetailResponse } from '../api/group'

const getGroup = vi.fn<() => Promise<GroupDetailResponse>>()
const leaveGroup = vi.fn<() => Promise<void>>()
const joinGroupById = vi.fn<() => Promise<unknown>>()

vi.mock('../api/group', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/group')>()
  return {
    ...actual,
    getGroup: () => getGroup(),
    leaveGroup: () => leaveGroup(),
    joinGroupById: () => joinGroupById(),
  }
})

function detail(
  overrides: Partial<GroupDetailResponse> = {},
): GroupDetailResponse {
  return {
    groupId: 5,
    name: '눈 건강 루틴 연구소',
    description: '함께 눈 건강 루틴을 만들어요.',
    members: 2,
    capacity: 10,
    visibility: 'PUBLIC',
    leader: '리더',
    isOwner: false,
    isJoined: true,
    joinCode: 'ABC123',
    createdAt: '2026-08-01T00:00:00Z',
    memberList: [
      { userId: 1, nickname: '리더', role: 'OWNER', joinedAt: '2026-08-01T00:00:00Z' },
      { userId: 2, nickname: '나', role: 'MEMBER', joinedAt: '2026-08-02T00:00:00Z' },
    ],
    ...overrides,
  }
}

function authenticate() {
  useAuthStore().setAuthenticatedUser({
    id: 2,
    nickname: '나',
    level: 1,
    avatar: '',
    profileImageCode: null,
    email: null,
    loginType: null,
    createdAt: null,
  })
}

async function mountDetail({ authed = true } = {}) {
  setActivePinia(createPinia())
  if (authed) authenticate()

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/community', name: 'community', component: { template: '<div />' } },
      { path: '/community/:groupId', name: 'community-detail', component: CommunityDetailPage },
    ],
  })
  await router.push('/community/5')
  await router.isReady()

  const wrapper = mount(CommunityDetailPage, {
    global: { plugins: [router] },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('CommunityDetailPage', () => {
  beforeEach(() => {
    getGroup.mockReset()
    leaveGroup.mockReset()
    joinGroupById.mockReset()
    getGroup.mockResolvedValue(detail())
    leaveGroup.mockResolvedValue()
  })

  it('게스트에게는 API를 부르지 않고 로그인 유도를 보여준다', async () => {
    const { wrapper } = await mountDetail({ authed: false })

    expect(wrapper.text()).toContain('로그인 후 확인')
    expect(getGroup).not.toHaveBeenCalled()
  })

  it('상세와 참여자 명단을 렌더한다', async () => {
    const { wrapper } = await mountDetail()

    expect(getGroup).toHaveBeenCalled()
    expect(wrapper.text()).toContain('눈 건강 루틴 연구소')
    expect(wrapper.text()).toContain('참여자 2명')
    expect(wrapper.text()).toContain('나')
  })

  it('참여 중 멤버는 나가기로 API를 부른다', async () => {
    const { wrapper } = await mountDetail()

    const leaveButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('소모임 나가기'))
    expect(leaveButton).toBeDefined()
    await leaveButton!.trigger('click')
    await flushPromises()

    expect(leaveGroup).toHaveBeenCalled()
  })
})
