import { Authority } from "@/shared/security/authority";

const JhiUserManagementComponent = () =>
  import("@/admin/user-management/user-management.vue");
const JhiUserManagementViewComponent = () =>
  import("@/admin/user-management/user-management-view.vue");
const JhiUserManagementEditComponent = () =>
  import("@/admin/user-management/user-management-edit.vue");
const JhiTenantManagementComponent = () =>
  import("@/admin/tenant-management/tenant-management.vue");
const JhiTenantManagementEditComponent = () =>
  import("@/admin/tenant-management/tenant-management-edit.vue");
const JhiTenantManagementViewComponent = () =>
  import("@/admin/tenant-management/tenant-management-view.vue");
const JhiRoleManagementComponent = () =>
  import("@/admin/role-management/role-management.vue");
const JhiRoleDataScopeComponent = () =>
  import("@/admin/role-data-scope/role-data-scope.vue");
const JhiDocsComponent = () => import("@/admin/docs/docs.vue");
const JhiConfigurationComponent = () =>
  import("@/admin/configuration/configuration.vue");
const JhiHealthComponent = () => import("@/admin/health/health.vue");
const JhiLogsComponent = () => import("@/admin/logs/logs.vue");
const JhiMetricsComponent = () => import("@/admin/metrics/metrics.vue");

export default [
  {
    path: "/admin/user-management",
    name: "JhiUser",
    component: JhiUserManagementComponent,
    meta: { authorities: [Authority.ADMIN] },
  },
  {
    path: "/admin/user-management/new",
    name: "JhiUserCreate",
    component: JhiUserManagementEditComponent,
    meta: { authorities: [Authority.ADMIN] },
  },
  {
    path: "/admin/user-management/:userId/edit",
    name: "JhiUserEdit",
    component: JhiUserManagementEditComponent,
    meta: { authorities: [Authority.ADMIN] },
  },
  {
    path: "/admin/user-management/:userId/view",
    name: "JhiUserView",
    component: JhiUserManagementViewComponent,
    meta: { authorities: [Authority.ADMIN] },
  },
  {
    path: "/admin/tenant-management",
    name: "JhiTenant",
    component: JhiTenantManagementComponent,
    meta: { authorities: [Authority.ADMIN] },
  },
  {
    path: "/admin/tenant-management/new",
    name: "JhiTenantCreate",
    component: JhiTenantManagementEditComponent,
    meta: { authorities: [Authority.ADMIN] },
  },
  {
    path: "/admin/tenant-management/:tenantId/edit",
    name: "JhiTenantEdit",
    component: JhiTenantManagementEditComponent,
    meta: { authorities: [Authority.ADMIN] },
  },
  {
    path: "/admin/tenant-management/:tenantId/view",
    name: "JhiTenantView",
    component: JhiTenantManagementViewComponent,
    meta: { authorities: [Authority.ADMIN] },
  },
  {
    path: "/admin/role-management",
    name: "JhiRoleManagement",
    component: JhiRoleManagementComponent,
    meta: { authorities: [Authority.ADMIN] },
  },
  {
    path: "/admin/role-data-scope",
    name: "JhiRoleDataScope",
    component: JhiRoleDataScopeComponent,
    meta: { authorities: [Authority.ADMIN] },
  },
  {
    path: "/admin/docs",
    name: "JhiDocsComponent",
    component: JhiDocsComponent,
    meta: { authorities: [Authority.ADMIN] },
  },
  {
    path: "/admin/health",
    name: "JhiHealthComponent",
    component: JhiHealthComponent,
    meta: { authorities: [Authority.ADMIN] },
  },
  {
    path: "/admin/logs",
    name: "JhiLogsComponent",
    component: JhiLogsComponent,
    meta: { authorities: [Authority.ADMIN] },
  },
  {
    path: "/admin/metrics",
    name: "JhiMetricsComponent",
    component: JhiMetricsComponent,
    meta: { authorities: [Authority.ADMIN] },
  },
  {
    path: "/admin/configuration",
    name: "JhiConfigurationComponent",
    component: JhiConfigurationComponent,
    meta: { authorities: [Authority.ADMIN] },
  },
];
