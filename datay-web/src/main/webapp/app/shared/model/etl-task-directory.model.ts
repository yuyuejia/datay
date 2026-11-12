export interface IETLTaskDirectory {
  id?: string;
  name?: string | null;
  parentId?: string | null;
  taskType?: string | null;
  sortOrder?: number | null;
  createTime?: Date | null;
  updateTime?: Date | null;
}

export class ETLTaskDirectory implements IETLTaskDirectory {
  constructor(
    public id?: string,
    public name?: string | null,
    public parentId?: string | null,
    public taskType?: string | null,
    public sortOrder?: number | null,
    public createTime?: Date | null,
    public updateTime?: Date | null,
  ) {}
}
