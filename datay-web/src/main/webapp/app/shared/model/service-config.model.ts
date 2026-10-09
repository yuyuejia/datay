export interface IServiceConfig {
  id?: string;
  dfGroup?: string | null;
  dfKey?: string | null;
  dfValue?: string | null;
  createTime?: Date | null;
  tenantId?: string | null;
}

export class ServiceConfig implements IServiceConfig {
  constructor(
    public id?: string,
    public dfGroup?: string | null,
    public dfKey?: string | null,
    public dfValue?: string | null,
    public createTime?: Date | null,
    public tenantId?: string | null,
  ) {}
}
