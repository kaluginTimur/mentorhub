import {
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  viewChild,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideClock, lucideLock, lucideLogOut, lucideShieldAlert } from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { AccessChannel } from '../../core/auth/access-channel';
import { AuthService } from '../../core/auth/auth.service';
import type { AccessScreen as Access } from '../../core/auth/current-user.service';

interface Message {
  title: string;
  text: string;
  icon: string;
}

const MESSAGES: Record<Exclude<Access, 'full'>, Message> = {
  'invitation-only': {
    title: 'Access by invitation only',
    text: 'MentorHub is available to students and staff of the training center by invitation. If you were invited, open your invitation link and sign in with the email it was sent to.',
    icon: 'lucideLock',
  },
  ended: {
    title: 'Your access has ended',
    text: 'Contact the training center if you think this is a mistake.',
    icon: 'lucideShieldAlert',
  },
  pending: {
    title: 'Your access is being set up',
    text: 'Contact an administrator.',
    icon: 'lucideClock',
  },
};

@Component({
  selector: 'app-access-screen',
  imports: [HlmCardImports, HlmButton, NgIcon],
  providers: [provideIcons({ lucideLock, lucideShieldAlert, lucideClock, lucideLogOut })],
  template: `
    <main class="flex min-h-[60vh] items-center justify-center px-4 py-12">
      <div hlmCard class="w-full max-w-md">
        <div hlmCardContent class="flex flex-col gap-4 pt-2">
          <div
            class="flex size-10 items-center justify-center rounded-lg bg-secondary text-secondary-foreground"
            aria-hidden="true"
          >
            <ng-icon [name]="message.icon" class="size-5" />
          </div>
          <div class="space-y-1.5">
            <h1
              #title
              tabindex="-1"
              class="font-display text-xl font-semibold tracking-tight outline-none"
              [textContent]="message.title"
            ></h1>
            <p class="text-sm text-muted-foreground">{{ message.text }}</p>
          </div>
        </div>
        <div hlmCardFooter class="justify-end">
          <button hlmBtn type="button" (click)="signOut()">
            <ng-icon name="lucideLogOut" aria-hidden="true" />
            Sign out
          </button>
        </div>
      </div>
    </main>
  `,
})
export class AccessScreen {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly message =
    MESSAGES[inject(ActivatedRoute).snapshot.data['access'] as Exclude<Access, 'full'>];
  private readonly title = viewChild.required<ElementRef<HTMLElement>>('title');

  constructor() {
    afterNextRender(() => this.title().nativeElement.focus());
    // access may have changed in another tab (an accepted invitation) or meanwhile (an administrator)
    const recheck = () => void this.router.navigateByUrl('/');
    const onVisible = () => {
      if (document.visibilityState === 'visible') {
        recheck();
      }
    };
    const off = inject(AccessChannel).onChanged(recheck);
    document.addEventListener('visibilitychange', onVisible);
    inject(DestroyRef).onDestroy(() => {
      off();
      document.removeEventListener('visibilitychange', onVisible);
    });
  }

  protected signOut(): void {
    this.auth.signOut();
  }
}
