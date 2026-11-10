<template>
  <div class="file-field">
    <el-input :model-value="modelValue" :placeholder="placeholder || '请选择文件'" readonly @click="openPicker">
      <template v-if="modelValue" #suffix>
        <font-awesome-icon icon="times" class="file-clear" @click.stop="clearFile" />
      </template>
      <template #append>
        <el-button @click="openPicker">选择</el-button>
      </template>
    </el-input>

    <app-modal v-model="showModal" title="选择文件" size="lg">
      <div class="file-picker-toolbar">
        <el-button size="small" :disabled="!currentPath" @click="navigateTo('')">根目录</el-button>
        <span class="file-picker-path">{{ currentPath || '/' }}</span>
      </div>
      <div v-if="loading" class="file-picker-loading"><font-awesome-icon icon="spinner" spin /> 加载中...</div>
      <el-table v-else :data="files" height="420" highlight-current-row class="file-picker-table" @row-click="handleRowClick">
        <el-table-column label="名称" min-width="280">
          <template #default="{ row }">
            <span class="file-picker-name">
              <font-awesome-icon :icon="row.isDirectory ? 'folder' : 'file'" class="file-picker-icon" />
              {{ row.name }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="120" align="center">
          <template #default="{ row }">{{ row.isDirectory ? '-' : formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button v-if="row.isDirectory" size="small" type="primary" link @click.stop="navigateTo(row.path)">进入</el-button>
            <el-button v-else size="small" type="primary" link @click.stop="selectFile(row)">选择</el-button>
          </template>
        </el-table-column>
        <template #empty>当前目录为空</template>
      </el-table>
      <template #modal-footer>
        <el-button @click="showModal = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedFile" @click="confirmSelection">确认选择</el-button>
      </template>
    </app-modal>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

import FileManagementService, { type FileInfo } from '@/entities/file-management/file-management.service';

defineProps<{
  modelValue?: string;
  placeholder?: string;
}>();

const emit = defineEmits(['change']);

const fileService = new FileManagementService();
const showModal = ref(false);
const currentPath = ref('');
const files = ref<FileInfo[]>([]);
const loading = ref(false);
const selectedFile = ref<FileInfo | null>(null);

const loadFiles = async (path: string) => {
  loading.value = true;
  try {
    const result = await fileService.list(path);
    files.value = Array.isArray(result) ? result : [];
  } catch (error) {
    console.error('获取文件列表失败', error);
    files.value = [];
  } finally {
    loading.value = false;
  }
};

const openPicker = async () => {
  showModal.value = true;
  selectedFile.value = null;
  await loadFiles(currentPath.value);
};

const navigateTo = async (path: string) => {
  currentPath.value = path || '';
  selectedFile.value = null;
  await loadFiles(currentPath.value);
};

const handleRowClick = async (row: FileInfo) => {
  if (row.isDirectory) {
    await navigateTo(row.path);
    return;
  }
  selectedFile.value = row;
};

const selectFile = (row: FileInfo) => {
  selectedFile.value = row;
  confirmSelection();
};

const confirmSelection = () => {
  if (!selectedFile.value) {
    return;
  }
  emit('change', selectedFile.value.path);
  showModal.value = false;
};

const clearFile = () => {
  emit('change', '');
};

const formatSize = (size: number) => {
  if (!size && size !== 0) {
    return '-';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
};
</script>

<style scoped>
.file-field {
  width: 100%;
}

.file-picker-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.file-picker-path {
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
  word-break: break-all;
}

.file-picker-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 420px;
  color: var(--el-text-color-secondary, #909399);
  gap: 8px;
}

.file-picker-name {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.file-picker-icon {
  color: var(--el-color-primary, #409eff);
}

.file-clear {
  cursor: pointer;
  color: var(--el-text-color-secondary, #909399);
}
</style>
