import axios from 'axios';

import buildPaginationQueryOpts from '@/shared/sort/sorts';

import { type IDataApi, type IDataApiCallStats } from '@/shared/model/data-api.model';

const baseApiUrl = 'api/data-apis';

export default class DataApiService {
  find(id: string): Promise<IDataApi> {
    return new Promise<IDataApi>((resolve, reject) => {
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

  findByDataset(datasetId: string): Promise<IDataApi | null> {
    return axios
      .get(`${baseApiUrl}/by-dataset/${encodeURIComponent(datasetId)}`)
      .then(res => res.data as IDataApi)
      .catch(err => {
        if (err?.response?.status === 404) {
          return null;
        }
        throw err;
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

  create(entity: IDataApi): Promise<IDataApi> {
    return new Promise<IDataApi>((resolve, reject) => {
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

  update(entity: IDataApi): Promise<IDataApi> {
    return new Promise<IDataApi>((resolve, reject) => {
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

  moveToDirectory(id: string, directoryId: string | null): Promise<IDataApi> {
    return new Promise<IDataApi>((resolve, reject) => {
      axios
        .patch(`${baseApiUrl}/${id}/directory`, { directoryId })
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  retrieveCallLogs(apiId: string, paginationQuery?: any): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${apiId}/call-logs?${buildPaginationQueryOpts(paginationQuery)}`)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  retrieveCallStats(apiId: string, startDate?: string, endDate?: string): Promise<IDataApiCallStats> {
    return new Promise<IDataApiCallStats>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${apiId}/call-stats`, { params: { startDate, endDate } })
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  clearCallLogs(apiId: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .delete(`${baseApiUrl}/${apiId}/call-logs`)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }
}
