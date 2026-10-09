export interface IDpTable {
  id?: string;
  name?: string | null;
  schemaName?: string | null;
  description?: string | null;
  sourceDBType?: string | null;
  sourceId?: string | null;
  sourceSchema?: string | null;
  sourceTable?: string | null;
  sqlContent?: string | null;
  fileType?: string | null;
  filePath?: string | null;
  updateTime?: Date | null;
  createTime?: Date | null;
  tenantId?: string | null;
}

export class DpTable implements IDpTable {
  constructor(
    public id?: string,
    public name?: string | null,
    public schemaName?: string | null,
    public description?: string | null,
    public sourceDBType?: string | null,
    public sourceId?: string | null,
    public sourceSchema?: string | null,
    public sourceTable?: string | null,
    public sqlContent?: string | null,
    public fileType?: string | null,
    public filePath?: string | null,
    public updateTime?: Date | null,
    public createTime?: Date | null,
    public tenantId?: string | null,
  ) {}
}
