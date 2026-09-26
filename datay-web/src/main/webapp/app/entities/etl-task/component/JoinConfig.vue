<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="上游表清单">
      <div class="table-toolbar">
        <el-button type="primary" size="small" :loading="loadingTables" @click="refreshTables(true)">调试上游获取表</el-button>
        <span v-if="upstreamTables.length" class="form-text text-muted">已获取 {{ upstreamTables.length }} 张表</span>
        <span v-else class="form-text text-muted">{{ tableError || '点击按钮调试上游组件，获取上游输出的表与字段' }}</span>
      </div>
      <small v-if="upstreamTables.length && tableError" class="form-text text-muted">{{ tableError }}</small>
    </el-form-item>

    <el-form-item label="结果集表名">
      <el-input id="outputTable" name="outputTable" v-model="formData.outputTable" placeholder="join_result" />
      <small class="form-text text-muted"> Join 结果写入 DuckDB main schema 的表名，默认为 join_result </small>
    </el-form-item>

    <el-form-item label="主表">
      <el-select id="fromTable" name="fromTable" v-model="formData.fromTable" placeholder="请选择主表">
            <el-option v-for="table in tableOptions(formData.fromTable)" :key="table" :value="table" :label="table" />
          </el-select>
    </el-form-item>

    <el-form-item label="关联表配置">
      <div class="mapping-container">
        <div v-for="(join, index) in formData.joins" :key="index" class="mapping-item">
          <div class="mapping-item-header">
            <span>关联 {{ index + 1 }}</span>
            <el-button type="danger" link size="small" @click="removeJoin(index)">删除</el-button>
          </div>
          <div class="mapping-row">
            <el-select v-model="join.type">
            <el-option value="INNER" label="INNER" />
            <el-option value="LEFT" label="LEFT" />
            <el-option value="RIGHT" label="RIGHT" />
            <el-option value="FULL" label="FULL" />
          </el-select>
            <el-select v-model="join.table" placeholder="请选择关联表">
            <el-option v-for="table in tableOptions(join.table)" :key="table" :value="table" :label="table" />
          </el-select>
          </div>

          <div class="condition-block">
            <div class="condition-title">关联字段</div>
            <div v-if="join.on && !join.leftField" class="mapping-row">
              <el-input v-model="join.on" placeholder="手写关联条件，如 orders.user_id = users.id" />
            </div>
            <div v-else class="mapping-row">
              <el-select v-model="join.leftTable" placeholder="左表">
            <el-option v-for="table in leftTableOptions(index)" :key="table" :value="table" :label="table" />
          </el-select>
              <el-select v-model="join.leftField" placeholder="左字段">
            <el-option v-for="column in columnOptions(join.leftTable || formData.fromTable, join.leftField)" :key="column.name" :value="column.name" :label="columnLabel(column)" />
          </el-select>
              <span class="mapping-arrow">=</span>
              <el-select v-model="join.rightField" placeholder="右字段">
            <el-option v-for="column in columnOptions(join.table, join.rightField)" :key="column.name" :value="column.name" :label="columnLabel(column)" />
          </el-select>
            </div>
          </div>
        </div>
        <el-button type="primary" size="small" @click="addJoin">添加关联表</el-button>
      </div>
      <small class="form-text text-muted"> 表与字段均来自上游调试结果的 tableMetadata，每个关联仅支持单字段关联 </small>
    </el-form-item>

    <el-form-item label="输出字段">
      <el-input type="textarea" id="selectColumns" name="selectColumns" v-model="formData.selectColumns" :rows="3" placeholder="留空表示 SELECT *，例如：orders.id AS order_id, users.name" />
      <small class="form-text text-muted"> 支持字段别名，使用表名限定，留空表示输出所有字段 </small>
    </el-form-item>

    <el-form-item v-if="previewSql" label="生成的 SQL 预览">
      <pre class="sql-preview">{{ previewSql }}</pre>
    </el-form-item>
  </el-form>
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

const buildForm = () => {
  const config = props.node?.data?.config || {};
  return {
    outputTable: config.outputTable || 'join_result',
    selectColumns: config.selectColumns || '',
    fromTable: config.fromTable || '',
    joins: Array.isArray(config.joins)
      ? config.joins.map((join) => ({
          type: join.type || 'INNER',
          table: join.table || '',
          on: join.on || '',
          leftTable: join.leftTable || '',
          leftField: join.leftField || '',
          rightField: join.rightField || '',
        }))
      : [],
  };
};

const formData = reactive(buildForm());

const resetForm = () => {
  Object.assign(formData, buildForm());
};

const tableOptions = (current) => {
  const options = upstreamTables.value.map((table) => table.name);
  const value = (current || '').trim();
  if (value && !options.includes(value)) {
    options.unshift(value);
  }
  return options;
};

const columnsOf = (table) => {
  const value = (table || '').trim();
  if (!value) {
    return [];
  }
  const found = upstreamTables.value.find((item) => item.name === value);
  return found ? found.columns : [];
};

const columnTypesOf = (table) => {
  const value = (table || '').trim();
  if (!value) {
    return {};
  }
  const found = upstreamTables.value.find((item) => item.name === value);
  return found && found.columnTypes ? found.columnTypes : {};
};

const columnOptions = (table, current) => {
  const types = columnTypesOf(table);
  const options = columnsOf(table).map((name) => ({ name, type: types[name] || '' }));
  const value = (current || '').trim();
  if (value && !options.some((column) => column.name === value)) {
    options.unshift({ name: value, type: types[value] || '' });
  }
  return options;
};

