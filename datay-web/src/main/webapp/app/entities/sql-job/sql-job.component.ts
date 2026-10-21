import {
  type Ref,
  defineComponent,
  inject,
  onMounted,
  onUnmounted,
  ref,
  watch,
} from "vue";
import { ElMessageBox } from "element-plus";

import SqlJobService from "./sql-job.service";
import TaskOnlineModal from "@/components/TaskOnlineModal.vue";
import { type IJob } from "@/shared/model/job.model";
import { useDateFormat } from "@/shared/composables";
import { useAlertService } from "@/shared/alert/alert.service";

export default defineComponent({
  name: "SqlJob",
  components: {
    TaskOnlineModal,
  },
  setup() {
    const dateFormat = useDateFormat();
    const sqlJobService = inject("sqlJobService", () => new SqlJobService());
    const alertService = inject("alertService", () => useAlertService(), true);

    const itemsPerPage = ref(20);
    const queryCount: Ref<number> = ref(null);
    const page: Ref<number> = ref(1);
    const propOrder = ref("id");
    const reverse = ref(false);
    const totalItems = ref(0);
    const search = ref("");
    const sqlJobs: Ref<IJob[]> = ref([]);
    const isFetching = ref(false);

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

    const retrieveSqlJobs = async () => {
      isFetching.value = true;
      try {
        const paginationQuery = {
          page: page.value - 1,
          size: itemsPerPage.value,
          sort: sort(),
          search: search.value,
        };
        const res = await sqlJobService().retrieveSqlJobs(paginationQuery);
        totalItems.value = Number(res.headers["x-total-count"]);
        queryCount.value = totalItems.value;
        sqlJobs.value = res.data;
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        isFetching.value = false;
      }
    };

    let searchTimer: ReturnType<typeof setTimeout> | null = null;

    watch(search, () => {
      if (searchTimer) {
        clearTimeout(searchTimer);
      }
      searchTimer = setTimeout(() => {
        if (page.value === 1) {
          retrieveSqlJobs();
        } else {
          clear();
        }
      }, 300);
    });

    onUnmounted(() => {
      if (searchTimer) {
        clearTimeout(searchTimer);
      }
    });

    const handleSyncList = () => {
      retrieveSqlJobs();
    };

    onMounted(async () => {
      await retrieveSqlJobs();
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
    const removeSqlJob = async () => {
      try {
        await sqlJobService().delete(removeId.value);
        alertService.showInfo("SQL 任务已删除", { variant: "danger" });
        removeId.value = null;
        retrieveSqlJobs();
        closeDialog();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const handleExecuteOnce = async (job: IJob) => {
      try {
        await sqlJobService().run(job.id);
        alertService.showInfo(`SQL 任务 ${job.jobName} 已触发执行一次`);
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const onlineModal = ref<any>(null);
    const onlineTask: Ref<IJob | null> = ref(null);
    const isOnlineSubmitting = ref(false);

    const handleOnline = (job: IJob) => {
      onlineTask.value = job;
      onlineModal.value?.show(job.cron || "", job.retryCount ?? 0, job.timeoutSeconds ?? 0);
    };

    const confirmOnline = async (payload: { cron: string; retryCount: number; timeoutSeconds: number }) => {
      if (!onlineTask.value?.id) {
        return;
      }
      isOnlineSubmitting.value = true;
      try {
        await sqlJobService().online(onlineTask.value.id, payload.cron, payload.retryCount, payload.timeoutSeconds);
        alertService.showInfo(`SQL 任务 ${onlineTask.value.jobName} 已上线调度`);
        onlineModal.value?.hide();
        await retrieveSqlJobs();
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
          `确定要将 SQL 任务「${job.jobName || job.id}」下线吗？下线后将取消定时调度。`,
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
        await sqlJobService().offline(job.id);
        alertService.showInfo(`SQL 任务 ${job.jobName} 已下线`);
        await retrieveSqlJobs();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const handleSortChange = (column: {
      prop: string;
      order: "ascending" | "descending" | null;
    }) => {
      if (column.prop && column.order) {
        reverse.value = column.order !== "ascending";
        propOrder.value = column.prop;
      }
    };

    watch([propOrder, reverse, itemsPerPage], async () => {
      if (page.value === 1) {
        await retrieveSqlJobs();
      } else {
        clear();
      }
    });

    watch(page, async () => {
      await retrieveSqlJobs();
    });

    return {
      sqlJobs,
      search,
      handleSyncList,
      isFetching,
      retrieveSqlJobs,
      clear,
      ...dateFormat,
      removeId,
      removeEntity,
      prepareRemove,
      closeDialog,
      removeSqlJob,
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
