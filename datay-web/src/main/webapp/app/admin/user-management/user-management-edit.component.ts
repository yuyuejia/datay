import { type Ref, defineComponent, inject, ref } from "vue";
import { useVuelidate } from "@vuelidate/core";
import { maxLength, minLength, required } from "@vuelidate/validators";
import { useRoute, useRouter } from "vue-router";
import UserManagementService from "./user-management.service";
import { type IUser, User } from "@/shared/model/user.model";
import { useAlertService } from "@/shared/alert/alert.service";

const loginValidator = (value: string) => {
  if (!value) {
    return true;
  }
  return /^[a-zA-Z0-9!$&*+=?^_`{|}~.-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$|^[_.@A-Za-z0-9-]+$/.test(
    value,
  );
};

/**
 * 与后端 Hibernate @Email 对齐：邮箱可选，允许内网域名（如 admin@localhost），不做 TLD 强校验。
 */
const emailValidator = (value: string) => {
  if (!value) {
    return true;
  }
  return /^[^\s@]+@[^\s@]+$/.test(value);
};

const validations: any = {
  userAccount: {
    login: {
      required,
      maxLength: maxLength(254),
      pattern: loginValidator,
    },
    firstName: {
      maxLength: maxLength(50),
    },
    lastName: {
      maxLength: maxLength(50),
    },
    email: {
      format: emailValidator,
      minLength: minLength(5),
      maxLength: maxLength(254),
    },
  },
};

export default defineComponent({
  name: "JhiUserManagementEdit",
  validations,
  setup() {
    const route = useRoute();
    const router = useRouter();

    const alertService = inject("alertService", () => useAlertService(), true);
    const userManagementService = inject(
      "userManagementService",
      () => new UserManagementService(),
      true,
    );
    const previousState = () => router.go(-1);

    const userAccount: Ref<IUser> = ref({ ...new User(), authorities: [] });
    const isSaving: Ref<boolean> = ref(false);
    const authorities: Ref<string[]> = ref([]);

    const initAuthorities = async () => {
      const response = await userManagementService.retrieveAuthorities();
      authorities.value = response.data;
    };

    const loadUser = async (userId: string) => {
      const response = await userManagementService.get(userId);
      userAccount.value = response.data;
    };

    initAuthorities();
    const userId = route.params?.userId;
    if (userId) {
      loadUser(userId);
    }

    return {
      alertService,
      userAccount,
      isSaving,
      authorities,
      userManagementService,
      previousState,
      v$: useVuelidate(),
    };
  },
  methods: {
    save(): void {
      this.isSaving = true;
      if (this.userAccount.id) {
        this.userManagementService
          .update(this.userAccount)
          .then((res) => {
            this.returnToList();
            this.alertService.showInfo(this.getToastMessageFromHeader(res));
          })
          .catch((error) => {
            this.isSaving = true;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.userAccount.langKey = this.userAccount.langKey ?? "zh-cn";
        this.userManagementService
          .create(this.userAccount)
          .then((res) => {
            this.returnToList();
            this.alertService.showSuccess(this.getToastMessageFromHeader(res));
          })
          .catch((error) => {
            this.isSaving = true;
            this.alertService.showHttpError(error.response);
          });
      }
    },

    returnToList(): void {
      this.isSaving = false;
      this.previousState();
    },

    getToastMessageFromHeader(res: any): string {
      return res.headers["x-datafusionapp-alert"];
    },
  },
});
