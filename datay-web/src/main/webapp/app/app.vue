<template>
  <div id="app">
    <!-- <ribbon></ribbon> -->
    <template v-if="!isLoginPage">
      <div id="app-header">
        <jhi-navbar></jhi-navbar>
      </div>
      <tab-view v-if="authenticated"></tab-view>
      <div class="container-fluid">
        <div
          class="card jh-card"
          :class="{ 'jh-card--no-padding': isDesignPage }"
        >
          <div v-if="noTenant" class="text-center py-5">
            <div style="font-size: 4rem; color: #ddd;">🏢</div>
            <h3 class="mt-3">当前账号尚未加入任何租户</h3>
            <p class="text-muted">
              请联系管理员将您添加到租户后再使用系统功能。
            </p>
          </div>
          <router-view v-else v-slot="{ Component }">
            <keep-alive :include="rootCachedViews" :max="20">
              <component :is="Component" :key="rootKey" />
            </keep-alive>
          </router-view>
        </div>
      </div>
    </template>
    <router-view v-else :key="route.fullPath"></router-view>
  </div>
</template>

<script lang="ts" src="./app.component.ts"></script>
