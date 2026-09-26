<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <el-form name="editForm" label-width="160px" @submit.prevent="save()">
        <h2 id="datafusionApp.serviceConfig.home.createOrEditLabel" data-cy="ServiceConfigCreateUpdateHeading">
          创建或编辑服务配置
        </h2>
        <div>
          <el-form-item v-if="serviceConfig.id" label="ID">
            <el-input id="id" name="id" v-model="serviceConfig.id" readonly />
          </el-form-item>
          <el-form-item label="配置分组">
            <el-input name="dfGroup" id="service-config-dfGroup" data-cy="dfGroup" v-model="v$.dfGroup.$model" />
          </el-form-item>
          <el-form-item label="配置项">
            <el-input name="dfKey" id="service-config-dfKey" data-cy="dfKey" v-model="v$.dfKey.$model" />
          </el-form-item>
          <el-form-item label="配置值">
            <el-input name="dfValue" id="service-config-dfValue" data-cy="dfValue" v-model="v$.dfValue.$model" />
          </el-form-item>
          <el-form-item label="创建时间">
            <div class="d-flex">
              <el-date-picker type="datetime" value-format="YYYY-MM-DDTHH:mm" id="service-config-createTime" data-cy="createTime" name="createTime" :model-value="convertDateTimeFromServer(v$.createTime.$model)" @update:model-value="value => updateZonedDateTimeField('createTime', value)" />
            </div>
          </el-form-item>
          <el-form-item label="租户ID">
            <el-input name="tenantId" id="service-config-tenantId" data-cy="tenantId" v-model="v$.tenantId.$model" />
          </el-form-item>
        </div>
        <div>
          <el-button id="cancel-save" data-cy="entityCreateCancelButton" @click="previousState()">
            <font-awesome-icon icon="ban"></font-awesome-icon>&nbsp;<span>取消</span>
          </el-button>
          <el-button type="primary" native-type="submit" id="save-entity" data-cy="entityCreateSaveButton" :disabled="v$.$invalid || isSaving">
            <font-awesome-icon icon="save"></font-awesome-icon>&nbsp;<span>保存</span>
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>
<script lang="ts" src="./service-config-update.component.ts"></script>
