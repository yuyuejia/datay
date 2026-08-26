<template>
  <div>
    <div class="row justify-content-center">
      <div class="col-md-8 toastify-container">
        <h2 v-if="username" id="settings-title">
          <span
            >[<strong>{{ username }}</strong
            >] 的用户设置</span
          >
        </h2>

        <div class="alert alert-success" role="alert" v-if="success"><strong>设置保存成功!</strong></div>

        <div class="alert alert-danger" role="alert" v-if="errorEmailExists"><strong>邮件已经被注册!</strong> 请选择其它邮件.</div>

        <form name="form" id="settings-form" @submit.prevent="save()" v-if="settingsAccount" novalidate>
          <div class="form-group">
            <label class="form-control-label" for="firstName">名字</label>
            <input
              type="text"
              class="form-control"
              id="firstName"
              name="firstName"
              placeholder="您的名字"
              :class="{ valid: !v$.settingsAccount.firstName.$invalid, invalid: v$.settingsAccount.firstName.$invalid }"
              v-model="v$.settingsAccount.firstName.$model"
              minlength="1"
              maxlength="50"
              required
              data-cy="firstname"
            />
            <div v-if="v$.settingsAccount.firstName.$anyDirty && v$.settingsAccount.firstName.$invalid">
              <small class="form-text text-danger" v-if="!v$.settingsAccount.firstName.required">您的名字是必填项.</small>
              <small class="form-text text-danger" v-if="!v$.settingsAccount.firstName.minLength">您的名字长度至少要有1个字符</small>
              <small class="form-text text-danger" v-if="!v$.settingsAccount.firstName.maxLength">您的名字长度不能超过50个字符</small>
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
              :class="{ valid: !v$.settingsAccount.lastName.$invalid, invalid: v$.settingsAccount.lastName.$invalid }"
              v-model="v$.settingsAccount.lastName.$model"
              minlength="1"
              maxlength="50"
              required
              data-cy="lastname"
            />
            <div v-if="v$.settingsAccount.lastName.$anyDirty && v$.settingsAccount.lastName.$invalid">
              <small class="form-text text-danger" v-if="!v$.settingsAccount.lastName.required">您的姓氏是必填项.</small>
              <small class="form-text text-danger" v-if="!v$.settingsAccount.lastName.minLength">您的姓氏长度至少要有1个字符</small>
              <small class="form-text text-danger" v-if="!v$.settingsAccount.lastName.maxLength">您的姓氏长度不能超过50个字符</small>
            </div>
          </div>
          <div class="form-group">
            <label class="form-control-label" for="email">电子邮件</label>
            <input
              type="email"
              class="form-control"
              id="email"
              name="email"
              placeholder="您的电子邮件"
              :class="{ valid: !v$.settingsAccount.email.$invalid, invalid: v$.settingsAccount.email.$invalid }"
              v-model="v$.settingsAccount.email.$model"
              minlength="5"
              maxlength="254"
              email
              required
              data-cy="email"
            />
            <div v-if="v$.settingsAccount.email.$anyDirty && v$.settingsAccount.email.$invalid">
              <small class="form-text text-danger" v-if="!v$.settingsAccount.email.required">您的电子邮件是必填项.</small>
              <small class="form-text text-danger" v-if="!v$.settingsAccount.email.email">您的电子邮件格式格式不正确.</small>
              <small class="form-text text-danger" v-if="!v$.settingsAccount.email.minLength">您的电子邮件长度至少要有5个字符</small>
              <small class="form-text text-danger" v-if="!v$.settingsAccount.email.maxLength">您的电子邮件长度不能超过50个字符</small>
            </div>
          </div>
          <button type="submit" :disabled="v$.settingsAccount.$invalid" class="btn btn-primary" data-cy="submit">保存</button>
        </form>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./settings.component.ts"></script>
