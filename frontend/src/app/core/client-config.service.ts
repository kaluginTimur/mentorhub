import { Service, signal } from '@angular/core';
import { ClientConfig } from './api/models/client-config';

/** Public client configuration flags the UI depends on; filled when the configuration is loaded at startup. */
@Service()
export class ClientConfigService {
  private readonly emails = signal(false);
  readonly invitationEmails = this.emails.asReadonly();

  apply(config: ClientConfig): void {
    this.emails.set(config.invitationEmails === true);
  }
}
