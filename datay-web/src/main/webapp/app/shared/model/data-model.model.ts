export interface IDataModel {
  id?: number;
  name?: string | null;
  code?: string | null;
  description?: string | null;
  directoryId?: number | null;
  modelType?: string | null;
  dimensionKind?: string | null;
  levelCount?: number | null;
  timeFieldName?: string | null;
  /** 时间维度选中的时间粒度（逗号分隔，如 YEAR,QUARTER,MONTH,DAY）。 */
  timeLevels?: string | null;
  /** 时间维度预置数据起始日期（yyyy-MM-dd）。 */
  timeStart?: string | null;
  /** 时间维度预置数据结束日期（yyyy-MM-dd）。 */
  timeEnd?: string | null;
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
    public timeFieldName?: string | null,
    public timeLevels?: string | null,
    public timeStart?: string | null,
    public timeEnd?: string | null,
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
