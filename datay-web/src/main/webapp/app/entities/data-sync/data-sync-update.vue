<template>
  <div class="data-sync-update-container">
    <div class="page-header">
      <div class="page-title">
        <h3 id="data-sync-heading" data-cy="DataSyncCreateUpdateHeading">
          {{ dataSync.id ? '编辑数据迁移任务' : '新增数据迁移任务' }}
        </h3>
        <p class="page-subtitle">配置源端与目标端数据源，选择需要迁移的表</p>
      </div>
      <div class="page-actions">
        <el-button id="cancel-save" data-cy="entityCreateCancelButton" @click="previousState()">
          <font-awesome-icon icon="ban"></font-awesome-icon>
          <span>取消</span>
        </el-button>
        <el-button
          type="primary"
          data-cy="entityCreateSaveButton"
          :loading="isSaving"
          :disabled="v$.jobName.$invalid || v$.jobDesc.$invalid || v$.type.$invalid || v$.cron.$invalid || selectedTables.length === 0"
          @click="save()"
        >
          <font-awesome-icon icon="save"></font-awesome-icon>
          <span>保存</span>
        </el-button>
      </div>
    </div>

    <el-form :model="dataSync" label-width="100px" class="data-sync-form" @submit.prevent>
      <section class="form-section">
        <div class="section-header">
          <div>
            <h4 class="section-title">基本信息</h4>
            <p class="section-hint">任务名称与同步类型用于标识本次迁移任务</p>
          </div>
        </div>

        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="任务名称" required>
              <el-input name="jobName" id="data-sync-jobName" data-cy="jobName" v-model="v$.jobName.$model" placeholder="请输入任务名称" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="同步类型" required>
              <el-radio-group v-model="v$.type.$model" data-cy="type">
                <el-radio value="FULL_SYNC">结构和数据</el-radio>
                <el-radio value="DATA_ONLY">仅数据</el-radio>
                <el-radio value="SCHEMA_ONLY">仅结构</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="源端数据源">
              <DataSourceSelector
                v-if="dataSyncLoaded"
                type="source"
                :datasource-id="sourceDataSourceId"
                :schema="sourceSchema"
                @selected="handleSourceSelected"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="目标数据源">
              <DataSourceSelector
                v-if="dataSyncLoaded"
                type="target"
                :datasource-id="targetDataSourceId"
                :schema="targetSchema"
                @selected="handleTargetSelected"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </section>

      <section class="form-section">
        <div class="section-header">
          <div>
            <h4 class="section-title">同步范围</h4>
            <p class="section-hint">
              选择需要迁移的表，并可调整目标表名
              <span v-if="selectedTables.length" class="selected-count">已选 {{ selectedTables.length }} 张表</span>
            </p>
          </div>
          <div class="range-selector">
            <DataSourceTableSelector
              :data-source-id="sourceDataSourceId"
              :schema="sourceSchema"
              :selected-tables="selectedTables"
              :multiple="true"
              @selected="updateSelectedTables"
            />
          </div>
        </div>

        <el-table :data="selectedTables" class="table-list" border stripe>
          <el-table-column prop="srcTableName" label="表名" min-width="180" />
          <el-table-column label="目标表名" min-width="200">
            <template #default="scope">
              <el-input v-model="scope.row.desTableName" placeholder="请输入目标表名" />
            </template>
          </el-table-column>
          <template #empty>
            <div class="empty-hint">尚未选择表，点击右上角「选择表」添加</div>
          </template>
        </el-table>
      </section>
    </el-form>
  </div>
</template>

<script lang="ts" src="./data-sync-update.component.ts"></script>

<style scoped>
.data-sync-update-container {
  padding: 20px;
  background: #fff;
  border-radius: 4px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.page-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.page-subtitle {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}

.page-actions {
  display: flex;
  flex: none;
  gap: 8px;
}

.form-section {
  margin-bottom: 28px;
}

.form-section + .form-section {
  padding-top: 24px;
  border-top: 1px solid #f0f2f5;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.section-hint {
  margin: 4px 0 0;
  color: #909399;
  font-size: 13px;
}

.selected-count {
  margin-left: 8px;
  color: #409eff;
}

.range-selector {
  flex: none;
  width: 380px;
  max-width: 100%;
}

.data-sync-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.data-sync-form :deep(.input-group) {
  margin-bottom: 0;
}

.data-sync-form :deep(.el-radio-group) {
  min-height: 32px;
  align-items: center;
}

.table-list {
  width: 100%;
}

.empty-hint {
  padding: 28px 0;
  color: #909399;
  font-size: 13px;
}

@media (max-width: 767px) {
  .page-header {
    flex-direction: column;
  }

  .page-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .range-selector {
    width: 100%;
  }
}
</style>
