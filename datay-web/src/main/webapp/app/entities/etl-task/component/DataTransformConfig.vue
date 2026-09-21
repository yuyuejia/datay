<template>
  <form name="editForm" novalidate>
    <div class="form-group">
      <label>上游字段</label>
      <div class="table-toolbar">
        <el-button type="primary" size="small" :loading="loadingTables" @click="refreshTables">调试上游获取字段</el-button>
        <span v-if="columnOptions.length" class="form-text text-muted">已获取 {{ columnOptions.length }} 个字段</span>
        <span v-else class="form-text text-muted">{{ tableError || '点击按钮调试上游组件，获取可转换的字段' }}</span>
      </div>
    </div>

    <div class="form-group">
      <label>转换规则</label>
      <div class="mapping-container">
        <div v-for="(rule, index) in formData.rules" :key="index" class="mapping-item">
          <div class="mapping-item-header">
            <span>规则 {{ index + 1 }} · {{ ruleLabel(rule.type) }}</span>
            <button type="button" class="btn-remove" @click="removeRule(index)">删除</button>
          </div>

          <div class="mapping-row">
            <select class="form-control" v-model="rule.type" @change="onRuleTypeChange(rule)">
              <option v-for="item in ruleTypes" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
          </div>

          <div class="mapping-row">
            <select class="form-control" v-model="rule.column">
              <option value="">请选择字段</option>
              <option v-for="column in optionsWith(rule.column)" :key="column.name" :value="column.name">
                {{ columnLabel(column) }}
              </option>
            </select>
            <input
              v-if="!isFilter(rule.type) && !isDrop(rule.type)"
              type="text"
              class="form-control"
              v-model="rule.targetColumn"
              :placeholder="rule.type === 'rename' ? '新字段名' : '输出字段（留空覆盖原字段）'"
            />
          </div>

          <div v-if="rule.type === 'cast'" class="mapping-row">
            <select class="form-control" v-model="rule.targetType">
              <option v-for="type in castTypes" :key="type" :value="type">
                {{ type }}
              </option>
            </select>
          </div>

          <div v-if="rule.type === 'cast' && isDecimalType(rule.targetType)" class="mapping-row">
            <input type="number" class="form-control" v-model.number="rule.precision" min="1" max="38" placeholder="精度，如 10" />
            <input type="number" class="form-control" v-model.number="rule.scale" min="0" max="38" placeholder="小数位，如 2" />
          </div>

          <template v-if="isFilter(rule.type)">
            <div class="mapping-row">
              <select class="form-control" v-model="rule.operator">
                <option v-for="op in operators" :key="op" :value="op">
                  {{ op }}
                </option>
              </select>
              <select class="form-control" v-model="rule.valueMode">
                <option value="literal">常量</option>
                <option value="column">字段</option>
                <option value="raw">表达式</option>
              </select>
            </div>
            <div v-if="!isNullOperator(rule.operator)" class="mapping-row">
              <input type="text" class="form-control" v-model="rule.value" :placeholder="valuePlaceholder(rule)" />
            </div>
          </template>

          <div v-if="rule.type === 'replace'" class="mapping-row">
            <input type="text" class="form-control" v-model="rule.search" placeholder="查找内容" />
            <input type="text" class="form-control" v-model="rule.replacement" placeholder="替换为（可空）" />
          </div>

          <div v-if="rule.type === 'substring'" class="mapping-row">
            <input type="number" class="form-control" v-model.number="rule.start" placeholder="起始位置（从1开始）" />
            <input type="number" class="form-control" v-model.number="rule.length" placeholder="长度（可空）" />
          </div>

          <div v-if="rule.type === 'concat'" class="mapping-row">
            <input type="text" class="form-control" v-model="rule.otherColumns" placeholder="其他字段，逗号分隔，如 last_name" />
            <input type="text" class="form-control" v-model="rule.separator" placeholder="分隔符（可空）" />
          </div>

          <div v-if="rule.type === 'coalesce'" class="mapping-row">
            <input type="text" class="form-control" v-model="rule.defaultValue" placeholder="空值填充值" />
          </div>

          <div v-if="rule.type === 'round'" class="mapping-row">
            <input type="number" class="form-control" v-model.number="rule.decimals" placeholder="小数位数" />
          </div>

          <div v-if="isDateFormat(rule.type)" class="mapping-row">
            <input type="text" class="form-control" v-model="rule.format" placeholder="日期格式，如 %Y-%m-%d" />
          </div>

          <div v-if="rule.type === 'extract'" class="mapping-row">
            <select class="form-control" v-model="rule.part">
              <option v-for="part in dateParts" :key="part" :value="part">
                {{ part }}
              </option>
            </select>
          </div>

          <div v-if="rule.type === 'split'" class="mapping-row">
            <input type="text" class="form-control" v-model="rule.delimiter" placeholder="分隔符" />
            <input type="number" class="form-control" v-model.number="rule.index" placeholder="取第几段" />
          </div>

          <template v-if="isArithmetic(rule.type)">
            <div class="mapping-row">
              <input type="text" class="form-control" v-model="rule.value" placeholder="运算值" />
              <label class="inline-check">
                <input type="checkbox" v-model="rule.valueIsColumn" />
                值来自字段
              </label>
            </div>
          </template>

          <div v-if="rule.type === 'custom'" class="mapping-row">
            <textarea
              class="form-control"
              v-model="rule.expression"
              :rows="2"
              placeholder="DuckDB 表达式，{column} 表示输入字段，如 {column} * 1.13"
            ></textarea>
          </div>
        </div>
        <el-button type="primary" size="small" @click="addRule">添加转换规则</el-button>
      </div>
      <small class="form-text text-muted"> 每条规则对选中的字段执行转换，输出字段留空表示覆盖原字段 </small>
    </div>

    <div class="form-group">
      <label for="selectColumns">基础输出字段</label>
      <textarea
        class="form-control"
        id="selectColumns"
        name="selectColumns"
        v-model="formData.selectColumns"
        :rows="2"
        placeholder="留空表示输出全部字段（被覆盖/删除/重命名的字段自动排除），例如：id, name"
      ></textarea>
    </div>

    <div class="form-group">
      <label for="filter">额外过滤条件</label>
      <textarea
        class="form-control"
        id="filter"
        name="filter"
        v-model="formData.filter"
        :rows="2"
        placeholder="原始 WHERE 条件（可空），例如：tenant_id = 1 AND status != 'DELETED'"
      ></textarea>
    </div>

    <div class="form-group">
      <label for="outputTable">输出表名</label>
      <input
        type="text"
        class="form-control"
        id="outputTable"
        name="outputTable"
        v-model="formData.outputTable"
        placeholder="transform_result"
      />
      <small class="form-text text-muted"> 转换结果输出给下游时携带的表名；留空默认使用上游输入表名，上游无表名时使用临时表名 </small>
    </div>

    <div v-if="previewSql" class="form-group">
      <label>生成的 SQL 预览</label>
      <pre class="sql-preview">{{ previewSql }}</pre>
    </div>
  </form>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, inject } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save']);

