<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <form name="editForm" novalidate @submit.prevent="save()" v-if="userAccount">
        <h2 id="myUserLabel">创建或编辑用户</h2>
        <div>
          <div class="form-group" :hidden="!userAccount.id">
            <label>ID</label>
            <input type="text" class="form-control" name="id" v-model="userAccount.id" readonly />
          </div>

          <div class="form-group">
            <label class="form-control-label">登录</label>
            <input
              type="text"
              class="form-control"
              name="login"
              :class="{ valid: !v$.userAccount.login.$invalid, invalid: v$.userAccount.login.$invalid }"
              v-model="v$.userAccount.login.$model"
            />

            <div v-if="v$.userAccount.login.$anyDirty && v$.userAccount.login.$invalid">
              <small class="form-text text-danger" v-if="!v$.userAccount.login.required">本字段不能为空.</small>

              <small class="form-text text-danger" v-if="!v$.userAccount.login.maxLength">本字段最大长度为 50 个字符.</small>

              <small class="form-text text-danger" v-if="!v$.userAccount.login.pattern"
                >This field can only contain letters, digits and e-mail addresses.</small
              >
            </div>
          </div>
          <div class="form-group">
            <label class="form-control-label" for="firstName">名字</label>
            <input
              type="text"
              class="form-control"
              id="firstName"
              name="firstName"
              placeholder="您的名字"
              :class="{ valid: !v$.userAccount.firstName.$invalid, invalid: v$.userAccount.firstName.$invalid }"
              v-model="v$.userAccount.firstName.$model"
            />
            <div v-if="v$.userAccount.firstName.$anyDirty && v$.userAccount.firstName.$invalid">
              <small class="form-text text-danger" v-if="!v$.userAccount.firstName.maxLength">本字段最大长度为 50 个字符.</small>
            </div>
          </div>
          <div class="form-group">
            <label class="form-control-label" for="lastName">姓氏</label>
            <input
              type="text"
              class="form-control"
              id="lastName"
              name="lastName"
              placeholder="您的姓氏"
              :class="{ valid: !v$.userAccount.lastName.$invalid, invalid: v$.userAccount.lastName.$invalid }"
              v-model="v$.userAccount.lastName.$model"
            />
            <div v-if="v$.userAccount.lastName.$anyDirty && v$.userAccount.lastName.$invalid">
              <small class="form-text text-danger" v-if="!v$.userAccount.lastName.maxLength">本字段最大长度为 50 个字符.</small>
            </div>
          </div>
          <div class="form-group">
            <label class="form-control-label" for="email">邮箱</label>
            <input
              type="email"
              class="form-control"
              id="email"
              name="email"
              placeholder="您的电子邮件"
              :class="{ valid: !v$.userAccount.email.$invalid, invalid: v$.userAccount.email.$invalid }"
              v-model="v$.userAccount.email.$model"
              email
              required
            />
            <div v-if="v$.userAccount.email.$anyDirty && v$.userAccount.email.$invalid">
              <small class="form-text text-danger" v-if="!v$.userAccount.email.required">您的电子邮件是必填项.</small>
              <small class="form-text text-danger" v-if="!v$.userAccount.email.email">您的电子邮件格式格式不正确.</small>
              <small class="form-text text-danger" v-if="!v$.userAccount.email.minLength">您的电子邮件长度至少要有5个字符</small>
              <small class="form-text text-danger" v-if="!v$.userAccount.email.maxLength">您的电子邮件长度不能超过50个字符</small>
            </div>
          </div>
          <div class="form-check">
            <label class="form-check-label" for="activated">
              <input
                class="form-check-input"
                :disabled="userAccount.id === null"
                type="checkbox"
                id="activated"
                name="activated"
                v-model="userAccount.activated"
              />
              <span>已激活</span>
            </label>
          </div>

          <div class="form-group">
            <label>角色</label>
            <select class="form-control" multiple name="authority" v-model="userAccount.authorities">
              <option v-for="authority of authorities" :value="authority" :key="authority">{{ authority }}</option>
            </select>
          </div>
        </div>
        <div>
          <button type="button" class="btn btn-secondary" @click="previousState()">
            <font-awesome-icon icon="ban"></font-awesome-icon>&nbsp;<span>取消</span>
          </button>
          <button type="submit" :disabled="v$.userAccount.$invalid || isSaving" class="btn btn-primary">
            <font-awesome-icon icon="save"></font-awesome-icon>&nbsp;<span>保存</span>
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script lang="ts" src="./user-management-edit.component.ts"></script>
