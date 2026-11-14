import { HttpErrorResponse } from '@angular/common/http';
import { InvitationConflictProblem } from '../api/models/invitation-conflict-problem';
import { Problem } from '../api/models/problem';
import { ValidationProblem } from '../api/models/validation-problem';

export type ProblemBody = Problem | ValidationProblem | InvitationConflictProblem;

export function problemError(body: ProblemBody): HttpErrorResponse {
  return new HttpErrorResponse({ status: body.status, error: body });
}
