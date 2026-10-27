import axios from 'axios';

const TIMEOUT = 1000000;
export const TOKEN_STORAGE_KEY = 'jhi-authenticationToken';

/**
 * Public authentication endpoints that must never receive an Authorization header.
 * <p>
 * If a stale (e.g. expired) JWT is attached, Spring Security's resource-server filter rejects
 * the whole request with 401 while validating the token, even though the endpoint is
 * {@code permitAll}. That would make it impossible to log in again until the token was removed
 * from the browser storage manually.
 * <p>
 * Note: {@code api/authenticate/switch-tenant} is intentionally NOT in this list because it
 * requires an authenticated user.
 */
const isTokenlessEndpoint = (url?: string): boolean => {
  const path = (url || '').split('?')[0];
  return (
    path.endsWith('api/authenticate') ||
    path.endsWith('api/register') ||
    path.endsWith('api/activate') ||
    path.includes('api/account/reset-password/')
  );
};

export const clearStoredToken = (): void => {
  localStorage.removeItem(TOKEN_STORAGE_KEY);
  sessionStorage.removeItem(TOKEN_STORAGE_KEY);
};

const onRequestSuccess = config => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY) || sessionStorage.getItem(TOKEN_STORAGE_KEY);
  if (token && !isTokenlessEndpoint(config.url)) {
    if (!config.headers) {
      config.headers = {};
    }
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.timeout = TIMEOUT;
  config.url = `${SERVER_API_URL}${config.url}`;
  return config;
};
const setupAxiosInterceptors = (onUnauthenticated, onServerError) => {
  const onResponseError = err => {
    const status = err.response?.status ?? err.status;
    if (status === 401) {
      // The token is no longer accepted (expired/revoked). Drop it so it cannot poison
      // the next login request or keep the app in a "logged in but always 401" state.
      clearStoredToken();
    }
    if (status === 403 || status === 401) {
      return onUnauthenticated(err);
    }
    if (status >= 500) {
      return onServerError(err);
    }
    return Promise.reject(err);
  };

  if (axios.interceptors) {
    axios.interceptors.request.use(onRequestSuccess);
    axios.interceptors.response.use(res => res, onResponseError);
  }
};

export { onRequestSuccess, setupAxiosInterceptors };
