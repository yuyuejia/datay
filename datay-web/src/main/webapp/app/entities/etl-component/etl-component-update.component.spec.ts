import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import dayjs from 'dayjs';
import ETLComponentUpdate from './etl-component-update.vue';
import ETLComponentService from './etl-component.service';
import { DATE_TIME_LONG_FORMAT } from '@/shared/composables/date-format';
import AlertService from '@/shared/alert/alert.service';

type ETLComponentUpdateComponentType = InstanceType<typeof ETLComponentUpdate>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const eTLComponentSample = { id: 123 };

describe('Component Tests', () => {
  let mountOptions: MountingOptions<ETLComponentUpdateComponentType>['global'];
  let alertService: AlertService;

  describe('ETLComponent Management Update Component', () => {
    let comp: ETLComponentUpdateComponentType;
    let eTLComponentServiceStub: SinonStubbedInstance<ETLComponentService>;

    beforeEach(() => {
      route = {};
      eTLComponentServiceStub = sinon.createStubInstance<ETLComponentService>(ETLComponentService);
      eTLComponentServiceStub.retrieve.onFirstCall().resolves(Promise.resolve([]));

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
          eTLComponentService: () => eTLComponentServiceStub,
        },
      };
    });

    afterEach(() => {
      vitest.resetAllMocks();
    });

    describe('load', () => {
      beforeEach(() => {
        const wrapper = shallowMount(ETLComponentUpdate, { global: mountOptions });
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
        const wrapper = shallowMount(ETLComponentUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.eTLComponent = eTLComponentSample;
        eTLComponentServiceStub.update.resolves(eTLComponentSample);

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(eTLComponentServiceStub.update.calledWith(eTLComponentSample)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });

      it('Should call create service on save for new entity', async () => {
        // GIVEN
        const entity = {};
        eTLComponentServiceStub.create.resolves(entity);
        const wrapper = shallowMount(ETLComponentUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.eTLComponent = entity;

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(eTLComponentServiceStub.create.calledWith(entity)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });
    });

    describe('Before route enter', () => {
      it('Should retrieve data', async () => {
        // GIVEN
        eTLComponentServiceStub.find.resolves(eTLComponentSample);
        eTLComponentServiceStub.retrieve.resolves([eTLComponentSample]);

        // WHEN
        route = {
          params: {
            eTLComponentId: `${eTLComponentSample.id}`,
          },
        };
        const wrapper = shallowMount(ETLComponentUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(comp.eTLComponent).toMatchObject(eTLComponentSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        eTLComponentServiceStub.find.resolves(eTLComponentSample);
        const wrapper = shallowMount(ETLComponentUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
