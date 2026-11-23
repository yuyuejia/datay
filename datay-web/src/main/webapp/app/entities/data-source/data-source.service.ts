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

  delete(id: string, force = false): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .delete(`${baseApiUrl}/${id}`, { params: force ? { force: true } : {} })
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  checkReferences(id: string): Promise<any[]> {
    return new Promise<any[]>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${id}/references`)
        .then(res => {
          resolve(res.data);
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

  getCatalogs(id: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${id}/catalogs`)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  getSchemas(id: string, catalog?: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${id}/schemas`, { params: catalog ? { catalog } : {} })
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  getTables(dataSourceId: string, schema: string, limit?: number, search?: string, catalog?: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      const params: Record<string, string | number> = {};
      if (catalog) params.catalog = catalog;
      if (limit) params.limit = limit;
      if (search) params.search = search;
      axios
        .get(`${baseApiUrl}/${dataSourceId}/schemas/${encodeURIComponent(schema)}/tables`, { params })
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  getFields(dataSourceId: string, schema: string, table: string, catalog?: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${dataSourceId}/schemas/${encodeURIComponent(schema)}/tables/${encodeURIComponent(table)}/columns`, {
          params: catalog ? { catalog } : {},
        })
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  getTableLineage(dataSourceId: string, schema: string, table: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${dataSourceId}/schemas/${schema}/tables/${table}/lineage`)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  getTableDetail(dataSourceId: string, schema: string, table: string, catalog?: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${dataSourceId}/schemas/${encodeURIComponent(schema)}/tables/${encodeURIComponent(table)}/meta`, {
          params: catalog ? { catalog } : {},
        })
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  getTableHealth(dataSourceId: string, schema: string, table: string, catalog?: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${dataSourceId}/schemas/${encodeURIComponent(schema)}/tables/${encodeURIComponent(table)}/health`, {
          params: catalog ? { catalog } : {},
        })
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  getCatalogMaintenanceActions(dataSourceId: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${dataSourceId}/catalog-maintenance`)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  executeCatalogMaintenance(dataSourceId: string, action: string, params: Record<string, any>): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/${dataSourceId}/catalog-maintenance/${encodeURIComponent(action)}`, params)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  getTableMaintenanceActions(dataSourceId: string, schema: string, table: string, catalog?: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${dataSourceId}/schemas/${encodeURIComponent(schema)}/tables/${encodeURIComponent(table)}/maintenance`, {
          params: catalog ? { catalog } : {},
        })
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  executeTableMaintenance(
    dataSourceId: string,
    schema: string,
    table: string,
    action: string,
    params: Record<string, any>,
    catalog?: string,
  ): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .post(
          `${baseApiUrl}/${dataSourceId}/schemas/${encodeURIComponent(schema)}/tables/${encodeURIComponent(table)}/maintenance/${encodeURIComponent(action)}`,
          params,
          { params: catalog ? { catalog } : {} },
        )
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

  getDriverStatus(type: string, version?: string, owner?: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      const params: Record<string, string> = {};
      if (version) params.version = version;
      if (owner) params.owner = owner;
      axios
        .get(`api/drivers/${type}`, { params })
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  uploadDriver(type: string, files: File[], replace = true, owner?: string): Promise<any> {
    const formData = new FormData();
    files.forEach(file => formData.append('files', file));
    return new Promise<any>((resolve, reject) => {
      const params: Record<string, string | boolean> = { replace };
      if (owner) params.owner = owner;
      axios
        .post(`api/drivers/${type}/upload`, formData, { params })
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }
}
