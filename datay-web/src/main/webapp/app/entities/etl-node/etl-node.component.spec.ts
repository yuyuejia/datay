import { vitest } from 'vitest';
import { type MountingOptions, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';

import ETLNode from './etl-node.vue';
import ETLNodeService from './etl-node.service';
import AlertService from '@/shared/alert/alert.service';

type ETLNodeComponentType = InstanceType<typeof ETLNode>;

const bModalStub = {
  render: () => {},
  methods: {
    hide: () => {},
    show: () => {},
  },
};

describe('Component Tests', () => {
  let alertService: AlertService;

  describe('ETLNode Management Component', () => {
    let eTLNodeServiceStub: SinonStubbedInstance<ETLNodeService>;
    let mountOptions: MountingOptions<ETLNodeComponentType>['global'];

    beforeEach(() => {
      eTLNodeServiceStub = sinon.createStubInstance<ETLNodeService>(ETLNodeService);
      eTLNodeServiceStub.retrieve.resolves({ headers: {} });

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
          eTLNodeService: () => eTLNodeServiceStub,
        },
      };
    });

    describe('Mount', () => {
      it('Should call load all on init', async () => {
        // GIVEN
        eTLNodeServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        const wrapper = shallowMount(ETLNode, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(eTLNodeServiceStub.retrieve.calledOnce).toBeTruthy();
        expect(comp.eTLNodes[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for an id', async () => {
        // WHEN
        const wrapper = shallowMount(ETLNode, { global: mountOptions });
        const comp = wrapper.vm;
        await comp.$nextTick();

        // THEN
        expect(eTLNodeServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['id,asc'],
        });
      });
    });
    describe('Handles', () => {
      let comp: ETLNodeComponentType;

      beforeEach(async () => {
        const wrapper = shallowMount(ETLNode, { global: mountOptions });
        comp = wrapper.vm;
        await comp.$nextTick();
        eTLNodeServiceStub.retrieve.reset();
        eTLNodeServiceStub.retrieve.resolves({ headers: {}, data: [] });
      });

      it('should load a page', async () => {
        // GIVEN
        eTLNodeServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.page = 2;
        await comp.$nextTick();

        // THEN
        expect(eTLNodeServiceStub.retrieve.called).toBeTruthy();
        expect(comp.eTLNodes[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should not load a page if the page is the same as the previous page', () => {
        // WHEN
        comp.page = 1;

        // THEN
        expect(eTLNodeServiceStub.retrieve.called).toBeFalsy();
      });

      it('should re-initialize the page', async () => {
        // GIVEN
        comp.page = 2;
        await comp.$nextTick();
        eTLNodeServiceStub.retrieve.reset();
        eTLNodeServiceStub.retrieve.resolves({ headers: {}, data: [{ id: 123 }] });

        // WHEN
        comp.clear();
        await comp.$nextTick();

        // THEN
        expect(comp.page).toEqual(1);
        expect(eTLNodeServiceStub.retrieve.callCount).toEqual(1);
        expect(comp.eTLNodes[0]).toEqual(expect.objectContaining({ id: 123 }));
      });

      it('should calculate the sort attribute for a non-id attribute', async () => {
        // WHEN
        comp.propOrder = 'name';
        await comp.$nextTick();

        // THEN
        expect(eTLNodeServiceStub.retrieve.lastCall.firstArg).toMatchObject({
          sort: ['name,asc', 'id'],
        });
      });

      it('Should call delete service on confirmDelete', async () => {
        // GIVEN
        eTLNodeServiceStub.delete.resolves({});

        // WHEN
        comp.prepareRemove({ id: 123 });

        comp.removeETLNode();
        await comp.$nextTick(); // clear components

        // THEN
        expect(eTLNodeServiceStub.delete.called).toBeTruthy();

        // THEN
        await comp.$nextTick(); // handle component clear watch
        expect(eTLNodeServiceStub.retrieve.callCount).toEqual(1);
      });
    });
  });
});
