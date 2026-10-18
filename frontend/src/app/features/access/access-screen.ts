import {
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  viewChild,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { AccessChannel } from '../../core/auth/access-channel';
import { AuthService } from '../../core/auth/auth.service';
import type { AccessScreen as Access } from '../../core/auth/current-user.service';

interface Message {
  title: string;
  text: string;
}

const MESSAGES: Record<Exclude<Access, 'full'>, Message> = {
  'invitation-only': {
    title: 'Access by invitation only',
    text: 'MentorHub is available to students and staff of the training center by invitation. If you were invited, open your invitation link and sign in with the email it was sent to.',
  },
  ended: {
    title: 'Your access has ended',
    text: 'Contact the training center if you think this is a mistake.',
  },
  pending: {
    title: 'Your access is being set up',
    text: 'Contact an administrator.',
  },
};

@Component({
  selector: 'app-access-screen',
  imports: [MatCardModule, MatButtonModule],
  template: `
    <main>
      <mat-card appearance="outlined">
        <mat-card-content>
          <h1 #title tabindex="-1">{{ message.title }}</h1>
          <p>{{ message.text }}</p>
        </mat-card-content>
        <mat-card-actions align="end">
          <button matButton="filled" type="button" (click)="signOut()">Sign out</button>
        </mat-card-actions>
      </mat-card>
    </main>
  `,
  styleUrl: './access-screen.scss',
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
