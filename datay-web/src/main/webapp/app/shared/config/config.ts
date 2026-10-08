import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";

import { library } from "@fortawesome/fontawesome-svg-core";
import { faArrowLeft } from "@fortawesome/free-solid-svg-icons/faArrowLeft";
import { faArrowRight } from "@fortawesome/free-solid-svg-icons/faArrowRight";
import { faAsterisk } from "@fortawesome/free-solid-svg-icons/faAsterisk";
import { faBan } from "@fortawesome/free-solid-svg-icons/faBan";
import { faBars } from "@fortawesome/free-solid-svg-icons/faBars";
import { faBell } from "@fortawesome/free-solid-svg-icons/faBell";
import { faBolt } from "@fortawesome/free-solid-svg-icons/faBolt";
import { faBook } from "@fortawesome/free-solid-svg-icons/faBook";
import { faBug } from "@fortawesome/free-solid-svg-icons/faBug";
import { faClock } from "@fortawesome/free-solid-svg-icons/faClock";
import { faCloud } from "@fortawesome/free-solid-svg-icons/faCloud";
import { faCode } from "@fortawesome/free-solid-svg-icons/faCode";
import { faCubes } from "@fortawesome/free-solid-svg-icons/faCubes";
import { faDatabase } from "@fortawesome/free-solid-svg-icons/faDatabase";
import { faExclamationCircle } from "@fortawesome/free-solid-svg-icons/faExclamationCircle";
import { faEye } from "@fortawesome/free-solid-svg-icons/faEye";
import { faFileCode } from "@fortawesome/free-solid-svg-icons/faFileCode";
import { faFlag } from "@fortawesome/free-solid-svg-icons/faFlag";
import { faGlobe } from "@fortawesome/free-solid-svg-icons/faGlobe";
import { faHandPointer } from "@fortawesome/free-solid-svg-icons/faHandPointer";
import { faHeart } from "@fortawesome/free-solid-svg-icons/faHeart";
import { faHome } from "@fortawesome/free-solid-svg-icons/faHome";
import { faList } from "@fortawesome/free-solid-svg-icons/faList";
import { faLock } from "@fortawesome/free-solid-svg-icons/faLock";
import { faPencilAlt } from "@fortawesome/free-solid-svg-icons/faPencilAlt";
import { faPlay } from "@fortawesome/free-solid-svg-icons/faPlay";
import { faPlus } from "@fortawesome/free-solid-svg-icons/faPlus";
import { faProjectDiagram } from "@fortawesome/free-solid-svg-icons/faProjectDiagram";
import { faRandom } from "@fortawesome/free-solid-svg-icons/faRandom";
import { faRoad } from "@fortawesome/free-solid-svg-icons/faRoad";
import { faRocket } from "@fortawesome/free-solid-svg-icons/faRocket";
import { faSave } from "@fortawesome/free-solid-svg-icons/faSave";
import { faSearch } from "@fortawesome/free-solid-svg-icons/faSearch";
import { faSignInAlt } from "@fortawesome/free-solid-svg-icons/faSignInAlt";
import { faSignOutAlt } from "@fortawesome/free-solid-svg-icons/faSignOutAlt";
import { faSort } from "@fortawesome/free-solid-svg-icons/faSort";
import { faSortDown } from "@fortawesome/free-solid-svg-icons/faSortDown";
import { faIndent } from "@fortawesome/free-solid-svg-icons/faIndent";
import { faSortUp } from "@fortawesome/free-solid-svg-icons/faSortUp";
import { faSync } from "@fortawesome/free-solid-svg-icons/faSync";
import { faTable } from "@fortawesome/free-solid-svg-icons/faTable";
import { faTachometerAlt } from "@fortawesome/free-solid-svg-icons/faTachometerAlt";
import { faTasks } from "@fortawesome/free-solid-svg-icons/faTasks";
import { faThList } from "@fortawesome/free-solid-svg-icons/faThList";
import { faTimesCircle } from "@fortawesome/free-solid-svg-icons/faTimesCircle";
import { faTimes } from "@fortawesome/free-solid-svg-icons/faTimes";
import { faTrash } from "@fortawesome/free-solid-svg-icons/faTrash";
import { faUser } from "@fortawesome/free-solid-svg-icons/faUser";
import { faUserPlus } from "@fortawesome/free-solid-svg-icons/faUserPlus";
import { faUsers } from "@fortawesome/free-solid-svg-icons/faUsers";
import { faUsersCog } from "@fortawesome/free-solid-svg-icons/faUsersCog";
import { faWrench } from "@fortawesome/free-solid-svg-icons/faWrench";
import { faRobot } from "@fortawesome/free-solid-svg-icons/faRobot";
import { faWandMagicSparkles } from "@fortawesome/free-solid-svg-icons/faWandMagicSparkles";
import { faArrowRightToBracket } from "@fortawesome/free-solid-svg-icons/faArrowRightToBracket";
import { faChartLine } from "@fortawesome/free-solid-svg-icons/faChartLine";
import { faCheck } from "@fortawesome/free-solid-svg-icons/faCheck";
import { faCheckCircle } from "@fortawesome/free-solid-svg-icons/faCheckCircle";
import { faCircleNotch } from "@fortawesome/free-solid-svg-icons/faCircleNotch";
import { faCircleQuestion } from "@fortawesome/free-solid-svg-icons/faCircleQuestion";
import { faCopy } from "@fortawesome/free-solid-svg-icons/faCopy";
import { faBoxOpen } from "@fortawesome/free-solid-svg-icons/faBoxOpen";
import { faFileImport } from "@fortawesome/free-solid-svg-icons/faFileImport";
import { faDownload } from "@fortawesome/free-solid-svg-icons/faDownload";
import { faEllipsisVertical } from "@fortawesome/free-solid-svg-icons/faEllipsisVertical";

export function initFortAwesome(vue) {
  vue.component("FontAwesomeIcon", FontAwesomeIcon);

  library.add(
    faArrowLeft,
    faArrowRight,
    faAsterisk,
    faBan,
    faBars,
    faBell,
    faBolt,
    faBook,
    faBug,
    faClock,
    faCloud,
    faCode,
    faCubes,
    faDatabase,
    faExclamationCircle,
    faEye,
    faFileCode,
    faFlag,
    faGlobe,
    faHandPointer,
    faHeart,
    faHome,
    faList,
    faLock,
    faPencilAlt,
    faPlay,
    faPlus,
    faProjectDiagram,
    faRandom,
    faRoad,
    faRocket,
    faSave,
    faSearch,
    faSignInAlt,
    faSignOutAlt,
    faSort,
    faSortDown,
    faIndent,
    faSortUp,
    faSync,
    faTable,
    faTachometerAlt,
    faTasks,
    faThList,
    faTimes,
    faTimesCircle,
    faTrash,
    faUser,
    faUserPlus,
    faUsers,
    faUsersCog,
    faWrench,
    faRobot,
    faWandMagicSparkles,
    faArrowRightToBracket,
    faChartLine,
    faCheck,
    faCheckCircle,
    faCircleNotch,
    faCircleQuestion,
    faCopy,
    faBoxOpen,
    faFileImport,
    faDownload,
    faEllipsisVertical,
  );
}
