import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import 'element-plus/dist/index.css';
import 'element-plus/theme-chalk/dark/css-vars.css';

import { setupAxiosInterceptors } from '@/shared/config/axios-interceptor';
import { isEmbedMode } from '@/shared/config/embed';
import { consumeUrlToken } from '@/shared/auth/url-token';
import '@/shared/dashboard/dashboard-theme.scss';
import App from './App.vue';
import './dashboard-viewer.scss';

// 独立看板常被 iframe 嵌入，先消费 URL 中的 JWT，再从地址栏清除。
consumeUrlToken();

setupAxiosInterceptors(
  () => {
    if (isEmbedMode()) {
      // 嵌入场景下不跳出 iframe，通知父应用重新获取 token 后刷新。
      window.parent?.postMessage({ type: 'datay:auth-required' }, '*');
      return Promise.reject(new Error('未认证'));
    }
    // 未认证：跳到主应用登录页，登录后可回到当前看板地址
    const redirect = encodeURIComponent(window.location.pathname + window.location.search);
    window.location.href = `/login?redirect=${redirect}`;
    return Promise.reject(new Error('未认证'));
  },
  error => Promise.reject(error),
);

createApp(App).use(ElementPlus, { locale: zhCn }).mount('#dashboard-app');
