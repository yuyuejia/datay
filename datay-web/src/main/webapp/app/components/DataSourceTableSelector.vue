<template>
  <div>
    <!-- 当单选模式时，显示选中的表名和选择按钮，使用 flex 布局实现并排显示 -->
    <div v-if="!props.multiple" class="input-group">
      <input type="text" :value="selectedTable" class="form-control" @input="confirmInput($event.target.value)" />
      <button type="button" class="btn btn-outline-secondary" @click="showModal = true">选择</button>
    </div>
    <!-- 多选模式时，只显示选择按钮 -->
    <div v-else>
      <button type="button" class="btn btn-primary" @click="showModal = true">选择表</button>
    </div>
    <b-modal size="lg" v-model="showModal" id="tableSelectorModal" title="选择数据源表" class="modal-dialog-scrollable">
      <div class="modal-body">
        <div class="mb-3">
          <input type="text" class="form-control" v-model="searchQuery" placeholder="搜索表名（最多显示 1000 张表）" />
        </div>
        <div v-if="loading" class="text-center text-muted py-4">加载中...</div>
        <div v-else-if="tables.length === 0" class="text-center text-muted py-4">无匹配的表</div>
        <div v-else style="max-height: 60vh; overflow-y: auto;">
          <table class="table">
            <thead>
              <tr>
                <th>选择</th>
                <th>表名</th>
                <th>描述</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="table in tables" :key="table.table">
                <td>
                  <input
                    :type="props.multiple ? 'checkbox' : 'radio'"
                    :checked="isTableSelected(table)"
                    :name="!props.multiple ? 'tableSelection' : undefined"
                    @change="toggleTableSelection(table, $event)"
                  />
                </td>
                <td>{{ table.table }}</td>
                <td>{{ table.comment }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="tables.length >= 1000" class="text-center text-muted small mt-2">已加载最多 1000 张表，使用搜索框可过滤结果</div>
      </div>
      <template #modal-footer>
        <div>
          <button type="button" class="btn btn-secondary" @click="showModal = false">取消</button>
          <button type="button" class="btn btn-primary" @click="confirmSelection">确定</button>
        </div>
      </template>
    </b-modal>
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

const toggleTableSelection = (table: any, event: Event) => {
  const checked = (event.target as HTMLInputElement).checked;
  const name = table.table;

  if (!props.multiple) {
    selectedNames.value.clear();
    selectedObjs.value.clear();
  }

  if (checked) {
    selectedNames.value.add(name);
    selectedObjs.value.set(name, table);
  } else {
    selectedNames.value.delete(name);
    selectedObjs.value.delete(name);
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