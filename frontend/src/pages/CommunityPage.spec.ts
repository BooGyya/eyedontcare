import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CommunityPage from './CommunityPage.vue'
import { ApiError } from '../api/http'
import { useAuthStore } from '../stores/auth'
import type {
  GroupListResponse,
  GroupResponse,
  MyGroupListResponse,
} from '../api/group'

const getGroups = vi.fn<() => Promise<GroupListResponse>>()
const getMyGroups = vi.fn<() => Promise<MyGroupListResponse>>()
const createGroup = vi.fn<() => Promise<GroupResponse>>()
const joinGroupByCode = vi.fn<(groupCode: string) => Promise<GroupResponse>>()
const joinGroupById = vi.fn<() => Promise<GroupResponse>>()

// REST 호출만 가짜로 바꾸고, toCommunityGroup(순수 변환)은 실제 구현을 쓴다.
vi.mock('../api/group', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/group')>()
  return {
    ...actual,
    getGroups: () => getGroups(),
    getMyGroups: () => getMyGroups(),
    createGroup: () => createGroup(),
    joinGroupByCode: (groupCode: string) => joinGroupByCode(groupCode),
    joinGroupById: () => joinGroupById(),
  }
})

function group(
  groupId: number,
  name: string,
  overrides: Partial<GroupResponse> = {},
): GroupResponse {
  return {
    groupId,
    name,
    description: `${name} 소개`,
    members: 3,
    capacity: 10,
    visibility: 'PUBLIC',
    leader: '리더',
    isOwner: false,
    isJoined: false,
    joinCode: null,
    createdAt: '2026-08-01T00:00:00Z',
    ...overrides,
  }
}

function authenticate() {
  useAuthStore().setAuthenticatedUser({
    id: 1,
    nickname: '나',
    level: 1,
    avatar: '',
    profileImageCode: null,
    email: null,
    loginType: null,
    createdAt: null,
  })
}

async function mountCommunityPage({ authed = true, path = '/community' } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  if (authed) authenticate()

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/community', name: 'community', component: CommunityPage },
      {
        path: '/community/:groupId',
        name: 'community-detail',
        component: { template: '<div />' },
      },
    ],
  })
  await router.push(path)
  await router.isReady()

  const wrapper = mount(CommunityPage, {
    global: { plugins: [pinia, router], stubs: { Teleport: true } },
  })
  await flushPromises()
  return wrapper
}

