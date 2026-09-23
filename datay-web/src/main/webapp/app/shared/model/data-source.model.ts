export interface IDataSource {
  id?: number;
  name?: string | null;
  description?: string | null;
  type?: string | null;
  version?: string | null;
  url?: string | null;
  hostname?: string | null;
  port?: string | null;
  schemaName?: string | null;
  username?: string | null;
  password?: string | null;
  updateTime?: Date | null;
  createTime?: Date | null;
  tenantId?: string | null;
  extraParams?: Record<string, string> | null;
}

export class DataSource implements IDataSource {
  constructor(
    public id?: number,
    public name?: string | null,
    public description?: string | null,
    public type?: string | null,
    public version?: string | null,
    public url?: string | null,
    public hostname?: string | null,
    public port?: string | null,
    public schemaName?: string | null,
    public username?: string | null,
    public password?: string | null,
    public updateTime?: Date | null,
    public createTime?: Date | null,
    public tenantId?: string | null,
    public extraParams?: Record<string, string> | null,
  ) {}
}
