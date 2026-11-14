import { Dialog } from '@angular/cdk/dialog';
import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ToastService } from '../../shared/toast/toast.service';
import { ClientConfigService } from '../../core/client-config.service';
import { sendInvitationEmail } from '../../core/api/fn/invitations/send-invitation-email';
import { Api } from '../../core/api/api';
import { getInvitationLink } from '../../core/api/fn/invitations/get-invitation-link';
import { listInvitations } from '../../core/api/fn/invitations/list-invitations';
import { reissueInvitationLink } from '../../core/api/fn/invitations/reissue-invitation-link';
import { revokeInvitation } from '../../core/api/fn/invitations/revoke-invitation';
import { InvitationsList } from './invitations-list';
import { created, invitation } from './test-data';

const page = (items: unknown[], totalItems = items.length) => ({
  items,
  page: 0,
  size: 25,
  totalItems,
});

const problem = (status: number, type: string) =>
  new HttpErrorResponse({ status, error: { type, status } });

const overlayButton = (text: string) =>
  [...document.querySelectorAll<HTMLButtonElement>('.cdk-overlay-container button')].find(
    (b) => b.textContent?.trim() === text,
  );

describe('InvitationsList', () => {
  const api = { invoke: vi.fn() };

  beforeEach(() => {
    api.invoke.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'invitations', component: InvitationsList }]),
        { provide: Api, useValue: api },
      ],
    });
  });

  afterEach(() => {
    TestBed.inject(Dialog).closeAll();
    document.querySelector('.cdk-overlay-container')?.replaceChildren();
  });

  async function open(url = '/invitations') {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    await harness.fixture.whenStable();
    harness.detectChanges();
    return { harness, element: harness.routeNativeElement as HTMLElement };
  }

  const listParams = () =>
    api.invoke.mock.calls.filter(([fn]) => fn === listInvitations).at(-1)?.[1] as Record<
      string,
      unknown
    >;
  const actionsTrigger = (element: HTMLElement, row = 0) =>
    element.querySelectorAll('tbody tr')[row]?.querySelector<HTMLButtonElement>('button') ?? null;
  const openActionsMenu = async (harness: RouterTestingHarness, element: HTMLElement, row = 0) => {
    actionsTrigger(element, row)?.click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    return [...document.querySelectorAll<HTMLButtonElement>('[hlmDropdownMenuItem]')];
  };
  const closeActionsMenu = async (harness: RouterTestingHarness) => {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await harness.fixture.whenStable();
    harness.detectChanges();
  };
  const clickAction = async (
    harness: RouterTestingHarness,
    element: HTMLElement,
    label: string,
    row = 0,
  ) => {
    const items = await openActionsMenu(harness, element, row);
    items.find((b) => b.textContent?.trim() === label)?.click();
  };

  const rowsOf = [
    invitation({ id: 'p', email: 'p@example.com', roles: ['MENTOR', 'STUDENT'] }),
    invitation({ id: 'e', email: 'e@example.com', status: 'EXPIRED', roles: [] }),
    invitation({
      id: 'a',
      email: 'a@example.com',
      status: 'ACCEPTED',
      acceptedAt: '2026-10-19T09:00:00Z',
      acceptedUser: { id: 'u9', name: 'Maria K.' },
    }),
    invitation({
      id: 'r',
      email: 'r@example.com',
      status: 'REVOKED',
      revokedAt: '2026-10-19T09:00:00Z',
      revokedBy: { id: 'a2', name: 'Boris B.' },
    }),
  ];

  it('shows invitations with status lines and row actions by status', async () => {
    api.invoke.mockResolvedValue(page(rowsOf));
    const { harness, element } = await open();
    expect(element.querySelectorAll('h1')).toHaveLength(1);
    expect(element.querySelector('h1')?.textContent).toBe('Invitations');
    expect([...element.querySelectorAll('th')].map((t) => t.textContent?.trim())).toEqual([
      'Email',
      'Roles',
      'Status',
      'Invited',
      'Actions',
    ]);
    const rows = element.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(4);
    expect(rows[0].textContent).toContain('Mentor');
    expect(rows[0].textContent).toContain('Student');
    expect(rows[0].textContent).toContain('Pending');
    expect(rows[0].textContent).toContain('Expires 25 Oct 2026');
    expect(rows[0].textContent).toContain('18 Oct 2026');
    expect(rows[0].textContent).toContain('Ada Admin');
    expect(rows[1].textContent).toContain('No roles');
    expect(rows[1].textContent).toContain('Expired 25 Oct 2026');
    expect(rows[2].textContent).toContain('Accepted by');
    expect(rows[2].querySelector('a')?.getAttribute('href')).toBe('/users/u9');
    expect(rows[2].querySelector('a')?.textContent).toBe('Maria K.');
    expect(rows[3].textContent).toContain('Revoked by Boris B.');
    expect(actionsTrigger(element, 0)?.getAttribute('aria-label')).toBe(
      'Actions for p@example.com',
    );
    expect((await openActionsMenu(harness, element, 0)).map((b) => b.textContent?.trim())).toEqual([
      'Copy link',
      'Get a new link',
      'Revoke',
    ]);
    await closeActionsMenu(harness);
    expect(actionsTrigger(element, 1)?.getAttribute('aria-label')).toBe(
      'Actions for e@example.com',
    );
    expect((await openActionsMenu(harness, element, 1)).map((b) => b.textContent?.trim())).toEqual([
      'Get a new link',
      'Revoke',
    ]);
    await closeActionsMenu(harness);
    expect(actionsTrigger(element, 2)).toBeNull();
    expect(actionsTrigger(element, 3)).toBeNull();
  });

  it('uses fixed desktop column widths and an accessible Actions header', async () => {
    api.invoke.mockResolvedValue(page(rowsOf));
    const { element } = await open();
    const table = element.querySelector('table');
    expect(table?.classList.contains('md:table-fixed')).toBe(true);
    const headers = [...element.querySelectorAll('th')];
    expect(headers.map((h) => h.textContent?.trim())).toEqual([
      'Email',
      'Roles',
      'Status',
      'Invited',
      'Actions',
    ]);
    expect(headers[0].className).not.toMatch(/\b(?:md:|lg:)?w-/);
    expect(headers[1].classList.contains('md:w-40')).toBe(true);
    expect(headers[1].classList.contains('lg:w-56')).toBe(true);
    expect(headers[2].classList.contains('md:w-40')).toBe(true);
    expect(headers[2].classList.contains('lg:w-52')).toBe(true);
    expect(headers[3].classList.contains('md:w-36')).toBe(true);
    expect(headers[3].classList.contains('lg:w-44')).toBe(true);
    expect(headers[4].classList.contains('md:w-14')).toBe(true);
    expect(headers[4].classList.contains('md:px-3')).toBe(true);
    expect(headers[4].querySelector('.sr-only')?.textContent?.trim()).toBe('Actions');
    const cells = element.querySelectorAll('tbody tr:first-child td');
    expect(cells[0].classList.contains('md:whitespace-normal')).toBe(true);
    expect(cells[0].classList.contains('md:break-all')).toBe(true);
    expect(cells[2].classList.contains('md:whitespace-normal')).toBe(true);
    expect(cells[3].classList.contains('md:whitespace-normal')).toBe(true);
    expect(cells[4].classList.contains('md:px-3')).toBe(true);
  });

  it('restores search, status and page from the URL', async () => {
    api.invoke.mockResolvedValue(page([invitation()], 60));
    const { element } = await open('/invitations?q=ivan%40example.com&status=EXPIRED&page=1');
    expect(listParams()).toEqual({
      q: 'ivan@example.com',
      status: 'EXPIRED',
      page: 1,
      size: 25,
    });
    expect((element.querySelector('input') as HTMLInputElement).value).toBe('ivan@example.com');
  });

  it('writes the search to the URL 300 ms after typing stops', async () => {
    api.invoke.mockResolvedValue(page([invitation()]));
    const { harness, element } = await open('/invitations?page=2');
    const input = element.querySelector('input') as HTMLInputElement;
    input.value = 'iv';
    input.dispatchEvent(new Event('input'));
    input.value = 'ivan';
    input.dispatchEvent(new Event('input'));
    expect(TestBed.inject(Router).url).toBe('/invitations?page=2');
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/invitations?q=ivan'));
    await harness.fixture.whenStable();
    expect(listParams()).toEqual(expect.objectContaining({ q: 'ivan', page: 0 }));
  });

  it('limits the search to 254 characters', async () => {
    api.invoke.mockResolvedValue(page([invitation()]));
    const { element } = await open();
    expect(element.querySelector('input')?.getAttribute('maxlength')).toBe('254');
  });

  it('filters by status through the URL', async () => {
    api.invoke.mockResolvedValue(page([invitation()]));
    const { harness, element } = await open();
    expect(document.querySelectorAll('hlm-select-item')).toHaveLength(0);
    (element.querySelector('hlm-select-trigger button') as HTMLElement).click();
    harness.detectChanges();
    const options = [...document.querySelectorAll('hlm-select-item')].map((o) =>
      o.textContent?.trim(),
    );
    expect(options).toEqual(['All', 'Pending', 'Expired', 'Accepted', 'Revoked']);
    (document.querySelectorAll('hlm-select-item')[3] as HTMLElement).click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/invitations?status=ACCEPTED');
  });

  it('shows the empty state without filters', async () => {
    api.invoke.mockResolvedValue(page([]));
    const { element } = await open();
    expect(element.textContent).toContain('No invitations yet.');
    expect(element.textContent).not.toContain('Clear filters');
  });

  it('shows the empty state with filters and clears them', async () => {
    api.invoke.mockResolvedValue(page([]));
    const { harness, element } = await open('/invitations?q=zzz&status=PENDING');
    expect(element.textContent).toContain('No invitations match the filters.');
    [...element.querySelectorAll('button')]
      .find((b) => b.textContent?.trim() === 'Clear filters')
      ?.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/invitations');
  });

  it('shows the progress bar and keeps the rows while loading', async () => {
    api.invoke.mockResolvedValueOnce(page([invitation()]));
    const { harness, element } = await open();
    let release!: (value: unknown) => void;
    api.invoke.mockReturnValueOnce(new Promise((resolve) => (release = resolve)));
    await TestBed.inject(Router).navigateByUrl('/invitations?status=PENDING');
    harness.detectChanges();
    expect(element.querySelector('[role="progressbar"]')?.getAttribute('aria-label')).toBe(
      'Loading invitations',
    );
    expect(element.querySelector('main')?.getAttribute('aria-busy')).toBe('true');
    expect(element.querySelectorAll('tbody tr')).toHaveLength(1);
    release(page([invitation(), invitation({ id: 'x' })]));
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(element.querySelector('[role="progressbar"]')).toBeNull();
    expect(element.querySelectorAll('tbody tr')).toHaveLength(2);
  });

  it('shows the unavailable card and retries', async () => {
    api.invoke.mockRejectedValueOnce(new Error('down'));
    const { harness, element } = await open();
    expect(element.querySelector('[role=alert]')?.textContent).toContain(
      'MentorHub is unavailable',
    );
    api.invoke.mockResolvedValueOnce(page([invitation()]));
    (element.querySelector('button') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(element.querySelectorAll('tbody tr')).toHaveLength(1);
  });

  it('sends the user home when the API refuses the list with 403', async () => {
    api.invoke.mockRejectedValue(problem(403, 'urn:mentorhub:problem:forbidden'));
    await open();
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/'));
  });

  describe('row actions', () => {
    async function openWithOnePending() {
      api.invoke.mockResolvedValue(page([invitation({ id: 'p', email: 'p@example.com' })]));
      const result = await open();
      return result;
    }

    async function confirm(harness: RouterTestingHarness, label: string) {
      await vi.waitFor(() => expect(overlayButton(label)).toBeTruthy());
      overlayButton(label)?.click();
      await harness.fixture.whenStable();
      harness.detectChanges();
    }

    it('INV-18.1 revokes after a confirmation and reloads the list', async () => {
      const { harness, element } = await openWithOnePending();
      await clickAction(harness, element, 'Revoke');
      await vi.waitFor(() =>
        expect(document.querySelector('cdk-dialog-container')?.textContent).toContain(
          'Revoke the invitation for p@example.com?',
        ),
      );
      expect(document.querySelector('cdk-dialog-container')?.textContent).toContain(
        'The link will stop working.',
      );
      expect(api.invoke).not.toHaveBeenCalledWith(revokeInvitation, expect.anything());
      api.invoke.mockImplementation(async (fn) =>
        fn === revokeInvitation ? invitation({ status: 'REVOKED' }) : page([invitation()]),
      );
      await confirm(harness, 'Revoke');
      await vi.waitFor(() =>
        expect(api.invoke).toHaveBeenCalledWith(revokeInvitation, { id: 'p' }),
      );
      await vi.waitFor(() =>
        expect(api.invoke.mock.calls.filter(([fn]) => fn === listInvitations)).toHaveLength(2),
      );
      expect(document.body.textContent).toContain('Invitation revoked');
    });

    it('INV-18.2 does nothing when the confirmation is cancelled', async () => {
      const { harness, element } = await openWithOnePending();
      await clickAction(harness, element, 'Revoke');
      await vi.waitFor(() => expect(overlayButton('Cancel')).toBeTruthy());
      overlayButton('Cancel')?.click();
      await harness.fixture.whenStable();
      expect(api.invoke).toHaveBeenCalledTimes(1);
    });

    it('INV-18.3 shows the closed message and reloads on 409 invitation-closed', async () => {
      const { harness, element } = await openWithOnePending();
      await clickAction(harness, element, 'Revoke');
      await vi.waitFor(() => expect(overlayButton('Revoke')).toBeTruthy());
      api.invoke.mockRejectedValueOnce(problem(409, 'urn:mentorhub:problem:invitation-closed'));
      await confirm(harness, 'Revoke');
      await vi.waitFor(() =>
        expect(document.body.textContent).toContain('This invitation is already closed.'),
      );
      await vi.waitFor(() =>
        expect(api.invoke.mock.calls.filter(([fn]) => fn === listInvitations)).toHaveLength(2),
      );
    });

    it('shows a generic message on other errors and leaves the row', async () => {
      const { harness, element } = await openWithOnePending();
      await clickAction(harness, element, 'Revoke');
      await vi.waitFor(() => expect(overlayButton('Revoke')).toBeTruthy());
      api.invoke.mockRejectedValueOnce(problem(500, 'about:blank'));
      await confirm(harness, 'Revoke');
      await vi.waitFor(() =>
        expect(document.body.textContent).toContain('Something went wrong. Try again.'),
      );
      expect(api.invoke.mock.calls.filter(([fn]) => fn === listInvitations)).toHaveLength(1);
      expect(element.querySelectorAll('tbody tr')).toHaveLength(1);
    });

    it('INV-18.1 issues a new link after a confirmation and shows it in the link view', async () => {
      const { harness, element } = await openWithOnePending();
      await clickAction(harness, element, 'Get a new link');
      await vi.waitFor(() =>
        expect(document.querySelector('cdk-dialog-container')?.textContent).toContain(
          'Get a new link for p@example.com?',
        ),
      );
      expect(document.querySelector('cdk-dialog-container')?.textContent).toContain(
        'The previous link will stop working.',
      );
      api.invoke.mockImplementation(async (fn) =>
        fn === reissueInvitationLink
          ? created({ id: 'p', email: 'p@example.com' })
          : page([invitation()]),
      );
      await confirm(harness, 'Get a new link');
      await vi.waitFor(() =>
        expect(api.invoke).toHaveBeenCalledWith(reissueInvitationLink, { id: 'p' }),
      );
      await vi.waitFor(() =>
        expect(document.querySelector('app-link-view')?.textContent).toContain('Invitation link'),
      );
      const field = document.querySelector('app-link-view input') as HTMLInputElement;
      expect(field.value).toBe('http://localhost:4200/invite/Xf3abc');
      expect(document.querySelector('app-link-view')?.textContent).toContain(
        'Send this link to p@example.com. It expires on 25 Oct 2026. You can copy it again from the invitations list while it is pending.',
      );
    });

    describe('Copy link', () => {
      const linkResponse = {
        link: 'http://localhost:4200/invite/Xf3abc',
        email: 'p@example.com',
        expiresAt: '2026-10-25T09:00:00Z',
      };

      afterEach(() => vi.unstubAllGlobals());

      const pending = async () => {
        const result = await openWithOnePending();
        api.invoke.mockImplementation(async (fn) =>
          fn === getInvitationLink ? linkResponse : page([invitation({ id: 'p' })]),
        );
        return result;
      };

      it('starts the clipboard item write inside the click, before the link arrives', async () => {
        class FakeItem {
          constructor(readonly data: Record<string, Promise<Blob>>) {}
        }
        let copiedText: Promise<string> | undefined;
        const write = vi.fn(async (items: FakeItem[]) => {
          copiedText = items[0].data['text/plain'].then((blob) => blob.text());
          await copiedText;
        });
        vi.stubGlobal('ClipboardItem', FakeItem);
        vi.stubGlobal('navigator', { clipboard: { write } });
        const { harness, element } = await openWithOnePending();
        let release!: (v: unknown) => void;
        api.invoke.mockImplementation(
          (fn) =>
            new Promise((resolve) => {
              if (fn === getInvitationLink) {
                release = resolve;
              } else {
                resolve(page([invitation({ id: 'p' })]));
              }
            }),
        );
        await clickAction(harness, element, 'Copy link');
        expect(write).toHaveBeenCalledTimes(1);
        expect(document.body.textContent).not.toContain('Link copied');
        release(linkResponse);
        await vi.waitFor(() => expect(document.body.textContent).toContain('Link copied'));
        expect(await copiedText).toBe(linkResponse.link);
        expect(api.invoke).toHaveBeenCalledWith(getInvitationLink, { id: 'p' });
      });

      it('falls back to writeText when the item write is rejected', async () => {
        class FakeItem {
          constructor(readonly data: Record<string, Promise<Blob>>) {}
        }
        const write = vi.fn().mockRejectedValue(new Error('denied'));
        const writeText = vi.fn().mockResolvedValue(undefined);
        vi.stubGlobal('ClipboardItem', FakeItem);
        vi.stubGlobal('navigator', { clipboard: { write, writeText } });
        const { harness, element } = await pending();
        await clickAction(harness, element, 'Copy link');
        await vi.waitFor(() => expect(writeText).toHaveBeenCalledWith(linkResponse.link));
        await vi.waitFor(() => expect(document.body.textContent).toContain('Link copied'));
      });

      it('falls back to writeText when the item write is not supported', async () => {
        const writeText = vi.fn().mockResolvedValue(undefined);
        vi.stubGlobal('ClipboardItem', undefined);
        vi.stubGlobal('navigator', { clipboard: { writeText } });
        const { harness, element } = await pending();
        await clickAction(harness, element, 'Copy link');
        await vi.waitFor(() => expect(writeText).toHaveBeenCalledWith(linkResponse.link));
        await vi.waitFor(() => expect(document.body.textContent).toContain('Link copied'));
      });

      it('INV-17.1 opens the copy view when no clipboard write works', async () => {
        vi.stubGlobal('ClipboardItem', undefined);
        vi.stubGlobal('navigator', {});
        const { harness, element } = await pending();
        await clickAction(harness, element, 'Copy link');
        await vi.waitFor(() =>
          expect(document.querySelector('app-link-view')?.textContent).toContain(
            'Copy this link and send it to p@example.com. It expires on 25 Oct 2026.',
          ),
        );
        expect((document.querySelector('app-link-view input') as HTMLInputElement).value).toBe(
          linkResponse.link,
        );
        expect(
          document.querySelector('app-link-view button[aria-label="Copy invitation link"]'),
        ).toBeNull();
        const input = document.querySelector('app-link-view input') as HTMLInputElement;
        expect(document.activeElement).toBe(input);
        expect(input.selectionStart).toBe(0);
        expect(input.selectionEnd).toBe(linkResponse.link.length);
      });

      it('returns focus to the row Actions button after the copy view closes', async () => {
        vi.stubGlobal('ClipboardItem', undefined);
        vi.stubGlobal('navigator', {});
        const { harness, element } = await pending();
        actionsTrigger(element, 0)?.focus();
        await clickAction(harness, element, 'Copy link');
        await vi.waitFor(() => expect(document.querySelector('app-link-view')).toBeTruthy());
        TestBed.inject(Dialog).closeAll();
        await vi.waitFor(() => expect(document.querySelector('app-link-view')).toBeNull());
        await vi.waitFor(() => {
          harness.detectChanges();
          const current = actionsTrigger(element, 0);
          expect(current?.getAttribute('aria-label')).toBe('Actions for p@example.com');
          expect(document.activeElement).toBe(current);
        });
      });

      it('shows nothing of its own on 403 (the interceptor shows the permission message)', async () => {
        vi.stubGlobal('ClipboardItem', undefined);
        vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn() } });
        const { harness, element } = await openWithOnePending();
        api.invoke.mockRejectedValueOnce(problem(403, 'urn:mentorhub:problem:forbidden'));
        await clickAction(harness, element, 'Copy link');
        await vi.waitFor(() =>
          expect(actionsTrigger(element, 0)?.getAttribute('aria-disabled')).not.toBe('true'),
        );
        expect(document.body.textContent).not.toContain('Something went wrong');
        expect(document.body.textContent).not.toContain('already closed');
        expect(api.invoke.mock.calls.filter(([fn]) => fn === listInvitations)).toHaveLength(1);
      });

      it('disables the row actions while the link is fetched', async () => {
        vi.stubGlobal('ClipboardItem', undefined);
        vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn() } });
        const { harness, element } = await openWithOnePending();
        let release!: (v: unknown) => void;
        api.invoke.mockImplementation(
          (fn) => new Promise((r) => (release = fn === getInvitationLink ? r : () => undefined)),
        );
        await clickAction(harness, element, 'Copy link');
        harness.detectChanges();
        const trigger = actionsTrigger(element, 0) as HTMLButtonElement;
        await vi.waitFor(() => {
          harness.detectChanges();
          expect(trigger.getAttribute('aria-disabled')).toBe('true');
          expect(trigger.disabled).toBe(false);
        });
        trigger.focus();
        expect(document.activeElement).toBe(trigger);
        const items = await openActionsMenu(harness, element, 0);
        expect(items).toHaveLength(3);
        expect(items.every((b) => b.getAttribute('aria-disabled') === 'true')).toBe(true);
        const calls = api.invoke.mock.calls.length;
        items[0].click();
        expect(api.invoke.mock.calls).toHaveLength(calls);
        await closeActionsMenu(harness);
        release(linkResponse);
      });

      it('keeps the busy state per row while another row starts an action', async () => {
        vi.stubGlobal('ClipboardItem', undefined);
        vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn() } });
        api.invoke.mockResolvedValue(
          page([invitation({ id: 'p' }), invitation({ id: 'q', email: 'q@example.com' })]),
        );
        const { harness, element } = await open();
        const releases: Record<string, (v: unknown) => void> = {};
        api.invoke.mockImplementation(
          (fn, params) =>
            new Promise((resolve) => {
              if (fn === getInvitationLink) {
                releases[(params as { id: string }).id] = resolve;
              } else {
                resolve(page([]));
              }
            }),
        );
        await clickAction(harness, element, 'Copy link', 0);
        await clickAction(harness, element, 'Copy link', 1);
        harness.detectChanges();
        expect(actionsTrigger(element, 0)?.getAttribute('aria-disabled')).toBe('true');
        expect(actionsTrigger(element, 1)?.getAttribute('aria-disabled')).toBe('true');
        releases['q'](linkResponse);
        await vi.waitFor(() => {
          harness.detectChanges();
          expect(actionsTrigger(element, 1)?.getAttribute('aria-disabled')).not.toBe('true');
        });
        expect(actionsTrigger(element, 0)?.getAttribute('aria-disabled')).toBe('true');
        releases['p'](linkResponse);
      });

      it('INV-17.3 shows the expired message and reloads on 409 invitation-expired', async () => {
        vi.stubGlobal('ClipboardItem', undefined);
        vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn() } });
        const { harness, element } = await openWithOnePending();
        api.invoke.mockRejectedValueOnce(problem(409, 'urn:mentorhub:problem:invitation-expired'));
        await clickAction(harness, element, 'Copy link');
        await vi.waitFor(() =>
          expect(document.body.textContent).toContain(
            'This invitation has expired. Get a new link.',
          ),
        );
        await vi.waitFor(() =>
          expect(api.invoke.mock.calls.filter(([fn]) => fn === listInvitations)).toHaveLength(2),
        );
      });

      it.each([
        [409, 'urn:mentorhub:problem:invitation-closed'],
        [404, 'urn:mentorhub:problem:not-found'],
      ])('INV-17.3 shows the closed message and reloads on %i', async (status, type) => {
        vi.stubGlobal('ClipboardItem', undefined);
        vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn() } });
        const { harness, element } = await openWithOnePending();
        api.invoke.mockRejectedValueOnce(problem(status, type));
        await clickAction(harness, element, 'Copy link');
        await vi.waitFor(() =>
          expect(document.body.textContent).toContain('This invitation is already closed.'),
        );
        await vi.waitFor(() =>
          expect(api.invoke.mock.calls.filter(([fn]) => fn === listInvitations)).toHaveLength(2),
        );
      });
    });

    it('reloads the list when the link dialog closes', async () => {
      const { harness, element } = await openWithOnePending();
      await clickAction(harness, element, 'Get a new link');
      await vi.waitFor(() => expect(overlayButton('Get a new link')).toBeTruthy());
      api.invoke.mockImplementation(async (fn) =>
        fn === reissueInvitationLink
          ? created({ id: 'p', email: 'p@example.com' })
          : page([invitation()]),
      );
      await confirm(harness, 'Get a new link');
      await vi.waitFor(() => expect(overlayButton('Done')).toBeTruthy());
      const before = api.invoke.mock.calls.filter(([fn]) => fn === listInvitations).length;
      overlayButton('Done')?.click();
      await vi.waitFor(() =>
        expect(api.invoke.mock.calls.filter(([fn]) => fn === listInvitations)).toHaveLength(
          before + 1,
        ),
      );
    });

    it('INV-18.3 shows the closed message when the new link is refused with 409', async () => {
      const { harness, element } = await openWithOnePending();
      await clickAction(harness, element, 'Get a new link');
      await vi.waitFor(() => expect(overlayButton('Get a new link')).toBeTruthy());
      api.invoke.mockRejectedValueOnce(problem(409, 'urn:mentorhub:problem:invitation-closed'));
      await confirm(harness, 'Get a new link');
      await vi.waitFor(() =>
        expect(document.body.textContent).toContain('This invitation is already closed.'),
      );
    });

    it('opens the invite dialog from the Invite button', async () => {
      const { element } = await openWithOnePending();
      (
        [...element.querySelectorAll('button')].find(
          (b) => b.textContent?.trim() === 'Invite',
        ) as HTMLButtonElement
      ).click();
      await vi.waitFor(() =>
        expect(document.querySelector('cdk-dialog-container')?.textContent).toContain(
          'Invite a person',
        ),
      );
    });
  });

  describe('invitation emails', () => {
    const emailOn = () =>
      TestBed.overrideProvider(ClientConfigService, { useValue: { invitationEmails: () => true } });
    const pending = () => invitation({ id: 'p', email: 'p@example.com' });
    const confirmDialog = async (harness: RouterTestingHarness, label: string) => {
      await vi.waitFor(() => expect(overlayButton(label)).toBeTruthy());
      overlayButton(label)?.click();
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    const snackSpy = () => vi.spyOn(TestBed.inject(ToastService), 'show');
    const listCalls = () => api.invoke.mock.calls.filter(([fn]) => fn === listInvitations).length;

    it('shows "Send email" before "Copy link" on pending rows only', async () => {
      emailOn();
      api.invoke.mockResolvedValue(page(rowsOf));
      const { harness, element } = await open();
      expect(
        (await openActionsMenu(harness, element, 0)).map((b) => b.textContent?.trim()),
      ).toEqual(['Send email', 'Copy link', 'Get a new link', 'Revoke']);
      await closeActionsMenu(harness);
      expect(
        (await openActionsMenu(harness, element, 1)).map((b) => b.textContent?.trim()),
      ).toEqual(['Get a new link', 'Revoke']);
      await closeActionsMenu(harness);
    });

    it('hides the email action when email is disabled', async () => {
      api.invoke.mockResolvedValue(page([pending()]));
      const { harness, element } = await open();
      expect(
        (await openActionsMenu(harness, element, 0)).map((b) => b.textContent?.trim()),
      ).toEqual(['Copy link', 'Get a new link', 'Revoke']);
    });

    const mail = (status: string, finishedAt?: string) =>
      ({
        status,
        requestedAt: '2026-10-18T09:00:00Z',
        requestedBy: { id: 'a1', name: 'Ada Admin' },
        ...(finishedAt ? { finishedAt } : {}),
      }) as never;
    const emailWords = /Sending email|Email sent|Email failed|Email not sent/;

    it('shows the email line for every status of lastEmail', async () => {
      emailOn();
      api.invoke.mockResolvedValue(
        page([
          invitation({ id: '1', lastEmail: mail('QUEUED') }),
          invitation({ id: '2', lastEmail: mail('SENT', '2026-10-18T09:05:00Z') }),
          invitation({ id: '3', lastEmail: mail('FAILED', '2026-10-19T09:05:00Z') }),
          invitation({ id: '4', lastEmail: mail('SKIPPED', '2026-10-19T09:05:00Z') }),
          invitation({
            id: '5',
            status: 'REVOKED',
            lastEmail: mail('SKIPPED', '2026-10-18T09:00:00Z'),
          }),
          invitation({ id: '6' }),
          invitation({
            id: '7',
            status: 'ACCEPTED',
            lastEmail: mail('SENT', '2026-10-18T09:05:00Z'),
          }),
        ]),
      );
      const { element } = await open();
      const rows = [...element.querySelectorAll('tbody tr')].map((r) => r.textContent ?? '');
      expect(rows[0]).toContain('Sending email…');
      expect(rows[1]).toContain('Email sent 18 Oct 2026');
      expect(rows[2]).toContain('Email failed 19 Oct 2026');
      expect(rows[3]).toContain('Email not sent');
      expect(rows[4]).toContain('Email not sent');
      expect(rows[5]).not.toMatch(emailWords);
      expect(rows[6]).toContain('Email sent 18 Oct 2026');
    });

    it('shows no email line when email is disabled', async () => {
      api.invoke.mockResolvedValue(
        page([invitation({ id: '2', lastEmail: mail('SENT', '2026-10-18T09:05:00Z') })]),
      );
      const { element } = await open();
      expect(element.querySelector('tbody tr')?.textContent).not.toMatch(emailWords);
    });

    it('sends without a confirmation, replaces the row and confirms', async () => {
      emailOn();
      api.invoke.mockResolvedValue(page([pending()]));
      const { harness, element } = await open();
      api.invoke.mockResolvedValueOnce(
        invitation({
          id: 'p',
          email: 'p@example.com',
          lastEmail: {
            status: 'QUEUED',
            requestedAt: '2026-10-18T09:00:00Z',
            requestedBy: { id: 'a1', name: 'Ada Admin' },
          },
        }),
      );
      const snack = snackSpy();
      await clickAction(harness, element, 'Send email');
      await vi.waitFor(() =>
        expect(api.invoke).toHaveBeenCalledWith(sendInvitationEmail, { id: 'p' }),
      );
      await harness.fixture.whenStable();
      harness.detectChanges();
      expect(document.querySelector('cdk-dialog-container')).toBeNull();
      expect(element.querySelector('tbody tr')?.textContent).toContain('Sending email…');
      expect(document.body.textContent).toContain('Email to p@example.com queued');
      expect(snack).toHaveBeenCalledWith(
        'Email to p@example.com queued',
        expect.objectContaining({ politeness: 'polite' }),
      );
      expect(listCalls()).toBe(1);
    });

    it('IEM-12.1 explains 409 email-queued and reloads', async () => {
      emailOn();
      api.invoke.mockResolvedValue(page([pending()]));
      const { harness, element } = await open();
      api.invoke.mockRejectedValueOnce(problem(409, 'urn:mentorhub:problem:email-queued'));
      const snack = snackSpy();
      await clickAction(harness, element, 'Send email');
      await vi.waitFor(() =>
        expect(document.body.textContent).toContain(
          'An email to p@example.com is already on its way.',
        ),
      );
      expect(snack).toHaveBeenCalledWith(
        'An email to p@example.com is already on its way.',
        expect.objectContaining({ politeness: 'assertive' }),
      );
      await vi.waitFor(() => expect(listCalls()).toBe(2));
    });

    it('IEM-12.2 explains 409 email-unavailable', async () => {
      emailOn();
      api.invoke.mockResolvedValue(page([pending()]));
      const { harness, element } = await open();
      api.invoke.mockRejectedValueOnce(problem(409, 'urn:mentorhub:problem:email-unavailable'));
      const snack = snackSpy();
      await clickAction(harness, element, 'Send email');
      await vi.waitFor(() =>
        expect(document.body.textContent).toContain(
          'Sending email is not available. Reload the page and try again.',
        ),
      );
      expect(snack).toHaveBeenCalledWith(
        'Sending email is not available. Reload the page and try again.',
        expect.objectContaining({ politeness: 'assertive' }),
      );
    });

    it('disables the row actions while the email request runs', async () => {
      emailOn();
      api.invoke.mockResolvedValue(page([pending()]));
      const { harness, element } = await open();
      let resolve!: (value: unknown) => void;
      api.invoke.mockReturnValueOnce(new Promise((r) => (resolve = r)));
      await clickAction(harness, element, 'Send email');
      harness.detectChanges();
      await vi.waitFor(() =>
        expect(actionsTrigger(element, 0)?.getAttribute('aria-disabled')).toBe('true'),
      );
      const items = await openActionsMenu(harness, element, 0);
      expect(items.map((b) => b.getAttribute('aria-disabled'))).toEqual([
        'true',
        'true',
        'true',
        'true',
      ]);
      await closeActionsMenu(harness);
      resolve(pending());
      await harness.fixture.whenStable();
      harness.detectChanges();
      await vi.waitFor(() =>
        expect(actionsTrigger(element, 0)?.getAttribute('aria-disabled')).not.toBe('true'),
      );
    });

    it.each([
      [409, 'urn:mentorhub:problem:invitation-closed'],
      [404, 'urn:mentorhub:problem:not-found'],
    ])('IEM-12.3 treats a %i like Copy link', async (status, type) => {
      emailOn();
      api.invoke.mockResolvedValue(page([pending()]));
      const { harness, element } = await open();
      api.invoke.mockRejectedValueOnce(problem(status, type));
      await clickAction(harness, element, 'Send email');
      await vi.waitFor(() =>
        expect(document.body.textContent).toContain('This invitation is already closed.'),
      );
      await vi.waitFor(() => expect(listCalls()).toBe(2));
    });

    it('offers no email choice in the new-link confirmation when email is disabled', async () => {
      api.invoke.mockResolvedValue(page([pending()]));
      const { harness, element } = await open();
      await clickAction(harness, element, 'Get a new link');
      await vi.waitFor(() => expect(overlayButton('Get a new link')).toBeTruthy());
      expect(document.querySelector('cdk-dialog-container hlm-checkbox')).toBeNull();
      api.invoke.mockImplementation(async (fn) =>
        fn === reissueInvitationLink ? created({ id: 'p' }) : page([pending()]),
      );
      await confirmDialog(harness, 'Get a new link');
      await vi.waitFor(() =>
        expect(api.invoke.mock.calls.some(([fn]) => fn === reissueInvitationLink)).toBe(true),
      );
      const call = api.invoke.mock.calls.find(([fn]) => fn === reissueInvitationLink);
      expect(JSON.stringify(call?.[1])).not.toContain('sendEmail');
    });

    it('offers the email checkbox in the new-link confirmation, checked, and requests it', async () => {
      emailOn();
      api.invoke.mockResolvedValue(page([pending()]));
      const { harness, element } = await open();
      await clickAction(harness, element, 'Get a new link');
      await vi.waitFor(() => expect(overlayButton('Get a new link')).toBeTruthy());
      const box = [...document.querySelectorAll('cdk-dialog-container label')].find((l) =>
        l.querySelector('hlm-checkbox'),
      ) as HTMLElement;
      expect(box.textContent).toContain('Send the new link by email');
      expect(box.querySelector('button[role="checkbox"]')?.getAttribute('aria-checked')).toBe(
        'true',
      );
      api.invoke.mockImplementation(async (fn) =>
        fn === reissueInvitationLink
          ? created({ id: 'p', email: 'p@example.com' })
          : page([pending()]),
      );
      await confirmDialog(harness, 'Get a new link');
      await vi.waitFor(() =>
        expect(api.invoke).toHaveBeenCalledWith(reissueInvitationLink, {
          id: 'p',
          body: { sendEmail: true },
        }),
      );
      await vi.waitFor(() =>
        expect(document.querySelector('app-link-view')?.textContent).toContain(
          'An email with this link is on its way to p@example.com. It expires on 25 Oct 2026. You can also copy the link, and copy it again from the invitations list while it is pending.',
        ),
      );
    });

    it('requests no email when the box is unchecked', async () => {
      emailOn();
      api.invoke.mockResolvedValue(page([pending()]));
      const { harness, element } = await open();
      await clickAction(harness, element, 'Get a new link');
      await vi.waitFor(() => expect(overlayButton('Get a new link')).toBeTruthy());
      (
        document.querySelector(
          'cdk-dialog-container hlm-checkbox button[role="checkbox"]',
        ) as HTMLElement
      ).click();
      api.invoke.mockImplementation(async (fn) =>
        fn === reissueInvitationLink ? created({ id: 'p' }) : page([pending()]),
      );
      await confirmDialog(harness, 'Get a new link');
      await vi.waitFor(() =>
        expect(api.invoke).toHaveBeenCalledWith(reissueInvitationLink, {
          id: 'p',
          body: { sendEmail: false },
        }),
      );
      await vi.waitFor(() =>
        expect(document.querySelector('app-link-view')?.textContent).toContain('Send this link to'),
      );
    });

    it('IEM-11.1 shows a snackbar when the new link cannot be emailed', async () => {
      emailOn();
      api.invoke.mockResolvedValue(page([pending()]));
      const { harness, element } = await open();
      await clickAction(harness, element, 'Get a new link');
      await vi.waitFor(() => expect(overlayButton('Get a new link')).toBeTruthy());
      api.invoke.mockRejectedValueOnce(problem(409, 'urn:mentorhub:problem:email-unavailable'));
      await confirmDialog(harness, 'Get a new link');
      await vi.waitFor(() =>
        expect(document.body.textContent).toContain(
          'Sending email is not available. Reload the page and try again.',
        ),
      );
      expect(document.querySelector('app-link-view')).toBeNull();
    });
  });
});
