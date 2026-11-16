import { TestBed } from '@angular/core/testing';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { EnrollmentCandidatePage } from '../../core/api/models/enrollment-candidate-page';
import { EnrollStudentDialog, EnrollStudentDialogData } from './enroll-student-dialog';
import { enrollmentCandidate } from './test-data';

const overlay = () => document.querySelector('.cdk-overlay-container') as HTMLElement;
const field = () => overlay().querySelector<HTMLInputElement>('#enroll-student-search');
const options = () => [...overlay().querySelectorAll<HTMLElement>('[role=option]')];
const flush = () => new Promise((resolve) => setTimeout(resolve, 400));

describe('EnrollStudentDialog candidates drop-down', () => {
  let enroll: EnrollStudentDialogData['enroll'];
  let ref: { close: () => void };

  beforeEach(async () => {
    enroll = vi.fn(async () => true);
    const page: EnrollmentCandidatePage = {
      items: [enrollmentCandidate()],
      page: 0,
      size: 25,
      totalItems: 1,
    };
    ref = TestBed.inject(HlmDialogService).open(EnrollStudentDialog, {
      context: { load: async () => page, enroll } satisfies EnrollStudentDialogData,
    });
    await vi.waitFor(() => expect(field()).not.toBeNull());
  });

  afterEach(() => ref.close());

  it('keeps the panel closed on open, opens it on a click and again after it was closed', async () => {
    await flush();
    expect(options()).toHaveLength(0);
    field()?.click();
    await vi.waitFor(() => expect(options()).toHaveLength(1));
    expect(options()[0].textContent).toContain('Olga Candidate');

    field()?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await vi.waitFor(() => expect(options()).toHaveLength(0));
    field()?.click();
    await vi.waitFor(() => expect(options()).toHaveLength(1));
  });

  it('shows the chosen name in the field and drops the choice when the user types again', async () => {
    field()?.click();
    await vi.waitFor(() => expect(options()).toHaveLength(1));
    options()[0].click();
    await flush();
    expect(field()?.value).toBe('Olga Candidate');
    expect(overlay().querySelector('#enroll-student-chosen')?.textContent?.trim()).toBe(
      `Chosen: ${enrollmentCandidate().email}`,
    );
    expect(options(), 'the panel closes').toHaveLength(0);

    const input = field();
    if (input) {
      input.value = 'Olga Candidatex';
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }
    await flush();
    expect(field()?.value, 'the typed text stays').toBe('Olga Candidatex');
    expect(overlay().querySelector('#enroll-student-chosen')?.textContent?.trim()).toBe('');
  });
});

describe('EnrollStudentDialog calendar popup keyboard', () => {
  let ref: { close: () => void };
  const calendar = () => overlay().querySelector<HTMLElement>('hlm-calendar');
  const tabbable = () =>
    [
      ...(calendar()?.parentElement?.querySelectorAll<HTMLElement>('button, [tabindex]') ?? []),
    ].filter((el) => el.tabIndex >= 0 && !el.closest('[inert]'));
  const press = (target: HTMLElement, shiftKey: boolean) =>
    target.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Tab', shiftKey, bubbles: true, cancelable: true }),
    );

  beforeEach(async () => {
    ref = TestBed.inject(HlmDialogService).open(EnrollStudentDialog, {
      context: {
        load: async () => ({ items: [], page: 0, size: 25, totalItems: 0 }),
        enroll: async () => true,
      } satisfies EnrollStudentDialogData,
    });
    await vi.waitFor(() => expect(field()).not.toBeNull());
    overlay()
      .querySelector<HTMLElement>('[aria-label="Choose the start date in a calendar"]')
      ?.click();
    await vi.waitFor(() =>
      expect(calendar()?.parentElement?.contains(document.activeElement)).toBe(true),
    );
  });

  afterEach(() => ref.close());

  it('closes the popup on Tab from its last stop and focuses the date field', async () => {
    const last = tabbable().at(-1) as HTMLElement;
    last.focus();
    const event = press(last, false);
    expect(event).toBe(false);
    await vi.waitFor(() => expect(calendar()).toBeNull());
    expect(document.activeElement?.id).toBe('enroll-start-date');
  });

  it('closes the popup on Shift+Tab from its first stop and focuses the date field', async () => {
    const first = tabbable()[0];
    first.focus();
    expect(press(first, true)).toBe(false);
    await vi.waitFor(() => expect(calendar()).toBeNull());
    expect(document.activeElement?.id).toBe('enroll-start-date');
  });

  it('leaves Tab between the stops of the popup alone', () => {
    const first = tabbable()[0];
    first.focus();
    expect(press(first, false)).toBe(true);
    expect(calendar()).not.toBeNull();
  });
});
