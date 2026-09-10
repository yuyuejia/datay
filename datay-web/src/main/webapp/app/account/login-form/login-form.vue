<template>
  <div class="login-form">
    <b-alert show data-cy="loginError" variant="danger" v-if="authenticationError"
      ><strong>登录失败!</strong> 请检查您的登录信息, 并重试一次.</b-alert
    >
    <b-form @submit.prevent="doLogin()">
      <b-form-group label="账号" label-for="username">
        <b-form-input id="username" type="text" name="username" autofocus placeholder="您的账号" v-model="login" data-cy="username">
        </b-form-input>
      </b-form-group>
      <b-form-group label="密码" label-for="password">
        <b-form-input id="password" type="password" name="password" placeholder="您的密码" v-model="password" data-cy="password">
        </b-form-input>
      </b-form-group>
      <b-form-checkbox id="rememberMe" name="rememberMe" v-model="rememberMe" checked>
        <span>自动登录</span>
      </b-form-checkbox>
      <div class="mt-3">
        <b-button data-cy="submit" type="submit" variant="primary" block>登录</b-button>
      </div>
    </b-form>
    <div class="d-flex justify-content-between mt-3">
      <b-link :to="'/account/reset/request'" data-cy="forgetYourPasswordSelector">忘记密码?</b-link>
      <b-link :to="'/register'">注册新账号</b-link>
    </div>
  </div>
</template>

<script lang="ts" src="./login-form.component.ts"></script>
