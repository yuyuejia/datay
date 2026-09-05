export interface IModelDirectory {
  id?: number;
  name?: string | null;
  parentId?: number | null;
  sortOrder?: number | null;
  createTime?: Date | null;
  updateTime?: Date | null;
}

export class ModelDirectory implements IModelDirectory {
  constructor(
    public id?: number,
    public name?: string | null,
    public parentId?: number | null,
    public sortOrder?: number | null,
    public createTime?: Date | null,
    public updateTime?: Date | null,
  ) {}
}
