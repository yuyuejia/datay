import { type Ref, computed, defineComponent, inject, nextTick, onMounted, ref, watch } from "vue";
import { ElMessageBox } from "element-plus";

import DagJobService from "./dag-job.service";
import ETLTaskDirectoryService from "@/entities/etl-task/etl-task-directory.service";
import TaskOnlineModal from "@/components/TaskOnlineModal.vue";
import { type IJob } from "@/shared/model/job.model";
import { ETLTaskDirectory, type IETLTaskDirectory } from "@/shared/model/etl-task-directory.model";
import { useDateFormat } from "@/shared/composables";
import { useAlertService } from "@/shared/alert/alert.service";

interface IDirectoryTreeNode {
  id: string;
  label: string;
  type: "directory";
  data: IETLTaskDirectory;
  children?: IDirectoryTreeNode[];
}

const DIRECTORY_TASK_TYPE = "DAG";

export default defineComponent({
  name: "DagJob",
  components: {
    TaskOnlineModal,
  },
  setup() {
    const dateFormat = useDateFormat();
    const dagJobService = inject("dagJobService", () => new DagJobService());
    const etlTaskDirectoryService = inject("etlTaskDirectoryService", () => new ETLTaskDirectoryService());
    const alertService = inject("alertService", () => useAlertService(), true);

    const itemsPerPage = ref(20);
    const queryCount: Ref<number> = ref(null);
    const page: Ref<number> = ref(1);
    const propOrder = ref("id");
    const reverse = ref(false);
    const totalItems = ref(0);
    const dagJobs: Ref<IJob[]> = ref([]);
    const isFetching = ref(false);

    // ---------------- 编排目录 ----------------

    const directories: Ref<IETLTaskDirectory[]> = ref([]);
    const directoryLoading = ref(false);
    const selectedDirectoryId: Ref<string | null> = ref(null);
    const treeProps = { children: "children", label: "label" };
    const directoryTreeRef = ref<any>(null);

    const compareDirectories = (a: IETLTaskDirectory, b: IETLTaskDirectory): number =>
      (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || String(a.name || "").localeCompare(String(b.name || ""));

    const directoryTree = computed<IDirectoryTreeNode[]>(() => {
      const byParent = new Map<string, IETLTaskDirectory[]>();
      directories.value.forEach(directory => {
        const parentId = directory.parentId || "";
        if (!byParent.has(parentId)) {
          byParent.set(parentId, []);
        }
        byParent.get(parentId)!.push(directory);
      });
      const build = (parentId: string): IDirectoryTreeNode[] =>
        (byParent.get(parentId) || [])
          .slice()
          .sort(compareDirectories)
          .map(directory => {
            const children = build(directory.id!);
            const node: IDirectoryTreeNode = {
              id: directory.id!,
              label: directory.name || "",
              type: "directory",
              data: directory,
            };
            if (children.length > 0) {
              node.children = children;
            }
            return node;
          });
      return build("");
    });

    const directoryPathMap = computed(() => {
      const byId = new Map(directories.value.filter(item => item.id).map(item => [item.id!, item]));
      const paths = new Map<string, string>();
      const resolve = (id: string, guard = new Set<string>()): string => {
        if (paths.has(id)) {
          return paths.get(id)!;
        }
        const directory = byId.get(id);
        if (!directory || guard.has(id)) {
          return "";
        }
        guard.add(id);
        const name = directory.name || "";
        const parentPath = directory.parentId ? resolve(directory.parentId, guard) : "";
        const path = parentPath ? `${parentPath} / ${name}` : name;
        paths.set(id, path);
        return path;
      };
      directories.value.forEach(directory => {
        if (directory.id) {
          resolve(directory.id);
        }
      });
      return paths;
    });

    const directoryPath = (job: IJob): string =>
      job.directoryId ? directoryPathMap.value.get(job.directoryId) || "-" : "根目录";

    const loadDirectories = async () => {
      directoryLoading.value = true;
      try {
        const res = await etlTaskDirectoryService().retrieve(DIRECTORY_TASK_TYPE);
        directories.value = res.data || [];
        if (selectedDirectoryId.value && !directories.value.some(item => item.id === selectedDirectoryId.value)) {
          selectedDirectoryId.value = null;
        }
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        directoryLoading.value = false;
      }
    };

    const clearTreeSelection = () => {
      nextTick(() => {
        if (typeof directoryTreeRef.value?.setCurrentKey === "function") {
          directoryTreeRef.value.setCurrentKey(undefined);
        }
      });
    };

    const selectAllJobs = () => {
      selectedDirectoryId.value = null;
      clearTreeSelection();
    };

    const handleDirectoryClick = (data: IDirectoryTreeNode) => {
      selectedDirectoryId.value = data.id;
    };

    const siblingDirectories = (directory: IETLTaskDirectory): IETLTaskDirectory[] =>
      directories.value
        .filter(item => (item.parentId || "") === (directory.parentId || ""))
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
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const directoryDialogVisible = ref(false);
    const directoryDialogTitle = ref("新增目录");
    const directoryForm: Ref<IETLTaskDirectory> = ref(new ETLTaskDirectory());
    const editingDirectoryId: Ref<string | null> = ref(null);

    const showAddDirectoryDialog = (parentId: string | null) => {
      editingDirectoryId.value = null;
      directoryDialogTitle.value = "新增目录";
      directoryForm.value = new ETLTaskDirectory();
      directoryForm.value.parentId = parentId;
      directoryForm.value.taskType = DIRECTORY_TASK_TYPE;
      directoryForm.value.sortOrder = 0;
      directoryDialogVisible.value = true;
    };

    const showEditDirectoryDialog = (directory: IETLTaskDirectory) => {
      editingDirectoryId.value = directory.id || null;
      directoryDialogTitle.value = "重命名目录";
      directoryForm.value = { ...directory };
      directoryDialogVisible.value = true;
    };

    const saveDirectory = async () => {
      if (!directoryForm.value.name || !directoryForm.value.name.trim()) {
        alertService.showError("请输入目录名称");
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
        alertService.showSuccess("目录保存成功");
        await loadDirectories();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const deleteDirectory = async (directory: IETLTaskDirectory) => {
      try {
        await ElMessageBox.confirm(
          `确定要删除目录「${directory.name || ""}」吗？删除后该目录下的子目录与编排将移动到根目录。`,
          "删除目录",
          {
            confirmButtonText: "删除",
            cancelButtonText: "取消",
            type: "warning",
          },
        );
      } catch {
        return;
      }
      try {
        await etlTaskDirectoryService().delete(directory.id!);
        if (selectedDirectoryId.value === directory.id) {
          selectAllJobs();
        }
        alertService.showSuccess("目录删除成功");
        await loadDirectories();
        await retrieveDagJobs();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const handleMoreCommand = (command: string, job: IJob) => {
      if (command === "move") {
        openMoveDialog(job);
      } else if (command === "delete") {
        prepareRemove(job);
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
        await dagJobService().moveToDirectory(moveJob.value.id, moveTargetDirectoryId.value || null);
        alertService.showSuccess("编排已移动");
        closeMoveDialog();
        await retrieveDagJobs();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const createRouteLocation = computed(() => ({
      name: "DagJobDesignNew",
      query: selectedDirectoryId.value ? { directoryId: selectedDirectoryId.value } : {},
    }));

    const clear = () => {
      page.value = 1;
    };

    const sort = (): Array<any> => {
      const result = [`${propOrder.value},${reverse.value ? "desc" : "asc"}`];
      if (propOrder.value !== "id") {
        result.push("id");
      }
      return result;
    };

    const retrieveDagJobs = async () => {
      isFetching.value = true;
      try {
        const paginationQuery: any = {
          page: page.value - 1,
          size: itemsPerPage.value,
          sort: sort(),
        };
        if (selectedDirectoryId.value) {
          paginationQuery.directoryId = selectedDirectoryId.value;
        }
        const res = await dagJobService().retrieveDagJobs(paginationQuery);
        totalItems.value = Number(res.headers["x-total-count"]);
        queryCount.value = totalItems.value;
        dagJobs.value = res.data;
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        isFetching.value = false;
      }
    };

    const handleSyncList = async () => {
      await loadDirectories();
      await retrieveDagJobs();
    };

    onMounted(async () => {
      await loadDirectories();
      await retrieveDagJobs();
    });

    watch(selectedDirectoryId, async () => {
      if (page.value === 1) {
        await retrieveDagJobs();
      } else {
        clear();
      }
    });

    const removeId: Ref<string> = ref(null);
    const removeEntity = ref<any>(null);
    const prepareRemove = (instance: IJob) => {
      removeId.value = instance.id;
      removeEntity.value.show();
    };
    const closeDialog = () => {
      removeEntity.value.hide();
    };
    const removeDagJob = async () => {
      try {
        await dagJobService().delete(removeId.value);
        alertService.showInfo("任务编排已删除", { variant: "danger" });
        removeId.value = null;
        retrieveDagJobs();
        closeDialog();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const handleExecuteOnce = async (job: IJob) => {
      try {
        await dagJobService().run(job.id);
        alertService.showInfo(`编排 ${job.jobName} 已触发执行一次`);
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const onlineModal = ref<any>(null);
    const onlineTask: Ref<IJob | null> = ref(null);
    const isOnlineSubmitting = ref(false);

    const handleOnline = (job: IJob) => {
      onlineTask.value = job;
      onlineModal.value?.show(job.cron || "", job.retryCount ?? 0, job.timeoutSeconds ?? 0, job.priority ?? 2);
    };

    const confirmOnline = async (payload: { cron: string; retryCount: number; timeoutSeconds: number; priority: number }) => {
      if (!onlineTask.value?.id) {
        return;
      }
      isOnlineSubmitting.value = true;
      try {
        await dagJobService().online(
          onlineTask.value.id,
          payload.cron,
          payload.retryCount,
          payload.timeoutSeconds,
          payload.priority,
        );
        alertService.showInfo(`编排 ${onlineTask.value.jobName} 已上线调度`);
        onlineModal.value?.hide();
        await retrieveDagJobs();
      } catch (error) {
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
        await ElMessageBox.confirm(
          `确定要将编排「${job.jobName || job.id}」下线吗？下线后将取消定时调度。`,
          "下线确认",
          {
            confirmButtonText: "确认下线",
            cancelButtonText: "取消",
            type: "warning",
          },
        );
      } catch {
        // 用户取消下线
        return;
      }
      try {
        await dagJobService().offline(job.id);
        alertService.showInfo(`编排 ${job.jobName} 已下线`);
        await retrieveDagJobs();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const handleSortChange = (column: {
      prop: string;
      order: "ascending" | "descending" | null;
    }) => {
      if (column.prop && column.order) {
        if (column.order === "ascending") {
          reverse.value = false;
        } else {
          reverse.value = true;
        }
        propOrder.value = column.prop;
      }
    };

    watch([propOrder, reverse, itemsPerPage], async () => {
      if (page.value === 1) {
        await retrieveDagJobs();
      } else {
        clear();
      }
    });

    watch(page, async () => {
      await retrieveDagJobs();
    });

    return {
      dagJobs,
      handleSyncList,
      isFetching,
      retrieveDagJobs,
      directories,
      directoryLoading,
      directoryTree,
      directoryTreeRef,
      treeProps,
      selectedDirectoryId,
      directoryPath,
      loadDirectories,
      selectAllJobs,
      handleDirectoryClick,
      directoryDialogVisible,
      directoryDialogTitle,
      directoryForm,
      editingDirectoryId,
      showAddDirectoryDialog,
      showEditDirectoryDialog,
      saveDirectory,
      deleteDirectory,
      canMoveDirectory,
      moveDirectory,
      handleMoreCommand,
      moveDialogVisible,
      moveJob,
      moveTargetDirectoryId,
      openMoveDialog,
      closeMoveDialog,
      confirmMove,
      createRouteLocation,
      clear,
      ...dateFormat,
      removeId,
      removeEntity,
      prepareRemove,
      closeDialog,
      removeDagJob,
      handleExecuteOnce,
      handleOnline,
      onlineModal,
      onlineTask,
      isOnlineSubmitting,
      confirmOnline,
      handleOffline,
      itemsPerPage,
      queryCount,
      page,
      propOrder,
      reverse,
      totalItems,
      handleSortChange,
    };
  },
});
