/**
 * 오디오 파일 → 비트맵 분석기.
 *
 * `ai_game` 프로토타입의 `audio-beatmap.js`를 그대로 이식했다. Web Audio API로 오디오를
 * 디코딩한 뒤, 에너지 프레임을 잘라 온셋(비트 타이밍)을 검출하고 BPM을 추정한다. 검출된 비트마다
 * Goertzel 알고리즘으로 멜로디에 가까운 주파수를 뽑아 왼쪽/오른쪽/양쪽 눈 레인을 배정한다 —
 * 저음이면 왼쪽, 고음이면 오른쪽, 강한 비트나 큰 음정 도약이면 양쪽.
 *
 * 순수 계산 함수 위주라 대부분 유닛 테스트 가능하다(`analyzeAudioUrlToBeatmap`만 fetch/디코딩이
 * 필요해 실제 오디오 없이는 테스트하기 어렵다).
 *
 * ⚠️ "너무 어렵다"는 피드백이 계속 나와서 두 가지를 완화했다.
 * 1. **노트 개수**: 비트 그리드가 만들어낸 노트에 {@link DEFAULT_NOTE_DENSITY} 비율만큼만
 *    남기는 필터({@link filterByDensity})를 최종 단계에 추가했다. 균등하게 분산해서 걷어내므로
 *    (Bresenham 방식) 특정 구간만 갑자기 비거나 몰리지 않는다.
 * 2. **동시에 양쪽 눈을 감아야 하는 노트(듀얼 노트) 빈도**: {@link createMelodyAwareBeatmapEntries}/
 *    {@link createBeatmapEntries}의 듀얼 노트 판정 임계값을 더 엄격하게(더 높게) 올렸다 — 아주
 *    강한 비트나 큰 음정 도약에서만 듀얼 노트가 나오게 했다.
 */
import type { RhythmBeatmapEntry, RhythmLane } from './rhythm-core'

const DEFAULT_FRAME_MS = 46
const DEFAULT_MIN_GAP_MS = 260
const DEFAULT_THRESHOLD = 1.34
const DEFAULT_LOCAL_WINDOW = 18
const DEFAULT_MIN_BEATS = 8
const DEFAULT_MIN_BPM = 80
const DEFAULT_MAX_BPM = 180
const DEFAULT_MELODY_WINDOW_MS = 185
/** 최종 노트 중 이 비율만 남긴다(나머지는 균등하게 걷어낸다) — 난이도 완화용. */
const DEFAULT_NOTE_DENSITY = 0.72
const MELODY_FREQUENCIES: readonly number[] = Object.freeze([
  110, 130.81, 155.56, 196, 246.94, 293.66, 349.23, 392, 493.88, 587.33, 698.46,
  783.99, 987.77, 1174.66, 1396.91, 1567.98, 1975.53,
])

export interface MelodyFeature {
  frequencyHz: number
  lowEnergy: number
  midEnergy: number
  highEnergy: number
  clarity: number
}

export interface AudioBeatmap {
  durationMs: number
  frameMs: number
  bpmEstimate: number
  notes: RhythmBeatmapEntry[]
  source: string
  stats: {
    frameCount: number
    onsetCount: number
    threshold: number
    minGapMs: number
    mode: string
    phaseOffsetMs: number
  }
}

export interface BeatmapOptions {
  frameMs?: number
  minGapMs?: number
  threshold?: number
  localWindow?: number
  minTimeMs?: number
  maxNotes?: number
  minBeats?: number
  minBpm?: number
  maxBpm?: number
  melodyWindowMs?: number
  /** 최종 노트 중 남길 비율(0~1). 기본값 {@link DEFAULT_NOTE_DENSITY} — 난이도 완화용. */
  noteDensity?: number
}

interface EnergyFrame {
  timeMs: number
  energy: number
  peak: number
}

interface OnsetCandidate {
  timeMs: number
  strength: number
  energy?: number
}

