import { type Ref, defineComponent, inject, onMounted, ref, watch } from 'vue';

import JobInstanceService from './job-instance.service';
import { type IJobInstance } from '@/shared/model/job-instance.model';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'JobInstance',
  setup() {
    const dateFormat = useDateFormat();
    const jobInstanceService = inject('jobInstanceService', () => new JobInstanceService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const itemsPerPage = ref(20);
    const queryCount: Ref<number> = ref(null);
    const page: Ref<number> = ref(1);
    const propOrder = ref('createTime');
    const reverse = ref(true);
    const totalItems = ref(0);

    const jobInstances: Ref<IJobInstance[]> = ref([]);

    const isFetching = ref(false);

    // 新增：日志相关状态
    const logContent = ref('');
    const isLogLoading = ref(false);
    const logEntity = ref<any>(null);
    const currentLogInstance: Ref<IJobInstance> = ref<IJobInstance>({});
    const logOffset = ref(0);
    const hasMoreLog = ref(false);
    const logFileSize = ref(0);

    // 新增：终止任务相关状态
    const stopEntity = ref<any>(null);
    const currentStopInstance: Ref<IJobInstance> = ref<IJobInstance>({});

    const clear = () => {
      page.value = 1;
    };

    // 新增：状态标签类型映射（成功绿、失败红、终止黄、其他灰）
    const getStatusType = (status?: string | null): 'success' | 'danger' | 'warning' | 'info' => {
      const normalized = status?.toUpperCase();
      switch (normalized) {
        case 'SUCCESSFUL':
        case 'SUCCESS':
          return 'success';
        case 'FAILED':
          return 'danger';
        case 'INTERRUPTED':
        case 'STOPPED':
        case 'KILLED':
        case 'TERMINATED':
          return 'warning';
        default:
          return 'info';
      }
    };

    const sort = (): Array<any> => {
      const result = [`${propOrder.value},${reverse.value ? 'desc' : 'asc'}`];
      if (propOrder.value !== 'id') {
        result.push('id');
      }
      return result;
    };

    const retrieveJobInstances = async () => {
      isFetching.value = true;
      try {
        const paginationQuery = {
          page: page.value - 1,
          size: itemsPerPage.value,
          sort: sort(),
        };
        const res = await jobInstanceService().retrieve(paginationQuery);
        totalItems.value = Number(res.headers['x-total-count']);
        queryCount.value = totalItems.value;
        jobInstances.value = res.data;
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        isFetching.value = false;
      }
    };

    const handleSyncList = () => {
      retrieveJobInstances();
    };

    onMounted(async () => {
      await retrieveJobInstances();
    });

    const removeId: Ref<number> = ref(null);
    const removeEntity = ref<any>(null);
    const prepareRemove = (instance: IJobInstance) => {
      removeId.value = instance.id;
      removeEntity.value.show();
    };
    const closeDialog = () => {
      removeEntity.value.hide();
    };
    const removeJobInstance = async () => {
      try {
        await jobInstanceService().delete(removeId.value);
        const message = `A JobInstance is deleted with identifier ${removeId.value}`;
        alertService.showInfo(message, { variant: 'danger' });
        removeId.value = null;
        retrieveJobInstances();
        closeDialog();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    // 新增：准备终止任务
    const prepareStopJobInstance = (instance: IJobInstance) => {
      currentStopInstance.value = instance;
      stopEntity.value.show();
    };
    // 新增：终止任务实例
    const stopJobInstance = async () => {
      if (!currentStopInstance.value) return;

      try {
        await jobInstanceService().stopJobInstance(currentStopInstance.value.instanceCode);
        const message = `任务实例 ${currentStopInstance.value.instanceCode} 已成功终止`;
        alertService.showInfo(message, { variant: 'success' });
        retrieveJobInstances(); // 刷新列表
        closeStopDialog();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };
    // 新增：关闭终止对话框
    const closeStopDialog = () => {
      stopEntity.value.hide();
      currentStopInstance.value = null;
    };

    // 新增：准备查看日志
    const prepareViewLog = async (instance: IJobInstance) => {
      currentLogInstance.value = instance;
      logContent.value = '';
      logOffset.value = 0;
      hasMoreLog.value = false;
      logFileSize.value = 0;

      try {
        // 先获取日志文件信息
        // const logInfo = await jobInstanceService().getLogInfo(instance.jobCode, instance.instanceCode);
        // if (logInfo.success && logInfo.exists) {
        //   logFileSize.value = logInfo.fileSize;
        //   await loadLogContent();
        // } else {
        //   alertService.showInfo('日志文件不存在', { variant: 'warning' });
        // }
        await loadLogContent();
      } catch (error) {
        alertService.showHttpError(error.response);
      }

      logEntity.value.show();
    };

    // 新增：加载日志内容
    const loadLogContent = async () => {
      if (!currentLogInstance.value) return;

      isLogLoading.value = true;
      try {
        const logData = await jobInstanceService().getTaskLog(
          currentLogInstance.value.jobCode,
          currentLogInstance.value.instanceCode,
          logOffset.value,
        );

        if (logData.success) {
          logContent.value += logData.content;
          logOffset.value = logData.newOffset;
          hasMoreLog.value = logData.hasMore;
        } else {
          alertService.showInfo(logData.message, { variant: 'warning' });
        }
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        isLogLoading.value = false;
      }
    };

    // 新增：加载更多日志
    const loadMoreLog = async () => {
      await loadLogContent();
      // if (hasMoreLog.value) {
      //   await loadLogContent();
      // }
    };

    // 新增：关闭日志对话框
    const closeLogDialog = () => {
      logEntity.value.hide();
      currentLogInstance.value = null;
      logContent.value = '';
      logOffset.value = 0;
      hasMoreLog.value = false;
      logFileSize.value = 0;
    };

    // 新增：复制日志内容到剪贴板
    const copyLogToClipboard = () => {
      navigator.clipboard
        .writeText(logContent.value)
        .then(() => {
          alertService.showInfo('日志内容已复制到剪贴板', { variant: 'success' });
        })
        .catch(() => {
          alertService.showInfo('复制失败，请手动选择文本复制', { variant: 'warning' });
        });
    };

    // 新增：下载日志文件
    const downloadLog = () => {
      if (!logContent.value) return;

      const blob = new Blob([logContent.value], { type: 'text/plain' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `job-log-${currentLogInstance.value.jobCode}-${currentLogInstance.value.instanceCode}.txt`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    };

    const changeOrder = (newOrder: string) => {
      if (propOrder.value === newOrder) {
        reverse.value = !reverse.value;
      } else {
        reverse.value = false;
      }
      propOrder.value = newOrder;
    };

    // 新增：el-table 排序事件处理
    // const handleSortChange = (sortInfo: any) => {
    //   if (sortInfo.prop) {
    //     propOrder.value = sortInfo.prop;
    //     reverse.value = sortInfo.order === 'descending';
    //     retrieveJobInstances();
    //   }
    // };
    const handleSortChange = (column: { prop: string; order: 'ascending' | 'descending' | null }) => {
      if (column.prop && column.order) {
        if (column.order === 'ascending') {
          reverse.value = false;
        } else {
          reverse.value = true;
        }
        propOrder.value = column.prop;
      }
    };

    // 新增：日期时间格式化函数
    const formatDateTime = (timestamp: number | string): string => {
      if (!timestamp) return '';

      // 如果是字符串，尝试转换为数字
      const timeValue = typeof timestamp === 'string' ? parseInt(timestamp, 10) : timestamp;

      // 检查是否为有效的时间戳
      if (isNaN(timeValue) || timeValue <= 0) return '';

      try {
        const date = new Date(timeValue);
        // 检查日期是否有效
        if (isNaN(date.getTime())) return '';

        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const seconds = String(date.getSeconds()).padStart(2, '0');

        return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
      } catch (error) {
        console.error('日期格式化错误:', error);
        return '';
      }
    };

    // Whenever order changes, reset the pagination
    watch([propOrder, reverse], async () => {
      if (page.value === 1) {
        // first page, retrieve new data
        await retrieveJobInstances();
      } else {
        // reset the pagination
        clear();
      }
    });

    // Whenever page changes, switch to the new page.
    watch(page, async () => {
      await retrieveJobInstances();
    });

    return {
      jobInstances,
      handleSyncList,
      isFetching,
      retrieveJobInstances,
      clear,
      ...dateFormat,
      removeId,
      removeEntity,
      prepareRemove,
      closeDialog,
      removeJobInstance,
      itemsPerPage,
      queryCount,
      page,
      propOrder,
      reverse,
      totalItems,
      changeOrder,
      // 新增：日志相关方法和状态
      logContent,
      isLogLoading,
      logEntity,
      currentLogInstance,
      hasMoreLog,
      logFileSize,
      prepareViewLog,
      loadMoreLog,
      closeLogDialog,
      copyLogToClipboard,
      downloadLog,
      handleSortChange,
      formatDateTime,
      getStatusType,
      prepareStopJobInstance,
      stopJobInstance,
      closeStopDialog,
      stopEntity,
      currentStopInstance,
    };
  },
});
