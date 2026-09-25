import { vitest } from "vitest";
import { type MountingOptions, shallowMount } from "@vue/test-utils";
import axios from "axios";
import sinon from "sinon";
import { type RouteLocation } from "vue-router";

import UserManagementEdit from "./user-management-edit.vue";
import AlertService from "@/shared/alert/alert.service";

type UserManagementEditComponentType = InstanceType<typeof UserManagementEdit>;

let route: Partial<RouteLocation>;
const routerGoMock = vitest.fn();

vitest.mock("vue-router", () => ({
  useRoute: () => route,
  useRouter: () => ({ go: routerGoMock }),
}));

describe("UserManagementEdit Component", () => {
  const axiosStub = {
    get: sinon.stub(axios, "get"),
    post: sinon.stub(axios, "post"),
    put: sinon.stub(axios, "put"),
  };
  let mountOptions: MountingOptions<UserManagementEditComponentType>["global"];
  let alertService: AlertService;

  beforeEach(() => {
    route = {};
    alertService = new AlertService({
      bvToast: {
        toast: vitest.fn(),
      } as any,
    });

    mountOptions = {
      stubs: {
        "font-awesome-icon": true,
      },
      provide: {
        alertService,
      },
    };

    axiosStub.get.reset();
    axiosStub.post.reset();
    axiosStub.put.reset();
  });

  describe("init", () => {
    it("Should load user", async () => {
      // GIVEN
      axiosStub.get.withArgs(`api/admin/users/${123}`).resolves({});
      axiosStub.get.withArgs("api/authorities").resolves({ data: [] });
      route = {
        params: {
          userId: `${123}`,
        },
      };
      const wrapper = shallowMount(UserManagementEdit, {
        global: mountOptions,
      });
      const userManagementEdit: UserManagementEditComponentType = wrapper.vm;

      // WHEN
      await userManagementEdit.$nextTick();

      // THEN
      expect(axiosStub.get.calledWith("api/authorities")).toBeTruthy();
      expect(axiosStub.get.calledWith(`api/admin/users/${123}`)).toBeTruthy();
    });
    it("Should open create user", async () => {
      // GIVEN
      axiosStub.get.resolves({});
      axiosStub.get.withArgs("api/authorities").resolves({ data: [] });
      route = {
        params: {},
      };
      const wrapper = shallowMount(UserManagementEdit, {
        global: mountOptions,
      });
      const userManagementEdit: UserManagementEditComponentType = wrapper.vm;

      // WHEN
      await userManagementEdit.$nextTick();

      // THEN
      expect(axiosStub.get.calledWith("api/authorities")).toBeTruthy();
      expect(axiosStub.get.callCount).toBe(1);
    });

    it("Should enable save for internal-domain email (admin@localhost)", async () => {
      // GIVEN
      axiosStub.get.withArgs(`api/admin/users/${123}`).resolves({
        data: {
          id: 123,
          login: "member",
          email: "member@localhost",
          activated: true,
          authorities: ["ROLE_USER"],
        },
      });
      axiosStub.get
        .withArgs("api/authorities")
        .resolves({ data: ["ROLE_ADMIN", "ROLE_USER"] });
      route = { params: { userId: `${123}` } };
      const wrapper = shallowMount(UserManagementEdit, {
        global: mountOptions,
      });
      const vm: any = wrapper.vm;

      // WHEN
      await vm.$nextTick();
      await vm.$nextTick();

      // THEN
      expect(vm.v$.userAccount.$invalid).toBe(false);
      expect(
        wrapper.find('button[type="submit"]').attributes("disabled"),
      ).toBeUndefined();
    });

    it("Should enable save when email is empty", async () => {
      // GIVEN
      axiosStub.get.withArgs(`api/admin/users/${123}`).resolves({
        data: {
          id: 123,
          login: "member",
          email: null,
          activated: true,
          authorities: ["ROLE_USER"],
        },
      });
      axiosStub.get
        .withArgs("api/authorities")
        .resolves({ data: ["ROLE_ADMIN", "ROLE_USER"] });
      route = { params: { userId: `${123}` } };
      const wrapper = shallowMount(UserManagementEdit, {
        global: mountOptions,
      });
      const vm: any = wrapper.vm;

      // WHEN
      await vm.$nextTick();
      await vm.$nextTick();

      // THEN
      expect(vm.v$.userAccount.$invalid).toBe(false);
    });

    it("Should keep save disabled for malformed email", async () => {
      // GIVEN
      axiosStub.get.withArgs(`api/admin/users/${123}`).resolves({
        data: {
          id: 123,
          login: "member",
          email: "not-an-email",
          activated: true,
          authorities: ["ROLE_USER"],
        },
      });
      axiosStub.get
        .withArgs("api/authorities")
        .resolves({ data: ["ROLE_ADMIN", "ROLE_USER"] });
      route = { params: { userId: `${123}` } };
      const wrapper = shallowMount(UserManagementEdit, {
        global: mountOptions,
      });
      const vm: any = wrapper.vm;

      // WHEN
      await vm.$nextTick();
      await vm.$nextTick();

      // THEN
      expect(vm.v$.userAccount.email.$invalid).toBe(true);
    });
  });

  describe("save", () => {
    it("Should call update service on save for existing user", async () => {
      // GIVEN
      axiosStub.put.resolves({
        headers: {
          "x-datafusionapp-alert": "",
          "x-datafusionapp-params": "",
        },
      });
      axiosStub.get.withArgs(`api/admin/users/${123}`).resolves({
        data: { id: 123, authorities: [] },
      });
      axiosStub.get.withArgs("api/authorities").resolves({ data: [] });
      route = {
        params: {
          userId: `${123}`,
        },
      };
      const wrapper = shallowMount(UserManagementEdit, {
        global: mountOptions,
      });
      const userManagementEdit: UserManagementEditComponentType = wrapper.vm;
      await userManagementEdit.$nextTick();

      // WHEN
      userManagementEdit.save();
      await userManagementEdit.$nextTick();

      // THEN
      expect(
        axiosStub.put.calledWith("api/admin/users", {
          id: 123,
          authorities: [],
        }),
      ).toBeTruthy();
      expect(userManagementEdit.isSaving).toEqual(false);
    });

    it("Should call create service on save for new user", async () => {
      // GIVEN
      axiosStub.post.resolves({
        headers: {
          "x-datafusionapp-alert": "",
          "x-datafusionapp-params": "",
        },
      });
      axiosStub.get.resolves({});
      axiosStub.get.withArgs("api/authorities").resolves({ data: [] });
      route = {
        params: {},
      };
      const wrapper = shallowMount(UserManagementEdit, {
        global: mountOptions,
      });
      const userManagementEdit: UserManagementEditComponentType = wrapper.vm;
      await userManagementEdit.$nextTick();
      userManagementEdit.userAccount = { authorities: [] };

      // WHEN
      userManagementEdit.save();
      await userManagementEdit.$nextTick();

      // THEN
      expect(
        axiosStub.post.calledWith("api/admin/users", {
          authorities: [],
          langKey: "zh-cn",
        }),
      ).toBeTruthy();
      expect(userManagementEdit.isSaving).toEqual(false);
    });
  });
});
