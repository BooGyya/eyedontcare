import { onScopeDispose, ref, shallowRef, watch } from 'vue'
import type {
  LocalTrack,
  RemoteParticipant,
  RemoteTrack,
  Room,
} from 'livekit-client'
import type {
  MediaSessionCredentials,
  RemoteMediaParticipant,
} from '../types/media'

interface ConnectOptions {
  /** 마이크도 함께 송출할지. 기본은 false(시선 게임은 영상만 필요). */
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

  const localVideoRef = ref<globalThis.HTMLVideoElement | null>(null)
  const remoteVideoRef = ref<globalThis.HTMLVideoElement | null>(null)

  let localTrack: LocalTrack | null = null
  let primaryRemoteVideoTrack: RemoteTrack | null = null
  const remoteTracksByIdentity = new Map<
    string,
    { video?: RemoteTrack; audio?: RemoteTrack }
  >()
  const hiddenAudioElements = new Set<globalThis.HTMLMediaElement>()

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
      } else if (options.audio) {
        await nextRoom.localParticipant.enableCameraAndMicrophone()
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
        error instanceof Error ? error.message : '미디어 서버 연결에 실패했어요.'
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
    localVideoRef,
    remoteVideoRef,
    connect,
    disconnect,
  }
}
