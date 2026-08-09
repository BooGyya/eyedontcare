import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import GameMediaControls from './GameMediaControls.vue'
import { useMediaSettingsStore } from '../../stores/mediaSettings'

describe('GameMediaControls', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    globalThis.localStorage.clear()
    pinia = createPinia()
    setActivePinia(pinia)
  })

  function mountControls(hasVoiceChat = false, hasBgm = true) {
    return mount(GameMediaControls, {
      props: { hasVoiceChat, hasBgm },
      global: { plugins: [pinia] },
    })
  }

  it('기본으로 BGM 볼륨과 카메라 토글을 보여준다', () => {
    const wrapper = mountControls()
    expect(wrapper.find('input[aria-label="배경음악 볼륨"]').exists()).toBe(
      true,
    )
    expect(wrapper.find('button[aria-label="카메라 끄기"]').exists()).toBe(true)
    // 음성 대화 상대가 없으면 마이크·상대 음성 컨트롤은 없다.
    expect(wrapper.find('input[aria-label="상대 음성 볼륨"]').exists()).toBe(
      false,
    )
    expect(wrapper.find('button[aria-label="마이크 끄기"]').exists()).toBe(
      false,
    )
  })

  it('대결 모드(hasVoiceChat)에서는 마이크 토글과 상대 음성 볼륨을 보여준다', () => {
    const wrapper = mountControls(true)
    expect(wrapper.find('input[aria-label="상대 음성 볼륨"]').exists()).toBe(
      true,
    )
    expect(wrapper.find('button[aria-label="마이크 끄기"]').exists()).toBe(true)
  })

  it('BGM이 없는 화면(hasBgm=false)에서는 BGM 컨트롤을 숨긴다', () => {
    const wrapper = mountControls(true, false)
    expect(wrapper.find('input[aria-label="배경음악 볼륨"]').exists()).toBe(
      false,
    )
    expect(wrapper.find('button[aria-label="배경음악 음소거"]').exists()).toBe(
      false,
    )
    // 음성 대화 컨트롤은 그대로 보인다.
    expect(wrapper.find('input[aria-label="상대 음성 볼륨"]').exists()).toBe(
      true,
    )
  })

  it('BGM 슬라이더를 움직이면 스토어 볼륨이 바뀐다', async () => {
    const wrapper = mountControls()
    const store = useMediaSettingsStore()
    await wrapper.find('input[aria-label="배경음악 볼륨"]').setValue('30')
    expect(store.bgmVolume).toBeCloseTo(0.3)
  })

  it('음소거 버튼을 누르면 BGM이 음소거되고 아이콘 레이블이 바뀐다', async () => {
    const wrapper = mountControls()
    const store = useMediaSettingsStore()
    await wrapper.find('button[aria-label="배경음악 음소거"]').trigger('click')
    expect(store.bgmMuted).toBe(true)
    expect(
      wrapper.find('button[aria-label="배경음악 음소거 해제"]').exists(),
    ).toBe(true)
  })

  it('마이크·카메라 토글이 스토어 상태를 뒤집는다', async () => {
    const wrapper = mountControls(true)
    const store = useMediaSettingsStore()
    await wrapper.find('button[aria-label="마이크 끄기"]').trigger('click')
    expect(store.micEnabled).toBe(false)
    await wrapper.find('button[aria-label="카메라 끄기"]').trigger('click')
    expect(store.cameraEnabled).toBe(false)
    expect(wrapper.find('button[aria-label="카메라 켜기"]').exists()).toBe(true)
  })
})
