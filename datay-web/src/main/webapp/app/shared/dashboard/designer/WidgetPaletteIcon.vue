<template>
  <svg
    class="widget-palette-icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <g v-if="name === 'bar'">
      <path d="M4 20h16" />
      <rect x="5" y="11" width="3" height="7" rx="0.8" fill="currentColor" stroke="none" />
      <rect x="10.5" y="6" width="3" height="12" rx="0.8" fill="currentColor" stroke="none" />
      <rect x="16" y="9" width="3" height="9" rx="0.8" fill="currentColor" stroke="none" />
    </g>
    <g v-else-if="name === 'line'">
      <path d="M4 20h16" />
      <polyline points="5 16 9 11 13 14 19 6" />
      <circle cx="5" cy="16" r="1.2" fill="currentColor" stroke="none" />
      <circle cx="9" cy="11" r="1.2" fill="currentColor" stroke="none" />
      <circle cx="13" cy="14" r="1.2" fill="currentColor" stroke="none" />
      <circle cx="19" cy="6" r="1.2" fill="currentColor" stroke="none" />
    </g>
    <g v-else-if="name === 'pie'">
      <circle cx="12" cy="12" r="8" />
      <path d="M12 12V4a8 8 0 0 1 8 8z" fill="currentColor" fill-opacity="0.25" />
    </g>
    <g v-else-if="name === 'area'">
      <path d="M4 20h16" />
      <path d="M5 16 9 11l4 3 6-8v10z" fill="currentColor" fill-opacity="0.2" stroke="none" />
      <polyline points="5 16 9 11 13 14 19 6" />
    </g>
    <g v-else-if="name === 'kpi'">
      <path d="M4 17a8 8 0 0 1 16 0" />
      <path d="M12 17l4.5-4.5" />
      <circle cx="12" cy="17" r="1.4" fill="currentColor" stroke="none" />
    </g>
    <g v-else-if="name === 'table'">
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <path d="M3 9h18M3 14h18M9 4v16" />
    </g>
    <g v-else>
      <polyline points="8 6 4 12 8 18" />
      <polyline points="16 6 20 12 16 18" />
      <path d="M13.5 5 10.5 19" />
    </g>
  </svg>
</template>

<script setup lang="ts">
defineProps<{
  name: string;
}>();
</script>

<style scoped>
.widget-palette-icon {
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
}
</style>
