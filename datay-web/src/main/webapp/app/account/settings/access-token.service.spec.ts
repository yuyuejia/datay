import axios from 'axios';
import sinon from 'sinon';

import AccessTokenService from './access-token.service';

const axiosStub = {
  get: sinon.stub(axios, 'get'),
  post: sinon.stub(axios, 'post'),
  patch: sinon.stub(axios, 'patch'),
  delete: sinon.stub(axios, 'delete'),
};

describe('AccessTokenService', () => {
  let service: AccessTokenService;

  beforeEach(() => {
    service = new AccessTokenService();
    axiosStub.get.reset();
    axiosStub.post.reset();
    axiosStub.patch.reset();
    axiosStub.delete.reset();
  });

  it('should list access tokens', async () => {
    axiosStub.get.resolves({ data: [{ id: '1', name: 'Claude Desktop' }] });

    const tokens = await service.list();

    expect(tokens).toHaveLength(1);
    expect(axiosStub.get.lastCall.args[0]).toBe('api/account/access-tokens');
  });

  it('should create an access token', async () => {
    const created = { token: 'mcp_1_abc', accessToken: { id: '1', name: 'Cursor' } };
    axiosStub.post.resolves({ data: created });

    const result = await service.create({ name: 'Cursor', description: '开发环境' });

    expect(result).toMatchObject(created);
    expect(axiosStub.post.lastCall.args[0]).toBe('api/account/access-tokens');
  });

  it('should update an access token', async () => {
    axiosStub.patch.resolves({ data: { id: '1', name: 'Cursor', status: 'DISABLED' } });

    const result = await service.update('1', { status: 'DISABLED' });

    expect(result.status).toBe('DISABLED');
    expect(axiosStub.patch.lastCall.args[0]).toBe('api/account/access-tokens/1');
  });

  it('should rotate an access token', async () => {
    axiosStub.post.resolves({ data: { token: 'mcp_1_new', accessToken: { id: '1', name: 'Cursor' } } });

    const result = await service.rotate('1');

    expect(result.token).toBe('mcp_1_new');
    expect(axiosStub.post.lastCall.args[0]).toBe('api/account/access-tokens/1/rotate');
  });

  it('should delete an access token', async () => {
    axiosStub.delete.resolves({});

    await service.remove('1');

    expect(axiosStub.delete.lastCall.args[0]).toBe('api/account/access-tokens/1');
  });
});
