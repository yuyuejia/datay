import axios from 'axios';

import { type IRoleDatasetScope } from '@/shared/model/role-dataset-scope.model';

const baseApiUrl = 'api/role-dataset-scopes';

export interface IDatasetFieldValuePage {
  values: string[];
  total: number;
  page: number;
  size: number;
}

export default class RoleDatasetScopeService {
  retrieve(roleName?: string): Promise<any> {
    return axios.get(baseApiUrl, { params: roleName ? { roleName } : {} });
  }

  find(id: string): Promise<any> {
    return axios.get(`${baseApiUrl}/${id}`);
  }

  create(entity: IRoleDatasetScope): Promise<any> {
    return axios.post(baseApiUrl, entity);
  }

  update(entity: IRoleDatasetScope): Promise<any> {
    return axios.put(`${baseApiUrl}/${entity.id}`, entity);
  }

  remove(id: string): Promise<any> {
    return axios.delete(`${baseApiUrl}/${id}`);
  }

  pageFieldValues(datasetId: string, fieldName: string, keyword?: string, page = 1, size = 20): Promise<IDatasetFieldValuePage> {
    return axios.get(`${baseApiUrl}/field-values`, { params: { datasetId, fieldName, keyword, page, size } }).then(res => res.data);
  }
}
