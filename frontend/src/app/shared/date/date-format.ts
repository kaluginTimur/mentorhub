import { formatDate } from '@angular/common';

/** How a calendar date (a start date) reads on screen, in templates (`date` pipe) and in text. */
export const DATE_FORMAT = 'd MMM y';

export const formatCalendarDate = (isoDate: string): string =>
  formatDate(isoDate, DATE_FORMAT, 'en-US');
