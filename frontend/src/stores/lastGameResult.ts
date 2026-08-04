import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { GameDetailId } from '../types/game-detail'
import type { GameResult, GameSessionMode } from '../types/gameplay'

export type LastGameOutcome = 'WIN' | 'LOSE' | 'DRAW' | 'COMPLETED' | 'UNKNOWN'

export interface LastGameResult extends GameResult {
  mode: GameSessionMode
  outcome: LastGameOutcome
}

/**
 * 방금 끝난 게임의 실제 결과를 결과 화면(`GameResultPage.vue`)으로 전달하는 저장소.
 *
 * `GamePlayPage.vue`가 게임이 끝나는 시점에 실제 점수/승패를 채워 넣고, 결과 화면은 이 값을
 * 최우선으로 쓴다 — 아직 실제 로직이 연결 안 된 게임(rhythm/draw/air)은 이 저장소에 값이 없으므로
 * 기존 mock 데이터로 자연스럽게 폴백된다.
 *
 * 페이지를 벗어나면(다시 플레이 등) 값을 지워서, 새로고침이나 직접 URL 진입 시 지난 판의 결과가
 * 엉뚱하게 남아있지 않도록 한다.
 */
export const useLastGameResultStore = defineStore('lastGameResult', () => {
  const current = ref<LastGameResult | null>(null)

  const isFor = computed(
    () =>
      (gameSlug: GameDetailId, mode: GameSessionMode): boolean =>
        current.value?.gameId === gameSlug && current.value?.mode === mode,
  )

  function set(result: LastGameResult): void {
    current.value = result
  }

  function clear(): void {
    current.value = null
  }

  return { current, isFor, set, clear }
})
