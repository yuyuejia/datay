import axios from 'axios';
import sinon from 'sinon';
import dayjs from 'dayjs';

import ServiceConfigService from './service-config.service';
import { DATE_TIME_FORMAT } from '@/shared/composables/date-format';
import { ServiceConfig } from '@/shared/model/service-config.model';

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
  patch: sinon.stub(axios, 'patch'),
  delete: sinon.stub(axios, 'delete'),
};

describe('Service Tests', () => {
  describe('ServiceConfig Service', () => {
    let service: ServiceConfigService;
    let elemDefault;
    let currentDate: Date;

    beforeEach(() => {
      service = new ServiceConfigService();
      currentDate = new Date();
      elemDefault = new ServiceConfig(123, 'AAAAAAA', 'AAAAAAA', 'AAAAAAA', currentDate, 'AAAAAAA');
    });

    describe('Service methods', () => {
      it('should find an element', async () => {
        const returnedFromService = { createTime: dayjs(currentDate).format(DATE_TIME_FORMAT), ...elemDefault };
        axiosStub.get.resolves({ data: returnedFromService });

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

      it('should create a ServiceConfig', async () => {
        const returnedFromService = { id: 123, createTime: dayjs(currentDate).format(DATE_TIME_FORMAT), ...elemDefault };
        const expected = { createTime: currentDate, ...returnedFromService };

        axiosStub.post.resolves({ data: returnedFromService });
        return service.create({}).then(res => {
          expect(res).toMatchObject(expected);
        });
      });

      it('should not create a ServiceConfig', async () => {
        axiosStub.post.rejects(error);

        return service
          .create({})
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should update a ServiceConfig', async () => {
        const returnedFromService = {
          dfGroup: 'BBBBBB',
          dfKey: 'BBBBBB',
          dfValue: 'BBBBBB',
          createTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          ytenantId: 'BBBBBB',
          ...elemDefault,
        };

        const expected = { createTime: currentDate, ...returnedFromService };
        axiosStub.put.resolves({ data: returnedFromService });

        return service.update(expected).then(res => {
          expect(res).toMatchObject(expected);
        });
      });

      it('should not update a ServiceConfig', async () => {
        axiosStub.put.rejects(error);

        return service
          .update({})
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should partial update a ServiceConfig', async () => {
        const patchObject = { dfKey: 'BBBBBB', dfValue: 'BBBBBB', ...new ServiceConfig() };
        const returnedFromService = Object.assign(patchObject, elemDefault);

        const expected = { createTime: currentDate, ...returnedFromService };
        axiosStub.patch.resolves({ data: returnedFromService });

        return service.partialUpdate(patchObject).then(res => {
          expect(res).toMatchObject(expected);
        });
      });

      it('should not partial update a ServiceConfig', async () => {
        axiosStub.patch.rejects(error);

        return service
          .partialUpdate({})
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should return a list of ServiceConfig', async () => {
        const returnedFromService = {
          dfGroup: 'BBBBBB',
          dfKey: 'BBBBBB',
          dfValue: 'BBBBBB',
          createTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          ytenantId: 'BBBBBB',
          ...elemDefault,
        };
        const expected = { createTime: currentDate, ...returnedFromService };
        axiosStub.get.resolves([returnedFromService]);
        return service.retrieve({ sort: {}, page: 0, size: 10 }).then(res => {
          expect(res).toContainEqual(expected);
        });
      });

      it('should not return a list of ServiceConfig', async () => {
        axiosStub.get.rejects(error);

        return service
          .retrieve()
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should delete a ServiceConfig', async () => {
        axiosStub.delete.resolves({ ok: true });
        return service.delete(123).then(res => {
          expect(res.ok).toBeTruthy();
        });
      });

      it('should not delete a ServiceConfig', async () => {
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
