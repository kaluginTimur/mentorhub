import { EnrolledStudent } from '../../core/api/models/enrolled-student';
import { EnrollmentCandidate } from '../../core/api/models/enrollment-candidate';
import { EnrollmentContact } from '../../core/api/models/enrollment-contact';
import { MeUser } from '../../core/api/models/me-user';
import { MyEnrollment } from '../../core/api/models/my-enrollment';
import { TrackEnrollment } from '../../core/api/models/track-enrollment';
import { TrackEnrollmentPage } from '../../core/api/models/track-enrollment-page';

export const STUDENT_ID = '66666666-6666-4666-8666-666666666666';
export const ENROLLMENT_ID = '77777777-7777-4777-8777-777777777777';
export const CANDIDATE_STUDENT_ID = '88888888-8888-4888-8888-888888888888';
export const TRACK_ID = '33333333-3333-4333-8333-333333333333';

export function enrolledStudent(overrides: Partial<EnrolledStudent> = {}): EnrolledStudent {
  return {
    id: STUDENT_ID,
    name: 'Sam Student',
    email: 'sam@example.com',
    disabled: false,
    studentRole: true,
    ...overrides,
  };
}

export function trackEnrollment(overrides: Partial<TrackEnrollment> = {}): TrackEnrollment {
  return {
    id: ENROLLMENT_ID,
    student: enrolledStudent(),
    startDate: '2026-09-01',
    status: 'ACTIVE',
    ...overrides,
  };
}

export function enrollmentPage(
  items: TrackEnrollment[],
  overrides: Partial<TrackEnrollmentPage> = {},
): TrackEnrollmentPage {
  return { items, page: 0, size: 25, totalItems: items.length, ...overrides };
}

export function enrollmentCandidate(
  overrides: Partial<EnrollmentCandidate> = {},
): EnrollmentCandidate {
  return {
    id: CANDIDATE_STUDENT_ID,
    name: 'Olga Candidate',
    email: 'olga@example.com',
    ...overrides,
  };
}

export function contact(overrides: Partial<EnrollmentContact> = {}): EnrollmentContact {
  return {
    name: 'Clara Coordinator',
    email: 'clara@example.com',
    phone: '+77011234567',
    ...overrides,
  };
}

export function myEnrollment(overrides: Partial<MyEnrollment> = {}): MyEnrollment {
  return {
    id: ENROLLMENT_ID,
    track: { id: TRACK_ID, code: 'QA', name: 'Quality Assurance' },
    startDate: '2026-09-01',
    status: 'ACTIVE',
    coordinators: [contact()],
    ...overrides,
  };
}

/** A user who holds ENROLLMENTS_READ and ENROLLMENTS_MANAGE, as an administrator does. */
export function enrollmentsAdmin(overrides: Partial<MeUser> = {}): MeUser {
  return {
    id: '11111111-1111-4111-8111-111111111111',
    email: 'anna@example.com',
    firstName: 'Anna',
    lastName: 'Petrova',
    phone: null,
    roles: ['ADMIN'],
    permissions: [
      'USERS_READ',
      'TRACKS_READ',
      'TRACKS_MANAGE',
      'ENROLLMENTS_READ',
      'ENROLLMENTS_MANAGE',
    ],
    ...overrides,
  };
}
