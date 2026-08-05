<script setup lang="ts">
import { ref } from 'vue'
import footerLogoImage from '../../assets/images/brand/footer-logo.png'
import PolicyDialog from '../common/PolicyDialog.vue'
import { policyDocuments } from '../../mocks/footer'
import type { PolicyDocument } from '../../types/footer'

const activeDocument = ref<PolicyDocument | null>(null)
</script>

<template>
  <footer class="app-footer">
    <RouterLink class="app-footer__brand" to="/" aria-label="eye dont care 홈">
      <img :src="footerLogoImage" alt="eye dont care" />
    </RouterLink>
    <div class="app-footer__meta">
      <small>© 2026 eye dont care. All rights reserved.</small>
      <p class="app-footer__support">
        문의 support@eyedontcare.app · 평일 10:00~18:00
      </p>
    </div>
    <div class="app-footer__links">
      <button
        v-for="doc in policyDocuments"
        :key="doc.id"
        type="button"
        @click="activeDocument = doc"
      >
        {{ doc.label }}
      </button>
    </div>

    <PolicyDialog :document="activeDocument" @close="activeDocument = null" />
  </footer>
</template>

<style scoped>
.app-footer {
  display: flex;
  align-items: center;
  gap: 28px;
  width: var(--layout-inline);
  min-height: 100px;
  margin: auto auto 0;
  padding: 16px 0 12px;
  border-top: 1px solid var(--color-line);
  color: var(--color-muted);
  font-size: 12px;
}

.app-footer__brand img {
  width: 72px;
  height: 72px;
  object-fit: contain;
}

.app-footer__meta {
  display: grid;
  gap: 4px;
  margin-right: 52px;
}

.app-footer__support {
  margin: 0;
  color: var(--color-muted);
  font-size: 11px;
}

.app-footer__links {
  display: flex;
  gap: 20px;
  margin-left: auto;
}

.app-footer__links button {
  padding: 0;
  border-bottom: 1px solid transparent;
  color: inherit;
  background: transparent;
  cursor: pointer;
  transition:
    color var(--duration-fast) ease,
    border-color var(--duration-fast) ease;
}

.app-footer__links button:hover,
.app-footer__links button:focus-visible {
  border-color: var(--color-ink);
  color: var(--color-ink);
}

@media (max-width: 1100px) {
  .app-footer {
    width: min(900px, calc(100% - 52px));
  }

  .app-footer__meta {
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
    width: 58px;
    height: 58px;
  }

  .app-footer__meta {
    order: 3;
    width: 100%;
  }

  .app-footer__links {
    flex-wrap: wrap;
    gap: 10px 14px;
    margin-left: 0;
    order: 2;
  }
}
</style>
