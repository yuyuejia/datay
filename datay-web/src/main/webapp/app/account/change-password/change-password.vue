<template>
  <div>
    <div class="row justify-content-center">
      <div class="col-md-8 toastify-container">
        <h2 v-if="username" id="password-title">
          <span
            >[<strong>{{ username }}</strong
            >] 的密码</span
          >
        </h2>

        <div class="alert alert-success" role="alert" v-if="success"><strong>密码修改成功!</strong></div>
        <div class="alert alert-danger" role="alert" v-if="error"><strong>发生错误!</strong> 密码无法被修改.</div>

        <div class="alert alert-danger" role="alert" v-if="doNotMatch">您输入的密码和确认密码不匹配!</div>

        <form name="form" id="password-form" @submit.prevent="changePassword()">
          <div class="form-group">
            <label class="form-control-label" for="currentPassword">当前密码</label>
            <input
              type="password"
              class="form-control"
              id="currentPassword"
              name="currentPassword"
              :class="{ valid: !v$.resetPassword.currentPassword.$invalid, invalid: v$.resetPassword.currentPassword.$invalid }"
              placeholder="您的当前密码"
              v-model="v$.resetPassword.currentPassword.$model"
              required
              data-cy="currentPassword"
            />
            <div v-if="v$.resetPassword.currentPassword.$anyDirty && v$.resetPassword.currentPassword.$invalid">
              <small class="form-text text-danger" v-if="!v$.resetPassword.currentPassword.required">您的密码是必填项.</small>
            </div>
          </div>
          <div class="form-group">
            <label class="form-control-label" for="newPassword">新密码</label>
            <input
              type="password"
              class="form-control"
              id="newPassword"
              name="newPassword"
              placeholder="您的新密码"
              :class="{ valid: !v$.resetPassword.newPassword.$invalid, invalid: v$.resetPassword.newPassword.$invalid }"
              v-model="v$.resetPassword.newPassword.$model"
              minlength="4"
              maxlength="50"
              required
              data-cy="newPassword"
            />
            <div v-if="v$.resetPassword.newPassword.$anyDirty && v$.resetPassword.newPassword.$invalid">
              <small class="form-text text-danger" v-if="!v$.resetPassword.newPassword.required">您的密码是必填项.</small>
              <small class="form-text text-danger" v-if="!v$.resetPassword.newPassword.minLength">您的密码长度至少要有4个字符</small>
              <small class="form-text text-danger" v-if="!v$.resetPassword.newPassword.maxLength">您的密码长度不能超过50个字符</small>
            </div>
          </div>
          <div class="form-group">
            <label class="form-control-label" for="confirmPassword">新密码确认</label>
            <input
              type="password"
              class="form-control"
              id="confirmPassword"
              name="confirmPassword"
              :class="{ valid: !v$.resetPassword.confirmPassword.$invalid, invalid: v$.resetPassword.confirmPassword.$invalid }"
              placeholder="确认您的新密码"
              v-model="v$.resetPassword.confirmPassword.$model"
              minlength="4"
              maxlength="50"
              required
              data-cy="confirmPassword"
            />
            <div v-if="v$.resetPassword.confirmPassword.$anyDirty && v$.resetPassword.confirmPassword.$invalid">
              <small class="form-text text-danger" v-if="!v$.resetPassword.confirmPassword.sameAsPassword"
                >您输入的密码和确认密码不匹配!</small
              >
            </div>
          </div>

          <button type="submit" :disabled="v$.resetPassword.$invalid" class="btn btn-primary" data-cy="submit">保存</button>
        </form>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./change-password.component.ts"></script>