/** 브라우저 캐시/CORS 정책상 오디오는 같은 오리진(프론트엔드가 서빙하는 정적 파일)에서 가져와야 한다. */
export async function analyzeAudioUrlToBeatmap(
  sourceUrl: string,
  options: BeatmapOptions = {},
): Promise<AudioBeatmap> {
  const response = await globalThis.fetch(sourceUrl)
  if (!response.ok) {
    throw new Error(`audio fetch failed: ${response.status}`)
  }
  const arrayBuffer = await response.arrayBuffer()
  const AudioContextClass =
    globalThis.AudioContext ??
    (globalThis as unknown as { webkitAudioContext?: typeof AudioContext })
      .webkitAudioContext
  if (!AudioContextClass) {
    throw new Error('Web Audio API를 사용할 수 없습니다.')
  }
  const audioContext = new AudioContextClass()
  try {
    const audioBuffer = await audioContext.decodeAudioData(arrayBuffer.slice(0))
    return analyzeAudioBufferToBeatmap(audioBuffer, {
      ...options,
      source: sourceUrl,
    })
  } finally {
    void audioContext.close()
  }
}

export function analyzeAudioBufferToBeatmap(
  audioBuffer: AudioBuffer,
  options: BeatmapOptions & { source?: string } = {},
): AudioBeatmap {
  const channelData = mixAudioChannels(audioBuffer)
  const frameMs = normalizePositive(options.frameMs, DEFAULT_FRAME_MS)
  const minGapMs = normalizePositive(options.minGapMs, DEFAULT_MIN_GAP_MS)
  const threshold = normalizePositive(options.threshold, DEFAULT_THRESHOLD)
  const localWindow = Math.max(
    Math.round(normalizePositive(options.localWindow, DEFAULT_LOCAL_WINDOW)),
    4,
  )
  const minTimeMs = Math.max(Math.round(options.minTimeMs ?? 0), 0)
  const maxNotes = Math.max(Math.round(options.maxNotes ?? 220), 1)
  const minBeats = Math.max(
    Math.round(options.minBeats ?? DEFAULT_MIN_BEATS),
    1,
  )
  const minBpm = Math.max(Math.round(options.minBpm ?? DEFAULT_MIN_BPM), 40)
  const maxBpm = Math.max(
    Math.round(options.maxBpm ?? DEFAULT_MAX_BPM),
    minBpm + 1,
  )
  const melodyWindowMs = Math.max(
    Math.round(options.melodyWindowMs ?? DEFAULT_MELODY_WINDOW_MS),
    60,
  )
  const noteDensity = Math.min(
    Math.max(options.noteDensity ?? DEFAULT_NOTE_DENSITY, 0.1),
    1,
  )
  const energies = calculateEnergyFrames(
    channelData,
    audioBuffer.sampleRate,
    frameMs,
  )
  let onsets = pickEnergyOnsets(energies, {
    frameMs,
    minGapMs,
    threshold,
    localWindow,
    minTimeMs,
  })

  if (onsets.length < minBeats) {
    onsets = pickTopEnergyFrames(energies, {
      minTimeMs,
      minGapMs,
      limit: Math.max(minBeats, Math.min(maxNotes, 48)),
    })
  }

  const gridBpm = estimateBpmFromEnergyFrames(energies, {
    frameMs,
    minBpm,
    maxBpm,
  })
  const onsetBpm = estimateBpmFromOnsets(onsets)
  const bpmEstimate = chooseBpmEstimate(gridBpm, onsetBpm)
  let notes: RhythmBeatmapEntry[] = []
  let mode = 'onset'
  let phaseOffsetMs = 0
  if (bpmEstimate > 0) {
    phaseOffsetMs = estimateBeatGridOffset(energies, {
      frameMs,
      bpm: bpmEstimate,
    })
    notes = createBeatGridEntries(energies, {
      frameMs,
      bpm: bpmEstimate,
      phaseOffsetMs,
      minTimeMs,
      maxNotes,
      samples: channelData,
      sampleRate: audioBuffer.sampleRate,
      melodyWindowMs,
    })
    mode = 'beat-grid+melody'
  }

  if (notes.length < minBeats) {
    notes = createBeatmapEntries(onsets.slice(0, maxNotes))
    mode = 'onset'
    phaseOffsetMs = 0
  }

  // 난이도 완화: minBeats 하한 판정이 끝난 뒤(=게임 진행에 필요한 최소 노트 수는 이미
  // 확보된 뒤) 최종 노트에만 밀도 필터를 적용한다. 판정 전에 걷어내면 엉뚱한 fallback(onset
  // 모드)로 잘못 넘어갈 수 있어서 순서가 중요하다.
  notes = filterByDensity(notes, noteDensity)

  return {
    durationMs: Math.round(audioBuffer.duration * 1000),
    frameMs,
    bpmEstimate,
    notes,
    source: options.source ?? 'audio-buffer',
    stats: {
      frameCount: energies.length,
      onsetCount: notes.length,
      threshold,
      minGapMs,
      mode,
      phaseOffsetMs,
    },
  }
}

