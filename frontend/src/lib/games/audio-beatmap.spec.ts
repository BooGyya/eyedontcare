import { describe, expect, it } from 'vitest'
import {
  calculateEnergyFrames,
  createBeatmapEntries,
  estimateBpmFromOnsets,
  filterByDensity,
  pickEnergyOnsets,
  pickTopEnergyFrames,
} from './audio-beatmap'

describe('audio-beatmap', () => {
  it('calculateEnergyFrames는 무음보다 소리가 큰 구간에서 더 높은 에너지를 낸다', () => {
    const sampleRate = 1000
    const samples = new Float32Array(200)
    // 뒷부분(100~200)에 진폭이 큰 신호를 넣는다.
    for (let index = 100; index < 200; index += 1) {
      samples[index] = Math.sin(index) * 0.9
    }

    const frames = calculateEnergyFrames(samples, sampleRate, 50)
    expect(frames.length).toBeGreaterThan(0)
    const quietFrame = frames[0]
    const loudFrame = frames[frames.length - 1]
    expect(loudFrame.energy).toBeGreaterThan(quietFrame.energy)
  })

  it('pickEnergyOnsets는 급격히 커지는 지점만 온셋으로 잡는다', () => {
    const frames = Array.from({ length: 40 }, (_, index) => ({
      timeMs: index * 50,
      energy: index === 20 ? 1 : 0.05,
      peak: index === 20 ? 1 : 0.05,
    }))

    const onsets = pickEnergyOnsets(frames, {
      frameMs: 50,
      minGapMs: 100,
      threshold: 1.2,
      localWindow: 10,
      minTimeMs: 0,
    })

    expect(onsets.length).toBeGreaterThan(0)
    expect(onsets[0].timeMs).toBe(1000)
  })

  it('pickTopEnergyFrames는 최소 간격을 지키며 에너지가 큰 순서로 고른다', () => {
    const frames = [
      { timeMs: 0, energy: 0.9, peak: 0.9 },
      { timeMs: 50, energy: 0.8, peak: 0.8 }, // 0과 너무 가까워서 제외돼야 함
      { timeMs: 400, energy: 0.7, peak: 0.7 },
      { timeMs: 900, energy: 0.6, peak: 0.6 },
    ]

    const picked = pickTopEnergyFrames(frames, {
      minGapMs: 300,
      minTimeMs: 0,
      limit: 3,
    })

    expect(picked.map((item) => item.timeMs)).toEqual([0, 400, 900])
  })

  it('createBeatmapEntries는 강한 온셋은 양쪽 눈, 나머지는 좌우 번갈아 배정한다', () => {
    // 난이도 완화로 듀얼 노트 임계값이 0.9로 올라갔다 — 0.96처럼 확실히 그 위인 값을 쓴다.
    const entries = createBeatmapEntries([
      { timeMs: 0, strength: 0.96 },
      { timeMs: 500, strength: 0.1 },
      { timeMs: 1000, strength: 0.1 },
    ])

    expect(entries[0].lanes).toEqual(['LEFT_EYE', 'RIGHT_EYE'])
    // index % 2 === 0(짝수)이면 LEFT_EYE, 홀수면 RIGHT_EYE.
    expect(entries[1].lanes).toEqual(['RIGHT_EYE'])
    expect(entries[2].lanes).toEqual(['LEFT_EYE'])
  })

  it('createBeatmapEntries: 임계값(0.9) 미만이면 듀얼 노트가 아니다(난이도 완화 확인)', () => {
    const entries = createBeatmapEntries([{ timeMs: 0, strength: 0.85 }])
    expect(entries[0].lanes).toEqual(['LEFT_EYE'])
  })

  it('estimateBpmFromOnsets는 온셋 간격의 중앙값으로 BPM을 추정한다', () => {
    // 정확히 500ms 간격(=120bpm)으로 온셋이 찍혔다고 가정.
    const onsets = [0, 500, 1000, 1500, 2000].map((timeMs) => ({ timeMs }))
    expect(estimateBpmFromOnsets(onsets)).toBe(120)
  })

  it('estimateBpmFromOnsets는 온셋이 2개 미만이면 0을 반환한다', () => {
    expect(estimateBpmFromOnsets([{ timeMs: 0 }])).toBe(0)
    expect(estimateBpmFromOnsets([])).toBe(0)
  })

  describe('filterByDensity (난이도 완화: 노트 개수 줄이기)', () => {
    it('keepRatio가 1 이상이면 그대로 반환한다', () => {
      const items = [1, 2, 3, 4]
      expect(filterByDensity(items, 1)).toEqual(items)
      expect(filterByDensity(items, 1.5)).toEqual(items)
    })

    it('keepRatio가 0 이하이면 전부 제거한다', () => {
      expect(filterByDensity([1, 2, 3], 0)).toEqual([])
    })

    it('keepRatio=0.5면 절반을 균등하게(Bresenham 방식) 남긴다', () => {
      const items = Array.from({ length: 10 }, (_, index) => index)
      const kept = filterByDensity(items, 0.5)
      expect(kept.length).toBe(5)
      // 균등하게 분산되어야 한다 — 앞부분에만 몰리거나 뒷부분만 비어선 안 된다.
      expect(kept).toEqual([1, 3, 5, 7, 9])
    })

    it('keepRatio=0.72(기본값)면 대략 그 비율만큼만 남고 순서는 유지된다', () => {
      const items = Array.from({ length: 100 }, (_, index) => index)
      const kept = filterByDensity(items, 0.72)
      expect(kept.length).toBeGreaterThanOrEqual(70)
      expect(kept.length).toBeLessThanOrEqual(74)
      // 원래 순서를 그대로 유지해야 한다(순서가 뒤바뀌면 리듬이 깨진다).
      expect(kept).toEqual([...kept].sort((a, b) => a - b))
    })
  })
})
