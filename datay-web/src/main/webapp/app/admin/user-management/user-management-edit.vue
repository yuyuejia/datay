<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <el-form
        name="editForm"
        label-width="160px"
        @submit.prevent="save()"
        v-if="userAccount"
      >
        <h2 id="myUserLabel">创建或编辑用户</h2>
        <div>
          <el-form-item :hidden="!userAccount.id" label="ID">
            <el-input name="id" v-model="userAccount.id" readonly />
          </el-form-item>

          <el-form-item label="登录">
            <el-input name="login" v-model="v$.userAccount.login.$model" />

            <div
              v-if="
                v$.userAccount.login.$anyDirty && v$.userAccount.login.$invalid
              "
            >
              <small
                class="form-text text-danger"
                v-if="!v$.userAccount.login.required"
                >本字段不能为空.</small
              >

              <small
                class="form-text text-danger"
                v-if="!v$.userAccount.login.maxLength"
                >本字段最大长度为 50 个字符.</small
              >

              <small
                class="form-text text-danger"
                v-if="!v$.userAccount.login.pattern"
                >This field can only contain letters, digits and e-mail
                addresses.</small
              >
            </div>
          </el-form-item>
          <el-form-item label="名字">
            <el-input id="firstName" name="firstName" placeholder="您的名字" v-model="v$.userAccount.firstName.$model" />
            <div
              v-if="
                v$.userAccount.firstName.$anyDirty &&
                v$.userAccount.firstName.$invalid
              "
            >
              <small
                class="form-text text-danger"
                v-if="!v$.userAccount.firstName.maxLength"
                >本字段最大长度为 50 个字符.</small
              >
            </div>
          </el-form-item>
          <el-form-item label="姓氏">
            <el-input id="lastName" name="lastName" placeholder="您的姓氏" v-model="v$.userAccount.lastName.$model" />
            <div
              v-if="
                v$.userAccount.lastName.$anyDirty &&
                v$.userAccount.lastName.$invalid
              "
            >
              <small
                class="form-text text-danger"
                v-if="!v$.userAccount.lastName.maxLength"
                >本字段最大长度为 50 个字符.</small
              >
            </div>
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input type="email" id="email" name="email" placeholder="您的电子邮件" v-model="v$.userAccount.email.$model" />
            <div
              v-if="
                v$.userAccount.email.$anyDirty && v$.userAccount.email.$invalid
              "
            >
              <small
                class="form-text text-danger"
                v-if="!v$.userAccount.email.format"
                >您的电子邮件格式不正确.</small
              >
              <small
                class="form-text text-danger"
                v-if="!v$.userAccount.email.minLength"
                >您的电子邮件长度至少要有5个字符</small
              >
              <small
                class="form-text text-danger"
                v-if="!v$.userAccount.email.maxLength"
                >您的电子邮件长度不能超过254个字符</small
              >
            </div>
          </el-form-item>
          <el-form-item label="状态">
            <el-checkbox
              :disabled="userAccount.id === null"
              v-model="userAccount.activated"
            >已激活</el-checkbox>
          </el-form-item>

          <el-form-item label="角色">
            <el-select multiple name="authority" v-model="userAccount.authorities">
            <el-option v-for="authority of authorities" :value="authority" :key="authority" :label="authority" />
          </el-select>
          </el-form-item>
        </div>
        <div>
          <el-button @click="previousState()">
            <font-awesome-icon icon="ban"></font-awesome-icon>&nbsp;<span
              >取消</span
            >
          </el-button>
          <el-button type="primary" native-type="submit" :disabled="v$.userAccount.$invalid || isSaving">
            <font-awesome-icon icon="save"></font-awesome-icon>&nbsp;<span
              >保存</span
            >
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script lang="ts" src="./user-management-edit.component.ts"></script>
