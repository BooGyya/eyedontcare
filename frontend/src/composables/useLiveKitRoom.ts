import { onScopeDispose, ref, shallowRef, watch } from 'vue'
import type {
  LocalTrack,
  Participant,
  RemoteAudioTrack,
  RemoteParticipant,
  RemoteTrack,
  RemoteTrackPublication,
  Room,
  TrackPublication,
} from 'livekit-client'
import type {
  MediaSessionCredentials,
  RemoteMediaParticipant,
} from '../types/media'

interface ConnectOptions {
  /**
   * 마이크(음성 대화)도 함께 송출할지. 기본은 false(시선 게임은 영상만 필요).
   * `localTrack`과 함께 주면 카메라는 그 트랙을 쓰고 마이크만 새로 획득한다.
   */
  audio?: boolean
  /**
   * 이미 확보한 로컬 카메라 트랙(getUserMedia). 주면 이 트랙을 그대로 송출하므로
   * 카메라를 두 번 여는 충돌을 피한다. 없으면 LiveKit이 직접 카메라를 켠다.
   */
  localTrack?: globalThis.MediaStreamTrack | null
}

/**
 * 무거운 `livekit-client`는 실제 연결 시점에만 동적으로 불러온다. 대결이 아닌 화면이나
 * 테스트(jsdom)에서는 이 모듈을 import해도 WebRTC 라이브러리가 로드되지 않는다.
 */
let livekit: typeof import('livekit-client') | null = null

/**
 * LiveKit(OpenVidu) 미디어 방 연결을 다루는 공용 컴포저블.
 *
 * 게임 화면은 `localVideoRef`/`remoteVideoRef`를 자기 `<video>` 요소에 바인딩하고
 * {@link connect}만 호출하면 된다. 로컬 카메라 송출과 원격 트랙 구독/attach를 모두 처리하며,
 * 컴포넌트가 사라지면 자동으로 연결을 정리한다. 원격 참가자가 여럿인 경우 `remoteParticipants`로
 * 직접 렌더링할 수 있다.
 */
