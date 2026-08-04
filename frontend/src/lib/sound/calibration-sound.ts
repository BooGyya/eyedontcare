/**
 * 캘리브레이션 단계 피드백용 효과음. 별도 오디오 에셋 없이 Web Audio로 짧은 톤을 합성한다.
 *
 * - `step`    : 한 단계(눈 뜨기/감기/시선 지점) 기록 성공
 * - `complete`: 캘리브레이션 전체 완료(상승 3음)
 * - `reject`  : 기준치 미달로 다음 단계로 넘어가지 못함
 *
 * 오디오 정책상 AudioContext는 사용자 제스처(버튼 클릭) 시점에만 만들어지고, 실패해도
 * 캘리브레이션 흐름에는 영향을 주지 않는다(전부 try/catch로 무해화). 테스트(jsdom)처럼
 * AudioContext가 없는 환경에서는 아무 것도 하지 않는다.
 */

type CalibrationSoundKind = 'step' | 'complete' | 'reject'

const TONE_HZ: Record<CalibrationSoundKind, number[]> = {
  step: [660],
  complete: [523.25, 659.25, 783.99], // C5 - E5 - G5 상승
  reject: [196], // 낮은 톤
}

let audioContext: AudioContext | null = null

function resolveAudioContext(): AudioContext | null {
  if (typeof window === 'undefined') return null
  const Ctor =
    window.AudioContext ??
    (window as unknown as { webkitAudioContext?: typeof AudioContext })
      .webkitAudioContext
  if (!Ctor) return null
  if (!audioContext) audioContext = new Ctor()
  return audioContext
}

export function playCalibrationSound(kind: CalibrationSoundKind): void {
  try {
    const audio = resolveAudioContext()
    if (!audio) return
    // 사용자 제스처 이후에도 suspended일 수 있어 재개를 시도한다.
    if (audio.state === 'suspended') void audio.resume()

    const noteDuration = kind === 'complete' ? 0.13 : 0.11
    const now = audio.currentTime

    TONE_HZ[kind].forEach((frequency, index) => {
      const oscillator = audio.createOscillator()
      const gain = audio.createGain()
      oscillator.type = kind === 'reject' ? 'sawtooth' : 'sine'
      oscillator.frequency.value = frequency

      const startAt = now + index * noteDuration
      const endAt = startAt + noteDuration
      // 짧은 attack/decay 엔벨로프로 클릭음(파형 튐)을 줄인다.
      gain.gain.setValueAtTime(0.0001, startAt)
      gain.gain.exponentialRampToValueAtTime(0.15, startAt + 0.012)
      gain.gain.exponentialRampToValueAtTime(0.0001, endAt)

      oscillator.connect(gain).connect(audio.destination)
      oscillator.start(startAt)
      oscillator.stop(endAt + 0.02)
    })
  } catch {
    // 효과음 실패는 캘리브레이션 진행에 영향을 주지 않는다.
  }
}
