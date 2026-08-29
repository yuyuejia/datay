<template>
  <div id="app">
    <!-- <ribbon></ribbon> -->
    <div id="app-header">
      <jhi-navbar></jhi-navbar>
    </div>
    <div class="container-fluid">
      <div class="card jh-card" :class="{ 'jh-card--no-padding': isDesignPage }">
        <router-view></router-view>
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