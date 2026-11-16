import { formatCalendarDate } from '../../shared/date/date-format';
import { TrackHistoryEntry } from '../../core/api/models/track-history-entry';

export function describeTrackEntry(entry: TrackHistoryEntry): string {
  const user = entry.user?.name ?? 'a user';
  switch (entry.action) {
    case 'TRACK_CREATED':
      return `Created with code ${entry.code ?? ''} and name ${entry.name ?? ''}`;
    case 'TRACK_UPDATED': {
      const changes = (entry.changes ?? []).map((c) => `${c.field} ${c.oldValue} → ${c.newValue}`);
      return changes.length > 0 ? `Changed ${changes.join(', ')}` : 'Changed';
    }
    case 'TRACK_ARCHIVED':
      return 'Archived';
    case 'TRACK_RESTORED':
      return 'Restored';
    case 'TRACK_COORDINATOR_ADDED':
      return `Named ${user} a coordinator`;
    case 'TRACK_COORDINATOR_REMOVED':
      return `Removed ${user} as a coordinator`;
    case 'TRACK_STUDENT_ENROLLED':
      return `Enrolled ${user}${entry.startDate ? ` from ${formatCalendarDate(entry.startDate)}` : ''}`;
    default: {
      const unreachable: never = entry.action;
      return unreachable;
    }
  }
}
