import { HttpErrorResponse } from '@angular/common/http';
import { ValidationError } from './api/models/validation-error';

export const PROBLEM = {
  validationFailed: 'urn:mentorhub:problem:validation-failed',
  selfLockout: 'urn:mentorhub:problem:self-lockout',
  lastAdmin: 'urn:mentorhub:problem:last-admin',
  invitationInvalid: 'urn:mentorhub:problem:invitation-invalid',
  invitationClosed: 'urn:mentorhub:problem:invitation-closed',
  invitationExpired: 'urn:mentorhub:problem:invitation-expired',
  invitationExists: 'urn:mentorhub:problem:invitation-exists',
  invitationEmailMismatch: 'urn:mentorhub:problem:invitation-email-mismatch',
  identityLinkedElsewhere: 'urn:mentorhub:problem:identity-linked-elsewhere',
  emailUnavailable: 'urn:mentorhub:problem:email-unavailable',
  emailQueued: 'urn:mentorhub:problem:email-queued',
  userActive: 'urn:mentorhub:problem:user-active',
} as const;

export interface ProblemInfo {
  status: number;
  type: string | null;
  errors: ValidationError[];
  /** The `userId` extension of a `user-active` problem. */
  userId: string | null;
  /** The `invitationId` extension of an `invitation-exists` problem. */
  invitationId: string | null;
}

/** The status, problem type and field errors of a failed API call; null for anything else. */
export function problemOf(error: unknown): ProblemInfo | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const body = error.error as {
    type?: unknown;
    errors?: unknown;
    userId?: unknown;
    invitationId?: unknown;
  } | null;
  return {
    status: error.status,
    type: typeof body?.type === 'string' ? body.type : null,
    errors: Array.isArray(body?.errors) ? (body.errors as ValidationError[]) : [],
    userId: typeof body?.userId === 'string' ? body.userId : null,
    invitationId: typeof body?.invitationId === 'string' ? body.invitationId : null,
  };
}

/** True when the API refused the call with 403; accepts the error a resource keeps (possibly wrapped). */
export function isForbidden(error: unknown): boolean {
  const cause = (error as { cause?: unknown } | undefined)?.cause;
  return [error, cause].some((e) => e instanceof HttpErrorResponse && e.status === 403);
}
