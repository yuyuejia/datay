export interface IJobInstance {
  id?: string;
  instanceCode?: string | null;
  jobName?: string | null;
  jobCode?: string | null;
  type?: string | null;
  retryCount?: number | null;
  timeoutSeconds?: number | null;
  jobContext?: string | null;
  status?: string | null;
  jobMessage?: string | null;
  execNode?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  createTime?: Date | null;
  project?: string | null;
  tenantId?: string | null;
  parentInstanceCode?: string | null;
  parentJobName?: string | null;
}

export class JobInstance implements IJobInstance {
  constructor(
    public id?: string,
    public instanceCode?: string | null,
    public jobName?: string | null,
    public jobCode?: string | null,
    public type?: string | null,
    public jobContext?: string | null,
    public status?: string | null,
    public jobMessage?: string | null,
    public execNode?: string | null,
    public startTime?: string | null,
    public endTime?: string | null,
    public createTime?: Date | null,
    public project?: string | null,
    public tenantId?: string | null,
    public parentInstanceCode?: string | null,
    public parentJobName?: string | null,
    public retryCount?: number | null,
    public timeoutSeconds?: number | null,
  ) {}
}
