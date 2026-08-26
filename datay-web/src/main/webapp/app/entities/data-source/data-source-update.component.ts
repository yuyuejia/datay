import { type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVuelidate } from '@vuelidate/core';

import DataSourceService from './data-source.service';
import { useDateFormat, useValidation } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import { DataSource, type IDataSource } from '@/shared/model/data-source.model';

// 数据库类型定义，基于DBType枚举类
interface DbType {
  name: string;
  displayName: string;
  jdbcUrlTemplate: string;
  supportedVersions: string[];
  defaultPort: string;
}

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'DataSourceUpdate',
  setup() {
    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const dataSource: Ref<IDataSource> = ref(new DataSource());
    const isSaving = ref(false);
    const isTestingConnection = ref(false);
    const currentLanguage = inject('currentLanguage', () => computed(() => navigator.language ?? 'zh-cn'), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);

    // 数据库类型列表
    const dbTypes: Ref<DbType[]> = ref([
      {
        name: 'MYSQL',
        displayName: 'MySQL',
        jdbcUrlTemplate: 'jdbc:mysql://{host}:{port}/{database}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai',
        supportedVersions: ['5.7', '8.0', '8.1', '8.2', '8.3', '8.4'],
        defaultPort: '3306',
      },
      {
        name: 'ORACLE',
        displayName: 'Oracle Database',
        jdbcUrlTemplate: 'jdbc:oracle:thin:@{host}:{port}:{database}',
        supportedVersions: ['11g', '12c', '18c', '19c', '21c', '23c'],
        defaultPort: '1521',
      },
      {
        name: 'POSTGRESQL',
        displayName: 'PostgreSQL',
        jdbcUrlTemplate: 'jdbc:postgresql://{host}:{port}/{database}',
        supportedVersions: ['9.6', '10', '11', '12', '13', '14', '15', '16'],
        defaultPort: '5432',
      },
      {
        name: 'SQLSERVER',
        displayName: 'Microsoft SQL Server',
        jdbcUrlTemplate: 'jdbc:sqlserver://{host}:{port};databaseName={database}',
        supportedVersions: ['2008', '2012', '2014', '2016', '2017', '2019', '2022'],
        defaultPort: '1433',
      },
      {
        name: 'DUCKDB',
        displayName: 'DuckDB',
        jdbcUrlTemplate: 'jdbc:duckdb:{database}',
        supportedVersions: ['0.8', '0.9', '1.0'],
        defaultPort: '',
      },
      {
        name: 'DUCKLAKE',
        displayName: 'DuckLake',
        jdbcUrlTemplate: 'ducklake:metadata.ducklake',
        supportedVersions: ['0.4'],
        defaultPort: '',
      },
      {
        name: 'CLICKHOUSE',
        displayName: 'ClickHouse',
        jdbcUrlTemplate: 'jdbc:clickhouse://{host}:{port}/{database}',
        supportedVersions: ['21.8', '22.3', '23.3', '24.1'],
        defaultPort: '8123',
      },
      {
        name: 'GREENPLUM',
        displayName: 'Greenplum',
        jdbcUrlTemplate: 'jdbc:postgresql://{host}:{port}/{database}',
        supportedVersions: ['5', '6', '7'],
        defaultPort: '5432',
      },
      {
        name: 'DORIS',
        displayName: 'Apache Doris',
        jdbcUrlTemplate: 'jdbc:mysql://{host}:{port}/{database}',
        supportedVersions: ['1.2', '2.0', '2.1'],
        defaultPort: '9030',
      },
      {
        name: 'AVRO',
        displayName: 'Apache Avro',
        jdbcUrlTemplate: 'jdbc:avro://{host}:{port}/{database}',
        supportedVersions: ['1.8', '1.9', '1.10', '1.11'],
        defaultPort: '9090',
      },
    ]);

    // 当前选中的数据库类型
    const selectedDbType = computed(() => {
      if (!dataSource.value.type) return null;
      return dbTypes.value.find(db => db.name === dataSource.value.type) || null;
    });

    // 数据库类型变更处理
    const onTypeChange = () => {
      const dbType = selectedDbType.value;
      if (dbType) {
        // 设置默认端口
        if (dbType.defaultPort && !dataSource.value.port) {
          dataSource.value.port = dbType.defaultPort;
        }
        // 设置默认版本
        if (dbType.supportedVersions.length > 0 && !dataSource.value.version) {
          dataSource.value.version = dbType.supportedVersions[dbType.supportedVersions.length - 1];
        }
        // 更新URL
        updateUrl();
      }
    };

    // 版本变更处理
    const onVersionChange = () => {
      // updateUrl();
    };

    // 更新URL
    const updateUrl = () => {
      const dbType = selectedDbType.value;
      if (!dbType) return;

      let urlTemplate = dbType.jdbcUrlTemplate;
      const hostname = dataSource.value.hostname || '{host}';
      const port = dataSource.value.port || '{port}';
      const schemaName = dataSource.value.schemaName || '{database}';

      // 替换模板中的占位符
      urlTemplate = urlTemplate.replace(/{host}/g, hostname);
      urlTemplate = urlTemplate.replace(/{port}/g, port);
      urlTemplate = urlTemplate.replace(/{database}/g, schemaName);

      // 对于MySQL，根据版本添加特定参数
      if (dbType.name === 'MYSQL' && dataSource.value.version) {
        if (dataSource.value.version.startsWith('5.')) {
          urlTemplate = urlTemplate.replace('serverTimezone=Asia/Shanghai', 'serverTimezone=UTC');
          if (!urlTemplate.includes('useSSL=')) {
            urlTemplate += '&useSSL=false';
          }
        } else {
          if (!urlTemplate.includes('useSSL=')) {
            urlTemplate += '&useSSL=true';
          }
        }
      }

      dataSource.value.url = urlTemplate;
    };

    const retrieveDataSource = async dataSourceId => {
      try {
        const res = await dataSourceService().find(dataSourceId);
        res.updateTime = new Date(res.updateTime);
        res.createTime = new Date(res.createTime);
        dataSource.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.dataSourceId) {
      retrieveDataSource(route.params.dataSourceId);
    }

    const validations = useValidation();
    const validationRules = {
      name: {},
      description: {},
      type: {},
      url: {},
      hostname: {},
      port: {},
      schemaName: {},
      username: {},
      password: {},
      updateTime: {},
      createTime: {},
      tenantId: {},
    };
    const v$ = useVuelidate(validationRules, dataSource as any);
    v$.value.$validate();

    // 测试连接方法
    const testConnection = async () => {
      if (!dataSource.value.type || !dataSource.value.url || !dataSource.value.username) {
        alertService.showError('请先填写数据库类型、URL和用户名');
        return;
      }

      isTestingConnection.value = true;
      try {
        const result = await dataSourceService().testConnection(dataSource.value);
        if (result.success) {
          alertService.showSuccess(result.message || '连接测试成功');
        } else {
          alertService.showError(result.message || '连接测试失败');
        }
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        isTestingConnection.value = false;
      }
    };

    return {
      dataSourceService,
      alertService,
      dataSource,
      previousState,
      isSaving,
      isTestingConnection,
      currentLanguage,
      v$,
      dbTypes,
      selectedDbType,
      onTypeChange,
      onVersionChange,
      updateUrl,
      testConnection,
      ...useDateFormat({ entityRef: dataSource }),
    };
  },
  created(): void {},
  methods: {
    save(): void {
      this.isSaving = true;
      if (this.dataSource.id) {
        this.dataSourceService()
          .update(this.dataSource)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showInfo(`A DataSource is updated with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.dataSourceService()
          .create(this.dataSource)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showSuccess(`A DataSource is created with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      }
    },
  },
});
