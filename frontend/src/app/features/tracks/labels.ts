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
