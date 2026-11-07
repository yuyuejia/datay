<template>
  <div class="quality-task-update-container">
    <div class="page-header">
      <div class="page-title">
        <h3>{{ isEdit ? '编辑数据质量检核任务' : '新建数据质量检核任务' }}</h3>
        <p class="page-subtitle">选择单张表并配置质量规则，执行时生成 ETL 任务并将结果写入文件存储</p>
      </div>
      <div class="page-actions">
        <el-button @click="cancel">取消</el-button>
        <el-button type="primary" :loading="isSaving" :disabled="!formReady" @click="save">保存</el-button>
      </div>
    </div>

    <el-form v-if="formReady" :model="form" label-width="100px" class="quality-task-form" @submit.prevent>
      <section class="form-section">
        <div class="section-header">
          <h4 class="section-title">基本信息</h4>
          <p class="section-hint">任务名称和描述用于标识本次数据质量检核</p>
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
      </section>

      <section class="form-section">
        <div class="section-header">
          <h4 class="section-title">检核对象</h4>
          <p class="section-hint">当前版本一个任务检核一张表</p>
        </div>
        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="数据源" required>
              <DataSourceSelector :datasource-id="form.dataSourceId" :schema="form.schemaName" @selected="handleDataSourceSelected" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="数据表" required>
              <DataSourceTableSelector
                :data-source-id="form.dataSourceId"
                :schema="form.schemaName"
                :selected-tables="[]"
                :selected-table="form.tableName"
                :multiple="false"
                @selected="handleTableSelected"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="过滤条件">
          <el-input v-model="form.whereClause" type="textarea" :rows="2" placeholder="可选，例如 created_at >= '2026-01-01'" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="WARN 也失败">
              <el-switch v-model="form.failOnWarn" />
              <span class="form-tip">开启后 WARN 规则不达标也会使任务失败</span>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="抽样行数">
              <el-input-number v-model="form.sampleLimit" :min="0" :max="10000" :controls="false" />
              <span class="form-tip">失败规则最多抽样多少行明细</span>
            </el-form-item>
          </el-col>
        </el-row>
      </section>

      <section class="form-section">
        <div class="section-header">
          <h4 class="section-title">质量规则</h4>
          <p class="section-hint">
            从规则库选择规则并绑定字段与阈值
            <span v-if="form.rules.length" class="selected-count">已选 {{ form.rules.length }} 条</span>
          </p>
          <el-button type="primary" size="small" @click="addRule">添加规则</el-button>
        </div>

        <div v-if="form.rules.length === 0" class="empty-rules">尚未配置质量规则</div>

        <div v-for="(rule, index) in form.rules" :key="index" class="rule-card">
          <div class="rule-card-header">
            <span class="rule-card-title">规则 {{ index + 1 }} · {{ rule.ruleName || '未选择规则' }}</span>
            <el-button type="danger" link size="small" @click="removeRule(index)">删除</el-button>
          </div>

          <el-row :gutter="16">
            <el-col :xs="24" :md="12">
              <el-form-item label="规则">
                <el-select v-model="rule.ruleId" filterable placeholder="从规则库选择" style="width: 100%" @change="onRuleSelected(rule)">
                  <el-option v-for="item in ruleLibrary" :key="item.id" :value="item.id" :label="`${item.ruleName}（${item.ruleType}）`" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="严重级别">
                <el-select v-model="rule.severity" style="width: 100%">
                  <el-option value="ERROR" label="ERROR（不达标任务失败）" />
                  <el-option value="WARN" label="WARN（仅告警）" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col v-if="rule.ruleType === 'COMPOSITE_UNIQUE'" :xs="24" :md="12">
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
                  <el-option v-for="column in columnOptions" :key="column" :value="column" :label="column" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-else-if="rule.ruleLevel === 'FIELD'" :xs="24" :md="12">
              <el-form-item label="字段">
                <el-select v-model="rule.column" filterable allow-create default-first-option style="width: 100%" placeholder="请选择字段">
                  <el-option v-for="column in columnOptions" :key="column" :value="column" :label="column" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="阈值">
                <div class="threshold-row">
                  <span>无效行数 ≤</span>
                  <el-input-number v-model="rule.maxInvalidRows" :min="0" :controls="false" />
                  <span>比例 ≤</span>
                  <el-input-number v-model="rule.maxInvalidRatio" :min="0" :max="1" :step="0.001" :controls="false" />
                </div>
              </el-form-item>
            </el-col>
          </el-row>

          <QualityRuleParams v-model="rule.config" :rule-type="rule.ruleType" />
        </div>
      </section>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import DataQualityTaskService, { type IDataQualityRuleBinding, type IDataQualityTask } from './data-quality-task.service';
import DataQualityRuleService, { type IDataQualityRule } from '@/entities/data-quality-rule/data-quality-rule.service';
import QualityRuleParams from '@/entities/data-quality-rule/QualityRuleParams.vue';
import DataSourceSelector from '@/components/DataSourceSelector.vue';
import DataSourceTableSelector from '@/components/DataSourceTableSelector.vue';
import DataSourceService from '@/entities/data-source/data-source.service';

defineOptions({ name: 'DataQualityTaskEdit' });

const route = useRoute();
const router = useRouter();
const service = new DataQualityTaskService();
const ruleService = new DataQualityRuleService();
const dataSourceService = new DataSourceService();

const isEdit = ref(Boolean(route.params.id));
const formReady = ref(!isEdit.value);
const isSaving = ref(false);
const ruleLibrary = ref<IDataQualityRule[]>([]);
const columnOptions = ref<string[]>([]);

