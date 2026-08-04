import { onScopeDispose, ref, watch } from 'vue'

/**
 * 로컬 웹캠(자기 자신 화면)을 다루는 공용 컴포저블.
 *
 * `getUserMedia`만 사용하므로 미디어 서버(OpenVidu) 없이도 동작한다. 준비 화면·게임 화면 어디서든
 * 내 웹캠을 즉시 보여줄 때 쓴다. 원격(상대) 영상은 {@link useLiveKitRoom}가 담당한다.
 *
 * 카메라 트랙이 앱 밖에서(브라우저/OS 권한 해제, 장치 분리 등) 끊기면 `ended` 이벤트가 온다. 이때
 * 상태를 즉시 반영(isActive=false)하고 죽은 스트림을 비워, 이후 {@link start}/{@link restart}가
 * 카메라를 다시 획득할 수 있게 한다. 이 정리를 안 하면 `start`의 재사용 가드가 죽은 스트림을
 * 돌려줘 "끄면 다시 안 켜지는" 문제가 생긴다.
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

  function attachTrackListeners(mediaStream: globalThis.MediaStream) {
    mediaStream
      .getVideoTracks()
      .forEach((track) => track.addEventListener('ended', handleTrackEnded))
  }

  function detachTrackListeners() {
    stream.value
      ?.getVideoTracks()
      .forEach((track) => track.removeEventListener('ended', handleTrackEnded))
  }

  /**
   * 카메라 트랙이 앱 밖에서 끊겼을 때(브라우저에서 캠 끔 등). 남은 트랙을 정리하고 상태를 즉시
   * 반영한다. stream을 비워 두어야 다음 start/restart가 카메라를 새로 획득한다.
   */
  function handleTrackEnded() {
    detachTrackListeners()
    stream.value?.getTracks().forEach((track) => track.stop())
    stream.value = null
    isActive.value = false
    errorName.value = 'ended'
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
      errorName.value = null
      attachTrackListeners(next)
      attach()
      return next
    } catch (error) {
      errorName.value =
        error instanceof globalThis.DOMException ? error.name : 'error'
      return null
    }
  }

  function stop(): void {
    detachTrackListeners()
    stream.value?.getTracks().forEach((track) => track.stop())
    stream.value = null
    isActive.value = false
  }

  /** 카메라를 강제로 다시 획득한다(껐다 켜기·트랙 종료 후 복구). */
  async function restart(): Promise<globalThis.MediaStream | null> {
    stop()
    return start()
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
    restart,
  }
}
