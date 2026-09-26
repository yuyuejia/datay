import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import dayjs from 'dayjs';
import DataSyncUpdate from './data-sync-update.vue';
import DataSyncService from './data-sync.service';
import { DATE_TIME_LONG_FORMAT } from '@/shared/composables/date-format';
import AlertService from '@/shared/alert/alert.service';

type DataSyncUpdateComponentType = InstanceType<typeof DataSyncUpdate>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const dataSyncSample = { id: 123 };

describe('Component Tests', () => {
  let mountOptions: MountingOptions<DataSyncUpdateComponentType>['global'];
  let alertService: AlertService;

  describe('DataSync Management Update Component', () => {
    let comp: DataSyncUpdateComponentType;
    let dataSyncServiceStub: SinonStubbedInstance<DataSyncService>;

    beforeEach(() => {
      route = {};
      dataSyncServiceStub = sinon.createStubInstance<DataSyncService>(DataSyncService);
      dataSyncServiceStub.retrieve.onFirstCall().resolves(Promise.resolve([]));

      alertService = new AlertService();

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
          dataSyncService: () => dataSyncServiceStub,
        },
      };
    });

    afterEach(() => {
      vitest.resetAllMocks();
    });

    describe('load', () => {
      beforeEach(() => {
        const wrapper = shallowMount(DataSyncUpdate, { global: mountOptions });
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
        const wrapper = shallowMount(DataSyncUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataSync = dataSyncSample;
        dataSyncServiceStub.update.resolves(dataSyncSample);

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(dataSyncServiceStub.update.calledWith(dataSyncSample)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });

      it('Should call create service on save for new entity', async () => {
        // GIVEN
        const entity = {};
        dataSyncServiceStub.create.resolves(entity);
        const wrapper = shallowMount(DataSyncUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataSync = entity;

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(dataSyncServiceStub.create.calledWith(entity)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });
    });

    describe('Before route enter', () => {
      it('Should retrieve data', async () => {
        // GIVEN
        dataSyncServiceStub.find.resolves(dataSyncSample);
        dataSyncServiceStub.retrieve.resolves([dataSyncSample]);

        // WHEN
        route = {
          params: {
            dataSyncId: `${dataSyncSample.id}`,
          },
        };
        const wrapper = shallowMount(DataSyncUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(comp.dataSync).toMatchObject(dataSyncSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        dataSyncServiceStub.find.resolves(dataSyncSample);
        const wrapper = shallowMount(DataSyncUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
