<template>
  <div class="metric-update-container">
    <div class="page-header">
      <h3>{{ isEdit ? "编辑指标" : "新建指标" }}</h3>
      <div>
        <el-button @click="previousState()">取消</el-button>
        <el-button @click="previewSql()">预览 SQL</el-button>
        <el-button type="primary" @click="save()" :loading="isSaving"
          >保存</el-button
        >
      </div>
    </div>

    <el-form :model="metric" label-width="100px" class="metric-form">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="指标名称" required>
            <b-form-input v-model="metric.name" placeholder="例如：销售额" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="指标编码" required>
            <b-form-input
              v-model="metric.code"
              placeholder="唯一标识，如 sales_amount"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="指标类型" required>
            <select
              class="form-control"
              v-model="metric.metricType"
              style="width: 100%"
            >
              <option
                v-for="opt in metricTypeOptions"
                :key="opt.value"
                :value="opt.value"
              >
                {{ opt.label }}
              </option>
            </select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="状态" required>
            <select
              class="form-control"
              v-model="metric.status"
              style="width: 100%"
            >
              <option
                v-for="opt in statusOptions"
                :key="opt.value"
                :value="opt.value"
              >
                {{ opt.label }}
              </option>
            </select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="所属目录">
            <select
              class="form-control"
              v-model="metric.directoryId"
              style="width: 100%"
            >
              <option :value="null">全部指标</option>
              <option
                v-for="dir in flatDirectoryOptions"
                :key="dir.id"
                :value="dir.id"
              >
                {{ "\u00A0\u00A0".repeat(dir.level) + dir.name }}
              </option>
            </select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="描述">
        <textarea
          class="form-control"
          v-model="metric.description"
          :rows="2"
          placeholder="业务含义说明（选填）"
        ></textarea>
      </el-form-item>

      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="单位">
            <b-form-input v-model="metric.unit" placeholder="如：元 / 件" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="数据类型" required>
            <select
              class="form-control"
              v-model="metric.dataType"
              style="width: 100%"
            >
              <option :value="null" disabled>请选择数据类型</option>
              <option v-for="dt in dataTypes" :key="dt.value" :value="dt.value">
                {{ dt.label }}
              </option>
            </select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="是否可累加">
            <select
              class="form-control"
              v-model="metric.isAdditive"
              style="width: 100%"
            >
              <option :value="true">可累加</option>
              <option :value="false">不可累加</option>
            </select>
          </el-form-item>
        </el-col>
      </el-row>

      <div v-if="metric.metricType === METRIC_TYPE_ATOMIC" class="type-block">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="事实表" required>
              <select
                class="form-control"
                v-model="metric.factModelId"
                style="width: 100%"
                @change="onFactModelChange"
              >
                <option :value="null" disabled>请选择事实表（DWD）</option>
                <option
                  v-for="model in factModels"
                  :key="model.id"
                  :value="model.id"
                >
                  {{ model.name }}
                </option>
              </select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="计算公式" required>
          <textarea
            class="form-control code-area"
            v-model="formula"
            :rows="3"
            placeholder="SQL 聚合表达式，例如：SUM(amount * quantity)"
          ></textarea>
          <div class="ref-insert">
            <select
              class="form-control"
              v-model="selectedFieldCode"
              :disabled="!metric.factModelId"
              style="width: 280px"
            >
              <option value="" disabled>
                {{ loadingFields ? "加载中..." : "选择要插入的事实表字段" }}
              </option>
              <option
                v-for="field in factFields"
                :key="field.fieldName"
                :value="field.fieldName"
              >
                {{ field.fieldName }} ({{ field.fieldType }})
              </option>
            </select>
            <el-button
              type="primary"
              size="small"
              @click="insertFieldRef(selectedFieldCode)"
              >插入</el-button
            >
          </div>
          <div class="agg-functions">
            <span class="agg-label">常用聚合函数：</span>
            <el-button
              v-for="fn in aggFunctions"
              :key="fn.value"
              size="small"
              @click="insertAggFunction(fn)"
              >{{ fn.label }}</el-button
            >
          </div>
          <div class="filter-hint">
            直接使用字段名作为 SQL 片段参与计算，示列：
            <code>SUM(amount * quantity)</code>、<code>COUNT(*)</code>。
          </div>
        </el-form-item>

        <div class="filter-section">
          <div class="filter-header">
            <h5>业务限定</h5>
            <el-button type="primary" size="small" @click="addFilterCondition">
              <font-awesome-icon icon="plus" /> 添加条件
            </el-button>
          </div>
          <div
            v-for="(condition, index) in filterConditions"
            :key="index"
            class="filter-row"
          >
            <span v-if="index > 0" class="filter-logic">
              <select v-model="condition.logic">
                <option value="AND">AND</option>
                <option value="OR">OR</option>
              </select>
            </span>
            <span v-else class="filter-logic placeholder">条件</span>

            <select
              class="filter-control"
              v-model="condition.type"
              @change="onFilterTypeChange(condition)"
            >
              <option
                v-for="opt in filterTypeOptions"
                :key="opt.value"
                :value="opt.value"
              >
                {{ opt.label }}
              </option>
            </select>

            <template v-if="condition.type === FILTER_TYPE_FACT_FIELD">
              <select class="filter-control" v-model="condition.factFieldName">
                <option :value="null" disabled>选择字段</option>
                <option
                  v-for="field in factFields"
                  :key="field.fieldName"
                  :value="field.fieldName"
                >
                  {{ field.fieldName }}
                </option>
              </select>
            </template>
            <template v-else-if="condition.type === FILTER_TYPE_DIMENSION">
              <select
                class="filter-control"
                v-model="condition.factFieldName"
                @change="onFilterFactFieldChange(condition)"
              >
                <option :value="null" disabled>关联维度字段</option>
                <option
                  v-for="field in dimensionFactFields"
                  :key="field.fieldName"
                  :value="field.fieldName"
                >
                  {{ field.fieldName }}
                </option>
              </select>
              <select
                class="filter-control"
                v-model="condition.dimensionFieldName"
                :disabled="!condition.factFieldName"
              >
                <option :value="null" disabled>维度字段</option>
                <option
                  v-for="field in condition._dimensionFields || []"
                  :key="field.fieldName"
                  :value="field.fieldName"
                >
                  {{ field.fieldName }}
                </option>
              </select>
            </template>
            <template v-else>
              <select class="filter-control" v-model="condition.factFieldName">
                <option :value="null" disabled>选择日期字段</option>
                <option
                  v-for="field in dateFactFields"
                  :key="field.fieldName"
                  :value="field.fieldName"
                >
                  {{ field.fieldName }}
                </option>
              </select>
            </template>

            <select
              class="filter-control operator"
              v-model="condition.operator"
            >
              <option
                v-for="op in filterOperators"
                :key="op.value"
                :value="op.value"
              >
                {{ op.label }}
              </option>
            </select>

            <template v-if="isUnaryOperator(condition.operator)">
              <span class="filter-value-placeholder">-</span>
            </template>
            <template v-else-if="isRangeOperator(condition.operator)">
              <input
                class="filter-control value"
                v-model="condition.value"
                placeholder="开始值"
              />
              <input
                class="filter-control value"
                v-model="condition.valueEnd"
                placeholder="结束值"
              />
            </template>
            <template v-else>
              <input
                class="filter-control value"
                v-model="condition.value"
                :placeholder="
                  condition.operator === 'IN' || condition.operator === 'NOT_IN'
                    ? '多个值用英文逗号分隔'
                    : '值'
                "
              />
            </template>

            <el-button
              link
              type="danger"
              size="small"
              @click="removeFilterCondition(index)"
            >
              <font-awesome-icon icon="trash" />
            </el-button>
          </div>
        </div>
      </div>

      <div
        v-else-if="metric.metricType === METRIC_TYPE_DERIVED"
        class="type-block"
      >
        <el-form-item label="计算公式" required>
          <textarea
            class="form-control code-area"
            v-model="formula"
            :rows="3"
            placeholder="例如：${sales_amount} / ${sales_quantity}"
          ></textarea>
        </el-form-item>
        <el-form-item label="插入指标">
          <div class="ref-insert">
            <select
              class="form-control"
              v-model="selectedRefCode"
              style="width: 280px"
            >
              <option value="" disabled>选择要引用的指标</option>
              <option
                v-for="item in insertableMetrics"
                :key="item.id"
                :value="item.code"
              >
                {{ item.name }} ({{ item.code }})
              </option>
            </select>
            <el-button
              type="primary"
              size="small"
              @click="insertMetricRef(selectedRefCode)"
              >插入</el-button
            >
          </div>
          <div class="filter-hint">
            <font-awesome-icon
              icon="exclamation-circle"
              class="mr-1"
            ></font-awesome-icon>
            公式仅支持数字、运算符 <code>+ - * / ( )</code> 以及
            <code>$&#123;指标编码&#125;</code>
            引用；可引用原子指标或其它衍生指标。
          </div>
        </el-form-item>
        <el-form-item v-if="formulaRefs.length > 0" label="已引用">
          <div class="ref-tags">
            <el-tag
              v-for="ref in formulaRefs"
              :key="ref"
              size="small"
              type="info"
              >{{ ref }}</el-tag
            >
          </div>
        </el-form-item>
      </div>
    </el-form>

    <b-modal v-model="sqlDialogVisible" title="计算 SQL 预览" size="lg">
      <div class="modal-body">
        <pre class="sql-code">{{ sqlContent }}</pre>
      </div>
      <template #modal-footer>
        <div>
          <button
            type="button"
            class="btn btn-secondary"
            @click="sqlDialogVisible = false"
          >
            关闭
          </button>
        </div>
      </template>
    </b-modal>
  </div>
