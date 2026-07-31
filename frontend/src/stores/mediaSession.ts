import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { MediaSessionCredentials } from '../types/media'

/**
 * 대기방에서 받은 미디어 접속 정보를 플레이 화면으로 넘기는 저장소.
 *
 * `GAME_START` 이벤트를 처리하는 곳에서 {@link setCredentials}로 채우고, 플레이 화면이
 * {@link useLiveKitRoom}로 연결한 뒤 화면을 벗어날 때 {@link clear}로 비운다.
 */
export const useMediaSessionStore = defineStore('mediaSession', () => {
  const credentials = ref<MediaSessionCredentials | null>(null)

  const hasCredentials = computed(() => credentials.value !== null)

  function setCredentials(next: MediaSessionCredentials) {
    credentials.value = next
  }

  function clear() {
    credentials.value = null
  }

  return {
    credentials,
    hasCredentials,
    setCredentials,
    clear,
  }
})
