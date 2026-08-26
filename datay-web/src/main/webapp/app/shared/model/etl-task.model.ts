export interface IETLTask {
  id?: number;
  taskName?: string | null;
  taskCode?: string | null;
  jobId?: number | null;
  taskDesc?: string | null;
  dir?: string | null;
  type?: string | null;
  cron?: string | null;
  jobContext?: string | null;
  status?: string | null;
  lastStatus?: string | null;
  updateTime?: Date | null;
  createTime?: Date | null;
  creater?: string | null;
  project?: string | null;
  tenantId?: string | null;
  dr?: number | null;
}

export class ETLTask implements IETLTask {
  constructor(
    public id?: number,
    public taskName?: string | null,
    public taskCode?: string | null,
    public jobId?: number | null,
    public taskDesc?: string | null,
    public dir?: string | null,
    public type?: string | null,
    public cron?: string | null,
    public jobContext?: string | null,
    public status?: string | null,
    public lastStatus?: string | null,
    public updateTime?: Date | null,
    public createTime?: Date | null,
    public creater?: string | null,
    public project?: string | null,
    public tenantId?: string | null,
    public dr?: number | null,
    public nodes?: any[] | null,
    public edges?: any[] | null,
  ) {}
}
