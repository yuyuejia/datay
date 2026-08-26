export interface IJobDepend {
  id?: number;
  parentJobCode?: string | null;
  childJobCode?: string | null;
  jobCode?: string | null;
  lastInterval?: number | null;
  createTime?: Date | null;
  tenantId?: string | null;
}

export class JobDepend implements IJobDepend {
  constructor(
    public id?: number,
    public parentJobCode?: string | null,
    public childJobCode?: string | null,
    public jobCode?: string | null,
    public lastInterval?: number | null,
    public createTime?: Date | null,
    public tenantId?: string | null,
  ) {}
}
