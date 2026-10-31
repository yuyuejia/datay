<template>
  <div class="data-compare-update-container">
    <div class="page-header">
      <div class="page-title">
        <h3 id="data-compare-heading">{{ isEdit ? '编辑数据比对任务' : '新增数据比对任务' }}</h3>
        <p class="page-subtitle">配置左右数据源并选择需要比对的表，支持一个任务比对多张表</p>
      </div>
      <div class="page-actions">
        <el-button @click="cancel">取消</el-button>
        <el-button type="primary" :loading="isSaving" :disabled="!formReady" @click="save">保存</el-button>
      </div>
    </div>

    <el-form v-if="formReady" :model="form" label-width="100px" class="compare-form" @submit.prevent>
      <section class="form-section">
        <div class="section-header">
          <div>
            <h4 class="section-title">基本信息</h4>
            <p class="section-hint">任务名称与描述用于标识本次比对任务</p>
          </div>
        </div>
        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="任务名称" required>
              <el-input v-model="form.taskName" maxlength="255" placeholder="请输入任务名称" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="任务描述">
              <el-input v-model="form.taskDesc" maxlength="2000" placeholder="请输入任务描述" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="对比项" required>
          <el-checkbox v-model="form.compareRecordCount">记录数对比</el-checkbox>
          <el-checkbox v-model="form.compareDetail">明细对比</el-checkbox>
          <div class="form-tip">只勾选记录数时，仅比较两侧表的总行数，不生成差异明细，也无需配置主键和比对字段。</div>
        </el-form-item>
      </section>

      <section class="form-section">
        <div class="section-header">
          <div>
            <h4 class="section-title">数据源</h4>
            <p class="section-hint">左侧为基准数据，右侧为对比数据，可来自不同数据库</p>
          </div>
        </div>
        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="左侧数据源" required>
              <DataSourceSelector :datasource-id="form.leftDataSourceId" :schema="form.leftSchema" @selected="handleLeftSource" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="右侧数据源" required>
              <DataSourceSelector :datasource-id="form.rightDataSourceId" :schema="form.rightSchema" @selected="handleRightSource" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="忽略大小写">
              <el-switch v-model="form.ignoreCase" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="去除首尾空格">
              <el-switch v-model="form.trimWhitespace" />
            </el-form-item>
          </el-col>
        </el-row>
      </section>

      <section class="form-section">
        <div class="section-header">
          <div>
            <h4 class="section-title">比对表</h4>
            <p class="section-hint">
              <template v-if="form.compareDetail">选择需要比对的左侧表，右侧表默认同名，可手动修改</template>
              <template v-else>当前为仅记录数对比，选择需要比对的左侧表即可</template>
              <span v-if="form.tables.length" class="selected-count">已选 {{ form.tables.length }} 张表</span>
            </p>
          </div>
          <div class="range-selector">
            <DataSourceTableSelector
              :data-source-id="form.leftDataSourceId"
              :schema="form.leftSchema"
              :selected-tables="selectorTables"
              :selected-table="null"
              :multiple="true"
              @selected="handleSelectedTables"
            />
          </div>
        </div>

        <el-table :data="form.tables" class="table-list" border stripe row-key="leftTable">
          <el-table-column type="expand" width="44">
            <template #default="scope">
              <div class="expand-config">
                <el-row :gutter="20">
                  <el-col :xs="24" :md="12">
                    <el-form-item label="左侧过滤条件">
                      <el-input
                        v-model="scope.row.leftWhere"
                        type="textarea"
                        :rows="3"
                        placeholder="可选，例如 created_at >= '2026-01-01'"
                      />
                    </el-form-item>
                  </el-col>
                  <el-col :xs="24" :md="12">
                    <el-form-item label="右侧过滤条件">
                      <el-input
                        v-model="scope.row.rightWhere"
                        type="textarea"
                        :rows="3"
                        placeholder="可选，例如 updated_at >= '2026-01-01'"
                      />
                    </el-form-item>
                  </el-col>
                </el-row>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="左侧表" min-width="180" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.leftTable }}</template>
          </el-table-column>
          <el-table-column label="右侧表" min-width="180">
            <template #default="scope">
              <el-input v-model="scope.row.rightTable" placeholder="默认与左侧表同名" />
            </template>
          </el-table-column>
          <el-table-column v-if="form.compareDetail" label="主键字段" min-width="220">
            <template #default="scope">
              <el-input v-model="scope.row.keyColumns" placeholder="多个字段用逗号分隔，例如 id,tenant_id" />
            </template>
          </el-table-column>
          <el-table-column v-if="form.compareDetail" label="比对字段" min-width="250">
            <template #default="scope">
              <el-input v-model="scope.row.compareColumns" placeholder="为空则自动比对两侧所有公共非主键字段" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="scope">
              <el-button link type="danger" @click="removeTable(scope.$index)">移除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="empty-hint">尚未选择比对表，点击右上角「选择表」添加</div>
          </template>
        </el-table>
      </section>
    </el-form>
  </div>
</template>

<script lang="ts" src="./data-compare-update.component.ts"></script>

<style scoped>
.data-compare-update-container {
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

.form-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
  margin-top: 4px;
}

.range-selector {
  flex: none;
  width: 380px;
  max-width: 100%;
}

.compare-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.compare-form :deep(.input-group) {
  margin-bottom: 0;
}

.table-list {
  width: 100%;
}

.expand-config {
  padding: 8px 20px 0;
  background: #fafafa;
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