export function mixAudioChannels(audioBuffer: AudioBuffer): Float32Array {
  const length = audioBuffer.length
  const channelCount = Math.max(audioBuffer.numberOfChannels, 1)
  const output = new Float32Array(length)
  for (let channel = 0; channel < channelCount; channel += 1) {
    const data = audioBuffer.getChannelData(channel)
    for (let index = 0; index < length; index += 1) {
      output[index] += data[index] / channelCount
    }
  }
  return output
}

export function calculateEnergyFrames(
  samples: Float32Array,
  sampleRate: number,
  frameMs = DEFAULT_FRAME_MS,
): EnergyFrame[] {
  const frameSize = Math.max(Math.round(sampleRate * (frameMs / 1000)), 1)
  const frames: EnergyFrame[] = []
  for (let start = 0; start < samples.length; start += frameSize) {
    let sum = 0
    let peak = 0
    const end = Math.min(start + frameSize, samples.length)
    for (let index = start; index < end; index += 1) {
      const absolute = Math.abs(samples[index])
      sum += absolute * absolute
      peak = Math.max(peak, absolute)
    }
    const size = Math.max(end - start, 1)
    frames.push({
      timeMs: (start / sampleRate) * 1000,
      energy: Math.sqrt(sum / size),
      peak,
    })
  }
  return frames
}

export function pickEnergyOnsets(
  frames: EnergyFrame[],
  options: {
    frameMs: number
    minGapMs: number
    threshold: number
    localWindow: number
    minTimeMs: number
  },
): OnsetCandidate[] {
  const onsets: OnsetCandidate[] = []
  let lastOnsetAt = Number.NEGATIVE_INFINITY
  for (let index = 1; index < frames.length; index += 1) {
    const frame = frames[index]
    if (frame.timeMs < options.minTimeMs) {
      continue
    }
    const start = Math.max(index - options.localWindow, 0)
    const history = frames.slice(start, index)
    const average = mean(history.map((item) => item.energy))
    const deviation = standardDeviation(
      history.map((item) => item.energy),
      average,
    )
    const previous = frames[index - 1]
    const dynamicThreshold = average + deviation * options.threshold
    const risingFast = frame.energy > previous.energy * 1.12
    const isLocalPeak =
      frame.energy >= frames[Math.min(index + 1, frames.length - 1)].energy
    if (
      frame.energy > dynamicThreshold &&
      risingFast &&
      isLocalPeak &&
      frame.timeMs - lastOnsetAt >= options.minGapMs
    ) {
      const strength =
        deviation > 0 ? (frame.energy - average) / deviation : frame.energy
      onsets.push({
        timeMs: Math.round(frame.timeMs),
        strength,
        energy: frame.energy,
      })
      lastOnsetAt = frame.timeMs
    }
  }
  return normalizeOnsetStrengths(onsets)
}

export function pickTopEnergyFrames(
  frames: EnergyFrame[],
  options: { minGapMs: number; minTimeMs: number; limit: number },
): OnsetCandidate[] {
  const candidates = frames
    .filter((frame) => frame.timeMs >= options.minTimeMs)
    .map((frame) => ({
      timeMs: Math.round(frame.timeMs),
      strength: frame.energy,
      energy: frame.energy,
    }))
    .sort((a, b) => b.energy - a.energy)
  const selected: OnsetCandidate[] = []
  for (const candidate of candidates) {
    if (selected.length >= options.limit) {
      break
    }
    if (
      selected.every(
        (item) => Math.abs(item.timeMs - candidate.timeMs) >= options.minGapMs,
      )
    ) {
      selected.push(candidate)
    }
  }
  selected.sort((a, b) => a.timeMs - b.timeMs)
  return normalizeOnsetStrengths(selected)
}

export function createBeatmapEntries(
  onsets: OnsetCandidate[],
): RhythmBeatmapEntry[] {
  return onsets.map((onset, index) => {
    // 난이도 완화: 듀얼 노트(양쪽 눈 동시)는 아주 강한 비트에서만 나오게 임계값을 올렸다.
    const lanes: RhythmLane[] =
      onset.strength >= 0.9
        ? ['LEFT_EYE', 'RIGHT_EYE']
        : index % 2 === 0
          ? ['LEFT_EYE']
          : ['RIGHT_EYE']
    return {
      timeMs: onset.timeMs,
      lanes,
      strength: Number(onset.strength.toFixed(3)),
    }
  })
}

