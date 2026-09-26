import { ElMessage } from 'element-plus';

const toMessageType = (variant?: string): 'success' | 'warning' | 'info' | 'error' => {
  switch (variant) {
    case 'success':
      return 'success';
    case 'warning':
      return 'warning';
    case 'danger':
    case 'error':
      return 'error';
    default:
      return 'info';
  }
};

export const useAlertService = () => new AlertService();

export default class AlertService {
  showInfo(toastMessage: string, toastOptions?: any) {
    this.show(toastMessage, { variant: 'info', ...toastOptions });
  }

  showSuccess(toastMessage: string) {
    this.show(toastMessage, { variant: 'success' });
  }

  showWarning(toastMessage: string) {
    this.show(toastMessage, { variant: 'warning' });
  }

  showError(toastMessage: string) {
    this.show(toastMessage, { variant: 'danger' });
  }

  private show(message: string, options: { variant?: string; [key: string]: any }) {
    const { variant, autoHideDelay } = options;
    ElMessage({
      message,
      type: toMessageType(variant),
      duration: autoHideDelay ?? 5000,
      showClose: true,
    });
  }

  showHttpError(httpErrorResponse: any) {
    let errorMessage: string | null = null;
    switch (httpErrorResponse.status) {
      case 0:
        errorMessage = '服务器无法访问';
        break;

      case 400: {
        const arr = Object.keys(httpErrorResponse.headers ?? {});
        for (const entry of arr) {
          if (entry.toLowerCase().endsWith('app-error')) {
            errorMessage = httpErrorResponse.headers[entry];
          }
        }
        if (!errorMessage && httpErrorResponse.data?.fieldErrors) {
          errorMessage = 'Validation error';
        } else if (!errorMessage) {
          errorMessage = httpErrorResponse.data?.message;
        }
        break;
      }

      case 404:
        errorMessage = '该页面不存在.';
        break;

      default:
        errorMessage = httpErrorResponse.data?.message;
    }
    if (!errorMessage) {
      errorMessage = '请求失败';
    }
    this.showError(errorMessage);
  }
}
