import axios from 'axios';
import { type ITenant } from '@/shared/model/tenant.model';

export default class TenantSwitchService {
  static readonly CACHE_KEY = 'jhi-selected-tenant';

  getMyTenants(): Promise<any> {
    return axios.get('api/tenants/my-tenants');
  }

  switchTenant(tenantId: number): Promise<any> {
    return axios.post('api/authenticate/switch-tenant', { tenantId });
  }

  static getCachedTenant(): { id: number; code: string; name: string } | null {
    try {
      const raw = localStorage.getItem(TenantSwitchService.CACHE_KEY) || sessionStorage.getItem(TenantSwitchService.CACHE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  static cacheTenant(tenant: { id: number; code: string; name: string }, rememberMe: boolean): void {
    const storage = rememberMe ? localStorage : sessionStorage;
    const other = rememberMe ? sessionStorage : localStorage;
    other.removeItem(TenantSwitchService.CACHE_KEY);
    storage.setItem(TenantSwitchService.CACHE_KEY, JSON.stringify(tenant));
  }

  static clearCache(): void {
    localStorage.removeItem(TenantSwitchService.CACHE_KEY);
    sessionStorage.removeItem(TenantSwitchService.CACHE_KEY);
  }
}

export type { ITenant };