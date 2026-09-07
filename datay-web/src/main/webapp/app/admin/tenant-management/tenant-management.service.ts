import axios from 'axios';
import buildPaginationQueryOpts from '@/shared/sort/sorts';
import { type ITenant } from '@/shared/model/tenant.model';

export default class TenantManagementService {
  get(id: number): Promise<any> {
    return axios.get(`api/admin/tenants/${id}`);
  }

  create(tenant: ITenant): Promise<any> {
    return axios.post('api/admin/tenants', tenant);
  }

  update(tenant: ITenant): Promise<any> {
    return axios.put(`api/admin/tenants/${tenant.id}`, tenant);
  }

  remove(id: number): Promise<any> {
    return axios.delete(`api/admin/tenants/${id}`);
  }

  retrieve(req?: any): Promise<any> {
    return axios.get(`api/admin/tenants?${buildPaginationQueryOpts(req)}`);
  }

  addUser(tenantId: number, userId: number): Promise<any> {
    return axios.post(`api/admin/tenants/${tenantId}/users/${userId}`);
  }

  removeUser(tenantId: number, userId: number): Promise<any> {
    return axios.delete(`api/admin/tenants/${tenantId}/users/${userId}`);
  }

  getTenantUsers(tenantId: number): Promise<any> {
    return axios.get(`api/admin/tenants/${tenantId}/users`);
  }

  searchUsers(query: string): Promise<any> {
    return axios.get(`api/admin/tenants/users/search?query=${encodeURIComponent(query)}`);
  }
}