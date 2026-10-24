<template>
  <router-view v-slot="{ Component }">
    <!-- 列表页每次进入都重新挂载，保证从编辑/设计页返回时重新取数；其他页面继续使用 keep-alive 缓存。 -->
    <keep-alive :include="cachedViews" :max="50">
      <component v-if="!isListPage" :is="Component" :key="innerKey" />
    </keep-alive>
    <component v-if="isListPage" :is="Component" :key="innerKey" />
  </router-view>
</template>

<script lang="ts" src="./entities.component.ts"></script>
