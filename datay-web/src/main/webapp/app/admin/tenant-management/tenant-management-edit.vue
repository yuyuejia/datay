<template>
<div class="row justify-content-center">
  <div class="col-8">
    <el-form name="editForm" label-width="160px" @submit.prevent="save()" v-if="tenant">
      <h2 id="tenantLabel">创建或编辑租户</h2>
      <div>
        <el-form-item :hidden="!tenant.id" label="ID">
            <el-input name="id" v-model="tenant.id" readonly />
          </el-form-item>

        <el-form-item label="租户编码">
            <el-input name="code" v-model="v$.tenant.code.$model" placeholder="唯一标识，如 system" :disabled="tenant.code === 'system'" />
          <div v-if="v$.tenant.code.$anyDirty && v$.tenant.code.$invalid">
            <small class="form-text text-danger" v-if="!v$.tenant.code.required">本字段不能为空.</small>
            <small class="form-text text-danger" v-if="!v$.tenant.code.maxLength">本字段最大长度为 50 个字符.</small>
          </div>
          </el-form-item>

        <el-form-item label="租户名称">
            <el-input name="name" v-model="v$.tenant.name.$model" placeholder="租户显示名称" />
          <div v-if="v$.tenant.name.$anyDirty && v$.tenant.name.$invalid">
            <small class="form-text text-danger" v-if="!v$.tenant.name.required">本字段不能为空.</small>
            <small class="form-text text-danger" v-if="!v$.tenant.name.maxLength">本字段最大长度为 100 个字符.</small>
          </div>
          </el-form-item>

        <el-form-item label="描述">
            <el-input type="textarea" name="description" v-model="tenant.description" placeholder="租户描述信息" :rows="3" />
          </el-form-item>

        <el-form-item label="启用状态">
            <el-switch v-model="tenant.enabled" />
          </el-form-item>
      </div>
      <div>
        <el-button @click="previousState">
          <font-awesome-icon icon="ban"></font-awesome-icon>&nbsp;<span>取消</span>
        </el-button>
        <el-button type="primary" native-type="submit" :disabled="isSaving">
          <font-awesome-icon icon="save"></font-awesome-icon>&nbsp;<span>保存</span>
        </el-button>
      </div>
    </el-form>
  </div>
</div>
</template>

<script lang="ts" src="./tenant-management-edit.component.ts"></script>