export interface IJob {
  id?: number;
  jobName?: string | null;
  jobGroup?: string | null;
  type?: string | null;
  cron?: string | null;
  jobContext?: string | null;
  status?: string | null;
  updateTime?: Date | null;
  createTime?: Date | null;
  project?: string | null;
  tenantId?: string | null;
}

export class Job implements IJob {
  constructor(
    public id?: number,
    public jobName?: string | null,
    public jobGroup?: string | null,
    public type?: string | null,
    public cron?: string | null,
    public jobContext?: string | null,
    public status?: string | null,
    public updateTime?: Date | null,
    public createTime?: Date | null,
    public project?: string | null,
    public tenantId?: string | null,
  ) {}
}
