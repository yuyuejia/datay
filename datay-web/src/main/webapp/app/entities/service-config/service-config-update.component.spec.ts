import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { type RouteLocation } from 'vue-router';

import dayjs from 'dayjs';
import ServiceConfigUpdate from './service-config-update.vue';
import ServiceConfigService from './service-config.service';
import { DATE_TIME_LONG_FORMAT } from '@/shared/composables/date-format';
import AlertService from '@/shared/alert/alert.service';

type ServiceConfigUpdateComponentType = InstanceType<typeof ServiceConfigUpdate>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

const serviceConfigSample = { id: 123 };

describe('Component Tests', () => {
  let mountOptions: MountingOptions<ServiceConfigUpdateComponentType>['global'];
  let alertService: AlertService;

  describe('ServiceConfig Management Update Component', () => {
    let comp: ServiceConfigUpdateComponentType;
    let serviceConfigServiceStub: SinonStubbedInstance<ServiceConfigService>;

    beforeEach(() => {
      route = {};
      serviceConfigServiceStub = sinon.createStubInstance<ServiceConfigService>(ServiceConfigService);
      serviceConfigServiceStub.retrieve.onFirstCall().resolves(Promise.resolve([]));

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
          serviceConfigService: () => serviceConfigServiceStub,
        },
      };
    });

    afterEach(() => {
      vitest.resetAllMocks();
    });

    describe('load', () => {
      beforeEach(() => {
        const wrapper = shallowMount(ServiceConfigUpdate, { global: mountOptions });
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
        const wrapper = shallowMount(ServiceConfigUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.serviceConfig = serviceConfigSample;
        serviceConfigServiceStub.update.resolves(serviceConfigSample);

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(serviceConfigServiceStub.update.calledWith(serviceConfigSample)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });

      it('Should call create service on save for new entity', async () => {
        // GIVEN
        const entity = {};
        serviceConfigServiceStub.create.resolves(entity);
        const wrapper = shallowMount(ServiceConfigUpdate, { global: mountOptions });
        comp = wrapper.vm;
        comp.serviceConfig = entity;

        // WHEN
        comp.save();
        await comp.$nextTick();

        // THEN
        expect(serviceConfigServiceStub.create.calledWith(entity)).toBeTruthy();
        expect(comp.isSaving).toEqual(false);
      });
    });

    describe('Before route enter', () => {
      it('Should retrieve data', async () => {
        // GIVEN
        serviceConfigServiceStub.find.resolves(serviceConfigSample);
        serviceConfigServiceStub.retrieve.resolves([serviceConfigSample]);

        // WHEN
        route = {
          params: {
            serviceConfigId: `${serviceConfigSample.id}`,
          },
        };
        const wrapper = shallowMount(ServiceConfigUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(comp.serviceConfig).toMatchObject(serviceConfigSample);
      });
    });

    describe('Previous state', () => {
      it('Should go previous state', async () => {
        serviceConfigServiceStub.find.resolves(serviceConfigSample);
        const wrapper = shallowMount(ServiceConfigUpdate, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();

        comp.previousState();
        await comp.$nextTick();

        expect(routerGoMock).toHaveBeenCalledWith(-1);
      });
    });
  });
});
