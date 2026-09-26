import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import dayjs from 'dayjs';
import DataSyncTableConfigUpdate from './data-sync-table-config-update.vue';
import DataSyncTableConfigService from './data-sync-table-config.service';
import { DATE_TIME_LONG_FORMAT } from '@/shared/composables/date-format';
import AlertService from '@/shared/alert/alert.service';

type DataSyncTableConfigUpdateComponentType = InstanceType<typeof DataSyncTableConfigUpdate>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const dataSyncTableConfigSample = { id: 123 };

describe('Component Tests', () => {
  let mountOptions: MountingOptions<DataSyncTableConfigUpdateComponentType>['global'];
  let alertService: AlertService;

  describe('DataSyncTableConfig Management Update Component', () => {
    let comp: DataSyncTableConfigUpdateComponentType;
    let dataSyncTableConfigServiceStub: SinonStubbedInstance<DataSyncTableConfigService>;

    beforeEach(() => {
      route = {};
      dataSyncTableConfigServiceStub = sinon.createStubInstance<DataSyncTableConfigService>(DataSyncTableConfigService);
      dataSyncTableConfigServiceStub.retrieve.onFirstCall().resolves(Promise.resolve([]));

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
          dataSyncTableConfigService: () => dataSyncTableConfigServiceStub,
        },
      };
    });

    afterEach(() => {
      vitest.resetAllMocks();
    });

    describe('load', () => {
      beforeEach(() => {
        const wrapper = shallowMount(DataSyncTableConfigUpdate, { global: mountOptions });
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
        const wrapper = shallowMount(DataSyncTableConfigUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataSyncTableConfig = dataSyncTableConfigSample;
        dataSyncTableConfigServiceStub.update.resolves(dataSyncTableConfigSample);

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(dataSyncTableConfigServiceStub.update.calledWith(dataSyncTableConfigSample)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });

      it('Should call create service on save for new entity', async () => {
        // GIVEN
        const entity = {};
        dataSyncTableConfigServiceStub.create.resolves(entity);
        const wrapper = shallowMount(DataSyncTableConfigUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.dataSyncTableConfig = entity;

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(dataSyncTableConfigServiceStub.create.calledWith(entity)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });
    });

    describe('Before route enter', () => {
      it('Should retrieve data', async () => {
        // GIVEN
        dataSyncTableConfigServiceStub.find.resolves(dataSyncTableConfigSample);
        dataSyncTableConfigServiceStub.retrieve.resolves([dataSyncTableConfigSample]);

        // WHEN
        route = {
          params: {
            dataSyncTableConfigId: `${dataSyncTableConfigSample.id}`,
          },
        };
        const wrapper = shallowMount(DataSyncTableConfigUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(comp.dataSyncTableConfig).toMatchObject(dataSyncTableConfigSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        dataSyncTableConfigServiceStub.find.resolves(dataSyncTableConfigSample);
        const wrapper = shallowMount(DataSyncTableConfigUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
