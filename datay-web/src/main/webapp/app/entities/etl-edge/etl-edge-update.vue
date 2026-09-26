<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <el-form name="editForm" label-width="160px" @submit.prevent="save()">
        <h2 id="datafusionApp.eTLEdge.home.createOrEditLabel" data-cy="ETLEdgeCreateUpdateHeading">创建或编辑 ETL Edge</h2>
        <div>
          <el-form-item v-if="eTLEdge.id" label="ID">
            <el-input id="id" name="id" v-model="eTLEdge.id" readonly />
          </el-form-item>
          <el-form-item label="Task Id">
            <el-input name="taskId" id="etl-edge-taskId" data-cy="taskId" v-model="v$.taskId.$model" />
          </el-form-item>
          <el-form-item label="Name">
            <el-input name="name" id="etl-edge-name" data-cy="name" v-model="v$.name.$model" />
          </el-form-item>
          <el-form-item label="Code">
            <el-input name="code" id="etl-edge-code" data-cy="code" v-model="v$.code.$model" />
          </el-form-item>
          <el-form-item label="Source">
            <el-input name="source" id="etl-edge-source" data-cy="source" v-model="v$.source.$model" />
          </el-form-item>
          <el-form-item label="Target">
            <el-input name="target" id="etl-edge-target" data-cy="target" v-model="v$.target.$model" />
          </el-form-item>
          <el-form-item label="Config">
            <el-input name="config" id="etl-edge-config" data-cy="config" v-model="v$.config.$model" />
          </el-form-item>
          <el-form-item label="Status">
            <el-input name="status" id="etl-edge-status" data-cy="status" v-model="v$.status.$model" />
          </el-form-item>
          <el-form-item label="Tenant Id">
            <el-input name="tenantId" id="etl-edge-tenantId" data-cy="tenantId" v-model="v$.tenantId.$model" />
          </el-form-item>
          <el-form-item label="Dr">
            <el-input-number :controls="false"  name="dr" id="etl-edge-dr" data-cy="dr" v-model="v$.dr.$model" />
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
<script lang="ts" src="./etl-edge-update.component.ts"></script>
