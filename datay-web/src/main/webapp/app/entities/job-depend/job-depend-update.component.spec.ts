import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import dayjs from 'dayjs';
import JobDependUpdate from './job-depend-update.vue';
import JobDependService from './job-depend.service';
import { DATE_TIME_LONG_FORMAT } from '@/shared/composables/date-format';
import AlertService from '@/shared/alert/alert.service';

type JobDependUpdateComponentType = InstanceType<typeof JobDependUpdate>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const jobDependSample = { id: 123 };

describe('Component Tests', () => {
  let mountOptions: MountingOptions<JobDependUpdateComponentType>['global'];
  let alertService: AlertService;

  describe('JobDepend Management Update Component', () => {
    let comp: JobDependUpdateComponentType;
    let jobDependServiceStub: SinonStubbedInstance<JobDependService>;

    beforeEach(() => {
      route = {};
      jobDependServiceStub = sinon.createStubInstance<JobDependService>(JobDependService);
      jobDependServiceStub.retrieve.onFirstCall().resolves(Promise.resolve([]));

      alertService = new AlertService({
        bvToast: {
          toast: vitest.fn(),
        } as any,
      });

      mountOptions = {
        stubs: {
          'font-awesome-icon': true,
          'b-input-group': true,
          'b-input-group-prepend': true,
          'b-form-datepicker': true,
          'b-form-input': true,
        },
        provide: {
          alertService,
          jobDependService: () => jobDependServiceStub,
        },
      };
    });

    afterEach(() => {
      vitest.resetAllMocks();
    });

    describe('load', () => {
      beforeEach(() => {
        const wrapper = shallowMount(JobDependUpdate, { global: mountOptions });
        comp = wrapper.vm;
      });
      it('Should convert date from string', () => {
        // GIVEN
        const date = new Date('2019-10-15T11:42:02Z');

        // WHEN
        const convertedDate = comp.convertDateTimeFromServer(date);

        // THEN
        expect(convertedDate).toEqual(dayjs(date).format(DATE_TIME_LONG_FORMAT));
      });

      it('Should not convert date if date is not present', () => {
        expect(comp.convertDateTimeFromServer(null)).toBeNull();
      });
    });

    describe('save', () => {
      it('Should call update service on save for existing entity', async () => {
        // GIVEN
        const wrapper = shallowMount(JobDependUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.jobDepend = jobDependSample;
        jobDependServiceStub.update.resolves(jobDependSample);

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(jobDependServiceStub.update.calledWith(jobDependSample)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });

      it('Should call create service on save for new entity', async () => {
        // GIVEN
        const entity = {};
        jobDependServiceStub.create.resolves(entity);
        const wrapper = shallowMount(JobDependUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.jobDepend = entity;

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(jobDependServiceStub.create.calledWith(entity)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });
    });

    describe('Before route enter', () => {
      it('Should retrieve data', async () => {
        // GIVEN
        jobDependServiceStub.find.resolves(jobDependSample);
        jobDependServiceStub.retrieve.resolves([jobDependSample]);

        // WHEN
        route = {
          params: {
            jobDependId: `${jobDependSample.id}`,
          },
        };
        const wrapper = shallowMount(JobDependUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(comp.jobDepend).toMatchObject(jobDependSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        jobDependServiceStub.find.resolves(jobDependSample);
        const wrapper = shallowMount(JobDependUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
