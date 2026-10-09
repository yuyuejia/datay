import axios from 'axios';

import buildPaginationQueryOpts from '@/shared/sort/sorts';

import { type IDataSource } from '@/shared/model/data-source.model';

const baseApiUrl = 'api/data-sources';

export default class DataSourceService {
  find(id: string): Promise<IDataSource> {
    return new Promise<IDataSource>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${id}`)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  retrieve(paginationQuery?: any): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}?${buildPaginationQueryOpts(paginationQuery)}`)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  delete(id: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .delete(`${baseApiUrl}/${id}`)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  create(entity: IDataSource): Promise<IDataSource> {
    return new Promise<IDataSource>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}`, entity)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  update(entity: IDataSource): Promise<IDataSource> {
    return new Promise<IDataSource>((resolve, reject) => {
      axios
        .put(`${baseApiUrl}/${entity.id}`, entity)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  partialUpdate(entity: IDataSource): Promise<IDataSource> {
    return new Promise<IDataSource>((resolve, reject) => {
      axios
        .patch(`${baseApiUrl}/${entity.id}`, entity)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  getDefaultWarehouse(): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/default-warehouse`)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  setDefaultWarehouse(id: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .put(`${baseApiUrl}/${id}/default-warehouse`)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  getSchemas(id: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${id}/schemas`)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  getTables(dataSourceId: string, schema: string, limit?: number, search?: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      const params = new URLSearchParams();
      if (limit) params.set('limit', String(limit));
      if (search) params.set('search', search);
      const queryString = params.toString();
      axios
        .get(`${baseApiUrl}/${dataSourceId}/schemas/${schema}/tables${queryString ? '?' + queryString : ''}`)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  getFields(dataSourceId: string, schema: string, table: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${dataSourceId}/schemas/${schema}/tables/${table}/columns`)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  getTableDetail(dataSourceId: string, schema: string, table: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${dataSourceId}/schemas/${schema}/tables/${table}/meta`)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  testConnection(entity: IDataSource): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/test-connection`, entity)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }
}
