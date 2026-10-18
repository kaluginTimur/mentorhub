import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Api } from '../../core/api/api';
import { createInvitation } from '../../core/api/fn/invitations/create-invitation';
import { InviteDialog } from './invite-dialog';
import { created } from './test-data';

const problem = (status: number, body: object) =>
  new HttpErrorResponse({ status, error: { status, ...body } });

describe('InviteDialog', () => {
  const api = { invoke: vi.fn() };
  let ref: MatDialogRef<InviteDialog>;

  beforeEach(() => {
    api.invoke.mockReset();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: Api, useValue: api }],
    });
  });

  afterEach(() => TestBed.inject(MatDialog).closeAll());

  async function open(data?: object) {
    ref = TestBed.inject(MatDialog).open(InviteDialog, { data });
    await new Promise((resolve) => setTimeout(resolve));
    const root = document.querySelector('app-invite-dialog') as HTMLElement;
    return { root };
  }

  const stable = async () => {
    await new Promise((resolve) => setTimeout(resolve, 20));
  };

  function type(root: HTMLElement, value: string) {
    const input = root.querySelector('input[type=email]') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  const submitForm = async (root: HTMLElement) => {
    (root.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit', { cancelable: true }),
    );
    await stable();
  };

  const checkbox = (root: HTMLElement, label: string) =>
    [...root.querySelectorAll('mat-checkbox')]
      .find((c) => c.textContent?.trim() === label)
      ?.querySelector('input') as HTMLInputElement;

  it('shows the fields, the roles and the hint (AC-19)', async () => {
    const { root } = await open();
    expect(root.textContent).toContain('Invite a person');
    expect(root.querySelector('mat-label')?.textContent).toBe('Email');
    const labels = [...root.querySelectorAll('mat-checkbox')].map((c) => c.textContent?.trim());
    expect(labels).toEqual(['Administrator', 'Coordinator', 'Mentor', 'Student', 'Observer']);
    expect(root.querySelectorAll('mat-checkbox input:checked')).toHaveLength(0);
    expect(root.textContent).toContain(
      'Without roles the person can sign in but has no access to anything.',
    );
    const buttons = [...root.querySelectorAll('button')].map((b) => b.textContent?.trim());
    expect(buttons).toEqual(['Cancel', 'Create invitation']);
  });

  it.each([
    ['', 'Enter an email'],
    ['   ', 'Enter an email'],
    ['not-an-email', 'Enter a valid email address'],
    [`${'a'.repeat(250)}@example.com`, 'Use at most 254 characters'],
  ])('validates the email %j on the client (AC-19)', async (value, message) => {
    const { root } = await open();
    type(root, value);
    await submitForm(root);
    expect(root.querySelector('mat-error')?.textContent).toContain(message);
    expect(api.invoke).not.toHaveBeenCalled();
  });

  it('creates the invitation with the trimmed email and the chosen roles, then shows the link once (AC-19)', async () => {
    api.invoke.mockResolvedValue(created({ email: 'ivan@example.com' }));
    const { root } = await open();
    type(root, '  ivan@example.com ');
    checkbox(root, 'Student').click();
    checkbox(root, 'Mentor').click();
    await submitForm(root);
    expect(api.invoke).toHaveBeenCalledWith(createInvitation, {
      body: { email: 'ivan@example.com', roles: ['MENTOR', 'STUDENT'] },
    });
    expect(root.textContent).toContain('Invitation link');
    expect(root.textContent).toContain(
      'Send this link to ivan@example.com. It is shown only once and expires on 25 Oct 2026.',
    );
    expect((root.querySelector('input[readonly]') as HTMLInputElement).value).toBe(
      'http://localhost:4200/invite/Xf3abc',
    );
    expect(root.querySelector('button[aria-label="Copy invitation link"]')?.textContent).toContain(
      'Copy link',
    );
    expect(root.querySelector('mat-label')?.textContent).toBe('Invitation link');
  });

  it('opens the link view directly for an issued link (AC-19)', async () => {
    const { root } = await open({ created: created() });
    expect(root.textContent).toContain('Invitation link');
    expect(root.querySelector('input[type=email]')).toBeNull();
  });

  it('copies the link and confirms with a snackbar (AC-19)', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    const { root } = await open({ created: created() });
    (root.querySelector('button[aria-label="Copy invitation link"]') as HTMLElement).click();
    await stable();
    expect(writeText).toHaveBeenCalledWith('http://localhost:4200/invite/Xf3abc');
    expect(document.body.textContent).toContain('Link copied');
    vi.unstubAllGlobals();
  });

  it('selects the link when the clipboard is not available (AC-19)', async () => {
    vi.stubGlobal('navigator', {});
    const { root } = await open({ created: created() });
    const field = root.querySelector('input[readonly]') as HTMLInputElement;
    const select = vi.spyOn(field, 'select');
    (root.querySelector('button[aria-label="Copy invitation link"]') as HTMLElement).click();
    await stable();
    expect(select).toHaveBeenCalled();
    expect(document.body.textContent).not.toContain('Link copied');
    vi.unstubAllGlobals();
  });

  it('closes from the link view with Done', async () => {
    const { root } = await open({ created: created() });
    const closed = vi.fn();
    ref.afterClosed().subscribe(closed);
    [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Done')?.click();
    await vi.waitFor(() => expect(closed).toHaveBeenCalled());
  });

  it('shows the active-user conflict with a link to the user and keeps the values (AC-19)', async () => {
    api.invoke.mockRejectedValue(
      problem(409, { type: 'urn:mentorhub:problem:user-active', userId: 'u7' }),
    );
    const { root } = await open();
    type(root, 'ivan@example.com');
    checkbox(root, 'Mentor').click();
    await submitForm(root);
    const alert = root.querySelector('.conflict[role=alert]') as HTMLElement;
    expect(alert.textContent).toContain('This person already has access.');
    const link = alert.querySelector('a') as HTMLAnchorElement;
    expect(link.textContent?.trim()).toBe('Open user');
    expect(link.getAttribute('href')).toBe('/users/u7');
    expect((root.querySelector('input[type=email]') as HTMLInputElement).value).toBe(
      'ivan@example.com',
    );
    expect(checkbox(root, 'Mentor').checked).toBe(true);
  });

  it('shows the existing-invitation conflict with a link to the list (AC-19)', async () => {
    api.invoke.mockRejectedValue(
      problem(409, { type: 'urn:mentorhub:problem:invitation-exists', invitationId: 'i7' }),
    );
    const { root } = await open();
    type(root, ' ivan@example.com ');
    await submitForm(root);
    const alert = root.querySelector('.conflict[role=alert]') as HTMLElement;
    expect(alert.textContent).toContain('There is already an invitation for this email.');
    const link = alert.querySelector('a') as HTMLAnchorElement;
    expect(link.textContent?.trim()).toBe('Show invitation');
    expect(link.getAttribute('href')).toBe('/invitations?q=ivan@example.com');
  });

  it('shows server field errors under their fields (AC-19)', async () => {
    api.invoke.mockRejectedValue(
      problem(400, {
        type: 'urn:mentorhub:problem:validation-failed',
        errors: [{ field: 'email', message: 'must be a well-formed email address' }],
      }),
    );
    const { root } = await open();
    type(root, 'ivan@example.com');
    await submitForm(root);
    expect(root.querySelector('mat-error')?.textContent).toContain(
      'must be a well-formed email address',
    );
    expect(root.querySelector('.error[role=alert]')).toBeNull();
  });

  it('shows a role error under the roles (AC-19)', async () => {
    api.invoke.mockRejectedValue(
      problem(400, {
        type: 'urn:mentorhub:problem:validation-failed',
        errors: [{ field: 'roles', message: 'unknown role' }],
      }),
    );
    const { root } = await open();
    type(root, 'ivan@example.com');
    await submitForm(root);
    expect(root.querySelector('fieldset .error[role=alert]')?.textContent).toContain(
      'unknown role',
    );
  });

  it('shows a generic inline alert on other errors and keeps the form (AC-19)', async () => {
    api.invoke.mockRejectedValue(problem(500, { type: 'about:blank' }));
    const { root } = await open();
    type(root, 'ivan@example.com');
    await submitForm(root);
    expect(root.querySelector('.error[role=alert]')?.textContent).toContain(
      'Something went wrong. Try again.',
    );
    expect((root.querySelector('input[type=email]') as HTMLInputElement).value).toBe(
      'ivan@example.com',
    );
  });
});
