<template>
  <div>
    <div class="row justify-content-center">
      <div class="col-md-8">
        <h1>启用</h1>
        <div class="alert alert-success" v-if="success">
          <span><strong>您的账号已启用.</strong> 请 </span>
          <a class="alert-link" @click="showLogin()">登录</a>.
        </div>
        <div class="alert alert-danger" v-if="error"><strong>您的账号无法启用.</strong> 请重新注册.</div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./activate.component.ts"></script>
