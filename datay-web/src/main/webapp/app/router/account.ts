import { Authority } from '@/shared/security/authority';

const Login = () => import('@/account/login/login.vue');
const Register = () => import('@/account/register/register.vue');
const Activate = () => import('@/account/activate/activate.vue');
const ResetPasswordInit = () => import('@/account/reset-password/init/reset-password-init.vue');
const ResetPasswordFinish = () => import('@/account/reset-password/finish/reset-password-finish.vue');
const ChangePassword = () => import('@/account/change-password/change-password.vue');
const Settings = () => import('@/account/settings/settings.vue');

export default [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { public: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: Register,
    meta: { public: true },
  },
  {
    path: '/account/activate',
    name: 'Activate',
    component: Activate,
    meta: { public: true },
  },
  {
    path: '/account/reset/request',
    name: 'ResetPasswordInit',
    component: ResetPasswordInit,
    meta: { public: true },
  },
  {
    path: '/account/reset/finish',
    name: 'ResetPasswordFinish',
    component: ResetPasswordFinish,
    meta: { public: true },
  },
  {
    path: '/account/password',
    name: 'ChangePassword',
    component: ChangePassword,
    meta: { authorities: [Authority.USER] },
  },
  {
    path: '/account/settings',
    name: 'Settings',
    component: Settings,
    meta: { authorities: [Authority.USER] },
  },
];
