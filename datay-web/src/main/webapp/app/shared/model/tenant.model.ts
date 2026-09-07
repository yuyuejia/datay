export interface ITenant {
  id?: number;
  code?: string;
  name?: string;
  description?: string;
  enabled?: boolean;
  createdBy?: string;
  createdDate?: Date;
  lastModifiedBy?: string;
  lastModifiedDate?: Date;
}

export class Tenant implements ITenant {
  constructor(
    public id?: number,
    public code?: string,
    public name?: string,
    public description?: string,
    public enabled?: boolean,
    public createdBy?: string,
    public createdDate?: Date,
    public lastModifiedBy?: string,
    public lastModifiedDate?: Date
  ) {
    this.enabled = this.enabled ?? true;
  }
}