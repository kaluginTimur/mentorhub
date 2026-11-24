import { TrackHistoryEntry } from '../../core/api/models/track-history-entry';
import { describeTrackEntry } from './track-history-text';

const base = { id: 'h1', occurredAt: '2026-11-15T10:00:00Z', actor: null };

describe('describeTrackEntry', () => {
  it('names the student of an enrollment', () => {
    const entry: TrackHistoryEntry = {
      ...base,
      action: 'TRACK_STUDENT_ENROLLED',
      user: { id: 's1', name: 'Sam Student' },
      startDate: '2026-09-01',
    };

    expect(describeTrackEntry(entry)).toContain('Sam Student');
  });

  // The date reads as in the enrollments list ("29 Jun 2026"), not as stored.
  it('shows the start date of an enrollment as the enrollments list does', () => {
    const entry: TrackHistoryEntry = {
      ...base,
      action: 'TRACK_STUDENT_ENROLLED',
      user: { id: 's1', name: 'Sam Student' },
      startDate: '2026-06-29',
    };

    expect(describeTrackEntry(entry)).toContain('from 29 Jun 2026');
    expect(describeTrackEntry(entry)).not.toContain('2026-06-29');
  });

  describe('the cohorts', () => {
    const spring = { id: 'c1', name: 'Spring 2026' };
    const autumn = { id: 'c2', name: 'Autumn 2026' };
    const sam = { id: 's1', name: 'Sam Student' };

    it('names the cohort an enrollment is made into', () => {
      const entry: TrackHistoryEntry = {
        ...base,
        action: 'TRACK_STUDENT_ENROLLED',
        user: sam,
        startDate: '2026-09-01',
        cohort: spring,
      };

      expect(describeTrackEntry(entry)).toContain('Spring 2026');
    });

    it('names the cohort created and its start date as the lists show it', () => {
      const text = describeTrackEntry({
        ...base,
        action: 'TRACK_COHORT_CREATED',
        cohort: spring,
        startDate: '2026-06-29',
      });

      expect(text).toContain('Spring 2026');
      expect(text).toContain('29 Jun 2026');
      expect(text).not.toContain('2026-06-29');
    });

    it('names the cohort deleted, told apart from one created', () => {
      const entry = { ...base, cohort: spring, startDate: '2026-06-29' };
      const deleted = describeTrackEntry({ ...entry, action: 'TRACK_COHORT_DELETED' });

      expect(deleted).toContain('Spring 2026');
      expect(deleted).toContain('29 Jun 2026');
      expect(deleted).not.toBe(describeTrackEntry({ ...entry, action: 'TRACK_COHORT_CREATED' }));
    });

    it('names the student and both cohorts of a move', () => {
      const text = describeTrackEntry({
        ...base,
        action: 'TRACK_ENROLLMENT_COHORT_CHANGED',
        user: sam,
        cohortChange: { from: spring, to: autumn },
      });

      expect(text).toContain('Sam Student');
      expect(text).toContain('Spring 2026');
      expect(text).toContain('Autumn 2026');
    });

    it('names the student and only the cohort joined when put in', () => {
      const text = describeTrackEntry({
        ...base,
        action: 'TRACK_ENROLLMENT_COHORT_CHANGED',
        user: sam,
        cohortChange: { from: null, to: autumn },
      });

      expect(text).toContain('Sam Student');
      expect(text).toContain('Autumn 2026');
    });

    it('names the student and only the cohort left when taken out', () => {
      const text = describeTrackEntry({
        ...base,
        action: 'TRACK_ENROLLMENT_COHORT_CHANGED',
        user: sam,
        cohortChange: { from: spring, to: null },
      });

      expect(text).toContain('Sam Student');
      expect(text).toContain('Spring 2026');
    });

    it('tells moved, put in and taken out apart', () => {
      const text = (from: typeof spring | null, to: typeof spring | null) =>
        describeTrackEntry({
          ...base,
          action: 'TRACK_ENROLLMENT_COHORT_CHANGED',
          user: sam,
          cohortChange: { from, to },
        });

      expect(new Set([text(spring, autumn), text(null, autumn), text(spring, null)]).size).toBe(3);
    });
  });
});
