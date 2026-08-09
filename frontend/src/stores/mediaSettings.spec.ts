import { beforeEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useMediaSettingsStore } from './mediaSettings'

const STORAGE_KEY = 'eye-dont-care.media-settings.v1'

describe('useMediaSettingsStore', () => {
  beforeEach(() => {
    globalThis.localStorage.clear()
    setActivePinia(createPinia())
  })

  it('기본값은 배율 1(원래 음량), 음소거 해제, 마이크 꺼짐, 카메라 켜짐이다', () => {
    const store = useMediaSettingsStore()
    expect(store.bgmVolume).toBe(1)
    expect(store.bgmMuted).toBe(false)
    expect(store.voiceVolume).toBe(1)
    // 마이크는 프라이버시를 위해 기본 꺼짐 — 사용자가 직접 켜야 송출된다.
    expect(store.micEnabled).toBe(false)
    expect(store.cameraEnabled).toBe(true)
    expect(store.effectiveBgmVolume).toBe(1)
  })

  it('볼륨을 0~1 범위로 강제한다', () => {
    const store = useMediaSettingsStore()
    store.setBgmVolume(1.7)
    expect(store.bgmVolume).toBe(1)
    store.setBgmVolume(-0.3)
    expect(store.bgmVolume).toBe(0)
    store.setVoiceVolume(Number.NaN)
    expect(store.voiceVolume).toBe(1)
  })

  it('음소거하면 적용 볼륨이 0이 되고, 슬라이더를 올리면 음소거가 풀린다', () => {
    const store = useMediaSettingsStore()
    store.toggleBgmMuted()
    expect(store.effectiveBgmVolume).toBe(0)
    store.setBgmVolume(0.4)
    expect(store.bgmMuted).toBe(false)
    expect(store.effectiveBgmVolume).toBe(0.4)
  })

  it('변경된 설정을 localStorage에 저장하고 다음 세션에서 복원한다', async () => {
    const store = useMediaSettingsStore()
    store.setBgmVolume(0.25)
    store.setVoiceVolume(0.5)
    store.toggleMic()
    store.toggleCamera()
    await nextTick()

    expect(globalThis.localStorage.getItem(STORAGE_KEY)).not.toBeNull()

    // 새 Pinia 인스턴스 = 새 세션과 동일한 조건.
    setActivePinia(createPinia())
    const restored = useMediaSettingsStore()
    expect(restored.bgmVolume).toBe(0.25)
    expect(restored.voiceVolume).toBe(0.5)
    // toggleMic로 기본값(꺼짐)에서 켠 상태가 복원되어야 한다.
    expect(restored.micEnabled).toBe(true)
    expect(restored.cameraEnabled).toBe(false)
  })

  it('저장소가 오염돼도 기본값으로 동작한다', () => {
    globalThis.localStorage.setItem(STORAGE_KEY, '{broken json')
    const store = useMediaSettingsStore()
    expect(store.bgmVolume).toBe(1)
    expect(store.micEnabled).toBe(false)
  })
})