export function useLiveKitRoom() {
  const room = shallowRef<Room | null>(null)
  const isConnected = ref(false)
  const errorMessage = ref<string | null>(null)
  const remoteParticipants = ref<RemoteMediaParticipant[]>([])
  // 원격 영상 트랙을 구독했는지(렌더링 판단용). mediaStreamTrack 유무에 의존하지 않고
  // 트랙 존재 자체로 판단해야 구독 직후에도 안정적으로 표시된다.
  const hasRemoteVideo = ref(false)
  /** 상대가 카메라 송출을 껐는지(트랙 mute). 화면에 "카메라 꺼짐" 표시를 띄우는 데 쓴다. */
  const isRemoteCameraOff = ref(false)
  /** 내 마이크가 지금 송출 중인지(권한 거부·꺼짐이면 false). UI 토글 표시용. */
  const isMicrophonePublished = ref(false)

  const localVideoRef = ref<globalThis.HTMLVideoElement | null>(null)
  const remoteVideoRef = ref<globalThis.HTMLVideoElement | null>(null)

  let localTrack: LocalTrack | null = null
  let primaryRemoteVideoTrack: RemoteTrack | null = null
  const remoteTracksByIdentity = new Map<
    string,
    { video?: RemoteTrack; audio?: RemoteTrack }
  >()
  const hiddenAudioElements = new Set<globalThis.HTMLMediaElement>()
  // 사용자가 정한 상대 음성 볼륨(0~1). 트랙 구독은 연결 이후 아무 때나 일어나므로,
  // 값을 기억해 뒀다가 새로 구독되는 오디오 트랙에도 즉시 적용한다.
  let desiredRemoteAudioVolume = 1

  function applyRemoteAudioVolume(track: RemoteTrack): void {
    // RemoteAudioTrack.setVolume은 WebAudio 게인을 써서 attach된 요소 전체에 일관 적용된다.
    ;(track as RemoteAudioTrack).setVolume?.(desiredRemoteAudioVolume)
  }

  function syncLocalVideo() {
    if (localTrack && localVideoRef.value) {
      localTrack.attach(localVideoRef.value)
    }
  }

  function syncPrimaryRemoteVideo() {
    if (primaryRemoteVideoTrack && remoteVideoRef.value) {
      primaryRemoteVideoTrack.attach(remoteVideoRef.value)
    }
  }

  function refreshRemoteParticipants() {
    const activeRoom = room.value
    if (!activeRoom) {
      remoteParticipants.value = []
      return
    }
    const list: RemoteMediaParticipant[] = []
    activeRoom.remoteParticipants.forEach((participant) => {
      const tracks = remoteTracksByIdentity.get(participant.identity)
      list.push({
        identity: participant.identity,
        name: participant.name || participant.identity,
        videoTrack: tracks?.video?.mediaStreamTrack ?? null,
        audioTrack: tracks?.audio?.mediaStreamTrack ?? null,
      })
    })
    remoteParticipants.value = list
  }

  function handleTrackSubscribed(
    track: RemoteTrack,
    _publication: RemoteTrackPublication,
    participant: RemoteParticipant,
  ) {
    if (!livekit) return
    const tracks = remoteTracksByIdentity.get(participant.identity) ?? {}
    if (track.kind === livekit.Track.Kind.Video) {
      tracks.video = track
      hasRemoteVideo.value = true
      if (!primaryRemoteVideoTrack) {
        primaryRemoteVideoTrack = track
      }
      syncPrimaryRemoteVideo()
    } else if (track.kind === livekit.Track.Kind.Audio) {
      tracks.audio = track
      const element = track.attach()
      element.style.display = 'none'
      globalThis.document?.body.appendChild(element)
      hiddenAudioElements.add(element)
      applyRemoteAudioVolume(track)
    }
    remoteTracksByIdentity.set(participant.identity, tracks)
    refreshRemoteParticipants()
  }

  function handleTrackUnsubscribed(track: RemoteTrack) {
    track.detach().forEach((element) => {
      element.remove()
      hiddenAudioElements.delete(element as globalThis.HTMLMediaElement)
    })
    if (track === primaryRemoteVideoTrack) {
      primaryRemoteVideoTrack = null
    }
    remoteTracksByIdentity.forEach((tracks, identity) => {
      if (tracks.video === track) tracks.video = undefined
      if (tracks.audio === track) tracks.audio = undefined
      if (!tracks.video && !tracks.audio) {
        remoteTracksByIdentity.delete(identity)
      }
    })
    hasRemoteVideo.value = [...remoteTracksByIdentity.values()].some(
      (tracks) => tracks.video,
    )
    refreshRemoteParticipants()
  }

  /** 상대의 카메라 트랙 mute/unmute를 "카메라 꺼짐" 표시로 반영한다. 내 트랙 이벤트는 무시. */
  function handleTrackMuteChanged(muted: boolean) {
    return (publication: TrackPublication, participant: Participant) => {
      if (!livekit) return
      if (participant === room.value?.localParticipant) return
      if (publication.kind !== livekit.Track.Kind.Video) return
      isRemoteCameraOff.value = muted
    }
  }

  /**
   * 상대 음성 볼륨(0~1)을 조절한다. 아직 오디오 트랙을 구독하기 전이라면 값만 기억해 두었다가
   * 구독 시점에 적용된다.
   */
  function setRemoteAudioVolume(volume: number): void {
    desiredRemoteAudioVolume = Math.min(1, Math.max(0, volume))
    remoteTracksByIdentity.forEach((tracks) => {
      if (tracks.audio) applyRemoteAudioVolume(tracks.audio)
    })
  }

  /**
   * 내 마이크 송출을 켜고 끈다. 켤 때 처음이면 브라우저 마이크 권한을 요청한다.
   * 권한 거부 등으로 실패하면 false를 돌려주고 상태를 꺼짐으로 유지한다(게임은 계속).
   */
  async function setMicrophoneEnabled(enabled: boolean): Promise<boolean> {
    const activeRoom = room.value
    if (!activeRoom || !isConnected.value) return false
    try {
      await activeRoom.localParticipant.setMicrophoneEnabled(enabled)
      isMicrophonePublished.value =
        enabled && activeRoom.localParticipant.isMicrophoneEnabled
      return isMicrophonePublished.value === enabled
    } catch {
      isMicrophonePublished.value = false
      return false
    }
  }

  /**
   * 내 카메라 **송출**을 켜고 끈다(상대 화면 기준). 우리가 넘긴 getUserMedia 트랙은
   * user-provided라 LiveKit이 mute 시에도 장치를 멈추지 않으므로, 시선 추적은 계속 동작한다.
   */
  async function setCameraPublishEnabled(enabled: boolean): Promise<void> {
    if (!localTrack) return
    try {
      if (enabled) await localTrack.unmute()
      else await localTrack.mute()
    } catch {
      // 송출 토글 실패는 게임 진행에 치명적이지 않으므로 조용히 무시한다.
    }
  }

  async function connect(
    credentials: MediaSessionCredentials,
    options: ConnectOptions = {},
  ) {
    errorMessage.value = null
    try {
      if (!livekit) {
        livekit = await import('livekit-client')
      }
      const { Room, RoomEvent, Track } = livekit

      // 두 플레이어가 창을 번갈아 볼 때 비활성(hidden) 창이 끊기지 않도록 자동 종료를 끈다.
      const nextRoom = new Room({
        adaptiveStream: true,
        dynacast: true,
        disconnectOnPageLeave: false,
      })
      nextRoom
        .on(RoomEvent.TrackSubscribed, handleTrackSubscribed)
        .on(RoomEvent.TrackUnsubscribed, handleTrackUnsubscribed)
        .on(RoomEvent.TrackPublished, refreshRemoteParticipants)
        .on(RoomEvent.LocalTrackPublished, refreshRemoteParticipants)
        .on(RoomEvent.ParticipantConnected, refreshRemoteParticipants)
        .on(RoomEvent.ParticipantDisconnected, refreshRemoteParticipants)
        .on(RoomEvent.TrackMuted, handleTrackMuteChanged(true))
        .on(RoomEvent.TrackUnmuted, handleTrackMuteChanged(false))
        .on(RoomEvent.Disconnected, () => {
          isConnected.value = false
        })
      room.value = nextRoom

      await nextRoom.connect(credentials.openviduUrl, credentials.token)
      isConnected.value = true

      if (options.localTrack) {
        await nextRoom.localParticipant.publishTrack(options.localTrack, {
          source: Track.Source.Camera,
        })
        if (options.audio) {
          // 마이크는 카메라와 달리 LiveKit이 직접 획득한다(시선 추적 스트림은 영상 전용).
          // 권한 거부로 실패해도 게임은 계속되어야 하므로 연결 실패로 취급하지 않는다.
          await setMicrophoneEnabled(true)
        }
      } else if (options.audio) {
        await nextRoom.localParticipant.enableCameraAndMicrophone()
        isMicrophonePublished.value =
          nextRoom.localParticipant.isMicrophoneEnabled
      } else {
        await nextRoom.localParticipant.setCameraEnabled(true)
      }
      localTrack =
        nextRoom.localParticipant.getTrackPublication(Track.Source.Camera)
          ?.track ?? null
      syncLocalVideo()
      refreshRemoteParticipants()
    } catch (error) {
      errorMessage.value =
        error instanceof Error
          ? error.message
          : '미디어 서버 연결에 실패했어요.'
      await disconnect()
    }
  }

  async function disconnect() {
    hiddenAudioElements.forEach((element) => element.remove())
    hiddenAudioElements.clear()
    remoteTracksByIdentity.clear()
    primaryRemoteVideoTrack = null
    localTrack = null
    remoteParticipants.value = []
    hasRemoteVideo.value = false
    isRemoteCameraOff.value = false
    isMicrophonePublished.value = false
    isConnected.value = false
    const activeRoom = room.value
    room.value = null
    if (activeRoom) {
      await activeRoom.disconnect()
    }
  }

  watch(localVideoRef, syncLocalVideo)
  watch(remoteVideoRef, syncPrimaryRemoteVideo)

  onScopeDispose(() => {
    void disconnect()
  })

  return {
    isConnected,
    errorMessage,
    remoteParticipants,
    hasRemoteVideo,
    isRemoteCameraOff,
    isMicrophonePublished,
    localVideoRef,
    remoteVideoRef,
    connect,
    disconnect,
    setRemoteAudioVolume,
    setMicrophoneEnabled,
    setCameraPublishEnabled,
  }
}
