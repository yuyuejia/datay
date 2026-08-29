<template>
  <div class="row justify-content-center">
    <div class="col-10">
      <div class="wizard">
        <div class="content">
          <h5 class="mb-3">基本信息</h5>
          <div class="row g-2 align-items-center mb-2">
            <label class="col-sm-2 col-form-label-sm text-end" for="data-sync-jobName">任务名称</label>
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
          </div>
          <div class="row g-2 align-items-center mb-2">
            <label class="col-sm-2 col-form-label-sm text-end">同步类型</label>
            <div class="col-sm-4">
              <div
                class="form-check form-check-inline"
                :class="{ valid: !v$.type.$invalid, invalid: v$.type.$invalid }"
                data-cy="type"
              >
                <input
                  class="form-check-input"
                  type="radio"
                  name="type"
                  id="data-sync-type-full"
                  value="FULL_SYNC"
                  v-model="v$.type.$model"
                />
                <label class="form-check-label" for="data-sync-type-full">结构和数据</label>
              </div>
              <div class="form-check form-check-inline">
                <input
                  class="form-check-input"
                  type="radio"
                  name="type"
                  id="data-sync-type-data-only"
                  value="DATA_ONLY"
                  v-model="v$.type.$model"
                />
                <label class="form-check-label" for="data-sync-type-data-only">仅数据</label>
              </div>
              <div class="form-check form-check-inline">
                <input
                  class="form-check-input"
                  type="radio"
                  name="type"
                  id="data-sync-type-schema-only"
                  value="SCHEMA_ONLY"
                  v-model="v$.type.$model"
                />
                <label class="form-check-label" for="data-sync-type-schema-only">仅结构</label>
              </div>
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
            <h5 class="mb-0">同步范围</h5>
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
                <input v-model="scope.row.desTableName" type="text" class="form-control form-control-sm" />
              </template>
            </el-table-column>
          </el-table>

          <div class="d-flex justify-content-end gap-2 mt-3">
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
                selectedTables.length === 0
              "
            >
              保存
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./data-sync-update.component.ts"></script>