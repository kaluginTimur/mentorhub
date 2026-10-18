import { CreatedInvitation } from '../../core/api/models/created-invitation';
import { Invitation } from '../../core/api/models/invitation';

export function invitation(overrides: Partial<Invitation> = {}): Invitation {
  return {
    id: 'i1',
    email: 'ivan@example.com',
    roles: ['MENTOR'],
    status: 'PENDING',
    createdAt: '2026-10-18T09:00:00Z',
    expiresAt: '2026-10-25T09:00:00Z',
    createdBy: { id: 'a1', name: 'Ada Admin' },
    ...overrides,
  };
}

export function created(overrides: Partial<Invitation> = {}): CreatedInvitation {
  return {
    invitation: invitation(overrides),
    link: 'http://localhost:4200/invite/Xf3abc',
  };
}
