export interface IDataModel {
  id?: number;
  name?: string | null;
  code?: string | null;
  description?: string | null;
  directoryId?: number | null;
  modelType?: string | null;
  dimensionKind?: string | null;
  levelCount?: number | null;
  isRegistered?: boolean | null;
  project?: string | null;
  tenantId?: string | null;
  dataSourceId?: number | null;
  schemaName?: string | null;
  tableName?: string | null;
  createTime?: Date | null;
  updateTime?: Date | null;
}

export class DataModel implements IDataModel {
  constructor(
    public id?: number,
    public name?: string | null,
    public code?: string | null,
    public description?: string | null,
    public directoryId?: number | null,
    public modelType?: string | null,
    public dimensionKind?: string | null,
    public levelCount?: number | null,
    public isRegistered?: boolean | null,
    public project?: string | null,
    public tenantId?: string | null,
    public dataSourceId?: number | null,
    public schemaName?: string | null,
    public tableName?: string | null,
    public createTime?: Date | null,
    public updateTime?: Date | null,
  ) {}
}
