import { readonly, ref } from 'vue'

const message = ref('')
const isVisible = ref(false)
let closeTimer: ReturnType<typeof setTimeout> | undefined

export function useToast() {
  function showToast(nextMessage: string) {
    message.value = nextMessage
    isVisible.value = true

    if (closeTimer) {
      clearTimeout(closeTimer)
    }

    closeTimer = setTimeout(() => {
      isVisible.value = false
    }, 2600)
  }

  /** 사용자가 직접 토스트를 닫는다(탭하여 닫기). auto-hide를 기다리지 않아도 되게 한다. */
  function hideToast() {
    if (closeTimer) {
      clearTimeout(closeTimer)
      closeTimer = undefined
    }
    isVisible.value = false
  }

  return {
    message: readonly(message),
    isVisible: readonly(isVisible),
    showToast,
    hideToast,
  }
}
