import { Service, signal } from '@angular/core';
import { Params } from '@angular/router';

/** Remembers the list's last query, so the track page can link back to it. */
@Service()
export class TracksListState {
  readonly query = signal<Params>({});
}
