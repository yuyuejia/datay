<template>
  <div class="login-form">
    <el-alert
      show-icon
      :closable="false"
      data-cy="loginError"
      type="error"
      v-if="authenticationError"
    ><strong>登录失败!</strong> 请检查您的登录信息, 并重试一次.</el-alert>
    <el-form @submit.prevent="doLogin()">
      <el-form-item label="账号">
        <el-input id="username" type="text" name="username" autofocus placeholder="您的账号" v-model="login" data-cy="username">
        </el-input>
      </el-form-item>
      <el-form-item label="密码">
        <el-input id="password" type="password" name="password" placeholder="您的密码" v-model="password" data-cy="password">
        </el-input>
      </el-form-item>
      <el-checkbox id="rememberMe" name="rememberMe" v-model="rememberMe">
        <span>自动登录</span>
      </el-checkbox>
      <div class="mt-3">
        <el-button data-cy="submit" native-type="submit" type="primary" class="w-100">登录</el-button>
      </div>
    </el-form>
    <div class="d-flex justify-content-between mt-3">
      <router-link :to="'/account/reset/request'" data-cy="forgetYourPasswordSelector">忘记密码?</router-link>
      <router-link :to="'/register'">注册新账号</router-link>
    </div>
  </div>
</template>

<script lang="ts" src="./login-form.component.ts"></script>
