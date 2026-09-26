<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <el-form name="editForm" label-width="160px" @submit.prevent="save()">
        <h2 id="datafusionApp.dataSyncTableConfig.home.createOrEditLabel" data-cy="DataSyncTableConfigCreateUpdateHeading">
          创建或编辑 Data Sync Table Config
        </h2>
        <div>
          <el-form-item v-if="dataSyncTableConfig.id" label="ID">
            <el-input id="id" name="id" v-model="dataSyncTableConfig.id" readonly />
          </el-form-item>
          <el-form-item label="Sync Task">
            <el-input name="syncTask" id="data-sync-table-config-syncTask" data-cy="syncTask" v-model="v$.syncTask.$model" />
          </el-form-item>
          <el-form-item label="Src Datasource">
            <el-input name="srcDatasource" id="data-sync-table-config-srcDatasource" data-cy="srcDatasource" v-model="v$.srcDatasource.$model" />
          </el-form-item>
          <el-form-item label="Src Schema Name">
            <el-input name="srcSchemaName" id="data-sync-table-config-srcSchemaName" data-cy="srcSchemaName" v-model="v$.srcSchemaName.$model" />
          </el-form-item>
          <el-form-item label="Src Table Name">
            <el-input name="srcTableName" id="data-sync-table-config-srcTableName" data-cy="srcTableName" v-model="v$.srcTableName.$model" />
          </el-form-item>
          <el-form-item label="Src Col Pks">
            <el-input name="srcColPks" id="data-sync-table-config-srcColPks" data-cy="srcColPks" v-model="v$.srcColPks.$model" />
          </el-form-item>
          <el-form-item label="Des Datasource">
            <el-input name="desDatasource" id="data-sync-table-config-desDatasource" data-cy="desDatasource" v-model="v$.desDatasource.$model" />
          </el-form-item>
          <el-form-item label="Des Schema Name">
            <el-input name="desSchemaName" id="data-sync-table-config-desSchemaName" data-cy="desSchemaName" v-model="v$.desSchemaName.$model" />
          </el-form-item>
          <el-form-item label="Des Table Name">
            <el-input name="desTableName" id="data-sync-table-config-desTableName" data-cy="desTableName" v-model="v$.desTableName.$model" />
          </el-form-item>
          <el-form-item label="Des Col Pks">
            <el-input name="desColPks" id="data-sync-table-config-desColPks" data-cy="desColPks" v-model="v$.desColPks.$model" />
          </el-form-item>
          <el-form-item label="Job Desc">
            <el-input name="jobDesc" id="data-sync-table-config-jobDesc" data-cy="jobDesc" v-model="v$.jobDesc.$model" />
          </el-form-item>
          <el-form-item label="Colume Config">
            <el-input name="columeConfig" id="data-sync-table-config-columeConfig" data-cy="columeConfig" v-model="v$.columeConfig.$model" />
          </el-form-item>
          <el-form-item label="Update Time">
            <div class="d-flex">
              <el-date-picker type="datetime" value-format="YYYY-MM-DDTHH:mm" id="data-sync-table-config-updateTime" data-cy="updateTime" name="updateTime" :model-value="convertDateTimeFromServer(v$.updateTime.$model)" @update:model-value="value => updateZonedDateTimeField('updateTime', value)" />
            </div>
          </el-form-item>
          <el-form-item label="Create Time">
            <div class="d-flex">
              <el-date-picker type="datetime" value-format="YYYY-MM-DDTHH:mm" id="data-sync-table-config-createTime" data-cy="createTime" name="createTime" :model-value="convertDateTimeFromServer(v$.createTime.$model)" @update:model-value="value => updateZonedDateTimeField('createTime', value)" />
            </div>
          </el-form-item>
          <el-form-item label="Project">
            <el-input name="project" id="data-sync-table-config-project" data-cy="project" v-model="v$.project.$model" />
          </el-form-item>
          <el-form-item label="Tenant Id">
            <el-input name="tenantId" id="data-sync-table-config-tenantId" data-cy="tenantId" v-model="v$.tenantId.$model" />
          </el-form-item>
          <el-form-item label="Dr">
            <el-input-number :controls="false"  name="dr" id="data-sync-table-config-dr" data-cy="dr" v-model="v$.dr.$model" />
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
<script lang="ts" src="./data-sync-table-config-update.component.ts"></script>
