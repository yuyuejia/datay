import axios from "axios";

import { type IRoleDataScope } from "@/shared/model/role-data-scope.model";

const baseApiUrl = "api/role-data-scopes";

export default class RoleDataScopeService {
  retrieve(roleName?: string): Promise<any> {
    return axios.get(baseApiUrl, { params: roleName ? { roleName } : {} });
  }

  find(id: string): Promise<any> {
    return axios.get(`${baseApiUrl}/${id}`);
  }

  create(entity: IRoleDataScope): Promise<any> {
    return axios.post(baseApiUrl, entity);
  }

  update(entity: IRoleDataScope): Promise<any> {
    return axios.put(`${baseApiUrl}/${entity.id}`, entity);
  }

  remove(id: string): Promise<any> {
    return axios.delete(`${baseApiUrl}/${id}`);
  }

  retrieveAuthorities(): Promise<any> {
    return axios.get("api/authorities");
  }

  retrieveDimensions(): Promise<any> {
    return axios.get("api/data-models/by-type", {
      params: { modelType: "DIMENSION" },
    });
  }

  retrieveDimensionFields(dimensionModelId: string): Promise<any> {
    return axios.get(`api/data-models/${dimensionModelId}/fields`);
  }
}
