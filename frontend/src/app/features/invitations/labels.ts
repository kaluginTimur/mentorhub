import { InvitationStatus } from '../../core/api/models/invitation-status';

export const INVITATION_STATUS_LABELS: Record<InvitationStatus, string> = {
  PENDING: 'Pending',
  EXPIRED: 'Expired',
  ACCEPTED: 'Accepted',
  REVOKED: 'Revoked',
};
