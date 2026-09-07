<template>
  <div id="app">
    <!-- <ribbon></ribbon> -->
    <div id="app-header">
      <jhi-navbar></jhi-navbar>
    </div>
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
        <router-view v-else :key="route.fullPath + '-' + tenantVersion"></router-view>
      </div>
      <b-modal id="login-page" v-model="loginModalOpen" hide-footer lazy>
        <template #modal-title>
          <span data-cy="loginTitle" id="login-title">登录</span>
        </template>
        <login-form></login-form>
      </b-modal>
    </div>
  </div>
</template>

<script lang="ts" src="./app.component.ts"></script>