import axios from "axios";

import { type IAuthority } from "@/shared/model/authority.model";

const baseApiUrl = "api/authorities";

export default class RoleManagementService {
  retrieve(): Promise<any> {
    return axios.get(baseApiUrl);
  }

  create(authority: IAuthority): Promise<any> {
    return axios.post(baseApiUrl, authority);
  }

  update(name: string, authority: IAuthority): Promise<any> {
    return axios.put(`${baseApiUrl}/${encodeURIComponent(name)}`, authority);
  }

  remove(name: string): Promise<any> {
    return axios.delete(`${baseApiUrl}/${encodeURIComponent(name)}`);
  }
}
