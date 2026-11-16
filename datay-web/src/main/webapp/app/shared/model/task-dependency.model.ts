export interface ITaskDependencyFormItem {
  parentJobCode: string;
  lastIntervalMinutes: number;
}

export interface ITaskOnlineDependencyPayload {
  parentJobCode: string;
  lastInterval: number;
}

export interface ITaskOnlineShowPayload {
  jobId?: string;
  cron?: string;
  retryCount?: number;
  timeoutSeconds?: number;
  priority?: number;
}

export interface ITaskOnlineConfirmPayload {
  cron: string;
  retryCount: number;
  timeoutSeconds: number;
  priority: number;
  dependencies: ITaskOnlineDependencyPayload[];
}