interface BeatGridOptions {
  frameMs: number
  bpm: number
  phaseOffsetMs: number
  minTimeMs: number
  maxNotes: number
  samples?: Float32Array
  sampleRate?: number
  melodyWindowMs?: number
}

export function createBeatGridEntries(
  frames: EnergyFrame[],
  options: BeatGridOptions,
): RhythmBeatmapEntry[] {
  if (
    frames.length === 0 ||
    !Number.isFinite(options.bpm) ||
    options.bpm <= 0
  ) {
    return []
  }
  const intervalMs = 60000 / options.bpm
  const durationMs = frames[frames.length - 1].timeMs + options.frameMs
  const energies: { timeMs: number; energy: number }[] = []
  for (
    let timeMs = options.phaseOffsetMs;
    timeMs <= durationMs;
    timeMs += intervalMs
  ) {
    if (timeMs < options.minTimeMs) {
      continue
    }
    energies.push({
      timeMs: Math.round(timeMs),
      energy: sampleEnergyAt(
        frames,
        timeMs,
        Math.max(options.frameMs * 1.5, 60),
      ),
    })
  }

  const beatCount = energies.length
  if (beatCount === 0) {
    return []
  }

  const stride = Math.max(Math.ceil(beatCount / options.maxNotes), 1)
  const selected = energies.filter((_, index) => index % stride === 0)
  const normalized = normalizeOnsetStrengths(
    selected.map((item) => ({
      timeMs: item.timeMs,
      strength: item.energy,
      energy: item.energy,
    })),
  )
  if (
    options.samples &&
    Number.isFinite(options.sampleRate) &&
    (options.sampleRate ?? 0) > 0
  ) {
    return createMelodyAwareBeatmapEntries(
      normalized.map((item) => ({
        ...item,
        melody: estimateMelodyFeatureAt(
          options.samples as Float32Array,
          Number(options.sampleRate),
          item.timeMs,
          options.melodyWindowMs ?? DEFAULT_MELODY_WINDOW_MS,
        ),
      })),
    )
  }
  return createBeatmapEntries(normalized)
}

export function estimateMelodyFeatureAt(
  samples: Float32Array,
  sampleRate: number,
  timeMs: number,
  windowMs = DEFAULT_MELODY_WINDOW_MS,
): MelodyFeature {
  if (!samples.length || !Number.isFinite(sampleRate) || sampleRate <= 0) {
    return makeEmptyMelodyFeature()
  }

  const safeWindowMs = Math.max(Math.round(windowMs), 60)
  const halfWindow = Math.round((sampleRate * safeWindowMs) / 2000)
  const center = Math.round((timeMs / 1000) * sampleRate)
  const start = Math.max(center - halfWindow, 0)
  const end = Math.min(center + halfWindow, samples.length)
  if (end - start < 8) {
    return makeEmptyMelodyFeature()
  }

  let totalPower = 0
  let weightedFrequency = 0
  let strongestFrequency = 0
  let strongestPower = 0
  let lowEnergy = 0
  let midEnergy = 0
  let highEnergy = 0
  const nyquist = sampleRate / 2

  for (const frequency of MELODY_FREQUENCIES) {
    if (frequency >= nyquist) {
      continue
    }
    const power = goertzelPower(samples, start, end, sampleRate, frequency)
    totalPower += power
    weightedFrequency += frequency * power
    if (frequency < 260) {
      lowEnergy += power
    } else if (frequency < 700) {
      midEnergy += power
    } else {
      highEnergy += power
    }
    if (power > strongestPower) {
      strongestPower = power
      strongestFrequency = frequency
    }
  }

  if (totalPower <= 0) {
    return makeEmptyMelodyFeature()
  }

  const centroid = weightedFrequency / totalPower
  return {
    frequencyHz: Math.round(strongestFrequency * 0.68 + centroid * 0.32),
    lowEnergy,
    midEnergy,
    highEnergy,
    clarity: strongestPower / totalPower,
  }
}

