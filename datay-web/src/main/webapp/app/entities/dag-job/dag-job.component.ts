import { type Ref, defineComponent, inject, onMounted, ref, watch } from "vue";
import { ElMessageBox } from "element-plus";

import DagJobService from "./dag-job.service";
import TaskOnlineModal from "@/components/TaskOnlineModal.vue";
import { type IJob } from "@/shared/model/job.model";
import { useDateFormat } from "@/shared/composables";
import { useAlertService } from "@/shared/alert/alert.service";

export default defineComponent({
  name: "DagJob",
  components: {
    TaskOnlineModal,
  },
  setup() {
    const dateFormat = useDateFormat();
    const dagJobService = inject("dagJobService", () => new DagJobService());
    const alertService = inject("alertService", () => useAlertService(), true);

    const itemsPerPage = ref(20);
    const queryCount: Ref<number> = ref(null);
    const page: Ref<number> = ref(1);
    const propOrder = ref("id");
    const reverse = ref(false);
    const totalItems = ref(0);
    const dagJobs: Ref<IJob[]> = ref([]);
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

    const retrieveDagJobs = async () => {
      isFetching.value = true;
      try {
        const paginationQuery = {
          page: page.value - 1,
          size: itemsPerPage.value,
          sort: sort(),
        };
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

    const handleSyncList = () => {
      retrieveDagJobs();
    };

    onMounted(async () => {
      await retrieveDagJobs();
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
      onlineModal.value?.show(job.cron || "", job.retryCount ?? 0, job.timeoutSeconds ?? 0);
    };

    const confirmOnline = async (payload: { cron: string; retryCount: number; timeoutSeconds: number }) => {
      if (!onlineTask.value?.id) {
        return;
      }
      isOnlineSubmitting.value = true;
      try {
        await dagJobService().online(onlineTask.value.id, payload.cron, payload.retryCount, payload.timeoutSeconds);
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