const designContext = inject('etlTaskDesignContext', null);

const upstreamTables = ref([]);
const loadingTables = ref(false);
const tableError = ref('');

const ruleTypes = [
  { value: 'cast', label: '类型转换' },
  { value: 'filter', label: '过滤条件' },
  { value: 'rename', label: '字段重命名' },
  { value: 'drop', label: '删除字段' },
  { value: 'upper', label: '转大写' },
  { value: 'lower', label: '转小写' },
  { value: 'trim', label: '去除首尾空格' },
  { value: 'replace', label: '字符串替换' },
  { value: 'substring', label: '截取子串' },
  { value: 'concat', label: '拼接字段' },
  { value: 'coalesce', label: '空值填充' },
  { value: 'round', label: '四舍五入' },
  { value: 'ceil', label: '向上取整' },
  { value: 'floor', label: '向下取整' },
  { value: 'abs', label: '绝对值' },
  { value: 'sqrt', label: '平方根' },
  { value: 'length', label: '字符串长度' },
  { value: 'date_format', label: '日期格式化' },
  { value: 'parse_date', label: '字符串转日期' },
  { value: 'extract', label: '日期部分提取' },
  { value: 'split', label: '字符串拆分' },
  { value: 'add', label: '加法' },
  { value: 'subtract', label: '减法' },
  { value: 'multiply', label: '乘法' },
  { value: 'divide', label: '除法' },
  { value: 'custom', label: '自定义表达式' },
];