export function createMelodyAwareBeatmapEntries(
  beats: { timeMs: number; strength: number; melody: MelodyFeature }[],
): RhythmBeatmapEntry[] {
  if (beats.length === 0) {
    return []
  }
  const validFrequencies = beats
    .map((beat) => beat.melody)
    .filter((melody) => melody.frequencyHz > 0 && melody.clarity >= 0.12)
    .map((melody) => melody.frequencyHz)

  if (validFrequencies.length < Math.max(3, beats.length * 0.2)) {
    return createBeatmapEntries(beats)
  }

  const centerFrequency = median(validFrequencies)
  let previousFrequency = 0
  return beats.map((beat, index) => {
    const melody = beat.melody
    const hasMelody = melody.frequencyHz > 0 && melody.clarity >= 0.12
    const jumpOctaves =
      hasMelody && previousFrequency > 0
        ? Math.abs(Math.log2(melody.frequencyHz / previousFrequency))
        : 0
    let lanes: RhythmLane[]
    // 난이도 완화: 듀얼 노트(양쪽 눈 동시)는 아주 강한 비트나 훨씬 큰 음정 도약에서만
    // 나오게 임계값을 올렸다(기존 0.92/0.42+0.48 → 0.95/0.55+0.62).
    if (
      beat.strength >= 0.95 ||
      (jumpOctaves >= 0.55 && beat.strength >= 0.62)
    ) {
      lanes = ['LEFT_EYE', 'RIGHT_EYE']
    } else if (hasMelody) {
      lanes =
        melody.frequencyHz >= centerFrequency ? ['RIGHT_EYE'] : ['LEFT_EYE']
    } else {
      lanes = index % 2 === 0 ? ['LEFT_EYE'] : ['RIGHT_EYE']
    }

    if (hasMelody) {
      previousFrequency = melody.frequencyHz
    }

    return {
      timeMs: beat.timeMs,
      lanes,
      strength: Number(beat.strength.toFixed(3)),
    }
  })
}

export function estimateBpmFromEnergyFrames(
  frames: EnergyFrame[],
  options: { frameMs: number; minBpm?: number; maxBpm?: number },
): number {
  if (frames.length < 8) {
    return 0
  }
  const minBpm = Math.max(Math.round(options.minBpm ?? DEFAULT_MIN_BPM), 40)
  const maxBpm = Math.max(
    Math.round(options.maxBpm ?? DEFAULT_MAX_BPM),
    minBpm + 1,
  )
  const envelope = calculateOnsetEnvelope(frames)
  const minLag = Math.max(Math.round(60000 / maxBpm / options.frameMs), 1)
  const maxLag = Math.max(Math.round(60000 / minBpm / options.frameMs), minLag)
  let bestLag = 0
  let bestScore = 0
  for (let lag = minLag; lag <= maxLag; lag += 1) {
    let score = 0
    let count = 0
    for (let index = lag; index < envelope.length; index += 1) {
      score += envelope[index] * envelope[index - lag]
      count += 1
    }
    const normalizedScore = count > 0 ? score / count : 0
    if (normalizedScore > bestScore) {
      bestScore = normalizedScore
      bestLag = lag
    }
  }
  return bestScore > 0 && bestLag > 0
    ? Math.round(60000 / (bestLag * options.frameMs))
    : 0
}

export function estimateBeatGridOffset(
  frames: EnergyFrame[],
  options: { frameMs: number; bpm: number },
): number {
  if (
    frames.length === 0 ||
    !Number.isFinite(options.bpm) ||
    options.bpm <= 0
  ) {
    return 0
  }
  const envelope = calculateOnsetEnvelope(frames)
  const lag = Math.max(Math.round(60000 / options.bpm / options.frameMs), 1)
  let bestOffsetIndex = 0
  let bestScore = 0
  for (let offset = 0; offset < lag; offset += 1) {
    let score = 0
    for (let index = offset; index < envelope.length; index += lag) {
      score +=
        (envelope[index - 1] ?? 0) * 0.35 +
        envelope[index] +
        (envelope[index + 1] ?? 0) * 0.35
    }
    if (score > bestScore) {
      bestScore = score
      bestOffsetIndex = offset
    }
  }
  return Math.round(frames[bestOffsetIndex]?.timeMs ?? 0)
}

export function estimateBpmFromOnsets(onsets: { timeMs: number }[]): number {
  if (onsets.length < 2) {
    return 0
  }
  const gaps: number[] = []
  for (let index = 1; index < onsets.length; index += 1) {
    const gap = onsets[index].timeMs - onsets[index - 1].timeMs
    if (gap >= 250 && gap <= 2000) {
      gaps.push(gap)
    }
  }
  if (gaps.length === 0) {
    return 0
  }
  const medianGap = median(gaps)
  return Math.round(60000 / medianGap)
}

