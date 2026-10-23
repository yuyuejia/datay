<template>
  <div ref="root" class="dimension-value-select">
    <div v-if="multiple && selectedValues.length" class="dv-tags">
      <el-tag v-for="value in selectedValues" :key="value" closable :disable-transitions="true" @close="removeValue(value)">
        {{ value }}
      </el-tag>
    </div>
    <el-input
      ref="inputRef"
      v-model="keyword"
      :disabled="disabled"
      :placeholder="displayPlaceholder"
      autocomplete="off"
      @focus="openPanel"
      @click="openPanel"
      @input="onInput"
      @keyup.enter.stop="useKeyword"
    />
    <Teleport to="body">
      <div v-if="openState" ref="panel" class="dv-panel" :style="panelStyle">
        <div v-if="loading" class="dv-empty">加载中...</div>
        <div v-else-if="hint" class="dv-empty">{{ hint }}</div>
        <div v-else-if="options.length === 0 && !canCreate" class="dv-empty">无匹配数据</div>
        <template v-else>
          <div v-if="canCreate" class="dv-option dv-option-create" @click="useKeyword">
            <span v-if="multiple" class="dv-check"></span>
            <span class="dv-option-text">使用输入值：{{ customValue }}</span>
          </div>
          <div
            v-for="option in options"
            :key="option"
            class="dv-option"
            :class="{ 'dv-option-selected': isSelected(option) }"
            @click="selectOption(option)"
          >
            <span v-if="multiple" class="dv-check">{{ isSelected(option) ? '✓' : '' }}</span>
            <span class="dv-option-text">{{ option }}</span>
          </div>
        </template>
        <div v-if="options.length > 0" class="dv-pager">
          <el-button size="small" :disabled="page <= 1" @click.stop="changePage(page - 1)"> 上一页 </el-button>
          <span class="dv-page-info">{{ page }} / {{ totalPages }}</span>
          <el-button size="small" :disabled="page >= totalPages" @click.stop="changePage(page + 1)"> 下一页 </el-button>
          <span class="dv-total">共 {{ total }} 条</span>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import DimensionValueService from '@/shared/service/dimension-value.service';

// eslint-disable-next-line no-unused-vars -- 类型签名中的参数名仅用于表达含义
type ValueLoader = (keyword: string, page: number, size: number) => Promise<{ values: string[]; total: number }>;

const props = withDefaults(
  defineProps<{
    modelValue?: string | string[];
    dimensionModelId?: string;
    fieldName?: string;
    multiple?: boolean;
    placeholder?: string;
    disabled?: boolean;
    pageSize?: number;
    /** 自定义取值加载器；传入后不再请求维度取值接口，供数据集字段等场景复用。 */
    valueLoader?: ValueLoader;
    /** 是否允许把输入框中不存在于候选值的内容作为自定义值。 */
    allowCreate?: boolean;
    sourceLabel?: string;
    fieldLabel?: string;
  }>(),
  {
    modelValue: '',
    dimensionModelId: '',
    fieldName: '',
    multiple: false,
    placeholder: '请选择',
    disabled: false,
    pageSize: 20,
    valueLoader: undefined,
    allowCreate: false,
    sourceLabel: '维度模型',
    fieldLabel: '维度字段',
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: string | string[]];
}>();

const service = new DimensionValueService();

const root = ref<HTMLElement | null>(null);
const inputRef = ref<any>(null);
const panel = ref<HTMLElement | null>(null);
const panelStyle = ref<Record<string, string>>({});
const keyword = ref('');
const options = ref<string[]>([]);
const total = ref(0);
const page = ref(1);
const loading = ref(false);
const openState = ref(false);
const hint = ref('');
let debounceTimer: ReturnType<typeof setTimeout> | null = null;
let requestSeq = 0;

const selectedValues = computed<string[]>(() => {
  if (Array.isArray(props.modelValue)) {
    return props.modelValue;
  }
  if (props.modelValue === undefined || props.modelValue === null || props.modelValue === '') {
    return [];
  }
  return [String(props.modelValue)];
});

const displayPlaceholder = computed(() => {
  if (!props.multiple && selectedValues.value.length > 0) {
    return selectedValues.value[0];
  }
  return props.placeholder;
});

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / props.pageSize)));

const isSelected = (option: string) => selectedValues.value.includes(option);

const customValue = computed(() => keyword.value.trim());

const canCreate = computed(() => props.allowCreate && customValue.value !== '' && !options.value.includes(customValue.value));

const updatePanelPosition = () => {
  const el = (inputRef.value?.$el ?? inputRef.value) as HTMLElement | undefined;
  if (!el) {
    return;
  }
  const rect = el.getBoundingClientRect();
  const spaceBelow = window.innerHeight - rect.bottom;
  const openUp = spaceBelow < 260 && rect.top > spaceBelow;
  panelStyle.value = {
    position: 'fixed',
    left: `${rect.left}px`,
    width: `${Math.max(rect.width, 200)}px`,
    zIndex: '3000',
    top: openUp ? 'auto' : `${rect.bottom + 2}px`,
    bottom: openUp ? `${window.innerHeight - rect.top + 2}px` : 'auto',
  };
};

