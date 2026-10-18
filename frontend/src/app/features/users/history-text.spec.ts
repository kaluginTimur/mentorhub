import { HistoryEntry } from '../../core/api/models/history-entry';
import { describeEntry } from './history-text';

const base = { id: 'h1', occurredAt: '2026-10-17T10:12:00Z', actor: null };

describe('describeEntry', () => {
  it.each<[Partial<HistoryEntry>, string]>([
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
  ])('describes %j', (partial, text) => {
    expect(describeEntry({ ...base, ...partial } as HistoryEntry)).toBe(text);
  });

  it('copes with missing optional lists', () => {
    expect(describeEntry({ ...base, action: 'USER_ROLES_CHANGED' })).toBe('Roles changed');
    expect(describeEntry({ ...base, action: 'USER_PROFILE_UPDATED' })).toBe('Profile updated');
  });
});
