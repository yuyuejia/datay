export interface IMetricDirectory {
  id?: number;
  name?: string | null;
  parentId?: number | null;
  sortOrder?: number | null;
  createTime?: Date | null;
  updateTime?: Date | null;
}

export class MetricDirectory implements IMetricDirectory {
  constructor(
    public id?: number,
    public name?: string | null,
    public parentId?: number | null,
    public sortOrder?: number | null,
    public createTime?: Date | null,
    public updateTime?: Date | null,
  ) {}
}
