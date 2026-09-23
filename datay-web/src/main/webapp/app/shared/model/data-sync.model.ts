import type { DataSyncTableConfig } from "@/shared/model/data-sync-table-config.model";

export interface IDataSync {
  id?: number;
  jobName?: string | null;
  jobCode?: string | null;
  jobDesc?: string | null;
  dir?: string | null;
  type?: string | null;
  source?: string | null;
  target?: string | null;
  cron?: string | null;
  jobContext?: string | null;
  status?: string | null;
  lastStatus?: string | null;
  updateTime?: Date | null;
  createTime?: Date | null;
  project?: string | null;
  tenantId?: string | null;
  dr?: number | null;
  // 添加 selectedTables 字段，类型为 DataSyncTableConfig 数组
  selectedTables?: DataSyncTableConfig[];
}

export class DataSync implements IDataSync {
  constructor(
    public id?: number,
    public jobName?: string | null,
    public jobCode?: string | null,
    public jobDesc?: string | null,
    public dir?: string | null,
    public type?: string | null,
    public source?: string | null,
    public target?: string | null,
    public cron?: string | null,
    public jobContext?: string | null,
    public status?: string | null,
    public lastStatus?: string | null,
    public updateTime?: Date | null,
    public createTime?: Date | null,
    public project?: string | null,
    public tenantId?: string | null,
    public dr?: number | null,
    // 初始化 selectedTables 字段
    public selectedTables: DataSyncTableConfig[] = [],
  ) {}
}
