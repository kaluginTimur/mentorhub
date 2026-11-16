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
