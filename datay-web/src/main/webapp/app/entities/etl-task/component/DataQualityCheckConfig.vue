<template>
  <el-form name="dataQualityCheckForm" label-position="top" class="data-quality-check-form">
    <el-form-item label="检核模式">
      <el-radio-group v-model="formData.checkMode">
        <el-radio value="STREAM">流式检核</el-radio>
        <el-radio value="FULL">全量检核</el-radio>
      </el-radio-group>
      <small class="form-text text-muted">
        流式：逐批检核当前 FlowFile，通过后立即下发；全量：先写入 DuckDB，上游结束后全量检核，通过后再下发。
      </small>
    </el-form-item>

    <el-form-item label="上游字段">
      <div class="table-toolbar">
        <el-button type="primary" size="small" :loading="loadingTables" @click="refreshTables(true)">调试上游获取字段</el-button>
        <span v-if="columnOptions.length" class="form-text text-muted">已获取 {{ columnOptions.length }} 个字段</span>
        <span v-else class="form-text text-muted">{{ tableError || '点击按钮调试上游组件，获取可选字段' }}</span>
      </div>
    </el-form-item>

    <el-form-item label="质量规则">
      <div class="rule-list">
        <div v-for="(rule, index) in formData.rules" :key="index" class="rule-card">
          <div class="rule-card-header">
            <span>规则 {{ index + 1 }} · {{ rule.ruleName || '未选择规则' }}</span>
            <el-button type="danger" link size="small" @click="removeRule(index)">删除</el-button>
          </div>

          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="规则">
                <el-select v-model="rule.ruleId" filterable placeholder="从规则库选择" style="width: 100%" @change="onRuleSelected(rule)">
                  <el-option v-for="item in ruleLibrary" :key="item.id" :value="item.id" :label="`${item.ruleName}（${item.ruleType}）`" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="严重级别">
                <el-select v-model="rule.severity" style="width: 100%">
                  <el-option value="ERROR" label="ERROR" />
                  <el-option value="WARN" label="WARN" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row v-if="rule.ruleType === 'COMPOSITE_UNIQUE' || rule.ruleLevel === 'FIELD'" :gutter="12">
            <el-col v-if="rule.ruleType === 'COMPOSITE_UNIQUE'" :span="12">
              <el-form-item label="组合字段">
                <el-select
                  v-model="rule.columns"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  style="width: 100%"
                  placeholder="至少选择两个字段"
                >
                  <el-option v-for="column in columnOptions" :key="column.name" :value="column.name" :label="columnLabel(column)" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-else :span="12">
              <el-form-item label="字段">
                <el-select v-model="rule.column" filterable allow-create default-first-option style="width: 100%" placeholder="请选择字段">
                  <el-option v-for="column in columnOptions" :key="column.name" :value="column.name" :label="columnLabel(column)" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="12">
            <el-col :span="24">
              <el-form-item label="阈值">
                <div class="threshold-grid">
                  <div class="threshold-item">
                    <span class="threshold-label">无效行数 ≤</span>
                    <el-input-number v-model="rule.maxInvalidRows" :min="0" :controls="false" />
                  </div>
                  <div class="threshold-item">
                    <span class="threshold-label">无效比例 ≤</span>
                    <el-input-number v-model="rule.maxInvalidRatio" :min="0" :max="1" :step="0.001" :controls="false" />
                  </div>
                </div>
              </el-form-item>
            </el-col>
          </el-row>

          <QualityRuleParams v-model="rule.config" :rule-type="rule.ruleType" />
        </div>

        <el-button type="primary" size="small" @click="addRule">添加质量规则</el-button>
      </div>
    </el-form-item>

    <el-row :gutter="12">
      <el-col :span="12">
        <el-form-item label="质量不达标失败">
          <el-switch v-model="formData.failOnError" />
          <small class="form-text text-muted">ERROR 规则不达标时终止任务</small>
        </el-form-item>
      </el-col>
    </el-row>
  </el-form>
</template>

