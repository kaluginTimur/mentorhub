import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
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
    TestBed.inject(MatDialog).closeAll();
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
  const rowButtons = (element: HTMLElement, row: number) =>
    [...element.querySelectorAll('tr.mat-mdc-row')[row].querySelectorAll('button')].map((b) =>
      b.textContent?.trim(),
    );

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

  it('shows invitations with status lines and row actions by status (AC-20)', async () => {
    api.invoke.mockResolvedValue(page(rowsOf));
    const { element } = await open();
    expect(element.querySelectorAll('h1')).toHaveLength(1);
    expect(element.querySelector('h1')?.textContent).toBe('Invitations');
    expect([...element.querySelectorAll('th')].map((t) => t.textContent?.trim())).toEqual([
      'Email',
      'Roles',
      'Status',
      'Invited',
      'Actions',
    ]);
    const rows = element.querySelectorAll('tr.mat-mdc-row');
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
    expect(rowButtons(element, 0)).toEqual(['Copy link', 'Get a new link', 'Revoke']);
    expect(rows[0].querySelector('button')?.getAttribute('aria-label')).toBe(
      'Copy invitation link for p@example.com',
    );
    expect(rowButtons(element, 1)).toEqual(['Get a new link', 'Revoke']);
    expect(rowButtons(element, 2)).toEqual([]);
    expect(rowButtons(element, 3)).toEqual([]);
  });

  it('restores search, status and page from the URL (AC-20)', async () => {
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

  it('writes the search to the URL 300 ms after typing stops (AC-20)', async () => {
    api.invoke.mockResolvedValue(page([invitation()]));
    const { harness, element } = await open('/invitations?page=2');
    const input = element.querySelector('input') as HTMLInputElement;
    input.value = 'iv';
    input.dispatchEvent(new Event('input'));
    input.value = 'ivan';
    input.dispatchEvent(new Event('input'));
    expect(TestBed.inject(Router).url).toBe('/invitations?page=2');
    await new Promise((resolve) => setTimeout(resolve, 400));
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/invitations?q=ivan');
    expect(listParams()).toEqual(expect.objectContaining({ q: 'ivan', page: 0 }));
  });

  it('limits the search to 254 characters', async () => {
    api.invoke.mockResolvedValue(page([invitation()]));
    const { element } = await open();
    expect(element.querySelector('input')?.getAttribute('maxlength')).toBe('254');
  });

  it('filters by status through the URL (AC-20)', async () => {
    api.invoke.mockResolvedValue(page([invitation()]));
    const { harness, element } = await open();
    (element.querySelector('mat-select .mat-mdc-select-trigger') as HTMLElement).click();
    harness.detectChanges();
    const options = [...document.querySelectorAll('mat-option')].map((o) => o.textContent?.trim());
    expect(options).toEqual(['All', 'Pending', 'Expired', 'Accepted', 'Revoked']);
    (document.querySelectorAll('mat-option')[3] as HTMLElement).click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/invitations?status=ACCEPTED');
  });

  it('shows the empty state without filters (AC-20)', async () => {
    api.invoke.mockResolvedValue(page([]));
    const { element } = await open();
    expect(element.textContent).toContain('No invitations yet.');
    expect(element.textContent).not.toContain('Clear filters');
  });

  it('shows the empty state with filters and clears them (AC-20)', async () => {
    api.invoke.mockResolvedValue(page([]));
    const { harness, element } = await open('/invitations?q=zzz&status=PENDING');
    expect(element.textContent).toContain('No invitations match the filters.');
    [...element.querySelectorAll('button')]
      .find((b) => b.textContent?.trim() === 'Clear filters')
      ?.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/invitations');
  });

  it('shows the progress bar and keeps the rows while loading (AC-20)', async () => {
    api.invoke.mockResolvedValueOnce(page([invitation()]));
    const { harness, element } = await open();
    let release!: (value: unknown) => void;
    api.invoke.mockReturnValueOnce(new Promise((resolve) => (release = resolve)));
    await TestBed.inject(Router).navigateByUrl('/invitations?status=PENDING');
    harness.detectChanges();
    expect(element.querySelector('mat-progress-bar')?.getAttribute('aria-label')).toBe(
      'Loading invitations',
    );
    expect(element.querySelector('main')?.getAttribute('aria-busy')).toBe('true');
    expect(element.querySelectorAll('tr.mat-mdc-row')).toHaveLength(1);
    release(page([invitation(), invitation({ id: 'x' })]));
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(element.querySelector('mat-progress-bar')).toBeNull();
    expect(element.querySelectorAll('tr.mat-mdc-row')).toHaveLength(2);
  });

  it('shows the unavailable card and retries (AC-20)', async () => {
    api.invoke.mockRejectedValueOnce(new Error('down'));
    const { harness, element } = await open();
    expect(element.querySelector('[role=alert]')?.textContent).toContain(
      'MentorHub is unavailable',
    );
    api.invoke.mockResolvedValueOnce(page([invitation()]));
    (element.querySelector('button') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(element.querySelectorAll('tr.mat-mdc-row')).toHaveLength(1);
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

    const click = (element: HTMLElement, label: string) =>
      [...element.querySelectorAll('tr.mat-mdc-row button')]
        .find((b) => b.textContent?.trim() === label)
        ?.dispatchEvent(new MouseEvent('click', { bubbles: true }));

    it('revokes after a confirmation and reloads the list (AC-20)', async () => {
      const { harness, element } = await openWithOnePending();
      click(element, 'Revoke');
      await vi.waitFor(() =>
        expect(document.querySelector('mat-dialog-container')?.textContent).toContain(
          'Revoke the invitation for p@example.com?',
        ),
      );
      expect(document.querySelector('mat-dialog-container')?.textContent).toContain(
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

    it('does nothing when the confirmation is cancelled', async () => {
      const { harness, element } = await openWithOnePending();
      click(element, 'Revoke');
      await vi.waitFor(() => expect(overlayButton('Cancel')).toBeTruthy());
      overlayButton('Cancel')?.click();
      await harness.fixture.whenStable();
      expect(api.invoke).toHaveBeenCalledTimes(1);
    });

    it('shows the closed message and reloads on 409 invitation-closed (AC-20)', async () => {
      const { harness, element } = await openWithOnePending();
      click(element, 'Revoke');
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

    it('handles a 404 like a closed invitation (AC-20)', async () => {
      const { harness, element } = await openWithOnePending();
      click(element, 'Revoke');
      await vi.waitFor(() => expect(overlayButton('Revoke')).toBeTruthy());
      api.invoke.mockRejectedValueOnce(problem(404, 'urn:mentorhub:problem:not-found'));
      await confirm(harness, 'Revoke');
      await vi.waitFor(() =>
        expect(document.body.textContent).toContain('This invitation is already closed.'),
      );
    });

    it('shows a generic message on other errors and leaves the row (AC-20)', async () => {
      const { harness, element } = await openWithOnePending();
      click(element, 'Revoke');
      await vi.waitFor(() => expect(overlayButton('Revoke')).toBeTruthy());
      api.invoke.mockRejectedValueOnce(problem(500, 'about:blank'));
      await confirm(harness, 'Revoke');
      await vi.waitFor(() =>
        expect(document.body.textContent).toContain('Something went wrong. Try again.'),
      );
      expect(api.invoke.mock.calls.filter(([fn]) => fn === listInvitations)).toHaveLength(1);
      expect(element.querySelectorAll('tr.mat-mdc-row')).toHaveLength(1);
    });

    it('issues a new link after a confirmation and shows it once (AC-19, AC-20)', async () => {
      const { harness, element } = await openWithOnePending();
      click(element, 'Get a new link');
      await vi.waitFor(() =>
        expect(document.querySelector('mat-dialog-container')?.textContent).toContain(
          'Get a new link for p@example.com?',
        ),
      );
      expect(document.querySelector('mat-dialog-container')?.textContent).toContain(
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

    describe('Copy link (AC-8)', () => {
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
        const { element } = await openWithOnePending();
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
        click(element, 'Copy link');
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
        const { element } = await pending();
        click(element, 'Copy link');
        await vi.waitFor(() => expect(writeText).toHaveBeenCalledWith(linkResponse.link));
        await vi.waitFor(() => expect(document.body.textContent).toContain('Link copied'));
      });

      it('falls back to writeText when the item write is not supported', async () => {
        const writeText = vi.fn().mockResolvedValue(undefined);
        vi.stubGlobal('ClipboardItem', undefined);
        vi.stubGlobal('navigator', { clipboard: { writeText } });
        const { element } = await pending();
        click(element, 'Copy link');
        await vi.waitFor(() => expect(writeText).toHaveBeenCalledWith(linkResponse.link));
        await vi.waitFor(() => expect(document.body.textContent).toContain('Link copied'));
      });

      it('opens the copy view when no clipboard write works', async () => {
        vi.stubGlobal('ClipboardItem', undefined);
        vi.stubGlobal('navigator', {});
        const { element } = await pending();
        click(element, 'Copy link');
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
      });

      it('disables the row actions while the link is fetched', async () => {
        vi.stubGlobal('ClipboardItem', undefined);
        vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn() } });
        const { harness, element } = await openWithOnePending();
        let release!: (v: unknown) => void;
        api.invoke.mockImplementation(
          (fn) => new Promise((r) => (release = fn === getInvitationLink ? r : () => undefined)),
        );
        click(element, 'Copy link');
        harness.detectChanges();
        await vi.waitFor(() => {
          harness.detectChanges();
          const buttons = [...element.querySelectorAll('tr.mat-mdc-row button')];
          expect(buttons).toHaveLength(3);
          expect(buttons.every((b) => (b as HTMLButtonElement).disabled)).toBe(true);
        });
        release(linkResponse);
      });

      it('shows the expired message and reloads on 409 invitation-expired', async () => {
        vi.stubGlobal('ClipboardItem', undefined);
        vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn() } });
        const { element } = await openWithOnePending();
        api.invoke.mockRejectedValueOnce(problem(409, 'urn:mentorhub:problem:invitation-expired'));
        click(element, 'Copy link');
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
      ])('shows the closed message and reloads on %i', async (status, type) => {
        vi.stubGlobal('ClipboardItem', undefined);
        vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn() } });
        const { element } = await openWithOnePending();
        api.invoke.mockRejectedValueOnce(problem(status, type));
        click(element, 'Copy link');
        await vi.waitFor(() =>
          expect(document.body.textContent).toContain('This invitation is already closed.'),
        );
        await vi.waitFor(() =>
          expect(api.invoke.mock.calls.filter(([fn]) => fn === listInvitations)).toHaveLength(2),
        );
      });

      it('shows a generic message on other errors', async () => {
        vi.stubGlobal('ClipboardItem', undefined);
        vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn() } });
        const { element } = await openWithOnePending();
        api.invoke.mockRejectedValueOnce(problem(500, 'about:blank'));
        click(element, 'Copy link');
        await vi.waitFor(() =>
          expect(document.body.textContent).toContain('Something went wrong. Try again.'),
        );
      });
    });

    it('reloads the list when the link dialog closes', async () => {
      const { harness, element } = await openWithOnePending();
      click(element, 'Get a new link');
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

    it('shows the closed message when the new link is refused with 409 (AC-20)', async () => {
      const { harness, element } = await openWithOnePending();
      click(element, 'Get a new link');
      await vi.waitFor(() => expect(overlayButton('Get a new link')).toBeTruthy());
      api.invoke.mockRejectedValueOnce(problem(409, 'urn:mentorhub:problem:invitation-closed'));
      await confirm(harness, 'Get a new link');
      await vi.waitFor(() =>
        expect(document.body.textContent).toContain('This invitation is already closed.'),
      );
    });

    it('opens the invite dialog from the Invite button (AC-19)', async () => {
      const { element } = await openWithOnePending();
      (
        [...element.querySelectorAll('button')].find(
          (b) => b.textContent?.trim() === 'Invite',
        ) as HTMLButtonElement
      ).click();
      await vi.waitFor(() =>
        expect(document.querySelector('mat-dialog-container')?.textContent).toContain(
          'Invite a person',
        ),
      );
    });
  });
});
