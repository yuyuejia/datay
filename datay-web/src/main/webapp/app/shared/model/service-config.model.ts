export interface IServiceConfig {
  id?: number;
  dfGroup?: string | null;
  dfKey?: string | null;
  dfValue?: string | null;
  createTime?: Date | null;
  ytenantId?: string | null;
}

export class ServiceConfig implements IServiceConfig {
  constructor(
    public id?: number,
    public dfGroup?: string | null,
    public dfKey?: string | null,
    public dfValue?: string | null,
    public createTime?: Date | null,
    public ytenantId?: string | null,
  ) {}
}
