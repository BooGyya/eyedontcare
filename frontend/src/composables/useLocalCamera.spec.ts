import { effectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useLocalCamera } from './useLocalCamera'

interface FakeTrack extends EventTarget {
  kind: string
  stop: ReturnType<typeof vi.fn>
}

function makeTrack(): FakeTrack {
  return Object.assign(new EventTarget(), {
    kind: 'video',
    stop: vi.fn(),
  }) as FakeTrack
}

function makeStream(track: FakeTrack) {
  return {
    getTracks: () => [track],
    getVideoTracks: () => [track],
  } as unknown as globalThis.MediaStream
}

let lastTrack: FakeTrack
const getUserMedia = vi.fn(async () => {
  lastTrack = makeTrack()
  return makeStream(lastTrack)
})

/** effectScope 안에서 실행해 onScopeDispose 경고를 피한다. */
function runInScope<T>(factory: () => T): T {
  let value!: T
  effectScope().run(() => {
    value = factory()
  })
  return value
}

describe('useLocalCamera', () => {
  beforeEach(() => {
    getUserMedia.mockClear()
    Object.defineProperty(globalThis.navigator, 'mediaDevices', {
      value: { getUserMedia },
      configurable: true,
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('start가 카메라를 획득하고 활성 상태로 만든다', async () => {
    const camera = runInScope(() => useLocalCamera())

    const stream = await camera.start()

    expect(stream).not.toBeNull()
    expect(camera.isActive.value).toBe(true)
    expect(getUserMedia).toHaveBeenCalledTimes(1)
  })

  it('트랙이 밖에서 끊기면(ended) 비활성으로 반영하고 스트림을 비운다', async () => {
    const camera = runInScope(() => useLocalCamera())
    await camera.start()

    lastTrack.dispatchEvent(new Event('ended'))

    expect(camera.isActive.value).toBe(false)
    expect(camera.errorName.value).toBe('ended')
    expect(camera.stream.value).toBeNull()
  })

  it('트랙 종료 후 start가 카메라를 다시 획득한다(끄면 다시 안 켜지는 문제 방지)', async () => {
    const camera = runInScope(() => useLocalCamera())
    await camera.start()
    lastTrack.dispatchEvent(new Event('ended'))

    const restarted = await camera.start()

    expect(restarted).not.toBeNull()
    expect(camera.isActive.value).toBe(true)
    expect(getUserMedia).toHaveBeenCalledTimes(2)
  })

  it('restart는 기존 트랙을 멈추고 새로 획득한다', async () => {
    const camera = runInScope(() => useLocalCamera())
    await camera.start()
    const firstTrack = lastTrack

    await camera.restart()

    expect(firstTrack.stop).toHaveBeenCalled()
    expect(camera.isActive.value).toBe(true)
    expect(getUserMedia).toHaveBeenCalledTimes(2)
  })
})
