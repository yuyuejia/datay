import { beforeAll } from 'vitest';
import { config } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import axios from 'axios';

// Node 22+ 内置了未启用的 localStorage/sessionStorage 全局属性，会遮蔽 happy-dom 的实现。
// 这里显式绑定到 window，保证测试中的 bare localStorage/sessionStorage 可用。
// 注意：Node 未提供 --localstorage-file 时 window.localStorage 为 undefined，
// 因此用内存实现兜底，避免不同 Node 版本导致测试无法运行。
const createMemoryStorage = (): Storage => {
  const store = new Map<string, string>();
  return {
    get length() {
      return store.size;
    },
    clear: () => store.clear(),
    getItem: key => store.get(key) ?? null,
    key: index => Array.from(store.keys())[index] ?? null,
    removeItem: key => store.delete(key),
    setItem: (key, value) => {
      store.set(key, String(value));
    },
  } as Storage;
};

const resolveStorage = (candidate: Storage | undefined): Storage => candidate ?? createMemoryStorage();

const bindWebStorages = () => {
  Object.defineProperty(globalThis, 'localStorage', {
    value: resolveStorage(typeof window !== 'undefined' ? window.localStorage : undefined),
    configurable: true,
  });
  Object.defineProperty(globalThis, 'sessionStorage', {
    value: resolveStorage(typeof window !== 'undefined' ? window.sessionStorage : undefined),
    configurable: true,
  });
};

// 组件中大量使用 Element Plus，测试挂载时注册插件，避免未知组件把作用域插槽当普通插槽渲染。
config.global.plugins = [ElementPlus];

beforeAll(() => {
  window.location.href = 'https://jhipster.tech/';
  bindWebStorages();

  // Make sure axios is never executed.
  axios.interceptors.request.use(request => {
    throw new Error(`Error axios should be mocked ${request.url}`);
  });
});
