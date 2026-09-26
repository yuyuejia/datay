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
