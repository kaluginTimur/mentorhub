import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { Unavailable } from '../../shared/unavailable/unavailable';

/** The API was unavailable during a navigation; "Retry" repeats that navigation. */
@Component({
  selector: 'app-unavailable-page',
  imports: [Unavailable],
  template: `<app-unavailable (retry)="retry()" />`,
})
export class UnavailablePage {
  private readonly router = inject(Router);
  private readonly currentUser = inject(CurrentUserService);

  protected retry(): void {
    void this.router.navigateByUrl(this.currentUser.failedUrl ?? '/', {
      onSameUrlNavigation: 'reload',
    });
  }
}
