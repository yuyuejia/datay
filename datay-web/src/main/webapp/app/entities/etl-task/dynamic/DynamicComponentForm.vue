<template>
  <el-form label-position="top" class="dynamic-component-form" @submit.prevent>
    <template v-for="group in groups" :key="group.name">
      <el-collapse v-if="group.advanced" class="dynamic-group-collapse">
        <el-collapse-item :title="group.name" :name="group.name">
          <template v-for="field in group.fields" :key="field.key">
            <el-form-item v-if="isVisible(field)" :label="field.label" :required="field.required">
              <DynamicField
                :field="field"
                :model-value="values[field.key]"
                :form-values="values"
                :component="component"
                @change="payload => handleFieldChange(field, payload)"
              />
              <small v-if="field.help" class="form-text text-muted">{{ field.help }}</small>
            </el-form-item>
          </template>
        </el-collapse-item>
      </el-collapse>
      <template v-else>
        <div v-if="group.name !== '基础配置'" class="dynamic-group-title">{{ group.name }}</div>
        <template v-for="field in group.fields" :key="field.key">
          <el-form-item v-if="isVisible(field)" :label="field.label" :required="field.required">
            <DynamicField
              :field="field"
              :model-value="values[field.key]"
              :form-values="values"
              :component="component"
              @change="payload => handleFieldChange(field, payload)"
            />
            <small v-if="field.help" class="form-text text-muted">{{ field.help }}</small>
          </el-form-item>
        </template>
      </template>
    </template>
  </el-form>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue';
import { ElMessage } from 'element-plus';

import type { IETLComponentField } from '@/shared/model/etl-component.model';

import DynamicField from './DynamicField.vue';

const props = defineProps<{
  node?: any;
  fields?: IETLComponentField[];
  component?: any;
}>();

const emits = defineEmits(['save']);

interface FieldChangePayload {
  value: any;
  extra?: Record<string, any>;
}

const values = reactive<Record<string, any>>({});

const normalizedFields = computed<IETLComponentField[]>(() =>
  [...(props.fields || [])].sort((left, right) => Number(left.order ?? 0) - Number(right.order ?? 0)),
);

const groups = computed(() => {
  const map = new Map<string, IETLComponentField[]>();
  for (const field of normalizedFields.value) {
    const groupName = field.group || '基础配置';
    if (!map.has(groupName)) {
      map.set(groupName, []);
    }
    map.get(groupName)!.push(field);
  }
  return Array.from(map, ([name, groupFields]) => ({
    name,
    fields: groupFields,
    advanced: name.includes('高级'),
  }));
});

const defaultFor = (field: IETLComponentField): any => {
  const type = (field.type || 'INPUT').toUpperCase();
  if (type === 'SWITCH') {
    return field.defaultValue === 'true';
  }
  if (type === 'NUMBER') {
    return field.defaultValue === '' || field.defaultValue === undefined || field.defaultValue === null ? null : Number(field.defaultValue);
  }
  if (['MULTI_SELECT', 'CHECKBOX_GROUP'].includes(type)) {
    return [];
  }
  if (type === 'TABLE' || type === 'COLUMN') {
    return field.multiple ? [] : '';
  }
  return field.defaultValue ?? '';
};

const parseValue = (field: IETLComponentField, raw: any): any => {
  const type = (field.type || 'INPUT').toUpperCase();
  if (raw === undefined || raw === null) {
    return defaultFor(field);
  }
  if (type === 'SWITCH') {
    return raw === true || raw === 'true';
  }
  if (type === 'NUMBER') {
    if (raw === '') {
      return null;
    }
    const numberValue = Number(raw);
    return Number.isFinite(numberValue) ? numberValue : null;
  }
  if (type === 'MULTI_SELECT' || type === 'CHECKBOX_GROUP') {
    if (Array.isArray(raw)) {
      return raw;
    }
    if (typeof raw === 'string' && raw.trim()) {
      try {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) {
          return parsed;
        }
      } catch {
        // 非 JSON，按逗号分隔处理
      }
      return raw
        .split(',')
        .map(item => item.trim())
        .filter(Boolean);
    }
    return [];
  }
  if ((type === 'TABLE' || type === 'COLUMN') && field.multiple) {
    if (Array.isArray(raw)) {
      return raw;
    }
    if (typeof raw === 'string' && raw.trim()) {
      return raw
        .split(',')
        .map(item => item.trim())
        .filter(Boolean);
    }
    return [];
  }
  return raw;
};

const initializeValues = () => {
  const config = props.node?.data?.config || {};
  for (const field of props.fields || []) {
    values[field.key] = parseValue(field, config[field.key]);
  }
  // 保留数据源选择器回写的 schema 等辅助配置项
  if (config.schema !== undefined && values.schema === undefined) {
    values.schema = config.schema;
  }
};

