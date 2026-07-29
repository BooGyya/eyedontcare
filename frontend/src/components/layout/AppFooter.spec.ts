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

  it('opens the feedback dialog, enables submit once text is entered, and closes on submit', async () => {
    const wrapper = mount(AppFooter, {
      global: { plugins: [createTestRouter()], stubs: { Teleport: true } },
    })

    const feedbackButton = wrapper
      .findAll('.app-footer__links button')
      .find((button) => button.text() === '피드백 보내기')
    await feedbackButton?.trigger('click')

    expect(wrapper.get('.feedback-dialog__submit').attributes()).toHaveProperty(
      'disabled',
    )

    await wrapper.get('.feedback-dialog__textarea').setValue('버튼이 안 눌려요')
    expect(
      wrapper.get('.feedback-dialog__submit').attributes(),
    ).not.toHaveProperty('disabled')

    await wrapper.get('.feedback-dialog__submit').trigger('click')
    expect(wrapper.find('.feedback-dialog').exists()).toBe(false)
  })
})
