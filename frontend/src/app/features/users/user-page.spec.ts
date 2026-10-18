import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Api } from '../../core/api/api';
import { disableUser } from '../../core/api/fn/users/disable-user';
import { enableUser } from '../../core/api/fn/users/enable-user';
import { getUserHistory } from '../../core/api/fn/users/get-user-history';
import { getUser } from '../../core/api/fn/users/get-user';
import { setUserRoles } from '../../core/api/fn/users/set-user-roles';
import { updateUserProfile } from '../../core/api/fn/users/update-user-profile';
import { MeUser } from '../../core/api/models/me-user';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { ADMIN_ID, USER_ID, meUser, userDetails } from './test-data';
import { UserPage } from './user-page';

const problem = (status: number, type: string, errors?: unknown[]) =>
  new HttpErrorResponse({ status, error: { type, status, title: 't', errors } });
const historyPage = (items: unknown[], totalItems = items.length) => ({
  items,
  page: 0,
  size: 25,
  totalItems,
});
const click = (el: Element | null | undefined) => (el as HTMLElement).click();
const buttonByText = (root: ParentNode, text: string) =>
  [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === text);

describe('UserPage', () => {
  const api = { invoke: vi.fn() };
  const me = signal<MeUser | null>(meUser());
  const currentUser = {
    user: me,
    load: vi.fn(),
    hasPermission: (p: string) => (me()?.permissions as string[] | undefined)?.includes(p) ?? false,
  };
  let responses: Map<unknown, (params: Record<string, unknown>) => unknown>;

  beforeEach(() => {
    api.invoke.mockReset();
    currentUser.load.mockReset().mockResolvedValue({});
    me.set(meUser());
    responses = new Map<unknown, (params: Record<string, unknown>) => unknown>([
      [getUser, () => userDetails()],
      [getUserHistory, () => historyPage([])],
    ]);
    api.invoke.mockImplementation(async (fn: unknown, params: Record<string, unknown>) => {
      const handler = responses.get(fn);
      if (!handler) throw new Error('unexpected call');
      return handler(params);
    });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'users/:id', component: UserPage },
          { path: '', children: [] },
        ]),
        { provide: Api, useValue: api },
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

  const overlay = () => document.querySelector('.cdk-overlay-container') as HTMLElement;
  const snack = () => document.querySelector('mat-snack-bar-container')?.textContent ?? '';

  afterEach(() => {
    document.querySelector('.cdk-overlay-container')?.replaceChildren();
  });

  it('shows the profile, access, roles and history (AC-24)', async () => {
    responses.set(getUserHistory, () =>
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
    expect(element.querySelector('mat-chip')?.textContent).toContain('Active');
    expect(element.textContent).toContain('Roles changed: added Mentor');
    expect(element.textContent).toContain('Anna Petrova');
    expect(element.textContent).toContain('System');
    expect(element.querySelectorAll('h2').length).toBe(4);
    expect(element.querySelector('.back')?.getAttribute('href')).toBe('/users');
  });

  it('shows the not found state for an unknown id (AC-24)', async () => {
    responses.set(getUser, () => {
      throw problem(404, 'urn:mentorhub:problem:not-found');
    });
    const { element } = await open();
    expect(element.querySelector('h1')?.textContent).toBe('User not found');
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/users');
  });

  it('shows the not found state for an id that is not a UUID without calling the API (AC-24)', async () => {
    const { element } = await open('abc');
    expect(element.querySelector('h1')?.textContent).toBe('User not found');
    expect(api.invoke).not.toHaveBeenCalled();
  });

  it('shows the unavailable card and retries (AC-24)', async () => {
    responses.set(getUser, () => {
      throw new Error('down');
    });
    const { element, settle } = await open();
    expect(element.querySelector('[role=alert]')?.textContent).toContain('unavailable');
    responses.set(getUser, () => userDetails());
    click(element.querySelector('button'));
    await settle();
    expect(element.querySelector('h1')?.textContent).toBe('Ivan Sidorov');
  });

  it('loads more history on "Show more" and handles absent optional lists', async () => {
    const entry = (id: string) => ({
      id,
      action: 'USER_ROLES_CHANGED',
      occurredAt: '2026-10-17T10:12:00Z',
      actor: null,
    });
    responses.set(getUserHistory, (p) =>
      p['page'] === 1 ? historyPage([entry('h3')], 3) : historyPage([entry('h1'), entry('h2')], 3),
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
    responses.set(getUserHistory, () => {
      throw new Error('down');
    });
    const { element, settle } = await open();
    expect(element.textContent).toContain('Could not load the history.');
    expect(element.querySelector('h1')?.textContent).toBe('Ivan Sidorov');
    responses.set(getUserHistory, () =>
      historyPage([
        { id: 'h1', action: 'USER_ENABLED', occurredAt: '2026-10-17T10:12:00Z', actor: null },
      ]),
    );
    click(buttonByText(element, 'Retry'));
    await settle();
    expect(element.querySelectorAll('.history li')).toHaveLength(1);
  });

  describe('disabling (AC-24, AC-26)', () => {
    async function openDialog() {
      const page = await open();
      click(buttonByText(page.element, 'Disable user'));
      await page.settle();
      return page;
    }

    it('requires a reason and a comment for "Other", then disables', async () => {
      responses.set(disableUser, (p) =>
        userDetails({
          status: 'DISABLED',
          disabledReason: 'OTHER',
          disabledComment: 'Left',
          ...(p ? {} : {}),
        }),
      );
      const { element, settle } = await openDialog();
      expect(overlay().textContent).toContain(`Disable Ivan Sidorov?`);
      expect(overlay().textContent).toContain('They lose access on their next action.');
      expect(overlay().textContent).toContain('Comment');
      expect(overlay().textContent).not.toContain('Comment (required)');

      click(buttonByText(overlay(), 'Disable'));
      await settle();
      expect(api.invoke).not.toHaveBeenCalledWith(disableUser, expect.anything());
      expect(overlay().textContent).toContain('Choose a reason');

      click(overlay().querySelector('mat-select'));
      await settle();
      const options = [...document.querySelectorAll('mat-option')];
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
      expect(api.invoke).not.toHaveBeenCalledWith(disableUser, expect.anything());

      const textarea = overlay().querySelector('textarea') as HTMLTextAreaElement;
      textarea.value = '  Left  ';
      textarea.dispatchEvent(new Event('input'));
      await settle();
      click(buttonByText(overlay(), 'Disable'));
      await settle();
      expect(api.invoke).toHaveBeenCalledWith(disableUser, {
        id: USER_ID,
        body: { reason: 'OTHER', comment: 'Left' },
      });
      await vi.waitFor(() => expect(snack()).toContain('User disabled'));
      await settle();
      expect(element.textContent).toContain('Other — Left');
      expect(element.querySelector('mat-chip')?.textContent).toContain('Disabled');
      expect(buttonByText(element, 'Enable user')).toBeDefined();
      const historyCalls = api.invoke.mock.calls.filter(([fn]) => fn === getUserHistory);
      expect(historyCalls.length).toBe(2);
    });

    it('shows server field errors under the fields and keeps the dialog open', async () => {
      responses.set(disableUser, () => {
        throw problem(400, 'urn:mentorhub:problem:validation-failed', [
          { field: 'comment', message: 'Comment is too long' },
        ]);
      });
      const { settle } = await openDialog();
      click(overlay().querySelector('mat-select'));
      await settle();
      click(document.querySelector('mat-option'));
      await settle();
      click(buttonByText(overlay(), 'Disable'));
      await settle();
      expect(overlay().textContent).toContain('Comment is too long');
      expect(overlay().querySelector('textarea')?.getAttribute('aria-invalid')).toBe('true');
    });

    it('shows the 409 message in a snackbar and keeps the dialog open', async () => {
      responses.set(disableUser, () => {
        throw problem(409, 'urn:mentorhub:problem:last-admin');
      });
      const { settle } = await openDialog();
      click(overlay().querySelector('mat-select'));
      await settle();
      click(document.querySelector('mat-option'));
      await settle();
      click(buttonByText(overlay(), 'Disable'));
      await settle();
      expect(snack()).toContain('MentorHub must keep at least one active administrator.');
      expect(overlay().textContent).toContain('Disable Ivan Sidorov?');
    });
  });

  it('enables a disabled user after confirmation (AC-24)', async () => {
    responses.set(getUser, () =>
      userDetails({ status: 'DISABLED', disabledReason: 'GRADUATED', disabledComment: 'Done' }),
    );
    responses.set(enableUser, () => userDetails());
    const { element, settle } = await open();
    expect(element.textContent).toContain('Graduated — Done');
    click(buttonByText(element, 'Enable user'));
    await settle();
    expect(overlay().textContent).toContain('Enable Ivan Sidorov?');
    expect(overlay().textContent).toContain('They will be able to sign in again.');
    click(buttonByText(overlay(), 'Enable'));
    await vi.waitFor(() => expect(api.invoke).toHaveBeenCalledWith(enableUser, { id: USER_ID }));
    await vi.waitFor(() => expect(snack()).toContain('User enabled'));
    await settle();
    expect(buttonByText(element, 'Disable user')).toBeDefined();
  });

  it('does not enable when the confirmation is cancelled', async () => {
    responses.set(getUser, () => userDetails({ status: 'DISABLED', disabledReason: 'GRADUATED' }));
    const { element, settle } = await open();
    click(buttonByText(element, 'Enable user'));
    await settle();
    click(buttonByText(overlay(), 'Cancel'));
    await settle();
    expect(api.invoke).not.toHaveBeenCalledWith(enableUser, expect.anything());
  });

  describe('roles (AC-24)', () => {
    it('saves the selection and shows the hint without roles', async () => {
      responses.set(setUserRoles, () => userDetails({ roles: [] }));
      const { element, settle } = await open();
      const save = () => buttonByText(element, 'Save') as HTMLButtonElement;
      expect(save().disabled).toBe(true);
      const boxes = [...element.querySelectorAll('mat-checkbox input')] as HTMLInputElement[];
      expect(boxes).toHaveLength(5);
      expect(boxes.map((b) => b.checked)).toEqual([false, false, true, false, false]);
      click(boxes[2]);
      await settle();
      expect(element.textContent).toContain(
        'Without roles the user can sign in but has no access to anything.',
      );
      expect(save().disabled).toBe(false);
      click(save());
      await settle();
      expect(api.invoke).toHaveBeenCalledWith(setUserRoles, { id: USER_ID, body: { roles: [] } });
      expect(snack()).toContain('Roles saved');
      expect(currentUser.load).not.toHaveBeenCalled();
    });

    it('resets the selection', async () => {
      const { element, settle } = await open();
      const boxes = [...element.querySelectorAll('mat-checkbox input')] as HTMLInputElement[];
      click(boxes[0]);
      await settle();
      expect(boxes[0].checked).toBe(true);
      click(buttonByText(element, 'Reset'));
      await settle();
      expect(boxes[0].checked).toBe(false);
      expect((buttonByText(element, 'Save') as HTMLButtonElement).disabled).toBe(true);
    });

    it('shows a 409 in a snackbar and keeps the selection (AC-26)', async () => {
      responses.set(setUserRoles, () => {
        throw problem(409, 'urn:mentorhub:problem:self-lockout');
      });
      const { element, settle } = await open();
      const boxes = [...element.querySelectorAll('mat-checkbox input')] as HTMLInputElement[];
      click(boxes[0]);
      await settle();
      click(buttonByText(element, 'Save'));
      await settle();
      expect(snack()).toContain("You can't lock yourself out.");
      expect(boxes[0].checked).toBe(true);
    });
  });

  describe('profile (AC-24, AC-26)', () => {
    it('edits, shows server field errors and then the stored values', async () => {
      const { element, settle } = await open();
      click(buttonByText(element, 'Edit'));
      await settle();
      const inputs = [...element.querySelectorAll('app-profile-form input')] as HTMLInputElement[];
      expect(inputs.map((i) => i.value)).toEqual(['Ivan', 'Sidorov', '+77011234567']);
      inputs[2].value = '123';
      inputs[2].dispatchEvent(new Event('input'));
      responses.set(updateUserProfile, () => {
        throw problem(400, 'urn:mentorhub:problem:validation-failed', [
          { field: 'phone', message: 'bad phone' },
        ]);
      });
      await settle();
      click(buttonByText(element, 'Save'));
      await settle();
      expect(element.textContent).toContain('Enter a valid phone number');
      expect(inputs[2].getAttribute('aria-invalid')).toBe('true');
      expect(inputs[0].value).toBe('Ivan');

      responses.set(updateUserProfile, () => userDetails({ phone: '+77017654321' }));
      inputs[2].value = '8 701 765 43 21';
      inputs[2].dispatchEvent(new Event('input'));
      await settle();
      click(buttonByText(element, 'Save'));
      await settle();
      expect(api.invoke).toHaveBeenCalledWith(updateUserProfile, {
        id: USER_ID,
        body: { firstName: 'Ivan', lastName: 'Sidorov', phone: '8 701 765 43 21' },
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
      expect(api.invoke).not.toHaveBeenCalledWith(updateUserProfile, expect.anything());
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

  describe('own page (AC-25)', () => {
    beforeEach(() => {
      responses.set(getUser, () =>
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
      expect(document.querySelector('mat-dialog-container')).toBeNull();

      const admin = element.querySelector('mat-checkbox input') as HTMLInputElement;
      expect(admin.getAttribute('aria-disabled')).toBe('true');
      const adminHint = element.querySelector(`#${admin.getAttribute('aria-describedby')}`);
      expect(adminHint?.textContent).toContain("You can't remove your own administrator role.");
    });

    it('refreshes the current user after saving the profile or roles', async () => {
      responses.set(updateUserProfile, () =>
        userDetails({ id: ADMIN_ID, firstName: 'Anya', roles: ['ADMIN'] }),
      );
      responses.set(setUserRoles, () => userDetails({ id: ADMIN_ID, roles: ['ADMIN', 'MENTOR'] }));
      const { element, settle } = await open(ADMIN_ID);
      click(buttonByText(element, 'Edit'));
      await settle();
      click(buttonByText(element, 'Save'));
      await settle();
      expect(currentUser.load).toHaveBeenCalledTimes(1);

      const boxes = [...element.querySelectorAll('mat-checkbox input')] as HTMLInputElement[];
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
    expect(group?.querySelectorAll('mat-checkbox')).toHaveLength(5);
  });

  it('counts and limits the comment after trimming', async () => {
    const { settle } = await (async () => {
      const page = await open();
      click(buttonByText(page.element, 'Disable user'));
      await page.settle();
      return page;
    })();
    click(overlay().querySelector('mat-select'));
    await settle();
    click(document.querySelector('mat-option'));
    await settle();
    const textarea = overlay().querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = `  ${'x'.repeat(500)}  `;
    textarea.dispatchEvent(new Event('input'));
    await settle();
    expect(overlay().textContent).toContain('500 / 500');
    click(buttonByText(overlay(), 'Disable'));
    await settle();
    expect(overlay().textContent).not.toContain('Use at most 500 characters');
    expect(api.invoke).toHaveBeenCalledWith(
      disableUser,
      expect.objectContaining({ body: expect.objectContaining({ comment: 'x'.repeat(500) }) }),
    );
  });

  it('sends the user home when the API refuses the user with 403', async () => {
    responses.set(getUser, () => {
      throw new HttpErrorResponse({
        status: 403,
        error: { type: 'urn:mentorhub:problem:forbidden' },
      });
    });
    const { harness } = await open();
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/'));
    expect(harness.routeNativeElement?.textContent ?? '').not.toContain('unavailable');
  });

  it('shows every card read-only without USERS_MANAGE (AC-22)', async () => {
    me.set(meUser({ permissions: ['USERS_READ'] }));
    const { element } = await open();
    expect(buttonByText(element, 'Edit')).toBeUndefined();
    expect(buttonByText(element, 'Disable user')).toBeUndefined();
    expect(buttonByText(element, 'Enable user')).toBeUndefined();
    expect(buttonByText(element, 'Save')).toBeUndefined();
    expect(element.querySelector('mat-checkbox')).toBeNull();
    expect(element.querySelector('ul')?.textContent).toContain('Mentor');
  });
});
