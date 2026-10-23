<template>
  <div class="dataset-editor" v-loading="loading">
    <div class="dataset-editor-header">
      <h3>{{ editingId ? '编辑数据集' : '新建数据集' }}</h3>
      <div class="dataset-editor-actions">
        <el-button @click="cancel">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </div>

    <div class="dataset-editor-body">
      <div class="dataset-editor-form">
        <el-form :model="form" label-width="100px" label-position="right">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="名称" required>
                <el-input v-model="form.name" placeholder="例如：订单销售明细" maxlength="120" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="编码">
                <el-input v-model="form.code" placeholder="留空自动生成，如 order_sales" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="类型" required>
                <el-radio-group v-model="form.type" @change="onTypeChange">
                  <el-radio-button label="METRIC">指标数据集</el-radio-button>
                  <el-radio-button label="SQL">SQL 数据集</el-radio-button>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态">
                <el-switch
                  :model-value="form.status !== 'DISABLED'"
                  active-text="启用"
                  inactive-text="停用"
                  @update:model-value="value => (form.status = value ? 'ENABLED' : 'DISABLED')"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="描述">
                <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入数据集描述" />
              </el-form-item>
            </el-col>
          </el-row>

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
            <div class="dataset-query-section">
              <div class="dataset-query-section-header">
                <h5>指标</h5>
                <span class="dataset-query-hint">选择本次数据集需要分析的指标</span>
              </div>
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
              <div v-if="factTables.length" class="dataset-fact-tables">
                <span class="dataset-query-hint">事实表：</span>
                <el-tag v-for="table in factTables" :key="table" size="small" type="info">{{ table }}</el-tag>
              </div>
            </div>

            <div class="dataset-query-section">
              <div class="dataset-query-section-header">
                <h5>维度</h5>
                <span class="dataset-query-hint">勾选维度后可设置层级和展示字段</span>
              </div>
              <div v-loading="loadingMeta" class="dataset-dimensions">
                <div v-if="!metricCodes.length" class="dataset-muted">选择指标后可加载可用维度</div>
                <div v-for="dimension in dimensions" :key="dimension.dimensionModelCode" class="dataset-dimension-item">
                  <el-checkbox v-model="dimension.selected">
                    {{ dimension.dimensionModelName }}
                    <el-tag v-if="dimension.isTimeDimension" size="small" type="info">时间</el-tag>
                    <el-tag v-if="dimension.isHierarchy" size="small" type="warning">层级</el-tag>
                  </el-checkbox>
                  <div class="dataset-dimension-controls">
                    <el-select
                      v-if="dimension.isHierarchy && dimensionLevels(dimension).length"
                      v-model="dimension.levelIndex"
                      placeholder="选择层级"
                      class="dataset-dimension-level-select"
                      :disabled="!dimension.selected"
                    >
                      <el-option
                        v-for="level in dimensionLevels(dimension)"
                        :key="level.levelIndex"
                        :label="dimensionLevelLabel(dimension, level.levelIndex)"
                        :value="level.levelIndex"
                      />
                    </el-select>
                    <el-select
                      v-model="dimension.fieldNames"
                      multiple
                      collapse-tags
                      collapse-tags-tooltip
                      filterable
                      clearable
                      :placeholder="dimension.isHierarchy ? '展示字段（可选，选择后按字段分组）' : '展示字段（可多选）'"
                      class="dataset-dimension-field-select"
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
                </div>
                <div v-if="metricCodes.length && !loadingMeta && !dimensions.length" class="dataset-muted">当前指标没有可用维度</div>
              </div>
            </div>

            <div class="dataset-query-section">
              <div class="dataset-query-section-header">
                <h5>业务限定</h5>
                <el-button type="primary" size="small" @click="addFilterCondition"> <font-awesome-icon icon="plus" /> 添加条件 </el-button>
              </div>
              <div v-if="!filterConditions.length" class="dataset-muted">未设置业务限定</div>
              <div v-for="(condition, index) in filterConditions" :key="`condition-${index}`" class="dataset-condition-row">
                <el-select v-if="index > 0" v-model="condition.logic" class="dataset-condition-logic" size="small">
                  <el-option value="AND" label="AND" />
                  <el-option value="OR" label="OR" />
                </el-select>
                <span v-else class="dataset-condition-logic dataset-condition-logic-placeholder">条件</span>
                <el-select
                  v-model="condition.dimensionModelCode"
                  class="dataset-condition-control"
                  placeholder="选择维度"
                  filterable
                  @change="onFilterDimensionChange(condition)"
                >
                  <el-option
                    v-for="dimension in dimensions"
                    :key="dimension.dimensionModelCode"
                    :label="dimension.dimensionModelName || dimension.dimensionModelCode"
                    :value="dimension.dimensionModelCode"
                  />
                </el-select>
                <el-select
                  v-model="condition.dimensionFieldName"
                  class="dataset-condition-control"
                  placeholder="选择维度字段"
                  filterable
                  :disabled="!condition.dimensionModelCode"
                >
                  <el-option
                    v-for="field in conditionDimensionFields(condition)"
                    :key="field.fieldName"
                    :label="field.description || field.fieldName"
                    :value="field.fieldName"
                  />
                </el-select>
                <el-select v-model="condition.operator" class="dataset-condition-operator">
                  <el-option v-for="op in filterOperators" :key="op.value" :value="op.value" :label="op.label" />
                </el-select>
                <span v-if="isUnaryOperator(condition.operator)" class="dataset-query-placeholder">-</span>
                <template v-else-if="isRangeOperator(condition.operator)">
                  <el-input v-model="condition.value" class="dataset-condition-value" placeholder="开始值" />
                  <el-input v-model="condition.valueEnd" class="dataset-condition-value" placeholder="结束值" />
                </template>
                <el-input v-else v-model="condition.value" class="dataset-condition-value" placeholder="值" />
                <el-button link type="danger" size="small" @click="removeFilterCondition(index)">
                  <font-awesome-icon icon="trash" />
                </el-button>
              </div>
            </div>

            <div class="dataset-query-section">
              <div class="dataset-query-section-header">
                <h5>时间统计范围</h5>
                <el-button v-if="queryTimeRange.start || queryTimeRange.end" link type="info" size="small" @click="clearTimeRange">
                  <font-awesome-icon icon="times" /> 清除
                </el-button>
              </div>
              <div class="dataset-time-row">
                <el-date-picker
                  v-model="queryTimeRange.start"
                  class="dataset-time-picker"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  placeholder="开始时间"
                />
                <span class="dataset-query-placeholder">~</span>
                <el-date-picker
                  v-model="queryTimeRange.end"
                  class="dataset-time-picker"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  placeholder="结束时间"
                />
              </div>
              <div v-if="timeFields.length" class="dataset-time-hint">
                <span class="dataset-query-hint">时间字段（事实表配置）：</span>
                <el-tag v-for="field in timeFields" :key="field.fieldName" size="small" type="info">
                  {{ field.fieldName }}
                </el-tag>
              </div>
              <div v-else class="dataset-time-hint-empty">所选指标的事实表未配置时间周期字段，时间范围将不生效</div>
            </div>
          </template>
        </el-form>
      </div>
    </div>

    <div class="dataset-fields-panel">
      <div class="dataset-fields-header">
        <div class="dataset-fields-title">
          <h4>字段信息</h4>
          <span class="dataset-fields-hint">校验并预览后自动带出字段，可设置描述、类型和主键</span>
        </div>
        <el-button :loading="previewLoading" @click="runPreview">
          <font-awesome-icon icon="eye" class="mr-1" />
          预览数据
        </el-button>
      </div>
      <el-table v-if="fieldMetadata.length" :data="fieldMetadata" border stripe size="small">
        <el-table-column prop="name" label="字段名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="描述" min-width="260">
          <template #default="{ row }">
            <el-input v-model="row.description" placeholder="请输入字段描述" clearable />
          </template>
        </el-table-column>
        <el-table-column label="字段类型" width="160">
          <template #default="{ row }">
            <el-select v-model="row.type" filterable allow-create placeholder="选择或输入类型">
              <el-option value="string" label="string" />
              <el-option value="number" label="number" />
              <el-option value="date" label="date" />
              <el-option value="boolean" label="boolean" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="主键" width="80" align="center">
          <template #default="{ row }">
            <el-checkbox v-model="row.primaryKey" />
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无字段信息，请先校验并预览" :image-size="60" />
    </div>

    <app-modal v-model="previewVisible" size="lg" :title="`数据预览 · ${form.name || '未命名数据集'}`">
      <el-alert v-if="previewError" type="error" :title="previewError" :closable="false" class="mb-2" />
      <div v-if="previewSql" class="dataset-preview-sql">
        <code>{{ previewSql }}</code>
      </div>
      <div v-loading="previewLoading" class="dataset-preview-table">
        <el-table v-if="previewColumns.length" :data="previewRows" border stripe height="420">
          <el-table-column
            v-for="column in previewColumns"
            :key="column"
            :prop="column"
            :label="previewColumnLabel(column)"
            min-width="130"
            show-overflow-tooltip
          />
        </el-table>
        <el-empty v-else-if="!previewLoading && !previewError" description="查询成功，无数据返回" />
      </div>
      <template #modal-footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="primary" :loading="previewLoading" @click="runPreview">重新预览</el-button>
      </template>
    </app-modal>
  </div>
