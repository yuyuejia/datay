import axios from 'axios';
import sinon from 'sinon';

import DataQueryService from './data-query.service';

const axiosPostStub = sinon.stub(axios, 'post');

describe('DataQueryService', () => {
  let service: DataQueryService;

  beforeEach(() => {
    service = new DataQueryService();
    axiosPostStub.reset();
  });

  it('should send the selected row limit to the query endpoint', async () => {
    axiosPostStub.resolves({ data: { rows: [] } });

    await service.executeQuery('ds1', 'select * from orders', 500);

    expect(axiosPostStub.lastCall.args[0]).toBe('api/data-sources/ds1/query');
    expect(axiosPostStub.lastCall.args[1]).toEqual({ sql: 'select * from orders', limit: 500 });
  });

  it('should send null when no row limit is provided', async () => {
    axiosPostStub.resolves({ data: { rows: [] } });

    await service.executeQuery('ds1', 'select 1');

    expect(axiosPostStub.lastCall.args[1]).toEqual({ sql: 'select 1', limit: null });
  });

  it('should reject when the query endpoint fails', async () => {
    const error = { response: { status: 500, data: { message: 'boom' } } };
    axiosPostStub.rejects(error);

    await expect(service.executeQuery('ds1', 'select 1', 100)).rejects.toMatchObject(error);
  });
});
