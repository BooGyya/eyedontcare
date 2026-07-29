<script setup lang="ts">
withDefaults(
  defineProps<{
    prompt: string
    size?: 'large' | 'medium'
  }>(),
  { size: 'large' },
)
</script>

<template>
  <span
    class="draw-prompt-icon"
    :class="`draw-prompt-icon--${size}`"
    aria-hidden="true"
  >
    <span v-if="prompt === '안경'" class="draw-prompt-icon__glasses">
      <i /><i /><b />
    </span>
    <span v-else-if="prompt === '우산'" class="draw-prompt-icon__umbrella">
      <i /><b />
    </span>
    <span v-else-if="prompt === '고양이'" class="draw-prompt-icon__cat">
      <i /><i /><b /><em />
    </span>
    <span v-else>✦</span>
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
.draw-prompt-icon--medium {
  width: 64px;
  height: 48px;
  transform: scale(0.78);
  transform-origin: center;
}
.draw-prompt-icon__glasses {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
}
.draw-prompt-icon__glasses i {
  display: block;
  width: 27px;
  height: 27px;
  border: 4px solid currentColor;
  border-radius: 50%;
}
.draw-prompt-icon__glasses b {
  position: absolute;
  top: 50%;
  left: 31px;
  width: 17px;
  border-top: 4px solid currentColor;
  transform: translateY(-50%);
}
.draw-prompt-icon__umbrella {
  position: relative;
  display: grid;
  width: 64px;
  height: 58px;
  justify-items: center;
}
.draw-prompt-icon__umbrella i {
  width: 62px;
  height: 31px;
  border: 4px solid currentColor;
  border-bottom: 0;
  border-radius: 62px 62px 0 0;
  background: #f3f2ff;
}
.draw-prompt-icon__umbrella b {
  position: absolute;
  top: 31px;
  width: 4px;
  height: 23px;
  border-radius: 99px;
  background: currentColor;
}
.draw-prompt-icon__umbrella b::after {
  position: absolute;
  right: -8px;
  bottom: -1px;
  width: 10px;
  height: 10px;
  border: 4px solid currentColor;
  border-top: 0;
  border-left: 0;
  border-radius: 0 0 10px 0;
  content: '';
}
.draw-prompt-icon__cat {
  position: relative;
  display: grid;
  width: 48px;
  height: 40px;
  place-items: center;
  border: 4px solid currentColor;
  border-radius: 48% 48% 43% 43%;
  background: #f3f2ff;
}
.draw-prompt-icon__cat::before,
.draw-prompt-icon__cat::after {
  position: absolute;
  top: -12px;
  width: 17px;
  height: 17px;
  border: 4px solid currentColor;
  border-bottom: 0;
  background: #f3f2ff;
  content: '';
}
.draw-prompt-icon__cat::before {
  left: -3px;
  transform: rotate(-38deg);
}
.draw-prompt-icon__cat::after {
  right: -3px;
  transform: rotate(38deg);
}
.draw-prompt-icon__cat i,
.draw-prompt-icon__cat i::before {
  position: absolute;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  content: '';
}
.draw-prompt-icon__cat i:first-child {
  top: 14px;
  left: 12px;
}
.draw-prompt-icon__cat i:nth-child(2) {
  top: 14px;
  right: 12px;
}
.draw-prompt-icon__cat b {
  position: absolute;
  bottom: 10px;
  width: 9px;
  height: 5px;
  border-bottom: 3px solid currentColor;
  border-radius: 0 0 9px 9px;
}
.draw-prompt-icon__cat em,
.draw-prompt-icon__cat em::before {
  position: absolute;
  bottom: 11px;
  width: 15px;
  border-top: 2px solid currentColor;
  content: '';
}
.draw-prompt-icon__cat em {
  left: -10px;
}
.draw-prompt-icon__cat em::before {
  top: 5px;
  left: 0;
}
</style>
