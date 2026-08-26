import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import ETLTaskDetails from './etl-task-details.vue';
import ETLTaskService from './etl-task.service';
import AlertService from '@/shared/alert/alert.service';

type ETLTaskDetailsComponentType = InstanceType<typeof ETLTaskDetails>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const eTLTaskSample = { id: 123 };
const taskInstancesSample = {
  headers: { 'x-total-count': '0' },
  data: [],
};

describe('Component Tests', () => {
  let alertService: AlertService;

  afterEach(() => {
    vitest.resetAllMocks();
  });

  describe('ETLTask Management Detail Component', () => {
    let eTLTaskServiceStub: SinonStubbedInstance<ETLTaskService>;
    let mountOptions: MountingOptions<ETLTaskDetailsComponentType>['global'];

    beforeEach(() => {
      route = {};
      eTLTaskServiceStub = sinon.createStubInstance<ETLTaskService>(ETLTaskService);

      alertService = new AlertService({
        bvToast: {
          toast: vitest.fn(),
        } as any,
      });

      mountOptions = {
        stubs: {
          'font-awesome-icon': true,
          'router-link': true,
          'el-table': true,
          'el-table-column': true,
          'el-button': true,
          'b-modal': true,
          'b-pagination': true,
          'jhi-item-count': true,
        },
        provide: {
          alertService,
          eTLTaskService: () => eTLTaskServiceStub,
        },
      };
    });

    describe('Navigate to details', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        eTLTaskServiceStub.find.resolves(eTLTaskSample);
        eTLTaskServiceStub.getTaskInstances.resolves(taskInstancesSample);
        route = {
          params: {
            eTLTaskId: `${123}`,
          },
        };
        const wrapper = shallowMount(ETLTaskDetails, { global: mountOptions });
        const comp = wrapper.vm;
        // WHEN
        await comp.$nextTick();

        // THEN
        expect(comp.eTLTask).toMatchObject(eTLTaskSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        eTLTaskServiceStub.find.resolves(eTLTaskSample);
        eTLTaskServiceStub.getTaskInstances.resolves(taskInstancesSample);
        const wrapper = shallowMount(ETLTaskDetails, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
