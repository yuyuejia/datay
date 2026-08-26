<template>
  <div>
    <div class="row justify-content-center">
      <div class="col-md-8">
        <h1>重置您的密码</h1>

        <div class="alert alert-danger" v-if="keyMissing">无效的重置密码请求.</div>

        <div class="alert alert-danger" v-if="error">
          <p>无法重置密码. 您必须在请求重置密码后24小时内完成重置.</p>
        </div>

        <div class="alert alert-success" v-if="success">
          <span><strong>您的密码已被重置.</strong> 请 </span>
          <a class="alert-link" @click="showLogin()">登录</a>
        </div>
        <div class="alert alert-danger" v-if="doNotMatch">
          <p>您输入的密码和确认密码不匹配!</p>
        </div>

        <div class="alert alert-warning" v-if="!success && !keyMissing">
          <p>请设置新密码</p>
        </div>

        <div v-if="!keyMissing">
          <form v-if="!success" name="form" @submit.prevent="finishReset()">
            <div class="form-group">
              <label class="form-control-label" for="newPassword">新密码</label>
              <input
                type="password"
                class="form-control"
                id="newPassword"
                name="newPassword"
                placeholder="您的新密码"
                :class="{ valid: !v$.resetAccount.newPassword.$invalid, invalid: v$.resetAccount.newPassword.$invalid }"
                v-model="v$.resetAccount.newPassword.$model"
                minlength="4"
                maxlength="50"
                required
                data-cy="resetPassword"
              />
              <div v-if="v$.resetAccount.newPassword.$anyDirty && v$.resetAccount.newPassword.$invalid">
                <small class="form-text text-danger" v-if="!v$.resetAccount.newPassword.required">您的密码是必填项.</small>
                <small class="form-text text-danger" v-if="!v$.resetAccount.newPassword.minLength">您的密码长度至少要有4个字符</small>
                <small class="form-text text-danger" v-if="!v$.resetAccount.newPassword.maxLength">您的密码长度不能超过50个字符</small>
              </div>
            </div>
            <div class="form-group">
              <label class="form-control-label" for="confirmPassword">新密码确认</label>
              <input
                type="password"
                class="form-control"
                id="confirmPassword"
                name="confirmPassword"
                :class="{ valid: !v$.resetAccount.confirmPassword.$invalid, invalid: v$.resetAccount.confirmPassword.$invalid }"
                placeholder="确认您的新密码"
                v-model="v$.resetAccount.confirmPassword.$model"
                minlength="4"
                maxlength="50"
                required
                data-cy="confirmResetPassword"
              />
              <div v-if="v$.resetAccount.confirmPassword.$anyDirty && v$.resetAccount.confirmPassword.$invalid">
                <small class="form-text text-danger" v-if="!v$.resetAccount.confirmPassword.sameAsPassword"
                  >您输入的密码和确认密码不匹配!</small
                >
              </div>
            </div>
            <button type="submit" :disabled="v$.resetAccount.$invalid" class="btn btn-primary" data-cy="submit">保存</button>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./reset-password-finish.component.ts"></script>