<script setup lang="ts">
import { computed, inject, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import DataQualityRuleService, { type IDataQualityRule } from '@/entities/data-quality-rule/data-quality-rule.service';
import QualityRuleParams from '@/entities/data-quality-rule/QualityRuleParams.vue';

const props = defineProps<{ node: any }>();
const emits = defineEmits(['save']);

const designContext = inject<any>('etlTaskDesignContext', null);
const ruleService = new DataQualityRuleService();

const loadingTables = ref(false);
const tableError = ref('');
const upstreamTables = ref<any[]>([]);
const ruleLibrary = ref<IDataQualityRule[]>([]);

const formData = reactive<any>({
  checkMode: 'STREAM',
  failOnError: true,
  rules: [],
});

const resetForm = () => {
  const config = props.node?.data?.config || {};
  formData.checkMode = config.checkMode === 'FULL' ? 'FULL' : 'STREAM';
  formData.failOnError = config.failOnError !== false;
  formData.rules = (config.rules || []).map((rule: any) => {
    const matchedRule =
      ruleLibrary.value.find(item => item.id === rule.ruleId) ||
      (rule.ruleType ? ruleLibrary.value.find(item => item.ruleType === rule.ruleType && item.builtin) : undefined) ||
      (rule.ruleType ? ruleLibrary.value.find(item => item.ruleType === rule.ruleType) : undefined);
    return {
      ruleId: matchedRule?.id || rule.ruleId,
      ruleCode: rule.ruleCode || matchedRule?.ruleCode,
      ruleName: rule.ruleName || matchedRule?.ruleName,
      ruleLevel: rule.ruleLevel || 'FIELD',
      ruleType: rule.ruleType,
      column: rule.column || '',
      columns: rule.columns || [],
      config: rule.config || {},
      severity: rule.severity || 'ERROR',
      maxInvalidRows: rule.maxInvalidRows ?? 0,
      maxInvalidRatio: rule.maxInvalidRatio ?? 0,
    };
  });
};

const columnOptions = computed(() => {
  const columns = new Map<string, string>();
  upstreamTables.value.forEach(table => {
    const types = table.columnTypes || {};
    (table.columns || []).forEach((column: string) => {
      if (!columns.has(column) || (!columns.get(column) && types[column])) {
        columns.set(column, types[column] || '');
      }
    });
  });
  return [...columns.entries()].map(([name, type]) => ({ name, type }));
});

const columnLabel = (column: { name: string; type?: string }) => (column.type ? `${column.name} (${column.type})` : column.name);

const refreshTables = async (force = false) => {
  if (!designContext || typeof designContext.debugUpstreamTables !== 'function') {
    tableError.value = '当前环境无法调试上游组件';
    return;
  }
  loadingTables.value = true;
  tableError.value = '';
  try {
    const res = await designContext.debugUpstreamTables(props.node?.id, force);
    upstreamTables.value = res.tables || [];
    tableError.value = res.error || '';
  } catch (error: any) {
    tableError.value = error?.message || '获取上游字段失败';
  } finally {
    loadingTables.value = false;
  }
};

const loadRules = async () => {
  try {
    const res = await ruleService.retrieve({ page: 0, size: 2000, sort: ['builtin,desc', 'ruleName,asc'], enabled: true });
    ruleLibrary.value = res.data || [];
  } catch {
    ruleLibrary.value = [];
  }
};

const parseConfig = (value?: string) => {
  if (!value) return {};
  try {
    return JSON.parse(value);
  } catch {
    return {};
  }
};

const addRule = () => {
  formData.rules.push({
    ruleId: undefined,
    ruleCode: undefined,
    ruleName: undefined,
    ruleLevel: 'FIELD',
    ruleType: undefined,
    column: '',
    columns: [],
    config: {},
    severity: 'ERROR',
    maxInvalidRows: 0,
    maxInvalidRatio: 0,
  });
};

const removeRule = (index: number) => {
  formData.rules.splice(index, 1);
};

const onRuleSelected = (rule: any) => {
  const selected = ruleLibrary.value.find(item => item.id === rule.ruleId);
  if (!selected) return;
  rule.ruleCode = selected.ruleCode;
  rule.ruleName = selected.ruleName;
  rule.ruleLevel = selected.ruleLevel;
  rule.ruleType = selected.ruleType;
  rule.severity = selected.severity || 'ERROR';
  rule.config = parseConfig(selected.defaultConfig);
  if (rule.ruleType === 'COMPOSITE_UNIQUE') {
    rule.columns = [];
  }
};

const saveConfig = () => {
  if (formData.rules.length === 0) {
    ElMessage.error('请至少添加一条质量规则');
    return;
  }
  for (let i = 0; i < formData.rules.length; i++) {
    const rule = formData.rules[i];
    if (!rule.ruleId || !rule.ruleType) {
      ElMessage.error(`请为第 ${i + 1} 条规则选择规则库规则`);
      return;
    }
    if (rule.ruleType === 'COMPOSITE_UNIQUE') {
      if (!rule.columns || rule.columns.length < 2) {
        ElMessage.error(`第 ${i + 1} 条组合唯一规则至少选择两个字段`);
        return;
      }
    } else if (rule.ruleLevel === 'FIELD' && !rule.column) {
      ElMessage.error(`请为第 ${i + 1} 条规则选择字段`);
      return;
    }
  }
  emits('save', {
    checkMode: formData.checkMode,
    failOnError: formData.failOnError,
    failOnWarn: false,
    sampleLimit: 100,
    // 数据质量结果统一使用 CSV，不再暴露格式配置。
    outputFormat: 'CSV',
    rules: formData.rules,
  });
};

watch(
  () => props.node?.id,
  async () => {
    await loadRules();
    resetForm();
    refreshTables(false);
  },
);

onMounted(async () => {
  await loadRules();
  resetForm();
  await refreshTables(false);
});

defineExpose({ saveConfig });
</script>

<style scoped>
.data-quality-check-form {
  max-width: 100%;
  overflow-x: hidden;
}

/* Element Plus 的 gutter 会给 row 增加负边距，在弹窗内会撑出横向滚动条。 */
.data-quality-check-form :deep(.el-row) {
  margin-left: 0 !important;
  margin-right: 0 !important;
}

.data-quality-check-form :deep(.el-form-item__content) {
  min-width: 0;
}

.table-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.rule-list {
  width: 100%;
}

.rule-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px 0;
  margin-bottom: 12px;
  background: #fafcff;
}

.rule-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-weight: 600;
}

.threshold-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
  max-width: 520px;
}

.threshold-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.threshold-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
}

.threshold-item :deep(.el-input-number) {
  width: 100%;
}
</style>
