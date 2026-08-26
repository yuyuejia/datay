import axios from 'axios';
import sinon from 'sinon';
import dayjs from 'dayjs';

import JobInstanceService from './job-instance.service';
import { DATE_TIME_FORMAT } from '@/shared/composables/date-format';
import { JobInstance } from '@/shared/model/job-instance.model';

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
  describe('JobInstance Service', () => {
    let service: JobInstanceService;
    let elemDefault;
    let currentDate: Date;

    beforeEach(() => {
      service = new JobInstanceService();
      currentDate = new Date();
      elemDefault = new JobInstance(
        123,
        'AAAAAAA',
        'AAAAAAA',
        'AAAAAAA',
        'AAAAAAA',
        'AAAAAAA',
        'AAAAAAA',
        'AAAAAAA',
        'AAAAAAA',
        'AAAAAAA',
        'AAAAAAA',
        currentDate,
        'AAAAAAA',
        'AAAAAAA',
      );
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

      it('should create a JobInstance', async () => {
        const returnedFromService = { id: 123, createTime: dayjs(currentDate).format(DATE_TIME_FORMAT), ...elemDefault };
        const expected = { createTime: currentDate, ...returnedFromService };

        axiosStub.post.resolves({ data: returnedFromService });
        return service.create({}).then(res => {
          expect(res).toMatchObject(expected);
        });
      });

      it('should not create a JobInstance', async () => {
        axiosStub.post.rejects(error);

        return service
          .create({})
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should update a JobInstance', async () => {
        const returnedFromService = {
          instanceCode: 'BBBBBB',
          jobName: 'BBBBBB',
          jobCode: 'BBBBBB',
          type: 'BBBBBB',
          jobContext: 'BBBBBB',
          status: 'BBBBBB',
          jobMessage: 'BBBBBB',
          execNode: 'BBBBBB',
          startTime: 'BBBBBB',
          endTime: 'BBBBBB',
          createTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          project: 'BBBBBB',
          tenantId: 'BBBBBB',
          ...elemDefault,
        };

        const expected = { createTime: currentDate, ...returnedFromService };
        axiosStub.put.resolves({ data: returnedFromService });

        return service.update(expected).then(res => {
          expect(res).toMatchObject(expected);
        });
      });

      it('should not update a JobInstance', async () => {
        axiosStub.put.rejects(error);

        return service
          .update({})
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should partial update a JobInstance', async () => {
        const patchObject = {
          jobCode: 'BBBBBB',
          type: 'BBBBBB',
          jobContext: 'BBBBBB',
          jobMessage: 'BBBBBB',
          startTime: 'BBBBBB',
          ...new JobInstance(),
        };
        const returnedFromService = Object.assign(patchObject, elemDefault);

        const expected = { createTime: currentDate, ...returnedFromService };
        axiosStub.patch.resolves({ data: returnedFromService });

        return service.partialUpdate(patchObject).then(res => {
          expect(res).toMatchObject(expected);
        });
      });

      it('should not partial update a JobInstance', async () => {
        axiosStub.patch.rejects(error);

        return service
          .partialUpdate({})
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should return a list of JobInstance', async () => {
        const returnedFromService = {
          instanceCode: 'BBBBBB',
          jobName: 'BBBBBB',
          jobCode: 'BBBBBB',
          type: 'BBBBBB',
          jobContext: 'BBBBBB',
          status: 'BBBBBB',
          jobMessage: 'BBBBBB',
          execNode: 'BBBBBB',
          startTime: 'BBBBBB',
          endTime: 'BBBBBB',
          createTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          project: 'BBBBBB',
          tenantId: 'BBBBBB',
          ...elemDefault,
        };
        const expected = { createTime: currentDate, ...returnedFromService };
        axiosStub.get.resolves([returnedFromService]);
        return service.retrieve({ sort: {}, page: 0, size: 10 }).then(res => {
          expect(res).toContainEqual(expected);
        });
      });

      it('should not return a list of JobInstance', async () => {
        axiosStub.get.rejects(error);

        return service
          .retrieve()
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should delete a JobInstance', async () => {
        axiosStub.delete.resolves({ ok: true });
        return service.delete(123).then(res => {
          expect(res.ok).toBeTruthy();
        });
      });

      it('should not delete a JobInstance', async () => {
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
