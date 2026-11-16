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
});
