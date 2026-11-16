import { type Ref, computed, defineComponent, inject, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessageBox } from 'element-plus';

import ShellJobService from './shell-job.service';
import ShellJobEditor from './shell-job-editor.vue';
import ETLTaskDirectoryService from '@/entities/etl-task/etl-task-directory.service';
import TaskOnlineModal from '@/components/TaskOnlineModal.vue';
import { type IJob } from '@/shared/model/job.model';
import type { ITaskOnlineConfirmPayload } from '@/shared/model/task-dependency.model';
import { ETLTaskDirectory, type IETLTaskDirectory } from '@/shared/model/etl-task-directory.model';
import { useAlertService } from '@/shared/alert/alert.service';

/** Shell 任务目录在公共目录表中的类型标识。 */
const DIRECTORY_TASK_TYPE = 'SHELL';

interface IDirectoryOption {
  id: string;
  label: string;
  children?: IDirectoryOption[];
}

interface IShellJobTreeNode {
  id: string;
  label: string;
  type: 'directory' | 'task';
  data: IETLTaskDirectory | IJob;
  children?: IShellJobTreeNode[];
}

const compareDirectories = (a: IETLTaskDirectory, b: IETLTaskDirectory): number =>
  (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || String(a.name || '').localeCompare(String(b.name || ''));

const compareJobs = (a: IJob, b: IJob): number => String(a.jobName || '').localeCompare(String(b.jobName || ''));

export default defineComponent({
  name: 'ShellJob',
  components: {
    ShellJobEditor,
    TaskOnlineModal,
  },
  setup() {
    const route = useRoute();
    const shellJobService = inject('shellJobService', () => new ShellJobService());
    const etlTaskDirectoryService = inject('etlTaskDirectoryService', () => new ETLTaskDirectoryService());
    const alertService = inject('alertService', () => useAlertService(), true);

    // ---------------- Shell 任务目录 ----------------

    const directories: Ref<IETLTaskDirectory[]> = ref([]);
    const directoryLoading = ref(false);
    const selectedDirectoryId: Ref<string | null> = ref(null);
    const treeProps = { children: 'children', label: 'label' };
    const treeRef = ref<any>(null);
    const treeSearch = ref('');
    const treeCollapsed = ref(false);

    const directoryTreeOptions = computed<IDirectoryOption[]>(() => {
      const byParent = new Map<string, IETLTaskDirectory[]>();
      directories.value.forEach(directory => {
        const parentId = directory.parentId || '';
        if (!byParent.has(parentId)) {
          byParent.set(parentId, []);
        }
        byParent.get(parentId)!.push(directory);
      });
      const build = (parentId: string): IDirectoryOption[] =>
        (byParent.get(parentId) || [])
          .slice()
          .sort(compareDirectories)
          .map(directory => {
            const children = build(directory.id!);
            return {
              id: directory.id!,
              label: directory.name || '',
              children: children.length ? children : undefined,
            };
          });
      return build('');
    });

    // ---------------- Shell 任务 ----------------

    const shellJobs: Ref<IJob[]> = ref([]);
    const isFetching = ref(false);
    const selectedJobId: Ref<string | null> = ref(null);
    const editorVisible = ref(false);
    const editorKey = ref('');
    const newTaskDirectoryId: Ref<string | null> = ref(null);
    let editorNonce = 0;

    const treeData = computed<IShellJobTreeNode[]>(() => {
      const directoryIds = new Set(directories.value.map(directory => directory.id).filter(Boolean));
      const jobsByDirectory = new Map<string, IJob[]>();
      const orphanJobs: IJob[] = [];
      shellJobs.value.forEach(job => {
        const directoryId = job.directoryId || '';
        if (directoryId && !directoryIds.has(directoryId)) {
          orphanJobs.push(job);
          return;
        }
        if (!jobsByDirectory.has(directoryId)) {
          jobsByDirectory.set(directoryId, []);
        }
        jobsByDirectory.get(directoryId)!.push(job);
      });

      const toTaskNode = (job: IJob): IShellJobTreeNode => ({
        id: job.id!,
        label: job.jobName || '',
        type: 'task',
        data: job,
      });

      const byParent = new Map<string, IETLTaskDirectory[]>();
      directories.value.forEach(directory => {
        const parentId = directory.parentId || '';
        if (!byParent.has(parentId)) {
          byParent.set(parentId, []);
        }
        byParent.get(parentId)!.push(directory);
      });

      const build = (parentId: string): IShellJobTreeNode[] => {
        const nodes: IShellJobTreeNode[] = [];
        (byParent.get(parentId) || [])
          .slice()
          .sort(compareDirectories)
          .forEach(directory => {
            const children = build(directory.id!);
            (jobsByDirectory.get(directory.id!) || [])
              .slice()
              .sort(compareJobs)
              .forEach(job => children.push(toTaskNode(job)));
            const node: IShellJobTreeNode = {
              id: directory.id!,
              label: directory.name || '',
              type: 'directory',
              data: directory,
            };
            if (children.length > 0) {
              node.children = children;
            }
            nodes.push(node);
          });
        return nodes;
      };

      const nodes = build('');
      [...(jobsByDirectory.get('') || []), ...orphanJobs].sort(compareJobs).forEach(job => nodes.push(toTaskNode(job)));
      return nodes;
    });

    const filterNode = (value: string, data: IShellJobTreeNode) => {
      if (!value) {
        return true;
      }
      if (data.type !== 'task') {
        return false;
      }
      const keyword = value.toLowerCase();
      return (data.label || '').toLowerCase().includes(keyword);
    };

    watch(treeSearch, value => {
      treeRef.value?.filter(value);
    });

    const statusClass = (status?: string | null) => {
      if (status === 'ONLINE') {
        return 'status-online';
      }
      if (status === 'RUNNING') {
        return 'status-running';
      }
      return 'status-offline';
    };

    const statusLabel = (status?: string | null) => {
      if (status === 'ONLINE') {
        return '已上线';
      }
      if (status === 'RUNNING') {
        return '运行中';
      }
      return '未上线';
    };

    const clearTreeSelection = () => {
      nextTick(() => {
        if (typeof treeRef.value?.setCurrentKey === 'function') {
          treeRef.value.setCurrentKey(undefined);
        }
      });
    };

    const selectAll = () => {
      selectedDirectoryId.value = null;
      newTaskDirectoryId.value = null;
      clearTreeSelection();
    };

    const setTreeCurrentKey = (key?: string | null) => {
      nextTick(() => {
        if (typeof treeRef.value?.setCurrentKey === 'function') {
          treeRef.value.setCurrentKey(key ?? undefined);
        }
        treeRef.value?.filter(treeSearch.value);
      });
    };

    const toggleTree = () => {
      treeCollapsed.value = !treeCollapsed.value;
      if (!treeCollapsed.value) {
        // 树重新挂载后恢复当前选中节点。
        setTreeCurrentKey(selectedJobId.value || selectedDirectoryId.value);
      }
    };

    const openTask = (jobId: string) => {
      selectedJobId.value = jobId;
      const job = shellJobs.value.find(item => item.id === jobId);
      if (job) {
        selectedDirectoryId.value = job.directoryId || null;
      }
      newTaskDirectoryId.value = null;
      editorVisible.value = true;
      editorKey.value = `job-${jobId}-${++editorNonce}`;
      setTreeCurrentKey(jobId);
    };

    const startNewTask = (directoryId?: string | null) => {
      const target = directoryId ?? selectedDirectoryId.value;
      newTaskDirectoryId.value = target || null;
      selectedJobId.value = null;
      editorVisible.value = true;
      editorKey.value = `new-${++editorNonce}`;
      setTreeCurrentKey(target || null);
    };

    const refreshEditor = () => {
      if (!selectedJobId.value) {
        return;
      }
      editorKey.value = `job-${selectedJobId.value}-${++editorNonce}`;
    };

    const handleNodeClick = (data: IShellJobTreeNode) => {
      if (data.type === 'directory') {
        selectedDirectoryId.value = data.id;
        newTaskDirectoryId.value = data.id;
        return;
      }
      openTask(data.id);
    };

    const onSaved = async (job?: IJob) => {
      const wasNew = !selectedJobId.value;
      await loadShellJobs();
      if (!job?.id) {
        return;
      }
      selectedJobId.value = job.id;
      selectedDirectoryId.value = job.directoryId || null;
      newTaskDirectoryId.value = null;
      if (wasNew) {
        editorKey.value = `job-${job.id}-${++editorNonce}`;
      }
      setTreeCurrentKey(job.id);
    };

    // ---------------- 加载数据 ----------------

    const loadDirectories = async () => {
      directoryLoading.value = true;
      try {
        const res = await etlTaskDirectoryService().retrieve(DIRECTORY_TASK_TYPE);
        directories.value = res.data || [];
        if (selectedDirectoryId.value && !directories.value.some(item => item.id === selectedDirectoryId.value)) {
          selectedDirectoryId.value = null;
        }
      } catch (error: any) {
        alertService.showHttpError(error.response);
      } finally {
        directoryLoading.value = false;
      }
    };

    const loadShellJobs = async () => {
      try {
        shellJobs.value = await shellJobService().retrieveAllShellJobs();
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    const refreshAll = async () => {
      isFetching.value = true;
      try {
        await Promise.all([loadDirectories(), loadShellJobs()]);
        setTreeCurrentKey(selectedJobId.value);
      } finally {
        isFetching.value = false;
      }
    };

    onMounted(async () => {
      await refreshAll();
      const jobId = route.query.jobId ? String(route.query.jobId) : null;
      if (jobId) {
        openTask(jobId);
        return;
      }
      const directoryId = route.query.directoryId ? String(route.query.directoryId) : null;
      if (directoryId) {
        selectedDirectoryId.value = directoryId;
        newTaskDirectoryId.value = directoryId;
        setTreeCurrentKey(directoryId);
      }
    });

    // ---------------- 目录管理 ----------------

    const siblingDirectories = (directory: IETLTaskDirectory): IETLTaskDirectory[] =>
      directories.value
        .filter(item => (item.parentId || '') === (directory.parentId || ''))
        .slice()
        .sort(compareDirectories);

    const canMoveDirectory = (directory: IETLTaskDirectory, offset: number): boolean => {
      const siblings = siblingDirectories(directory);
      const index = siblings.findIndex(item => item.id === directory.id);
      const targetIndex = index + offset;
      return index >= 0 && targetIndex >= 0 && targetIndex < siblings.length;
    };

    const moveDirectory = async (directory: IETLTaskDirectory, offset: number) => {
      const siblings = siblingDirectories(directory);
      const index = siblings.findIndex(item => item.id === directory.id);
      const targetIndex = index + offset;
      if (index < 0 || targetIndex < 0 || targetIndex >= siblings.length) {
        return;
      }
      const reordered = siblings.slice();
      [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
      try {
        await Promise.all(
          reordered
            .map((item, order) => ({ item, order }))
            .filter(({ item, order }) => (item.sortOrder ?? -1) !== order)
            .map(({ item, order }) => etlTaskDirectoryService().update({ ...item, sortOrder: order })),
        );
        await loadDirectories();
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    const directoryDialogVisible = ref(false);
    const directoryDialogTitle = ref('新增目录');
    const directoryForm: Ref<IETLTaskDirectory> = ref(new ETLTaskDirectory());
    const editingDirectoryId: Ref<string | null> = ref(null);

    const showAddDirectoryDialog = (parentId: string | null) => {
      editingDirectoryId.value = null;
      directoryDialogTitle.value = '新增目录';
      directoryForm.value = new ETLTaskDirectory();
      directoryForm.value.parentId = parentId;
      directoryForm.value.taskType = DIRECTORY_TASK_TYPE;
      directoryForm.value.sortOrder = 0;
      directoryDialogVisible.value = true;
    };

    const showEditDirectoryDialog = (directory: IETLTaskDirectory) => {
      editingDirectoryId.value = directory.id || null;
      directoryDialogTitle.value = '重命名目录';
      directoryForm.value = { ...directory };
      directoryDialogVisible.value = true;
    };

    const saveDirectory = async () => {
      if (!directoryForm.value.name || !directoryForm.value.name.trim()) {
        alertService.showError('请输入目录名称');
        return;
      }
      directoryForm.value.taskType = DIRECTORY_TASK_TYPE;
      try {
        if (editingDirectoryId.value) {
          directoryForm.value.id = editingDirectoryId.value;
          await etlTaskDirectoryService().update(directoryForm.value);
        } else {
          await etlTaskDirectoryService().create(directoryForm.value);
        }
        directoryDialogVisible.value = false;
        alertService.showSuccess('目录保存成功');
        await loadDirectories();
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    const deleteDirectory = async (directory: IETLTaskDirectory) => {
      try {
        await ElMessageBox.confirm(
          `确定要删除目录「${directory.name || ''}」吗？删除后该目录下的子目录与 Shell 任务将移动到根目录。`,
          '删除目录',
          {
            confirmButtonText: '删除',
            cancelButtonText: '取消',
            type: 'warning',
          },
        );
      } catch {
        return;
      }
      try {
        await etlTaskDirectoryService().delete(directory.id!);
        if (selectedDirectoryId.value === directory.id) {
          selectAll();
        }
        alertService.showSuccess('目录删除成功');
        await Promise.all([loadDirectories(), loadShellJobs()]);
        setTreeCurrentKey(selectedJobId.value);
        refreshEditor();
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    // ---------------- 任务操作 ----------------

    const handleExecuteOnce = async (job: IJob) => {
      try {
        await shellJobService().run(job.id);
        alertService.showInfo(`Shell 任务 ${job.jobName} 已触发执行一次`);
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    const onlineModal = ref<any>(null);
    const onlineTask: Ref<IJob | null> = ref(null);
    const isOnlineSubmitting = ref(false);

    const handleOnline = (job: IJob) => {
      onlineTask.value = job;
      onlineModal.value?.show({
        jobId: job.id || '',
        cron: job.cron || '',
        retryCount: job.retryCount ?? 0,
        timeoutSeconds: job.timeoutSeconds ?? 0,
        priority: job.priority ?? 2,
      });
    };

    const confirmOnline = async (payload: ITaskOnlineConfirmPayload) => {
      if (!onlineTask.value?.id) {
        return;
      }
      isOnlineSubmitting.value = true;
      try {
        await shellJobService().online(
          onlineTask.value.id,
          payload.cron,
          payload.retryCount,
          payload.timeoutSeconds,
          payload.priority,
          payload.dependencies,
        );
        alertService.showInfo(`Shell 任务 ${onlineTask.value.jobName} 已上线调度`);
        onlineModal.value?.hide();
        await loadShellJobs();
        setTreeCurrentKey(selectedJobId.value);
      } catch (error: any) {
        alertService.showHttpError(error.response);
      } finally {
        isOnlineSubmitting.value = false;
      }
    };

    const handleOffline = async (job: IJob) => {
      if (!job?.id) {
        return;
      }
      try {
        await ElMessageBox.confirm(`确定要将 Shell 任务「${job.jobName || job.id}」下线吗？下线后将取消定时调度。`, '下线确认', {
          confirmButtonText: '确认下线',
          cancelButtonText: '取消',
          type: 'warning',
        });
      } catch {
        return;
      }
      try {
        await shellJobService().offline(job.id);
        alertService.showInfo(`Shell 任务 ${job.jobName} 已下线`);
        await loadShellJobs();
        setTreeCurrentKey(selectedJobId.value);
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    const deleteDialogVisible = ref(false);
    const removeJob: Ref<IJob | null> = ref(null);

    const prepareRemove = (job: IJob) => {
      removeJob.value = job;
      deleteDialogVisible.value = true;
    };

    const closeDeleteDialog = () => {
      deleteDialogVisible.value = false;
      removeJob.value = null;
    };

    const removeShellJob = async () => {
      if (!removeJob.value?.id) {
        return;
      }
      const removedId = removeJob.value.id;
      try {
        await shellJobService().delete(removedId);
        alertService.showInfo('Shell 任务已删除', { variant: 'danger' });
        if (selectedJobId.value === removedId) {
          selectedJobId.value = null;
          editorVisible.value = false;
        }
        closeDeleteDialog();
        await loadShellJobs();
        setTreeCurrentKey(selectedJobId.value);
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    const moveDialogVisible = ref(false);
    const moveJob: Ref<IJob | null> = ref(null);
    const moveTargetDirectoryId: Ref<string | null> = ref(null);

    const openMoveDialog = (job: IJob) => {
      moveJob.value = job;
      moveTargetDirectoryId.value = job.directoryId || null;
      moveDialogVisible.value = true;
    };

    const closeMoveDialog = () => {
      moveDialogVisible.value = false;
      moveJob.value = null;
      moveTargetDirectoryId.value = null;
    };

    const confirmMove = async () => {
      if (!moveJob.value?.id) {
        return;
      }
      try {
        const moved = await shellJobService().moveToDirectory(moveJob.value.id, moveTargetDirectoryId.value || null);
        alertService.showSuccess('Shell 任务已移动');
        const movedId = moveJob.value.id;
        closeMoveDialog();
        await loadShellJobs();
        if (selectedJobId.value === movedId) {
          selectedDirectoryId.value = moved?.directoryId || null;
          refreshEditor();
        }
        setTreeCurrentKey(selectedJobId.value);
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    const handleTaskCommand = (command: string, job: IJob) => {
      if (command === 'run') {
        handleExecuteOnce(job);
      } else if (command === 'online') {
        handleOnline(job);
      } else if (command === 'offline') {
        handleOffline(job);
      } else if (command === 'move') {
        openMoveDialog(job);
      } else if (command === 'delete') {
        prepareRemove(job);
      }
    };

    return {
      directories,
      directoryLoading,
      directoryTreeOptions,
      treeProps,
      treeRef,
      treeSearch,
      treeCollapsed,
      toggleTree,
      filterNode,
      treeData,
      selectedDirectoryId,
      isFetching,
      selectedJobId,
      editorVisible,
      editorKey,
      newTaskDirectoryId,
      shellJobs,
      statusClass,
      statusLabel,
      selectAll,
      handleNodeClick,
      openTask,
      startNewTask,
      onSaved,
      canMoveDirectory,
      moveDirectory,
      directoryDialogVisible,
      directoryDialogTitle,
      directoryForm,
      showAddDirectoryDialog,
      showEditDirectoryDialog,
      saveDirectory,
      deleteDirectory,
      handleTaskCommand,
      onlineModal,
      onlineTask,
      isOnlineSubmitting,
      confirmOnline,
      deleteDialogVisible,
      removeJob,
      closeDeleteDialog,
      removeShellJob,
      moveDialogVisible,
      moveJob,
      moveTargetDirectoryId,
      closeMoveDialog,
      confirmMove,
    };
  },
});
