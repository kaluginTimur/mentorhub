import { Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, NavigationError, Router, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { stripCallbackParams } from './core/auth/api-urls';
import { Loading } from './shared/loading/loading';
import { Unavailable } from './shared/unavailable/unavailable';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Loading, Unavailable],
  templateUrl: './app.html',
  host: { '(window:pageshow)': 'onPageShow($event)' },
})
export class App {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly initialNavigationDone = signal(false);

  protected readonly startupError = this.auth.startupError;
  protected readonly loading = computed(
    () => this.auth.redirecting() || !this.initialNavigationDone(),
  );

  constructor() {
    this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd || event instanceof NavigationError) {
        this.initialNavigationDone.set(true);
      }
    });
  }

  protected onPageShow(event: PageTransitionEvent): void {
    // restored from the back/forward cache, for example after Back from the sign-in page
    if (event.persisted) {
      this.auth.redirecting.set(false);
    }
  }

  protected reload(): void {
    // a leftover callback URL would be processed, and fail, again
    globalThis.location.assign(stripCallbackParams(globalThis.location.href));
  }
}
