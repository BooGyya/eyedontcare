import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  DEFAULT_CALIBRATION_PROFILE,
  type EyeCalibrationProfile,
} from '../lib/eye-tracking/config'
import type { GazeCalibrationProfile } from '../lib/eye-tracking/gaze-calibration'

const EYE_PROFILE_STORAGE_KEY = 'eye-dont-care.calibration.eye-profile.v1'
const GAZE_PROFILE_STORAGE_KEY = 'eye-dont-care.calibration.gaze-profile.v1'

/**
 * 캘리브레이션 결과 저장소.
 *
 * ⚠️ 지금은 `localStorage`에만 저장한다 — 브라우저/기기를 바꾸면 다시 보정해야 한다.
 * 백엔드에 캘리브레이션 프로필 저장 API(`api-spec.md` 7장에 초안이 있던 `/calibration-profiles`)가
 * 생기면, {@link save}/{@link load}만 그 API 호출로 바꾸면 되도록 인터페이스를 최대한 단순하게
 * 유지했다. 로그인 사용자는 서버에, 게스트는 계속 localStorage에 저장하는 하이브리드도 가능하다.
 */
export const useCalibrationStore = defineStore('calibration', () => {
  const eyeProfile = ref<EyeCalibrationProfile | null>(
    loadFromStorage(EYE_PROFILE_STORAGE_KEY),
  )
  const gazeProfile = ref<GazeCalibrationProfile | null>(
    loadFromStorage(GAZE_PROFILE_STORAGE_KEY),
  )

  /** 눈 뜬/감은 기준 보정이 한 번이라도 끝났는지. 게임 시작 가능 여부 판단에 쓴다. */
  const isEyeCalibrated = computed(() => eyeProfile.value !== null)
  /** 시선 좌표(화면 위치) 보정까지 끝났는지. 그림그리기·에어하키처럼 좌표가 필요한 게임에서만 확인한다. */
  const isGazeCalibrated = computed(() => gazeProfile.value !== null)

  function saveEyeProfile(profile: EyeCalibrationProfile): void {
    eyeProfile.value = profile
    saveToStorage(EYE_PROFILE_STORAGE_KEY, profile)
  }

  function saveGazeProfile(profile: GazeCalibrationProfile): void {
    gazeProfile.value = profile
    saveToStorage(GAZE_PROFILE_STORAGE_KEY, profile)
  }

  function reset(): void {
    eyeProfile.value = null
    gazeProfile.value = null
    removeFromStorage(EYE_PROFILE_STORAGE_KEY)
    removeFromStorage(GAZE_PROFILE_STORAGE_KEY)
  }

  return {
    eyeProfile,
    gazeProfile,
    isEyeCalibrated,
    isGazeCalibrated,
    saveEyeProfile,
    saveGazeProfile,
    reset,
  }
})

function loadFromStorage<T>(key: string): T | null {
  if (typeof globalThis.localStorage === 'undefined') return null
  const raw = globalThis.localStorage.getItem(key)
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

function saveToStorage(key: string, value: unknown): void {
  if (typeof globalThis.localStorage === 'undefined') return
  try {
    globalThis.localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // 저장 실패(용량 초과 등)는 캘리브레이션 자체를 막지 않는다 — 이번 세션 메모리 값으로 계속 진행.
  }
}

function removeFromStorage(key: string): void {
  if (typeof globalThis.localStorage === 'undefined') return
  globalThis.localStorage.removeItem(key)
}

// 기본값을 참조용으로 다시 내보낸다(예: "기준 초기화" 버튼에서 프로필을 기본값으로 되돌릴 때).
export { DEFAULT_CALIBRATION_PROFILE }
