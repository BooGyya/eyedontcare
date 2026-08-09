import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'eye-dont-care.media-settings.v1'

/** 볼륨 값(0~1)으로 강제한다. 저장소가 오염됐거나 슬라이더 밖 값이 들어와도 안전하게. */
function clampVolume(value: number): number {
  if (!Number.isFinite(value)) return 1
  return Math.min(1, Math.max(0, value))
}

interface PersistedMediaSettings {
  bgmVolume: number
  bgmMuted: boolean
  voiceVolume: number
  micEnabled: boolean
  cameraEnabled: boolean
}

function loadPersisted(): Partial<PersistedMediaSettings> {
  if (typeof globalThis.localStorage === 'undefined') return {}
  const raw = globalThis.localStorage.getItem(STORAGE_KEY)
  if (!raw) return {}
  try {
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null) return {}
    return parsed as Partial<PersistedMediaSettings>
  } catch {
    return {}
  }
}

/**
 * 게임 화면의 소리·카메라 사용자 설정 저장소.
 *
 * - `bgmVolume`/`voiceVolume`은 0~1 배율이다. 게임마다 기본 음량(예: BGM 0.5, 리듬 음원 0.6)이
 *   다르므로 절대값이 아니라 **기본 음량에 곱하는 배율**로 정의해, 기본값(1)에서는 기존 음량이
 *   그대로 유지된다.
 * - `micEnabled`/`cameraEnabled`는 대결 중 마이크·카메라 송출 의사. 실제 트랙 mute는
 *   `useLiveKitRoom`이 이 값을 보고 수행한다(카메라 장치 자체는 시선 추적을 위해 계속 켜 둔다).
 * - 모든 값은 localStorage에 저장되어 다음 게임에서도 유지된다.
 */
export const useMediaSettingsStore = defineStore('mediaSettings', () => {
  const persisted = loadPersisted()

  const bgmVolume = ref(clampVolume(persisted.bgmVolume ?? 1))
  const bgmMuted = ref(persisted.bgmMuted === true)
  const voiceVolume = ref(clampVolume(persisted.voiceVolume ?? 1))
  const micEnabled = ref(persisted.micEnabled !== false)
  const cameraEnabled = ref(persisted.cameraEnabled !== false)

  /** BGM에 실제 적용할 배율 — 음소거면 0. */
  const effectiveBgmVolume = computed(() =>
    bgmMuted.value ? 0 : bgmVolume.value,
  )

  function setBgmVolume(volume: number): void {
    bgmVolume.value = clampVolume(volume)
    // 슬라이더를 움직이면 소리를 듣겠다는 의도이므로 음소거를 자동 해제한다.
    if (bgmVolume.value > 0) bgmMuted.value = false
  }

  function toggleBgmMuted(): void {
    bgmMuted.value = !bgmMuted.value
  }

  function setVoiceVolume(volume: number): void {
    voiceVolume.value = clampVolume(volume)
  }

  function toggleMic(): void {
    micEnabled.value = !micEnabled.value
  }

  function toggleCamera(): void {
    cameraEnabled.value = !cameraEnabled.value
  }

  watch([bgmVolume, bgmMuted, voiceVolume, micEnabled, cameraEnabled], () => {
    if (typeof globalThis.localStorage === 'undefined') return
    const snapshot: PersistedMediaSettings = {
      bgmVolume: bgmVolume.value,
      bgmMuted: bgmMuted.value,
      voiceVolume: voiceVolume.value,
      micEnabled: micEnabled.value,
      cameraEnabled: cameraEnabled.value,
    }
    try {
      globalThis.localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot))
    } catch {
      // 저장 실패(용량 초과 등)해도 이번 세션 메모리 값으로 계속 동작한다.
    }
  })

  return {
    bgmVolume,
    bgmMuted,
    voiceVolume,
    micEnabled,
    cameraEnabled,
    effectiveBgmVolume,
    setBgmVolume,
    toggleBgmMuted,
    setVoiceVolume,
    toggleMic,
    toggleCamera,
  }
})
