import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import DataSyncTableConfigDetails from './data-sync-table-config-details.vue';
import DataSyncTableConfigService from './data-sync-table-config.service';
import AlertService from '@/shared/alert/alert.service';

type DataSyncTableConfigDetailsComponentType = InstanceType<typeof DataSyncTableConfigDetails>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const dataSyncTableConfigSample = { id: 123 };

describe('Component Tests', () => {
  let alertService: AlertService;

  afterEach(() => {
    vitest.resetAllMocks();
  });

  describe('DataSyncTableConfig Management Detail Component', () => {
    let dataSyncTableConfigServiceStub: SinonStubbedInstance<DataSyncTableConfigService>;
    let mountOptions: MountingOptions<DataSyncTableConfigDetailsComponentType>['global'];

    beforeEach(() => {
      route = {};
      dataSyncTableConfigServiceStub = sinon.createStubInstance<DataSyncTableConfigService>(DataSyncTableConfigService);

      alertService = new AlertService();

      mountOptions = {
        stubs: {
          'font-awesome-icon': true,
          'router-link': true,
        },
        provide: {
          alertService,
          dataSyncTableConfigService: () => dataSyncTableConfigServiceStub,
        },
      };
    });

    describe('Navigate to details', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        dataSyncTableConfigServiceStub.find.resolves(dataSyncTableConfigSample);
        route = {
          params: {
            dataSyncTableConfigId: `${123}`,
          },
        };
        const wrapper = shallowMount(DataSyncTableConfigDetails, { global: mountOptions });
        const comp = wrapper.vm;
        // WHEN
        await comp.$nextTick();

        // THEN
        expect(comp.dataSyncTableConfig).toMatchObject(dataSyncTableConfigSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        dataSyncTableConfigServiceStub.find.resolves(dataSyncTableConfigSample);
        const wrapper = shallowMount(DataSyncTableConfigDetails, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
