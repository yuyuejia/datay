<template>
<div class="row justify-content-center">
  <div class="col-8">
    <form name="editForm" novalidate @submit.prevent="save()" v-if="tenant">
      <h2 id="tenantLabel">创建或编辑租户</h2>
      <div>
        <div class="form-group" :hidden="!tenant.id">
          <label>ID</label>
          <input type="text" class="form-control" name="id" v-model="tenant.id" readonly />
        </div>

        <div class="form-group">
          <label class="form-control-label">租户编码</label>
          <input
            type="text"
            class="form-control"
            name="code"
            :class="{ valid: !v$.tenant.code.$invalid, invalid: v$.tenant.code.$invalid }"
            v-model="v$.tenant.code.$model"
            placeholder="唯一标识，如 system"
            :disabled="tenant.code === 'system'"
          />
          <div v-if="v$.tenant.code.$anyDirty && v$.tenant.code.$invalid">
            <small class="form-text text-danger" v-if="!v$.tenant.code.required">本字段不能为空.</small>
            <small class="form-text text-danger" v-if="!v$.tenant.code.maxLength">本字段最大长度为 50 个字符.</small>
          </div>
        </div>

        <div class="form-group">
          <label class="form-control-label">租户名称</label>
          <input
            type="text"
            class="form-control"
            name="name"
            :class="{ valid: !v$.tenant.name.$invalid, invalid: v$.tenant.name.$invalid }"
            v-model="v$.tenant.name.$model"
            placeholder="租户显示名称"
          />
          <div v-if="v$.tenant.name.$anyDirty && v$.tenant.name.$invalid">
            <small class="form-text text-danger" v-if="!v$.tenant.name.required">本字段不能为空.</small>
            <small class="form-text text-danger" v-if="!v$.tenant.name.maxLength">本字段最大长度为 100 个字符.</small>
          </div>
        </div>

        <div class="form-group">
          <label class="form-control-label">描述</label>
          <textarea
            type="text"
            class="form-control"
            name="description"
            v-model="tenant.description"
            placeholder="租户描述信息"
            rows="3"
          ></textarea>
        </div>

        <div class="form-group">
          <label class="form-check-label">
            <input type="checkbox" class="form-check-input" v-model="tenant.enabled" />
            启用状态
          </label>
        </div>
      </div>
      <div>
        <button type="button" class="btn btn-secondary" @click="previousState">
          <font-awesome-icon icon="ban"></font-awesome-icon>&nbsp;<span>取消</span>
        </button>
        <button type="submit" class="btn btn-primary" :disabled="isSaving">
          <font-awesome-icon icon="save"></font-awesome-icon>&nbsp;<span>保存</span>
        </button>
      </div>
    </form>
  </div>
</div>
</template>

<script lang="ts" src="./tenant-management-edit.component.ts"></script>