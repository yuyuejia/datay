export interface IETLNode {
  id?: number;
  taskId?: string | null;
  label?: string | null;
  code?: string | null;
  desc?: string | null;
  type?: string | null;
  config?: string | null;
  xAxis?: string | null;
  yAxis?: string | null;
  status?: string | null;
  updateTime?: Date | null;
  createTime?: Date | null;
  tenantId?: string | null;
  dr?: number | null;
}

export class ETLNode implements IETLNode {
  constructor(
    public id?: number,
    public taskId?: string | null,
    public label?: string | null,
    public code?: string | null,
    public desc?: string | null,
    public type?: string | null,
    public config?: string | null,
    public xAxis?: string | null,
    public yAxis?: string | null,
    public status?: string | null,
    public updateTime?: Date | null,
    public createTime?: Date | null,
    public tenantId?: string | null,
    public dr?: number | null,
  ) {}
}
