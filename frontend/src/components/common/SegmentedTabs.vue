<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  items: readonly string[]
  modelValue: string
  label: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const tabButtons = ref<Array<{ focus: () => void }>>([])

function handleKeydown(
  event: {
    key: string
    currentTarget: unknown
    preventDefault: () => void
  },
  items: readonly string[],
) {
  const target = event.currentTarget as { value?: string } | null
  const currentIndex = items.indexOf(target?.value ?? '')
  const nextIndexByKey: Record<string, number> = {
    ArrowRight: (currentIndex + 1) % items.length,
    ArrowLeft: (currentIndex - 1 + items.length) % items.length,
    Home: 0,
    End: items.length - 1,
  }
  const nextIndex = nextIndexByKey[event.key]

  if (nextIndex === undefined) {
    return
  }

  event.preventDefault()
  emit('update:modelValue', items[nextIndex])
  tabButtons.value[nextIndex]?.focus()
}
</script>

<template>
  <div class="segmented-tabs" :aria-label="label" role="tablist">
    <button
      v-for="item in items"
      :key="item"
      ref="tabButtons"
      :aria-selected="modelValue === item"
      :class="{ 'segmented-tabs__button--active': modelValue === item }"
      :tabindex="modelValue === item ? 0 : -1"
      :value="item"
      role="tab"
      type="button"
      @click="emit('update:modelValue', item)"
      @keydown="handleKeydown($event, items)"
    >
      {{ item }}
    </button>
  </div>
</template>

<style scoped>
.segmented-tabs {
  display: flex;
  width: fit-content;
  max-width: 100%;
  gap: 5px;
  overflow-x: auto;
  padding: 5px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-button);
  background: #fff;
}

.segmented-tabs button {
  flex: 0 0 auto;
  padding: 9px 17px;
  border-radius: var(--radius-button);
  color: var(--color-muted);
  background: transparent;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.segmented-tabs .segmented-tabs__button--active {
  color: #fff;
  background: var(--color-accent-blue);
  box-shadow: 0 4px 10px rgba(79, 116, 219, 0.2);
}
</style>
