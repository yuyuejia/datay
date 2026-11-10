export interface IETLComponentFieldOption {
  value: string;
  label?: string | null;
  description?: string | null;
}

export interface IETLComponentField {
  key: string;
  label?: string | null;
  type?: string | null;
  group?: string | null;
  order?: number | null;
  required?: boolean | null;
  placeholder?: string | null;
  help?: string | null;
  pattern?: string | null;
  patternMessage?: string | null;
  defaultValue?: string | null;
  options?: IETLComponentFieldOption[] | null;
  multiple?: boolean | null;
  secret?: boolean | null;
  valueFormat?: string | null;
  dependsOn?: string | null;
  visibleWhen?: string[] | null;
  dataSourceType?: string | null;
  dataSourceField?: string | null;
  schemaField?: string | null;
  tableField?: string | null;
  min?: number | null;
  max?: number | null;
  step?: number | null;
  customComponent?: string | null;
}

export interface IETLComponent {
  id?: string;
  name?: string | null;
  code?: string | null;
  desc?: string | null;
  group?: string | null;
  type?: string | null;
  config?: string | null;
  status?: string | null;
  fields?: IETLComponentField[] | null;
  updateTime?: Date | null;
  createTime?: Date | null;
  creater?: string | null;
  tenantId?: string | null;
  dr?: number | null;
}

export class ETLComponent implements IETLComponent {
  constructor(
    public id?: string,
    public name?: string | null,
    public code?: string | null,
    public desc?: string | null,
    public group?: string | null,
    public type?: string | null,
    public config?: string | null,
    public status?: string | null,
    public updateTime?: Date | null,
    public createTime?: Date | null,
    public creater?: string | null,
    public tenantId?: string | null,
    public dr?: number | null,
    public fields?: IETLComponentField[] | null,
  ) {}
}
