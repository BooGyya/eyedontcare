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

  return {
    message: readonly(message),
    isVisible: readonly(isVisible),
    showToast,
  }
}
