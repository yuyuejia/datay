import { vitest } from 'vitest';
import { type MountingOptions, flushPromises, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import DataApiUpdate from './data-api-update.vue';
import DataApiService from './data-api.service';
import DataSourceService from '@/entities/data-source/data-source.service';
import AlertService from '@/shared/alert/alert.service';

type DataApiUpdateComponentType = InstanceType<typeof DataApiUpdate>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const dataApiSample = { id: 123 };

describe('Component Tests', () => {
  let mountOptions: MountingOptions<DataApiUpdateComponentType>['global'];
  let alertService: AlertService;

  describe('DataApi Management Update Component', () => {
    let comp: DataApiUpdateComponentType;
    let dataApiServiceStub: SinonStubbedInstance<DataApiService>;
    let dataSourceServiceStub: SinonStubbedInstance<DataSourceService>;

    beforeEach(() => {
      route = {};
      dataApiServiceStub = sinon.createStubInstance<DataApiService>(DataApiService);
      dataSourceServiceStub = sinon.createStubInstance<DataSourceService>(DataSourceService);
      dataSourceServiceStub.retrieve.resolves({ data: [] });

      alertService = new AlertService();

      mountOptions = {
        stubs: {
          'font-awesome-icon': true,
          'el-button': true,
          'el-form-item': true,
          'el-col': true,
          'el-row': true,
          'el-form': true,
          'el-tag': true,
          'b-form-input': true,
        },
        provide: {
          alertService,
          dataApiService: () => dataApiServiceStub,
          dataSourceService: () => dataSourceServiceStub,
        },
      };
    });

    afterEach(() => {
      vitest.resetAllMocks();
    });

    describe('save', () => {
      it('Should call update service on save for existing entity', async () => {
        // GIVEN
        const wrapper = shallowMount(DataApiUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataApi = {
          ...dataApiSample,
          name: 'x',
          code: 'order',
          dataSourceId: 1,
          sourceType: 'TABLE',
          schemaName: 's',
          tableName: 't',
          status: 'ENABLED',
        };
        dataApiServiceStub.update.resolves(dataApiSample);

        // WHEN
        comp.save();
        await flushPromises();

        // THEN
        expect(dataApiServiceStub.update.called).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });

      it('Should call create service on save for new entity', async () => {
        // GIVEN
        dataApiServiceStub.create.resolves({ id: 1 });
        const wrapper = shallowMount(DataApiUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataApi = {
          name: '订单',
          code: 'order_query',
          dataSourceId: 1,
          sourceType: 'TABLE',
          schemaName: 's',
          tableName: 't',
          status: 'ENABLED',
        };

        // WHEN
        comp.save();
        await flushPromises();

        // THEN
        expect(dataApiServiceStub.create.called).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });

      it('Should not save without a code', async () => {
        const wrapper = shallowMount(DataApiUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataApi = { name: '订单', dataSourceId: 1, sourceType: 'TABLE', schemaName: 's', tableName: 't' };

        comp.save();
        await comp.$nextTick();

        expect(dataApiServiceStub.create.called).toBeFalsy();
      });
    });

    describe('debug', () => {
      it('Should detect SQL placeholders and call debug service', async () => {
        const wrapper = shallowMount(DataApiUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataApi = {
          sourceType: 'SQL',
          dataSourceId: 1,
          sqlText: 'select * from orders where id = ${id}',
        };
        dataApiServiceStub.debug.resolves({ code: 0, data: [{ id: 1 }], columns: ['id'], total: 1 });

        comp.openDebug();
        await comp.$nextTick();

        expect(comp.debugVisible).toBe(true);
        expect(comp.debugParams.map(param => param.name)).toContain('id');

        comp.debugParams = [{ name: 'id', value: '1' }];
        await comp.sendDebug();
        await flushPromises();

        expect(dataApiServiceStub.debug.called).toBeTruthy();
        const [entity, params] = (dataApiServiceStub.debug as any).lastCall.args;
        expect(entity.sourceType).toBe('SQL');
        expect(entity.sqlText).toContain('${id}');
        expect(params).toEqual({ id: '1' });
        expect(comp.debugResult?.code).toBe(0);
      });

      it('Should detect API placeholders and skip token', async () => {
        const wrapper = shallowMount(DataApiUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataApi = { sourceType: 'API', status: 'ENABLED' };
        comp.apiConfig.url = 'https://api.example.com/orders?status=${status}';
        comp.apiConfig.body = '{"keyword":"${keyword}"}';
        comp.apiHeadersText = '{"Authorization":"Bearer ${authToken}"}';
        comp.apiConfig.preProcess = {
          enabled: true,
          url: 'https://auth.example.com/token',
          method: 'POST',
          headers: {},
          body: '{"appId":"${appId}"}',
          tokenPath: '$.data.token',
          cacheTtlSeconds: 0,
          sslVerify: true,
        };
        comp.preHeadersText = '{}';

        comp.openDebug();
        await comp.$nextTick();

        const names = comp.debugParams.map(param => param.name);
        expect(names).toContain('status');
        expect(names).toContain('keyword');
        expect(names).toContain('authToken');
        expect(names).toContain('appId');
        expect(names).not.toContain('token');
      });

      it('Should only prefill pagination for table source', async () => {
        const wrapper = shallowMount(DataApiUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataApi = {
          sourceType: 'TABLE',
          dataSourceId: 1,
          schemaName: 'public',
          tableName: 'orders',
        };
        comp.columns = [{ name: 'id' }, { name: 'status' }, { name: 'amount' }];

        comp.openDebug();
        await comp.$nextTick();

        expect(comp.debugParams.map(param => param.name)).toEqual(['pageNum', 'pageSize']);
        expect(comp.debugDetectedNames).toContain('status');
      });

      it('Should expose complete upstream body for API debug result', async () => {
        const wrapper = shallowMount(DataApiUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataApi = { sourceType: 'API', status: 'ENABLED' };
        comp.apiConfig.url = 'https://api.example.com/orders';
        const upstreamBody = JSON.stringify({
          code: 0,
          data: { items: [{ id: 1 }, { id: 2 }] },
          extra: 'x'.repeat(1000),
        });
        dataApiServiceStub.debug.resolves({
          code: 0,
          data: [{ id: 1 }, { id: 2 }],
          columns: ['id'],
          total: 2,
          upstream: { status: 200, body: upstreamBody },
        });

        comp.openDebug();
        await comp.$nextTick();
        await comp.sendDebug();
        await flushPromises();

        expect(comp.debugActiveTab).toBe('data');
        expect(comp.debugHasTable).toBe(true);
        expect(comp.debugShowRawBody).toBe(true);
        expect(comp.debugRawBody).toContain('"extra"');
        expect(comp.debugRawBody).toContain(JSON.stringify('x'.repeat(1000)));
      });

      it('Should expose complete response body for table source debug result', async () => {
        const wrapper = shallowMount(DataApiUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataApi = { sourceType: 'TABLE', dataSourceId: 1, schemaName: 's', tableName: 't' };
        comp.debugResult = {
          code: 0,
          message: 'success',
          data: [{ id: 1 }],
          columns: ['id'],
          total: 1,
          pageNum: 1,
          pageSize: 10,
          durationMs: 5,
        };
        await comp.$nextTick();

        expect(comp.debugShowRawBody).toBe(true);
        expect(comp.debugRawBody).toContain('"pageNum": 1');
        expect(comp.debugRawBody).toContain('"data"');
        expect(comp.debugRawBody).not.toContain('durationMs');
      });

      it('Should surface debug error message', async () => {
        const wrapper = shallowMount(DataApiUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataApi = { sourceType: 'SQL', dataSourceId: 1, sqlText: 'select * from orders' };
        dataApiServiceStub.debug.resolves({ code: 500, message: '服务执行失败', data: null });

        comp.openDebug();
        await comp.$nextTick();
        await comp.sendDebug();
        await flushPromises();

        expect(comp.debugError).toBe('服务执行失败');
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        const wrapper = shallowMount(DataApiUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
