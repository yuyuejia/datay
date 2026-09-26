<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <el-form name="editForm" label-width="160px" @submit.prevent="save()">
        <h2 id="datafusionApp.eTLNode.home.createOrEditLabel" data-cy="ETLNodeCreateUpdateHeading">创建或编辑 ETL Node</h2>
        <div>
          <el-form-item v-if="eTLNode.id" label="ID">
            <el-input id="id" name="id" v-model="eTLNode.id" readonly />
          </el-form-item>
          <el-form-item label="Task Id">
            <el-input name="taskId" id="etl-node-taskId" data-cy="taskId" v-model="v$.taskId.$model" />
          </el-form-item>
          <el-form-item label="Label">
            <el-input name="label" id="etl-node-label" data-cy="label" v-model="v$.label.$model" />
          </el-form-item>
          <el-form-item label="Code">
            <el-input name="code" id="etl-node-code" data-cy="code" v-model="v$.code.$model" />
          </el-form-item>
          <el-form-item label="Desc">
            <el-input name="desc" id="etl-node-desc" data-cy="desc" v-model="v$.desc.$model" />
          </el-form-item>
          <el-form-item label="Type">
            <el-input name="type" id="etl-node-type" data-cy="type" v-model="v$.type.$model" />
          </el-form-item>
          <el-form-item label="Config">
            <el-input name="config" id="etl-node-config" data-cy="config" v-model="v$.config.$model" />
          </el-form-item>
          <el-form-item label="X Axis">
            <el-input name="xAxis" id="etl-node-xAxis" data-cy="xAxis" v-model="v$.xAxis.$model" />
          </el-form-item>
          <el-form-item label="Y Axis">
            <el-input name="yAxis" id="etl-node-yAxis" data-cy="yAxis" v-model="v$.yAxis.$model" />
          </el-form-item>
          <el-form-item label="Status">
            <el-input name="status" id="etl-node-status" data-cy="status" v-model="v$.status.$model" />
          </el-form-item>
          <el-form-item label="Update Time">
            <div class="d-flex">
              <el-date-picker type="datetime" value-format="YYYY-MM-DDTHH:mm" id="etl-node-updateTime" data-cy="updateTime" name="updateTime" :model-value="convertDateTimeFromServer(v$.updateTime.$model)" @update:model-value="value => updateZonedDateTimeField('updateTime', value)" />
            </div>
          </el-form-item>
          <el-form-item label="Create Time">
            <div class="d-flex">
              <el-date-picker type="datetime" value-format="YYYY-MM-DDTHH:mm" id="etl-node-createTime" data-cy="createTime" name="createTime" :model-value="convertDateTimeFromServer(v$.createTime.$model)" @update:model-value="value => updateZonedDateTimeField('createTime', value)" />
            </div>
          </el-form-item>
          <el-form-item label="Tenant Id">
            <el-input name="tenantId" id="etl-node-tenantId" data-cy="tenantId" v-model="v$.tenantId.$model" />
          </el-form-item>
          <el-form-item label="Dr">
            <el-input-number :controls="false"  name="dr" id="etl-node-dr" data-cy="dr" v-model="v$.dr.$model" />
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
<script lang="ts" src="./etl-node-update.component.ts"></script>
