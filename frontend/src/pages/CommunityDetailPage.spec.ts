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
const updateGroupPost =
  vi.fn<
    (
      groupId: string,
      postId: string,
      content: string,
    ) => Promise<GroupPostResponse>
  >()
const deleteGroupPost =
  vi.fn<(groupId: string, postId: string) => Promise<void>>()
const createGroupComment =
  vi.fn<
    (
      groupId: string,
      postId: string,
      content: string,
    ) => Promise<GroupCommentResponse>
  >()
const updateGroupComment =
  vi.fn<
    (
      groupId: string,
      postId: string,
      commentId: string,
      content: string,
    ) => Promise<GroupCommentResponse>
  >()
const deleteGroupComment =
  vi.fn<(groupId: string, postId: string, commentId: string) => Promise<void>>()
const showToast = vi.hoisted(() => vi.fn())

vi.mock('../composables/useToast', () => ({
  useToast: () => ({ showToast }),
}))

// getGroupPosts/createGroupPost/createGroupComment 등만 가짜로 바꾸고, 변환기(toCommunityPost 등
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
    updateGroupPost: (groupId: string, postId: string, content: string) =>
      updateGroupPost(groupId, postId, content),
    deleteGroupPost: (groupId: string, postId: string) =>
      deleteGroupPost(groupId, postId),
    createGroupComment: (groupId: string, postId: string, content: string) =>
      createGroupComment(groupId, postId, content),
    updateGroupComment: (
      groupId: string,
      postId: string,
      commentId: string,
      content: string,
    ) => updateGroupComment(groupId, postId, commentId, content),
    deleteGroupComment: (groupId: string, postId: string, commentId: string) =>
      deleteGroupComment(groupId, postId, commentId),
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
        mine: false,
        comments: [
          {
            commentId: 1,
            author: '나',
            content: '내 댓글',
            createdAt: '2026-08-01T00:00:00Z',
            mine: true,
          },
          {
            commentId: 2,
            author: '리더',
            content: '리더 댓글',
            createdAt: '2026-08-01T00:00:00Z',
            mine: false,
          },
        ],
      },
      {
        postId: 200,
        author: '나',
        isLeader: false,
        content: '내가 쓴 후기',
        createdAt: '2026-08-01T00:00:00Z',
        mine: true,
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
    updateGroupPost.mockReset()
    deleteGroupPost.mockReset()
    createGroupComment.mockReset()
    updateGroupComment.mockReset()
    deleteGroupComment.mockReset()
    showToast.mockReset()

    getGroup.mockResolvedValue(detail())
    leaveGroup.mockResolvedValue()
    getGroupPosts.mockResolvedValue(postList())
    deleteGroupComment.mockResolvedValue()
    deleteGroupPost.mockResolvedValue()

    let commentSeq = 0
    createGroupComment.mockImplementation((_groupId, _postId, content) =>
      Promise.resolve({
        commentId: (commentSeq += 1),
        author: '나',
        content,
        createdAt: '2026-08-05T00:00:00Z',
        mine: true,
      }),
    )
    updateGroupComment.mockImplementation(
      (_groupId, _postId, commentId, content) =>
        Promise.resolve({
          commentId: Number(commentId),
          author: '나',
          content,
          createdAt: '2026-08-05T00:00:00Z',
          mine: true,
        }),
    )
    createGroupPost.mockImplementation((_groupId, content) =>
      Promise.resolve({
        postId: 999,
        author: '나',
        isLeader: false,
        content,
        createdAt: '2026-08-05T00:00:00Z',
        mine: true,
        comments: [],
      }),
    )
    updateGroupPost.mockImplementation((_groupId, postId, content) =>
      Promise.resolve({
        postId: Number(postId),
        author: '나',
        isLeader: false,
        content,
        createdAt: '2026-08-05T00:00:00Z',
        mine: true,
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

  it('상세와 길드원 명단을 렌더한다', async () => {
    const { wrapper } = await mountDetail()

    expect(getGroup).toHaveBeenCalled()
    expect(wrapper.text()).toContain('눈 건강 루틴 연구소')
    expect(wrapper.text()).toContain('길드원 2명')
    expect(wrapper.text()).toContain('나')
  })

  it('가입한 길드원은 나가기로 API를 부른다', async () => {
    const { wrapper } = await mountDetail()

    const leaveButton = wrapper
      .find('.community-detail__top-actions')
      .findAll('button')
      .find((button) => button.text().includes('길드 나가기'))
    expect(leaveButton).toBeDefined()
    await leaveButton!.trigger('click')
    await flushPromises()

    expect(leaveGroup).toHaveBeenCalled()
  })

  it('공개 길드 미가입자에게는 우측 상단에 가입하기만 노출된다', async () => {
    getGroup.mockResolvedValue(
      detail({ isJoined: false, isOwner: false, joinCode: null }),
    )
    const { wrapper } = await mountDetail()

    const topActions = wrapper.find('.community-detail__top-actions')
    expect(topActions.text()).toContain('가입하기')
    expect(topActions.findAll('button')).toHaveLength(1)
  })

  it('길드원 명단에는 방장 대신 리더 라벨이 노출된다', async () => {
    const { wrapper } = await mountDetail()

    expect(wrapper.text()).toContain('리더')
    expect(wrapper.text()).not.toContain('방장')
  })

  it('뒤로가기 컨트롤에 길드 목록으로가 노출된다', async () => {
    const { wrapper } = await mountDetail()

    expect(wrapper.find('.community-detail__back').text()).toContain(
      '길드 목록으로',
    )
  })

  it('게시판 섹션에 서버 후기를 불러와 렌더한다', async () => {
    const { wrapper } = await mountDetail()

    expect(getGroupPosts).toHaveBeenCalled()
    expect(wrapper.text()).toContain('Talk')
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
    await wrapper
      .find('.community-detail__comment-form button')
      .trigger('click')
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
    await wrapper
      .find('.community-detail__comment-form button')
      .trigger('click')
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
    await wrapper
      .find('.community-detail__comment-form button')
      .trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('첫 번째 댓글')

    // 곧바로(쿨다운 이내) 다른 내용을 작성해도 막혀야 한다.
    await input.setValue('난사 댓글')
    await wrapper
      .find('.community-detail__comment-form button')
      .trigger('click')
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
    await wrapper
      .find('.community-detail__comment-form button')
      .trigger('click')
    await flushPromises()

    clock += COMMENT_COOLDOWN_MS + 1 // 쿨다운은 지났지만 내용이 동일
    await input.setValue('중복 방지 댓글')
    await wrapper
      .find('.community-detail__comment-form button')
      .trigger('click')
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

  it('본인 댓글은 수정 버튼으로 인라인 수정해 저장할 수 있다', async () => {
    const { wrapper } = await mountDetail()

    const toggleButton = wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('댓글'))
    await toggleButton!.trigger('click')

    const editButton = wrapper.find('.community-detail__comment-edit')
    expect(editButton.exists()).toBe(true)
    await editButton.trigger('click')

    const editInput = wrapper.find('.community-detail__comment-edit-input')
    expect(editInput.exists()).toBe(true)
    await editInput.setValue('수정된 댓글')
    await wrapper.find('.community-detail__comment-save').trigger('click')
    await flushPromises()

    expect(updateGroupComment).toHaveBeenCalledTimes(1)
    expect(updateGroupComment).toHaveBeenCalledWith(
      '5',
      '100',
      '1',
      '수정된 댓글',
    )
    expect(wrapper.text()).toContain('수정된 댓글')
    expect(wrapper.find('.community-detail__comment-edit-input').exists()).toBe(
      false,
    )
    expect(showToast).toHaveBeenCalledWith('댓글을 수정했어요.')

    await wrapper.find('.community-detail__comment-edit').trigger('click')
    expect(
      wrapper.find('.community-detail__comment-save').attributes('disabled'),
    ).toBeDefined()
    await wrapper
      .find('.community-detail__comment-edit-input')
      .setValue('내 댓글')
    expect(
      wrapper.find('.community-detail__comment-save').attributes('disabled'),
    ).toBeUndefined()
  })

  it('댓글은 실제 내용이 변경된 경우에만 저장할 수 있다', async () => {
    const { wrapper } = await mountDetail()

    const toggleButton = wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('댓글'))
    await toggleButton!.trigger('click')
    await wrapper.find('.community-detail__comment-edit').trigger('click')

    const editInput = wrapper.find('.community-detail__comment-edit-input')
    const saveButton = wrapper.find('.community-detail__comment-save')
    expect(saveButton.attributes('disabled')).toBeDefined()

    await editInput.setValue('변경된 댓글')
    expect(saveButton.attributes('disabled')).toBeUndefined()

    await editInput.setValue('내 댓글')
    expect(saveButton.attributes('disabled')).toBeDefined()

    await editInput.setValue('  내 댓글   ')
    expect(saveButton.attributes('disabled')).toBeDefined()

    await editInput.setValue('   ')
    expect(saveButton.attributes('disabled')).toBeDefined()

    await editInput.setValue('내 댓글')
    await editInput.trigger('keyup', { key: 'Enter' })
    await saveButton.trigger('click')
    await flushPromises()

    expect(updateGroupComment).not.toHaveBeenCalled()
    expect(showToast).not.toHaveBeenCalledWith('댓글을 수정했어요.')
    expect(wrapper.find('.community-detail__comment-edit-input').exists()).toBe(
      true,
    )
  })

  it('본인 댓글은 삭제 확인 후 목록에서 제거된다', async () => {
    const confirmSpy = vi.spyOn(globalThis, 'confirm').mockReturnValue(true)
    const { wrapper } = await mountDetail()

    const toggleButton = wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('댓글'))
    await toggleButton!.trigger('click')
    expect(wrapper.text()).toContain('내 댓글')

    await wrapper.find('.community-detail__comment-delete').trigger('click')
    await flushPromises()

    expect(deleteGroupComment).toHaveBeenCalledWith('5', '100', '1')
    expect(wrapper.text()).not.toContain('내 댓글')
    confirmSpy.mockRestore()
  })

  it('본인 글은 수정 버튼으로 인라인 수정해 저장할 수 있다', async () => {
    const { wrapper } = await mountDetail()
    expect(wrapper.text()).toContain('내가 쓴 후기')

    const editButton = wrapper.find('.community-detail__post-edit')
    expect(editButton.exists()).toBe(true)
    await editButton.trigger('click')

    const editTextarea = wrapper.find('.community-detail__post-edit-textarea')
    expect(editTextarea.exists()).toBe(true)
    await editTextarea.setValue('수정한 후기')
    await wrapper
      .find('.community-detail__post-edit-actions button:last-child')
      .trigger('click')
    await flushPromises()

    expect(updateGroupPost).toHaveBeenCalledTimes(1)
    expect(updateGroupPost).toHaveBeenCalledWith('5', '200', '수정한 후기')
    expect(wrapper.text()).toContain('수정한 후기')
    expect(wrapper.find('.community-detail__post-edit-textarea').exists()).toBe(
      false,
    )
    expect(showToast).toHaveBeenCalledWith('글을 수정했어요.')

    await wrapper.find('.community-detail__post-edit').trigger('click')
    expect(
      wrapper
        .find('.community-detail__post-edit-actions .community-detail__primary')
        .attributes('disabled'),
    ).toBeDefined()
    await wrapper
      .find('.community-detail__post-edit-textarea')
      .setValue('내가 쓴 후기')
    expect(
      wrapper
        .find('.community-detail__post-edit-actions .community-detail__primary')
        .attributes('disabled'),
    ).toBeUndefined()
  })

  it('글은 실제 내용이 변경된 경우에만 저장할 수 있다', async () => {
    const { wrapper } = await mountDetail()
    await wrapper.find('.community-detail__post-edit').trigger('click')

    const editTextarea = wrapper.find('.community-detail__post-edit-textarea')
    const saveButton = wrapper.find(
      '.community-detail__post-edit-actions .community-detail__primary',
    )
    expect(saveButton.attributes('disabled')).toBeDefined()

    await editTextarea.setValue('변경된 후기')
    expect(saveButton.attributes('disabled')).toBeUndefined()

    await editTextarea.setValue('내가 쓴 후기')
    expect(saveButton.attributes('disabled')).toBeDefined()

    await editTextarea.setValue('  내가 쓴 후기   ')
    expect(saveButton.attributes('disabled')).toBeDefined()

    await editTextarea.setValue('   ')
    expect(saveButton.attributes('disabled')).toBeDefined()

    await editTextarea.setValue('내가 쓴 후기')
    await saveButton.trigger('click')
    await flushPromises()

    expect(updateGroupPost).not.toHaveBeenCalled()
    expect(showToast).not.toHaveBeenCalledWith('글을 수정했어요.')
    expect(wrapper.find('.community-detail__post-edit-textarea').exists()).toBe(
      true,
    )
  })

  it('본인 글은 삭제 확인 후 목록에서 제거된다', async () => {
    const confirmSpy = vi.spyOn(globalThis, 'confirm').mockReturnValue(true)
    const { wrapper } = await mountDetail()
    expect(wrapper.text()).toContain('내가 쓴 후기')

    await wrapper.find('.community-detail__post-delete').trigger('click')
    await flushPromises()

    expect(deleteGroupPost).toHaveBeenCalledWith('5', '200')
    expect(wrapper.text()).not.toContain('내가 쓴 후기')
    confirmSpy.mockRestore()
  })
})
