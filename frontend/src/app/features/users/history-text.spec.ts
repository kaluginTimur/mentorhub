import { HistoryEntry } from '../../core/api/models/history-entry';
import { describeEntry } from './history-text';

const base = { id: 'h1', occurredAt: '2026-10-17T10:12:00Z', actor: null };

describe('describeEntry', () => {
  it.each<[Omit<HistoryEntry, 'id' | 'occurredAt' | 'actor'>, string]>([
    [{ action: 'USER_DISABLED', reason: 'GRADUATED' }, 'Disabled: Graduated'],
    [
      { action: 'USER_DISABLED', reason: 'OTHER', comment: 'Moved away' },
      'Disabled: Other — Moved away',
    ],
    [{ action: 'USER_ENABLED' }, 'Enabled'],
    [
      { action: 'USER_ROLES_CHANGED', rolesAdded: ['MENTOR'], rolesRemoved: ['STUDENT'] },
      'Roles changed: added Mentor; removed Student',
    ],
    [
      { action: 'USER_ROLES_CHANGED', rolesAdded: ['ADMIN', 'MENTOR'] },
      'Roles changed: added Administrator, Mentor',
    ],
    [{ action: 'USER_ROLES_CHANGED', rolesRemoved: ['STUDENT'] }, 'Roles changed: removed Student'],
    [
      {
        action: 'USER_PROFILE_UPDATED',
        changes: [
          { field: 'phone', oldValue: '+77011234567', newValue: '+77017654321' },
          { field: 'lastName', oldValue: null, newValue: 'Petrova' },
        ],
      },
      'Profile updated: phone +77011234567 → +77017654321, last name (empty) → Petrova',
    ],
    [
      {
        action: 'USER_BOOTSTRAPPED_ADMIN',
        bootstrap: { adminAdded: true, enabled: false, identityLinked: false, userCreated: false },
      },
      'Granted administrator access by bootstrap',
    ],
    [
      {
        action: 'USER_BOOTSTRAPPED_ADMIN',
        bootstrap: { adminAdded: true, enabled: true, identityLinked: true, userCreated: true },
      },
      'Granted administrator access by bootstrap (created the user, linked the sign-in identity, enabled the user)',
    ],
    [
      {
        action: 'USER_BOOTSTRAPPED_ADMIN',
        bootstrap: { adminAdded: false, enabled: true, identityLinked: false, userCreated: false },
      },
      'Bootstrap (enabled the user)',
    ],
    [
      {
        action: 'USER_INVITATION_ACCEPTED',
        invitation: {
          invitationId: 'i1',
          invitedBy: { id: 'a1', name: 'Ada Admin' },
          userCreated: true,
          identityLinked: true,
          enabled: false,
        },
        rolesAdded: ['MENTOR', 'STUDENT'],
      },
      'Joined by invitation from Ada Admin; roles added: Mentor, Student',
    ],
    [
      {
        action: 'USER_INVITATION_ACCEPTED',
        invitation: {
          invitationId: 'i1',
          invitedBy: { id: 'a1', name: 'Ada Admin' },
          userCreated: false,
          identityLinked: true,
          enabled: true,
        },
        rolesAdded: ['MENTOR'],
        rolesRemoved: ['ADMIN'],
      },
      'Reactivated by invitation from Ada Admin; roles added: Mentor; roles removed: Administrator',
    ],
    [
      {
        action: 'USER_INVITATION_ACCEPTED',
        invitation: {
          invitationId: 'i1',
          invitedBy: null,
          userCreated: true,
          identityLinked: true,
          enabled: false,
        },
      },
      'Joined by invitation',
    ],
  ])('describes %j', (partial, text) => {
    expect(describeEntry({ ...base, ...partial } satisfies HistoryEntry)).toBe(text);
  });

  // USR-11: the sentence of a coordinator named or removed names the track as "<code> · <name>", as stored.
  it('names the track of a coordinator named or removed', () => {
    const track = { id: 't1', code: 'QA', name: 'Quality Assurance' };
    const added = describeEntry({ ...base, action: 'USER_TRACK_COORDINATOR_ADDED', track });
    const removed = describeEntry({ ...base, action: 'USER_TRACK_COORDINATOR_REMOVED', track });
    expect(added).toContain('QA · Quality Assurance');
    expect(removed).toContain('QA · Quality Assurance');
    expect(added).not.toBe(removed);
  });

  it('names the track of an enrollment as "<code> · <name>", as stored', () => {
    const track = { id: 't1', code: 'QA', name: 'Quality Assurance' };
    expect(
      describeEntry({ ...base, action: 'USER_ENROLLED', track, startDate: '2026-09-01' }),
    ).toContain('QA · Quality Assurance');
  });

  it('copes with missing optional lists', () => {
    expect(describeEntry({ ...base, action: 'USER_ROLES_CHANGED' })).toBe('Roles changed');
    expect(describeEntry({ ...base, action: 'USER_PROFILE_UPDATED' })).toBe('Profile updated');
  });
});
