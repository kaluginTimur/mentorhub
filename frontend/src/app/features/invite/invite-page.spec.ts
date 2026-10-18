import { Component } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Api } from '../../core/api/api';
import { acceptInvitation } from '../../core/api/fn/invitations/accept-invitation';
import { previewInvitation } from '../../core/api/fn/invitations/preview-invitation';
import { AccessChannel } from '../../core/auth/access-channel';
import { AuthService } from '../../core/auth/auth.service';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { InvitationSession } from '../../core/auth/invitation-session';
import { InvitePage } from './invite-page';

@Component({ template: 'home' })
class Home {}

const problem = (status: number, type: string) =>
  new HttpErrorResponse({ status, error: { type, status } });

const PREVIEW = { email: 'ivan@example.com', expiresAt: '2026-10-25T09:00:00Z' };
const ME = { status: 'ACTIVE', user: { id: 'u1' } };

describe('InvitePage', () => {
  const api = { invoke: vi.fn() };
  const auth = {
    isSignedIn: vi.fn(),
    accountIdentity: vi.fn(),
    signIn: vi.fn(),
    signOut: vi.fn(),
    startupError: { set: vi.fn() },
  };
  const currentUser = { me: { set: vi.fn() } };
  const accessChannel = { notifyChanged: vi.fn() };
  let session: InvitationSession;

  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    api.invoke.mockImplementation(async (fn) => {
      if (fn === previewInvitation) return PREVIEW;
      if (fn === acceptInvitation) return ME;
      throw new Error('unexpected');
    });
    auth.isSignedIn.mockResolvedValue(false);
    auth.accountIdentity.mockResolvedValue({ email: 'ivan@example.com' });
    auth.signIn.mockResolvedValue(true);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', component: Home },
          { path: 'invite/:token', component: InvitePage },
          { path: 'invite', component: InvitePage },
        ]),
        { provide: Api, useValue: api },
        { provide: AuthService, useValue: auth },
        { provide: CurrentUserService, useValue: currentUser },
        { provide: AccessChannel, useValue: accessChannel },
      ],
    });
    session = TestBed.inject(InvitationSession);
  });

  async function open(url = '/invite/tok123') {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    await settle(harness);
    return { harness, element: harness.routeNativeElement as HTMLElement };
  }

  async function settle(harness: RouterTestingHarness) {
    await harness.fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 10));
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  const button = (element: HTMLElement, text: string) =>
    [...element.querySelectorAll('button')].find((b) => b.textContent?.trim() === text);

  it('shows a loading state while the invitation is checked (AC-21)', async () => {
    let release!: (value: unknown) => void;
    api.invoke.mockReturnValueOnce(new Promise((resolve) => (release = resolve)));
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/invite/tok123');
    harness.detectChanges();
    const element = harness.routeNativeElement as HTMLElement;
    expect(element.querySelector('[role=status]')?.textContent).toContain('Loading');
    release(PREVIEW);
    await settle(harness);
    expect(element.querySelector('h1')?.textContent).toBe("You're invited to MentorHub");
  });

  it('moves the token to session storage and removes it from the address and router (AC-21)', async () => {
    await open('/invite/tok123');
    expect(session.token()).toBe('tok123');
    expect(TestBed.inject(Router).url).toBe('/invite');
    expect(api.invoke).toHaveBeenCalledTimes(1);
    expect(api.invoke).toHaveBeenCalledWith(previewInvitation, { body: { token: 'tok123' } });
  });

  it('resets the auto-accept flag when a fresh link is opened', async () => {
    auth.isSignedIn.mockResolvedValue(true);
    session.setAutoAccept(true);
    const { element } = await open('/invite/tok123');
    expect(session.autoAccept()).toBe(false);
    expect(api.invoke).not.toHaveBeenCalledWith(acceptInvitation, expect.anything());
    expect(button(element, 'Accept invitation')).toBeTruthy();
  });

  it('offers sign-in and registration with the invited email for a visitor (AC-21)', async () => {
    const { element } = await open();
    expect(element.querySelectorAll('h1')).toHaveLength(1);
    expect(element.textContent).toContain(
      "This invitation is for ivan@example.com. Sign in with that email, or create an account if you don't have one.",
    );
    expect(document.activeElement).toBe(element.querySelector('h1'));

    button(element, 'Sign in')?.click();
    await vi.waitFor(() =>
      expect(auth.signIn).toHaveBeenCalledWith('/invite', {
        loginHint: 'ivan@example.com',
        register: false,
      }),
    );
    expect(session.autoAccept()).toBe(true);

    button(element, 'Create an account')?.click();
    await vi.waitFor(() =>
      expect(auth.signIn).toHaveBeenLastCalledWith('/invite', {
        loginHint: 'ivan@example.com',
        register: true,
      }),
    );
  });

  it('shows the startup error when sign-in cannot start', async () => {
    auth.signIn.mockResolvedValue(false);
    const { element } = await open();
    button(element, 'Sign in')?.click();
    await vi.waitFor(() => expect(auth.startupError.set).toHaveBeenCalledWith(true));
    expect(session.autoAccept()).toBe(false);
  });

  it.each([
    ['invitation-invalid', 404],
    ['validation-failed', 400],
  ])('shows the invalid link message on %s and forgets the token (AC-21)', async (type, status) => {
    api.invoke.mockRejectedValue(problem(status, `urn:mentorhub:problem:${type}`));
    const { element } = await open();
    expect(element.querySelector('h1')?.textContent).toBe('Invitation not valid');
    expect(element.querySelector('[role=alert]')?.textContent).toContain(
      'This invitation link is invalid or has expired. Ask the person who invited you for a new link.',
    );
    expect(session.token()).toBeNull();
    expect(element.querySelector('button')).toBeNull();
  });

  it('shows the invalid message when there is no token at all', async () => {
    const { element } = await open('/invite');
    expect(element.querySelector('h1')?.textContent).toBe('Invitation not valid');
    expect(api.invoke).not.toHaveBeenCalled();
  });

  it('uses the token kept in session storage after the round trip (AC-21)', async () => {
    session.setToken('kept');
    await open('/invite');
    expect(api.invoke).toHaveBeenCalledWith(previewInvitation, { body: { token: 'kept' } });
  });

  it('shows the account and accepts on request when already signed in (AC-21)', async () => {
    auth.isSignedIn.mockResolvedValue(true);
    auth.accountIdentity.mockResolvedValue({ email: 'me@example.com' });
    const { harness, element } = await open();
    expect(element.textContent).toContain("You're signed in as me@example.com.");
    expect(api.invoke).not.toHaveBeenCalledWith(acceptInvitation, expect.anything());
    button(element, 'Accept invitation')?.click();
    await settle(harness);
    expect(api.invoke).toHaveBeenCalledWith(acceptInvitation, { body: { token: 'tok123' } });
    expect(currentUser.me.set).toHaveBeenCalledWith(ME);
    expect(accessChannel.notifyChanged).toHaveBeenCalledOnce();
    expect(TestBed.inject(Router).url).toBe('/');
    expect(session.token()).toBeNull();
  });

  it('signs out for another account and keeps the token (AC-21)', async () => {
    auth.isSignedIn.mockResolvedValue(true);
    const { element } = await open();
    button(element, 'Use another account')?.click();
    expect(auth.signOut).toHaveBeenCalledOnce();
    expect(session.token()).toBe('tok123');
  });

  it('accepts without another click after returning from the identity provider (AC-21)', async () => {
    auth.isSignedIn.mockResolvedValue(true);
    session.setToken('tok123');
    session.setAutoAccept(true);
    await open('/invite');
    expect(api.invoke).toHaveBeenCalledWith(acceptInvitation, { body: { token: 'tok123' } });
    expect(TestBed.inject(Router).url).toBe('/');
    expect(session.autoAccept()).toBe(false);
  });

  it('shows a progress state while accepting (AC-21)', async () => {
    auth.isSignedIn.mockResolvedValue(true);
    const { harness, element } = await open();
    let release!: (value: unknown) => void;
    api.invoke.mockReturnValueOnce(new Promise((resolve) => (release = resolve)));
    button(element, 'Accept invitation')?.click();
    harness.detectChanges();
    expect(element.querySelector('mat-card')?.getAttribute('aria-busy')).toBe('true');
    expect(element.querySelector('[role=status]')?.textContent).toContain('Accepting');
    release(ME);
    await settle(harness);
  });

  describe('refused acceptance', () => {
    async function acceptWith(error: unknown) {
      auth.isSignedIn.mockResolvedValue(true);
      const { harness, element } = await open();
      api.invoke.mockRejectedValueOnce(error);
      button(element, 'Accept invitation')?.click();
      await settle(harness);
      return { harness, element };
    }

    it('shows the other account on a mismatch (AC-21)', async () => {
      auth.accountIdentity.mockResolvedValue({ email: 'other@example.com' });
      const { element } = await acceptWith(
        problem(409, 'urn:mentorhub:problem:invitation-email-mismatch'),
      );
      expect(element.querySelector('h1')?.textContent).toBe('Wrong account');
      expect(element.querySelector('[role=alert]')?.textContent).toContain(
        "This invitation is for ivan@example.com, but you're signed in as other@example.com.",
      );
      expect(button(element, 'Use another account')).toBeTruthy();
      expect(document.activeElement).toBe(element.querySelector('h1'));
    });

    it('asks to verify the email when the emails are equal ignoring case (AC-21)', async () => {
      auth.accountIdentity.mockResolvedValue({ email: 'Ivan@Example.com' });
      const { element } = await acceptWith(
        problem(409, 'urn:mentorhub:problem:invitation-email-mismatch'),
      );
      expect(element.querySelector('[role=alert]')?.textContent).toContain(
        'Verify your email in your account first, then try again.',
      );
    });

    it('explains an account that belongs to another user and signs out (AC-21)', async () => {
      const { element } = await acceptWith(
        problem(409, 'urn:mentorhub:problem:identity-linked-elsewhere'),
      );
      expect(element.querySelector('h1')?.textContent).toBe('Account already in use');
      expect(element.querySelector('[role=alert]')?.textContent).toContain(
        'This account already belongs to another MentorHub user. Contact the person who invited you.',
      );
      button(element, 'Sign out')?.click();
      expect(auth.signOut).toHaveBeenCalledOnce();
      expect(session.token()).toBeNull();
    });

    it('shows the invalid message when the link stopped working (AC-21)', async () => {
      const { element } = await acceptWith(
        problem(404, 'urn:mentorhub:problem:invitation-invalid'),
      );
      expect(element.querySelector('h1')?.textContent).toBe('Invitation not valid');
      expect(session.token()).toBeNull();
    });

    it('shows the unavailable card and retries the acceptance (AC-21)', async () => {
      const { harness, element } = await acceptWith(problem(500, 'about:blank'));
      expect(element.querySelector('[role=alert]')?.textContent).toContain(
        'MentorHub is unavailable',
      );
      button(element, 'Retry')?.click();
      await settle(harness);
      expect(api.invoke.mock.calls.filter(([fn]) => fn === acceptInvitation)).toHaveLength(2);
      expect(TestBed.inject(Router).url).toBe('/');
    });
  });

  it('shows the unavailable card when the preview fails and retries it (AC-21)', async () => {
    api.invoke.mockRejectedValueOnce(problem(500, 'about:blank'));
    const { harness, element } = await open();
    expect(element.querySelector('[role=alert]')?.textContent).toContain(
      'MentorHub is unavailable',
    );
    expect(session.token()).toBe('tok123');
    button(element, 'Retry')?.click();
    await settle(harness);
    expect(element.querySelector('h1')?.textContent).toBe("You're invited to MentorHub");
  });
});
