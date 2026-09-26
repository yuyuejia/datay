<template>
  <div class="html-widget" v-html="safeHtml"></div>
</template>

<script setup lang="ts">
import DOMPurify from "dompurify";
import { computed } from "vue";

import type { DashboardWidget } from "./types";

const props = defineProps<{ widget: DashboardWidget }>();

const safeHtml = computed(() =>
  DOMPurify.sanitize(props.widget.html || "", {
    USE_PROFILES: { html: true },
  }),
);
</script>

<style scoped>
.html-widget {
  overflow: auto;
  height: 100%;
  color: #303133;
  font-size: 14px;
  line-height: 1.6;
}
</style>
