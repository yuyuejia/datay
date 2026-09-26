<template>
  <div class="cron-selector" :class="{ compact: props.compact }">
    <el-input
      v-model="cronExpression"
      :size="props.compact ? 'small' : 'default'"
      :class="{ 'is-invalid': cronExpression && !isValidExpression(cronExpression) }"
      placeholder="请输入 Cron 表达式，例如：0 0 12 * * ?"
      @input="handleInput"
    >
      <template #append>
        <el-button :size="props.compact ? 'small' : 'default'" @click="openModal">
          <font-awesome-icon icon="clock" class="me-1" />
          设置
        </el-button>
      </template>
    </el-input>
    <app-modal v-model="showModal" id="cronExpressionModal" title="设置 Cron 表达式" size="lg">
      <div class="modal-body cron-modal-body">
        <div class="cron-tabs">
          <div class="cron-tab-header">
            <button type="button" class="cron-tab-btn" :class="{ active: activeTab === 'preset' }" @click="activeTab = 'preset'">
              常用选项
            </button>
            <button type="button" class="cron-tab-btn" :class="{ active: activeTab === 'custom' }" @click="activeTab = 'custom'">
              自定义
            </button>
          </div>
          <div v-show="activeTab === 'preset'" class="cron-tab-pane">
            <div class="preset-groups">
              <div v-for="group in presetGroups" :key="group.title" class="preset-group">
                <div class="preset-group-title">{{ group.title }}</div>
                <div class="preset-items">
                  <button
                    v-for="item in group.items"
                    :key="item.value"
                    type="button"
                    class="preset-item"
                    :class="{ active: tempCronExpression === item.value }"
                    @click="selectPreset(item.value)"
                  >
                    <span class="preset-item-label">{{ item.label }}</span>
                    <code class="preset-item-value">{{ item.value }}</code>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div v-show="activeTab === 'custom'" class="cron-tab-pane">
            <div class="custom-builder">
              <div class="custom-raw">
                <label class="custom-raw-label">表达式</label>
                <el-input v-model="tempCronExpression" placeholder="秒 分 时 日 月 周" @input="parseExpression" />
              </div>
              <div class="field-tabs">
                <div class="field-tab-nav">
                  <button
                    v-for="field in fields"
                    :key="field.key"
                    type="button"
                    class="field-tab-btn"
                    :class="{ active: activeField === field.key }"
                    @click="activeField = field.key"
                  >
                    {{ field.label }}
                  </button>
                </div>
                <div class="field-tab-body">
                  <div v-for="field in fields" v-show="activeField === field.key" :key="field.key" class="field-control">
                    <el-radio-group
                      :model-value="parts[field.key].type"
                      class="field-type-group"
                      size="small"
                      @change="val => setPartType(field.key, val)"
                    >
                      <el-radio-button value="every">{{ field.everyLabel }}</el-radio-button>
                      <el-radio-button value="range">区间</el-radio-button>
                      <el-radio-button value="step">间隔</el-radio-button>
                      <el-radio-button value="specific">指定</el-radio-button>
                      <el-radio-button value="custom">自定义</el-radio-button>
                    </el-radio-group>

                    <div v-if="parts[field.key].type === 'range'" class="field-detail">
                      <span class="detail-text">从</span>
                      <el-select v-model="parts[field.key].from" class="detail-select" size="small">
                        <el-option v-for="opt in field.options" :key="opt.value" :value="opt.value" :label="opt.label" />
                      </el-select>
                      <span class="detail-text">到</span>
                      <el-select v-model="parts[field.key].to" class="detail-select" size="small">
                        <el-option v-for="opt in field.options" :key="opt.value" :value="opt.value" :label="opt.label" />
                      </el-select>
                    </div>

                    <div v-else-if="parts[field.key].type === 'step'" class="field-detail">
                      <span class="detail-text">从</span>
                      <el-select v-model="parts[field.key].stepFrom" class="detail-select" size="small">
                        <el-option v-for="opt in field.options" :key="opt.value" :value="opt.value" :label="opt.label" />
                      </el-select>
                      <span class="detail-text">开始，每</span>
                      <el-input-number
                        v-model="parts[field.key].step"
                        :min="1"
                        :max="field.max"
                        :controls="false"
                        size="small"
                        class="detail-number"
                      />
                      <span class="detail-text">{{ field.unit }}执行一次</span>
                    </div>

                    <div v-else-if="parts[field.key].type === 'specific'" class="field-detail">
                      <el-checkbox-group v-model="parts[field.key].specific" class="specific-detail">
                        <el-checkbox v-for="opt in field.options" :key="opt.value" :value="opt.value">{{ opt.label }}</el-checkbox>
                      </el-checkbox-group>
                    </div>

                    <div v-else-if="parts[field.key].type === 'custom'" class="field-detail">
                      <el-input v-model="parts[field.key].raw" size="small" placeholder="如：L 或 1,15,20" />
                    </div>

                    <div v-else class="field-detail">
                      <span class="detail-text">{{ field.everyHint }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="cron-result">
          <div class="cron-result-row">
            <span class="cron-result-label">表达式</span>
            <code class="cron-result-value">{{ tempCronExpression || '—' }}</code>
            <span v-if="!isValidCron" class="cron-result-error">格式不正确，应为 6 或 7 个字段</span>
          </div>
          <div class="cron-result-row">
            <span class="cron-result-label">含义</span>
            <span class="cron-result-desc">{{ cronDescription }}</span>
          </div>
        </div>
      </div>

      <template #modal-footer>
        <el-button @click="cancelSelection">取消</el-button>
        <el-button type="primary" @click="confirmSelection" :disabled="!isValidCron">确认</el-button>
      </template>
    </app-modal>
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, reactive, watch } from 'vue';

