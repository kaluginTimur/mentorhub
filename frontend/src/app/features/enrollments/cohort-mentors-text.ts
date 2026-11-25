import { CohortMentor } from '../../core/api/models/cohort-mentor';

export const mentorsText = (n: number) => `${n} ${n === 1 ? 'mentor' : 'mentors'}`;

/** A mentor whose assignment grants nothing: disabled, or without the mentor role. */
export const isInactive = (m: Pick<CohortMentor, 'disabled' | 'mentorRole'>) =>
  m.disabled || !m.mentorRole;

/** Active mentors first, so the names shown first are the ones that can act. */
export function activeFirst<T extends Pick<CohortMentor, 'disabled' | 'mentorRole'>>(
  mentors: readonly T[],
): T[] {
  return [...mentors].sort((a, b) => Number(isInactive(a)) - Number(isInactive(b)));
}

export function markWords(m: Pick<CohortMentor, 'disabled' | 'mentorRole'>): string {
  const marks = [m.disabled ? 'Disabled' : '', m.mentorRole ? '' : 'No mentor role'].filter(
    Boolean,
  );
  return marks.length > 0 ? `, ${marks.join(', ')}` : '';
}

/** "2 mentors · 1 inactive" */
export function summaryText(mentors: readonly CohortMentor[]): string {
  const inactive = mentors.filter(isInactive).length;
  return `${mentorsText(mentors.length)}${inactive > 0 ? ` · ${inactive} inactive` : ''}`;
}

/**
 * How many of the names fit in one row of `available` pixels next to the "+k" button of `moreWidth`:
 * all of them when they fit without the button, otherwise as many as fit with it, at least one.
 */
export function fitCount(widths: readonly number[], moreWidth: number, available: number): number {
  if (widths.reduce((a, b) => a + b, 0) <= available) {
    return widths.length;
  }
  let used = moreWidth;
  let count = 0;
  for (const w of widths) {
    if (used + w > available) {
      break;
    }
    used += w;
    count++;
  }
  return Math.max(1, count);
}

/** The load of a candidate on the track: "3 students · 1 cohort" (COH-10.2, ENR-10.2). */
export function loadText(c: { students: number; cohorts: number }): string {
  const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`;
  return `${plural(c.students, 'student')} · ${plural(c.cohorts, 'cohort')}`;
}
