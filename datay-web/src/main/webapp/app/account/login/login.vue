<template>
  <div class="login-page d-flex align-items-center justify-content-center">
    <div class="login-card card">
      <div class="card-body p-4">
        <h2 class="text-center mb-4" data-cy="loginTitle">登录</h2>
        <login-form></login-form>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./login.component.ts"></script>

<style scoped>
.login-page {
  min-height: 100vh;
  padding: 1rem;
  background: #f5f7fa;
}

.login-card {
  width: 100%;
  max-width: 420px;
}
</style>
