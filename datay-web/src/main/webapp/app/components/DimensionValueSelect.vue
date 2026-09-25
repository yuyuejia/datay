<template>
  <div ref="root" class="dimension-value-select">
    <div v-if="multiple && selectedValues.length" class="dv-tags">
      <span v-for="value in selectedValues" :key="value" class="dv-tag">
        <span class="dv-tag-text">{{ value }}</span>
        <button
          type="button"
          class="dv-tag-remove"
          :disabled="disabled"
          @click="removeValue(value)"
        >
          ×
        </button>
      </span>
    </div>
    <input
      ref="inputRef"
      v-model="keyword"
      type="text"
      class="form-control"
      :disabled="disabled"
      :placeholder="displayPlaceholder"
      autocomplete="off"
      @focus="openPanel"
      @click="openPanel"
      @input="onInput"
    />
    <Teleport to="body">
      <div v-if="openState" ref="panel" class="dv-panel" :style="panelStyle">
        <div v-if="loading" class="dv-empty">加载中...</div>
        <div v-else-if="hint" class="dv-empty">{{ hint }}</div>
        <div v-else-if="options.length === 0" class="dv-empty">无匹配数据</div>
        <template v-else>
          <div
            v-for="option in options"
            :key="option"
            class="dv-option"
            :class="{ 'dv-option-selected': isSelected(option) }"
            @click="selectOption(option)"
          >
            <span v-if="multiple" class="dv-check">{{
              isSelected(option) ? "✓" : ""
            }}</span>
            <span class="dv-option-text">{{ option }}</span>
          </div>
        </template>
        <div v-if="options.length > 0" class="dv-pager">
          <button
            type="button"
            class="dv-page-btn"
            :disabled="page <= 1"
            @click.stop="changePage(page - 1)"
          >
            上一页
          </button>
          <span class="dv-page-info">{{ page }} / {{ totalPages }}</span>
          <button
            type="button"
            class="dv-page-btn"
            :disabled="page >= totalPages"
            @click.stop="changePage(page + 1)"
          >
            下一页
          </button>
          <span class="dv-total">共 {{ total }} 条</span>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";

import DimensionValueService from "@/shared/service/dimension-value.service";

const props = withDefaults(
  defineProps<{
    modelValue?: string | string[];
    dimensionModelId?: number | string;
    fieldName?: string;
    multiple?: boolean;
    placeholder?: string;
    disabled?: boolean;
    pageSize?: number;
  }>(),
  {
    modelValue: "",
    dimensionModelId: "",
    fieldName: "",
    multiple: false,
    placeholder: "请选择",
    disabled: false,
    pageSize: 20,
  },
);

const emit = defineEmits<{
  "update:modelValue": [value: string | string[]];
}>();

const service = new DimensionValueService();

const root = ref<HTMLElement | null>(null);
const inputRef = ref<HTMLInputElement | null>(null);
const panel = ref<HTMLElement | null>(null);
const panelStyle = ref<Record<string, string>>({});
const keyword = ref("");
const options = ref<string[]>([]);
const total = ref(0);
const page = ref(1);
const loading = ref(false);
const openState = ref(false);
const hint = ref("");
let debounceTimer: ReturnType<typeof setTimeout> | null = null;
let requestSeq = 0;

const selectedValues = computed<string[]>(() => {
  if (Array.isArray(props.modelValue)) {
    return props.modelValue;
  }
  if (
    props.modelValue === undefined ||
    props.modelValue === null ||
    props.modelValue === ""
  ) {
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

const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / props.pageSize)),
);

const isSelected = (option: string) => selectedValues.value.includes(option);

const updatePanelPosition = () => {
  const el = inputRef.value;
  if (!el) {
    return;
  }
  const rect = el.getBoundingClientRect();
  const spaceBelow = window.innerHeight - rect.bottom;
  const openUp = spaceBelow < 260 && rect.top > spaceBelow;
  panelStyle.value = {
    position: "fixed",
    left: `${rect.left}px`,
    width: `${Math.max(rect.width, 200)}px`,
    zIndex: "3000",
    top: openUp ? "auto" : `${rect.bottom + 2}px`,
    bottom: openUp ? `${window.innerHeight - rect.top + 2}px` : "auto",
  };
};

const fetchValues = async () => {
  if (!props.dimensionModelId || !props.fieldName) {
    options.value = [];
    total.value = 0;
    hint.value = !props.dimensionModelId
      ? "请先选择维度模型"
      : "请先选择维度字段";
    return;
  }
  hint.value = "";
  loading.value = true;
  const seq = ++requestSeq;
  try {
    const data = await service.pageValues(
      props.dimensionModelId,
      props.fieldName,
      keyword.value.trim(),
      page.value,
      props.pageSize,
    );
    if (seq !== requestSeq) {
      return;
    }
    options.value = data.values ?? [];
    total.value = data.total ?? 0;
  } catch {
    if (seq === requestSeq) {
      options.value = [];
      total.value = 0;
      hint.value = "维度取值加载失败";
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
    const next = isSelected(option)
      ? selectedValues.value.filter((item) => item !== option)
      : [...selectedValues.value, option];
    emit("update:modelValue", next);
    return;
  }
  emit("update:modelValue", option);
  openState.value = false;
  keyword.value = "";
};

const removeValue = (value: string) => {
  if (!props.multiple) {
    emit("update:modelValue", "");
    return;
  }
  emit(
    "update:modelValue",
    selectedValues.value.filter((item) => item !== value),
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
  document.addEventListener("mousedown", onDocumentMouseDown);
  window.addEventListener("scroll", onViewportChange, true);
  window.addEventListener("resize", onViewportChange);
});

onBeforeUnmount(() => {
  document.removeEventListener("mousedown", onDocumentMouseDown);
  window.removeEventListener("scroll", onViewportChange, true);
  window.removeEventListener("resize", onViewportChange);
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
