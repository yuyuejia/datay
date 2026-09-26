<template>
  <div class="metric-container">
    <div class="page-header">
      <h4 class="page-title">指标管理</h4>
      <div class="page-header-actions">
        <el-button
          type="primary"
          @click="showAddMetricDialog(selectedDirectoryId)"
        >
          <font-awesome-icon icon="plus" /> 新建指标
        </el-button>
        <el-button type="success" @click="openQuery">
          <font-awesome-icon icon="chart-line" /> 指标查询
        </el-button>
      </div>
    </div>
    <div class="metric-layout">
      <div class="tree-panel">
        <div class="tree-header">
          <h5>指标目录</h5>
          <div class="tree-actions">
            <el-button
              type="primary"
              size="small"
              @click="showAddDirectoryDialog(null)"
            >
              <font-awesome-icon icon="plus" /> 目录
            </el-button>
          </div>
        </div>
        <div class="tree-search">
          <el-input
            v-model="treeSearch"
            placeholder="搜索指标名称或编码"
            clearable
          >
            <template #prefix>
              <font-awesome-icon icon="search" />
            </template>
          </el-input>
        </div>
        <div class="tree-body" v-loading="loading">
          <el-tree
            ref="treeRef"
            :data="treeData"
            :props="treeProps"
            :filter-node-method="filterNode"
            node-key="id"
            default-expand-all
            highlight-current
            :expand-on-click-node="true"
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <span class="custom-tree-node">
                <span v-if="data.type === 'directory'">
                  <font-awesome-icon
                    icon="folder"
                    class="tree-icon folder-icon"
                  />
                  <span>{{ node.label }}</span>
                </span>
                <span v-else>
                  <font-awesome-icon
                    icon="chart-line"
                    class="tree-icon metric-icon"
                  />
                  <span>{{ node.label }}</span>
                </span>
                <span class="tree-node-actions">
                  <el-button
                    v-if="data.type === 'directory'"
                    link
                    type="primary"
                    size="small"
                    title="新增子目录"
                    @click.stop="showAddDirectoryDialog(data.id)"
                  >
                    <font-awesome-icon icon="plus" />
                  </el-button>
                  <el-button
                    v-if="data.type === 'directory'"
                    link
                    type="primary"
                    size="small"
                    title="新增指标"
                    @click.stop="showAddMetricDialog(data.id)"
                  >
                    <font-awesome-icon icon="chart-line" />
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    size="small"
                    title="删除"
                    @click.stop="handleDeleteNode(data)"
                  >
                    <font-awesome-icon icon="trash" />
                  </el-button>
                </span>
              </span>
            </template>
          </el-tree>
        </div>
      </div>

      <div class="detail-panel">
        <div v-if="!selectedMetric && !queryMode" class="empty-state">
          <font-awesome-icon icon="arrow-left" class="empty-icon" />
          <p>请从左侧目录树选择一个指标查看详情</p>
          <el-button type="primary" @click="showAddMetricDialog(null)">
            <font-awesome-icon icon="plus" /> 新建指标
          </el-button>
        </div>
        <div v-else class="metric-detail">
          <div class="detail-header">
            <h4>
              {{
                queryMode
                  ? selectedMetric
                    ? "指标查询：" + selectedMetric.name
                    : "指标查询"
                  : selectedMetric.name
              }}
            </h4>
            <div class="detail-actions">
              <template v-if="queryMode">
                <el-button type="primary" size="small" @click="closeQuery">
                  <font-awesome-icon icon="arrow-left" /> 返回详情
                </el-button>
              </template>
              <template v-else>
                <el-button
                  type="info"
                  size="small"
                  @click="previewSql(selectedMetric)"
                >
                  <font-awesome-icon icon="code" /> 预览 SQL
                </el-button>
                <router-link
                  :to="{
                    name: 'MetricEdit',
                    params: { metricId: selectedMetric.id },
                  }"
                  custom
                  v-slot="{ navigate }"
                >
                  <el-button type="primary" size="small" @click="navigate">
                    <font-awesome-icon icon="pencil-alt" /> 编辑
                  </el-button>
                </router-link>
                <el-button
                  type="danger"
                  size="small"
                  @click="prepareRemove(selectedMetric)"
                >
                  <font-awesome-icon icon="trash" /> 删除
                </el-button>
              </template>
            </div>
          </div>

          <div
            v-if="queryMode"
            class="metric-query"
            v-loading="queryMetaLoading"
          >
            <div class="query-section">
              <div class="query-section-header">
                <h5>指标</h5>
                <el-button type="primary" size="small" @click="addQueryMetric">
                  <font-awesome-icon icon="plus" /> 添加指标
                </el-button>
              </div>
              <div v-if="queryMetricRows.length === 0" class="query-empty">
                请至少添加一个指标
              </div>
              <div
                v-for="(row, index) in queryMetricRows"
                :key="'metric' + index"
                class="query-row"
              >
                <el-select
                  v-model="row.metricCode"
                  class="query-control metric-select"
                  placeholder="请选择指标"
                  filterable
                  @change="onQueryMetricChange"
                >
                  <el-option
                    v-for="m in allMetricOptions"
                    :key="m.code"
                    :value="m.code"
                    :label="`${m.name} (${m.code})`"
                  />
                </el-select>
                <el-button
                  v-if="queryMetricRows.length > 1"
                  link
                  type="danger"
                  size="small"
                  @click="removeQueryMetric(index)"
                >
                  <font-awesome-icon icon="trash" />
                </el-button>
              </div>
              <div
                v-if="
                  queryMeta &&
                  queryMeta.factTables &&
                  queryMeta.factTables.length
                "
                class="query-fact-tables"
              >
                <span class="query-fact-label">事实表：</span>
                <el-tag
                  v-for="t in queryMeta.factTables"
                  :key="t"
                  size="small"
                  type="info"
                >
                  {{ t }}
                </el-tag>
              </div>
            </div>

            <div class="query-section">
              <div class="query-section-header">
                <h5>维度</h5>
                <el-button type="primary" size="small" @click="addDimension">
                  <font-awesome-icon icon="plus" /> 添加维度
                </el-button>
              </div>
              <div v-if="queryDimensions.length === 0" class="query-empty">
                未选择维度，将返回指标汇总值
              </div>
              <div
                v-for="(dim, index) in queryDimensions"
                :key="'dim' + index"
                class="query-row"
              >
                <el-select
                  class="query-control"
                  v-model="dim.dimensionModelCode"
                  placeholder="选择维度"
                  @change="onDimensionModelChange(dim)"
                >
                  <el-option
                    v-for="dimension in queryDimensionOptions"
                    :key="dimension.dimensionModelCode"
                    :value="dimension.dimensionModelCode"
                    :label="
                      dimension.dimensionModelName ||
                      dimension.dimensionModelCode
                    "
                  />
                </el-select>
                <el-select
                  v-if="dim.dimensionModelCode && isHierarchyDimension(dim)"
                  class="query-control"
                  v-model="dim.levelIndex"
                  placeholder="选择层级"
                >
                  <el-option
                    v-for="lv in dimensionLevels(dim)"
                    :key="lv.levelIndex"
                    :value="lv.levelIndex"
                    :label="levelLabel(dim, lv.levelIndex)"
                  />
                </el-select>
                <el-select
                  v-else-if="dim.dimensionModelCode"
                  v-model="dim.dimensionFieldNames"
                  class="query-control"
                  multiple
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="默认按主键汇总"
                >
                  <el-option
                    v-for="field in dim._dimensionFields || []"
                    :key="field.fieldName"
                    :value="field.fieldName"
                    :label="field.fieldName"
                  />
                </el-select>
                <el-button
                  link
                  type="danger"
                  size="small"
                  @click="removeDimension(index)"
                >
                  <font-awesome-icon icon="trash" />
                </el-button>
              </div>
            </div>

            <div class="query-section">
              <div class="query-section-header">
                <h5>业务限定</h5>
                <el-button
                  type="primary"
                  size="small"
                  @click="addQueryCondition"
                >
                  <font-awesome-icon icon="plus" /> 添加条件
                </el-button>
              </div>
              <div v-if="queryConditions.length === 0" class="query-empty">
                未设置业务限定
              </div>
              <div
                v-for="(condition, index) in queryConditions"
                :key="'cond' + index"
                class="query-row"
              >
                <el-select
                  v-if="index > 0"
                  v-model="condition.logic"
                  class="query-logic"
                  size="small"
                >
                  <el-option value="AND" label="AND" />
                  <el-option value="OR" label="OR" />
                </el-select>
                <span v-else class="query-logic placeholder">条件</span>
                <el-select
                  class="query-control"
                  v-model="condition.dimensionModelCode"
                  placeholder="选择维度"
                  @change="onQueryConditionModelChange(condition)"
                >
                  <el-option
                    v-for="dimension in queryDimensionOptions"
                    :key="dimension.dimensionModelCode"
                    :value="dimension.dimensionModelCode"
                    :label="
                      dimension.dimensionModelName ||
                      dimension.dimensionModelCode
                    "
                  />
                </el-select>
                <el-select
                  v-if="condition.dimensionModelCode"
                  class="query-control"
                  v-model="condition.dimensionFieldName"
                  placeholder="选择维度字段"
                >
                  <el-option
                    v-for="field in condition._dimensionFields || []"
                    :key="field.fieldName"
                    :value="field.fieldName"
                    :label="field.fieldName"
                  />
                </el-select>
                <el-select
                  class="query-control operator"
                  v-model="condition.operator"
                >
                  <el-option
                    v-for="op in filterOperators"
                    :key="op.value"
                    :value="op.value"
                    :label="op.label"
                  />
                </el-select>
                <template v-if="isUnaryOperator(condition.operator)">
                  <span class="query-placeholder">-</span>
                </template>
                <template v-else-if="isRangeOperator(condition.operator)">
                  <el-input
                    class="query-control value"
                    v-model="condition.value"
                    placeholder="开始值"
                  />
                  <el-input
                    class="query-control value"
                    v-model="condition.valueEnd"
                    placeholder="结束值"
                  />
                </template>
                <template v-else>
                  <el-input
                    class="query-control value"
                    v-model="condition.value"
                    placeholder="值"
                  />
                </template>
                <el-button
                  link
                  type="danger"
                  size="small"
                  @click="removeQueryCondition(index)"
                >
                  <font-awesome-icon icon="trash" />
                </el-button>
              </div>
            </div>

            <div class="query-section">
              <div class="query-section-header">
                <h5>时间统计范围</h5>
              </div>
              <div class="query-row">
                <el-date-picker
                  class="query-date-picker"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  placeholder="开始时间"
                  v-model="queryTimeRange.start"
                />
                <span class="query-placeholder">~</span>
                <el-date-picker
                  class="query-date-picker"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  placeholder="结束时间"
                  v-model="queryTimeRange.end"
                />
                <el-button
                  v-if="queryTimeRange.start || queryTimeRange.end"
                  link
                  type="info"
                  size="small"
                  @click="clearQueryTimeRange"
                >
                  <font-awesome-icon icon="times" /> 清除
                </el-button>
              </div>
              <div v-if="queryTimeFields.length > 0" class="query-time-hint">
                <span class="query-time-hint-label"
                  >时间字段（事实表配置）：</span
                >
                <el-tag
                  v-for="field in queryTimeFields"
                  :key="field.fieldName"
                  size="small"
                  type="info"
                >
                  {{ field.fieldName }}
                </el-tag>
              </div>
              <div v-else class="query-time-hint-empty">
                所选指标的事实表未配置时间周期字段，时间范围将不生效
              </div>
            </div>

            <div class="query-actions">
              <el-button
                type="primary"
                :loading="queryLoading"
                @click="runQuery"
              >
                <font-awesome-icon icon="search" /> 查询
              </el-button>
              <el-button @click="previewQuerySql">查看 SQL</el-button>
            </div>

            <div v-if="queryColumns.length > 0" class="query-result">
              <el-table
                :data="queryRows"
                border
                size="small"
                style="width: 100%"
                max-height="360"
              >
                <el-table-column
                  v-for="col in queryColumns"
                  :key="col"
                  :prop="col"
                  :label="col"
                  min-width="140"
                />
              </el-table>
              <div class="query-result-count">共 {{ queryRows.length }} 行</div>
            </div>

            <div v-if="querySql" class="query-sql">
              <div class="query-section-header">
                <h5>查询 SQL</h5>
              </div>
              <pre class="sql-code">{{ querySql }}</pre>
            </div>
          </div>

          <div v-if="!queryMode" class="detail-info">
            <el-form
              :model="selectedMetric"
              label-width="100px"
              class="metric-detail-form"
            >
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-form-item label="指标名称">
                    <span class="detail-value">{{ selectedMetric.name }}</span>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="指标编码">
                    <span class="detail-value"
                      ><code>{{ selectedMetric.code || "-" }}</code></span
                    >
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="指标类型">
                    <el-tag
                      :type="typeTag(selectedMetric.metricType)"
                      size="small"
                      >{{ typeLabel(selectedMetric.metricType) }}</el-tag
                    >
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-form-item label="状态">
                    <el-tag
                      :type="
                        selectedMetric.status === 'DISABLED'
                          ? 'info'
                          : 'success'
                      "
                      size="small"
                    >
                      {{ statusLabel(selectedMetric.status) }}
                    </el-tag>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="创建时间">
                    <span class="detail-value">{{
                      formatDateLong(selectedMetric.createTime) || "-"
                    }}</span>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="更新时间">
                    <span class="detail-value">{{
                      formatDateLong(selectedMetric.updateTime) || "-"
                    }}</span>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-form-item label="单位">
                    <span class="detail-value">{{
                      selectedMetric.unit || "-"
                    }}</span>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="数据类型">
                    <span class="detail-value">{{
                      dataTypeLabel(selectedMetric.dataType)
                    }}</span>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="是否可累加">
                    <el-tag
                      :type="
                        selectedMetric.isAdditive === false ? 'info' : 'success'
                      "
                      size="small"
                    >
                      {{
                        selectedMetric.isAdditive === false
                          ? "不可累加"
                          : "可累加"
                      }}
                    </el-tag>
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row
                v-if="selectedMetric.metricType === 'ATOMIC'"
                :gutter="20"
              >
                <el-col :span="8">
                  <el-form-item label="事实表">
                    <span class="detail-value">{{
                      factDisplay(selectedMetric)
                    }}</span>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="24">
                  <el-form-item label="计算公式">
                    <pre class="formula-code">{{
                      selectedMetric.formula || "-"
                    }}</pre>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row
                v-if="
                  selectedMetric.metricType !== 'ATOMIC' &&
                  (selectedMetric.refMetrics || []).length > 0
                "
                :gutter="20"
              >
                <el-col :span="24">
                  <el-form-item label="引用指标">
                    <div class="ref-tags">
                      <el-tag
                        v-for="ref in selectedMetric.refMetrics"
                        :key="ref.id"
                        size="small"
                        type="info"
                      >
                        {{ ref.name }} ({{ ref.code }})
                      </el-tag>
                    </div>
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="20">
                <el-col :span="24">
                  <el-form-item label="描述">
                    <span class="detail-value detail-description">{{
                      selectedMetric.description || "-"
                    }}</span>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>
          </div>

          <div
            v-if="!queryMode && selectedMetric.metricType === 'ATOMIC'"
            class="detail-conditions"
          >
            <h5>业务限定</h5>
            <el-table
              :data="selectedConditions"
              border
              size="small"
              style="width: 100%"
            >
              <el-table-column label="类型" width="100">
                <template #default="{ row }">
                  <el-tag size="small">{{ filterTypeLabel(row.type) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="字段" min-width="200">
                <template #default="{ row }"
                  ><code>{{ conditionTarget(row) }}</code></template
                >
              </el-table-column>
              <el-table-column label="运算符" width="150">
                <template #default="{ row }">{{
                  operatorLabel(row.operator)
                }}</template>
              </el-table-column>
              <el-table-column label="取值" min-width="180">
                <template #default="{ row }">{{
                  conditionValue(row)
                }}</template>
              </el-table-column>
              <el-table-column label="组合" width="80" align="center">
                <template #default="{ row }">
                  <el-tag size="small" type="info">{{
                    (row.logic || "AND").toUpperCase()
                  }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>
    </div>

    <app-modal
      v-model="directoryDialogVisible"
      :title="directoryDialogTitle"
      size="md"
    >
      <div class="modal-body">
        <el-form name="directoryForm" label-position="top" @submit.prevent="saveDirectory">
          <el-form-item label="目录名称">
            <el-input v-model="directoryForm.name" placeholder="请输入目录名称" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="directoryForm.sortOrder" :min="0" :max="9999" :controls="false" />
          </el-form-item>
        </el-form>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="directoryDialogVisible = false">
            取消
          </el-button>
          <el-button type="primary" @click="saveDirectory">
            确定
          </el-button>
        </div>
      </template>
    </app-modal>

    <app-modal v-model="deleteDialogVisible" title="确认删除" size="md">
      <div class="modal-body">
        <p>{{ deleteMessage }}</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="deleteDialogVisible = false">
            取消
          </el-button>
          <el-button type="danger" @click="confirmDelete">
            确认删除
          </el-button>
        </div>
      </template>
    </app-modal>

    <app-modal v-model="sqlDialogVisible" title="计算 SQL 预览" size="lg">
      <div class="modal-body">
        <div v-if="sqlLoading" class="sql-loading">正在生成 SQL...</div>
        <pre v-else class="sql-code">{{ sqlContent }}</pre>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="sqlDialogVisible = false">
            关闭
          </el-button>
        </div>
      </template>
    </app-modal>
  </div>
</template>

<script lang="ts" src="./metric.component.ts"></script>

<style scoped>
.metric-container {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 4px 12px;
  border-bottom: 1px solid #e4e7ed;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.page-header-actions {
  display: flex;
  gap: 8px;
}

.metric-layout {
  display: flex;
  flex: 1;
  min-height: 0;
  gap: 0;
}

.tree-panel {
  width: 300px;
  min-width: 300px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.tree-header {
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tree-header h5 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.tree-actions {
  display: flex;
  gap: 8px;
}

.tree-search {
  padding: 8px 12px;
  border-bottom: 1px solid #e4e7ed;
}

.tree-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 8px;
}

.tree-icon {
  margin-right: 6px;
}

.folder-icon {
  color: #e6a23c;
}

.metric-icon {
  color: #409eff;
}

.tree-node-actions {
  display: none;
}

.custom-tree-node:hover .tree-node-actions {
  display: inline-flex;
}

.detail-panel {
  flex: 1;
  overflow-y: auto;
  background: #f5f7fa;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
  gap: 12px;
}

.empty-icon {
  font-size: 48px;
}

.metric-detail {
  background: #fff;
  border-radius: 4px;
  padding: 24px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
}

.detail-header h4 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.detail-info {
  margin-bottom: 24px;
}

.metric-detail-form :deep(.el-form-item__content) {
  line-height: 28px;
}

.detail-value {
  font-size: 14px;
  color: #303133;
}

.detail-value code,
.detail-conditions code {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 3px;
  padding: 1px 6px;
  color: #303133;
}

.detail-description {
  display: block;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}

.formula-code {
  margin: 0;
  padding: 10px 12px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px;
  color: #c7254e;
  white-space: pre-wrap;
  word-break: break-all;
}

.ref-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.detail-conditions h5 {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}

.conditions-empty {
  color: #909399;
  font-size: 13px;
  padding: 8px 0;
}

.sql-loading {
  color: #909399;
  text-align: center;
  padding: 30px 0;
}

.sql-code {
  margin: 0;
  padding: 12px;
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: "Consolas", "Monaco", "Courier New", monospace;
  font-size: 13px;
  line-height: 1.6;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 460px;
  overflow: auto;
}

.metric-query {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.query-form {
  margin-bottom: 0;
}

.query-section {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px 16px;
  background: #fafcff;
}

.query-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f2f5;
}

.query-section-header h5 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.query-fact-tables {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.query-fact-label {
  color: #909399;
  font-size: 13px;
}

.query-time-hint {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.query-time-hint-label {
  color: #909399;
  font-size: 13px;
}

.query-time-hint-empty {
  margin-top: 8px;
  color: #e6a23c;
  font-size: 13px;
}

.query-empty {
  color: #909399;
  font-size: 13px;
  padding: 6px 0;
}

.query-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.query-logic {
  width: 80px;
  flex: none;
}

.query-logic.placeholder {
  color: #909399;
  font-size: 13px;
  text-align: center;
}

.query-control {
  flex: 1 1 180px;
  min-width: 150px;
}

.query-control.metric-select {
  flex: 1 1 320px;
  min-width: 240px;
}

.query-control.operator {
  flex: 0 0 120px;
  min-width: 0;
}

.query-control.value {
  flex: 1 1 140px;
  min-width: 0;
}

.query-row > .el-button {
  flex: none;
}

.query-date-picker {
  flex: 0 0 220px;
  width: 220px;
}

.query-placeholder {
  color: #c0c4cc;
  flex: none;
  padding: 0 4px;
}

.query-actions {
  display: flex;
  gap: 8px;
}

.query-result {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px;
}

.query-result-count {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}
</style>
