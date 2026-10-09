import axios from 'axios';

import buildPaginationQueryOpts from '@/shared/sort/sorts';

import { type IJobInstance } from '@/shared/model/job-instance.model';

const baseApiUrl = 'api/job-instances';

export default class JobInstanceService {
  find(id: string): Promise<IJobInstance> {
    return new Promise<IJobInstance>((resolve, reject) => {
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

  create(entity: IJobInstance): Promise<IJobInstance> {
    return new Promise<IJobInstance>((resolve, reject) => {
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

  update(entity: IJobInstance): Promise<IJobInstance> {
    return new Promise<IJobInstance>((resolve, reject) => {
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

  partialUpdate(entity: IJobInstance): Promise<IJobInstance> {
    return new Promise<IJobInstance>((resolve, reject) => {
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

  // 新增：获取 DAG 编排实例下的子任务实例列表
  getSubInstances(instanceCode: string): Promise<IJobInstance[]> {
    return new Promise<IJobInstance[]>((resolve, reject) => {
      axios
        .get(`${baseApiUrl}/${instanceCode}/sub-instances`)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  // 新增：获取任务实例日志
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

  // 新增：获取完整任务日志（一次性获取）
  getFullTaskLog(jobCode: string, jobInstanceCode: string, maxSize: number = 10485760): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`api/worker/log/full`, {
          params: {
            jobCode,
            jobInstanceCode,
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

  // 新增：获取日志文件信息
  getLogInfo(jobCode: string, jobInstanceCode: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .get(`api/worker/log/info`, {
          params: {
            jobCode,
            jobInstanceCode,
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

  // 新增：终止任务实例
  stopJobInstance(instanceCode: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .post(`api/worker/${instanceCode}/stop`)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }
}
