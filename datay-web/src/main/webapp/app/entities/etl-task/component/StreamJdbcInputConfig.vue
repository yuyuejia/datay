<template>
  <form name="editForm" novalidate>
    <div>
      <!-- 引入 DataSourceSelector 组件 -->
      <div class="form-group">
        <label for="sourceId">数据源</label>
        <DataSourceSelector
          type="source"
          :datasourceId="formData.sourceId"
          :schema="formData.schema"
          @selected="handleDataSourceSelected"
        />
      </div>
      <div class="form-group">
        <div class="table-label-row">
          <label for="table">表名</label>
          <!-- 使用 DataSourceTableSelector 组件（多选） -->
          <DataSourceTableSelector
            :key="tableSelectorKey"
            :dataSourceId="formData.sourceId"
            :schema="formData.schema"
            :selectedTable="null"
            :selectedTables="formData.tables"
            :multiple="true"
            @selected="handleTablesSelected"
          />
        </div>
        <div v-if="formData.tables.length > 0" class="selected-tables">
          <el-tag v-for="item in formData.tables" :key="item.table" class="table-tag">
            {{ item.table }}
            <span class="table-tag-close" @click.stop="removeTable(item.table)">×</span>
          </el-tag>
        </div>
        <small class="form-text text-muted"> 不选择任何表时，表示同步该 schema 下的所有表 </small>
      </div>
      <div class="form-group">
        <label for="syncMode">同步模式</label>
        <select class="form-control" id="syncMode" name="syncMode" v-model="formData.syncMode">
          <option value="full">全量同步</option>
          <option value="incremental">增量同步</option>
        </select>
      </div>
      <div v-if="formData.syncMode === 'incremental'" class="form-group">
        <label for="incrColumn">增量字段</label>
        <div v-if="formData.tables.length === 0" class="empty-tip">增量同步需至少选择一张表，并为其选择增量字段</div>
        <div v-else class="incr-column-list">
          <div v-for="item in formData.tables" :key="item.table" class="incr-column-item">
            <label class="incr-column-label">{{ item.table }}</label>
            <DataSourceTableFieldSelector
              :dataSourceId="formData.sourceId"
              :schema="formData.schema"
              :table="item.table"
              :selectedFields="[]"
              :selectedField="formData.incrColumns[item.table] || ''"
              :multiple="false"
              @selected="(field) => handleIncrColumnSelected(item.table, field)"
            />
          </div>
        </div>
      </div>
      <div class="form-group">
        <label for="where">where</label>
        <input type="text" class="form-control" id="where" name="where" v-model="formData.where" />
      </div>
    </div>
    <div class="form-actions">
      <el-button type="primary" @click="saveConfig">保存</el-button>
      <el-button @click="cancelConfig">取消</el-button>
    </div>
  </form>
</template>

<script setup>
import { reactive, computed } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';
import DataSourceSelector from '@/components/DataSourceSelector.vue';
import DataSourceTableSelector from '@/components/DataSourceTableSelector.vue'; // 引入组件
import DataSourceTableFieldSelector from '@/components/DataSourceTableFieldSelector.vue'; // 引入组件

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save', 'cancel']);

// 解析已保存的表名（后端以逗号分隔字符串存储）
const initTables = () => {
  const tableStr = props.node?.data?.config?.table;
  if (!tableStr || typeof tableStr !== 'string') {
    return [];
  }
  return tableStr
    .split(',')
    .map((name) => name.trim())
    .filter(Boolean)
    .map((name) => ({ table: name }));
};

// 解析已保存的增量字段（新格式为 JSON，兼容旧的纯字段名格式）
const initIncrColumns = (tables) => {
  const raw = props.node?.data?.config?.incrColumn;
  if (!raw || typeof raw !== 'string' || !raw.trim()) {
    return {};
  }
  try {
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === 'object') {
      return parsed;
    }
  } catch {
    // 非 JSON，按单表纯字段名处理
    if (tables.length === 1) {
      return { [tables[0].table]: raw.trim() };
    }
  }
  return {};
};

const tables = initTables();
const incrColumns = initIncrColumns(tables);

const formData = reactive({
  sourceId: props.node?.data?.config?.sourceId,
  schema: props.node?.data?.config?.schema,
  tables,
  incrColumns,
  syncMode: Object.keys(incrColumns).length > 0 ? 'incremental' : 'full',
  where: props.node?.data?.config?.where,
});

// 表选择变化时重新挂载选择器，保证弹窗中的勾选状态与已选表一致
const tableSelectorKey = computed(() => formData.tables.map((item) => item.table).join(','));

// 处理数据源选择事件
const handleDataSourceSelected = (selectedData) => {
  formData.sourceId = selectedData.dataSourceId;
  formData.schema = selectedData.schema;
  formData.tables = []; // 切换数据源时清空表名
  formData.incrColumns = {};
};

// 处理表选择事件（多选）
const handleTablesSelected = (selection) => {
  const selected = Array.isArray(selection) ? selection : [];
  formData.tables = selected;
  const names = new Set(selected.map((item) => item.table));
  Object.keys(formData.incrColumns).forEach((name) => {
    if (!names.has(name)) {
      delete formData.incrColumns[name];
    }
  });
};

// 移除已选表
const removeTable = (tableName) => {
  formData.tables = formData.tables.filter((item) => item.table !== tableName);
  delete formData.incrColumns[tableName];
};

// 处理增量字段选择事件
const handleIncrColumnSelected = (tableName, selectedField) => {
  formData.incrColumns[tableName] = selectedField?.name || '';
};

const saveConfig = async () => {
  if (!formData.sourceId) {
    ElMessage.error('请选择数据源');
    return;
  }

  const tableStr = formData.tables.map((item) => item.table).join(',');

  let incrColumn = '';
  if (formData.syncMode === 'incremental') {
    if (formData.tables.length === 0) {
      ElMessage.error('增量同步需至少选择一张表');
      return;
    }
    const missing = formData.tables.filter((item) => !formData.incrColumns[item.table]);
    if (missing.length > 0) {
      ElMessage.error(`请为表 ${missing.map((item) => item.table).join('、')} 选择增量字段`);
      return;
    }
    const incrMap = {};
    formData.tables.forEach((item) => {
      incrMap[item.table] = formData.incrColumns[item.table];
    });
    incrColumn = JSON.stringify(incrMap);
  }

  emits('save', {
    sourceId: formData.sourceId,
    schema: formData.schema,
    table: tableStr,
    where: formData.where,
    incrColumn,
  });
};

const cancelConfig = () => {
  emits('cancel');
};

defineExpose({ saveConfig });
</script>

<style scoped>
.form-actions {
  margin-top: 30px;
  text-align: right;
}

.form-group {
  margin-bottom: 20px;
}

.table-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.selected-tables {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.table-tag-close {
  margin-left: 6px;
  cursor: pointer;
  font-weight: bold;
  color: #909399;
}

.table-tag-close:hover {
  color: #606266;
}

.empty-tip {
  color: #e6a23c;
  font-size: 13px;
}

.incr-column-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.incr-column-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.incr-column-label {
  min-width: 120px;
  font-weight: normal;
  color: #606266;
  margin-bottom: 0;
}
</style>
