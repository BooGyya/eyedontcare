<script setup lang="ts">
import { computed } from 'vue'
import type { CommunityGroup } from '../../types/community'

const props = defineProps<{
  group: CommunityGroup
  isGuest?: boolean
}>()

const emit = defineEmits<{
  join: [group: CommunityGroup]
  enter: [group: CommunityGroup]
}>()

const isFull = computed(() => props.group.members >= props.group.capacity)

const actionLabel = computed(() => {
  if (props.isGuest) return '로그인 후 이용'
  if (props.group.isJoined) return '입장하기'
  if (isFull.value) return '정원 마감'
  return props.group.visibility === 'private' ? '코드로 가입' : '가입하기'
})

function handleAction() {
  if (props.group.isJoined) {
    emit('enter', props.group)
    return
  }

  emit('join', props.group)
}
</script>

<template>
  <article
    class="community-group-card"
    :data-testid="`community-group-${group.id}`"
  >
    <div class="community-group-card__image">
      <img
        :src="group.image"
        :alt="`${group.name} 대표 이미지`"
        loading="lazy"
      />
      <span :class="`community-group-card__visibility--${group.visibility}`">
        {{ group.visibility === 'public' ? '공개' : '비공개' }}
      </span>
    </div>
    <div class="community-group-card__content">
      <div class="community-group-card__meta">
        <span>{{ group.members }} / {{ group.capacity }}명</span>
      </div>
      <h2>{{ group.name }}</h2>
      <p>{{ group.description }}</p>
      <footer>
        <span
          >리더 <b>{{ group.leader }}</b></span
        >
        <button
          :data-testid="`community-group-action-${group.id}`"
          :disabled="isFull && !group.isJoined"
          type="button"
          @click="handleAction"
        >
          {{ actionLabel }}
        </button>
      </footer>
    </div>
  </article>
</template>

<style scoped>
.community-group-card {
  display: grid;
  overflow: hidden;
  min-width: 0;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: #fff;
  box-shadow: var(--shadow-card);
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.community-group-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-float);
}
.community-group-card__image {
  position: relative;
  height: 132px;
  overflow: hidden;
  background: var(--color-purple-soft);
}
.community-group-card__image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.community-group-card__visibility--public,
.community-group-card__visibility--private {
  position: absolute;
  top: 14px;
  right: 14px;
  padding: 5px 9px;
  border-radius: var(--radius-button);
  font-size: 11px;
  font-weight: 800;
}
.community-group-card__visibility--public {
  color: #287c66;
  background: var(--color-mint-soft);
}
.community-group-card__visibility--private {
  color: #67509d;
  background: var(--color-purple-soft);
}
.community-group-card__content {
  display: grid;
  padding: 18px;
}
.community-group-card__meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: var(--color-accent-blue);
  font-size: 12px;
  font-weight: 800;
}
.community-group-card__meta span:last-child {
  color: var(--color-muted);
  font-weight: 700;
  white-space: nowrap;
}
.community-group-card h2 {
  margin: 7px 0 6px;
  overflow: hidden;
  font-size: 20px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.community-group-card p {
  min-height: 42px;
  margin: 0 0 17px;
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.6;
  word-break: keep-all;
}
.community-group-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--color-muted);
  font-size: 12px;
}
.community-group-card footer b {
  color: var(--color-ink);
}
.community-group-card button {
  padding: 9px 13px;
  border: 1px solid var(--color-accent-blue);
  border-radius: 10px;
  color: var(--color-accent-blue);
  background: #fff;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
  cursor: pointer;
}
.community-group-card button:hover:not(:disabled) {
  color: #fff;
  background: var(--color-accent-blue);
}
.community-group-card button:active:not(:disabled) {
  transform: translateY(1px);
}
.community-group-card button:focus-visible {
  outline: 3px solid rgba(79, 116, 219, 0.5);
  outline-offset: 2px;
}
.community-group-card button:disabled {
  border-color: var(--color-line);
  color: var(--color-muted);
  background: var(--color-surface-soft);
  cursor: not-allowed;
}

@media (max-width: 640px) {
  .community-group-card__image {
    height: 118px;
  }
}
</style>
