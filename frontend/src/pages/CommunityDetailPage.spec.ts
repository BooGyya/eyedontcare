import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CommunityDetailPage from './CommunityDetailPage.vue'
import { useAuthStore } from '../stores/auth'
import type { GroupDetailResponse } from '../api/group'
import { COMMENT_MAX_LENGTH, POST_MAX_LENGTH } from '../types/community'

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
      {
        userId: 1,
        nickname: '리더',
        role: 'OWNER',
        joinedAt: '2026-08-01T00:00:00Z',
      },
      {
        userId: 2,
        nickname: '나',
        role: 'MEMBER',
        joinedAt: '2026-08-02T00:00:00Z',
      },
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
      {
        path: '/community',
        name: 'community',
        component: { template: '<div />' },
      },
      {
        path: '/community/:groupId',
        name: 'community-detail',
        component: CommunityDetailPage,
      },
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

  it('뒤로가기 컨트롤에 소모임 목록으로가 노출된다', async () => {
    const { wrapper } = await mountDetail()

    expect(wrapper.find('.community-detail__back').text()).toContain(
      '소모임 목록으로',
    )
  })

  it('게시판 섹션에 목 후기가 노출된다', async () => {
    const { wrapper } = await mountDetail()

    expect(wrapper.text()).toContain('게임 후기 게시판')
    expect(wrapper.find('.community-detail__post').exists()).toBe(true)
  })

  it('가입한 사용자는 댓글을 남길 수 있다', async () => {
    const { wrapper } = await mountDetail()

    const toggleButton = wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('댓글'))
    expect(toggleButton).toBeDefined()
    await toggleButton!.trigger('click')

    const input = wrapper.find('.community-detail__comment-form input')
    expect(input.exists()).toBe(true)
    await input.setValue('축하해요~')

    const submitButton = wrapper.find('.community-detail__comment-form button')
    await submitButton.trigger('click')

    expect(wrapper.text()).toContain('축하해요~')
  })

  it('댓글 입력에 최대 글자 수 제한이 걸려 있고 초과 댓글은 등록되지 않는다', async () => {
    const { wrapper } = await mountDetail()

    const toggleButton = wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('댓글'))
    await toggleButton!.trigger('click')

    const input = wrapper.find('.community-detail__comment-form input')
    expect(input.attributes('maxlength')).toBe(String(COMMENT_MAX_LENGTH))

    // setValue는 maxlength를 우회하므로, 제출 가드가 초과분을 막는지 검증한다.
    const tooLong = '가'.repeat(COMMENT_MAX_LENGTH + 1)
    await input.setValue(tooLong)
    await wrapper.find('.community-detail__comment-form button').trigger('click')

    expect(wrapper.text()).not.toContain(tooLong)
  })

  it('후기 작성에 최대 글자 수 제한이 걸려 있고 초과 후기는 등록되지 않는다', async () => {
    const { wrapper } = await mountDetail()

    const writeButton = wrapper
      .findAll('button')
      .find((button) => button.text().trim() === '글쓰기')
    expect(writeButton).toBeDefined()
    await writeButton!.trigger('click')

    const textarea = wrapper.find('.community-detail__composer-textarea')
    expect(textarea.exists()).toBe(true)
    expect(textarea.attributes('maxlength')).toBe(String(POST_MAX_LENGTH))

    const tooLong = '가'.repeat(POST_MAX_LENGTH + 1)
    await textarea.setValue(tooLong)
    await wrapper
      .find('.community-detail__composer button[type="submit"]')
      .trigger('click')

    expect(wrapper.text()).not.toContain(tooLong)
  })
})
