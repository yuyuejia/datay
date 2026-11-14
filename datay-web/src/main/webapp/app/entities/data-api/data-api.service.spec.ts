import axios from 'axios';
import sinon from 'sinon';

import DataApiService from './data-api.service';
import { DataApi } from '@/shared/model/data-api.model';

const error = {
  response: {
    status: null,
    data: {
      type: null,
    },
  },
};

const axiosStub = {
  get: sinon.stub(axios, 'get'),
  post: sinon.stub(axios, 'post'),
  put: sinon.stub(axios, 'put'),
  delete: sinon.stub(axios, 'delete'),
};

describe('Service Tests', () => {
  describe('DataApi Service', () => {
    let service: DataApiService;
    let elemDefault: DataApi;

    beforeEach(() => {
      service = new DataApiService();
      elemDefault = new DataApi(
        123,
        '订单查询',
        'order_query',
        '查询订单',
        1,
        'TABLE',
        'test_db',
        'orders',
        null,
        'ENABLED',
        '0',
        new Date(),
        new Date(),
      );
    });

    describe('Service methods', () => {
      it('should find an element', async () => {
        axiosStub.get.resolves({ data: elemDefault });

        return service.find(123).then(res => {
          expect(res).toMatchObject(elemDefault);
        });
      });

      it('should not find an element', async () => {
        axiosStub.get.rejects(error);

        return service
          .find(123)
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should create a DataApi', async () => {
        axiosStub.post.resolves({ data: elemDefault });
        return service.create(elemDefault).then(res => {
          expect(res).toMatchObject(elemDefault);
        });
      });

      it('should not create a DataApi', async () => {
        axiosStub.post.rejects(error);

        return service
          .create(elemDefault)
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should update a DataApi', async () => {
        const updated = { ...elemDefault, name: 'BBBBBB' };
        axiosStub.put.resolves({ data: updated });

        return service.update(updated).then(res => {
          expect(res).toMatchObject(updated);
        });
      });

      it('should not update a DataApi', async () => {
        axiosStub.put.rejects(error);

        return service
          .update(elemDefault)
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should return a list of DataApi', async () => {
        axiosStub.get.resolves({ headers: {}, data: [elemDefault] });
        return service.retrieve({ page: 0, size: 20 }).then(res => {
          expect(res.data).toContainEqual(elemDefault);
        });
      });

      it('should not return a list of DataApi', async () => {
        axiosStub.get.rejects(error);

        return service
          .retrieve()
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should retrieve call logs', async () => {
        axiosStub.get.resolves({ headers: { 'x-total-count': '1' }, data: [{ id: 'log-1', status: 'SUCCESS' }] });

        const res = await service.retrieveCallLogs(123, { page: 0, size: 20, status: 'SUCCESS' });

        expect(res.data).toContainEqual(expect.objectContaining({ id: 'log-1' }));
        expect(axiosStub.get.lastCall.args[0]).toContain('api/data-apis/123/call-logs');
      });

      it('should retrieve call stats', async () => {
        const stats = { totalCount: 10, successCount: 9, failureCount: 1, trend: [] };
        axiosStub.get.resolves({ data: stats });

        const res = await service.retrieveCallStats('123', '2026-11-01', '2026-11-14');

        expect(res).toMatchObject(stats);
        expect(axiosStub.get.lastCall.args[0]).toContain('api/data-apis/123/call-stats');
      });

      it('should clear call logs', async () => {
        axiosStub.delete.resolves({ ok: true });

        const res = await service.clearCallLogs('123');

        expect(res.ok).toBeTruthy();
        expect(axiosStub.delete.lastCall.args[0]).toContain('api/data-apis/123/call-logs');
      });

      it('should delete a DataApi', async () => {
        axiosStub.delete.resolves({ ok: true });
        return service.delete(123).then(res => {
          expect(res.ok).toBeTruthy();
        });
      });

      it('should not delete a DataApi', async () => {
        axiosStub.delete.rejects(error);

        return service
          .delete(123)
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });
    });
  });
});
