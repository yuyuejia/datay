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
  id?: number;
  roleName?: string;
  dimensionModelId?: number;
  filterConfig?: string;
  enabled?: boolean;
  createTime?: Date;
  updateTime?: Date;
}

export class RoleDataScope implements IRoleDataScope {
  constructor(
    public id?: number,
    public roleName?: string,
    public dimensionModelId?: number,
    public filterConfig?: string,
    public enabled?: boolean,
    public createTime?: Date,
    public updateTime?: Date,
  ) {
    this.enabled = this.enabled ?? true;
  }
}
