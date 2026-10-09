import {
  type Ref,
  defineComponent,
  inject,
  onMounted,
  onUnmounted,
  ref,
  watch,
} from "vue";

import SqlJobService from "./sql-job.service";
import { type IJob } from "@/shared/model/job.model";
import { useDateFormat } from "@/shared/composables";
import { useAlertService } from "@/shared/alert/alert.service";

export default defineComponent({
  name: "SqlJob",
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

    const handleOnline = async (job: IJob) => {
      try {
        await sqlJobService().online(job.id);
        alertService.showInfo(`SQL 任务 ${job.jobName} 已上线调度`);
        await retrieveSqlJobs();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const handleOffline = async (job: IJob) => {
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
