import { DisableReason } from '../../core/api/models/disable-reason';
import { Role } from '../../core/api/models/role';
import { UserStatus } from '../../core/api/models/user-status';

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrator',
  COORDINATOR: 'Coordinator',
  MENTOR: 'Mentor',
  STUDENT: 'Student',
  OBSERVER: 'Observer',
};

export const REASON_LABELS: Record<DisableReason, string> = {
  GRADUATED: 'Graduated',
  EXPELLED: 'Expelled',
  LEFT_PROGRAM: 'Left the program',
  STAFF_OFFBOARDED: 'No longer on staff',
  SECURITY: 'Security concern',
  OTHER: 'Other',
};

export const STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: 'Active',
  DISABLED: 'Disabled',
};
