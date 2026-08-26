import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import dayjs from 'dayjs';
import DpTableUpdate from './dp-table-update.vue';
import DpTableService from './dp-table.service';
import { DATE_TIME_LONG_FORMAT } from '@/shared/composables/date-format';
import AlertService from '@/shared/alert/alert.service';

type DpTableUpdateComponentType = InstanceType<typeof DpTableUpdate>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const dpTableSample = { id: 123 };

describe('Component Tests', () => {
  let mountOptions: MountingOptions<DpTableUpdateComponentType>['global'];
  let alertService: AlertService;

  describe('DpTable Management Update Component', () => {
    let comp: DpTableUpdateComponentType;
    let dpTableServiceStub: SinonStubbedInstance<DpTableService>;

    beforeEach(() => {
      route = {};
      dpTableServiceStub = sinon.createStubInstance<DpTableService>(DpTableService);
      dpTableServiceStub.retrieve.onFirstCall().resolves(Promise.resolve([]));

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
          dpTableService: () => dpTableServiceStub,
        },
      };
    });

    afterEach(() => {
      vitest.resetAllMocks();
    });

    describe('load', () => {
      beforeEach(() => {
        const wrapper = shallowMount(DpTableUpdate, { global: mountOptions });
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
        const wrapper = shallowMount(DpTableUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dpTable = dpTableSample;
        dpTableServiceStub.update.resolves(dpTableSample);

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(dpTableServiceStub.update.calledWith(dpTableSample)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });

      it('Should call create service on save for new entity', async () => {
        // GIVEN
        const entity = {};
        dpTableServiceStub.create.resolves(entity);
        const wrapper = shallowMount(DpTableUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dpTable = entity;

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(dpTableServiceStub.create.calledWith(entity)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });
    });

    describe('Before route enter', () => {
      it('Should retrieve data', async () => {
        // GIVEN
        dpTableServiceStub.find.resolves(dpTableSample);
        dpTableServiceStub.retrieve.resolves([dpTableSample]);

        // WHEN
        route = {
          params: {
            dpTableId: `${dpTableSample.id}`,
          },
        };
        const wrapper = shallowMount(DpTableUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(comp.dpTable).toMatchObject(dpTableSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        dpTableServiceStub.find.resolves(dpTableSample);
        const wrapper = shallowMount(DpTableUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
