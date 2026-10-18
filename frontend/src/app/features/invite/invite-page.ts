import {
  Component,
  Injector,
  afterNextRender,
  effect,
  inject,
  signal,
  untracked,
  ElementRef,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Api } from '../../core/api/api';
import { acceptInvitation } from '../../core/api/fn/invitations/accept-invitation';
import { previewInvitation } from '../../core/api/fn/invitations/preview-invitation';
import { InvitationPreview } from '../../core/api/models/invitation-preview';
import { AccountIdentity, AuthService } from '../../core/auth/auth.service';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { InvitationSession } from '../../core/auth/invitation-session';
import { PROBLEM, problemOf } from '../../core/problem';
import { Loading } from '../../shared/loading/loading';
import { Unavailable } from '../../shared/unavailable/unavailable';

type View =
  | 'loading'
  | 'invalid'
  | 'anonymous'
  | 'signed-in'
  | 'accepting'
  | 'mismatch'
  | 'linked-elsewhere'
  | 'error';

const LANDING_PATH = '/invite';

/** The public landing page of an invitation link. */
@Component({
  selector: 'app-invite-page',
  imports: [MatButtonModule, MatCardModule, MatProgressSpinnerModule, Loading, Unavailable],
  templateUrl: './invite-page.html',
  styleUrl: '../access/access-screen.scss',
})
export class InvitePage {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);
  private readonly currentUser = inject(CurrentUserService);
  private readonly session = inject(InvitationSession);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly view = signal<View>('loading');
  protected readonly preview = signal<InvitationPreview | null>(null);
  protected readonly account = signal<AccountIdentity>({ email: null });
  private failedStep: 'start' | 'accept' = 'start';

  constructor() {
    effect(() => {
      this.view();
      untracked(() =>
        afterNextRender(() => this.host.nativeElement.querySelector('h1')?.focus(), {
          injector: this.injector,
        }),
      );
    });
    void this.start();
  }

  /** The account's email equals the invitation's, so the account itself is the problem. */
  protected unverified(): boolean {
    const invited = this.preview()?.email.toLowerCase();
    return !!invited && this.account().email?.toLowerCase() === invited;
  }

  protected retry(): void {
    void (this.failedStep === 'accept' ? this.accept() : this.start());
  }

  protected signIn(register: boolean): Promise<void> {
    return this.leaveForSignIn(register);
  }

  protected useAnotherAccount(): void {
    this.session.setAutoAccept(false);
    this.auth.signOut();
  }

  protected signOut(): void {
    this.session.clear();
    this.auth.signOut();
  }

  protected async accept(): Promise<void> {
    const token = this.session.token();
    if (token === null) {
      this.view.set('invalid');
      return;
    }
    this.session.setAutoAccept(false);
    this.view.set('accepting');
    try {
      const me = await this.api.invoke(acceptInvitation, { body: { token } });
      this.session.clear();
      this.currentUser.me.set(me);
      await this.router.navigateByUrl('/');
    } catch (error) {
      const type = problemOf(error)?.type;
      if (type === PROBLEM.invitationInvalid) {
        this.session.clear();
        this.view.set('invalid');
      } else if (type === PROBLEM.invitationEmailMismatch) {
        this.view.set('mismatch');
      } else if (type === PROBLEM.identityLinkedElsewhere) {
        this.view.set('linked-elsewhere');
      } else {
        this.failedStep = 'accept';
        this.view.set('error');
      }
    }
  }

  private async start(): Promise<void> {
    this.failedStep = 'start';
    this.view.set('loading');
    const fromLink = this.route.snapshot.paramMap.get('token');
    if (fromLink) {
      // the token leaves the address bar, the router state and the history at once;
      // the instance of the token-less route reads it from session storage
      this.session.setToken(fromLink);
      this.session.setAutoAccept(false);
      await this.router.navigate([LANDING_PATH], { replaceUrl: true });
      return;
    }
    const token = this.session.token();
    if (token === null) {
      this.view.set('invalid');
      return;
    }
    try {
      this.preview.set(await this.api.invoke(previewInvitation, { body: { token } }));
    } catch (error) {
      const problem = problemOf(error);
      if (problem?.type === PROBLEM.invitationInvalid || problem?.status === 400) {
        this.session.clear();
        this.view.set('invalid');
      } else {
        this.view.set('error');
      }
      return;
    }
    if (!(await this.auth.isSignedIn(LANDING_PATH))) {
      this.view.set('anonymous');
      return;
    }
    this.account.set(await this.auth.accountIdentity());
    if (this.session.autoAccept()) {
      await this.accept();
    } else {
      this.view.set('signed-in');
    }
  }

  private async leaveForSignIn(register: boolean): Promise<void> {
    this.session.setAutoAccept(true);
    const started = await this.auth.signIn(LANDING_PATH, {
      loginHint: this.preview()?.email,
      register,
    });
    if (!started) {
      this.session.setAutoAccept(false);
      this.auth.startupError.set(true);
    }
  }
}