</template>

<script lang="ts" src="./analysis-dataset-edit.component.ts"></script>

<style scoped>
.dataset-editor {
  width: 100%;
  padding: 20px;
  background: #fff;
  border-radius: 4px;
}

.dataset-editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.dataset-editor-header h3 {
  margin: 0;
  color: #303133;
  font-size: 18px;
  font-weight: 600;
}

.dataset-editor-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
  align-items: center;
}

.dataset-editor-body {
  display: block;
}

.dataset-editor-form {
  min-width: 0;
  margin-bottom: 32px;
}

.dataset-fields-panel {
  margin-top: 24px;
}

.dataset-fields-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 16px;
  color: #303133;
}

.dataset-fields-title {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.dataset-fields-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.dataset-fields-hint {
  color: #909399;
  font-size: 12px;
  font-weight: 400;
}

.dataset-preview-sql {
  max-height: 120px;
  margin-bottom: 12px;
  padding: 10px 12px;
  overflow: auto;
  background: #f7f8fa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.dataset-preview-table {
  min-height: 180px;
}

.dataset-table-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.dataset-sql-input :deep(textarea) {
  min-height: 240px;
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

.dataset-query-section {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fafcff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}

.dataset-query-section-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f2f5;
}

.dataset-query-section-header h5 {
  margin: 0;
  color: #303133;
  font-size: 14px;
  font-weight: 600;
}

.dataset-query-hint {
  color: #909399;
  font-size: 12px;
}

.dataset-fact-tables {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-top: 8px;
}

.dataset-dimensions {
  width: 100%;
  min-height: 60px;
}

.dataset-dimension-item {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #f5f7fa;
}

.dataset-dimension-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  max-width: 100%;
}

.dataset-dimension-field-select {
  width: 380px;
  max-width: 100%;
}

.dataset-dimension-level-select {
  width: 160px;
}

.dataset-condition-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.dataset-condition-logic {
  flex: none;
  width: 80px;
}

.dataset-condition-logic-placeholder {
  color: #909399;
  font-size: 13px;
  text-align: center;
}

.dataset-condition-control {
  flex: 1 1 180px;
  min-width: 150px;
}

.dataset-condition-operator {
  flex: 0 0 140px;
  min-width: 0;
}

.dataset-condition-value {
  flex: 1 1 140px;
  min-width: 0;
}

.dataset-query-placeholder {
  flex: none;
  padding: 0 4px;
  color: #c0c4cc;
}

.dataset-time-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.dataset-time-picker {
  flex: 0 0 220px;
  width: 220px;
}

.dataset-time-hint {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-top: 8px;
}

.dataset-time-hint-empty {
  margin-top: 8px;
  color: #e6a23c;
  font-size: 13px;
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

@media (max-width: 768px) {
  .dataset-editor-header {
    flex-direction: column;
    align-items: stretch;
  }

  .dataset-editor-actions {
    justify-content: flex-end;
  }
}
</style>
