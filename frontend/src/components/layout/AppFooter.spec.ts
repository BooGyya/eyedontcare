import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import AppFooter from './AppFooter.vue'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
}

describe('AppFooter', () => {
  it('opens the terms dialog and closes it with the confirm button', async () => {
    const wrapper = mount(AppFooter, {
      global: { plugins: [createTestRouter()], stubs: { Teleport: true } },
    })

    const termsButton = wrapper
      .findAll('.app-footer__links button')
      .find((button) => button.text() === '이용약관')
    await termsButton?.trigger('click')

    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    expect(wrapper.get('#policy-dialog-title').text()).toBe('이용약관')

    await wrapper.get('.policy-dialog__confirm').trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('does not render a feedback entry in the footer links or support dialog', async () => {
    const wrapper = mount(AppFooter, {
      global: { plugins: [createTestRouter()], stubs: { Teleport: true } },
    })

    expect(
      wrapper
        .findAll('.app-footer__links button')
        .some((button) => button.text() === '피드백 보내기'),
    ).toBe(false)

    const supportButton = wrapper
      .findAll('.app-footer__links button')
      .find((button) => button.text() === '고객센터')
    await supportButton?.trigger('click')

    expect(wrapper.get('#policy-dialog-title').text()).toBe('고객센터')
    expect(wrapper.find('.policy-dialog__feedback').exists()).toBe(false)
  })
})
