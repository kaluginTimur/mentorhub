import { StatusTone } from '../../shared/status-dot/status-dot';
import { InvitationStatus } from '../../core/api/models/invitation-status';

export const INVITATION_STATUS_LABELS: Record<InvitationStatus, string> = {
  PENDING: 'Pending',
  EXPIRED: 'Expired',
  ACCEPTED: 'Accepted',
  REVOKED: 'Revoked',
};

export const INVITATION_STATUS_TONES: Record<InvitationStatus, StatusTone> = {
  PENDING: 'info',
  ACCEPTED: 'success',
  EXPIRED: 'neutral',
  REVOKED: 'neutral',
};
