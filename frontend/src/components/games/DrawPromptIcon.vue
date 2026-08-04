<script setup lang="ts">
import {
  IconApple,
  IconArmchair,
  IconBook,
  IconCar,
  IconClock,
  IconCloud,
  IconCrown,
  IconCup,
  IconEyeglass,
  IconFish,
  IconFlower,
  IconGuitarPick,
  IconHeart,
  IconHome,
  IconKey,
  IconMoon,
  IconMoodSmile,
  IconMountain,
  IconPlane,
  IconShip,
  IconStar,
  IconSun,
  IconTree,
  IconUmbrella,
} from '@tabler/icons-vue'
import { computed, type Component } from 'vue'

const props = withDefaults(
  defineProps<{
    prompt: string
    size?: 'large' | 'medium'
  }>(),
  { size: 'large' },
)

const promptIcons: Record<string, Component> = {
  달: IconMoon,
  산: IconMountain,
  하트: IconHeart,
  해: IconSun,
  책: IconBook,
  컵: IconCup,
  별: IconStar,
  구름: IconCloud,
  사과: IconApple,
  열쇠: IconKey,
  나무: IconTree,
  꽃: IconFlower,
  안경: IconEyeglass,
  우산: IconUmbrella,
  자동차: IconCar,
  얼굴: IconMoodSmile,
  배: IconShip,
  비행기: IconPlane,
  기타: IconGuitarPick,
  물고기: IconFish,
  의자: IconArmchair,
  집: IconHome,
  시계: IconClock,
  왕관: IconCrown,
}

const icon = computed(() => promptIcons[props.prompt] ?? IconStar)
</script>

<template>
  <span
    class="draw-prompt-icon"
    :class="`draw-prompt-icon--${size}`"
    aria-hidden="true"
  >
    <component :is="icon" :stroke-width="2.2" />
  </span>
</template>

<style scoped>
.draw-prompt-icon {
  display: inline-grid;
  width: 82px;
  height: 62px;
  place-items: center;
  color: var(--color-accent-blue);
}

.draw-prompt-icon :deep(svg) {
  width: 52px;
  height: 52px;
}

.draw-prompt-icon--medium {
  width: 64px;
  height: 48px;
}

.draw-prompt-icon--medium :deep(svg) {
  width: 36px;
  height: 36px;
}
</style>
