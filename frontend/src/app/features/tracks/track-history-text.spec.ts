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
});
