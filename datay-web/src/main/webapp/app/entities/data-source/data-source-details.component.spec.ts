import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import DataSourceDetails from './data-source-details.vue';
import DataSourceService from './data-source.service';
import AlertService from '@/shared/alert/alert.service';

type DataSourceDetailsComponentType = InstanceType<typeof DataSourceDetails>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const dataSourceSample = { id: 123 };

describe('Component Tests', () => {
  let alertService: AlertService;

  afterEach(() => {
    vitest.resetAllMocks();
  });

  describe('DataSource Management Detail Component', () => {
    let dataSourceServiceStub: SinonStubbedInstance<DataSourceService>;
    let mountOptions: MountingOptions<DataSourceDetailsComponentType>['global'];

    beforeEach(() => {
      route = {};
      dataSourceServiceStub = sinon.createStubInstance<DataSourceService>(DataSourceService);

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
          dataSourceService: () => dataSourceServiceStub,
        },
      };
    });

    describe('Navigate to details', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        dataSourceServiceStub.find.resolves(dataSourceSample);
        route = {
          params: {
            dataSourceId: `${123}`,
          },
        };
        const wrapper = shallowMount(DataSourceDetails, { global: mountOptions });
        const comp = wrapper.vm;
        // WHEN
        await comp.$nextTick();

        // THEN
        expect(comp.dataSource).toMatchObject(dataSourceSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        dataSourceServiceStub.find.resolves(dataSourceSample);
        const wrapper = shallowMount(DataSourceDetails, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