initializeValues();

const isVisible = (field: IETLComponentField): boolean => {
  if (!field.dependsOn) {
    return true;
  }
  const expected = field.visibleWhen || [];
  if (expected.length === 0) {
    return true;
  }
  const current = values[field.dependsOn];
  const currentValues = Array.isArray(current) ? current.map(item => String(item)) : [String(current ?? '')];
  return expected.some(item => currentValues.includes(String(item)));
};

const clearDependentFields = (types: string[]) => {
  for (const field of props.fields || []) {
    if (types.includes((field.type || '').toUpperCase())) {
      values[field.key] = defaultFor(field);
    }
  }
};

const handleFieldChange = (field: IETLComponentField, payload: FieldChangePayload) => {
  const type = (field.type || 'INPUT').toUpperCase();
  const previous = values[field.key];
  const extraChanged = payload.extra ? Object.entries(payload.extra).some(([key, value]) => values[key] !== value) : false;
  values[field.key] = payload.value;

  if (payload.extra) {
    Object.assign(values, payload.extra);
  }

  if (previous === payload.value && !extraChanged) {
    return;
  }

  if (type === 'DATASOURCE') {
    clearDependentFields(['TABLE', 'COLUMN']);
  } else if (type === 'SCHEMA') {
    clearDependentFields(['TABLE', 'COLUMN']);
  } else if (type === 'TABLE') {
    clearDependentFields(['COLUMN']);
  }
};

const isEmptyValue = (value: any): boolean => {
  if (value === undefined || value === null) {
    return true;
  }
  if (typeof value === 'string') {
    return value.trim().length === 0;
  }
  if (Array.isArray(value)) {
    return value.length === 0;
  }
  return false;
};

const validate = (): boolean => {
  for (const field of props.fields || []) {
    if (!isVisible(field)) {
      continue;
    }
    const value = values[field.key];
    if (field.required && isEmptyValue(value)) {
      ElMessage.error(`请填写${field.label || field.key}`);
      return false;
    }
    if (isEmptyValue(value)) {
      continue;
    }
    if (field.pattern) {
      const regex = new RegExp(field.pattern);
      if (!regex.test(String(value))) {
        ElMessage.error(field.patternMessage || `${field.label || field.key}格式不正确`);
        return false;
      }
    }
    const type = (field.type || 'INPUT').toUpperCase();
    if (type === 'NUMBER') {
      const numberValue = Number(value);
      if (!Number.isFinite(numberValue)) {
        ElMessage.error(`${field.label || field.key}必须是数字`);
        return false;
      }
      if (field.min !== undefined && field.min !== null && numberValue < Number(field.min)) {
        ElMessage.error(`${field.label || field.key}不能小于 ${field.min}`);
        return false;
      }
      if (field.max !== undefined && field.max !== null && numberValue > Number(field.max)) {
        ElMessage.error(`${field.label || field.key}不能大于 ${field.max}`);
        return false;
      }
    }
  }
  return true;
};

const serializeValue = (field: IETLComponentField, value: any): any => {
  const type = (field.type || 'INPUT').toUpperCase();
  const format = (field.valueFormat || 'AUTO').toUpperCase();
  if (type === 'MULTI_SELECT' || type === 'CHECKBOX_GROUP' || ((type === 'TABLE' || type === 'COLUMN') && field.multiple)) {
    const arrayValue = Array.isArray(value) ? value : [];
    if (format === 'COMMA_STRING' || format === 'STRING') {
      return arrayValue.join(',');
    }
    return arrayValue;
  }
  if (type === 'SWITCH') {
    return value === true;
  }
  if (type === 'NUMBER') {
    if (value === '' || value === undefined || value === null) {
      return null;
    }
    const numberValue = Number(value);
    return Number.isFinite(numberValue) ? numberValue : null;
  }
  return value;
};

const saveConfig = async () => {
  if (!validate()) {
    return;
  }
  const config: Record<string, any> = { ...(props.node?.data?.config || {}) };
  for (const field of props.fields || []) {
    config[field.key] = serializeValue(field, values[field.key]);
  }
  // 辅助字段（例如数据源选择器回写的 schema）也一并保存
  for (const [key, value] of Object.entries(values)) {
    if (!(props.fields || []).some(field => field.key === key)) {
      config[key] = value;
    }
  }
  emits('save', config);
};

defineExpose({ saveConfig, validate });
</script>

<style scoped>
.dynamic-component-form {
  width: 100%;
}

.dynamic-group-title {
  margin: 8px 0 16px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  color: var(--el-text-color-primary, #303133);
  font-size: 14px;
  font-weight: 600;
}

.dynamic-group-collapse {
  margin-top: 8px;
}

.form-text {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
  line-height: 1.4;
}
</style>
