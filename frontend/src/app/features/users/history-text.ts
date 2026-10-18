import { HistoryEntry } from '../../core/api/models/history-entry';
import { ProfileField } from '../../core/api/models/profile-field';
import { REASON_LABELS, ROLE_LABELS } from './labels';

const FIELD_LABELS: Record<ProfileField, string> = {
  firstName: 'first name',
  lastName: 'last name',
  phone: 'phone',
};

const value = (v: string | null): string => (v ? v : '(empty)');

export function describeEntry(entry: HistoryEntry): string {
  switch (entry.action) {
    case 'USER_DISABLED': {
      const reason = entry.reason ? REASON_LABELS[entry.reason] : 'No reason';
      return entry.comment ? `Disabled: ${reason} — ${entry.comment}` : `Disabled: ${reason}`;
    }
    case 'USER_ENABLED':
      return 'Enabled';
    case 'USER_ROLES_CHANGED': {
      const parts: string[] = [];
      const added = (entry.rolesAdded ?? []).map((r) => ROLE_LABELS[r]);
      const removed = (entry.rolesRemoved ?? []).map((r) => ROLE_LABELS[r]);
      if (added.length > 0) {
        parts.push(`added ${added.join(', ')}`);
      }
      if (removed.length > 0) {
        parts.push(`removed ${removed.join(', ')}`);
      }
      return parts.length > 0 ? `Roles changed: ${parts.join('; ')}` : 'Roles changed';
    }
    case 'USER_PROFILE_UPDATED': {
      const changes = (entry.changes ?? []).map(
        (c) => `${FIELD_LABELS[c.field]} ${value(c.oldValue)} → ${value(c.newValue)}`,
      );
      return changes.length > 0 ? `Profile updated: ${changes.join(', ')}` : 'Profile updated';
    }
    case 'USER_BOOTSTRAPPED_ADMIN': {
      const b = entry.bootstrap;
      const extras = [
        b?.userCreated ? 'created the user' : '',
        b?.identityLinked ? 'linked the sign-in identity' : '',
        b?.enabled ? 'enabled the user' : '',
      ].filter(Boolean);
      const base =
        b?.adminAdded === false ? 'Bootstrap' : 'Granted administrator access by bootstrap';
      return extras.length > 0 ? `${base} (${extras.join(', ')})` : base;
    }
    case 'USER_INVITATION_ACCEPTED': {
      const inviter = entry.invitation?.invitedBy?.name;
      const base = entry.invitation?.enabled ? 'Reactivated by invitation' : 'Joined by invitation';
      const parts = [inviter ? `${base} from ${inviter}` : base];
      const added = (entry.rolesAdded ?? []).map((r) => ROLE_LABELS[r]);
      const removed = (entry.rolesRemoved ?? []).map((r) => ROLE_LABELS[r]);
      if (added.length > 0) {
        parts.push(`roles added: ${added.join(', ')}`);
      }
      if (removed.length > 0) {
        parts.push(`roles removed: ${removed.join(', ')}`);
      }
      return parts.join('; ');
    }
    default: {
      const unreachable: never = entry.action;
      return unreachable;
    }
  }
}