</template>

<script lang="ts" src="./metric-update.component.ts"></script>

<style scoped>
.metric-update-container {
  padding: 20px;
  background: #fff;
  border-radius: 4px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.page-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.type-block {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 16px 16px 4px;
  margin-bottom: 8px;
}

.filter-section {
  margin: 4px 0 16px;
  padding: 12px 16px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fff;
}

.filter-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.filter-header h5 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.filter-empty {
  color: #909399;
  font-size: 13px;
  padding: 8px 0;
}

.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.filter-logic {
  width: 60px;
  flex-shrink: 0;
}

.filter-logic select {
  width: 100%;
}

.filter-logic.placeholder {
  color: #909399;
  font-size: 13px;
}

.filter-control {
  height: 32px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 0 8px;
  font-size: 13px;
  min-width: 140px;
}

.filter-control.operator {
  min-width: 130px;
}

.filter-control.value {
  min-width: 160px;
}

.filter-value-placeholder {
  color: #c0c4cc;
}

.code-area {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
}

.ref-insert {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agg-functions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.agg-label {
  color: #606266;
  font-size: 13px;
}

.filter-hint {
  margin-top: 8px;
  padding: 10px 12px;
  background: #ecf5ff;
  border-radius: 4px;
  color: #606266;
  font-size: 13px;
  line-height: 22px;
}

.ref-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
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
</style>
