import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import JobDependDetails from './job-depend-details.vue';
import JobDependService from './job-depend.service';
import AlertService from '@/shared/alert/alert.service';

type JobDependDetailsComponentType = InstanceType<typeof JobDependDetails>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const jobDependSample = { id: 123 };

describe('Component Tests', () => {
  let alertService: AlertService;

  afterEach(() => {
    vitest.resetAllMocks();
  });

  describe('JobDepend Management Detail Component', () => {
    let jobDependServiceStub: SinonStubbedInstance<JobDependService>;
    let mountOptions: MountingOptions<JobDependDetailsComponentType>['global'];

    beforeEach(() => {
      route = {};
      jobDependServiceStub = sinon.createStubInstance<JobDependService>(JobDependService);

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
          jobDependService: () => jobDependServiceStub,
        },
      };
    });

    describe('Navigate to details', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        jobDependServiceStub.find.resolves(jobDependSample);
        route = {
          params: {
            jobDependId: `${123}`,
          },
        };
        const wrapper = shallowMount(JobDependDetails, { global: mountOptions });
        const comp = wrapper.vm;
        // WHEN
        await comp.$nextTick();

        // THEN
        expect(comp.jobDepend).toMatchObject(jobDependSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        jobDependServiceStub.find.resolves(jobDependSample);
        const wrapper = shallowMount(JobDependDetails, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
