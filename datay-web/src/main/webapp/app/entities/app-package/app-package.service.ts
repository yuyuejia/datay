import axios from 'axios';

import buildPaginationQueryOpts from '@/shared/sort/sorts';
import {
  type IAppPackage,
  type IAppPackageExportOptions,
  type IAppPackageExportRequest,
  type IAppPackageInitRequest,
  type IAppPackageInitResult,
} from '@/shared/model/app-package.model';

const baseApiUrl = 'api/app-packages';

/**
 * 数据服务应用市场服务：资产包浏览、导出、初始化与初始化记录查询。
 */
export default class AppPackageService {
  /** 分页浏览市场（系统预制包 + 本租户导出的包）。 */
  retrieve(paginationQuery?: any, filters?: { search?: string; source?: string; category?: string }): Promise<any> {
    const params = {
      ...(paginationQuery || {}),
      ...(filters?.search ? { search: filters.search } : {}),
      ...(filters?.source ? { source: filters.source } : {}),
      ...(filters?.category ? { category: filters.category } : {}),
    };
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}?${buildPaginationQueryOpts(params)}`)
        .then((res) => {
          resolve(res);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  find(id: string): Promise<IAppPackage> {
    return new Promise<IAppPackage>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${id}`)
        .then((res) => {
          resolve(res.data);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  /** 下载资产包 JSON 文件。 */
  download(id: string): Promise<Blob> {
    return new Promise<Blob>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${id}/content`, { responseType: 'blob' })
        .then((res) => {
          resolve(res.data);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  /** 导出向导的可选资产清单。 */
  getExportOptions(): Promise<IAppPackageExportOptions> {
    return new Promise<IAppPackageExportOptions>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/export-options`)
        .then((res) => {
          resolve(res.data);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  /** 市场里出现过的业务场景分类。 */
  getCategories(): Promise<string[]> {
    return new Promise<string[]>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/categories`)
        .then((res) => {
          resolve(res.data);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  /** 按选择导出资产包。 */
  exportPackage(request: IAppPackageExportRequest): Promise<IAppPackage> {
    return new Promise<IAppPackage>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/export`, request)
        .then((res) => {
          resolve(res.data);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  /** 用市场中已有的资产包初始化数据应用。 */
  initPackage(id: string, request?: IAppPackageInitRequest): Promise<IAppPackageInitResult> {
    return new Promise<IAppPackageInitResult>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/${id}/init`, request || {})
        .then((res) => {
          resolve(res.data);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  /** 上传一份资产包 JSON 内容进行初始化。 */
  importContent(request: IAppPackageInitRequest): Promise<IAppPackageInitResult> {
    return new Promise<IAppPackageInitResult>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/import`, request)
        .then((res) => {
          resolve(res.data);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  /** 当前租户的数据应用初始化记录。 */
  retrieveInstances(paginationQuery?: any): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/instances?${buildPaginationQueryOpts(paginationQuery || {})}`)
        .then((res) => {
          resolve(res);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }

  delete(id: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .delete(`${baseApiUrl}/${id}`)
        .then((res) => {
          resolve(res);
        })
        .catch((err) => {
          reject(err);
        });
    });
  }
}
