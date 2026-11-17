import axios from 'axios';

const baseApiUrl = 'api/account/access-tokens';

/** 访问令牌元数据，不包含明文和哈希。 */
export interface IAccessToken {
  id: string;
  name: string;
  description?: string | null;
  tokenHint?: string | null;
  status: string;
  lastUsedTime?: string | null;
  createTime?: string | null;
}

/** 创建或重新生成令牌后的返回体，明文令牌仅返回一次。 */
export interface ICreatedAccessToken {
  token: string;
  accessToken: IAccessToken;
}

export interface IAccessTokenPayload {
  name?: string;
  description?: string | null;
  status?: string;
}

export default class AccessTokenService {
  list(): Promise<IAccessToken[]> {
    return axios.get(baseApiUrl).then(res => res.data);
  }

  create(payload: { name: string; description?: string | null }): Promise<ICreatedAccessToken> {
    return axios.post(baseApiUrl, payload).then(res => res.data);
  }

  update(id: string, payload: IAccessTokenPayload): Promise<IAccessToken> {
    return axios.patch(`${baseApiUrl}/${id}`, payload).then(res => res.data);
  }

  rotate(id: string): Promise<ICreatedAccessToken> {
    return axios.post(`${baseApiUrl}/${id}/rotate`).then(res => res.data);
  }

  remove(id: string): Promise<void> {
    return axios.delete(`${baseApiUrl}/${id}`).then(() => undefined);
  }
}
