<template>
  <div class="table-selector">
    <!-- 单选模式：带“选择”按钮的输入框 -->
    <el-input v-if="!props.multiple" :model-value="selectedTable" placeholder="请选择表" @input="confirmInput">
      <template #append>
        <el-button @click="showModal = true">选择</el-button>
      </template>
    </el-input>
    <!-- 多选模式：输入框内展示可删除的已选表 + 右侧“选择表”按钮 -->
    <div v-else class="table-multi-input" @click="showModal = true">
      <el-tag
        v-for="t in props.selectedTables || []"
        :key="t.table"
        closable
        size="small"
        :disable-transitions="true"
        @close.stop="removeSelectedTable(t.table)"
      >
        {{ t.table }}
      </el-tag>
      <span v-if="!(props.selectedTables && props.selectedTables.length)" class="table-multi-placeholder">请选择表（不选则默认全部）</span>
      <el-button class="table-multi-input-btn" @click.stop="showModal = true">选择表</el-button>
    </div>

    <app-modal size="lg" v-model="showModal" id="tableSelectorModal" title="选择数据源表">
      <div class="table-selector-body">
        <div class="table-selector-toolbar">
          <el-input v-model="searchQuery" placeholder="搜索表名（最多显示 1000 张表）" clearable class="table-selector-search">
            <template #prefix>
              <font-awesome-icon icon="search" />
            </template>
          </el-input>
          <span v-if="props.multiple" class="table-selector-selected">已选 {{ selectedNames.size }} 张表</span>
        </div>
        <el-table
          :data="tables"
          v-loading="loading"
          max-height="52vh"
          border
          stripe
          :row-key="row => row.table"
          class="table-selector-table"
          @row-click="onRowClick"
        >
          <el-table-column width="56" align="center">
            <template #header>选择</template>
            <template #default="{ row }">
              <el-checkbox
                v-if="props.multiple"
                :model-value="isTableSelected(row)"
                @click.stop
                @change="val => toggleTableSelection(row, val)"
              />
              <el-radio
                v-else
                :model-value="isTableSelected(row) ? row.table : null"
                :value="row.table"
                @click.stop
                @change="() => selectSingleTable(row)"
              ><span /></el-radio>
            </template>
          </el-table-column>
          <el-table-column prop="table" label="表名" min-width="220" show-overflow-tooltip />
          <el-table-column label="描述" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">{{ row.comment || '-' }}</template>
          </el-table-column>
          <template #empty>无匹配的表</template>
        </el-table>
        <div v-if="tables.length >= 1000" class="table-selector-hint">已加载最多 1000 张表，使用搜索框可过滤结果</div>
      </div>
      <template #modal-footer>
        <div class="table-selector-footer">
          <el-button @click="showModal = false">取消</el-button>
          <el-button type="primary" @click="confirmSelection">确定</el-button>
        </div>
      </template>
    </app-modal>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, watch } from 'vue';
import DataSourceService from '@/entities/data-source/data-source.service';

const MAX_TABLES = 1000;
const SEARCH_DEBOUNCE_MS = 300;

const props = defineProps<{
  dataSourceId: number | null;
  schema: string | null;
  selectedTables: any[];
  selectedTable: string | null;
  multiple: boolean;
}>();

const emit = defineEmits(['selected']);

const dataSourceService = new DataSourceService();
const showModal = ref(false);
const tables = ref<any[]>([]);
const selectedTable = ref<string | null>(props.selectedTable);
const searchQuery = ref('');
const loading = ref(false);
let searchTimer: ReturnType<typeof setTimeout> | null = null;

const selectedNames = ref<Set<string>>(new Set());
const selectedObjs = ref<Map<string, any>>(new Map());

const initSelectionFromProps = () => {
  selectedNames.value = new Set();
  selectedObjs.value = new Map();
  if (props.multiple && props.selectedTables) {
    for (const t of props.selectedTables) {
      if (t?.table) {
        selectedNames.value.add(t.table);
        selectedObjs.value.set(t.table, t);
      }
    }
  } else if (!props.multiple && props.selectedTable) {
    selectedNames.value.add(props.selectedTable);
  }
};

const isTableSelected = (table: any) => selectedNames.value.has(table.table);