const columnLabel = (column) => (column.type ? `${column.name} (${column.type})` : column.name);

const leftTableOptions = (index) => {
  const options = [];
  const push = (table) => {
    const value = (table || '').trim();
    if (value && !options.includes(value)) {
      options.push(value);
    }
  };
  push(formData.fromTable);
  formData.joins.slice(0, index).forEach((join) => push(join.table));
  return options;
};

const simpleName = (name) => {
  const value = (name || '').trim();
  return value.includes('.') ? value.substring(value.lastIndexOf('.') + 1) : value;
};

const buildOnSql = (join) => {
  const leftField = (join.leftField || '').trim();
  const rightField = (join.rightField || '').trim();
  if (!leftField || !rightField) {
    return '';
  }
  const leftTable = simpleName(join.leftTable || formData.fromTable);
  const rightTable = simpleName(join.table);
  return `${leftTable}.${leftField} = ${rightTable}.${rightField}`;
};

const refreshTables = async (force = false) => {
  if (!designContext || typeof designContext.debugUpstreamTables !== 'function') {
    tableError.value = '当前环境无法调试上游组件';
    return;
  }
  loadingTables.value = true;
  tableError.value = '';
  try {
    // 设计器会缓存各节点的上游表：已有缓存时不会重新执行调试，force=true 时强制重新获取
    const res = await designContext.debugUpstreamTables(props.node?.id, force);
    upstreamTables.value = res.tables || [];
    tableError.value = res.error || '';
  } catch (error) {
    tableError.value = error?.message || '获取上游表清单失败';
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

const addJoin = () => {
  formData.joins.push({
    type: 'INNER',
    table: '',
    on: '',
    leftTable: '',
    leftField: '',
    rightField: '',
  });
};

const removeJoin = (index) => {
  formData.joins.splice(index, 1);
};

const qualify = (name) => {
  const trimmed = (name || '').trim();
  if (!trimmed) {
    return '';
  }
  return trimmed.includes('.') ? trimmed : `main.${trimmed}`;
};

const previewSql = computed(() => {
  const fromTable = (formData.fromTable || '').trim();
  if (!fromTable) {
    return '';
  }
  const select = (formData.selectColumns || '').trim() || '*';
  let sql = `SELECT ${select} FROM ${qualify(fromTable)}`;
  formData.joins.forEach((join) => {
    const table = (join.table || '').trim();
    if (!table) {
      return;
    }
    const on = buildOnSql(join) || (join.on || '').trim();
    if (!on) {
      return;
    }
    const type = (join.type || 'INNER').trim().toUpperCase();
    const joinType = type.endsWith('JOIN') ? type : `${type} JOIN`;
    sql += ` ${joinType} ${qualify(table)}`;
    sql += ` ON ${on}`;
  });
  const outputTable = (formData.outputTable || 'join_result').trim();
  return `CREATE OR REPLACE TABLE ${qualify(outputTable)} AS ${sql}`;
});

const saveConfig = async () => {
  if (!(formData.fromTable || '').trim()) {
    ElMessage.error('请选择主表');
    return;
  }
  const outputTable = (formData.outputTable || '').trim();
  if (outputTable && !/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(outputTable)) {
    ElMessage.error('结果集表名只能包含字母、数字和下划线，且不能以数字开头');
    return;
  }

  const savedJoins = [];
  for (let i = 0; i < formData.joins.length; i++) {
    const join = formData.joins[i];
    const table = (join.table || '').trim();
    const leftField = (join.leftField || '').trim();
    const rightField = (join.rightField || '').trim();
    const on = (join.on || '').trim();
    if (!table) {
      ElMessage.error(`请选择第 ${i + 1} 个关联表`);
      return;
    }
    if (!leftField && !on) {
      ElMessage.error(`请选择第 ${i + 1} 个关联表的左字段`);
      return;
    }
    if (!rightField && !on) {
      ElMessage.error(`请选择第 ${i + 1} 个关联表的右字段`);
      return;
    }
    savedJoins.push({
      type: (join.type || 'INNER').trim(),
      table,
      leftTable: (join.leftTable || '').trim() || (formData.fromTable || '').trim(),
      leftField: leftField || '',
      rightField: rightField || '',
      on: leftField && rightField ? '' : on,
    });
  }

  emits('save', {
    outputTable: outputTable || 'join_result',
    selectColumns: (formData.selectColumns || '').trim(),
    fromTable: (formData.fromTable || '').trim(),
    joins: savedJoins,
  });
};

defineExpose({ saveConfig });
</script>

<style scoped>
.table-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.mapping-container {
  width: 100%;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  background-color: #fafcff;
}

.mapping-item {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 12px;
  background-color: #fff;
  box-shadow: 0 1px 2px rgb(0 0 0 / 4%);
}

.mapping-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f2f5;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
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

.mapping-row > :deep(.el-select),
.mapping-row > :deep(.el-input),
.mapping-row > :deep(.el-textarea),
.mapping-row > :deep(.el-input-number) {
  flex: 1 1 0;
  width: auto;
  min-width: 0;
}

.condition-block {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  background-color: #f7f9fc;
}

.condition-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.mapping-arrow {
  flex: none;
  color: #909399;
  font-weight: bold;
}

.form-text {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}

.sql-preview {
  width: 100%;
  background-color: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px;
  margin: 0;
  font-size: 12px;
  font-family: 'Courier New', monospace;
  white-space: pre-wrap;
  word-break: break-all;
}

textarea {
  resize: vertical;
}
</style>
