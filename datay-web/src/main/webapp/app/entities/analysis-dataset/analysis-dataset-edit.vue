<template>
  <div class="dataset-editor" v-loading="loading">
    <div class="dataset-editor-header">
      <div class="dataset-editor-title">
        <el-button text @click="cancel" class="mr-2">← 返回</el-button>
        <span>{{ editingId ? '编辑数据集' : '新建数据集' }}</span>
      </div>
      <div class="dataset-editor-actions">
        <el-button :loading="previewLoading" @click="runPreview">校验并预览</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </div>

    <div class="dataset-editor-body">
      <div class="dataset-editor-form">
        <el-form label-width="92px" label-position="right">
          <el-form-item label="名称" required>
            <el-input v-model="form.name" placeholder="例如：订单销售明细" maxlength="120" />
          </el-form-item>
          <el-form-item label="编码">
            <el-input v-model="form.code" placeholder="留空自动生成，如 order_sales" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选" />
          </el-form-item>
          <el-form-item label="类型" required>
            <el-radio-group v-model="form.type" @change="onTypeChange">
              <el-radio-button label="METRIC">指标数据集</el-radio-button>
              <el-radio-button label="SQL">SQL 数据集</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="状态">
            <el-switch
              :model-value="form.status !== 'DISABLED'"
              active-text="启用"
              inactive-text="停用"
              @update:model-value="value => (form.status = value ? 'ENABLED' : 'DISABLED')"
            />
          </el-form-item>

          <template v-if="form.type === 'SQL'">
            <el-form-item label="数据源" required>
              <el-select v-model="form.dataSourceId" filterable placeholder="请选择数据源" style="width: 100%" @change="onDataSourceChange">
                <el-option v-for="dataSource in dataSources" :key="dataSource.id" :label="dataSource.name" :value="dataSource.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="表结构">
              <div class="dataset-table-picker">
                <el-select
                  v-model="selectedSchema"
                  placeholder="schema"
                  style="width: 140px"
                  :disabled="!form.dataSourceId"
                  @change="loadTables"
                >
                  <el-option v-for="schema in schemas" :key="schema" :label="schema" :value="schema" />
                </el-select>
                <el-select
                  v-model="selectedTable"
                  placeholder="选择表"
                  filterable
                  style="width: 220px"
                  :loading="loadingTables"
                  :disabled="!selectedSchema"
                >
                  <el-option v-for="table in tables" :key="table" :label="table" :value="table" />
                </el-select>
                <el-button :disabled="!selectedTable" @click="insertTable">插入查询</el-button>
              </div>
            </el-form-item>
            <el-form-item label="SQL" required>
              <el-input
                v-model="sqlText"
                type="textarea"
                :rows="12"
                class="dataset-sql-input"
                spellcheck="false"
                placeholder="仅支持单条只读查询，例如：SELECT region, SUM(amount) AS amount FROM sales GROUP BY region"
              />
              <div class="dataset-sql-hint">
                <span>支持的筛选器占位符：</span>
                <code v-for="param in placeholders" :key="param.name">{{ '${' + param.name + '}' }}</code>
                <span v-if="!placeholders.length">暂无，使用 ${参数名} 可绑定全局筛选器</span>
              </div>
            </el-form-item>
          </template>

          <template v-else>
            <el-form-item label="指标" required>
              <el-select
                v-model="metricCodes"
                multiple
                filterable
                collapse-tags
                collapse-tags-tooltip
                placeholder="请选择指标"
                style="width: 100%"
                @change="onMetricChange"
              >
                <el-option v-for="metric in metrics" :key="metric.code" :label="metric.name" :value="metric.code">
                  <span>{{ metric.name }}</span>
                  <span class="dataset-option-code">{{ metric.code }}</span>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="维度">
              <div v-loading="loadingMeta" class="dataset-dimensions">
                <div v-if="!metricCodes.length" class="dataset-muted">选择指标后可加载可用维度</div>
                <div v-for="dimension in dimensions" :key="dimension.dimensionModelCode" class="dataset-dimension-item">
                  <el-checkbox v-model="dimension.selected">
                    {{ dimension.dimensionModelName }}
                    <el-tag v-if="dimension.isTimeDimension" size="small" type="info">时间</el-tag>
                  </el-checkbox>
                  <el-select
                    v-model="dimension.fieldName"
                    size="small"
                    clearable
                    placeholder="展示字段"
                    style="width: 200px"
                    :disabled="!dimension.selected"
                  >
                    <el-option
                      v-for="field in dimension.fields"
                      :key="field.fieldName"
                      :label="field.description || field.fieldName"
                      :value="field.fieldName"
                    />
                  </el-select>
                </div>
                <div v-if="metricCodes.length && !loadingMeta && !dimensions.length" class="dataset-muted">当前指标没有可用维度</div>
              </div>
            </el-form-item>
          </template>
        </el-form>
      </div>

      <div class="dataset-editor-preview">
        <div class="dataset-preview-header">
          <span>数据预览</span>
          <span v-if="previewError" class="dataset-preview-error">{{ previewError }}</span>
        </div>
        <div v-if="previewSql" class="dataset-preview-sql">
          <code>{{ previewSql }}</code>
        </div>
        <div class="dataset-preview-table" v-loading="previewLoading">
          <el-table v-if="previewColumns.length" :data="previewRows" border stripe height="100%">
            <el-table-column
              v-for="column in previewColumns"
              :key="column"
              :prop="column"
              :label="column"
              min-width="130"
              show-overflow-tooltip
            />
          </el-table>
          <el-empty v-else description="点击「校验并预览」查看数据" />
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./analysis-dataset-edit.component.ts"></script>

<style scoped>
.dataset-editor {
  padding: 8px 4px;
}

.dataset-editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.dataset-editor-title {
  display: flex;
  align-items: center;
  color: #303133;
  font-size: 18px;
  font-weight: 600;
}

.dataset-editor-actions {
  display: flex;
  gap: 8px;
}

.dataset-editor-body {
  display: flex;
  gap: 16px;
  align-items: stretch;
}

.dataset-editor-form {
  flex: 1;
  min-width: 0;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
}

.dataset-editor-preview {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 560px;
  padding: 12px;
  background: #fff;
  border-radius: 8px;
}

.dataset-preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #303133;
  font-weight: 600;
}

.dataset-preview-error {
  max-width: 60%;
  color: #f56c6c;
  font-size: 12px;
  font-weight: 400;
  text-align: right;
}

.dataset-preview-sql {
  max-height: 90px;
  margin-bottom: 8px;
  padding: 8px;
  overflow: auto;
  background: #f5f7fa;
  border-radius: 4px;
  color: #606266;
  font-size: 11px;
  white-space: pre-wrap;
}

.dataset-preview-table {
  flex: 1;
  min-height: 0;
}

.dataset-table-picker {
  display: flex;
  gap: 8px;
  align-items: center;
}

.dataset-sql-input :deep(textarea) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
}

.dataset-sql-hint {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}

.dataset-sql-hint code {
  padding: 1px 5px;
  background: #f4f4f5;
  border-radius: 3px;
  color: #e6a23c;
}

.dataset-dimensions {
  width: 100%;
  min-height: 60px;
}

.dataset-dimension-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid #f5f7fa;
}

.dataset-muted {
  padding: 8px 0;
  color: #909399;
  font-size: 12px;
}

.dataset-option-code {
  float: right;
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
</style>
