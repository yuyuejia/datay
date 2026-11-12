import axios from 'axios';

import { type IETLTaskDirectory } from '@/shared/model/etl-task-directory.model';

const baseApiUrl = 'api/etl-task-directories';

export default class ETLTaskDirectoryService {
  find(id: string): Promise<IETLTaskDirectory> {
    return new Promise<IETLTaskDirectory>((resolve, reject) => {
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

  retrieve(taskType: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}?taskType=${encodeURIComponent(taskType)}`)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  retrieveByParent(taskType: string, parentId?: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      const params: Record<string, string> = { taskType };
      if (parentId) {
        params.parentId = parentId;
      }
      axios
        .get(`${baseApiUrl}/by-parent`, { params })
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  create(entity: IETLTaskDirectory): Promise<IETLTaskDirectory> {
    return new Promise<IETLTaskDirectory>((resolve, reject) => {
      axios
        .post(baseApiUrl, entity)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  update(entity: IETLTaskDirectory): Promise<IETLTaskDirectory> {
    return new Promise<IETLTaskDirectory>((resolve, reject) => {
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
}
