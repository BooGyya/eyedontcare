import { onScopeDispose, ref, shallowRef, watch } from 'vue'
import {
  Room,
  RoomEvent,
  Track,
  type LocalTrack,
  type RemoteParticipant,
  type RemoteTrack,
} from 'livekit-client'
import type {
  MediaSessionCredentials,
  RemoteMediaParticipant,
} from '../types/media'

interface ConnectOptions {
  /** 마이크도 함께 송출할지. 기본은 false(시선 게임은 영상만 필요). */
  audio?: boolean
}

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
    const tracks = remoteTracksByIdentity.get(participant.identity) ?? {}
    if (track.kind === Track.Kind.Video) {
      tracks.video = track
      if (!primaryRemoteVideoTrack) {
        primaryRemoteVideoTrack = track
        syncPrimaryRemoteVideo()
      }
    } else if (track.kind === Track.Kind.Audio) {
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
    refreshRemoteParticipants()
  }

  function registerRoomEvents(target: Room) {
    target
      .on(RoomEvent.TrackSubscribed, handleTrackSubscribed)
      .on(RoomEvent.TrackUnsubscribed, handleTrackUnsubscribed)
      .on(RoomEvent.ParticipantDisconnected, refreshRemoteParticipants)
      .on(RoomEvent.Disconnected, () => {
        isConnected.value = false
      })
  }

  async function connect(
    credentials: MediaSessionCredentials,
    options: ConnectOptions = {},
  ) {
    errorMessage.value = null
    try {
      const nextRoom = new Room({ adaptiveStream: true, dynacast: true })
      registerRoomEvents(nextRoom)
      room.value = nextRoom

      await nextRoom.connect(credentials.openviduUrl, credentials.token)
      isConnected.value = true

      if (options.audio) {
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
    localVideoRef,
    remoteVideoRef,
    connect,
    disconnect,
  }
}
