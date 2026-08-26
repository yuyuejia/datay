<template>
  <div>
    <div class="row justify-content-center">
      <div class="col-md-8 toastify-container">
        <h1 id="register-title" data-cy="registerTitle">注册</h1>

        <div class="alert alert-success" role="alert" v-if="success"><strong>注册成功!</strong> 请检查您的邮箱.</div>

        <div class="alert alert-danger" role="alert" v-if="error"><strong>注册失败!</strong> 请稍后再试.</div>

        <div class="alert alert-danger" role="alert" v-if="errorUserExists"><strong>账号已被注册!</strong> 请选择其它账号.</div>

        <div class="alert alert-danger" role="alert" v-if="errorEmailExists"><strong>邮件已经被注册!</strong> 请选择其它邮件.</div>
      </div>
    </div>
    <div class="row justify-content-center">
      <div class="col-md-8">
        <form id="register-form" name="registerForm" @submit.prevent="register()" v-if="!success" no-validate>
          <div class="form-group">
            <label class="form-control-label" for="username">账号</label>
            <input
              type="text"
              class="form-control"
              v-model="v$.registerAccount.login.$model"
              id="username"
              name="login"
              :class="{ valid: !v$.registerAccount.login.$invalid, invalid: v$.registerAccount.login.$invalid }"
              required
              minlength="1"
              maxlength="50"
              pattern="^[a-zA-Z0-9!#$&'*+=?^_`{|}~.-]+@?[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$"
              placeholder="您的账号"
              data-cy="username"
            />
            <div v-if="v$.registerAccount.login.$anyDirty && v$.registerAccount.login.$invalid">
              <small class="form-text text-danger" v-if="!v$.registerAccount.login.required">您的账号是必填项.</small>
              <small class="form-text text-danger" v-if="!v$.registerAccount.login.minLength">您的账号长度至少要有1个字符</small>
              <small class="form-text text-danger" v-if="!v$.registerAccount.login.maxLength">您的账号长度不能超过50个字符</small>
              <small class="form-text text-danger" v-if="!v$.registerAccount.login.pattern">Your username is invalid.</small>
            </div>
          </div>
          <div class="form-group">
            <label class="form-control-label" for="email">电子邮件</label>
            <input
              type="email"
              class="form-control"
              id="email"
              name="email"
              :class="{ valid: !v$.registerAccount.email.$invalid, invalid: v$.registerAccount.email.$invalid }"
              v-model="v$.registerAccount.email.$model"
              minlength="5"
              maxlength="254"
              email
              required
              placeholder="您的电子邮件"
              data-cy="email"
            />
            <div v-if="v$.registerAccount.email.$anyDirty && v$.registerAccount.email.$invalid">
              <small class="form-text text-danger" v-if="!v$.registerAccount.email.required">您的电子邮件是必填项.</small>
              <small class="form-text text-danger" v-if="!v$.registerAccount.email.email">您的电子邮件格式格式不正确.</small>
              <small class="form-text text-danger" v-if="!v$.registerAccount.email.minLength">您的电子邮件长度至少要有5个字符</small>
              <small class="form-text text-danger" v-if="!v$.registerAccount.email.maxLength">您的电子邮件长度不能超过50个字符</small>
            </div>
          </div>
          <div class="form-group">
            <label class="form-control-label" for="firstPassword">新密码</label>
            <input
              type="password"
              class="form-control"
              id="firstPassword"
              name="password"
              :class="{ valid: !v$.registerAccount.password.$invalid, invalid: v$.registerAccount.password.$invalid }"
              v-model="v$.registerAccount.password.$model"
              minlength="4"
              maxlength="50"
              required
              placeholder="您的新密码"
              data-cy="firstPassword"
            />
            <div v-if="v$.registerAccount.password.$anyDirty && v$.registerAccount.password.$invalid">
              <small class="form-text text-danger" v-if="!v$.registerAccount.password.required">您的密码是必填项.</small>
              <small class="form-text text-danger" v-if="!v$.registerAccount.password.minLength">您的密码长度至少要有4个字符</small>
              <small class="form-text text-danger" v-if="!v$.registerAccount.password.maxLength">您的密码长度不能超过50个字符</small>
            </div>
          </div>
          <div class="form-group">
            <label class="form-control-label" for="secondPassword">新密码确认</label>
            <input
              type="password"
              class="form-control"
              id="secondPassword"
              name="confirmPasswordInput"
              :class="{ valid: !v$.confirmPassword.$invalid, invalid: v$.confirmPassword.$invalid }"
              v-model="v$.confirmPassword.$model"
              minlength="4"
              maxlength="50"
              required
              placeholder="确认您的新密码"
              data-cy="secondPassword"
            />
            <div v-if="v$.confirmPassword.$dirty && v$.confirmPassword.$invalid">
              <small class="form-text text-danger" v-if="!v$.confirmPassword.required">您的确认密码是必填项.</small>
              <small class="form-text text-danger" v-if="!v$.confirmPassword.minLength">您的确认密码长度至少要有4个字符</small>
              <small class="form-text text-danger" v-if="!v$.confirmPassword.maxLength">您的确认密码长度不能超过50个字符</small>
              <small class="form-text text-danger" v-if="!v$.confirmPassword.sameAsPassword">您输入的密码和确认密码不匹配!</small>
            </div>
          </div>

          <button type="submit" :disabled="v$.$invalid" class="btn btn-primary" data-cy="submit">注册</button>
        </form>
        <p></p>
        <div class="alert alert-warning">
          <span>如果您要</span>
          <a class="alert-link" @click="showLogin()">登录</a
          ><span>, 您可以使用默认账号:<br />- 管理员 (账号="admin"和密码="admin") <br />- 普通用户 (账号="user"和密码="user").</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./register.component.ts"></script>
