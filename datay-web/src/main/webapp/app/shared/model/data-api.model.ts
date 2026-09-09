export interface IDataApi {
  id?: number;
  name?: string | null;
  code?: string | null;
  description?: string | null;
  dataSourceId?: number | null;
  sourceType?: string | null;
  schemaName?: string | null;
  tableName?: string | null;
  sqlText?: string | null;
  status?: string | null;
  tenantId?: string | null;
  updateTime?: Date | null;
  createTime?: Date | null;
}

export class DataApi implements IDataApi {
  constructor(
    public id?: number,
    public name?: string | null,
    public code?: string | null,
    public description?: string | null,
    public dataSourceId?: number | null,
    public sourceType?: string | null,
    public schemaName?: string | null,
    public tableName?: string | null,
    public sqlText?: string | null,
    public status?: string | null,
    public tenantId?: string | null,
    public updateTime?: Date | null,
    public createTime?: Date | null,
  ) {}
}

export const DATA_API_SOURCE_TYPE_TABLE = 'TABLE';
export const DATA_API_SOURCE_TYPE_SQL = 'SQL';
export const DATA_API_STATUS_ENABLED = 'ENABLED';
export const DATA_API_STATUS_DISABLED = 'DISABLED';