const castTypes = [
  'VARCHAR',
  'INTEGER',
  'BIGINT',
  'SMALLINT',
  'TINYINT',
  'DOUBLE',
  'FLOAT',
  'DECIMAL',
  'NUMERIC',
  'BOOLEAN',
  'DATE',
  'TIMESTAMP',
];
const decimalTypes = ['DECIMAL', 'NUMERIC'];
const operators = ['=', '!=', '>', '>=', '<', '<=', 'LIKE', 'NOT LIKE', 'IN', 'NOT IN', 'IS NULL', 'IS NOT NULL', 'BETWEEN', 'NOT BETWEEN'];
const dateParts = ['year', 'month', 'day', 'hour', 'minute', 'second', 'week', 'quarter', 'dow', 'doy'];
const arithmeticTypes = ['add', 'subtract', 'multiply', 'divide'];
const dateFormatTypes = ['date_format', 'parse_date'];

const defaultRule = (type) => ({
  type: type || 'cast',
  column: '',
  targetColumn: '',
  targetType: 'VARCHAR',
  precision: 10,
  scale: 2,
  operator: '=',
  value: '',
  valueMode: 'literal',
  valueIsColumn: false,
  search: '',
  replacement: '',
  start: 1,
  length: null,
  otherColumns: '',
  separator: '',
  defaultValue: '',
  decimals: 2,
  format: '',
  part: 'year',
  delimiter: ',',
  index: 1,
  expression: '',
});

const fromConfigRule = (rule) => {
  const normalized = { ...defaultRule(rule.type), ...rule };
  normalized.valueIsColumn = !!rule.valueIsColumn;
  normalized.otherColumns = Array.isArray(rule.columns) ? rule.columns.join(', ') : rule.columns || '';
  normalized.valueMode = rule.valueIsColumn ? 'column' : rule.raw ? 'raw' : 'literal';
  // 兼容历史配置：targetType 直接写成 DECIMAL(10,2) 时拆分为基础类型 + 精度 + 小数位
  if (rule.targetType) {
    const match = /^([A-Za-z]+)\s*\(\s*(\d+)\s*(?:,\s*(\d+)\s*)?\)$/.exec(String(rule.targetType).trim());
    if (match && decimalTypes.includes(match[1].toUpperCase())) {
      normalized.targetType = match[1].toUpperCase();
      normalized.precision = Number(match[2]);
      normalized.scale = match[3] === undefined ? 2 : Number(match[3]);
    } else if (decimalTypes.includes(String(rule.targetType).toUpperCase())) {
      normalized.targetType = String(rule.targetType).toUpperCase();
    }
  }
  return normalized;
};

const buildForm = () => {
  const config = props.node?.data?.config || {};
  return {
    rules: Array.isArray(config.rules) ? config.rules.map(fromConfigRule) : [],
    selectColumns: config.selectColumns || '',
    filter: config.filter || '',
    outputTable: config.outputTable || '',
  };
};

const formData = reactive(buildForm());

const resetForm = () => {
  Object.assign(formData, buildForm());
};

const columnOptions = computed(() => {
  const columns = new Map();
  upstreamTables.value.forEach((table) => {
    const types = table.columnTypes || {};
    (table.columns || []).forEach((column) => {
      if (!columns.has(column) || (!columns.get(column) && types[column])) {
        columns.set(column, types[column] || '');
      }
    });
  });
  return [...columns.entries()].map(([name, type]) => ({ name, type }));
});

const optionsWith = (current) => {
  const options = [...columnOptions.value];
  const value = (current || '').trim();
  if (value && !options.some((column) => column.name === value)) {
    options.unshift({ name: value, type: '' });
  }
  return options;
};

const columnLabel = (column) => (column.type ? `${column.name} (${column.type})` : column.name);

const ruleLabel = (type) => (ruleTypes.find((item) => item.value === type) || {}).label || type;
const isFilter = (type) => type === 'filter';
const isDrop = (type) => type === 'drop';
const isNullOperator = (operator) => operator === 'IS NULL' || operator === 'IS NOT NULL';
const isArithmetic = (type) => arithmeticTypes.includes(type);
const isDateFormat = (type) => dateFormatTypes.includes(type);
const isDecimalType = (type) => decimalTypes.includes((type || '').toUpperCase());