type PartType = 'every' | 'range' | 'step' | 'specific' | 'custom';

interface FieldOption {
  value: number;
  label: string;
}

interface FieldMeta {
  key: string;
  label: string;
  min: number;
  max: number;
  unit: string;
  everyLabel: string;
  everyHint: string;
  options: FieldOption[];
}

interface CronPart {
  type: PartType;
  from: number;
  to: number;
  stepFrom: number;
  step: number;
  specific: number[];
  raw: string;
}

interface PresetItem {
  label: string;
  value: string;
}

interface PresetGroup {
  title: string;
  items: PresetItem[];
}

const props = defineProps<{
  value?: string;
  compact?: boolean;
}>();

const emit = defineEmits(['update:value', 'change']);

const WEEK_LABELS: Record<number, string> = { 1: '周日', 2: '周一', 3: '周二', 4: '周三', 5: '周四', 6: '周五', 7: '周六' };
const MONTH_LABELS: Record<number, string> = {
  1: '1月',
  2: '2月',
  3: '3月',
  4: '4月',
  5: '5月',
  6: '6月',
  7: '7月',
  8: '8月',
  9: '9月',
  10: '10月',
  11: '11月',
  12: '12月',
};
const WEEK_NAMES = ['', 'SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
const WEEK_NAME_TO_NUM: Record<string, number> = { SUN: 1, MON: 2, TUE: 3, WED: 4, THU: 5, FRI: 6, SAT: 7 };

const rangeOptions = (min: number, max: number, labels?: Record<number, string>): FieldOption[] => {
  const options: FieldOption[] = [];
  for (let i = min; i <= max; i++) {
    options.push({ value: i, label: labels?.[i] ?? String(i) });
  }
  return options;
};

const fields: FieldMeta[] = [
  {
    key: 'second',
    label: '秒',
    min: 0,
    max: 59,
    unit: '秒',
    everyLabel: '每秒',
    everyHint: '每一秒都触发',
    options: rangeOptions(0, 59),
  },
  {
    key: 'minute',
    label: '分',
    min: 0,
    max: 59,
    unit: '分钟',
    everyLabel: '每分',
    everyHint: '每一分钟都触发',
    options: rangeOptions(0, 59),
  },
  {
    key: 'hour',
    label: '时',
    min: 0,
    max: 23,
    unit: '小时',
    everyLabel: '每时',
    everyHint: '每小时都触发',
    options: rangeOptions(0, 23),
  },
  {
    key: 'day',
    label: '日',
    min: 1,
    max: 31,
    unit: '天',
    everyLabel: '每天',
    everyHint: '每一天都触发',
    options: rangeOptions(1, 31),
  },
  {
    key: 'month',
    label: '月',
    min: 1,
    max: 12,
    unit: '月',
    everyLabel: '每月',
    everyHint: '每一个月都触发',
    options: rangeOptions(1, 12, MONTH_LABELS),
  },
  {
    key: 'week',
    label: '周',
    min: 1,
    max: 7,
    unit: '周',
    everyLabel: '每周',
    everyHint: '不指定星期（由日期决定）',
    options: rangeOptions(1, 7, WEEK_LABELS),
  },
];

const createPart = (field: FieldMeta): CronPart => ({
  type: 'every',
  from: field.min,
  to: Math.min(field.min + 1, field.max),
  stepFrom: field.min,
  step: 1,
  specific: [field.min],
  raw: '',
});

const parts = reactive<Record<string, CronPart>>({
  second: createPart(fields[0]),
  minute: createPart(fields[1]),
  hour: createPart(fields[2]),
  day: createPart(fields[3]),
  month: createPart(fields[4]),
  week: createPart(fields[5]),
});

const presetGroups: PresetGroup[] = [
  {
    title: '按分钟',
    items: [
      { label: '每分钟', value: '0 * * * * ?' },
      { label: '每 5 分钟', value: '0 0/5 * * * ?' },
      { label: '每 10 分钟', value: '0 0/10 * * * ?' },
      { label: '每 30 分钟', value: '0 0/30 * * * ?' },
    ],
  },
  {
    title: '按小时',
    items: [
      { label: '每小时', value: '0 0 * * * ?' },
      { label: '每小时第 30 分', value: '0 30 * * * ?' },
      { label: '每 2 小时', value: '0 0 0/2 * * ?' },
      { label: '每 6 小时', value: '0 0 0/6 * * ?' },
    ],
  },
  {
    title: '按天',
    items: [
      { label: '每天 0 点', value: '0 0 0 * * ?' },
      { label: '每天 8 点', value: '0 0 8 * * ?' },
      { label: '每天中午 12 点', value: '0 0 12 * * ?' },
      { label: '每天 18 点', value: '0 0 18 * * ?' },
      { label: '每天 9 点和 18 点', value: '0 0 9,18 * * ?' },
    ],
  },
  {
    title: '按周',
    items: [
      { label: '每周一 0 点', value: '0 0 0 ? * MON' },
      { label: '每周一 9 点', value: '0 0 9 ? * MON' },
      { label: '工作日 9 点', value: '0 0 9 ? * MON-FRI' },
      { label: '周末 10 点', value: '0 0 10 ? * SAT,SUN' },
    ],
  },
  {
    title: '按月',
    items: [
      { label: '每月 1 号 0 点', value: '0 0 0 1 * ?' },
      { label: '每月 1 号 9 点', value: '0 0 9 1 * ?' },
      { label: '每月 15 号 0 点', value: '0 0 0 15 * ?' },
      { label: '每月最后一天', value: '0 0 0 L * ?' },
    ],
  },
  {
    title: '按年',
    items: [
      { label: '每年 1 月 1 日', value: '0 0 0 1 1 ?' },
      { label: '每年 12 月 25 日', value: '0 0 0 25 12 ?' },
    ],
  },
];

const showModal = ref(false);
const activeTab = ref('preset');
const activeField = ref('second');
const cronExpression = ref(props.value || '');
const tempCronExpression = ref(props.value || '');
const yearPart = ref('');

watch(
  () => props.value,
  newValue => {
    const value = newValue || '';
    if (value !== cronExpression.value) {
      cronExpression.value = value;
      tempCronExpression.value = value;
    }
  },
);

watch(
  () => JSON.stringify(parts),
  () => {
    tempCronExpression.value = generateCron();
  },
);

const isAny = (value: string): boolean => value === '*' || value === '?';

const isValidExpression = (expression: string): boolean => {
  const parts = expression.trim().split(/\s+/);
  return parts.length === 6 || parts.length === 7;
};

const isValidCron = computed(() => isValidExpression(tempCronExpression.value));

const pad = (value: number): string => (value < 10 ? `0${value}` : String(value));

const stepInfo = (value: string): { from: string; step: number } | null => {
  if (/^\*\/(\d+)$/.test(value)) {
    return { from: '*', step: Number(value.split('/')[1]) };
  }
  if (/^\d+\/\d+$/.test(value)) {
    const [from, step] = value.split('/');
    return { from, step: Number(step) };
  }
  return null;
};

const weekNum = (token: string): number => {
  if (/^\d+$/.test(token)) {
    return Number(token);
  }
  return WEEK_NAME_TO_NUM[token.toUpperCase()] ?? NaN;
};

const describeNums = (value: string, labels?: Record<number, string>): string =>
  value
    .split(',')
    .map(item => labels?.[Number(item)] ?? item)
    .join('、');

const describeDow = (value: string): string => {
  if (value.includes('-')) {
    const [from, to] = value.split('-').map(weekNum);
    return `每${WEEK_LABELS[from]}至${WEEK_LABELS[to]}`;
  }
  if (value.includes(',')) {
    return value
      .split(',')
      .map(token => `每${WEEK_LABELS[weekNum(token)] ?? token}`)
      .join('');
  }
  const num = weekNum(value);
  return Number.isNaN(num) ? `每${value}` : `每${WEEK_LABELS[num]}`;
};

const buildDateDesc = (day: string, month: string, week: string): string => {
  const segments: string[] = [];
  const monthSpecified = !isAny(month);
  const daySpecified = !isAny(day) && day !== 'L';
  const weekSpecified = !isAny(week);

  if (monthSpecified && daySpecified) {
    segments.push(`每年 ${describeNums(month, MONTH_LABELS)}${describeNums(day)} 日`);
    return segments.join(' ');
  }

  if (monthSpecified) {
    segments.push(day === 'L' ? `每年 ${describeNums(month, MONTH_LABELS)}最后一天` : `每年 ${describeNums(month, MONTH_LABELS)}`);
  } else if (day === 'L') {
    segments.push('每月最后一天');
  } else if (daySpecified) {
    segments.push(`每月 ${describeNums(day)} 号`);
  } else if (day === '*') {
    segments.push('每天');
  }

  if (weekSpecified) {
    segments.push(describeDow(week));
  }

  return segments.join(' ');
};

const buildTimeDesc = (second: string, minute: string, hour: string): string => {
  if (/^\d+$/.test(hour) && /^\d+$/.test(minute) && /^\d+$/.test(second)) {
    return `${pad(Number(hour))}:${pad(Number(minute))}:${pad(Number(second))}`;
  }
  if (/^\d+$/.test(hour) && /^\d+$/.test(minute) && isAny(second)) {
    return `${pad(Number(hour))}:${pad(Number(minute))}`;
  }
  if (/^\d+$/.test(hour) && isAny(minute) && isAny(second)) {
    return `每天 ${Number(hour)} 点`;
  }
  if (/^\d+$/.test(minute) && /^\d+$/.test(second) && isAny(hour)) {
    return `每小时 ${pad(Number(minute))}:${pad(Number(second))}`;
  }
  if (/^\d+$/.test(second) && isAny(minute) && isAny(hour)) {
    return `每分钟第 ${second} 秒`;
  }
  if (/^\d+$/.test(minute) && /^\d+$/.test(second) && hour.includes(',')) {
    return hour
      .split(',')
      .map(item => `${pad(Number(item))}:${pad(Number(minute))}:${pad(Number(second))}`)
      .join('、');
  }
  if (/^\d+$/.test(minute) && /^\d+$/.test(second) && /^\d+\/\d+$/.test(hour)) {
    const info = stepInfo(hour)!;
    return `每天从第 ${info.from} 点开始，每 ${info.step} 小时`;
  }

  const secondStep = stepInfo(second);
  if (secondStep && isAny(minute) && isAny(hour)) {
    return `每 ${secondStep.step} 秒`;
  }

  const minuteStep = stepInfo(minute);
  if (minuteStep && isAny(hour) && isAny(second)) {
    return minuteStep.from === '*' ? `每 ${minuteStep.step} 分钟` : `每小时从第 ${minuteStep.from} 分开始，每 ${minuteStep.step} 分钟`;
  }

  const hourStep = stepInfo(hour);
  if (hourStep && isAny(minute) && isAny(second)) {
    return hourStep.from === '*' ? `每 ${hourStep.step} 小时` : `每天从第 ${hourStep.from} 点开始，每 ${hourStep.step} 小时`;
  }

  if (/^\d+$/.test(minute) && isAny(hour) && isAny(second)) {
    return `每小时第 ${minute} 分`;
  }

  if (isAny(hour) && isAny(minute) && isAny(second)) {
    return '每分钟';
  }

  return '';
};

const describeCron = (expression: string): string => {
  const segments = expression.trim().split(/\s+/);
  if (segments.length < 6) {
    return '自定义表达式';
  }
  const [second, minute, hour, day, month, week] = segments;
  const dateDesc = buildDateDesc(day, month, week);
  let timeDesc = buildTimeDesc(second, minute, hour);

  if (!dateDesc && !timeDesc) {
    return '自定义表达式';
  }

  let result = dateDesc;
  if (timeDesc) {
    if (dateDesc) {
      timeDesc = timeDesc.replace(/^每天\s*/, '');
      if (result === '每天' && /^每/.test(timeDesc)) {
        result = '';
      }
    } else if (/^\d{2}:\d{2}/.test(timeDesc)) {
      timeDesc = `每天 ${timeDesc}`;
    }
    result = result ? `${result} ${timeDesc}` : timeDesc;
  }

  return `${result} 执行`;
};

const cronDescription = computed(() => describeCron(tempCronExpression.value || ''));

const buildPart = (field: FieldMeta, part: CronPart, weekConstrained: boolean): string => {
  switch (part.type) {
    case 'every':
      if (field.key === 'week') {
        return '?';
      }
      if (field.key === 'day') {
        return weekConstrained ? '?' : '*';
      }
      return '*';
    case 'range': {
      const from = Math.min(part.from, part.to);
      const to = Math.max(part.from, part.to);
      if (field.key === 'week') {
        return `${WEEK_NAMES[from]}-${WEEK_NAMES[to]}`;
      }
      return `${from}-${to}`;
    }
    case 'step':
      return `${part.stepFrom}/${part.step}`;
    case 'specific': {
      if (!part.specific.length) {
        return field.key === 'week' ? '?' : '*';
      }
      if (field.key === 'week') {
        return part.specific.map(item => WEEK_NAMES[item]).join(',');
      }
      return part.specific.join(',');
    }
    case 'custom':
      return part.raw?.trim() || '*';
    default:
      return '*';
  }
};

const generateCron = (): string => {
  const weekConstrained = parts.week.type !== 'every';
  const expression = [
    buildPart(fields[0], parts.second, weekConstrained),
    buildPart(fields[1], parts.minute, weekConstrained),
    buildPart(fields[2], parts.hour, weekConstrained),
    buildPart(fields[3], parts.day, weekConstrained),
    buildPart(fields[4], parts.month, weekConstrained),
    buildPart(fields[5], parts.week, weekConstrained),
  ].join(' ');
  return yearPart.value ? `${expression} ${yearPart.value}` : expression;
};

const parseExpression = () => {
  const segments = (tempCronExpression.value || '').trim().split(/\s+/);
  yearPart.value = segments.length >= 7 ? segments[6] : '';
  if (segments.length < 6) {
    return;
  }
  fields.forEach((field, index) => {
    parts[field.key] = parsePart(field, segments[index]);
  });
};

const parsePart = (field: FieldMeta, value: string): CronPart => {
  const part = createPart(field);
  const normalized = value?.trim();
  if (!normalized) {
    return part;
  }
  if (normalized === '*' || normalized === '?') {
    return part;
  }
  const step = stepInfo(normalized);
  if (step) {
    part.type = 'step';
    part.stepFrom = step.from === '*' ? field.min : Number(step.from);
    part.step = step.step;
    return part;
  }
  if (/^\d+$/.test(normalized)) {
    part.type = 'specific';
    part.specific = [Number(normalized)];
    return part;
  }
  if (/^\d+-\d+$/.test(normalized)) {
    const [from, to] = normalized.split('-').map(Number);
    part.type = 'range';
    part.from = from;
    part.to = to;
    return part;
  }
  const tokens = normalized.split(',');
  const nums = tokens.map(token => (field.key === 'week' ? weekNum(token) : Number(token)));
  if (nums.every(num => !Number.isNaN(num))) {
    part.type = 'specific';
    part.specific = nums;
    return part;
  }
  if (field.key === 'week' && /[A-Za-z]+-[A-Za-z]+/.test(normalized)) {
    const [from, to] = normalized.split('-').map(weekNum);
    if (!Number.isNaN(from) && !Number.isNaN(to)) {
      part.type = 'range';
      part.from = from;
      part.to = to;
      return part;
    }
  }
  part.type = 'custom';
  part.raw = normalized;
  return part;
};

const setPartType = (key: string, type: PartType) => {
  const part = parts[key];
  part.type = type;
  if (part.type === 'range' && part.from > part.to) {
    const temp = part.from;
    part.from = part.to;
    part.to = temp;
  }
  if (key === 'day' && part.type !== 'every') {
    parts.week.type = 'every';
  }
  if (key === 'week' && part.type !== 'every') {
    parts.day.type = 'every';
  }
};

const openModal = () => {
  tempCronExpression.value = cronExpression.value;
  activeTab.value = 'preset';
  activeField.value = 'second';
  parseExpression();
  showModal.value = true;
};

const selectPreset = (value: string) => {
  tempCronExpression.value = value;
  parseExpression();
};

const handleInput = () => {
  emit('update:value', cronExpression.value);
  emit('change', cronExpression.value);
};

const confirmSelection = () => {
  cronExpression.value = tempCronExpression.value.trim();
  emit('update:value', cronExpression.value);
  emit('change', cronExpression.value);
  showModal.value = false;
};

const cancelSelection = () => {
  showModal.value = false;
};
</script>

<style scoped>
.cron-selector {
  width: 100%;
}

.cron-selector :deep(.el-input-group__append) .el-button {
  white-space: nowrap;
}

.cron-modal-body {
  padding: 0 4px;
}

.preset-groups {
  max-height: 320px;
  overflow-y: auto;
  padding-right: 4px;
}

.preset-group {
  margin-bottom: 14px;
}

.preset-group-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}

