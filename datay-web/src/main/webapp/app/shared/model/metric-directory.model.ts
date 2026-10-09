export interface IMetricDirectory {
  id?: string;
  name?: string | null;
  parentId?: string | null;
  sortOrder?: number | null;
  createTime?: Date | null;
  updateTime?: Date | null;
}

export class MetricDirectory implements IMetricDirectory {
  constructor(
    public id?: string,
    public name?: string | null,
    public parentId?: string | null,
    public sortOrder?: number | null,
    public createTime?: Date | null,
    public updateTime?: Date | null,
  ) {}
}
