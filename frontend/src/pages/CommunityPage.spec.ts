import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CommunityPage from './CommunityPage.vue'

function mountCommunityPage() {
  return mount(CommunityPage, {
    global: {
      stubs: {
        Teleport: true,
      },
    },
  })
}

describe('CommunityPage', () => {
  it('renders the mock group cards and the full-group disabled state', () => {
    const wrapper = mountCommunityPage()

    expect(wrapper.findAll('.community-group-card')).toHaveLength(4)
    expect(wrapper.text()).toContain('눈 건강 루틴 연구소')
    expect(
      wrapper
        .get('[data-testid="community-group-action-night-players"]')
        .attributes('disabled'),
    ).toBeDefined()
  })

  it('filters groups by search query and shows an empty state', async () => {
    const wrapper = mountCommunityPage()

    await wrapper.get('[data-testid="community-search"]').setValue('깜빡이')
    expect(wrapper.findAll('.community-group-card')).toHaveLength(1)

    await wrapper.get('[data-testid="community-search"]').setValue('없는 모임')
    expect(wrapper.text()).toContain('찾는 소모임이 없어요.')
  })

  it('opens, validates, closes, and creates a group from the mock form', async () => {
    const wrapper = mountCommunityPage()

    await wrapper.get('[data-testid="open-create-dialog"]').trigger('click')
    await wrapper.find('.community-form').trigger('submit')
    expect(wrapper.text()).toContain('소모임 이름을 입력해주세요.')

    await wrapper
      .get('[data-testid="create-group-name"]')
      .setValue('주말 눈 휴식 모임')
    await wrapper
      .get('[data-testid="create-group-description"]')
      .setValue('주말마다 함께 눈 휴식 게임을 즐겨요.')
    await wrapper.get('[data-testid="create-group-capacity"]').setValue(12)
    await wrapper.find('.community-form').trigger('submit')

    expect(wrapper.text()).toContain('주말 눈 휴식 모임')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)

    await wrapper.get('[data-testid="open-create-dialog"]').trigger('click')
    await wrapper.get('[aria-label="모달 닫기"]').trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('handles invalid, valid, and duplicate join-code submissions', async () => {
    const wrapper = mountCommunityPage()

    await wrapper.get('[data-testid="open-join-dialog"]').trigger('click')
    await wrapper.find('.community-form').trigger('submit')
    expect(wrapper.text()).toContain('참여 코드를 입력해주세요.')

    await wrapper.get('[data-testid="join-code-input"]').setValue('INVALID')
    await wrapper.find('.community-form').trigger('submit')
    expect(wrapper.text()).toContain('일치하는 참여 코드를 찾지 못했어요.')

    await wrapper.get('[data-testid="join-code-input"]').setValue('FOCUS7')
    await wrapper.find('.community-form').trigger('submit')
    expect(
      wrapper
        .get('[data-testid="community-group-action-focus-champions"]')
        .text(),
    ).toBe('입장하기')

    await wrapper.get('[data-testid="open-join-dialog"]').trigger('click')
    await wrapper.get('[data-testid="join-code-input"]').setValue('FOCUS7')
    await wrapper.find('.community-form').trigger('submit')
    expect(wrapper.text()).toContain('이미 참여 중인 소모임이에요.')
  })

  it('routes private-card joins through the join-code dialog', async () => {
    const wrapper = mountCommunityPage()

    await wrapper
      .get('[data-testid="community-group-action-focus-champions"]')
      .trigger('click')
    expect(wrapper.find('[role="dialog"]').text()).toContain('코드로 참가하기')
  })
})