const fetchValues = async () => {
  if (!props.dimensionModelId || !props.fieldName) {
    options.value = [];
    total.value = 0;
    hint.value = !props.dimensionModelId ? `请先选择${props.sourceLabel}` : `请先选择${props.fieldLabel}`;
    return;
  }
  hint.value = '';
  loading.value = true;
  const seq = ++requestSeq;
  try {
    const data = props.valueLoader
      ? await props.valueLoader(keyword.value.trim(), page.value, props.pageSize)
      : await service.pageValues(props.dimensionModelId, props.fieldName, keyword.value.trim(), page.value, props.pageSize);
    if (seq !== requestSeq) {
      return;
    }
    options.value = data.values ?? [];
    total.value = data.total ?? 0;
  } catch {
    if (seq === requestSeq) {
      options.value = [];
      total.value = 0;
      hint.value = props.valueLoader ? '取值加载失败' : '维度取值加载失败';
    }
  } finally {
    if (seq === requestSeq) {
      loading.value = false;
    }
  }
};

const openPanel = () => {
  if (props.disabled) {
    return;
  }
  updatePanelPosition();
  openState.value = true;
  fetchValues();
};

const onInput = () => {
  page.value = 1;
  if (debounceTimer) {
    clearTimeout(debounceTimer);
  }
  debounceTimer = setTimeout(fetchValues, 300);
};

const changePage = (next: number) => {
  if (next < 1 || next > totalPages.value) {
    return;
  }
  page.value = next;
  fetchValues();
};

const selectOption = (option: string) => {
  if (props.multiple) {
    const next = isSelected(option) ? selectedValues.value.filter(item => item !== option) : [...selectedValues.value, option];
    emit('update:modelValue', next);
    return;
  }
  emit('update:modelValue', option);
  openState.value = false;
  keyword.value = '';
};

/** 把输入框中自定义的内容作为条件值。 */
const useKeyword = () => {
  if (!props.allowCreate) {
    return;
  }
  const value = customValue.value;
  if (!value) {
    return;
  }
  if (props.multiple) {
    if (!isSelected(value)) {
      emit('update:modelValue', [...selectedValues.value, value]);
    }
    keyword.value = '';
    page.value = 1;
    fetchValues();
    return;
  }
  emit('update:modelValue', value);
  openState.value = false;
  keyword.value = '';
};

const removeValue = (value: string) => {
  if (!props.multiple) {
    emit('update:modelValue', '');
    return;
  }
  emit(
    'update:modelValue',
    selectedValues.value.filter(item => item !== value),
  );
};

const onDocumentMouseDown = (event: MouseEvent) => {
  const target = event.target as Node;
  if (root.value?.contains(target) || panel.value?.contains(target)) {
    return;
  }
  openState.value = false;
};

const onViewportChange = () => {
  if (openState.value) {
    updatePanelPosition();
  }
};

watch(
  () => [props.dimensionModelId, props.fieldName],
  () => {
    options.value = [];
    total.value = 0;
    page.value = 1;
    if (openState.value) {
      fetchValues();
    }
  },
);

onMounted(() => {
  document.addEventListener('mousedown', onDocumentMouseDown);
  window.addEventListener('scroll', onViewportChange, true);
  window.addEventListener('resize', onViewportChange);
});

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentMouseDown);
  window.removeEventListener('scroll', onViewportChange, true);
  window.removeEventListener('resize', onViewportChange);
  if (debounceTimer) {
    clearTimeout(debounceTimer);
  }
});
</script>

<style scoped>
.dimension-value-select {
  position: relative;
  width: 100%;
}

.dv-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 4px;
}

.dv-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  font-size: 12px;
  background: #eef5ff;
  border: 1px solid #c6daff;
  border-radius: 3px;
}

.dv-tag-remove {
  margin-left: 4px;
  padding: 0;
  color: #409eff;
  background: none;
  border: none;
  cursor: pointer;
  line-height: 1;
}

.dv-panel {
  max-height: 280px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #ced4da;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.dv-empty {
  padding: 8px;
  color: #909399;
  font-size: 13px;
  text-align: center;
}

.dv-option {
  display: flex;
  align-items: center;
  padding: 4px 8px;
  font-size: 13px;
  cursor: pointer;
}

.dv-option:hover {
  background: #f0f6ff;
}

.dv-option-selected {
  color: #409eff;
  background: #f5f9ff;
}

.dv-option-create {
  color: #409eff;
  border-bottom: 1px dashed #e4e7ed;
}

.dv-check {
  width: 16px;
  flex: none;
}

.dv-option-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dv-pager {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-top: 1px solid #e9ecef;
  background: #fafafa;
  font-size: 12px;
}

.dv-page-btn {
  padding: 1px 6px;
  background: #fff;
  border: 1px solid #ced4da;
  border-radius: 3px;
  cursor: pointer;
}

.dv-page-btn:disabled {
  color: #bbb;
  cursor: not-allowed;
}

.dv-total {
  margin-left: auto;
  color: #909399;
}
</style>
