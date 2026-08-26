import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';

import ETLComponent from './etl-component.vue';
import ETLComponentService from './etl-component.service';
import AlertService from '@/shared/alert/alert.service';

type ETLComponentComponentType = InstanceType<typeof ETLComponent>;

const bModalStub = {
  render: () => {},
  methods: {
    hide: () => {},
    show: () => {},
  },
};

describe('Component Tests', () => {
  let alertService: AlertService;

  describe('ETLComponent Management Component', () => {
    let eTLComponentServiceStub: SinonStubbedInstance<ETLComponentService>;
    let mountOptions: MountingOptions<ETLComponentComponentType>['global'];

    beforeEach(() => {
      eTLComponentServiceStub = sinon.createStubInstance<ETLComponentService>(ETLComponentService);
      eTLComponentServiceStub.retrieve.resolves({ headers: {} });

      alertService = new AlertService({
        bvToast: {
          toast: vitest.fn(),
        } as any,
      });

      mountOptions = {
        stubs: {
          jhiItemCount: true,
          bPagination: true,
          bModal: bModalStub as any,
          'font-awesome-icon': true,
          'b-badge': true,
          'jhi-sort-indicator': true,
          'b-button': true,
          'router-link': true,
        },
        directives: {
          'b-modal': {},
        },
        provide: {
          alertService,
          eTLComponentService: () => eTLComponentServiceStub,
        },
      };
    });

    describe('Mount', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        eTLComponentServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        const wrapper = shallowMount(ETLComponent, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(eTLComponentServiceStub.retrieve.calledOnce).toBeTruthy();
        expect(comp.eTLComponents[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for an id', async () => {
        // WHEN
        const wrapper = shallowMount(ETLComponent, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(eTLComponentServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['id,asc'],
        });
      });
    });
    describe('Handles', () => {
      let comp: ETLComponentComponentType;

      beforeEach(async () => {
        const wrapper = shallowMount(ETLComponent, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();
        eTLComponentServiceStub.retrieve.reset();
        eTLComponentServiceStub.retrieve.resolves({ headers: {}, data: [] });
      });

      it('should load a page', async () => {
        // GIVEN
        eTLComponentServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.page = 2;
        await comp.$nextTick();

        // THEN
        expect(eTLComponentServiceStub.retrieve.called).toBeTruthy();
        expect(comp.eTLComponents[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should not load a page if the page is the same as the previous page', () => {
        // WHEN
        comp.page = 1;

        // THEN
        expect(eTLComponentServiceStub.retrieve.called).toBeFalsy();
      });

      it('should re-initialize the page', async () => {
        // GIVEN
        comp.page = 2;
        await comp.$nextTick();
        eTLComponentServiceStub.retrieve.reset();
        eTLComponentServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.clear();
        await comp.$nextTick();

        // THEN
        expect(comp.page).toEqual(1);
        expect(eTLComponentServiceStub.retrieve.callCount).toEqual(1);
        expect(comp.eTLComponents[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for a non-id attribute', async () => {
        // WHEN
        comp.propOrder = 'name';
        await comp.$nextTick();

        // THEN
        expect(eTLComponentServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['name,asc', 'id'],
        });
      });

      it('Should call delete service on confirmDelete', async () => {
        // GIVEN
        eTLComponentServiceStub.delete.resolves({});

        // WHEN
        comp.prepareRemove({ id: 123 });

        comp.removeETLComponent();
        await comp.$nextTick(); // clear components

        // THEN
        expect(eTLComponentServiceStub.delete.called).toBeTruthy();

        // THEN
        await comp.$nextTick(); // handle component clear watch
        expect(eTLComponentServiceStub.retrieve.callCount).toEqual(1);
      });
    });
  });
});
