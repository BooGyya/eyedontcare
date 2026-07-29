<script setup lang="ts">
import { ref } from 'vue'
import logoImage from '../../assets/images/brand/logo.png'
import { useToast } from '../../composables/useToast'
import PolicyDialog from '../common/PolicyDialog.vue'
import FeedbackDialog from '../common/FeedbackDialog.vue'
import { policyDocuments } from '../../mocks/footer'
import type { PolicyDocument } from '../../types/footer'

const { showToast } = useToast()

const activeDocument = ref<PolicyDocument | null>(null)
const isFeedbackOpen = ref(false)

function openFeedbackFromPolicy() {
  activeDocument.value = null
  isFeedbackOpen.value = true
}

function handleFeedbackSubmit() {
  isFeedbackOpen.value = false
  showToast('소중한 피드백이 접수됐어요. 감사합니다!')
}
</script>

<template>
  <footer class="app-footer">
    <RouterLink class="app-footer__brand" to="/" aria-label="eye dont care 홈">
      <img :src="logoImage" alt="eye dont care" />
    </RouterLink>
    <small>© 2026 eye dont care. All rights reserved.</small>
    <div class="app-footer__links">
      <button
        v-for="doc in policyDocuments"
        :key="doc.id"
        type="button"
        @click="activeDocument = doc"
      >
        {{ doc.label }}
      </button>
      <button type="button" @click="isFeedbackOpen = true">
        피드백 보내기
      </button>
    </div>

    <PolicyDialog
      :document="activeDocument"
      @close="activeDocument = null"
      @open-feedback="openFeedbackFromPolicy"
    />
    <FeedbackDialog
      :open="isFeedbackOpen"
      @close="isFeedbackOpen = false"
      @submit="handleFeedbackSubmit"
    />
  </footer>
</template>

<style scoped>
.app-footer {
  display: flex;
  align-items: center;
  gap: 28px;
  width: min(var(--content-width), calc(100% - 120px));
  min-height: 100px;
  margin: auto auto 0;
  padding: 16px 0 12px;
  border-top: 1px solid var(--color-line);
  color: #17345e;
  font-size: 12px;
}

.app-footer__brand img {
  width: 105px;
  height: 72px;
  object-fit: contain;
}

.app-footer small {
  margin-right: 52px;
}

.app-footer__links {
  display: flex;
  gap: 20px;
  margin-left: auto;
}

.app-footer__links button {
  padding: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
}

@media (max-width: 1100px) {
  .app-footer {
    width: min(900px, calc(100% - 52px));
  }

  .app-footer small {
    margin-right: 0;
  }

  .app-footer__links {
    gap: 16px;
  }
}

@media (max-width: 640px) {
  .app-footer {
    flex-wrap: wrap;
    gap: 7px 14px;
    width: calc(100% - 32px);
    min-height: 80px;
    font-size: 10px;
  }

  .app-footer__brand img {
    width: 87px;
    height: 58px;
  }

  .app-footer small {
    width: 100%;
    order: 3;
  }

  .app-footer__links {
    flex-wrap: wrap;
    gap: 10px 14px;
    margin-left: 0;
    order: 2;
  }
}
</style>
