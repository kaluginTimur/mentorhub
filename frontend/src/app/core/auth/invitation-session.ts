import { Service } from '@angular/core';
import { readSession, writeSession } from './session-store';

const TOKEN_KEY = 'mentorhub.invitation-token';
const AUTO_ACCEPT_KEY = 'mentorhub.invitation-auto-accept';

/**
 * The invitation token lives only in session storage: it must survive the round trip to the
 * identity provider, but never goes into a URL, the OIDC state or a log.
 */
@Service()
export class InvitationSession {
  token(): string | null {
    return readSession(TOKEN_KEY);
  }

  setToken(token: string): void {
    writeSession(TOKEN_KEY, token);
  }

  /** True when the visitor left for the identity provider from the landing page. */
  autoAccept(): boolean {
    return readSession(AUTO_ACCEPT_KEY) !== null;
  }

  setAutoAccept(value: boolean): void {
    writeSession(AUTO_ACCEPT_KEY, value ? '1' : null);
  }

  clear(): void {
    writeSession(TOKEN_KEY, null);
    writeSession(AUTO_ACCEPT_KEY, null);
  }
}
