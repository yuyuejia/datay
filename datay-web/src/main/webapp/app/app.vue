<template>
  <div id="app" :class="{ 'app-embed': isEmbedded }">
    <!-- <ribbon></ribbon> -->
    <template v-if="!isLoginPage">
      <div v-if="!isEmbedded" ref="topbarRef" class="app-topbar">
        <div id="app-header">
          <jhi-navbar></jhi-navbar>
        </div>
        <tab-view v-if="authenticated"></tab-view>
      </div>
      <div class="container-fluid">
        <div class="card jh-card" :class="{ 'jh-card--no-padding': isDesignPage, 'jh-card--fill': isListPage }">
          <div v-if="noTenant" class="text-center py-5">
            <div style="font-size: 4rem; color: #ddd">🏢</div>
            <h3 class="mt-3">当前账号尚未加入任何租户</h3>
            <p class="text-muted">请联系管理员将您添加到租户后再使用系统功能。</p>
          </div>
          <router-view v-else v-slot="{ Component }">
            <!-- 顶层列表页（如系统管理）不缓存，返回时重新挂载并取数；其他页面继续使用 keep-alive。 -->
            <keep-alive :include="rootCachedViews" :max="20">
              <component v-if="!isTopLevelListPage" :is="Component" :key="rootKey" />
            </keep-alive>
            <component v-if="isTopLevelListPage" :is="Component" :key="route.fullPath" />
          </router-view>
        </div>
      </div>
    </template>
    <router-view v-else :key="route.fullPath"></router-view>
  </div>
</template>

<script lang="ts" src="./app.component.ts"></script>

<style scoped>
/* 顶栏（菜单 + 菜单页签）吸顶，页面内容在其下方滚动 */
.app-topbar {
  position: sticky;
  top: 0;
  z-index: 1030;
  background: #fff;
  box-shadow: 0 2px 6px rgb(15 29 69 / 6%);
}

/* 嵌入模式：没有顶栏，去掉内容区多余的顶部留白 */
#app.app-embed .jh-card {
  padding: 0;
}
</style>
