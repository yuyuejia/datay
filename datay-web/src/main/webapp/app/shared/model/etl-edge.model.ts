export interface IETLEdge {
  id?: number;
  taskId?: string | null;
  name?: string | null;
  code?: string | null;
  source?: string | null;
  target?: string | null;
  config?: string | null;
  status?: string | null;
  tenantId?: string | null;
  dr?: number | null;
}

export class ETLEdge implements IETLEdge {
  constructor(
    public id?: number,
    public taskId?: string | null,
    public name?: string | null,
    public code?: string | null,
    public source?: string | null,
    public target?: string | null,
    public config?: string | null,
    public status?: string | null,
    public tenantId?: string | null,
    public dr?: number | null,
  ) {}
}
