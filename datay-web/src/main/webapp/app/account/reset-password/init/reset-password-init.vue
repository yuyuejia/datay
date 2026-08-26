<template>
  <div>
    <div class="row justify-content-center">
      <div class="col-md-8">
        <h1>重置您的密码</h1>

        <div class="alert alert-warning" v-if="!success">
          <p>请输入您注册时使用的邮件地址</p>
        </div>

        <div class="alert alert-success" v-if="success">
          <p>已将重置密码的操作说明发送到您的邮箱，请检查邮件.</p>
        </div>

        <form v-if="!success" name="form" @submit.prevent="requestReset()">
          <div class="form-group">
            <label class="form-control-label" for="email">电子邮件</label>
            <input
              type="email"
              class="form-control"
              id="email"
              name="email"
              placeholder="您的电子邮件"
              :class="{ valid: !v$.resetAccount.email.$invalid, invalid: v$.resetAccount.email.$invalid }"
              v-model="v$.resetAccount.email.$model"
              minlength="5"
              maxlength="254"
              email
              required
              data-cy="emailResetPassword"
            />
            <div v-if="v$.resetAccount.email.$anyDirty && v$.resetAccount.email.$invalid">
              <small class="form-text text-danger" v-if="!v$.resetAccount.email.required">您的电子邮件是必填项.</small>
              <small class="form-text text-danger" v-if="!v$.resetAccount.email.email">您的电子邮件格式格式不正确.</small>
              <small class="form-text text-danger" v-if="!v$.resetAccount.email.minLength">您的电子邮件长度至少要有5个字符</small>
              <small class="form-text text-danger" v-if="!v$.resetAccount.email.maxLength">您的电子邮件长度不能超过50个字符</small>
            </div>
          </div>
          <button type="submit" :disabled="v$.resetAccount.$invalid" class="btn btn-primary" data-cy="submit">重置密码</button>
        </form>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./reset-password-init.component.ts"></script>
