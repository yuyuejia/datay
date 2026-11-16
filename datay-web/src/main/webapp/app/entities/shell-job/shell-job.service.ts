import axios from 'axios';

import buildPaginationQueryOpts from '@/shared/sort/sorts';
import { type IJob } from '@/shared/model/job.model';

const baseApiUrl = 'api/jobs';

/**
 * Service for the Shell task management (Shell任务). A Shell task is persisted
 * as a regular Job whose type is {@code SHELL} and whose {@code jobContext}
 * stores the shell script to execute.
 */
export default class ShellJobService {
  /**
   * Query jobs. Supported extra query params: {@code type}, {@code typeNot}.
   */
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

  /** List the Shell jobs (paged). */
  retrieveShellJobs(paginationQuery?: any): Promise<any> {
    return this.retrieve({ ...paginationQuery, type: 'SHELL' });
  }

  /** Load every Shell job, following pagination so directory trees never miss tasks. */
  async retrieveAllShellJobs(): Promise<IJob[]> {
    const pageSize = 500;
    const jobs: IJob[] = [];
    let page = 0;
    let hasMore = true;
    while (hasMore) {
      const res = await this.retrieveShellJobs({
        page,
        size: pageSize,
        sort: ['jobName,asc'],
      });
      const rows: IJob[] = res.data || [];
      jobs.push(...rows);
      const total = Number(res.headers?.['x-total-count']);
      if (Number.isFinite(total)) {
        hasMore = jobs.length < total;
      } else {
        hasMore = rows.length >= pageSize;
      }
      if (rows.length === 0) {
        hasMore = false;
      }
      page += 1;
    }
    return jobs;
  }

  find(id: string): Promise<IJob> {
    return new Promise<IJob>((resolve, reject) => {
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

  create(entity: IJob): Promise<IJob> {
    return new Promise<IJob>((resolve, reject) => {
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

  update(entity: IJob): Promise<IJob> {
    return new Promise<IJob>((resolve, reject) => {
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

  run(id: string): Promise<any> {
    return new Promise<any>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/${id}/run`)
        .then(res => {
          resolve(res);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  online(id: string, cron?: string, retryCount?: number, timeoutSeconds?: number, priority?: number): Promise<IJob> {
    return new Promise<IJob>((resolve, reject) => {
      axios
        .post(`${baseApiUrl}/${id}/online`, null, { params: { cron, retryCount, timeoutSeconds, priority } })
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  offline(id: string): Promise<IJob> {
    return new Promise<IJob>((resolve, reject) => {
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

  /** Move a Shell job to the given directory; null moves it to the root. */
  moveToDirectory(id: string, directoryId: string | null): Promise<IJob> {
    return new Promise<IJob>((resolve, reject) => {
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
}