function chooseBpmEstimate(gridBpm: number, onsetBpm: number): number {
  if (!gridBpm) {
    return onsetBpm || 0
  }
  if (!onsetBpm) {
    return gridBpm
  }
  const candidates = [onsetBpm, onsetBpm * 2, onsetBpm / 2]
  if (candidates.some((candidate) => Math.abs(candidate - gridBpm) <= 8)) {
    return gridBpm
  }
  return gridBpm
}

function calculateOnsetEnvelope(frames: EnergyFrame[]): number[] {
  const raw = frames.map((frame, index) => {
    const previous = frames[index - 1]?.energy ?? frame.energy
    return Math.max(frame.energy - previous, 0)
  })
  const average = mean(raw)
  const deviation = standardDeviation(raw, average) || 1
  return raw.map((value) => Math.max((value - average) / deviation, 0))
}

function sampleEnergyAt(
  frames: { timeMs: number; energy: number }[],
  timeMs: number,
  windowMs: number,
): number {
  let best = 0
  for (const frame of frames) {
    if (Math.abs(frame.timeMs - timeMs) <= windowMs) {
      best = Math.max(best, frame.energy)
    }
  }
  return best
}

function goertzelPower(
  samples: Float32Array,
  start: number,
  end: number,
  sampleRate: number,
  frequency: number,
): number {
  const size = Math.max(end - start, 1)
  const radians = (2 * Math.PI * frequency) / sampleRate
  const coefficient = 2 * Math.cos(radians)
  let s1 = 0
  let s2 = 0
  for (let index = start; index < end; index += 1) {
    const offset = index - start
    const window =
      size > 1
        ? 0.5 - 0.5 * Math.cos((2 * Math.PI * offset) / Math.max(size - 1, 1))
        : 1
    const sample = samples[index] * window + coefficient * s1 - s2
    s2 = s1
    s1 = sample
  }
  return s1 * s1 + s2 * s2 - coefficient * s1 * s2
}

function makeEmptyMelodyFeature(): MelodyFeature {
  return {
    frequencyHz: 0,
    lowEnergy: 0,
    midEnergy: 0,
    highEnergy: 0,
    clarity: 0,
  }
}

function normalizeOnsetStrengths<T extends { strength: number }>(
  onsets: T[],
): T[] {
  if (onsets.length === 0) {
    return []
  }
  const strengths = onsets.map((onset) => onset.strength)
  const min = Math.min(...strengths)
  const max = Math.max(...strengths)
  const range = Math.max(max - min, 0.0001)
  return onsets.map((onset) => ({
    ...onset,
    strength: (onset.strength - min) / range,
  }))
}

/**
 * 노트를 균등하게 분산해서 `keepRatio`만큼만 남긴다(Bresenham 방식) — 특정 구간만 갑자기
 * 비거나 몰리지 않고 전체적으로 고르게 줄어든다. 난이도 완화(노트 개수 줄이기)에 쓴다.
 */
export function filterByDensity<T>(items: T[], keepRatio: number): T[] {
  if (keepRatio >= 1) {
    return items
  }
  if (keepRatio <= 0) {
    return []
  }
  const result: T[] = []
  let accumulator = 0
  for (const item of items) {
    accumulator += keepRatio
    if (accumulator >= 1) {
      result.push(item)
      accumulator -= 1
    }
  }
  return result
}

function mean(values: number[]): number {
  if (values.length === 0) {
    return 0
  }
  return values.reduce((sum, value) => sum + value, 0) / values.length
}

function standardDeviation(values: number[], average: number): number {
  if (values.length === 0) {
    return 0
  }
  const variance =
    values.reduce((sum, value) => sum + (value - average) ** 2, 0) /
    values.length
  return Math.sqrt(variance)
}

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b)
  const middle = Math.floor(sorted.length / 2)
  if (sorted.length % 2 === 0) {
    return (sorted[middle - 1] + sorted[middle]) / 2
  }
  return sorted[middle]
}

function normalizePositive(
  value: number | undefined,
  fallback: number,
): number {
  if (!Number.isFinite(value) || Number(value) <= 0) {
    return fallback
  }
  return Number(value)
}
