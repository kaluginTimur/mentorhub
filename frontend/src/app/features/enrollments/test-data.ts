import { EnrolledStudent } from '../../core/api/models/enrolled-student';
import { EnrollmentCandidate } from '../../core/api/models/enrollment-candidate';
import { EnrollmentContact } from '../../core/api/models/enrollment-contact';
import { EnrollmentMentor } from '../../core/api/models/enrollment-mentor';
import { MentorCandidate } from '../../core/api/models/mentor-candidate';
import { MentoredEnrollment } from '../../core/api/models/mentored-enrollment';
import { MentoredEnrollmentPage } from '../../core/api/models/mentored-enrollment-page';
import { MeUser } from '../../core/api/models/me-user';
import { MyEnrollment } from '../../core/api/models/my-enrollment';
import { TrackEnrollment } from '../../core/api/models/track-enrollment';
import { TrackEnrollmentPage } from '../../core/api/models/track-enrollment-page';

export const STUDENT_ID = '66666666-6666-4666-8666-666666666666';
export const ENROLLMENT_ID = '77777777-7777-4777-8777-777777777777';
export const CANDIDATE_STUDENT_ID = '88888888-8888-4888-8888-888888888888';
export const MENTOR_ID = '99999999-9999-4999-8999-999999999999';
export const CANDIDATE_MENTOR_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
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
    mentors: [],
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
    mentors: [],
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

export function enrollmentMentor(overrides: Partial<EnrollmentMentor> = {}): EnrollmentMentor {
  return {
    id: MENTOR_ID,
    name: 'Mia Mentor',
    email: 'mia@example.com',
    disabled: false,
    mentorRole: true,
    ...overrides,
  };
}

export function mentorCandidate(overrides: Partial<MentorCandidate> = {}): MentorCandidate {
  return {
    id: CANDIDATE_MENTOR_ID,
    name: 'Omar Candidate',
    email: 'omar@example.com',
    ...overrides,
  };
}

export function mentoredEnrollment(
  overrides: Partial<MentoredEnrollment> = {},
): MentoredEnrollment {
  return {
    id: ENROLLMENT_ID,
    student: enrolledStudent(),
    track: { id: TRACK_ID, code: 'QA', name: 'Quality Assurance' },
    startDate: '2026-09-01',
    status: 'ACTIVE',
    ...overrides,
  };
}

export function mentoredEnrollmentPage(
  items: MentoredEnrollment[],
  overrides: Partial<MentoredEnrollmentPage> = {},
): MentoredEnrollmentPage {
  return { items, page: 0, size: 25, totalItems: items.length, ...overrides };
}

/** A user who holds only ENROLLMENTS_READ_MENTORED, as a mentor does. */
export function mentorUser(overrides: Partial<MeUser> = {}): MeUser {
  return {
    id: MENTOR_ID,
    email: 'mia@example.com',
    firstName: 'Mia',
    lastName: 'Mentor',
    phone: null,
    roles: ['MENTOR'],
    permissions: ['ENROLLMENTS_READ_MENTORED'],
    ...overrides,
  };
}
