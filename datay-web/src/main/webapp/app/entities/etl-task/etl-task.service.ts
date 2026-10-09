import axios from 'axios';

import buildPaginationQueryOpts from '@/shared/sort/sorts';

import { type IETLTask } from '@/shared/model/etl-task.model';

const baseApiUrl = 'api/etl-tasks';

export default class ETLTaskService {
  find(id: string): Promise<IETLTask> {
    return new Promise<IETLTask>((resolve, reject) => {
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

  create(entity: IETLTask): Promise<IETLTask> {
    return new Promise<IETLTask>((resolve, reject) => {
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

  update(entity: IETLTask): Promise<IETLTask> {
    return new Promise<IETLTask>((resolve, reject) => {
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

  partialUpdate(entity: IETLTask): Promise<IETLTask> {
    return new Promise<IETLTask>((resolve, reject) => {
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

  run(id: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/${id}/run`)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  debug(task: any, rowLimit: number, targetNodeId?: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/debug`, { task, rowLimit, targetNodeId: targetNodeId || null })
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  online(id: string): Promise<IETLTask> {
    return new Promise<IETLTask>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/${id}/online`)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  offline(id: string): Promise<IETLTask> {
    return new Promise<IETLTask>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/${id}/offline`)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  getTaskInstances(taskId: string, paginationQuery?: any): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${taskId}/instances?${buildPaginationQueryOpts(paginationQuery)}`)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  getTaskLog(jobCode: string, jobInstanceCode: string, offset: number = 0, maxSize: number = 10485760): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`api/worker/log`, {
          params: {
            jobCode,
            jobInstanceCode,
            offset,
            maxSize,
          },
        })
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }
}
