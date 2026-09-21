<template>
  <form name="editForm" novalidate>
    <div class="form-group">
      <label>文件 <span class="required">*</span></label>
      <div class="file-picker">
        <input
          type="text"
          class="form-control file-path-input"
          :value="displayFilePath"
          placeholder="点击选择文件管理中的 csv / excel / parquet 文件"
          readonly
          @click="openFilePicker"
        />
        <el-button type="primary" plain @click="openFilePicker">选择</el-button>
        <el-button v-if="formData.filePath" @click="clearFile">清除</el-button>
      </div>
      <small class="form-text text-muted">仅支持 csv、xlsx、xls、parquet 格式</small>
    </div>

    <div class="form-group">
      <label>文件格式</label>
      <select class="form-control" v-model="formData.format" @change="onFormatChange">
        <option value="">自动（按扩展名推断）</option>
        <option value="csv">CSV</option>
        <option value="excel">Excel (.xlsx / .xls)</option>
        <option value="parquet">Parquet</option>
      </select>
      <small class="form-text text-muted">未选择时将按文件扩展名自动匹配</small>
    </div>

    <template v-if="effectiveFormat === 'csv'">
      <div class="form-group">
        <label>分隔符</label>
        <input
          type="text"
          class="form-control"
          v-model="formData.csvDelimiter"
          placeholder="默认逗号 ,"
        />
      </div>
      <div class="form-group">
        <label class="checkbox-inline">
          <input type="checkbox" v-model="formData.csvHasHeader" />
          <span>包含表头（第一行作为列名）</span>
        </label>
      </div>
    </template>

    <div v-if="effectiveFormat === 'excel'" class="form-group">
      <label>Sheet 名称</label>
      <input
        type="text"
        class="form-control"
        v-model="formData.excelSheet"
        placeholder="留空读取第一个 sheet"
      />
      <small class="form-text text-muted">不填则默认读取 Excel 第一个 sheet</small>
    </div>

    <b-modal v-model="showPicker" title="选择文件" size="lg">
      <div class="picker-breadcrumb">
        <span
          class="crumb-item"
          :class="{ active: !picker.currentPath }"
          @click="navigatePicker('')"
        >根目录</span>
        <template v-for="(seg, idx) in pickerSegments" :key="idx">
          <span class="crumb-sep">/</span>
          <span
            class="crumb-item"
            :class="{ active: idx === pickerSegments.length - 1 }"
            @click="navigatePicker(seg.path)"
          >{{ seg.name }}</span>
        </template>
      </div>

      <div v-if="picker.loading" class="picker-loading">
        <font-awesome-icon icon="spinner" spin class="mr-1" />加载中...
      </div>

      <div v-else-if="picker.files.length === 0" class="picker-empty">当前目录为空</div>

      <el-table v-else :data="picker.files" style="width: 100%" height="420" highlight-current-row @row-click="handlePickerRowClick">
        <el-table-column label="名称" min-width="280">
          <template #default="scope">
            <div class="picker-name">
              <font-awesome-icon
                :icon="scope.row.isDirectory ? 'folder' : fileIcon(scope.row.name)"
                :class="scope.row.isDirectory ? 'text-warning' : 'text-muted'"
                class="mr-2"
              />
              <span>{{ scope.row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.isDirectory" type="warning" size="small">目录</el-tag>
            <el-tag
              v-else-if="isSupportedFile(scope.row.name)"
              type="success"
              size="small"
            >{{ extLabel(scope.row.name) }}</el-tag>
            <el-tag v-else type="info" size="small">{{ extLabel(scope.row.name) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="120" align="center">
          <template #default="scope">{{ scope.row.isDirectory ? '-' : formatSize(scope.row.size) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center">
          <template #default="scope">
            <el-button
              v-if="scope.row.isDirectory"
              size="small"
              type="primary"
              link
              @click.stop="navigatePicker(scope.row.path)"
            >进入</el-button>
            <el-button
              v-else-if="isSupportedFile(scope.row.name)"
              size="small"
              type="primary"
              link
              @click.stop="selectFile(scope.row)"
            >选择</el-button>
            <span v-else class="picker-disabled">不支持</span>
          </template>
        </el-table-column>
      </el-table>

      <template #modal-footer>
        <el-button @click="showPicker = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!picker.selectedFile"
          @click="confirmPickFile"
        >确认选择</el-button>
      </template>
    </b-modal>
  </form>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import { ElMessage } from 'element-plus';
import FileManagementService from '@/entities/file-management/file-management.service';

const props = defineProps({ node: Object });
const emits = defineEmits(['save']);

const fileService = new FileManagementService();

const SUPPORTED_EXTS = ['.csv', '.xlsx', '.xls', '.parquet'];

const formData = reactive({
  filePath: props.node?.data?.config?.filePath || '',
  format: props.node?.data?.config?.format || '',
  csvDelimiter: props.node?.data?.config?.csvDelimiter || '',
  csvHasHeader: props.node?.data?.config?.csvHasHeader !== false,
  excelSheet: props.node?.data?.config?.excelSheet || '',
});

const showPicker = ref(false);
const picker = reactive({
  currentPath: '',
  files: [],
  loading: false,
  selectedFile: null,
});

const pickerSegments = computed(() => {
  const seg = [];
  let acc = '';
  const parts = picker.currentPath.split('/').filter(Boolean);
  for (const p of parts) {
    acc = acc ? acc + '/' + p : p;
    seg.push({ name: p, path: acc });
  }
  return seg;
});

const displayFilePath = computed(() => formData.filePath);

const effectiveFormat = computed(() => {
  if (formData.format) {
    return formData.format;
  }
  const path = formData.filePath.toLowerCase();
  if (path.endsWith('.parquet')) return 'parquet';
  if (path.endsWith('.xlsx') || path.endsWith('.xls')) return 'excel';
  if (path.endsWith('.csv')) return 'csv';
  return '';
});

const onFormatChange = () => {};

function isSupportedFile(name) {
  const lower = name.toLowerCase();
  return SUPPORTED_EXTS.some(ext => lower.endsWith(ext));
}

function extLabel(name) {
  const lower = name.toLowerCase();
  if (lower.endsWith('.parquet')) return 'Parquet';
  if (lower.endsWith('.xlsx')) return 'Excel';
  if (lower.endsWith('.xls')) return 'Excel';
  if (lower.endsWith('.csv')) return 'CSV';
  if (lower.endsWith('.json')) return 'JSON';
  const dot = lower.lastIndexOf('.');
  return dot >= 0 ? lower.substring(dot + 1).toUpperCase() : '文件';
}

function fileIcon(name) {
  const lower = name.toLowerCase();
  if (lower.endsWith('.parquet')) return 'file-code';
  if (lower.endsWith('.xlsx') || lower.endsWith('.xls')) return 'file-excel';
  if (lower.endsWith('.csv')) return 'file-csv';
  return 'file';
}

function formatSize(bytes) {
  if (bytes == null) return '';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
}

async function openFilePicker() {
  picker.currentPath = extractDir(formData.filePath);
  picker.selectedFile = null;
  showPicker.value = true;
  await loadPickerDir();
}

function extractDir(path) {
  if (!path) return '';
  const idx = path.lastIndexOf('/');
  return idx >= 0 ? path.substring(0, idx) : '';
}

async function loadPickerDir() {
  picker.loading = true;
  try {
    const files = await fileService.list(picker.currentPath);
    picker.files = files || [];
  } catch (e) {
    ElMessage.error('读取文件目录失败');
    picker.files = [];
  } finally {
    picker.loading = false;
  }
}

async function navigatePicker(path) {
  picker.currentPath = path || '';
  picker.selectedFile = null;
  await loadPickerDir();
}

function handlePickerRowClick(row) {
  if (row.isDirectory) {
    navigatePicker(row.path);
  } else if (isSupportedFile(row.name)) {
    picker.selectedFile = row;
  }
}

function selectFile(row) {
  picker.selectedFile = row;
  confirmPickFile();
}

function confirmPickFile() {
  if (!picker.selectedFile) return;
  formData.filePath = picker.selectedFile.path;
  if (!formData.format) {
    const eff = effectiveFormat.value;
    if (eff) formData.format = eff;
  }
  if (formData.csvHasHeader === undefined) {
    formData.csvHasHeader = true;
  }
  showPicker.value = false;
}

function clearFile() {
  formData.filePath = '';
  formData.format = '';
}

function saveConfig() {
  if (!formData.filePath) {
    ElMessage.error('请选择文件');
    return;
  }
  emits('save', { ...formData });
}

defineExpose({ saveConfig });
</script>

<style scoped>
.form-group {
  margin-bottom: 18px;
}
.form-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}
.form-group label .required {
  color: var(--el-color-danger, #f56c6c);
}
.form-group label .optional {
  color: var(--el-text-color-secondary, #909399);
  font-weight: normal;
  font-size: 12px;
}
.checkbox-inline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular, #606266);
  cursor: pointer;
  user-select: none;
}
.checkbox-inline input[type="checkbox"] {
  cursor: pointer;
  accent-color: var(--el-color-primary, #409eff);
  margin: 0;
}
.form-control {
  width: 100%;
  padding: 7px 10px;
  font-size: 13px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  box-sizing: border-box;
  background-color: var(--el-bg-color, #fff);
  color: var(--el-text-color-regular, #606266);
  outline: none;
  transition: border-color 0.2s;
}
.form-control:focus {
  border-color: var(--el-color-primary, #409eff);
}
.form-text {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  margin-top: 4px;
  display: block;
}
.file-picker {
  display: flex;
  gap: 8px;
  align-items: center;
}
.file-path-input {
  flex: 1;
  cursor: pointer;
}
.file-path-input:hover {
  border-color: var(--el-color-primary, #409eff);
}
.picker-breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  padding: 8px 10px;
  margin-bottom: 10px;
  background-color: var(--el-fill-color-lighter, #f5f7fa);
  border-radius: 4px;
  font-size: 13px;
}
.crumb-item {
  color: var(--el-color-primary, #409eff);
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 3px;
  transition: background-color 0.15s;
}
.crumb-item:hover {
  background-color: var(--el-color-light-9, #ecf5ff);
}
.crumb-item.active {
  color: var(--el-text-color-primary, #303133);
  cursor: default;
  font-weight: 600;
}
.crumb-item.active:hover {
  background-color: transparent;
}
.crumb-sep {
  color: var(--el-text-color-secondary, #909399);
}
.picker-loading,
.picker-empty {
  padding: 40px 0;
  text-align: center;
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
}
.picker-name {
  display: flex;
  align-items: center;
  font-size: 13px;
}
.picker-disabled {
  color: var(--el-text-color-secondary, #c0c4cc);
  font-size: 12px;
}
.form-actions {
  margin-top: 24px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
  text-align: right;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>