<script setup lang="ts">
defineProps<{
  items: readonly string[]
  modelValue: string
  label: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <div class="segmented-tabs" :aria-label="label" role="tablist">
    <button
      v-for="item in items"
      :key="item"
      :aria-selected="modelValue === item"
      :class="{ 'segmented-tabs__button--active': modelValue === item }"
      role="tab"
      type="button"
      @click="emit('update:modelValue', item)"
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
