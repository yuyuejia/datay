import {
  BAlert,
  BBadge,
  BButton,
  BCollapse,
  BDropdown,
  BDropdownItem,
  BForm,
  BFormCheckbox,
  BFormDatepicker,
  BFormFile,
  BFormGroup,
  BFormInput,
  BInputGroup,
  BInputGroupPrepend,
  BLink,
  BModal,
  BNavItem,
  BNavItemDropdown,
  BNavbar,
  BNavbarBrand,
  BNavbarNav,
  BNavbarToggle,
  BPagination,
  BProgress,
  BProgressBar,
  ToastPlugin,
  VBModal,
} from "bootstrap-vue";

export function initBootstrapVue(vue) {
  vue.use(ToastPlugin);

  vue.component("BBadge", BBadge);
  vue.component("BDropdown", BDropdown);
  vue.component("BDropdownItem", BDropdownItem);
  vue.component("BLink", BLink);
  vue.component("BAlert", BAlert);
  vue.component("BButton", BButton);
  vue.component("BNavbar", BNavbar);
  vue.component("BNavbarNav", BNavbarNav);
  vue.component("BNavbarBrand", BNavbarBrand);
  vue.component("BNavbarToggle", BNavbarToggle);
  vue.component("BPagination", BPagination);
  vue.component("BProgress", BProgress);
  vue.component("BProgressBar", BProgressBar);
  vue.component("BForm", BForm);
  vue.component("BFormInput", BFormInput);
  vue.component("BFormGroup", BFormGroup);
  vue.component("BFormCheckbox", BFormCheckbox);
  vue.component("BFormFile", BFormFile);
  vue.component("BCollapse", BCollapse);
  vue.component("BNavItem", BNavItem);
  vue.component("BNavItemDropdown", BNavItemDropdown);
  vue.component("BModal", BModal);
  vue.directive("b-modal", VBModal);
  vue.component("BFormDatepicker", BFormDatepicker);
  vue.component("BInputGroup", BInputGroup);
  vue.component("BInputGroupPrepend", BInputGroupPrepend);
}