const resolvedCastType = (rule) => {
  const type = (rule.targetType || '').toUpperCase();
  if (!isDecimalType(type)) {
    return type;
  }
  const precision = Number(rule.precision) || 10;
  const scale = rule.scale === null || rule.scale === undefined || rule.scale === '' ? 2 : Number(rule.scale);
  return `${type}(${precision},${scale})`;
};

const valuePlaceholder = (rule) => {
  if (rule.operator === 'IN' || rule.operator === 'NOT IN') {
    return '多个值用逗号分隔，如 A,B';
  }
  if (rule.operator === 'BETWEEN' || rule.operator === 'NOT BETWEEN') {
    return '两个值用逗号分隔，如 18,60';
  }
  if (rule.valueMode === 'column') {
    return '字段名';
  }
  if (rule.valueMode === 'raw') {
    return 'DuckDB 表达式';
  }
  return '常量值';
};

const refreshTables = async () => {
  if (!designContext || typeof designContext.debugUpstreamTables !== 'function') {
    tableError.value = '当前环境无法调试上游组件';
    return;
  }
  loadingTables.value = true;
  tableError.value = '';
  try {
    const res = await designContext.debugUpstreamTables(props.node?.id);
    upstreamTables.value = res.tables || [];
    tableError.value = res.error || '';
  } catch (error) {
    tableError.value = error?.message || '获取上游字段失败';
  } finally {
    loadingTables.value = false;
  }
};

onMounted(refreshTables);

watch(
  () => props.node?.id,
  () => {
    resetForm();
    upstreamTables.value = [];
    tableError.value = '';
    refreshTables();
  },
);

const addRule = () => {
  formData.rules.push(defaultRule('cast'));
};

const removeRule = (index) => {
  formData.rules.splice(index, 1);
};

const onRuleTypeChange = (rule) => {
  const next = defaultRule(rule.type);
  next.column = rule.column;
  next.targetColumn = rule.targetColumn;
  Object.keys(next).forEach((key) => {
    rule[key] = next[key];
  });
};

const identifier = (name) => {
  const value = (name || '').trim();
  return /^[A-Za-z_][A-Za-z0-9_]*$/.test(value) ? value : `"${value.replace(/"/g, '""')}"`;
};

const literal = (value) => {
  if (value === null || value === undefined || value === '') {
    return "''";
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  return `'${String(value).replace(/'/g, "''")}'`;
};

const operand = (rule, fallbackValue) => {
  if (rule.valueIsColumn) {
    return identifier(fallbackValue);
  }
  return literal(fallbackValue);
};

const buildRuleExpression = (rule) => {
  const col = identifier(rule.column);
  switch (rule.type) {
    case 'cast':
      return `CAST(${col} AS ${resolvedCastType(rule)})`;
    case 'rename':
      return col;
    case 'upper':
      return `UPPER(${col})`;
    case 'lower':
      return `LOWER(${col})`;
    case 'trim':
      return `TRIM(${col})`;
    case 'ltrim':
      return `LTRIM(${col})`;
    case 'rtrim':
      return `RTRIM(${col})`;
    case 'length':
      return `LENGTH(${col})`;
    case 'replace':
      return `REPLACE(${col}, ${literal(rule.search)}, ${literal(rule.replacement)})`;
    case 'substring':
      return rule.length === null || rule.length === undefined || rule.length === ''
        ? `SUBSTRING(${col}, ${Number(rule.start) || 1})`
        : `SUBSTRING(${col}, ${Number(rule.start) || 1}, ${Number(rule.length)})`;
    case 'concat': {
      const others = (rule.otherColumns || '')
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean)
        .map(identifier);
      return rule.separator
        ? `CONCAT_WS(${literal(rule.separator)}, ${[col, ...others].join(', ')})`
        : `CONCAT(${[col, ...others].join(', ')})`;
    }
    case 'coalesce':
      return `COALESCE(${col}, ${literal(rule.defaultValue)})`;
    case 'round':
      return rule.decimals === null || rule.decimals === undefined || rule.decimals === ''
        ? `ROUND(${col})`
        : `ROUND(${col}, ${Number(rule.decimals)})`;
    case 'ceil':
      return `CEIL(${col})`;
    case 'floor':
      return `FLOOR(${col})`;
    case 'abs':
      return `ABS(${col})`;
    case 'sqrt':
      return `SQRT(${col})`;
    case 'date_format':
      return `STRFTIME(${col}, ${literal(rule.format)})`;
    case 'parse_date':
      return `STRPTIME(${col}, ${literal(rule.format)})`;
    case 'extract':
      return `DATE_PART(${literal(rule.part)}, ${col})`;
    case 'split':
      return `SPLIT_PART(${col}, ${literal(rule.delimiter)}, ${Number(rule.index) || 1})`;
    case 'add':
      return `(${col} + ${operand(rule, rule.value)})`;
    case 'subtract':
      return `(${col} - ${operand(rule, rule.value)})`;
    case 'multiply':
      return `(${col} * ${operand(rule, rule.value)})`;
    case 'divide':
      return `(${col} / ${operand(rule, rule.value)})`;
    case 'custom':
      return (rule.expression || '').replace(/\{column\}/g, col);
    default:
      return col;
  }
};

