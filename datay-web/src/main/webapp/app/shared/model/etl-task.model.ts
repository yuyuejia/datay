export interface IETLTask {
  id?: string;
  taskName?: string | null;
  taskCode?: string | null;
  jobId?: string | null;
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
    public id?: string,
    public taskName?: string | null,
    public taskCode?: string | null,
    public jobId?: string | null,
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

/** 导出的单个 ETL 任务 JSON（与后端 ETLTaskTransferDTO 对应）。 */
export interface IETLTaskTransfer {
  format?: string;
  version?: string;
  task?: {
    taskName?: string | null;
    taskCode?: string | null;
    taskDesc?: string | null;
    dir?: string | null;
    type?: string | null;
    cron?: string | null;
    project?: string | null;
    nodes?: any[];
    edges?: any[];
  };
  references?: {
    dataSources?: Array<{ oldId?: string; name?: string | null; type?: string | null; url?: string | null }>;
    models?: Array<{ oldId?: string; code?: string | null; name?: string | null }>;
  };
}

/** ETL 任务导入结果（与后端 ETLTaskImportResultDTO 对应）。 */
export interface IETLTaskImportResult {
  taskId?: string;
  taskCode?: string;
  taskName?: string;
  renamed?: boolean;
  dataSources?: Array<{ oldId?: string; label?: string; matched?: boolean; newId?: string }>;
  models?: Array<{ oldId?: string; label?: string; matched?: boolean; newId?: string }>;
  warnings?: string[];
}
