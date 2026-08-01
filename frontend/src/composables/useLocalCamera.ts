import { onScopeDispose, ref, watch } from 'vue'

/**
 * 로컬 웹캠(자기 자신 화면)을 다루는 공용 컴포저블.
 *
 * `getUserMedia`만 사용하므로 미디어 서버(OpenVidu) 없이도 동작한다. 준비 화면·게임 화면 어디서든
 * 내 웹캠을 즉시 보여줄 때 쓴다. 원격(상대) 영상은 {@link useLiveKitRoom}가 담당한다.
 */
export function useLocalCamera() {
  const stream = ref<globalThis.MediaStream | null>(null)
  const videoRef = ref<globalThis.HTMLVideoElement | null>(null)
  const isActive = ref(false)
  const errorName = ref<string | null>(null)

  function attach() {
    if (videoRef.value && stream.value) {
      videoRef.value.srcObject = stream.value
      void videoRef.value.play().catch(() => undefined)
    }
  }

  async function start(): Promise<globalThis.MediaStream | null> {
    if (stream.value) return stream.value
    if (!globalThis.navigator?.mediaDevices?.getUserMedia) {
      errorName.value = 'unavailable'
      return null
    }
    try {
      const next = await globalThis.navigator.mediaDevices.getUserMedia({
        video: true,
      })
      stream.value = next
      isActive.value = true
      attach()
      return next
    } catch (error) {
      errorName.value =
        error instanceof globalThis.DOMException ? error.name : 'error'
      return null
    }
  }

  function stop(): void {
    stream.value?.getTracks().forEach((track) => track.stop())
    stream.value = null
    isActive.value = false
  }

  watch(videoRef, attach)

  onScopeDispose(stop)

  return {
    stream,
    videoRef,
    isActive,
    errorName,
    start,
    stop,
  }
}