const buildFilterCondition = (rule) => {
  const col = identifier(rule.column);
  const operator = (rule.operator || '=').toUpperCase();
  if (isNullOperator(operator)) {
    return `${col} ${operator}`;
  }
  const renderValue = (value, useColumn) => (useColumn ? identifier(value) : literal(value));
  if (operator === 'IN' || operator === 'NOT IN') {
    const items = String(rule.value || '')
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
      .map((item) => renderValue(item, rule.valueMode === 'column'));
    return `${col} ${operator} (${items.join(', ')})`;
  }
  if (operator === 'BETWEEN' || operator === 'NOT BETWEEN') {
    const items = String(rule.value || '')
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
    return `${col} ${operator} ${renderValue(items[0], rule.valueMode === 'column')} AND ${renderValue(items[1], rule.valueMode === 'column')}`;
  }
  if (rule.valueMode === 'raw') {
    return `${col} ${operator} ${rule.value}`;
  }
  return `${col} ${operator} ${renderValue(rule.value, rule.valueMode === 'column')}`;
};

const previewSql = computed(() => {
  const computedItems = [];
  const excluded = [];
  const wheres = [];

  formData.rules.forEach((rule) => {
    if (!rule.type) {
      return;
    }
    if (rule.type === 'filter') {
      if (rule.column) {
        wheres.push(buildFilterCondition(rule));
      }
      return;
    }
    if (!rule.column) {
      return;
    }
    if (rule.type === 'drop') {
      excluded.push(rule.column);
      return;
    }
    const expression = buildRuleExpression(rule);
    if (!expression) {
      return;
    }
    const alias = (rule.targetColumn || rule.column).trim();
    computedItems.push(`${expression} AS ${identifier(alias)}`);
    if (alias === rule.column) {
      excluded.push(rule.column);
    }
  });

  let base;
  if ((formData.selectColumns || '').trim()) {
    base = formData.selectColumns.trim();
  } else if (excluded.length) {
    base = `* EXCLUDE (${excluded.map(identifier).join(', ')})`;
  } else {
    base = '*';
  }

  const items = [base, ...computedItems];
  const inputTable = upstreamTables.value.length ? `main.${upstreamTables.value[0].name}` : 'main.input_table';
  let sql = `SELECT ${items.join(', ')} FROM ${inputTable}`;

  const conditions = [];
  if ((formData.filter || '').trim()) {
    conditions.push(`(${formData.filter.trim()})`);
  }
  conditions.push(...wheres);
  if (conditions.length) {
    sql += ` WHERE ${conditions.join(' AND ')}`;
  }
  return sql;
});

