<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <el-form name="editForm" label-width="160px" @submit.prevent="save()">
        <h2 id="datafusionApp.dpTable.home.createOrEditLabel" data-cy="DpTableCreateUpdateHeading">创建或编辑 Dp Table</h2>
        <div>
          <el-form-item v-if="dpTable.id" label="ID">
            <el-input id="id" name="id" v-model="dpTable.id" readonly />
          </el-form-item>
          <el-form-item label="Name">
            <el-input name="name" id="dp-table-name" data-cy="name" v-model="v$.name.$model" />
          </el-form-item>
          <el-form-item label="Schema Name">
            <el-input name="schemaName" id="dp-table-schemaName" data-cy="schemaName" v-model="v$.schemaName.$model" />
          </el-form-item>
          <el-form-item label="Description">
            <el-input name="description" id="dp-table-description" data-cy="description" v-model="v$.description.$model" />
          </el-form-item>
          <el-form-item label="Source DB Type">
            <el-input name="sourceDBType" id="dp-table-sourceDBType" data-cy="sourceDBType" v-model="v$.sourceDBType.$model" />
          </el-form-item>
          <el-form-item label="Source Id">
            <DataSourceSelector
              type="source"
              :datasourceId="dpTable.sourceId"
              :schema="dpTable.sourceSchema"
              @selected="handleSourceSelected"
            />
          </el-form-item>
          <el-form-item label="Source Schema">
            <el-input name="sourceSchema" id="dp-table-sourceSchema" data-cy="sourceSchema" v-model="v$.sourceSchema.$model" />
          </el-form-item>
          <el-form-item label="Source Table">
            <el-input name="sourceTable" id="dp-table-sourceTable" data-cy="sourceTable" v-model="v$.sourceTable.$model" />
          </el-form-item>
          <el-form-item label="Sql Content">
            <el-input name="sqlContent" id="dp-table-sqlContent" data-cy="sqlContent" v-model="v$.sqlContent.$model" />
          </el-form-item>
          <el-form-item label="File Type">
            <el-input name="fileType" id="dp-table-fileType" data-cy="fileType" v-model="v$.fileType.$model" />
          </el-form-item>
          <el-form-item label="File Path">
            <el-input name="filePath" id="dp-table-filePath" data-cy="filePath" v-model="v$.filePath.$model" />
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
<script lang="ts" src="./dp-table-update.component.ts"></script>
