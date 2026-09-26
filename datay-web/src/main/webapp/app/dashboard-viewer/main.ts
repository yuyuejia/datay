import { createApp } from "vue";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";

import { setupAxiosInterceptors } from "@/shared/config/axios-interceptor";
import App from "./App.vue";
import "./dashboard-viewer.scss";

setupAxiosInterceptors(
  () => {
    // 未认证：跳到主应用登录页，登录后可回到当前看板地址
    const redirect = encodeURIComponent(
      window.location.pathname + window.location.search,
    );
    window.location.href = `/login?redirect=${redirect}`;
    return Promise.reject(new Error("未认证"));
  },
  (error) => Promise.reject(error),
);

createApp(App).use(ElementPlus).mount("#dashboard-app");
