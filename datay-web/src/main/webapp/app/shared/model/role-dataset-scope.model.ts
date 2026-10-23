export interface IRoleDatasetScopeCondition {
  fieldName?: string;
  operator?: string;
  value?: string;
  valueEnd?: string;
}

export interface IRoleDatasetScopeFilterConfig {
  conditions?: IRoleDatasetScopeCondition[];
}

export interface IRoleDatasetScope {
  id?: string;
  roleName?: string;
  datasetId?: string;
  filterConfig?: string;
  enabled?: boolean;
  createTime?: Date;
  updateTime?: Date;
}

export class RoleDatasetScope implements IRoleDatasetScope {
  constructor(
    public id?: string,
    public roleName?: string,
    public datasetId?: string,
    public filterConfig?: string,
    public enabled?: boolean,
    public createTime?: Date,
    public updateTime?: Date,
  ) {
    this.enabled = this.enabled ?? true;
  }
}
