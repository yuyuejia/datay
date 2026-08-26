export interface IDataSyncTableConfig {
  id?: number;
  syncTask?: string | null;
  srcDatasource?: string | null;
  srcSchemaName?: string | null;
  srcTableName?: string | null;
  srcColPks?: string | null;
  desDatasource?: string | null;
  desSchemaName?: string | null;
  desTableName?: string | null;
  desColPks?: string | null;
  jobDesc?: string | null;
  columeConfig?: string | null;
  updateTime?: Date | null;
  createTime?: Date | null;
  project?: string | null;
  tenantId?: string | null;
  dr?: number | null;
}

export class DataSyncTableConfig implements IDataSyncTableConfig {
  constructor(
    public id?: number,
    public syncTask?: string | null,
    public srcDatasource?: string | null,
    public srcSchemaName?: string | null,
    public srcTableName?: string | null,
    public srcColPks?: string | null,
    public desDatasource?: string | null,
    public desSchemaName?: string | null,
    public desTableName?: string | null,
    public desColPks?: string | null,
    public jobDesc?: string | null,
    public columeConfig?: string | null,
    public updateTime?: Date | null,
    public createTime?: Date | null,
    public project?: string | null,
    public tenantId?: string | null,
    public dr?: number | null,
  ) {}
}
