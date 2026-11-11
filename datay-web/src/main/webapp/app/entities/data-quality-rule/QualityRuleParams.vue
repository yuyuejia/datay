<template>
  <div class="quality-rule-params">
    <template v-if="ruleType === 'ROW_COUNT_MIN'">
      <el-form-item label="最小行数">
        <el-input-number v-model="config.minRows" :min="0" :controls="false" />
      </el-form-item>
    </template>

    <template v-else-if="ruleType === 'ROW_COUNT_RANGE'">
      <el-form-item label="最小行数">
        <el-input-number v-model="config.minRows" :min="0" :controls="false" />
      </el-form-item>
      <el-form-item label="最大行数">
        <el-input-number v-model="config.maxRows" :min="0" :controls="false" />
      </el-form-item>
    </template>

    <template v-else-if="ruleType === 'NOT_NULL'">
      <el-form-item label="空值处理">
        <el-checkbox v-model="config.trimWhitespace">空白字符串视为空值</el-checkbox>
      </el-form-item>
    </template>

    <template v-else-if="ruleType === 'RANGE'">
      <el-form-item label="值类型">
        <el-select v-model="config.valueType">
          <el-option value="NUMBER" label="数值" />
          <el-option value="DATE" label="日期 / 时间" />
        </el-select>
      </el-form-item>
      <el-form-item label="最小值">
        <el-input v-model="config.min" placeholder="可空" />
      </el-form-item>
      <el-form-item label="最大值">
        <el-input v-model="config.max" placeholder="可空" />
      </el-form-item>
    </template>

    <template v-else-if="ruleType === 'ENUM'">
      <el-form-item label="允许值">
        <el-input
          :model-value="allowedValuesText"
          type="textarea"
          :rows="2"
          placeholder="多个值用英文逗号或换行分隔，如：ACTIVE,INACTIVE"
          @update:model-value="updateAllowedValues"
        />
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="config.ignoreCase">忽略大小写</el-checkbox>
      </el-form-item>
    </template>

    <template v-else-if="ruleType === 'REGEX'">
      <el-form-item label="正则表达式">
        <el-input v-model="config.pattern" placeholder="例如：^[A-Za-z0-9_-]+$" />
      </el-form-item>
    </template>

    <template v-else-if="ruleType === 'LENGTH'">
      <el-form-item label="最小长度">
        <el-input-number v-model="config.minLength" :min="0" :controls="false" />
      </el-form-item>
      <el-form-item label="最大长度">
        <el-input-number v-model="config.maxLength" :min="0" :controls="false" />
      </el-form-item>
    </template>

    <template v-else-if="ruleType === 'FRESHNESS'">
      <el-form-item label="最大时效（小时）">
        <el-input-number v-model="config.maxAgeHours" :min="1" :controls="false" />
      </el-form-item>
    </template>

    <template v-else-if="ruleType === 'CUSTOM_SQL'">
      <el-form-item label="自定义 SQL">
        <el-input
          v-model="config.sql"
          type="textarea"
          :rows="4"
          placeholder="单条 SELECT，需返回 invalid_rows 列。可直接使用当前检核的源表名和字段。"
        />
      </el-form-item>
      <div class="params-hint">示例（检核表为 orders 时）：SELECT COUNT(*) AS invalid_rows FROM orders WHERE amount &lt; 0</div>
    </template>

    <template v-else-if="ruleType === 'COMPOSITE_UNIQUE'">
      <div class="params-hint">组合字段在任务规则中配置，规则库只保存规则定义。</div>
    </template>

    <template v-else>
      <div class="params-hint">该规则类型无需额外参数。</div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  ruleType?: string;
  modelValue: Record<string, any>;
}>();

const config = computed(() => props.modelValue || {});

const allowedValuesText = computed(() => {
  const values = config.value.allowedValues;
  if (Array.isArray(values)) {
    return values.join(',');
  }
  return values || '';
});

const updateAllowedValues = (value: string) => {
  config.value.allowedValues = String(value || '')
    .split(/[,，\n]/)
    .map(item => item.trim())
    .filter(Boolean);
};
</script>

<style scoped>
.quality-rule-params {
  width: 100%;
}

.params-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-bottom: 12px;
}
</style>
