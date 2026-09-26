import { ref, defineComponent, computed, onMounted, inject } from 'vue';
import FileManagementService, { type FileInfo } from './file-management.service';

export default defineComponent({
  name: 'FileManagement',
  setup() {
    const alertService = inject<any>('alertService');
    const fileService = new FileManagementService();

    const currentPath = ref('');
    const files = ref<FileInfo[]>([]);
    const loading = ref(false);
    const showUploadModal = ref(false);
    const showMkdirModal = ref(false);
    const selectedFile = ref<File | null>(null);
    const newDirName = ref('');
    const showDeleteDialog = ref(false);
    const pendingDeleteFile = ref<FileInfo | null>(null);

    const breadcrumbs = computed(() => {
      if (!currentPath.value) return [{ name: '', path: '' }];
      const parts = currentPath.value.split('/').filter(Boolean);
      const crumbs: { name: string; path: string }[] = [{ name: '', path: '' }];
      let accumulated = '';
      for (const part of parts) {
        accumulated += accumulated ? '/' + part : part;
        crumbs.push({ name: part, path: accumulated });
      }
      return crumbs;
    });

    const loadFiles = async () => {
      loading.value = true;
      try {
        files.value = await fileService.list(currentPath.value);
      } catch (e: any) {
        alertService.showError(e.message || '加载文件列表失败');
      } finally {
        loading.value = false;
      }
    };

    const navigateTo = (path: string) => {
      currentPath.value = path;
      loadFiles();
    };

    const goUp = () => {
      if (!currentPath.value) return;
      const parts = currentPath.value.split('/').filter(Boolean);
      parts.pop();
      currentPath.value = parts.join('/');
      loadFiles();
    };

    const onFileChange = (uploadFile: any) => {
      selectedFile.value = uploadFile?.raw ?? null;
    };

    const onFileRemove = () => {
      selectedFile.value = null;
    };

    const handleUpload = async () => {
      if (!selectedFile.value) {
        alertService.showWarning('请先选择文件');
        return;
      }
      try {
        await fileService.upload(selectedFile.value, currentPath.value);
        alertService.showSuccess('文件上传成功');
        selectedFile.value = null;
        showUploadModal.value = false;
        loadFiles();
      } catch (e: any) {
        alertService.showError(e.message || '文件上传失败');
      }
    };

    const handleMkdir = async () => {
      if (!newDirName.value.trim()) {
        alertService.showWarning('请输入目录名称');
        return;
      }
      try {
        const fullPath = currentPath.value ? currentPath.value + '/' + newDirName.value : newDirName.value;
        await fileService.mkdir(fullPath);
        alertService.showSuccess('目录创建成功');
        newDirName.value = '';
        showMkdirModal.value = false;
        loadFiles();
      } catch (e: any) {
        alertService.showError(e.message || '目录创建失败');
      }
    };

    const handleDelete = (file: FileInfo) => {
      pendingDeleteFile.value = file;
      showDeleteDialog.value = true;
    };

    const confirmDelete = async () => {
      const file = pendingDeleteFile.value;
      if (!file) return;
      try {
        await fileService.delete(file.path);
        alertService.showSuccess('删除成功');
        pendingDeleteFile.value = null;
        showDeleteDialog.value = false;
        loadFiles();
      } catch (e: any) {
        alertService.showError(e.message || '删除失败');
      }
    };

    const handleDownload = async (file: FileInfo) => {
      if (file.isDirectory) return;
      try {
        await fileService.download(file.path);
      } catch (e: any) {
        alertService.showError(e.message || '下载失败');
      }
    };

    const formatSize = (size: number): string => {
      if (size === 0) return '-';
      if (size < 1024) return size + ' B';
      if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB';
      if (size < 1024 * 1024 * 1024) return (size / (1024 * 1024)).toFixed(1) + ' MB';
      return (size / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
    };

    const formatDate = (timestamp: number): string => {
      if (!timestamp) return '-';
      return new Date(timestamp).toLocaleString();
    };

    const getFileIcon = (file: FileInfo): string => {
      if (file.isDirectory) return 'folder';
      const name = file.name.toLowerCase();
      if (name.endsWith('.pdf')) return 'file-pdf';
      if (name.endsWith('.doc') || name.endsWith('.docx')) return 'file-word';
      if (name.endsWith('.xls') || name.endsWith('.xlsx') || name.endsWith('.csv')) return 'file-excel';
      if (name.endsWith('.zip') || name.endsWith('.tar') || name.endsWith('.gz')) return 'file-archive';
      if (name.endsWith('.jpg') || name.endsWith('.png') || name.endsWith('.gif') || name.endsWith('.bmp')) return 'file-image';
      if (name.endsWith('.txt') || name.endsWith('.md')) return 'file-alt';
      return 'file';
    };

    const clickItem = (file: FileInfo) => {
      if (file.isDirectory) {
        navigateTo(file.path);
      }
    };

    onMounted(() => {
      loadFiles();
    });

    return {
      currentPath,
      files,
      loading,
      showUploadModal,
      showMkdirModal,
      selectedFile,
      newDirName,
      showDeleteDialog,
      pendingDeleteFile,
      breadcrumbs,
      navigateTo,
      goUp,
      handleUpload,
      onFileChange,
      onFileRemove,
      handleMkdir,
      handleDelete,
      confirmDelete,
      handleDownload,
      formatSize,
      formatDate,
      getFileIcon,
      clickItem,
    };
  },
});