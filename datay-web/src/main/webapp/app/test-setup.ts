import { beforeAll } from 'vitest';
import { config } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import axios from 'axios';

// Node 22+ 内置了未启用的 localStorage/sessionStorage 全局属性，会遮蔽 happy-dom 的实现。
// 这里显式绑定到 window，保证测试中的 bare localStorage/sessionStorage 可用。
Object.defineProperty(globalThis, 'localStorage', { value: window.localStorage, configurable: true });
Object.defineProperty(globalThis, 'sessionStorage', { value: window.sessionStorage, configurable: true });

// 组件中大量使用 Element Plus，测试挂载时注册插件，避免未知组件把作用域插槽当普通插槽渲染。
config.global.plugins = [ElementPlus];

beforeAll(() => {
  window.location.href = 'https://jhipster.tech/';

  // Make sure axios is never executed.
  axios.interceptors.request.use(request => {
    throw new Error(`Error axios should be mocked ${request.url}`);
  });
});
