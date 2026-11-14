import { Dialog } from '@angular/cdk/dialog';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { ClientConfigService } from '../../core/client-config.service';
import { createInvitation } from '../../core/api/fn/invitations/create-invitation';
import { InvitationConflictProblem } from '../../core/api/models/invitation-conflict-problem';
import { ValidationProblem } from '../../core/api/models/validation-problem';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { problemError } from '../../core/testing/problem-error';
import { InviteDialog } from './invite-dialog';
import { created } from './test-data';

const problem = (status: number, type: string) => problemError({ status, type, title: 'Error' });

const conflictProblem = (
  body: Pick<InvitationConflictProblem, 'type' | 'userId' | 'invitationId'>,
) => problemError({ status: 409, title: 'Conflict', ...body });

const validationProblem = (errors: ValidationProblem['errors']) =>
  problemError({
    status: 400,
    title: 'Validation Failed',
    type: 'urn:mentorhub:problem:validation-failed',
    errors,
  });

describe('InviteDialog', () => {
  const api = createApiStub();
  let ref: BrnDialogRef;

  beforeEach(() => {
    api.reset();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideApiStub(api)],
    });
  });

  afterEach(() => TestBed.inject(Dialog).closeAll());

  async function open(data?: object) {
    ref = TestBed.inject(HlmDialogService).open(InviteDialog, { context: data });
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

  const checkboxLabels = (root: HTMLElement) =>
    [...root.querySelectorAll('label')].filter((l) => l.querySelector('hlm-checkbox'));

  const checkbox = (root: HTMLElement, label: string) =>
    checkboxLabels(root)
      .find((c) => c.textContent?.trim() === label)
      ?.querySelector('button[role="checkbox"]') as HTMLButtonElement;

  it('shows the fields, the roles and the hint', async () => {
    const { root } = await open();
    expect(root.textContent).toContain('Invite a person');
    expect(root.querySelector('label[hlmLabel]')?.textContent).toBe('Email');
    const labels = checkboxLabels(root).map((c) => c.textContent?.trim());
    expect(labels).toEqual(['Administrator', 'Coordinator', 'Mentor', 'Student', 'Observer']);
    expect(
      root.querySelectorAll('hlm-checkbox button[role="checkbox"][aria-checked="true"]'),
    ).toHaveLength(0);
    expect(root.textContent).toContain(
      'Without roles the person can sign in but has no access to anything.',
    );
    const buttons = [...root.querySelectorAll('button:not([role="checkbox"])')].map((b) =>
      b.textContent?.trim(),
    );
    expect(buttons).toEqual(['Cancel', 'Create invitation']);
  });

  it.each([
    ['', 'Enter an email'],
    ['   ', 'Enter an email'],
    ['not-an-email', 'Enter a valid email address'],
    [`${'a'.repeat(250)}@example.com`, 'Use at most 254 characters'],
  ])('validates the email %j on the client', async (value, message) => {
    const { root } = await open();
    type(root, value);
    await submitForm(root);
    expect(root.querySelector('.field-error')?.textContent).toContain(message);
    expect(api.allCalls()).toHaveLength(0);
  });

  it('creates the invitation with the trimmed email and the chosen roles, then shows the link', async () => {
    api.resolve(createInvitation, created({ email: 'ivan@example.com' }));
    const { root } = await open();
    type(root, '  ivan@example.com ');
    checkbox(root, 'Student').click();
    checkbox(root, 'Mentor').click();
    await submitForm(root);
    expect(api.callsFor(createInvitation)).toEqual([
      { body: { email: 'ivan@example.com', roles: ['MENTOR', 'STUDENT'] } },
    ]);
    expect(root.textContent).toContain('Invitation link');
    expect(root.textContent).toContain(
      'Send this link to ivan@example.com. It expires on 25 Oct 2026. You can copy it again from the invitations list while it is pending.',
    );
    expect((root.querySelector('input[readonly]') as HTMLInputElement).value).toBe(
      'http://localhost:4200/invite/Xf3abc',
    );
    expect(root.querySelector('button[aria-label="Copy invitation link"]')?.textContent).toContain(
      'Copy link',
    );
    expect(root.querySelector('label[hlmLabel]')?.textContent).toBe('Invitation link');
    expect(root.textContent).not.toContain('once');
  });

  it('opens the link view directly for an issued link', async () => {
    const { root } = await open({ created: created() });
    expect(root.textContent).toContain('Invitation link');
    expect(root.querySelector('input[type=email]')).toBeNull();
  });

  it('copies the link and confirms with a snackbar', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    const { root } = await open({ created: created() });
    (root.querySelector('button[aria-label="Copy invitation link"]') as HTMLElement).click();
    await stable();
    expect(writeText).toHaveBeenCalledWith('http://localhost:4200/invite/Xf3abc');
    expect(document.body.textContent).toContain('Link copied');
    vi.unstubAllGlobals();
  });

  it('selects the link when the clipboard is not available', async () => {
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
    ref.closed$.subscribe(closed);
    [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Done')?.click();
    await vi.waitFor(() => expect(closed).toHaveBeenCalled());
  });

  it('INV-19.1 shows the active-user conflict with a link to the user', async () => {
    api.reject(
      createInvitation,
      conflictProblem({ type: 'urn:mentorhub:problem:user-active', userId: 'u7' }),
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
  });

  it('INV-19.3 keeps the entered values after a conflict', async () => {
    api.reject(
      createInvitation,
      conflictProblem({ type: 'urn:mentorhub:problem:user-active', userId: 'u7' }),
    );
    const { root } = await open();
    type(root, 'ivan@example.com');
    checkbox(root, 'Mentor').click();
    await submitForm(root);
    expect((root.querySelector('input[type=email]') as HTMLInputElement).value).toBe(
      'ivan@example.com',
    );
    expect(checkbox(root, 'Mentor').getAttribute('aria-checked')).toBe('true');
  });

  it('INV-19.2 shows the existing-invitation conflict with a link to the list', async () => {
    api.reject(
      createInvitation,
      conflictProblem({ type: 'urn:mentorhub:problem:invitation-exists', invitationId: 'i7' }),
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

  it('shows server field errors under their fields', async () => {
    api.reject(
      createInvitation,
      validationProblem([{ field: 'email', message: 'must be a well-formed email address' }]),
    );
    const { root } = await open();
    type(root, 'ivan@example.com');
    await submitForm(root);
    expect(root.querySelector('.field-error')?.textContent).toContain(
      'must be a well-formed email address',
    );
    expect(root.querySelector('.error[role=alert]')).toBeNull();
  });

  it('shows a role error under the roles', async () => {
    api.reject(createInvitation, validationProblem([{ field: 'roles', message: 'unknown role' }]));
    const { root } = await open();
    type(root, 'ivan@example.com');
    await submitForm(root);
    expect(root.querySelector('fieldset .error[role=alert]')?.textContent).toContain(
      'unknown role',
    );
  });

  it('shows a generic inline alert on other errors and keeps the form', async () => {
    api.reject(createInvitation, problem(500, 'about:blank'));
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

  describe('with email enabled', () => {
    beforeEach(() =>
      TestBed.overrideProvider(ClientConfigService, { useValue: { invitationEmails: () => true } }),
    );

    const emailBox = (root: HTMLElement) =>
      checkbox(root, 'Send the invitation by email to this address');

    it('shows the checkbox, checked by default, below the roles', async () => {
      const { root } = await open();
      expect(emailBox(root).getAttribute('aria-checked')).toBe('true');
      const labels = checkboxLabels(root).map((c) => c.textContent?.trim());
      expect(labels.at(-1)).toBe('Send the invitation by email to this address');
    });

    it('requests the email and tells the link view so when the choice is on', async () => {
      api.resolve(createInvitation, created());
      const { root } = await open();
      type(root, 'ivan@example.com');
      await submitForm(root);
      expect(api.callsFor(createInvitation)).toEqual([
        { body: { email: 'ivan@example.com', roles: [], sendEmail: true } },
      ]);
      expect(root.textContent).toContain(
        'An email with this link is on its way to ivan@example.com. It expires on 25 Oct 2026. You can also copy the link, and copy it again from the invitations list while it is pending.',
      );
      expect(root.querySelector('button[aria-label="Copy invitation link"]')).toBeTruthy();
    });

    it('sends sendEmail false and shows the usual text when the choice is off', async () => {
      api.resolve(createInvitation, created());
      const { root } = await open();
      type(root, 'ivan@example.com');
      emailBox(root).click();
      await submitForm(root);
      expect(api.callsFor(createInvitation)).toEqual([
        { body: { email: 'ivan@example.com', roles: [], sendEmail: false } },
      ]);
      expect(root.textContent).toContain('Send this link to ivan@example.com.');
    });

    it('IEM-11.1 shows an inline alert on 409 email-unavailable and keeps the values', async () => {
      api.reject(createInvitation, problem(409, 'urn:mentorhub:problem:email-unavailable'));
      const { root } = await open();
      type(root, 'ivan@example.com');
      checkbox(root, 'Mentor').click();
      await submitForm(root);
      expect(root.querySelector('.error[role=alert]')?.textContent).toContain(
        'Sending email is not available. Reload the page and try again.',
      );
      expect((root.querySelector('input[type=email]') as HTMLInputElement).value).toBe(
        'ivan@example.com',
      );
      expect(checkbox(root, 'Mentor').getAttribute('aria-checked')).toBe('true');
      expect(root.querySelector('app-link-view')).toBeNull();
    });
  });

  it('renders no email checkbox and sends no flag when email is disabled', async () => {
    api.resolve(createInvitation, created());
    const { root } = await open();
    expect(root.textContent).not.toContain('by email');
    type(root, 'ivan@example.com');
    await submitForm(root);
    expect(api.callsFor(createInvitation)[0]).toEqual({
      body: { email: 'ivan@example.com', roles: [] },
    });
    expect(Object.keys(api.callsFor(createInvitation)[0].body)).not.toContain('sendEmail');
  });
});
