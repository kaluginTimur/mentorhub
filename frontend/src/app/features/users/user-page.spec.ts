import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { disableUser } from '../../core/api/fn/users/disable-user';
import { enableUser } from '../../core/api/fn/users/enable-user';
import { getUserHistory } from '../../core/api/fn/users/get-user-history';
import { getUser } from '../../core/api/fn/users/get-user';
import { listUsers } from '../../core/api/fn/users/list-users';
import { setUserRoles } from '../../core/api/fn/users/set-user-roles';
import { updateUserProfile } from '../../core/api/fn/users/update-user-profile';
import { HistoryAction } from '../../core/api/models/history-action';
import { HistoryEntry } from '../../core/api/models/history-entry';
import { HistoryPage } from '../../core/api/models/history-page';
import { Me } from '../../core/api/models/me';
import { MeUser } from '../../core/api/models/me-user';
import { Permission } from '../../core/api/models/permission';
import { UserPage as ApiUserPage } from '../../core/api/models/user-page';
import { UserSummary } from '../../core/api/models/user-summary';
import { ValidationError } from '../../core/api/models/validation-error';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { problemError } from '../../core/testing/problem-error';
import { ADMIN_ID, USER_ID, meUser, userDetails } from './test-data';
import { UserPage } from './user-page';
import { UsersList } from './users-list';

const problem = (status: number, type: string) => problemError({ type, status, title: 'Error' });
const validationProblem = (errors: ValidationError[]) =>
  problemError({
    type: 'urn:mentorhub:problem:validation-failed',
    status: 400,
    title: 'Validation Failed',
    errors,
  });
const historyPage = (items: HistoryEntry[], totalItems = items.length): HistoryPage => ({
  items,
  page: 0,
  size: 25,
  totalItems,
});
const userPage = (items: UserSummary[], totalItems = items.length): ApiUserPage => ({
  items,
  page: 0,
  size: 25,
  totalItems,
});
const historyEntry = (action: HistoryAction, extra: Partial<HistoryEntry> = {}): HistoryEntry => ({
  id: 'new',
  action,
  occurredAt: '2026-10-18T10:00:00Z',
  actor: null,
  ...extra,
});
const click = (el: Element | null | undefined) => (el as HTMLElement).click();
const buttonByText = (root: ParentNode, text: string) =>
  [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === text);

