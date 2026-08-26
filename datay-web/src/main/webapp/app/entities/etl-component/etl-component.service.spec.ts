import axios from 'axios';
import sinon from 'sinon';
import dayjs from 'dayjs';

import ETLComponentService from './etl-component.service';
import { DATE_TIME_FORMAT } from '@/shared/composables/date-format';
import { ETLComponent } from '@/shared/model/etl-component.model';

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
  describe('ETLComponent Service', () => {
    let service: ETLComponentService;
    let elemDefault;
    let currentDate: Date;

    beforeEach(() => {
      service = new ETLComponentService();
      currentDate = new Date();
      elemDefault = new ETLComponent(
        123,
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

      it('should create a ETLComponent', async () => {
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

      it('should not create a ETLComponent', async () => {
        axiosStub.post.rejects(error);

        return service
          .create({})
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should update a ETLComponent', async () => {
        const returnedFromService = {
          name: 'BBBBBB',
          code: 'BBBBBB',
          desc: 'BBBBBB',
          group: 'BBBBBB',
          type: 'BBBBBB',
          config: 'BBBBBB',
          status: 'BBBBBB',
          updateTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          createTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          creater: 'BBBBBB',
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

      it('should not update a ETLComponent', async () => {
        axiosStub.put.rejects(error);

        return service
          .update({})
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should partial update a ETLComponent', async () => {
        const patchObject = {
          group: 'BBBBBB',
          type: 'BBBBBB',
          updateTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          createTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          creater: 'BBBBBB',
          tenantId: 'BBBBBB',
          ...new ETLComponent(),
        };
        const returnedFromService = Object.assign(patchObject, elemDefault);

        const expected = { updateTime: currentDate, createTime: currentDate, ...returnedFromService };
        axiosStub.patch.resolves({ data: returnedFromService });

        return service.partialUpdate(patchObject).then(res => {
          expect(res).toMatchObject(expected);
        });
      });

      it('should not partial update a ETLComponent', async () => {
        axiosStub.patch.rejects(error);

        return service
          .partialUpdate({})
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should return a list of ETLComponent', async () => {
        const returnedFromService = {
          name: 'BBBBBB',
          code: 'BBBBBB',
          desc: 'BBBBBB',
          group: 'BBBBBB',
          type: 'BBBBBB',
          config: 'BBBBBB',
          status: 'BBBBBB',
          updateTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          createTime: dayjs(currentDate).format(DATE_TIME_FORMAT),
          creater: 'BBBBBB',
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

      it('should not return a list of ETLComponent', async () => {
        axiosStub.get.rejects(error);

        return service
          .retrieve()
          .then()
          .catch(err => {
            expect(err).toMatchObject(error);
          });
      });

      it('should delete a ETLComponent', async () => {
        axiosStub.delete.resolves({ ok: true });
        return service.delete(123).then(res => {
          expect(res.ok).toBeTruthy();
        });
      });

      it('should not delete a ETLComponent', async () => {
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
