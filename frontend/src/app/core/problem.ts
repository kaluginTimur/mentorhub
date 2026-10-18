import { HttpErrorResponse } from '@angular/common/http';
import { ValidationError } from './api/models/validation-error';

export const PROBLEM = {
  validationFailed: 'urn:mentorhub:problem:validation-failed',
  selfLockout: 'urn:mentorhub:problem:self-lockout',
  lastAdmin: 'urn:mentorhub:problem:last-admin',
} as const;

export interface ProblemInfo {
  status: number;
  type: string | null;
  errors: ValidationError[];
}

/** The status, problem type and field errors of a failed API call; null for anything else. */
export function problemOf(error: unknown): ProblemInfo | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const body = error.error as { type?: unknown; errors?: unknown } | null;
  return {
    status: error.status,
    type: typeof body?.type === 'string' ? body.type : null,
    errors: Array.isArray(body?.errors) ? (body.errors as ValidationError[]) : [],
  };
}

/** True when the API refused the call with 403; accepts the error a resource keeps (possibly wrapped). */
export function isForbidden(error: unknown): boolean {
  const cause = (error as { cause?: unknown } | undefined)?.cause;
  return [error, cause].some((e) => e instanceof HttpErrorResponse && e.status === 403);
}
