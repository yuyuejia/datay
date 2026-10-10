export interface TabDefinition {
  /** Canonical menu path, used to resolve which tab owns a nested route. */
  path: string;
  /** Display title shown on the tab. */
  title: string;
  /** Component name, must match the page component's resolved name for keep-alive. */
  componentName: string;
  /** Whether the tab can be closed by the user. */
  closable: boolean;
}

export const HOME_TAB_NAME = 'Home';

export const HOME_TAB: TabDefinition = {
  path: '/',
  title: '首页',
  componentName: 'Home',
  closable: false,
};

/**
 * Routes that open as application tabs, keyed by route name.
 *
 * Only top-level menu entries are listed here; nested pages such as
 * create/edit/detail/design routes are rendered inside their owning tab.
 */
export const TAB_DEFINITIONS: Record<string, TabDefinition> = {
  // 首页
  Home: HOME_TAB,
  // 数据源
  DataSource: { path: '/data-source', title: '数据源管理', componentName: 'DataSource', closable: true },
  FileManagement: { path: '/file-management', title: '文件管理', componentName: 'FileManagement', closable: true },
  // 数据集成
  ETLTask: { path: '/etl-task', title: '数据集成', componentName: 'ETLTask', closable: true },
  // 数据模型
  DataModel: { path: '/data-model', title: '维度建模', componentName: 'DataModel', closable: true },
  Metric: { path: '/metric', title: '指标管理', componentName: 'Metric', closable: true },
  // 数据开发
  SqlJob: { path: '/sql-job', title: 'SQL 任务', componentName: 'SqlJob', closable: true },
  DagJob: { path: '/dag-job', title: '任务编排', componentName: 'DagJob', closable: true },
  ShellJob: { path: '/shell-job', title: 'Shell 任务', componentName: 'ShellJob', closable: true },
  JobInstance: { path: '/job-instance', title: '任务实例', componentName: 'JobInstance', closable: true },
  // 数据应用
  MetricAi: { path: '/metric-ai', title: '智能问数', componentName: 'MetricAi', closable: true },
  DataApi: { path: '/data-api', title: 'API 服务', componentName: 'DataApi', closable: true },
  AnalysisDashboard: { path: '/analysis-dashboard', title: '分析看板', componentName: 'AnalysisDashboard', closable: true },
  AppPackage: { path: '/app-package', title: '应用市场', componentName: 'AppPackage', closable: true },
  // 账号菜单
  Settings: { path: '/account/settings', title: '设置', componentName: 'Settings', closable: true },
  ChangePassword: { path: '/account/password', title: '密码', componentName: 'ChangePassword', closable: true },
  // 系统管理
  JhiUser: { path: '/admin/user-management', title: '用户管理', componentName: 'JhiUserManagementComponent', closable: true },
  JhiTenant: { path: '/admin/tenant-management', title: '租户管理', componentName: 'JhiTenantManagementComponent', closable: true },
  JhiRoleManagement: { path: '/admin/role-management', title: '角色管理', componentName: 'JhiRoleManagement', closable: true },
  JhiRoleDataScope: { path: '/admin/role-data-scope', title: '数据权限', componentName: 'JhiRoleDataScope', closable: true },
  ServiceConfig: { path: '/service-config', title: '服务配置', componentName: 'ServiceConfigSettings', closable: true },
};

/**
 * Find the tab definition that owns the given path (longest matching path
 * prefix), used to attach a nested page to its menu tab.
 */
export const findTabDefinitionByPath = (path: string): { name: string; def: TabDefinition } | undefined => {
  let match: { name: string; def: TabDefinition } | undefined;
  for (const [name, def] of Object.entries(TAB_DEFINITIONS)) {
    if (name === HOME_TAB_NAME) continue;
    if (path === def.path || path.startsWith(`${def.path}/`)) {
      if (!match || def.path.length > match.def.path.length) {
        match = { name, def };
      }
    }
  }
  return match;
};
