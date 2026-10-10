<template>
  <router-view v-slot="{ Component }">
    <keep-alive :include="cachedViews" :max="50">
      <component :is="Component" :key="innerKey" />
    </keep-alive>
  </router-view>
</template>

<script lang="ts" src="./entities.component.ts"></script>
