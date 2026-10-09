export interface IModelField {
  id?: string;
  modelId?: string | null;
  fieldName?: string | null;
  fieldType?: string | null;
  fieldLength?: number | null;
  fieldPrecision?: number | null;
  fieldScale?: number | null;
  description?: string | null;
  sortOrder?: number | null;
  isPartitionKey?: boolean | null;
  isPrimaryKey?: boolean | null;
  dimensionModelId?: string | null;
  dimensionFieldId?: string | null;
  fieldRole?: string | null;
  levelIndex?: number | null;
  createTime?: Date | null;
  updateTime?: Date | null;
}

export class ModelField implements IModelField {
  constructor(
    public id?: string,
    public modelId?: string | null,
    public fieldName?: string | null,
    public fieldType?: string | null,
    public fieldLength?: number | null,
    public fieldPrecision?: number | null,
    public fieldScale?: number | null,
    public description?: string | null,
    public sortOrder?: number | null,
    public isPartitionKey?: boolean | null,
    public isPrimaryKey?: boolean | null,
    public dimensionModelId?: string | null,
    public dimensionFieldId?: string | null,
    public fieldRole?: string | null,
    public levelIndex?: number | null,
    public createTime?: Date | null,
    public updateTime?: Date | null,
  ) {}
}
