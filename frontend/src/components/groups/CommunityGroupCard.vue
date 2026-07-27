<script setup lang="ts">
import type { CommunityGroup } from '../../types/pages'

defineProps<{
  group: CommunityGroup
}>()

const emit = defineEmits<{
  enter: [group: CommunityGroup]
}>()
</script>

<template>
  <article class="community-group-card">
    <img :src="group.image" :alt="`${group.name} 대표 이미지`" />
    <div class="community-group-card__content">
      <span>{{ group.members }} / {{ group.capacity }}명 참여 중</span>
      <h2>{{ group.name }}</h2>
      <p>{{ group.description }}</p>
      <button
        :disabled="group.status === 'full'"
        type="button"
        @click="emit('enter', group)"
      >
        {{ group.status === 'open' ? '소모임 입장' : '정원 마감' }}
      </button>
    </div>
  </article>
</template>

<style scoped>
.community-group-card {
  display: grid;
  grid-template-columns: 128px 1fr;
  gap: 18px;
  align-items: center;
  padding: 16px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: #fff;
  box-shadow: var(--shadow-card);
}

.community-group-card > img {
  width: 128px;
  height: 112px;
  border-radius: 14px;
  object-fit: contain;
  background: var(--color-purple-soft);
}

.community-group-card__content > span {
  color: var(--color-accent-blue);
  font-size: 12px;
  font-weight: 800;
}

.community-group-card h2 {
  margin: 4px 0 6px;
  font-size: 19px;
}

.community-group-card p {
  margin: 0 0 13px;
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.5;
  word-break: keep-all;
}

.community-group-card button {
  padding: 8px 13px;
  border-radius: 9px;
  color: #fff;
  background: var(--color-accent-blue);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.community-group-card button:disabled {
  color: var(--color-muted);
  background: var(--color-surface-soft);
  cursor: not-allowed;
}

@media (max-width: 640px) {
  .community-group-card {
    grid-template-columns: 92px 1fr;
    gap: 13px;
  }

  .community-group-card > img {
    width: 92px;
    height: 92px;
  }
}
</style>
