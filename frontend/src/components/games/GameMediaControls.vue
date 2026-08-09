<script setup lang="ts">
import { computed } from 'vue'
import {
  IconCamera,
  IconCameraOff,
  IconHeadphones,
  IconMicrophone,
  IconMicrophoneOff,
  IconMusic,
  IconMusicOff,
} from '@tabler/icons-vue'
import { useMediaSettingsStore } from '../../stores/mediaSettings'

// ⚠️ boolean prop은 안 넘기면 Vue가 undefined가 아닌 false로 캐스팅한다.
// hasBgm처럼 기본이 '켜짐'인 prop은 반드시 withDefaults로 기본값을 선언해야 한다.
const props = withDefaults(
  defineProps<{
    /** 상대 플레이어(사람)가 있는 대결인지 — 마이크·상대 음성 컨트롤은 이때만 보인다. */
    hasVoiceChat?: boolean
    /** BGM이 재생되는 화면인지(기본 true). 대기방처럼 BGM이 없는 화면에서는 끈다. */
    hasBgm?: boolean
  }>(),
  { hasVoiceChat: false, hasBgm: true },
)

const settings = useMediaSettingsStore()

// <input type="range">는 0~100 정수가 다루기 쉬우므로 스토어의 0~1 배율과 상호 변환한다.
const bgmPercent = computed({
  get: () => Math.round(settings.bgmVolume * 100),
  set: (value: number) => settings.setBgmVolume(value / 100),
})
const voicePercent = computed({
  get: () => Math.round(settings.voiceVolume * 100),
  set: (value: number) => settings.setVoiceVolume(value / 100),
})

const bgmLabel = computed(() =>
  settings.bgmMuted ? '배경음악 음소거 해제' : '배경음악 음소거',
)
const micLabel = computed(() =>
  settings.micEnabled ? '마이크 끄기' : '마이크 켜기',
)
const cameraLabel = computed(() =>
  settings.cameraEnabled ? '카메라 끄기' : '카메라 켜기',
)

function handleBgmInput(event: globalThis.Event) {
  bgmPercent.value = Number((event.target as globalThis.HTMLInputElement).value)
}

function handleVoiceInput(event: globalThis.Event) {
  voicePercent.value = Number(
    (event.target as globalThis.HTMLInputElement).value,
  )
}
</script>

<template>
  <div class="media-controls" role="group" aria-label="소리·카메라 설정">
    <div
      v-if="props.hasBgm"
      class="media-controls__slider"
      :class="{ muted: settings.bgmMuted }"
    >
      <button
        type="button"
        class="media-controls__toggle"
        :aria-label="bgmLabel"
        :aria-pressed="settings.bgmMuted"
        @click="settings.toggleBgmMuted()"
      >
        <IconMusicOff v-if="settings.bgmMuted" :size="18" />
        <IconMusic v-else :size="18" />
      </button>
      <input
        type="range"
        min="0"
        max="100"
        step="5"
        :value="bgmPercent"
        aria-label="배경음악 볼륨"
        @input="handleBgmInput"
      />
    </div>

    <template v-if="props.hasVoiceChat">
      <div class="media-controls__slider">
        <span class="media-controls__icon" aria-hidden="true">
          <IconHeadphones :size="18" />
        </span>
        <input
          type="range"
          min="0"
          max="100"
          step="5"
          :value="voicePercent"
          aria-label="상대 음성 볼륨"
          @input="handleVoiceInput"
        />
      </div>
      <button
        type="button"
        class="media-controls__toggle"
        :class="{ off: !settings.micEnabled }"
        :aria-label="micLabel"
        :aria-pressed="!settings.micEnabled"
        @click="settings.toggleMic()"
      >
        <IconMicrophone v-if="settings.micEnabled" :size="18" />
        <IconMicrophoneOff v-else :size="18" />
      </button>
    </template>

    <button
      type="button"
      class="media-controls__toggle"
      :class="{ off: !settings.cameraEnabled }"
      :aria-label="cameraLabel"
      :aria-pressed="!settings.cameraEnabled"
      @click="settings.toggleCamera()"
    >
      <IconCamera v-if="settings.cameraEnabled" :size="18" />
      <IconCameraOff v-else :size="18" />
    </button>
  </div>
</template>

<style scoped>
.media-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid #dfe3ee;
  border-radius: 999px;
  background: #fffef9;
  box-shadow: 0 7px 18px rgba(23, 36, 61, 0.08);
}
.media-controls__slider {
  display: flex;
  align-items: center;
  gap: 6px;
}
.media-controls__slider.muted input[type='range'] {
  opacity: 0.4;
}
.media-controls__icon {
  display: grid;
  place-items: center;
  color: var(--color-muted);
}
.media-controls__toggle {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 50%;
  color: var(--color-ink);
  background: transparent;
  cursor: pointer;
  transition:
    color var(--duration-fast) ease,
    background-color var(--duration-fast) ease,
    border-color var(--duration-fast) ease;
}
.media-controls__toggle:hover {
  border-color: #bfc7e9;
  color: var(--color-accent-blue);
  background: #f8f8ff;
}
.media-controls__toggle.off {
  color: #fff;
  background: var(--color-danger, #c2455a);
}
.media-controls__toggle.off:hover {
  border-color: transparent;
  color: #fff;
  background: color-mix(in srgb, var(--color-danger, #c2455a) 82%, black);
}
.media-controls input[type='range'] {
  width: 88px;
  accent-color: var(--color-accent-blue, #4f40ea);
}
@media (max-width: 720px) {
  .media-controls {
    gap: 6px;
    padding: 6px 10px;
  }
  .media-controls input[type='range'] {
    width: 64px;
  }
}
</style>
