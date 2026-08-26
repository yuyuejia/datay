<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <form name="editForm" novalidate @submit.prevent="save()">
        <h2 id="datafusionApp.dpTable.home.createOrEditLabel" data-cy="DpTableCreateUpdateHeading">创建或编辑 Dp Table</h2>
        <div>
          <div class="form-group" v-if="dpTable.id">
            <label for="id">ID</label>
            <input type="text" class="form-control" id="id" name="id" v-model="dpTable.id" readonly />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="dp-table-name">Name</label>
            <input
              type="text"
              class="form-control"
              name="name"
              id="dp-table-name"
              data-cy="name"
              :class="{ valid: !v$.name.$invalid, invalid: v$.name.$invalid }"
              v-model="v$.name.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="dp-table-schemaName">Schema Name</label>
            <input
              type="text"
              class="form-control"
              name="schemaName"
              id="dp-table-schemaName"
              data-cy="schemaName"
              :class="{ valid: !v$.schemaName.$invalid, invalid: v$.schemaName.$invalid }"
              v-model="v$.schemaName.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="dp-table-description">Description</label>
            <input
              type="text"
              class="form-control"
              name="description"
              id="dp-table-description"
              data-cy="description"
              :class="{ valid: !v$.description.$invalid, invalid: v$.description.$invalid }"
              v-model="v$.description.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="dp-table-sourceDBType">Source DB Type</label>
            <input
              type="text"
              class="form-control"
              name="sourceDBType"
              id="dp-table-sourceDBType"
              data-cy="sourceDBType"
              :class="{ valid: !v$.sourceDBType.$invalid, invalid: v$.sourceDBType.$invalid }"
              v-model="v$.sourceDBType.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="dp-table-sourceId">Source Id</label>
            <DataSourceSelector
              type="source"
              :datasourceId="dpTable.sourceId"
              :schema="dpTable.sourceSchema"
              @selected="handleSourceSelected"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="dp-table-sourceSchema">Source Schema</label>
            <input
              type="text"
              class="form-control"
              name="sourceSchema"
              id="dp-table-sourceSchema"
              data-cy="sourceSchema"
              :class="{ valid: !v$.sourceSchema.$invalid, invalid: v$.sourceSchema.$invalid }"
              v-model="v$.sourceSchema.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="dp-table-sourceTable">Source Table</label>
            <input
              type="text"
              class="form-control"
              name="sourceTable"
              id="dp-table-sourceTable"
              data-cy="sourceTable"
              :class="{ valid: !v$.sourceTable.$invalid, invalid: v$.sourceTable.$invalid }"
              v-model="v$.sourceTable.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="dp-table-sqlContent">Sql Content</label>
            <input
              type="text"
              class="form-control"
              name="sqlContent"
              id="dp-table-sqlContent"
              data-cy="sqlContent"
              :class="{ valid: !v$.sqlContent.$invalid, invalid: v$.sqlContent.$invalid }"
              v-model="v$.sqlContent.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="dp-table-fileType">File Type</label>
            <input
              type="text"
              class="form-control"
              name="fileType"
              id="dp-table-fileType"
              data-cy="fileType"
              :class="{ valid: !v$.fileType.$invalid, invalid: v$.fileType.$invalid }"
              v-model="v$.fileType.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="dp-table-filePath">File Path</label>
            <input
              type="text"
              class="form-control"
              name="filePath"
              id="dp-table-filePath"
              data-cy="filePath"
              :class="{ valid: !v$.filePath.$invalid, invalid: v$.filePath.$invalid }"
              v-model="v$.filePath.$model"
            />
          </div>
        </div>
        <div>
          <button type="button" id="cancel-save" data-cy="entityCreateCancelButton" class="btn btn-secondary" @click="previousState()">
            <font-awesome-icon icon="ban"></font-awesome-icon>&nbsp;<span>取消</span>
          </button>
          <button
            type="submit"
            id="save-entity"
            data-cy="entityCreateSaveButton"
            :disabled="v$.$invalid || isSaving"
            class="btn btn-primary"
          >
            <font-awesome-icon icon="save"></font-awesome-icon>&nbsp;<span>保存</span>
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
<script lang="ts" src="./dp-table-update.component.ts"></script>
