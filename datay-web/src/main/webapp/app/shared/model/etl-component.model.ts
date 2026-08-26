export interface IETLComponent {
  id?: number;
  name?: string | null;
  code?: string | null;
  desc?: string | null;
  group?: string | null;
  type?: string | null;
  config?: string | null;
  status?: string | null;
  updateTime?: Date | null;
  createTime?: Date | null;
  creater?: string | null;
  tenantId?: string | null;
  dr?: number | null;
}

export class ETLComponent implements IETLComponent {
  constructor(
    public id?: number,
    public name?: string | null,
    public code?: string | null,
    public desc?: string | null,
    public group?: string | null,
    public type?: string | null,
    public config?: string | null,
    public status?: string | null,
    public updateTime?: Date | null,
    public createTime?: Date | null,
    public creater?: string | null,
    public tenantId?: string | null,
    public dr?: number | null,
  ) {}
}
