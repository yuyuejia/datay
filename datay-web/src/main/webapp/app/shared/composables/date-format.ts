import { type Ref } from 'vue';
import dayjs from 'dayjs';

export const DATE_FORMAT = 'YYYY-MM-DD';
export const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm';

export const DATE_TIME_LONG_FORMAT = 'YYYY-MM-DDTHH:mm';

export const useDateFormat = ({ entityRef }: { entityRef?: Ref<Record<string, any>> } = {}) => {
  const formatDate = value => (value ? dayjs(value).format(DATE_TIME_FORMAT) : '');
  const dateFormatUtils = {
    convertDateTimeFromServer: (date: Date): string => (date && dayjs(date).isValid() ? dayjs(date).format(DATE_TIME_LONG_FORMAT) : null),
    formatDate,
    formatDuration: value => (value ? (dayjs.duration(value).humanize() ?? value) : ''),
    formatDateLong: formatDate,
    formatDateShort: formatDate,
  };
  const entityUtils = entityRef
    ? {
        ...dateFormatUtils,
        updateInstantField: (field: string, event: any) => {
          if (event.target?.value) {
            entityRef.value[field] = dayjs(event.target.value, DATE_TIME_LONG_FORMAT);
          } else {
            entityRef.value[field] = null;
          }
        },
        updateZonedDateTimeField: (field: string, event: any) => {
          if (event.target?.value) {
            entityRef.value[field] = dayjs(event.target.value, DATE_TIME_LONG_FORMAT);
          } else {
            entityRef.value[field] = null;
          }
        },
      }
    : {};

  return {
    ...dateFormatUtils,
    ...entityUtils,
  };
};
