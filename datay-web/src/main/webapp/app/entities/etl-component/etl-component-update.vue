<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <el-form name="editForm" label-width="160px" @submit.prevent="save()">
        <h2 id="datafusionApp.eTLComponent.home.createOrEditLabel" data-cy="ETLComponentCreateUpdateHeading">创建或编辑 ETL Component</h2>
        <div>
          <el-form-item v-if="eTLComponent.id" label="ID">
            <el-input id="id" name="id" v-model="eTLComponent.id" readonly />
          </el-form-item>
          <el-form-item label="Name">
            <el-input name="name" id="etl-component-name" data-cy="name" v-model="v$.name.$model" />
          </el-form-item>
          <el-form-item label="Code">
            <el-input name="code" id="etl-component-code" data-cy="code" v-model="v$.code.$model" />
          </el-form-item>
          <el-form-item label="Desc">
            <el-input name="desc" id="etl-component-desc" data-cy="desc" v-model="v$.desc.$model" />
          </el-form-item>
          <el-form-item label="Group">
            <el-input name="group" id="etl-component-group" data-cy="group" v-model="v$.group.$model" />
          </el-form-item>
          <el-form-item label="Type">
            <el-input name="type" id="etl-component-type" data-cy="type" v-model="v$.type.$model" />
          </el-form-item>
          <el-form-item label="Config">
            <el-input name="config" id="etl-component-config" data-cy="config" v-model="v$.config.$model" />
          </el-form-item>
          <el-form-item label="Status">
            <el-input name="status" id="etl-component-status" data-cy="status" v-model="v$.status.$model" />
          </el-form-item>
          <el-form-item label="Update Time">
            <div class="d-flex">
              <el-date-picker type="datetime" value-format="YYYY-MM-DDTHH:mm" id="etl-component-updateTime" data-cy="updateTime" name="updateTime" :model-value="convertDateTimeFromServer(v$.updateTime.$model)" @update:model-value="value => updateZonedDateTimeField('updateTime', value)" />
            </div>
          </el-form-item>
          <el-form-item label="Create Time">
            <div class="d-flex">
              <el-date-picker type="datetime" value-format="YYYY-MM-DDTHH:mm" id="etl-component-createTime" data-cy="createTime" name="createTime" :model-value="convertDateTimeFromServer(v$.createTime.$model)" @update:model-value="value => updateZonedDateTimeField('createTime', value)" />
            </div>
          </el-form-item>
          <el-form-item label="Creater">
            <el-input name="creater" id="etl-component-creater" data-cy="creater" v-model="v$.creater.$model" />
          </el-form-item>
          <el-form-item label="Tenant Id">
            <el-input name="tenantId" id="etl-component-tenantId" data-cy="tenantId" v-model="v$.tenantId.$model" />
          </el-form-item>
          <el-form-item label="Dr">
            <el-input-number :controls="false"  name="dr" id="etl-component-dr" data-cy="dr" v-model="v$.dr.$model" />
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
<script lang="ts" src="./etl-component-update.component.ts"></script>
