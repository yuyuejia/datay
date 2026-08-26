import axios from 'axios';
import sinon from 'sinon';
import dayjs from 'dayjs';

import JobDependService from './job-depend.service';
import { DATE_TIME_FORMAT } from '@/shared/composables/date-format';
import { JobDepend } from '@/shared/model/job-depend.model';

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
  describe('JobDepend Service', () => {
    let service: JobDependService;
    let elemDefault;
    let currentDate: Date;

    beforeEach(() => {
      service = new JobDependService();
      currentDate = new Date();
      elemDefault = new JobDepend(123, 'AAAAAAA', 'AAAAAAA', 'AAAAAAA', 0, currentDate, 'AAAAAAA');
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

      it('should create a JobDepend', async () => {
        const returnedFromService = { id: 123, createTime: dayjs(currentDate).format(DATE_TIME_FORMAT), ...elemDefault };
        const expected = { createTime: currentDate, ...returnedFromService };

        axiosStub.post.resolves({ data: returnedFromService });
        return service.create({}).then(res => {
          expect(res).toMatchObject(expected);
        });
      });

      it('should not create a JobDepend', async () => {
        axiosStub.post.rejects(error);

        return service
          .create({})
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should update a JobDepend', async () => {
        const returnedFromService = {
          parentJobCode: 'BBBBBB',
          childJobCode: 'BBBBBB',
          jobCode: 'BBBBBB',
          lastInterval: 1,
          createTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          tenantId: 'BBBBBB',
          ...elemDefault,
        };

        const expected = { createTime: currentDate, ...returnedFromService };
        axiosStub.put.resolves({ data: returnedFromService });

        return service.update(expected).then(res => {
          expect(res).toMatchObject(expected);
        });
      });

      it('should not update a JobDepend', async () => {
        axiosStub.put.rejects(error);

        return service
          .update({})
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should partial update a JobDepend', async () => {
        const patchObject = { lastInterval: 1, ...new JobDepend() };
        const returnedFromService = Object.assign(patchObject, elemDefault);

        const expected = { createTime: currentDate, ...returnedFromService };
        axiosStub.patch.resolves({ data: returnedFromService });

        return service.partialUpdate(patchObject).then(res => {
          expect(res).toMatchObject(expected);
        });
      });

      it('should not partial update a JobDepend', async () => {
        axiosStub.patch.rejects(error);

        return service
          .partialUpdate({})
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should return a list of JobDepend', async () => {
        const returnedFromService = {
          parentJobCode: 'BBBBBB',
          childJobCode: 'BBBBBB',
          jobCode: 'BBBBBB',
          lastInterval: 1,
          createTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          tenantId: 'BBBBBB',
          ...elemDefault,
        };
        const expected = { createTime: currentDate, ...returnedFromService };
        axiosStub.get.resolves([returnedFromService]);
        return service.retrieve({ sort: {}, page: 0, size: 10 }).then(res => {
          expect(res).toContainEqual(expected);
        });
      });

      it('should not return a list of JobDepend', async () => {
        axiosStub.get.rejects(error);

        return service
          .retrieve()
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should delete a JobDepend', async () => {
        axiosStub.delete.resolves({ ok: true });
        return service.delete(123).then(res => {
          expect(res.ok).toBeTruthy();
        });
      });

      it('should not delete a JobDepend', async () => {
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
