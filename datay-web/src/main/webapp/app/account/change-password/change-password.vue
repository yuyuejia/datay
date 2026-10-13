<template>
  <div>
    <div class="alert alert-success" role="alert" v-if="success"><strong>密码修改成功!</strong></div>
    <div class="alert alert-danger" role="alert" v-if="error"><strong>发生错误!</strong> 密码无法被修改.</div>
    <div class="alert alert-danger" role="alert" v-if="doNotMatch">您输入的密码和确认密码不匹配!</div>

    <el-form label-width="110px" @submit.prevent="changePassword()">
      <el-form-item label="当前密码" :error="fieldError(v$.resetPassword.currentPassword, '请输入当前密码')">
        <el-input
          type="password"
          show-password
          name="currentPassword"
          placeholder="您的当前密码"
          v-model="v$.resetPassword.currentPassword.$model"
          data-cy="currentPassword"
        />
      </el-form-item>
      <el-form-item label="新密码" :error="fieldError(v$.resetPassword.newPassword, '新密码长度需为 4 - 100 个字符')">
        <el-input
          type="password"
          show-password
          name="newPassword"
          placeholder="您的新密码"
          maxlength="100"
          v-model="v$.resetPassword.newPassword.$model"
          data-cy="newPassword"
        />
      </el-form-item>
      <el-form-item label="确认新密码" :error="fieldError(v$.resetPassword.confirmPassword, '两次输入的密码不一致')">
        <el-input
          type="password"
          show-password
          name="confirmPassword"
          placeholder="再次输入新密码"
          maxlength="100"
          v-model="v$.resetPassword.confirmPassword.$model"
          data-cy="confirmPassword"
        />
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          native-type="submit"
          :loading="isSaving"
          :disabled="v$.resetPassword.$invalid"
          data-cy="change-password-submit"
          >保存</el-button
        >
        <el-button @click="resetForm()" :disabled="isSaving">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script lang="ts" src="./change-password.component.ts"></script>
