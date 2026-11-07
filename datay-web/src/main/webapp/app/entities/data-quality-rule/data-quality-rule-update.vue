<template>
  <div class="quality-rule-update-container">
    <div class="page-header">
      <div class="page-title">
        <h3>{{ isViewOnly ? '查看质量规则' : isEdit ? '编辑质量规则' : '新建质量规则' }}</h3>
        <p class="page-subtitle">预置规则只读，可复制为自定义规则后调整参数</p>
      </div>
      <div class="page-actions">
        <el-button @click="cancel">返回</el-button>
        <el-button v-if="!isViewOnly" type="primary" :loading="isSaving" :disabled="!formReady" @click="save">保存</el-button>
      </div>
    </div>

    <el-alert
      v-if="isViewOnly"
      type="info"
      :closable="false"
      class="mb-3"
      title="系统预置规则不允许直接修改，请返回列表使用“复制”创建自定义规则。"
    />

    <el-form v-if="formReady" :model="form" label-width="100px" class="quality-rule-form" @submit.prevent>
      <section class="form-section">
        <div class="section-header">
          <div>
            <h4 class="section-title">基本信息</h4>
            <p class="section-hint">规则名称、类型和严重级别用于标识规则的用途与失败策略</p>
          </div>
        </div>
        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="规则名称" required>
              <el-input v-model="form.ruleName" maxlength="255" :disabled="isViewOnly" placeholder="如：订单号非空" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="规则编码" required>
              <el-input v-model="form.ruleCode" maxlength="100" :disabled="isViewOnly" placeholder="如：ORDER_ID_NOT_NULL" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="规则类型" required>
              <el-select v-model="form.ruleType" :disabled="isViewOnly" style="width: 100%" @change="onTypeChange">
                <el-option v-for="item in ruleTypes" :key="item.code" :value="item.code" :label="typeOptionLabel(item)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="严重级别">
              <el-radio-group v-model="form.severity" :disabled="isViewOnly">
                <el-radio value="ERROR">ERROR</el-radio>
                <el-radio value="WARN">WARN</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="规则说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="2000"
            :disabled="isViewOnly"
            placeholder="说明规则用途和判定口径"
          />
        </el-form-item>

        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" :disabled="isViewOnly" />
        </el-form-item>
      </section>

      <section class="form-section">
        <div class="section-header">
          <div>
            <h4 class="section-title">默认参数</h4>
            <p class="section-hint">任务绑定规则时可以覆盖这里的默认参数</p>
          </div>
        </div>
        <QualityRuleParams :rule-type="form.ruleType" v-model="form.config" :disabled="isViewOnly" />
      </section>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import DataQualityRuleService, { type IDataQualityRule, type IQualityRuleType } from './data-quality-rule.service';
import QualityRuleParams from './QualityRuleParams.vue';

defineOptions({ name: 'DataQualityRuleEdit' });

const route = useRoute();
const router = useRouter();
const service = new DataQualityRuleService();

const isEdit = computed(() => Boolean(route.params.id));
const isViewOnly = ref(false);
const formReady = ref(!isEdit.value);
const isSaving = ref(false);
const ruleTypes = ref<IQualityRuleType[]>([]);

/** 自定义 SQL 不展示“表级/字段级”后缀，避免与字段级规则概念混淆。 */
const typeOptionLabel = (item: IQualityRuleType) =>
  item.code === 'CUSTOM_SQL' ? item.label : `${item.label}（${item.level === 'TABLE' ? '表级' : '字段级'}）`;

const form = reactive<IDataQualityRule & { config: Record<string, any> }>({
  id: undefined,
  ruleName: '',
  ruleCode: '',
  ruleLevel: 'FIELD',
  ruleType: 'NOT_NULL',
  description: '',
  defaultConfig: '{}',
  severity: 'ERROR',
  enabled: true,
  config: {},
});

const parseConfig = (value?: string) => {
  if (!value) return {};
  try {
    return JSON.parse(value);
  } catch {
    return {};
  }
};

const onTypeChange = () => {
  const type = ruleTypes.value.find(item => item.code === form.ruleType);
  if (type) {
    form.ruleLevel = type.level;
    form.description = form.description || type.description;
  }
};

const loadTypes = async () => {
  ruleTypes.value = await service.getTypes();
};

const loadRule = async (id: string) => {
  const rule = await service.find(id);
  Object.assign(form, rule);
  form.config = parseConfig(rule.defaultConfig);
  isViewOnly.value = Boolean(rule.builtin);
};

const loadCopy = async (id: string) => {
  const rule = await service.find(id);
  Object.assign(form, rule);
  form.id = undefined;
  form.ruleName = `${rule.ruleName || ''} - 副本`;
  form.ruleCode = `${rule.ruleCode || 'DQ'}_COPY_${Date.now()}`;
  form.builtin = false;
  form.config = parseConfig(rule.defaultConfig);
};

const save = async () => {
  if (!form.ruleName?.trim()) {
    ElMessage.error('请输入规则名称');
    return;
  }
  if (!form.ruleCode?.trim()) {
    ElMessage.error('请输入规则编码');
    return;
  }
  if (!form.ruleType) {
    ElMessage.error('请选择规则类型');
    return;
  }
  isSaving.value = true;
  try {
    const payload = { ...form, defaultConfig: JSON.stringify(form.config || {}) };
    delete (payload as any).config;
    if (form.id) {
      await service.update(payload);
    } else {
      await service.create(payload);
    }
    ElMessage.success('保存成功');
    router.push({ name: 'DataQualityRule' });
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.detail || e?.message || '保存失败');
  } finally {
    isSaving.value = false;
  }
};

const cancel = () => router.push({ name: 'DataQualityRule' });

onMounted(async () => {
  await loadTypes();
  if (route.query.copyFrom) {
    await loadCopy(String(route.query.copyFrom));
  } else if (route.params.id) {
    await loadRule(String(route.params.id));
  }
  formReady.value = true;
});
</script>

<style scoped>
.quality-rule-update-container {
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

.quality-rule-form :deep(.el-form-item) {
  margin-bottom: 18px;
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
