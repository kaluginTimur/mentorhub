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
    case 'TRACK_MENTOR_ADDED':
      return `Named ${user} a mentor`;
    case 'TRACK_MENTOR_REMOVED':
      return `Removed ${user} as a mentor`;
    case 'TRACK_STUDENT_ENROLLED':
      return `Enrolled ${user}${entry.startDate ? ` from ${formatCalendarDate(entry.startDate)}` : ''}${entry.cohort ? `, into the cohort ${entry.cohort.name}` : ''}`;
    case 'TRACK_ENROLLMENT_MENTOR_ASSIGNED':
      return `Assigned ${entry.mentor?.name ?? 'a mentor'} as a mentor of ${user}`;
    case 'TRACK_ENROLLMENT_MENTOR_REMOVED':
      return `Removed ${entry.mentor?.name ?? 'a mentor'} as a mentor of ${user}`;
    case 'TRACK_COHORT_MENTOR_ASSIGNED':
      return `Assigned ${entry.mentor?.name ?? 'a mentor'} as a mentor of the cohort ${entry.cohort?.name ?? ''}`;
    case 'TRACK_COHORT_MENTOR_REMOVED':
      return `Removed ${entry.mentor?.name ?? 'a mentor'} as a mentor of the cohort ${entry.cohort?.name ?? ''}`;
    case 'TRACK_COHORT_CREATED':
      return `Created the cohort ${entry.cohort?.name ?? ''}${entry.startDate ? `, starting ${formatCalendarDate(entry.startDate)}` : ''}`;
    case 'TRACK_COHORT_DELETED':
      return `Deleted the cohort ${entry.cohort?.name ?? ''}${entry.startDate ? `, which started ${formatCalendarDate(entry.startDate)}` : ''}`;
    case 'TRACK_ENROLLMENT_COHORT_CHANGED': {
      const { from, to } = entry.cohortChange ?? { from: null, to: null };
      if (from && to) {
        return `Moved ${user} from the cohort ${from.name} to the cohort ${to.name}`;
      }
      if (to) {
        return `Put ${user} into the cohort ${to.name}`;
      }
      return from ? `Took ${user} out of the cohort ${from.name}` : `Changed the cohort of ${user}`;
    }
    default: {
      const unreachable: never = entry.action;
      return unreachable;
    }
  }
}