describe('UserPage', () => {
  const api = createApiStub();
  const me = signal<MeUser | null>(meUser());
  const currentUser = {
    user: me,
    load: vi.fn(),
    hasPermission: (p: Permission) => me()?.permissions.includes(p) ?? false,
  };

  beforeEach(() => {
    api.reset();
    currentUser.load
      .mockReset()
      .mockResolvedValue({ status: 'ACTIVE', user: meUser() } satisfies Me);
    me.set(meUser());
    api.resolve(getUser, userDetails());
    api.resolve(getUserHistory, historyPage([]));
    api.resolve(listUsers, userPage([]));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'users/:id', component: UserPage },
          { path: 'users', component: UsersList },
          { path: '', children: [] },
        ]),
        provideApiStub(api),
        { provide: CurrentUserService, useValue: currentUser },
      ],
    });
  });

  async function open(id = USER_ID) {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/users/${id}`);
    await harness.fixture.whenStable();
    harness.detectChanges();
    const element = harness.routeNativeElement as HTMLElement;
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
      await harness.fixture.whenStable();
    };
    return { harness, element, settle };
  }

  // The first history load is empty; every later one returns the entry the change created.
  const historyAfterChange = (entry: HistoryEntry) => {
    let loads = 0;
    api.handle(getUserHistory, () => historyPage(loads++ === 0 ? [] : [entry]));
  };

  const overlay = () => document.querySelector('.cdk-overlay-container') as HTMLElement;
  const snack = () => document.querySelector('app-toast-host')?.textContent ?? '';

  afterEach(() => {
    document.querySelector('.cdk-overlay-container')?.replaceChildren();
  });

  it('shows the profile, roles and history', async () => {
    api.resolve(
      getUserHistory,
      historyPage([
        {
          id: 'h1',
          action: 'USER_ROLES_CHANGED',
          occurredAt: '2026-10-17T10:12:00Z',
          actor: { id: ADMIN_ID, name: 'Anna Petrova' },
          rolesAdded: ['MENTOR'],
        },
        { id: 'h2', action: 'USER_ENABLED', occurredAt: '2026-10-12T09:00:00Z', actor: null },
      ]),
    );
    const { element } = await open();
    expect(element.querySelectorAll('h1')).toHaveLength(1);
    expect(element.querySelector('h1')?.textContent).toBe('Ivan Sidorov');
    expect(element.textContent).toContain('ivan@example.com');
    expect(element.textContent).toContain('+77011234567');
    expect(element.textContent).toContain('12 Oct 2026');
    expect(element.querySelector('[hlmBadge]')?.textContent).toContain('Active');
    expect(element.textContent).toContain('Roles changed: added Mentor');
    expect(element.textContent).toContain('Anna Petrova');
    expect(element.textContent).toContain('System');
    expect(element.querySelectorAll('h2').length).toBe(3);
    expect(element.querySelector('.back')?.getAttribute('href')).toBe('/users');
  });

  it.each([
    ['unknown', USER_ID, 404, 'urn:mentorhub:problem:not-found'],
    ['invalid', 'abc', 400, 'urn:mentorhub:problem:validation-failed'],
  ])('shows the not found state for an %s id', async (_name, id, status, type) => {
    api.reject(getUser, problem(status, type));
    const { element } = await open(id);
    expect(element.querySelector('h1')?.textContent).toBe('User not found');
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/users');
  });

  it('shows the unavailable card and retries', async () => {
    api.rejectOnce(getUser, new Error('down'));
    const { element, settle } = await open();
    expect(element.querySelector('[role=alert]')?.textContent).toContain('unavailable');
    click(element.querySelector('button'));
    await settle();
    expect(element.querySelector('h1')?.textContent).toBe('Ivan Sidorov');
  });

  it('loads more history on "Show more"', async () => {
    const entry = (id: string): HistoryEntry => ({
      id,
      action: 'USER_ROLES_CHANGED',
      occurredAt: '2026-10-17T10:12:00Z',
      actor: null,
    });
    api.handle(getUserHistory, (p) =>
      p.page === 1 ? historyPage([entry('h3')], 3) : historyPage([entry('h1'), entry('h2')], 3),
    );
    const { element, settle } = await open();
    expect(element.querySelectorAll('.history li')).toHaveLength(2);
    expect(element.querySelector('.history li')?.textContent).toContain('Roles changed');
    click(buttonByText(element, 'Show more'));
    await settle();
    expect(element.querySelectorAll('.history li')).toHaveLength(3);
    expect(buttonByText(element, 'Show more')).toBeUndefined();
  });

  it('shows an error in the history card and retries it', async () => {
    api.rejectOnce(getUserHistory, new Error('down'));
    const { element, settle } = await open();
    expect(element.textContent).toContain('Could not load the history.');
    expect(element.querySelector('h1')?.textContent).toBe('Ivan Sidorov');
    api.resolve(
      getUserHistory,
      historyPage([
        { id: 'h1', action: 'USER_ENABLED', occurredAt: '2026-10-17T10:12:00Z', actor: null },
      ]),
    );
    click(buttonByText(element, 'Retry'));
    await settle();
    expect(element.querySelectorAll('.history li')).toHaveLength(1);
  });

  describe('disabling', () => {
    async function openDialog() {
      const page = await open();
      click(buttonByText(page.element, 'Disable user'));
      await page.settle();
      return page;
    }

    it('requires a reason and a comment for "Other", then disables', async () => {
      historyAfterChange(historyEntry('USER_DISABLED', { reason: 'OTHER', comment: 'Left' }));
      api.resolve(
        disableUser,
        userDetails({ status: 'DISABLED', disabledReason: 'OTHER', disabledComment: 'Left' }),
      );
      const { element, settle } = await openDialog();
      expect(overlay().textContent).toContain(`Disable Ivan Sidorov?`);
      expect(overlay().textContent).toContain('They lose access on their next action.');
      expect(overlay().textContent).toContain('Comment');
      expect(overlay().textContent).not.toContain('Comment (required)');

      click(buttonByText(overlay(), 'Disable'));
      await settle();
      expect(api.callsFor(disableUser)).toHaveLength(0);
      expect(overlay().textContent).toContain('Choose a reason');
      expect(document.querySelectorAll('hlm-select-item')).toHaveLength(0);

      click(overlay().querySelector('hlm-select-trigger button'));
      await settle();
      const options = [...document.querySelectorAll('hlm-select-item')];
      expect(options.map((o) => o.textContent?.trim())).toEqual([
        'Graduated',
        'Expelled',
        'Left the program',
        'No longer on staff',
        'Security concern',
        'Other',
      ]);
      click(options[5]);
      await settle();
      expect(overlay().textContent).toContain('Comment (required)');
      expect(overlay().querySelector('textarea')?.required).toBe(true);
      click(buttonByText(overlay(), 'Disable'));
      await settle();
      expect(overlay().textContent).toContain('Enter a comment');
      expect(api.callsFor(disableUser)).toHaveLength(0);

      const textarea = overlay().querySelector('textarea') as HTMLTextAreaElement;
      textarea.value = '  Left  ';
      textarea.dispatchEvent(new Event('input'));
      await settle();
      click(buttonByText(overlay(), 'Disable'));
      await settle();
      expect(api.callsFor(disableUser)).toEqual([
        { id: USER_ID, body: { reason: 'OTHER', comment: 'Left' } },
      ]);
      await vi.waitFor(() => expect(snack()).toContain('User disabled'));
      await settle();
      expect(element.textContent).toContain('Other — Left');
      expect(element.querySelector('[hlmBadge]')?.textContent).toContain('Disabled');
      expect(buttonByText(element, 'Enable user')).toBeDefined();
      expect(element.querySelector('.history')?.textContent).toContain('Disabled: Other — Left');
    });

    it('shows server field errors under the fields and keeps the dialog open', async () => {
      api.reject(
        disableUser,
        validationProblem([{ field: 'comment', message: 'Comment is too long' }]),
      );
      const { settle } = await openDialog();
      click(overlay().querySelector('hlm-select-trigger button'));
      await settle();
      click(document.querySelector('hlm-select-item'));
      await settle();
      click(buttonByText(overlay(), 'Disable'));
      await settle();
      expect(overlay().textContent).toContain('Comment is too long');
      expect(overlay().querySelector('textarea')?.getAttribute('aria-invalid')).toBe('true');
    });

    it('USR-17.1 shows the 409 message in a snackbar and keeps the dialog open', async () => {
      api.reject(disableUser, problem(409, 'urn:mentorhub:problem:last-admin'));
      const { settle } = await openDialog();
      click(overlay().querySelector('hlm-select-trigger button'));
      await settle();
      click(document.querySelector('hlm-select-item'));
      await settle();
      click(buttonByText(overlay(), 'Disable'));
      await settle();
      expect(snack()).toContain('MentorHub must keep at least one active administrator.');
      expect(overlay().textContent).toContain('Disable Ivan Sidorov?');
      expect(overlay().querySelector('hlm-select-trigger')?.textContent).toContain('Graduated');
    });

    it('counts and limits the comment after trimming', async () => {
      api.resolve(disableUser, userDetails({ status: 'DISABLED', disabledReason: 'GRADUATED' }));
      const { settle } = await openDialog();
      click(overlay().querySelector('hlm-select-trigger button'));
      await settle();
      click(document.querySelector('hlm-select-item'));
      await settle();
      const textarea = overlay().querySelector('textarea') as HTMLTextAreaElement;
      textarea.value = `  ${'x'.repeat(500)}  `;
      textarea.dispatchEvent(new Event('input'));
      await settle();
      expect(overlay().textContent).toContain('500 / 500');
      click(buttonByText(overlay(), 'Disable'));
      await settle();
      expect(overlay().textContent).not.toContain('Use at most 500 characters');
      expect(api.callsFor(disableUser)).toEqual([
        expect.objectContaining({ body: expect.objectContaining({ comment: 'x'.repeat(500) }) }),
      ]);
    });
  });

  it('enables a disabled user after confirmation', async () => {
    api.resolve(
      getUser,
      userDetails({ status: 'DISABLED', disabledReason: 'GRADUATED', disabledComment: 'Done' }),
    );
    api.resolve(enableUser, userDetails());
    historyAfterChange(historyEntry('USER_ENABLED'));
    const { element, settle } = await open();
    expect(element.textContent).toContain('Graduated — Done');
    click(buttonByText(element, 'Enable user'));
    await settle();
    expect(overlay().textContent).toContain('Enable Ivan Sidorov?');
    expect(overlay().textContent).toContain('They will be able to sign in again.');
    expect(api.callsFor(enableUser)).toHaveLength(0);
    click(buttonByText(overlay(), 'Enable'));
    await vi.waitFor(() => expect(api.callsFor(enableUser)).toEqual([{ id: USER_ID }]));
    await vi.waitFor(() => expect(snack()).toContain('User enabled'));
    await settle();
    expect(buttonByText(element, 'Disable user')).toBeDefined();
    expect(element.querySelector('.history')?.textContent).toContain('Enabled');
  });

  it('does not enable when the confirmation is cancelled', async () => {
    api.resolve(getUser, userDetails({ status: 'DISABLED', disabledReason: 'GRADUATED' }));
    const { element, settle } = await open();
    click(buttonByText(element, 'Enable user'));
    await settle();
    click(buttonByText(overlay(), 'Cancel'));
    await settle();
    expect(api.callsFor(enableUser)).toHaveLength(0);
  });

  describe('roles', () => {
    it('saves the selection and shows the hint without roles', async () => {
      api.resolve(setUserRoles, userDetails({ roles: [] }));
      historyAfterChange(historyEntry('USER_ROLES_CHANGED', { rolesRemoved: ['MENTOR'] }));
      const { element, settle } = await open();
      const save = () => buttonByText(element, 'Save') as HTMLButtonElement;
      expect(save().disabled).toBe(true);
      const boxes = [
        ...element.querySelectorAll('hlm-checkbox button[role="checkbox"]'),
      ] as HTMLButtonElement[];
      expect(boxes).toHaveLength(5);
      expect(boxes.map((b) => b.getAttribute('aria-checked') === 'true')).toEqual([
        false,
        false,
        true,
        false,
        false,
      ]);
      click(boxes[2]);
      await settle();
      expect(element.textContent).toContain(
        'Without roles the user can sign in but has no access to anything.',
      );
      expect(save().disabled).toBe(false);
      click(save());
      await settle();
      expect(api.callsFor(setUserRoles)).toEqual([{ id: USER_ID, body: { roles: [] } }]);
      expect(snack()).toContain('Roles saved');
      expect(element.querySelector('.history')?.textContent).toContain(
        'Roles changed: removed Mentor',
      );
    });

    it('resets the selection', async () => {
      const { element, settle } = await open();
      const boxes = [
        ...element.querySelectorAll('hlm-checkbox button[role="checkbox"]'),
      ] as HTMLButtonElement[];
      click(boxes[0]);
      await settle();
      expect(boxes[0].getAttribute('aria-checked')).toBe('true');
      click(buttonByText(element, 'Reset'));
      await settle();
      expect(boxes[0].getAttribute('aria-checked')).toBe('false');
      expect((buttonByText(element, 'Save') as HTMLButtonElement).disabled).toBe(true);
    });

    it('USR-17.1 shows a 409 in a snackbar and keeps the selection', async () => {
      api.reject(setUserRoles, problem(409, 'urn:mentorhub:problem:self-lockout'));
      const { element, settle } = await open();
      const boxes = [
        ...element.querySelectorAll('hlm-checkbox button[role="checkbox"]'),
      ] as HTMLButtonElement[];
      click(boxes[0]);
      await settle();
      click(buttonByText(element, 'Save'));
      await settle();
      expect(snack()).toContain("You can't lock yourself out.");
      expect(boxes[0].getAttribute('aria-checked')).toBe('true');
    });
  });

  describe('profile', () => {
    it('edits, shows server field errors and then the stored values', async () => {
      const { element, settle } = await open();
      click(buttonByText(element, 'Edit'));
      await settle();
      const inputs = [...element.querySelectorAll('app-profile-form input')] as HTMLInputElement[];
      expect(inputs.map((i) => i.value)).toEqual(['Ivan', 'Sidorov', '+77011234567']);
      inputs[2].value = '123';
      inputs[2].dispatchEvent(new Event('input'));
      api.rejectOnce(
        updateUserProfile,
        validationProblem([{ field: 'phone', message: 'bad phone' }]),
      );
      await settle();
      click(buttonByText(element, 'Save'));
      await settle();
      expect(element.textContent).toContain('Enter a valid phone number');
      expect(inputs[2].getAttribute('aria-invalid')).toBe('true');
      expect(inputs[0].value).toBe('Ivan');

      api.resolve(updateUserProfile, userDetails({ phone: '+77017654321' }));
      inputs[2].value = '8 701 765 43 21';
      inputs[2].dispatchEvent(new Event('input'));
      await settle();
      click(buttonByText(element, 'Save'));
      await settle();
      expect(api.callsFor(updateUserProfile).at(-1)).toEqual({
        id: USER_ID,
        body: {
          firstName: 'Ivan',
          lastName: 'Sidorov',
          phone: '8 701 765 43 21',
        },
      });
      expect(snack()).toContain('Profile saved');
      expect(element.querySelector('app-profile-form')).toBeNull();
      expect(element.textContent).toContain('+77017654321');
    });

    it('rejects names over 100 characters before sending', async () => {
      const { element, settle } = await open();
      click(buttonByText(element, 'Edit'));
      await settle();
      const first = element.querySelector('app-profile-form input') as HTMLInputElement;
      first.value = 'x'.repeat(101);
      first.dispatchEvent(new Event('input'));
      await settle();
      click(buttonByText(element, 'Save'));
      await settle();
      expect(element.textContent).toContain('Use at most 100 characters');
      expect(api.callsFor(updateUserProfile)).toHaveLength(0);
    });

    it('cancels editing', async () => {
      const { element, settle } = await open();
      click(buttonByText(element, 'Edit'));
      await settle();
      click(buttonByText(element, 'Cancel'));
      await settle();
      expect(element.querySelector('app-profile-form')).toBeNull();
    });
  });

  describe('own page', () => {
    beforeEach(() => {
      api.resolve(
        getUser,
        userDetails({ id: ADMIN_ID, email: 'anna@example.com', roles: ['ADMIN'] }),
      );
    });

    it('disables "Disable user" and the Administrator checkbox with reachable hints', async () => {
      const { element } = await open(ADMIN_ID);
      const disable = buttonByText(element, 'Disable user') as HTMLButtonElement;
      expect(disable.getAttribute('aria-disabled')).toBe('true');
      expect(disable.disabled).toBe(false);
      const hint = element.querySelector(`#${disable.getAttribute('aria-describedby')}`);
      expect(hint?.textContent).toContain("You can't disable yourself.");
      click(disable);
      expect(document.querySelector('cdk-dialog-container')).toBeNull();

      const admin = element.querySelector(
        'hlm-checkbox button[role="checkbox"]',
      ) as HTMLButtonElement;
      expect(admin.getAttribute('aria-disabled')).toBe('true');
      const adminHint = element.querySelector(`#${admin.getAttribute('aria-describedby')}`);
      expect(adminHint?.textContent).toContain("You can't remove your own administrator role.");
    });

    it('refreshes the current user after saving the profile or roles', async () => {
      api.resolve(
        updateUserProfile,
        userDetails({ id: ADMIN_ID, firstName: 'Anya', roles: ['ADMIN'] }),
      );
      api.resolve(setUserRoles, userDetails({ id: ADMIN_ID, roles: ['ADMIN', 'MENTOR'] }));
      const { element, settle } = await open(ADMIN_ID);
      click(buttonByText(element, 'Edit'));
      await settle();
      click(buttonByText(element, 'Save'));
      await settle();
      expect(currentUser.load).toHaveBeenCalledTimes(1);

      const boxes = [
        ...element.querySelectorAll('hlm-checkbox button[role="checkbox"]'),
      ] as HTMLButtonElement[];
      click(boxes[2]);
      await settle();
      click(buttonByText(element, 'Save'));
      await settle();
      expect(currentUser.load).toHaveBeenCalledTimes(2);
    });
  });

  it('groups the role checkboxes under the Roles heading', async () => {
    const { element } = await open();
    const group = element.querySelector('[role=group]');
    expect(element.querySelector(`#${group?.getAttribute('aria-labelledby')}`)?.textContent).toBe(
      'Roles',
    );
    expect(group?.querySelectorAll('hlm-checkbox')).toHaveLength(5);
  });

  it('sends the user home when the API refuses the user with 403', async () => {
    api.reject(getUser, problem(403, 'urn:mentorhub:problem:forbidden'));
    const { harness } = await open();
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/'));
    expect(harness.routeNativeElement?.textContent ?? '').not.toContain('unavailable');
  });

  it.each(['ACTIVE', 'DISABLED'] as const)(
    'shows every card read-only without USERS_MANAGE (%s user)',
    async (status) => {
      api.resolve(
        getUser,
        userDetails({ status, ...(status === 'DISABLED' ? { disabledReason: 'GRADUATED' } : {}) }),
      );
      me.set(meUser({ permissions: ['USERS_READ'] }));
      const { element } = await open();
      expect(buttonByText(element, 'Edit')).toBeUndefined();
      expect(buttonByText(element, 'Disable user')).toBeUndefined();
      expect(buttonByText(element, 'Enable user')).toBeUndefined();
      expect(buttonByText(element, 'Save')).toBeUndefined();
      expect(element.querySelector('hlm-checkbox')).toBeNull();
      expect(element.querySelector('ul')?.textContent).toContain('Mentor');
    },
  );

  it('shows the user and leads back to the list with its search, filters and page', async () => {
    api.resolve(
      listUsers,
      userPage(
        [
          {
            id: USER_ID,
            email: 'ivan@example.com',
            firstName: 'Ivan',
            lastName: 'Sidorov',
            status: 'ACTIVE',
            roles: ['MENTOR'],
          } satisfies UserSummary,
        ],
        60,
      ),
    );
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/users?q=ivan&status=ACTIVE&role=MENTOR&page=1');
    await harness.fixture.whenStable();
    harness.detectChanges();
    click((harness.routeNativeElement as HTMLElement).querySelector('tbody a'));
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    const element = harness.routeNativeElement as HTMLElement;
    expect(element.querySelector('h1')?.textContent).toBe('Ivan Sidorov');

    click(element.querySelector('.back'));
    await harness.fixture.whenStable();
    harness.detectChanges();
    const router = TestBed.inject(Router);
    expect(router.url.startsWith('/users?')).toBe(true);
    expect(router.parseUrl(router.url).queryParams).toEqual({
      q: 'ivan',
      status: 'ACTIVE',
      role: 'MENTOR',
      page: '1',
    });
  });
});