const applySelection = (table: any, checked: boolean) => {
  const name = table.table;
  if (!props.multiple && checked) {
    selectedNames.value = new Set();
    selectedObjs.value = new Map();
  }
  if (checked) {
    selectedNames.value.add(name);
    selectedObjs.value.set(name, table);
  } else {
    selectedNames.value.delete(name);
    selectedObjs.value.delete(name);
  }
  selectedNames.value = new Set(selectedNames.value);
  selectedObjs.value = new Map(selectedObjs.value);
};

const toggleTableSelection = (table: any, checked: boolean) => {
  applySelection(table, !!checked);
};

const selectSingleTable = (table: any) => {
  applySelection(table, true);
};

const removeSelectedTable = (name: string) => {
  selectedNames.value.delete(name);
  selectedObjs.value.delete(name);
  selectedNames.value = new Set(selectedNames.value);
  selectedObjs.value = new Map(selectedObjs.value);
  const remaining = (props.selectedTables || []).filter(t => t?.table !== name);
  emit('selected', remaining);
};

const onRowClick = (row: any) => {
  if (props.multiple) {
    toggleTableSelection(row, !isTableSelected(row));
  } else {
    selectSingleTable(row);
  }
};

const fetchTables = async (search?: string, preserveSelection = true) => {
  try {
    loading.value = true;
    if (props.dataSourceId && props.schema) {
      const res = await dataSourceService.getTables(props.dataSourceId, props.schema, MAX_TABLES, search);
      tables.value = res.data;
      if (!preserveSelection) {
        initSelectionFromProps();
      }
    }
  } catch (error) {
    console.error('获取表清单失败', error);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  initSelectionFromProps();
  fetchTables(undefined, false);
});

watch([() => props.dataSourceId, () => props.schema], () => {
  fetchTables(undefined, false);
  searchQuery.value = '';
});

watch(searchQuery, newQuery => {
  if (searchTimer) {
    clearTimeout(searchTimer);
  }
  searchTimer = setTimeout(() => {
    fetchTables(newQuery || undefined, true);
  }, SEARCH_DEBOUNCE_MS);
});

const confirmSelection = () => {
  if (props.multiple) {
    const selection = Array.from(selectedNames.value)
      .map(name => selectedObjs.value.get(name))
      .filter(Boolean);
    emit('selected', selection);
  } else {
    const name = selectedNames.value.values().next().value;
    const selection = name ? selectedObjs.value.get(name) || { table: name } : null;
    if (selection) {
      selectedTable.value = selection.table;
    }
    emit('selected', selection);
  }
  showModal.value = false;
};

const confirmInput = (value: string) => {
  if (value) {
    selectedTable.value = value;
    emit('selected', { table: value });
  }
};
</script>

<style scoped>
.table-selector {
  width: 100%;
}

.table-multi-input {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  min-height: 32px;
  padding: 3px 4px 3px 8px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  background: var(--el-fill-color-blank, #fff);
  cursor: pointer;
  transition: border-color 0.2s;
}

.table-multi-input:hover {
  border-color: var(--el-border-color-hover, #c0c4cc);
}

.table-multi-input .el-tag {
  margin-right: 0;
}

.table-multi-placeholder {
  color: var(--el-text-color-placeholder, #a8abb2);
  font-size: 14px;
  line-height: 24px;
}

.table-multi-input-btn {
  margin: -3px -4px -3px auto;
  align-self: stretch;
  flex: none;
  height: auto;
  border: 0;
  border-left: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 0 3px 3px 0;
  background: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-text-color-regular, #606266);
}

.table-multi-input-btn:hover,
.table-multi-input-btn:focus {
  border-color: var(--el-border-color, #dcdfe6);
  background: var(--el-fill-color, #f0f2f5);
  color: var(--el-color-primary, #409eff);
}

.table-selector-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.table-selector-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.table-selector-search {
  max-width: 460px;
}

.table-selector-selected {
  color: #409eff;
  font-size: 13px;
  white-space: nowrap;
}

.table-selector-table {
  width: 100%;
}

.table-selector-table :deep(.el-table__row) {
  cursor: pointer;
}

.table-selector-hint {
  color: #909399;
  font-size: 12px;
  text-align: center;
}

.table-selector-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>