import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';

import ETLEdge from './etl-edge.vue';
import ETLEdgeService from './etl-edge.service';
import AlertService from '@/shared/alert/alert.service';

type ETLEdgeComponentType = InstanceType<typeof ETLEdge>;

const bModalStub = {
  render: () => {},
  methods: {
    hide: () => {},
    show: () => {},
  },
};

describe('Component Tests', () => {
  let alertService: AlertService;

  describe('ETLEdge Management Component', () => {
    let eTLEdgeServiceStub: SinonStubbedInstance<ETLEdgeService>;
    let mountOptions: MountingOptions<ETLEdgeComponentType>['global'];

    beforeEach(() => {
      eTLEdgeServiceStub = sinon.createStubInstance<ETLEdgeService>(ETLEdgeService);
      eTLEdgeServiceStub.retrieve.resolves({ headers: {} });

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
          eTLEdgeService: () => eTLEdgeServiceStub,
        },
      };
    });

    describe('Mount', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        eTLEdgeServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        const wrapper = shallowMount(ETLEdge, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(eTLEdgeServiceStub.retrieve.calledOnce).toBeTruthy();
        expect(comp.eTLEdges[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for an id', async () => {
        // WHEN
        const wrapper = shallowMount(ETLEdge, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(eTLEdgeServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['id,asc'],
        });
      });
    });
    describe('Handles', () => {
      let comp: ETLEdgeComponentType;

      beforeEach(async () => {
        const wrapper = shallowMount(ETLEdge, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();
        eTLEdgeServiceStub.retrieve.reset();
        eTLEdgeServiceStub.retrieve.resolves({ headers: {}, data: [] });
      });

      it('should load a page', async () => {
        // GIVEN
        eTLEdgeServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.page = 2;
        await comp.$nextTick();

        // THEN
        expect(eTLEdgeServiceStub.retrieve.called).toBeTruthy();
        expect(comp.eTLEdges[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should not load a page if the page is the same as the previous page', () => {
        // WHEN
        comp.page = 1;

        // THEN
        expect(eTLEdgeServiceStub.retrieve.called).toBeFalsy();
      });

      it('should re-initialize the page', async () => {
        // GIVEN
        comp.page = 2;
        await comp.$nextTick();
        eTLEdgeServiceStub.retrieve.reset();
        eTLEdgeServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.clear();
        await comp.$nextTick();

        // THEN
        expect(comp.page).toEqual(1);
        expect(eTLEdgeServiceStub.retrieve.callCount).toEqual(1);
        expect(comp.eTLEdges[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for a non-id attribute', async () => {
        // WHEN
        comp.propOrder = 'name';
        await comp.$nextTick();

        // THEN
        expect(eTLEdgeServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['name,asc', 'id'],
        });
      });

      it('Should call delete service on confirmDelete', async () => {
        // GIVEN
        eTLEdgeServiceStub.delete.resolves({});

        // WHEN
        comp.prepareRemove({ id: 123 });

        comp.removeETLEdge();
        await comp.$nextTick(); // clear components

        // THEN
        expect(eTLEdgeServiceStub.delete.called).toBeTruthy();

        // THEN
        await comp.$nextTick(); // handle component clear watch
        expect(eTLEdgeServiceStub.retrieve.callCount).toEqual(1);
      });
    });
  });
});
