import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import dayjs from 'dayjs';
import ETLTaskUpdate from './etl-task-update.vue';
import ETLTaskService from './etl-task.service';
import { DATE_TIME_LONG_FORMAT } from '@/shared/composables/date-format';
import AlertService from '@/shared/alert/alert.service';

type ETLTaskUpdateComponentType = InstanceType<typeof ETLTaskUpdate>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const eTLTaskSample = { id: 123 };

describe('Component Tests', () => {
  let mountOptions: MountingOptions<ETLTaskUpdateComponentType>['global'];
  let alertService: AlertService;

  describe('ETLTask Management Update Component', () => {
    let comp: ETLTaskUpdateComponentType;
    let eTLTaskServiceStub: SinonStubbedInstance<ETLTaskService>;

    beforeEach(() => {
      route = {};
      eTLTaskServiceStub = sinon.createStubInstance<ETLTaskService>(ETLTaskService);
      eTLTaskServiceStub.retrieve.onFirstCall().resolves(Promise.resolve([]));

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
          eTLTaskService: () => eTLTaskServiceStub,
        },
      };
    });

    afterEach(() => {
      vitest.resetAllMocks();
    });

    describe('load', () => {
      beforeEach(() => {
        const wrapper = shallowMount(ETLTaskUpdate, { global: mountOptions });
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
        const wrapper = shallowMount(ETLTaskUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.eTLTask = eTLTaskSample;
        eTLTaskServiceStub.update.resolves(eTLTaskSample);

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(eTLTaskServiceStub.update.calledWith(eTLTaskSample)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });

      it('Should call create service on save for new entity', async () => {
        // GIVEN
        const entity = {};
        eTLTaskServiceStub.create.resolves(entity);
        const wrapper = shallowMount(ETLTaskUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.eTLTask = entity;

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(eTLTaskServiceStub.create.calledWith(entity)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });
    });

    describe('Before route enter', () => {
      it('Should retrieve data', async () => {
        // GIVEN
        eTLTaskServiceStub.find.resolves(eTLTaskSample);
        eTLTaskServiceStub.retrieve.resolves([eTLTaskSample]);

        // WHEN
        route = {
          params: {
            eTLTaskId: `${eTLTaskSample.id}`,
          },
        };
        const wrapper = shallowMount(ETLTaskUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(comp.eTLTask).toMatchObject(eTLTaskSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        eTLTaskServiceStub.find.resolves(eTLTaskSample);
        const wrapper = shallowMount(ETLTaskUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
