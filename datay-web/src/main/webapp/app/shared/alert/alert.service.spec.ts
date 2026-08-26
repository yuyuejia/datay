import { vitest } from 'vitest';
import AlertService from './alert.service';

describe('Alert Service test suite', () => {
  let toastStub: vitest.Mock;
  let alertService: AlertService;

  beforeEach(() => {
    toastStub = vitest.fn();
    alertService = new AlertService({
      bvToast: {
        toast: toastStub,
      } as any,
    });
  });

  it('should show error toast with translation/message', async () => {
    const message = 'translatedMessage';

    // WHEN
    alertService.showError(message);

    // THEN
    expect(toastStub).toBeCalledTimes(1);
    expect(toastStub).toHaveBeenCalledWith(message, {
      toaster: 'b-toaster-top-center',
      title: 'Error',
      variant: 'danger',
      solid: true,
      autoHideDelay: 5000,
    });
  });

  it('should show not reachable toast when http status = 0', async () => {
    const message = 'Server not reachable';
    const httpErrorResponse = {
      status: 0,
    };

    // WHEN
    alertService.showHttpError(httpErrorResponse);

    // THEN
    expect(toastStub).toBeCalledTimes(1);
    expect(toastStub).toHaveBeenCalledWith(message, {
      toaster: 'b-toaster-top-center',
      title: 'Error',
      variant: 'danger',
      solid: true,
      autoHideDelay: 5000,
    });
  });

  it('should show parameterized error toast when http status = 400 and entity headers', async () => {
    const message = 'Updation Error';
    const httpErrorResponse = {
      status: 400,
      headers: {
        'x-jhipsterapp-error': message,
        'x-jhipsterapp-params': 'dummyEntity',
      },
    };

    // WHEN
    alertService.showHttpError(httpErrorResponse);

    // THEN
    expect(toastStub).toHaveBeenCalledWith(message, {
      toaster: 'b-toaster-top-center',
      title: 'Error',
      variant: 'danger',
      solid: true,
      autoHideDelay: 5000,
    });
  });

  it('should show error toast with data.message when http status = 400 and entity headers', async () => {
    const message = 'Validation error';
    const httpErrorResponse = {
      status: 400,
      headers: {
        'x-jhipsterapp-error400': 'error',
        'x-jhipsterapp-params400': 'dummyEntity',
      },
      data: {
        message,
        fieldErrors: {
          field1: 'error1',
        },
      },
    };

    // WHEN
    alertService.showHttpError(httpErrorResponse);

    // THEN
    expect(toastStub).toBeCalledTimes(1);
    expect(toastStub).toHaveBeenCalledWith(message, {
      toaster: 'b-toaster-top-center',
      title: 'Error',
      variant: 'danger',
      solid: true,
      autoHideDelay: 5000,
    });
  });

  it('should show error toast when http status = 404', async () => {
    const message = 'The page does not exist.';
    const httpErrorResponse = {
      status: 404,
    };

    // WHEN
    alertService.showHttpError(httpErrorResponse);

    // THEN
    expect(toastStub).toBeCalledTimes(1);
    expect(toastStub).toHaveBeenCalledWith(message, {
      toaster: 'b-toaster-top-center',
      title: 'Error',
      variant: 'danger',
      solid: true,
      autoHideDelay: 5000,
    });
  });

  it('should show error toast when http status != 400,404', async () => {
    const message = 'Error 500';
    const httpErrorResponse = {
      status: 500,
      data: {
        message,
      },
    };

    // WHEN
    alertService.showHttpError(httpErrorResponse);

    // THEN
    expect(toastStub).toBeCalledTimes(1);
    expect(toastStub).toHaveBeenCalledWith(message, {
      toaster: 'b-toaster-top-center',
      title: 'Error',
      variant: 'danger',
      solid: true,
      autoHideDelay: 5000,
    });
  });
});
