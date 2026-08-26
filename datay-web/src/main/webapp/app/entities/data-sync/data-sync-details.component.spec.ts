import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import DataSyncDetails from './data-sync-details.vue';
import DataSyncService from './data-sync.service';
import AlertService from '@/shared/alert/alert.service';

type DataSyncDetailsComponentType = InstanceType<typeof DataSyncDetails>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const dataSyncSample = { id: 123 };

describe('Component Tests', () => {
  let alertService: AlertService;

  afterEach(() => {
    vitest.resetAllMocks();
  });

  describe('DataSync Management Detail Component', () => {
    let dataSyncServiceStub: SinonStubbedInstance<DataSyncService>;
    let mountOptions: MountingOptions<DataSyncDetailsComponentType>['global'];

    beforeEach(() => {
      route = {};
      dataSyncServiceStub = sinon.createStubInstance<DataSyncService>(DataSyncService);

      alertService = new AlertService({
        bvToast: {
          toast: vitest.fn(),
        } as any,
      });

      mountOptions = {
        stubs: {
          'font-awesome-icon': true,
          'router-link': true,
        },
        provide: {
          alertService,
          dataSyncService: () => dataSyncServiceStub,
        },
      };
    });

    describe('Navigate to details', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        dataSyncServiceStub.find.resolves(dataSyncSample);
        route = {
          params: {
            dataSyncId: `${123}`,
          },
        };
        const wrapper = shallowMount(DataSyncDetails, { global: mountOptions });
        const comp = wrapper.vm;
        // WHEN
        await comp.$nextTick();

        // THEN
        expect(comp.dataSync).toMatchObject(dataSyncSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        dataSyncServiceStub.find.resolves(dataSyncSample);
        const wrapper = shallowMount(DataSyncDetails, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
