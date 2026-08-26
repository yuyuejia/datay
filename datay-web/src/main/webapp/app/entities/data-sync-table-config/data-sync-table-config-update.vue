<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <form name="editForm" novalidate @submit.prevent="save()">
        <h2 id="datafusionApp.dataSyncTableConfig.home.createOrEditLabel" data-cy="DataSyncTableConfigCreateUpdateHeading">
          创建或编辑 Data Sync Table Config
        </h2>
        <div>
          <div class="form-group" v-if="dataSyncTableConfig.id">
            <label for="id">ID</label>
            <input type="text" class="form-control" id="id" name="id" v-model="dataSyncTableConfig.id" readonly />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-syncTask">Sync Task</label>
            <input
              type="text"
              class="form-control"
              name="syncTask"
              id="data-sync-table-config-syncTask"
              data-cy="syncTask"
              :class="{ valid: !v$.syncTask.$invalid, invalid: v$.syncTask.$invalid }"
              v-model="v$.syncTask.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-srcDatasource">Src Datasource</label>
            <input
              type="text"
              class="form-control"
              name="srcDatasource"
              id="data-sync-table-config-srcDatasource"
              data-cy="srcDatasource"
              :class="{ valid: !v$.srcDatasource.$invalid, invalid: v$.srcDatasource.$invalid }"
              v-model="v$.srcDatasource.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-srcSchemaName">Src Schema Name</label>
            <input
              type="text"
              class="form-control"
              name="srcSchemaName"
              id="data-sync-table-config-srcSchemaName"
              data-cy="srcSchemaName"
              :class="{ valid: !v$.srcSchemaName.$invalid, invalid: v$.srcSchemaName.$invalid }"
              v-model="v$.srcSchemaName.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-srcTableName">Src Table Name</label>
            <input
              type="text"
              class="form-control"
              name="srcTableName"
              id="data-sync-table-config-srcTableName"
              data-cy="srcTableName"
              :class="{ valid: !v$.srcTableName.$invalid, invalid: v$.srcTableName.$invalid }"
              v-model="v$.srcTableName.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-srcColPks">Src Col Pks</label>
            <input
              type="text"
              class="form-control"
              name="srcColPks"
              id="data-sync-table-config-srcColPks"
              data-cy="srcColPks"
              :class="{ valid: !v$.srcColPks.$invalid, invalid: v$.srcColPks.$invalid }"
              v-model="v$.srcColPks.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-desDatasource">Des Datasource</label>
            <input
              type="text"
              class="form-control"
              name="desDatasource"
              id="data-sync-table-config-desDatasource"
              data-cy="desDatasource"
              :class="{ valid: !v$.desDatasource.$invalid, invalid: v$.desDatasource.$invalid }"
              v-model="v$.desDatasource.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-desSchemaName">Des Schema Name</label>
            <input
              type="text"
              class="form-control"
              name="desSchemaName"
              id="data-sync-table-config-desSchemaName"
              data-cy="desSchemaName"
              :class="{ valid: !v$.desSchemaName.$invalid, invalid: v$.desSchemaName.$invalid }"
              v-model="v$.desSchemaName.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-desTableName">Des Table Name</label>
            <input
              type="text"
              class="form-control"
              name="desTableName"
              id="data-sync-table-config-desTableName"
              data-cy="desTableName"
              :class="{ valid: !v$.desTableName.$invalid, invalid: v$.desTableName.$invalid }"
              v-model="v$.desTableName.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-desColPks">Des Col Pks</label>
            <input
              type="text"
              class="form-control"
              name="desColPks"
              id="data-sync-table-config-desColPks"
              data-cy="desColPks"
              :class="{ valid: !v$.desColPks.$invalid, invalid: v$.desColPks.$invalid }"
              v-model="v$.desColPks.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-jobDesc">Job Desc</label>
            <input
              type="text"
              class="form-control"
              name="jobDesc"
              id="data-sync-table-config-jobDesc"
              data-cy="jobDesc"
              :class="{ valid: !v$.jobDesc.$invalid, invalid: v$.jobDesc.$invalid }"
              v-model="v$.jobDesc.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-columeConfig">Colume Config</label>
            <input
              type="text"
              class="form-control"
              name="columeConfig"
              id="data-sync-table-config-columeConfig"
              data-cy="columeConfig"
              :class="{ valid: !v$.columeConfig.$invalid, invalid: v$.columeConfig.$invalid }"
              v-model="v$.columeConfig.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-updateTime">Update Time</label>
            <div class="d-flex">
              <input
                id="data-sync-table-config-updateTime"
                data-cy="updateTime"
                type="datetime-local"
                class="form-control"
                name="updateTime"
                :class="{ valid: !v$.updateTime.$invalid, invalid: v$.updateTime.$invalid }"
                :value="convertDateTimeFromServer(v$.updateTime.$model)"
                @change="updateZonedDateTimeField('updateTime', $event)"
              />
            </div>
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-createTime">Create Time</label>
            <div class="d-flex">
              <input
                id="data-sync-table-config-createTime"
                data-cy="createTime"
                type="datetime-local"
                class="form-control"
                name="createTime"
                :class="{ valid: !v$.createTime.$invalid, invalid: v$.createTime.$invalid }"
                :value="convertDateTimeFromServer(v$.createTime.$model)"
                @change="updateZonedDateTimeField('createTime', $event)"
              />
            </div>
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-project">Project</label>
            <input
              type="text"
              class="form-control"
              name="project"
              id="data-sync-table-config-project"
              data-cy="project"
              :class="{ valid: !v$.project.$invalid, invalid: v$.project.$invalid }"
              v-model="v$.project.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-tenantId">Tenant Id</label>
            <input
              type="text"
              class="form-control"
              name="tenantId"
              id="data-sync-table-config-tenantId"
              data-cy="tenantId"
              :class="{ valid: !v$.tenantId.$invalid, invalid: v$.tenantId.$invalid }"
              v-model="v$.tenantId.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-sync-table-config-dr">Dr</label>
            <input
              type="number"
              class="form-control"
              name="dr"
              id="data-sync-table-config-dr"
              data-cy="dr"
              :class="{ valid: !v$.dr.$invalid, invalid: v$.dr.$invalid }"
              v-model.number="v$.dr.$model"
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
<script lang="ts" src="./data-sync-table-config-update.component.ts"></script>
