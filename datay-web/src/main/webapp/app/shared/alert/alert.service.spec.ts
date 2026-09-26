import { vitest } from 'vitest';
import { ElMessage } from 'element-plus';
import AlertService from './alert.service';

vitest.mock('element-plus', () => ({
  ElMessage: vitest.fn(),
}));

describe('Alert Service test suite', () => {
  let alertService: AlertService;

  beforeEach(() => {
    vitest.clearAllMocks();
    alertService = new AlertService();
  });

  it('should show error message', () => {
    alertService.showError('translatedMessage');

    expect(ElMessage).toHaveBeenCalledTimes(1);
    expect(ElMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        message: 'translatedMessage',
        type: 'error',
      }),
    );
  });

  it('should show not reachable message when http status = 0', () => {
    alertService.showHttpError({ status: 0 });

    expect(ElMessage).toHaveBeenCalledTimes(1);
    expect(ElMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        message: '服务器无法访问',
        type: 'error',
      }),
    );
  });

  it('should show parameterized error message when http status = 400 and entity headers', () => {
    alertService.showHttpError({
      status: 400,
      headers: {
        'x-jhipsterapp-error': 'Updation Error',
        'x-jhipsterapp-params': 'dummyEntity',
      },
    });

    expect(ElMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        message: 'Updation Error',
        type: 'error',
      }),
    );
  });

  it('should show validation error message when http status = 400 and fieldErrors', () => {
    alertService.showHttpError({
      status: 400,
      headers: {
        'x-jhipsterapp-error400': 'error',
        'x-jhipsterapp-params400': 'dummyEntity',
      },
      data: {
        message: 'Validation error',
        fieldErrors: {
          field1: 'error1',
        },
      },
    });

    expect(ElMessage).toHaveBeenCalledTimes(1);
    expect(ElMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        message: 'Validation error',
        type: 'error',
      }),
    );
  });

  it('should show error message when http status = 404', () => {
    alertService.showHttpError({ status: 404 });

    expect(ElMessage).toHaveBeenCalledTimes(1);
    expect(ElMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        message: '该页面不存在.',
        type: 'error',
      }),
    );
  });

  it('should show error message when http status != 400,404', () => {
    alertService.showHttpError({ status: 500, data: { message: 'Error 500' } });

    expect(ElMessage).toHaveBeenCalledTimes(1);
    expect(ElMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        message: 'Error 500',
        type: 'error',
      }),
    );
  });
});
