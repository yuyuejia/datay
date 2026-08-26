<template>
  <div class="row justify-content-center">
    <div class="col-10">
      <div class="wizard">
        <div class="content">
          <h5 class="mb-3">基本信息</h5>
          <div class="row g-2 align-items-center mb-2">
            <label class="col-sm-2 col-form-label-sm text-end" for="data-sync-jobName">Job Name</label>
            <div class="col-sm-4">
              <input
                type="text"
                class="form-control form-control-sm"
                name="jobName"
                id="data-sync-jobName"
                data-cy="jobName"
                :class="{ valid: !v$.jobName.$invalid, invalid: v$.jobName.$invalid }"
                v-model="v$.jobName.$model"
              />
            </div>
            <label class="col-sm-2 col-form-label-sm text-end" for="data-sync-jobDesc">Job Desc</label>
            <div class="col-sm-4">
              <input
                type="text"
                class="form-control form-control-sm"
                name="jobDesc"
                id="data-sync-jobDesc"
                data-cy="jobDesc"
                :class="{ valid: !v$.jobDesc.$invalid, invalid: v$.jobDesc.$invalid }"
                v-model="v$.jobDesc.$model"
              />
            </div>
          </div>
          <div class="row g-2 align-items-center mb-2">
            <label class="col-sm-2 col-form-label-sm text-end" for="data-sync-type">同步类型</label>
            <div class="col-sm-4">
              <select
                class="form-select form-select-sm"
                name="type"
                id="data-sync-type"
                data-cy="type"
                :class="{ valid: !v$.type.$invalid, invalid: v$.type.$invalid }"
                v-model="v$.type.$model"
              >
                <option value="">请选择同步类型</option>
                <option value="SCHEMA_SYNC">表结构同步</option>
                <option value="FULL_SYNC">全量同步</option>
                <option value="INCREMENTAL_SYNC">增量同步</option>
              </select>
            </div>
            <label class="col-sm-2 col-form-label-sm text-end" for="data-sync-cron">Cron</label>
            <div class="col-sm-4">
              <input
                type="text"
                class="form-control form-control-sm"
                name="cron"
                id="data-sync-cron"
                data-cy="cron"
                :class="{ valid: !v$.cron.$invalid, invalid: v$.cron.$invalid }"
                v-model="v$.cron.$model"
              />
            </div>
          </div>
          <div class="row g-2 align-items-center mb-2">
            <label class="col-sm-2 col-form-label-sm text-end">Source</label>
            <div class="col-sm-4">
              <DataSourceSelector
                v-if="dataSyncLoaded"
                type="source"
                :datasourceId="sourceDataSourceId"
                :schema="sourceSchema"
                @selected="handleSourceSelected"
              />
            </div>
            <label class="col-sm-2 col-form-label-sm text-end">Target</label>
            <div class="col-sm-4">
              <DataSourceSelector
                v-if="dataSyncLoaded"
                type="target"
                :datasourceId="targetDataSourceId"
                :schema="targetSchema"
                @selected="handleTargetSelected"
              />
            </div>
          </div>

          <div class="d-flex justify-content-between align-items-center mb-3 mt-3">
            <h5 class="mb-0">选择表</h5>
            <DataSourceTableSelector
              :dataSourceId="sourceDataSourceId"
              :schema="sourceSchema"
              :selectedTables="selectedTables"
              :multiple="true"
              @selected="updateSelectedTables"
            />
          </div>
          <el-table :data="selectedTables" style="width: 100%" class="mt-3" border stripe>
            <el-table-column prop="srcTableName" label="表名" min-width="180" />
            <el-table-column label="目标表名" min-width="200">
              <template #default="scope">
                <el-input v-model="scope.row.desTableName" size="small" />
              </template>
            </el-table-column>
            <el-table-column v-if="dataSync.type === 'INCREMENTAL_SYNC'" label="增量字段" min-width="220">
              <template #default="scope">
                <el-input v-model="scope.row.srcColPks" size="small" placeholder="例如：id,update_time" />
              </template>
            </el-table-column>
          </el-table>

          <button type="button" id="cancel-save" data-cy="entityCreateCancelButton" class="btn btn-secondary" @click="previousState()">
            <font-awesome-icon icon="ban"></font-awesome-icon>&nbsp;<span>取消</span>
          </button>
          <button
            type="submit"
            class="btn btn-primary"
            @click="save"
            :disabled="
              v$.jobName.$invalid ||
              v$.jobDesc.$invalid ||
              v$.type.$invalid ||
              v$.cron.$invalid ||
              selectedTables.length === 0 ||
              (dataSync.type === 'INCREMENTAL_SYNC' && !validateIncrementalFields())
            "
          >
            保存
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./data-sync-update.component.ts"></script>
