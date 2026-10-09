export interface IRoleScopeCondition {
  type?: string;
  dimensionFieldName?: string;
  operator?: string;
  value?: string;
  valueEnd?: string;
}

export interface IRoleScopeFilterConfig {
  conditions?: IRoleScopeCondition[];
}

export interface IRoleDataScope {
  id?: string;
  roleName?: string;
  dimensionModelId?: string;
  filterConfig?: string;
  enabled?: boolean;
  createTime?: Date;
  updateTime?: Date;
}

export class RoleDataScope implements IRoleDataScope {
  constructor(
    public id?: string,
    public roleName?: string,
    public dimensionModelId?: string,
    public filterConfig?: string,
    public enabled?: boolean,
    public createTime?: Date,
    public updateTime?: Date,
  ) {
    this.enabled = this.enabled ?? true;
  }
}
