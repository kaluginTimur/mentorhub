import { MeUser } from '../../core/api/models/me-user';
import { UserDetails } from '../../core/api/models/user-details';

export const ADMIN_ID = '11111111-1111-4111-8111-111111111111';
export const USER_ID = '22222222-2222-4222-8222-222222222222';

export function userDetails(overrides: Partial<UserDetails> = {}): UserDetails {
  return {
    id: USER_ID,
    email: 'ivan@example.com',
    firstName: 'Ivan',
    lastName: 'Sidorov',
    phone: '+77011234567',
    status: 'ACTIVE',
    roles: ['MENTOR'],
    createdAt: '2026-10-12T09:00:00Z',
    ...overrides,
  };
}

export function meUser(overrides: Partial<MeUser> = {}): MeUser {
  return {
    id: ADMIN_ID,
    email: 'anna@example.com',
    firstName: 'Anna',
    lastName: 'Petrova',
    phone: null,
    roles: ['ADMIN'],
    permissions: ['USERS_READ', 'USERS_MANAGE'],
    ...overrides,
  };
}
