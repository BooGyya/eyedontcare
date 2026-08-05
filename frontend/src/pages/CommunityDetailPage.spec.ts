import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CommunityDetailPage from './CommunityDetailPage.vue'
import { useAuthStore } from '../stores/auth'
import type {
  GroupCommentResponse,
  GroupDetailResponse,
  GroupPostListResponse,
  GroupPostResponse,
} from '../api/group'
import {
  COMMENT_COOLDOWN_MS,
  COMMENT_MAX_LENGTH,
  POST_MAX_LENGTH,
} from '../types/community'

const getGroup = vi.fn<() => Promise<GroupDetailResponse>>()
const leaveGroup = vi.fn<() => Promise<void>>()
const joinGroupById = vi.fn<() => Promise<unknown>>()
const getGroupPosts = vi.fn<() => Promise<GroupPostListResponse>>()
const createGroupPost =
  vi.fn<(groupId: string, content: string) => Promise<GroupPostResponse>>()
const createGroupComment =
  vi.fn<
    (
      groupId: string,
      postId: string,
      content: string,
    ) => Promise<GroupCommentResponse>
  >()

// getGroupPosts/createGroupPost/createGroupComment만 가짜로 바꾸고, 변환기(toCommunityPost 등
// 순수 함수)는 실제 구현을 그대로 쓴다.
vi.mock('../api/group', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/group')>()
  return {
    ...actual,
    getGroup: () => getGroup(),
    leaveGroup: () => leaveGroup(),
    joinGroupById: () => joinGroupById(),
    getGroupPosts: () => getGroupPosts(),
    createGroupPost: (groupId: string, content: string) =>
      createGroupPost(groupId, content),
    createGroupComment: (groupId: string, postId: string, content: string) =>
      createGroupComment(groupId, postId, content),
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

function postList(): GroupPostListResponse {
  return {
    posts: [
      {
        postId: 100,
        author: '리더',
        isLeader: true,
        content: '기존 후기',
        createdAt: '2026-08-01T00:00:00Z',
        comments: [],
      },
    ],
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
    getGroupPosts.mockReset()
    createGroupPost.mockReset()
    createGroupComment.mockReset()

    getGroup.mockResolvedValue(detail())
    leaveGroup.mockResolvedValue()
    getGroupPosts.mockResolvedValue(postList())

    let commentSeq = 0
    createGroupComment.mockImplementation((_groupId, _postId, content) =>
      Promise.resolve({
        commentId: (commentSeq += 1),
        author: '나',
        content,
        createdAt: '2026-08-05T00:00:00Z',
      }),
    )
    createGroupPost.mockImplementation((_groupId, content) =>
      Promise.resolve({
        postId: 999,
        author: '나',
        isLeader: false,
        content,
        createdAt: '2026-08-05T00:00:00Z',
        comments: [],
      }),
    )
  })

  it('게스트에게는 API를 부르지 않고 로그인 유도를 보여준다', async () => {
    const { wrapper } = await mountDetail({ authed: false })

    expect(wrapper.text()).toContain('로그인 후 확인')
    expect(getGroup).not.toHaveBeenCalled()
    expect(getGroupPosts).not.toHaveBeenCalled()
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

  it('게시판 섹션에 서버 후기를 불러와 렌더한다', async () => {
    const { wrapper } = await mountDetail()

    expect(getGroupPosts).toHaveBeenCalled()
    expect(wrapper.text()).toContain('게임 후기 게시판')
    expect(wrapper.find('.community-detail__post').exists()).toBe(true)
    expect(wrapper.text()).toContain('기존 후기')
  })

  it('후기를 작성하면 저장 API를 호출하고 목록에 추가한다', async () => {
    const { wrapper } = await mountDetail()

    const writeButton = wrapper
      .findAll('button')
      .find((button) => button.text().trim() === '글쓰기')
    await writeButton!.trigger('click')

    await wrapper
      .find('.community-detail__composer-textarea')
      .setValue('오늘 눈 운동 완료!')
    await wrapper.find('.community-detail__composer').trigger('submit')
    await flushPromises()

    expect(createGroupPost).toHaveBeenCalledWith('5', '오늘 눈 운동 완료!')
    expect(wrapper.text()).toContain('오늘 눈 운동 완료!')
  })

  it('가입한 사용자는 댓글을 작성하면 저장 API를 호출한다', async () => {
    const { wrapper } = await mountDetail()

    const toggleButton = wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('댓글'))
    expect(toggleButton).toBeDefined()
    await toggleButton!.trigger('click')

    const input = wrapper.find('.community-detail__comment-form input')
    expect(input.exists()).toBe(true)
    await input.setValue('축하해요~')
    await wrapper.find('.community-detail__comment-form button').trigger('click')
    await flushPromises()

    expect(createGroupComment).toHaveBeenCalledWith('5', '100', '축하해요~')
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
    await input.setValue('가'.repeat(COMMENT_MAX_LENGTH + 1))
    await wrapper.find('.community-detail__comment-form button').trigger('click')
    await flushPromises()

    expect(createGroupComment).not.toHaveBeenCalled()
  })

  it('짧은 간격으로 연속 작성하면 쿨다운으로 등록되지 않는다', async () => {
    const { wrapper } = await mountDetail()

    const toggleButton = wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('댓글'))
    await toggleButton!.trigger('click')

    const input = wrapper.find('.community-detail__comment-form input')
    await input.setValue('첫 번째 댓글')
    await wrapper.find('.community-detail__comment-form button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('첫 번째 댓글')

    // 곧바로(쿨다운 이내) 다른 내용을 작성해도 막혀야 한다.
    await input.setValue('난사 댓글')
    await wrapper.find('.community-detail__comment-form button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('난사 댓글')
    expect(createGroupComment).toHaveBeenCalledTimes(1)
  })

  it('쿨다운이 지나도 직전과 같은 내용은 중복으로 막힌다', async () => {
    const nowSpy = vi.spyOn(Date, 'now')
    let clock = 1_000_000
    nowSpy.mockImplementation(() => clock)

    const { wrapper } = await mountDetail()
    const toggleButton = wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('댓글'))
    await toggleButton!.trigger('click')

    const input = wrapper.find('.community-detail__comment-form input')
    await input.setValue('중복 방지 댓글')
    await wrapper.find('.community-detail__comment-form button').trigger('click')
    await flushPromises()

    clock += COMMENT_COOLDOWN_MS + 1 // 쿨다운은 지났지만 내용이 동일
    await input.setValue('중복 방지 댓글')
    await wrapper.find('.community-detail__comment-form button').trigger('click')
    await flushPromises()

    expect(createGroupComment).toHaveBeenCalledTimes(1)
    nowSpy.mockRestore()
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

    await textarea.setValue('가'.repeat(POST_MAX_LENGTH + 1))
    await wrapper.find('.community-detail__composer').trigger('submit')
    await flushPromises()

    expect(createGroupPost).not.toHaveBeenCalled()
  })
})
