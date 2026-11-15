import { TrackStatus } from '../../core/api/models/track-status';
import { TrackStatusFilter } from '../../core/api/models/track-status-filter';

export const TRACK_STATUS_LABELS: Record<TrackStatus, string> = {
  ACTIVE: 'Active',
  ARCHIVED: 'Archived',
};

export const TRACK_FILTER_LABELS: Record<TrackStatusFilter, string> = {
  ACTIVE: 'Active',
  ARCHIVED: 'Archived',
  ALL: 'All',
};

export const TRACK_CODE_LIMIT = 20;
export const TRACK_NAME_LIMIT = 100;

/** How the UI names a track: its code and its name, joined by a middle dot. */
export const trackLabel = (track: { code: string; name: string }): string =>
  `${track.code} · ${track.name}`;