const form = reactive<IDataQualityTask>({
  id: undefined,
  taskName: '',
  taskDesc: '',
  dataSourceId: '',
  schemaName: '',
  tableName: '',
  whereClause: '',
  failOnWarn: false,
  sampleLimit: 100,
  outputFormat: 'CSV',
  outputDir: 'data-quality',
  rules: [],
});

const parseConfig = (value?: string) => {
  if (!value) return {};
  try {
    return JSON.parse(value);
  } catch {
    return {};
  }
};

const loadRuleLibrary = async () => {
  const res = await ruleService.retrieve({ page: 0, size: 2000, sort: ['builtin,desc', 'ruleName,asc'], enabled: true });
  ruleLibrary.value = res.data || [];
};

const loadColumns = async () => {
  if (!form.dataSourceId || !form.schemaName || !form.tableName) {
    columnOptions.value = [];
    return;
  }
  try {
    const res = await dataSourceService.getFields(form.dataSourceId, form.schemaName, form.tableName);
    columnOptions.value = (res.data || []).filter((field: any) => field?.name).map((field: any) => String(field.name));
  } catch {
    columnOptions.value = [];
    ElMessage.warning('获取表字段失败，可手动输入字段名');
  }
};

const handleDataSourceSelected = async (selection: any) => {
  form.dataSourceId = selection?.dataSourceId || '';
  form.schemaName = selection?.schema || '';
  form.tableName = '';
  columnOptions.value = [];
};

const handleTableSelected = async (selection: any) => {
  form.tableName = selection?.table || '';
  await loadColumns();
};

const newBinding = (): IDataQualityRuleBinding => ({
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

const addRule = () => {
  (form.rules as IDataQualityRuleBinding[]).push(newBinding());
};

const removeRule = (index: number) => {
  form.rules?.splice(index, 1);
};

const onRuleSelected = (rule: IDataQualityRuleBinding) => {
  const selected = ruleLibrary.value.find(item => item.id === rule.ruleId);
  if (!selected) return;
  rule.ruleCode = selected.ruleCode;
  rule.ruleName = selected.ruleName;
  rule.ruleLevel = selected.ruleLevel;
  rule.ruleType = selected.ruleType;
  rule.severity = selected.severity || 'ERROR';
  rule.config = parseConfig(selected.defaultConfig);
  if (selected.ruleType === 'COMPOSITE_UNIQUE') {
    rule.columns = [];
  } else {
    rule.column = rule.column || '';
  }
};

const validate = (): string | null => {
  if (!form.taskName?.trim()) return '请填写任务名称';
  if (!form.dataSourceId) return '请选择数据源';
  if (!form.schemaName) return '请选择 Schema';
  if (!form.tableName) return '请选择数据表';
  if (!form.rules || form.rules.length === 0) return '请至少添加一条质量规则';
  for (let i = 0; i < form.rules.length; i++) {
    const rule = form.rules[i];
    if (!rule.ruleId || !rule.ruleType) return `请为第 ${i + 1} 条规则选择规则库规则`;
    if (rule.ruleType === 'COMPOSITE_UNIQUE') {
      if (!rule.columns || rule.columns.length < 2) return `第 ${i + 1} 条组合唯一规则至少选择两个字段`;
    } else if (rule.ruleLevel === 'FIELD' && !rule.column) {
      return `请为第 ${i + 1} 条规则选择字段`;
    }
  }
  return null;
};

const save = async () => {
  const error = validate();
  if (error) {
    ElMessage.error(error);
    return;
  }
  isSaving.value = true;
  try {
    const payload = {
      ...JSON.parse(JSON.stringify(form)),
      // 结果格式和输出目录固定使用系统默认值，页面不再暴露配置。
      outputFormat: 'CSV',
      outputDir: 'data-quality',
    };
    if (form.id) {
      await service.update(payload);
    } else {
      await service.create(payload);
    }
    ElMessage.success('保存成功');
    router.push({ name: 'DataQualityTask' });
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.detail || e?.message || '保存失败');
  } finally {
    isSaving.value = false;
  }
};

const cancel = () => router.push({ name: 'DataQualityTask' });

onMounted(async () => {
  await loadRuleLibrary();
  if (route.params.id) {
    const task = await service.find(String(route.params.id));
    Object.assign(form, task);
    form.rules = (task.rules || []).map(rule => {
      const matchedRule =
        ruleLibrary.value.find(item => item.id === rule.ruleId) ||
        (rule.ruleType ? ruleLibrary.value.find(item => item.ruleType === rule.ruleType && item.builtin) : undefined) ||
        (rule.ruleType ? ruleLibrary.value.find(item => item.ruleType === rule.ruleType) : undefined);
      return {
        ...rule,
        ruleId: matchedRule?.id || rule.ruleId,
        ruleCode: rule.ruleCode || matchedRule?.ruleCode,
        ruleName: rule.ruleName || matchedRule?.ruleName,
        columns: rule.columns || [],
        config: rule.config || {},
      };
    });
  }
  formReady.value = true;
  await loadColumns();
});
</script>

<style scoped>
.quality-task-update-container {
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

.section-hint,
.form-tip {
  margin: 4px 0 0;
  color: #909399;
  font-size: 13px;
}

.form-tip {
  font-size: 12px;
  line-height: 1.5;
}

.selected-count {
  margin-left: 8px;
  color: #409eff;
}

.quality-task-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.empty-rules {
  padding: 28px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  margin-bottom: 16px;
}

.rule-card {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 14px 16px 0;
  margin-bottom: 14px;
  background: #fafcff;
}

.rule-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.rule-card-title {
  font-weight: 600;
}

.threshold-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: var(--el-text-color-regular);
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
}
</style>