describe('CommunityPage', () => {
  beforeEach(() => {
    for (const fn of [
      getGroups,
      getMyGroups,
      createGroup,
      joinGroupByCode,
      joinGroupById,
    ]) {
      fn.mockReset()
    }
    getGroups.mockResolvedValue({
      groups: [
        group(1, '눈 건강 루틴 연구소'),
        group(2, '집중력 챔피언스', { visibility: 'PRIVATE' }),
        group(3, '가득 모임', { members: 10, capacity: 10 }),
      ],
      page: 1,
      size: 20,
      totalElements: 3,
      totalPages: 1,
    })
    getMyGroups.mockResolvedValue({ groups: [] })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('게스트에게는 API를 부르지 않고 로그인 유도를 보여준다', async () => {
    const wrapper = await mountCommunityPage({ authed: false })

    expect(wrapper.text()).toContain('로그인 후 이용')
    expect(wrapper.findAll('.community-group-card')).toHaveLength(0)
    expect(getGroups).not.toHaveBeenCalled()
  })

  it('로그인 사용자에게 API 소모임을 렌더하고 정원 마감은 비활성화한다', async () => {
    const wrapper = await mountCommunityPage()

    expect(getGroups).toHaveBeenCalledTimes(1)
    expect(getMyGroups).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.community-group-card')).toHaveLength(3)
    expect(wrapper.text()).toContain('눈 건강 루틴 연구소')
    expect(
      wrapper
        .get('[data-testid="community-group-action-3"]')
        .attributes('disabled'),
    ).toBeDefined()
  })

  it('검색어로 필터링하고 빈 상태를 보여준다', async () => {
    const wrapper = await mountCommunityPage()

    await wrapper.get('[data-testid="community-search"]').setValue('집중')
    expect(wrapper.findAll('.community-group-card')).toHaveLength(1)

    await wrapper.get('[data-testid="community-search"]').setValue('없는모임')
    expect(wrapper.text()).toContain('찾는 소모임이 없어요.')
  })

  it('공개 소모임 가입은 join-by-id API를 부른다', async () => {
    joinGroupById.mockResolvedValue(
      group(1, '눈 건강 루틴 연구소', { isJoined: true, members: 4 }),
    )
    const wrapper = await mountCommunityPage()

    await wrapper
      .get('[data-testid="community-group-action-1"]')
      .trigger('click')
    await flushPromises()

    expect(joinGroupById).toHaveBeenCalled()
  })

  it('비공개 카드 가입은 코드 입력 다이얼로그로 보낸다', async () => {
    const wrapper = await mountCommunityPage()

    await wrapper
      .get('[data-testid="community-group-action-2"]')
      .trigger('click')

    expect(wrapper.find('[role="dialog"]').text()).toContain('코드로 참가하기')
    expect(
      wrapper.get('[data-testid="join-code-input"]').attributes('maxlength'),
    ).toBe('6')
    expect(joinGroupById).not.toHaveBeenCalled()
  })

  it('생성 폼은 검증 후 createGroup API를 부른다', async () => {
    createGroup.mockResolvedValue(
      group(99, '주말 눈 휴식 모임', { isJoined: true, isOwner: true }),
    )
    const wrapper = await mountCommunityPage()

    await wrapper.get('[data-testid="open-create-dialog"]').trigger('click')
    await wrapper.find('.community-form').trigger('submit')
    expect(wrapper.text()).toContain('소모임 이름을 입력해주세요.')
    expect(createGroup).not.toHaveBeenCalled()

    await wrapper
      .get('[data-testid="create-group-name"]')
      .setValue('주말 눈 휴식 모임')
    await wrapper
      .get('[data-testid="create-group-description"]')
      .setValue('주말마다 함께 눈 휴식 게임을 즐겨요.')
    await wrapper.get('[data-testid="create-group-capacity"]').setValue(12)
    await wrapper.find('.community-form').trigger('submit')
    await flushPromises()

    expect(createGroup).toHaveBeenCalled()
    expect(wrapper.text()).toContain('주말 눈 휴식 모임')
  })

  it('코드 입장은 joinGroupByCode API를 부른다', async () => {
    joinGroupByCode.mockResolvedValue(
      group(2, '집중력 챔피언스', {
        visibility: 'PRIVATE',
        isJoined: true,
        joinCode: 'FOCUS7',
      }),
    )
    const wrapper = await mountCommunityPage()

    await wrapper.get('[data-testid="open-join-dialog"]').trigger('click')
    await wrapper.get('[data-testid="join-code-input"]').setValue(' focus7 ')
    await wrapper.find('.community-form').trigger('submit')
    await flushPromises()

    expect(joinGroupByCode).toHaveBeenCalledWith('FOCUS7')
  })

  it('메인 화면에서 join=code로 진입하면 코드 입장 다이얼로그가 열린다', async () => {
    const wrapper = await mountCommunityPage({ path: '/community?join=code' })

    expect(wrapper.find('[data-testid="join-code-input"]').exists()).toBe(true)
  })

  it('빈 참여 코드는 안내하고 API를 호출하지 않는다', async () => {
    const wrapper = await mountCommunityPage()

    await wrapper.get('[data-testid="open-join-dialog"]').trigger('click')
    await wrapper.get('[data-testid="join-code-input"]').setValue('   ')
    await wrapper.find('.community-form').trigger('submit')

    expect(wrapper.text()).toContain('참여 코드를 입력해 주세요.')
    expect(joinGroupByCode).not.toHaveBeenCalled()
  })

  it('6자리가 아닌 참여 코드는 안내하고 API를 호출하지 않는다', async () => {
    const wrapper = await mountCommunityPage()

    await wrapper.get('[data-testid="open-join-dialog"]').trigger('click')
    await wrapper.get('[data-testid="join-code-input"]').setValue('A1B2C')
    await wrapper.find('.community-form').trigger('submit')

    expect(wrapper.text()).toContain('참여 코드는 6자리로 입력해 주세요.')
    expect(joinGroupByCode).not.toHaveBeenCalled()
  })

  it('존재하지 않는 참여 코드는 전용 오류 문구를 보여준다', async () => {
    joinGroupByCode.mockRejectedValue(
      new ApiError('GROUP-002', '유효하지 않은 소모임 코드입니다.', 404),
    )
    const wrapper = await mountCommunityPage()

    await wrapper.get('[data-testid="open-join-dialog"]').trigger('click')
    await wrapper.get('[data-testid="join-code-input"]').setValue('NOPE00')
    await wrapper.find('.community-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('존재하지 않는 소모임이에요.')
  })

  it.each([
    ['GROUP-003', '소모임 정원이 가득 찼습니다.'],
    ['GROUP-004', '이미 가입한 소모임입니다.'],
  ])('%s 오류는 기존 메시지를 유지한다', async (code, message) => {
    joinGroupByCode.mockRejectedValue(new ApiError(code, message, 409))
    const wrapper = await mountCommunityPage()

    await wrapper.get('[data-testid="open-join-dialog"]').trigger('click')
    await wrapper.get('[data-testid="join-code-input"]').setValue('NOPE00')
    await wrapper.find('.community-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain(message)
  })

  it('인증 복원 후 목록을 다시 조회한다', async () => {
    const wrapper = await mountCommunityPage({ authed: false })

    expect(getGroups).not.toHaveBeenCalled()
    authenticate()
    await flushPromises()

    expect(getGroups).toHaveBeenCalledTimes(1)
    expect(getMyGroups).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.community-group-card')).toHaveLength(3)
  })

  it('로그아웃하면 기존 목록을 비운다', async () => {
    const fetchMock = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({
        code: 'AUTH_LOGOUT_SUCCESS',
        message: '로그아웃이 완료되었습니다.',
        data: null,
      }),
    }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = await mountCommunityPage()

    await useAuthStore().signOut()
    await flushPromises()

    expect(wrapper.findAll('.community-group-card')).toHaveLength(0)
  })
})