const serializeRule = (rule) => {
  const result = { type: rule.type, column: (rule.column || '').trim() };
  const targetColumn = (rule.targetColumn || '').trim();
  if (targetColumn && !isDrop(rule.type) && !isFilter(rule.type)) {
    result.targetColumn = targetColumn;
  }
  switch (rule.type) {
    case 'cast':
      result.targetType = rule.targetType;
      if (isDecimalType(rule.targetType)) {
        result.precision = Number(rule.precision) || 10;
        result.scale = rule.scale === null || rule.scale === undefined || rule.scale === '' ? 2 : Number(rule.scale);
      }
      break;
    case 'filter':
      result.operator = rule.operator;
      if (!isNullOperator(rule.operator)) {
        if (rule.valueMode === 'column') {
          result.valueIsColumn = true;
        } else if (rule.valueMode === 'raw') {
          result.raw = true;
        }
        result.value = rule.value;
      }
      break;
    case 'replace':
      result.search = rule.search;
      result.replacement = rule.replacement;
      break;
    case 'substring':
      result.start = Number(rule.start) || 1;
      if (rule.length !== null && rule.length !== undefined && rule.length !== '') {
        result.length = Number(rule.length);
      }
      break;
    case 'concat':
      result.columns = (rule.otherColumns || '')
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean);
      if (rule.separator) {
        result.separator = rule.separator;
      }
      break;
    case 'coalesce':
      result.defaultValue = rule.defaultValue;
      break;
    case 'round':
      if (rule.decimals !== null && rule.decimals !== undefined && rule.decimals !== '') {
        result.decimals = Number(rule.decimals);
      }
      break;
    case 'date_format':
    case 'parse_date':
      result.format = rule.format;
      break;
    case 'extract':
      result.part = rule.part;
      break;
    case 'split':
      result.delimiter = rule.delimiter;
      result.index = Number(rule.index) || 1;
      break;
    case 'add':
    case 'subtract':
    case 'multiply':
    case 'divide':
      result.value = rule.value;
      if (rule.valueIsColumn) {
        result.valueIsColumn = true;
      }
      break;
    case 'custom':
      result.expression = rule.expression;
      break;
    default:
      break;
  }
  return result;
};

const saveConfig = async () => {
  const rules = [];
  for (let i = 0; i < formData.rules.length; i++) {
    const rule = formData.rules[i];
    if (!(rule.column || '').trim()) {
      ElMessage.error(`请选择第 ${i + 1} 条规则的字段`);
      return;
    }
    if (rule.type === 'cast' && !rule.targetType) {
      ElMessage.error(`请选择第 ${i + 1} 条规则的目标类型`);
      return;
    }
    if (rule.type === 'cast' && isDecimalType(rule.targetType)) {
      const precision = Number(rule.precision);
      const scale = Number(rule.scale);
      if (!Number.isInteger(precision) || precision < 1 || precision > 38) {
        ElMessage.error(`第 ${i + 1} 条规则的 DECIMAL 精度需为 1-38 的整数`);
        return;
      }
      if (!Number.isInteger(scale) || scale < 0 || scale > precision) {
        ElMessage.error(`第 ${i + 1} 条规则的 DECIMAL 小数位需为 0 且不超过精度`);
        return;
      }
    }
    if (rule.type === 'custom' && !(rule.expression || '').trim()) {
      ElMessage.error(`请填写第 ${i + 1} 条规则的自定义表达式`);
      return;
    }
    rules.push(serializeRule(rule));
  }

  emits('save', {
    rules,
    selectColumns: (formData.selectColumns || '').trim(),
    filter: (formData.filter || '').trim(),
    outputTable: (formData.outputTable || '').trim(),
  });
};

defineExpose({ saveConfig });
</script>

<style scoped>
.form-group {
  margin-bottom: 20px;
}
.form-control {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
}
.form-control:focus {
  border-color: #409eff;
  outline: none;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}
.form-text {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
.table-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}
.table-toolbar .form-text {
  margin-top: 0;
}
.mapping-container {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
  background-color: #fafafa;
}
.mapping-item {
  border-bottom: 1px dashed #dcdfe6;
  padding-bottom: 12px;
  margin-bottom: 12px;
}
.mapping-item:last-of-type {
  border-bottom: none;
  padding-bottom: 0;
  margin-bottom: 8px;
}
.mapping-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #606266;
  font-size: 13px;
}
.mapping-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.mapping-row:last-child {
  margin-bottom: 0;
}
.mapping-row .form-control {
  flex: 1;
}
.inline-check {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
  white-space: nowrap;
}
.btn-remove {
  padding: 4px 10px;
  border: 1px solid #f56c6c;
  background: #fff;
  color: #f56c6c;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  white-space: nowrap;
}
.btn-remove:hover {
  background: #f56c6c;
  color: #fff;
}
textarea {
  resize: vertical;
  min-height: 56px;
}
.sql-preview {
  background-color: #f5f5f5;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px;
  margin: 0;
  font-size: 12px;
  font-family: 'Courier New', monospace;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
