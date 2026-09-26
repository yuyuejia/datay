import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';

import ServiceConfig from './service-config.vue';
import ServiceConfigService from './service-config.service';
import AlertService from '@/shared/alert/alert.service';

type ServiceConfigComponentType = InstanceType<typeof ServiceConfig>;

const bModalStub = {
  render: () => {},
  methods: {
    hide: () => {},
    show: () => {},
  },
};

describe('Component Tests', () => {
  let alertService: AlertService;

  describe('ServiceConfig Management Component', () => {
    let serviceConfigServiceStub: SinonStubbedInstance<ServiceConfigService>;
    let mountOptions: MountingOptions<ServiceConfigComponentType>['global'];

    beforeEach(() => {
      serviceConfigServiceStub = sinon.createStubInstance<ServiceConfigService>(ServiceConfigService);
      serviceConfigServiceStub.retrieve.resolves({ headers: {} });

      alertService = new AlertService();

      mountOptions = {
        stubs: {
          jhiItemCount: true,
          bPagination: true,
          AppModal: bModalStub as any,
          'font-awesome-icon': true,
          'b-badge': true,
          'jhi-sort-indicator': true,
          'b-button': true,
          'router-link': true,
          'el-table': true,
          'el-table-column': true,
          'el-button': true,
        },
        provide: {
          alertService,
          serviceConfigService: () => serviceConfigServiceStub,
        },
      };
    });

    describe('Mount', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        serviceConfigServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        const wrapper = shallowMount(ServiceConfig, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(serviceConfigServiceStub.retrieve.calledOnce).toBeTruthy();
        expect(comp.serviceConfigs[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for an id', async () => {
        // WHEN
        const wrapper = shallowMount(ServiceConfig, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(serviceConfigServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['id,asc'],
        });
      });
    });
    describe('Handles', () => {
      let comp: ServiceConfigComponentType;

      beforeEach(async () => {
        const wrapper = shallowMount(ServiceConfig, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();
        serviceConfigServiceStub.retrieve.reset();
        serviceConfigServiceStub.retrieve.resolves({ headers: {}, data: [] });
      });

      it('should load a page', async () => {
        // GIVEN
        serviceConfigServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.page = 2;
        await comp.$nextTick();

        // THEN
        expect(serviceConfigServiceStub.retrieve.called).toBeTruthy();
        expect(comp.serviceConfigs[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should not load a page if the page is the same as the previous page', () => {
        // WHEN
        comp.page = 1;

        // THEN
        expect(serviceConfigServiceStub.retrieve.called).toBeFalsy();
      });

      it('should re-initialize the page', async () => {
        // GIVEN
        comp.page = 2;
        await comp.$nextTick();
        serviceConfigServiceStub.retrieve.reset();
        serviceConfigServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.clear();
        await comp.$nextTick();

        // THEN
        expect(comp.page).toEqual(1);
        expect(serviceConfigServiceStub.retrieve.callCount).toEqual(1);
        expect(comp.serviceConfigs[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for a non-id attribute', async () => {
        // WHEN
        comp.propOrder = 'name';
        await comp.$nextTick();

        // THEN
        expect(serviceConfigServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['name,asc', 'id'],
        });
      });

      it('Should call delete service on confirmDelete', async () => {
        // GIVEN
        serviceConfigServiceStub.delete.resolves({});

        // WHEN
        comp.prepareRemove({ id: 123 });

        comp.removeServiceConfig();
        await comp.$nextTick(); // clear components

        // THEN
        expect(serviceConfigServiceStub.delete.called).toBeTruthy();

        // THEN
        await comp.$nextTick(); // handle component clear watch
        expect(serviceConfigServiceStub.retrieve.callCount).toEqual(1);
      });
    });
  });
});