.preset-items {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preset-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  padding: 6px 10px;
  cursor: pointer;
  transition: all 0.15s;
}

.preset-item:hover {
  border-color: var(--el-color-primary, #409eff);
  color: var(--el-color-primary, #409eff);
}

.preset-item.active {
  border-color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary-light-9, #ecf5ff);
}

.preset-item-label {
  font-size: 13px;
}

.preset-item-value {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.custom-raw {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.custom-raw-label {
  flex: 0 0 auto;
  width: 64px;
  font-size: 13px;
  color: #606266;
}

.cron-tab-header {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid #e4e7ed;
  margin-bottom: 12px;
}

.cron-tab-btn {
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  padding: 6px 14px;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
}

.cron-tab-btn:hover {
  color: var(--el-color-primary, #409eff);
}

.cron-tab-btn.active {
  color: var(--el-color-primary, #409eff);
  border-bottom-color: var(--el-color-primary, #409eff);
}

.field-tabs {
  display: flex;
  gap: 12px;
  min-height: 260px;
}

.field-tab-nav {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-right: 1px solid #e4e7ed;
  padding-right: 8px;
}

.field-tab-btn {
  background: transparent;
  border: none;
  border-radius: 4px;
  padding: 6px 16px;
  font-size: 13px;
  color: #606266;
  text-align: left;
  cursor: pointer;
}

.field-tab-btn:hover {
  color: var(--el-color-primary, #409eff);
  background: #f5f7fa;
}

.field-tab-btn.active {
  color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary-light-5, #d9ecff);
}

.field-tab-body {
  flex: 1 1 auto;
  min-width: 0;
}

.field-control {
  padding-left: 4px;
}

.field-type-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.type-btn {
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 4px 12px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
}

.type-btn:hover {
  color: var(--el-color-primary, #409eff);
  border-color: var(--el-color-primary, #409eff);
}

.type-btn.active {
  color: #fff;
  border-color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary, #409eff);
}

.field-detail {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 32px;
}

.specific-detail {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  max-height: 220px;
  overflow-y: auto;
  padding: 4px 0;
}

.specific-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  margin: 0;
}

.detail-number {
  width: 90px;
}

.detail-text {
  font-size: 13px;
  color: #606266;
}

.detail-select {
  width: 100px;
}

.cron-result {
  margin-top: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
}

.cron-result-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cron-result-row + .cron-result-row {
  margin-top: 6px;
}

.cron-result-label {
  flex: 0 0 auto;
  font-size: 13px;
  color: #909399;
}

.cron-result-value {
  color: var(--el-color-primary, #409eff);
  font-size: 14px;
  font-weight: 600;
}

.cron-result-desc {
  font-size: 13px;
  color: #303133;
}

.cron-result-error {
  font-size: 12px;
  color: #f56c6c;
}
</style>
