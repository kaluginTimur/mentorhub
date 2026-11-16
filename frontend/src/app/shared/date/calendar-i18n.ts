import { provideBrnCalendarI18n } from '@spartan-ng/brain/calendar';

const caption = (month: number, year: number, options: Intl.DateTimeFormatOptions) =>
  new Date(year, month).toLocaleDateString('en-US', options);

/** Month names and captions of every calendar in English, whatever the browser's language (C-10); weeks start on Monday. */
export const provideEnglishCalendar = () =>
  provideBrnCalendarI18n({
    formatHeader: (month, year) => caption(month, year, { month: 'long', year: 'numeric' }),
    formatMonth: (month) => caption(month, 2000, { month: 'short' }),
    formatYear: (year) => caption(0, year, { year: 'numeric' }),
    firstDayOfWeek: () => 1,
  });
