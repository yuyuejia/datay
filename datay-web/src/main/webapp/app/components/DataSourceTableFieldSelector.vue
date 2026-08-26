<template>
  <div>
    <!-- 当单选模式时，显示选中的字段名和选择按钮 -->
    <div v-if="!props.multiple" class="input-group">
      <input type="text" :value="selectedField" class="form-control" @input="confirmInput($event.target.value)" />
      <button type="button" class="btn btn-outline-secondary" @click="showModal = true">选择</button>
    </div>
    <!-- 多选模式时，显示已选字段和选择按钮 -->
    <div v-else>
      <div class="selected-fields mb-2">
        <span v-for="field in selectedFieldObjects" :key="field.name" class="badge bg-primary me-1">
          {{ field.name }}
          <button type="button" class="btn-close btn-close-white ms-1" @click="removeField(field)"></button>
        </span>
      </div>
      <button type="button" class="btn btn-primary" @click="showModal = true">选择字段</button>
    </div>

    <b-modal v-model="showModal" id="fieldSelectorModal" title="选择表字段">
      <div class="modal-body">
        <div v-if="loading" class="text-center">
          <div class="spinner-border" role="status">
            <span class="visually-hidden">加载中...</span>
          </div>
        </div>
        <div v-else-if="fields.length === 0" class="text-center text-muted">暂无字段数据</div>
        <div v-else>
          <div class="mb-3">
            <input type="text" class="form-control" placeholder="搜索字段..." v-model="searchKeyword" @input="filterFields" />
          </div>
          <div class="field-list" style="max-height: 300px; overflow-y: auto">
            <div v-for="field in filteredFields" :key="field.name" class="field-item mb-2">
              <div class="form-check">
                <input
                  :type="props.multiple ? 'checkbox' : 'radio'"
                  class="form-check-input"
                  :id="'field_' + field.name"
                  v-model="selectedFieldObjects"
                  :value="field"
                  :name="!props.multiple ? 'fieldSelection' : undefined"
                />
                <label class="form-check-label d-flex justify-content-between w-100" :for="'field_' + field.name">
                  <span class="field-name">{{ field.name }}</span>
                  <span class="field-type text-muted">{{ field.type }}</span>
                </label>
              </div>
              <div v-if="field.comment" class="field-comment text-muted small ms-3">
                {{ field.comment }}
              </div>
            </div>
          </div>
        </div>
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
import { ref, onMounted, watch, computed } from 'vue';
import DataSourceService from '@/entities/data-source/data-source.service';

const props = defineProps<{
  dataSourceId: number | null;
  schema: string | null;
  table: string | null;
  selectedFields: any[];
  selectedField: string | null;
  multiple: boolean;
}>();

const emit = defineEmits(['selected']);

const dataSourceService = new DataSourceService();
const showModal = ref(false);
const fields = ref<any[]>([]);
const filteredFields = ref<any[]>([]);
const selectedFieldObjects = ref<any>(props.selectedFields || []);
const selectedField = ref<string | null>(props.selectedField);
const loading = ref(false);
const searchKeyword = ref('');

const fetchFields = async () => {
  try {
    if (props.dataSourceId && props.schema && props.table) {
      loading.value = true;
      const res = await dataSourceService.getFields(props.dataSourceId, props.schema, props.table);
      fields.value = res.data;
      filteredFields.value = res.data;

      // 当字段数据加载完成后，设置已选中的字段
      if (props.multiple) {
        selectedFieldObjects.value = props.selectedFields?.filter(selected => fields.value.some(field => field.name === selected.name));
      } else if (props.selectedField) {
        const field = fields.value.find(f => f.name === props.selectedField);
        if (field) {
          selectedFieldObjects.value = [field];
        }
      }
    } else {
      fields.value = [];
      filteredFields.value = [];
      selectedFieldObjects.value = [];
    }
  } catch (error) {
    console.error('获取字段清单失败', error);
    fields.value = [];
    filteredFields.value = [];
  } finally {
    loading.value = false;
  }
};

const filterFields = () => {
  if (!searchKeyword.value.trim()) {
    filteredFields.value = fields.value;
    return;
  }

  const keyword = searchKeyword.value.toLowerCase();
  filteredFields.value = fields.value.filter(
    field =>
      field.name.toLowerCase().includes(keyword) ||
      (field.comment && field.comment.toLowerCase().includes(keyword)) ||
      (field.type && field.type.toLowerCase().includes(keyword)),
  );
};

onMounted(() => {
  fetchFields();
});

// 监听 dataSourceId、schema 和 table 的变化
watch([() => props.dataSourceId, () => props.schema, () => props.table], () => {
  fetchFields();
});

const confirmSelection = () => {
  const selection = selectedFieldObjects.value;

  if (!props.multiple && selection) {
    selectedField.value = selection.name;
  }

  emit('selected', selection);
  showModal.value = false;
};

const confirmInput = (value: string) => {
  if (value) {
    selectedField.value = value;
    emit('selected', { name: value });
  }
};

const removeField = (field: any) => {
  selectedFieldObjects.value = selectedFieldObjects.value.filter(f => f.name !== field.name);
  emit('selected', selectedFieldObjects.value);
};
</script>

<style scoped>
.field-item {
  padding: 8px;
  border: 1px solid #e9ecef;
  border-radius: 4px;
}

.field-item:hover {
  background-color: #f8f9fa;
}

.field-name {
  font-weight: 500;
}

.field-type {
  font-size: 0.875rem;
}

.field-comment {
  margin-top: 4px;
}

.selected-fields .badge {
  font-size: 0.875rem;
  padding: 4px 8px;
}
</style>
