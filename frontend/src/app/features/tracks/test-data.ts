import { CoordinatorCandidate } from '../../core/api/models/coordinator-candidate';
import { MeUser } from '../../core/api/models/me-user';
import { Track } from '../../core/api/models/track';
import { TrackCoordinator } from '../../core/api/models/track-coordinator';

export const ADMIN_ID = '11111111-1111-4111-8111-111111111111';
export const TRACK_ID = '33333333-3333-4333-8333-333333333333';
export const COORDINATOR_ID = '44444444-4444-4444-8444-444444444444';
export const CANDIDATE_ID = '55555555-5555-4555-8555-555555555555';

export function coordinator(overrides: Partial<TrackCoordinator> = {}): TrackCoordinator {
  return {
    id: COORDINATOR_ID,
    name: 'Clara Coordinator',
    email: 'clara@example.com',
    disabled: false,
    coordinatorRole: true,
    ...overrides,
  };
}

export function track(overrides: Partial<Track> = {}): Track {
  return {
    id: TRACK_ID,
    code: 'QA',
    name: 'Quality Assurance',
    status: 'ACTIVE',
    coordinators: [coordinator()],
    ...overrides,
  };
}

export function candidate(overrides: Partial<CoordinatorCandidate> = {}): CoordinatorCandidate {
  return {
    id: CANDIDATE_ID,
    name: 'Boris Candidate',
    email: 'boris@example.com',
    ...overrides,
  };
}

export function trackAdmin(overrides: Partial<MeUser> = {}): MeUser {
  return {
    id: ADMIN_ID,
    email: 'anna@example.com',
    firstName: 'Anna',
    lastName: 'Petrova',
    phone: null,
    roles: ['ADMIN'],
    permissions: ['USERS_READ', 'USERS_MANAGE', 'USERS_INVITE', 'TRACKS_READ', 'TRACKS_MANAGE'],
    ...overrides,
  };
}
