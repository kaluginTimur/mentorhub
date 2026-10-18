import { Service, signal } from '@angular/core';
import { Params } from '@angular/router';

/** Remembers the list's last query, so the user page can link back to it. */
@Service()
export class UsersListState {
  readonly query = signal<Params>({});
}
