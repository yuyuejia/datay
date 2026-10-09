import axios from 'axios';

import buildPaginationQueryOpts from '@/shared/sort/sorts';

import { type IETLComponent } from '@/shared/model/etl-component.model';

const baseApiUrl = 'api/etl-components';

export default class ETLComponentService {
  find(id: string): Promise<IETLComponent> {
    return new Promise<IETLComponent>((resolve, reject) => {
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

  catalog(): Promise<IETLComponent[]> {
    return new Promise<IETLComponent[]>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/catalog`)
        .then(res => {
          resolve(res.data);
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

  create(entity: IETLComponent): Promise<IETLComponent> {
    return new Promise<IETLComponent>((resolve, reject) => {
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

  update(entity: IETLComponent): Promise<IETLComponent> {
    return new Promise<IETLComponent>((resolve, reject) => {
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

  partialUpdate(entity: IETLComponent): Promise<IETLComponent> {
    return new Promise<IETLComponent>((resolve, reject) => {
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
}
