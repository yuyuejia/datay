/** 维度角色：同一张维度表在事实表中扮演的不同业务含义。 */
export interface IDimensionRole {
  id?: string;
  dimensionModelId?: string | null;
  code?: string | null;
  name?: string | null;
  description?: string | null;
  sortOrder?: number | null;
  createTime?: Date | null;
  updateTime?: Date | null;
}

export class DimensionRole implements IDimensionRole {
  constructor(
    public id?: string,
    public dimensionModelId?: string | null,
    public code?: string | null,
    public name?: string | null,
    public description?: string | null,
    public sortOrder?: number | null,
    public createTime?: Date | null,
    public updateTime?: Date | null,
  ) {}
}
