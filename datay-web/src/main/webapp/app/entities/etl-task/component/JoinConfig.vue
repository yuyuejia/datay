<template>
  <form name="editForm" novalidate>
    <div class="form-group">
      <label>上游表清单</label>
      <div class="table-toolbar">
        <el-button type="primary" size="small" :loading="loadingTables" @click="refreshTables">调试上游获取表</el-button>
        <span v-if="upstreamTables.length" class="form-text text-muted">已获取 {{ upstreamTables.length }} 张表</span>
        <span v-else class="form-text text-muted">{{ tableError || '点击按钮调试上游组件，获取上游输出的表与字段' }}</span>
      </div>
      <small v-if="upstreamTables.length && tableError" class="form-text text-muted">{{ tableError }}</small>
    </div>

    <div class="form-group">
      <label for="outputTable">结果集表名</label>
      <input
        type="text"
        class="form-control"
        id="outputTable"
        name="outputTable"
        v-model="formData.outputTable"
        placeholder="join_result"
      />
      <small class="form-text text-muted"> Join 结果写入 DuckDB main schema 的表名，默认为 join_result </small>
    </div>

    <div class="form-group">
      <label for="fromTable">主表</label>
      <select class="form-control" id="fromTable" name="fromTable" v-model="formData.fromTable">
        <option value="">请选择主表</option>
        <option v-for="table in tableOptions(formData.fromTable)" :key="table" :value="table">{{ table }}</option>
      </select>
    </div>

    <div class="form-group">
      <label>关联表配置</label>
      <div class="mapping-container">
        <div v-for="(join, index) in formData.joins" :key="index" class="mapping-item">
          <div class="mapping-item-header">
            <span>关联 {{ index + 1 }}</span>
            <button type="button" class="btn-remove" @click="removeJoin(index)">删除</button>
          </div>
          <div class="mapping-row">
            <select class="form-control" v-model="join.type">
              <option value="INNER">INNER</option>
              <option value="LEFT">LEFT</option>
              <option value="RIGHT">RIGHT</option>
              <option value="FULL">FULL</option>
            </select>
            <select class="form-control" v-model="join.table">
              <option value="">请选择关联表</option>
              <option v-for="table in tableOptions(join.table)" :key="table" :value="table">{{ table }}</option>
            </select>
          </div>

          <div class="condition-block">
            <div class="condition-title">关联字段</div>
            <div v-if="join.on && !join.leftField" class="mapping-row">
              <input type="text" class="form-control" v-model="join.on" placeholder="手写关联条件，如 orders.user_id = users.id" />
            </div>
            <div v-else class="mapping-row">
              <select class="form-control" v-model="join.leftTable">
                <option value="">左表</option>
                <option v-for="table in leftTableOptions(index)" :key="table" :value="table">{{ table }}</option>
              </select>
              <select class="form-control" v-model="join.leftField">
                <option value="">左字段</option>
                <option v-for="column in columnOptions(join.leftTable || formData.fromTable, join.leftField)" :key="column" :value="column">
                  {{ column }}
                </option>
              </select>
              <span class="mapping-arrow">=</span>
              <select class="form-control" v-model="join.rightField">
                <option value="">右字段</option>
                <option v-for="column in columnOptions(join.table, join.rightField)" :key="column" :value="column">
                  {{ column }}
                </option>
              </select>
            </div>
          </div>
        </div>
        <el-button type="primary" size="small" @click="addJoin">添加关联表</el-button>
      </div>
      <small class="form-text text-muted"> 表与字段均来自上游调试结果的 tableMetadata，每个关联仅支持单字段关联 </small>
    </div>

    <div class="form-group">
      <label for="selectColumns">输出字段</label>
      <textarea
        class="form-control"
        id="selectColumns"
        name="selectColumns"
        v-model="formData.selectColumns"
        :rows="3"
        placeholder="留空表示 SELECT *，例如：orders.id AS order_id, users.name"
      ></textarea>
      <small class="form-text text-muted"> 支持字段别名，使用表名限定，留空表示输出所有字段 </small>
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

const buildForm = () => {
  const config = props.node?.data?.config || {};
  return {
    outputTable: config.outputTable || 'join_result',
    selectColumns: config.selectColumns || '',
    fromTable: config.fromTable || '',
    joins: Array.isArray(config.joins)
      ? config.joins.map(join => ({
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

const tableOptions = current => {
  const options = upstreamTables.value.map(table => table.name);
  const value = (current || '').trim();
  if (value && !options.includes(value)) {
    options.unshift(value);
  }
  return options;
};

const columnsOf = table => {
  const value = (table || '').trim();
  if (!value) {
    return [];
  }
  const found = upstreamTables.value.find(item => item.name === value);
  return found ? found.columns : [];
};

const columnOptions = (table, current) => {
  const options = [...columnsOf(table)];
  const value = (current || '').trim();
  if (value && !options.includes(value)) {
    options.unshift(value);
  }
  return options;
};

const leftTableOptions = index => {
  const options = [];
  const push = table => {
    const value = (table || '').trim();
    if (value && !options.includes(value)) {
      options.push(value);
    }
  };
  push(formData.fromTable);
  formData.joins.slice(0, index).forEach(join => push(join.table));
  return options;
};

const simpleName = name => {
  const value = (name || '').trim();
  return value.includes('.') ? value.substring(value.lastIndexOf('.') + 1) : value;
};

const buildOnSql = join => {
  const leftField = (join.leftField || '').trim();
  const rightField = (join.rightField || '').trim();
  if (!leftField || !rightField) {
    return '';
  }
  const leftTable = simpleName(join.leftTable || formData.fromTable);
  const rightTable = simpleName(join.table);
  return `${leftTable}.${leftField} = ${rightTable}.${rightField}`;
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

const removeJoin = index => {
  formData.joins.splice(index, 1);
};

const qualify = name => {
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
  formData.joins.forEach(join => {
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
.form-actions {
  margin-top: 30px;
  text-align: right;
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
.condition-block {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #ebeef5;
}
.condition-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.mapping-arrow {
  color: #909399;
  font-weight: bold;
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
  min-height: 80px;
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
