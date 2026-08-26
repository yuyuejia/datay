import axios from 'axios';
import sinon from 'sinon';
import dayjs from 'dayjs';

import ETLTaskService from './etl-task.service';
import { DATE_TIME_FORMAT } from '@/shared/composables/date-format';
import { ETLTask } from '@/shared/model/etl-task.model';

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
  describe('ETLTask Service', () => {
    let service: ETLTaskService;
    let elemDefault;
    let currentDate: Date;

    beforeEach(() => {
      service = new ETLTaskService();
      currentDate = new Date();
      elemDefault = new ETLTask(
        123,
        'AAAAAAA',
        'AAAAAAA',
        0,
        'AAAAAAA',
        'AAAAAAA',
        'AAAAAAA',
        'AAAAAAA',
        'AAAAAAA',
        'AAAAAAA',
        'AAAAAAA',
        currentDate,
        currentDate,
        'AAAAAAA',
        'AAAAAAA',
        'AAAAAAA',
        0,
      );
    });

    describe('Service methods', () => {
      it('should find an element', async () => {
        const returnedFromService = {
          updateTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          createTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          ...elemDefault,
        };
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

      it('should create a ETLTask', async () => {
        const returnedFromService = {
          id: 123,
          updateTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          createTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          ...elemDefault,
        };
        const expected = { updateTime: currentDate, createTime: currentDate, ...returnedFromService };

        axiosStub.post.resolves({ data: returnedFromService });
        return service.create({}).then(res => {
          expect(res).toMatchObject(expected);
        });
      });

      it('should not create a ETLTask', async () => {
        axiosStub.post.rejects(error);

        return service
          .create({})
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should update a ETLTask', async () => {
        const returnedFromService = {
          taskName: 'BBBBBB',
          taskCode: 'BBBBBB',
          jobId: 1,
          taskDesc: 'BBBBBB',
          dir: 'BBBBBB',
          type: 'BBBBBB',
          cron: 'BBBBBB',
          jobContext: 'BBBBBB',
          status: 'BBBBBB',
          lastStatus: 'BBBBBB',
          updateTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          createTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          creater: 'BBBBBB',
          project: 'BBBBBB',
          tenantId: 'BBBBBB',
          dr: 1,
          ...elemDefault,
        };

        const expected = { updateTime: currentDate, createTime: currentDate, ...returnedFromService };
        axiosStub.put.resolves({ data: returnedFromService });

        return service.update(expected).then(res => {
          expect(res).toMatchObject(expected);
        });
      });

      it('should not update a ETLTask', async () => {
        axiosStub.put.rejects(error);

        return service
          .update({})
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should partial update a ETLTask', async () => {
        const patchObject = {
          type: 'BBBBBB',
          jobContext: 'BBBBBB',
          status: 'BBBBBB',
          lastStatus: 'BBBBBB',
          updateTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          createTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          project: 'BBBBBB',
          tenantId: 'BBBBBB',
          dr: 1,
          ...new ETLTask(),
        };
        const returnedFromService = Object.assign(patchObject, elemDefault);

        const expected = { updateTime: currentDate, createTime: currentDate, ...returnedFromService };
        axiosStub.patch.resolves({ data: returnedFromService });

        return service.partialUpdate(patchObject).then(res => {
          expect(res).toMatchObject(expected);
        });
      });

      it('should not partial update a ETLTask', async () => {
        axiosStub.patch.rejects(error);

        return service
          .partialUpdate({})
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should return a list of ETLTask', async () => {
        const returnedFromService = {
          taskName: 'BBBBBB',
          taskCode: 'BBBBBB',
          jobId: 1,
          taskDesc: 'BBBBBB',
          dir: 'BBBBBB',
          type: 'BBBBBB',
          cron: 'BBBBBB',
          jobContext: 'BBBBBB',
          status: 'BBBBBB',
          lastStatus: 'BBBBBB',
          updateTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          createTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          creater: 'BBBBBB',
          project: 'BBBBBB',
          tenantId: 'BBBBBB',
          dr: 1,
          ...elemDefault,
        };
        const expected = { updateTime: currentDate, createTime: currentDate, ...returnedFromService };
        axiosStub.get.resolves([returnedFromService]);
        return service.retrieve({ sort: {}, page: 0, size: 10 }).then(res => {
          expect(res).toContainEqual(expected);
        });
      });

      it('should not return a list of ETLTask', async () => {
        axiosStub.get.rejects(error);

        return service
          .retrieve()
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should delete a ETLTask', async () => {
        axiosStub.delete.resolves({ ok: true });
        return service.delete(123).then(res => {
          expect(res.ok).toBeTruthy();
        });
      });

      it('should not delete a ETLTask', async () => {
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
