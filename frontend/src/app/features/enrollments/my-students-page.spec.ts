import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { listMyStudents } from '../../core/api/fn/enrollments/list-my-students';
import { Permission } from '../../core/api/models/permission';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { MyStudentsPage } from './my-students-page';
import {
  enrolledStudent,
  mentorUser,
  mentoredEnrollment,
  mentoredEnrollmentPage,
} from './test-data';

describe('MyStudentsPage', () => {
  const api = createApiStub();
  const me = signal(mentorUser());
  const currentUser = {
    user: me,
    load: vi.fn(),
    hasPermission: (p: Permission) => me().permissions.includes(p),
  };

  beforeEach(() => {
    api.reset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'my-students', component: MyStudentsPage }]),
        provideApiStub(api),
        { provide: CurrentUserService, useValue: currentUser },
      ],
    });
  });

  async function open() {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/my-students');
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    return {
      element: harness.routeNativeElement as HTMLElement,
      settle: async () => {
        await harness.fixture.whenStable();
        harness.detectChanges();
        await harness.fixture.whenStable();
      },
    };
  }

  const rowOf = (root: ParentNode, text: string) =>
    [...root.querySelectorAll('li, tr')].find((r) => r.textContent?.includes(text));

  it('shows each student with the email, the track, the start date and the marks', async () => {
    api.resolve(
      listMyStudents,
      mentoredEnrollmentPage([
        mentoredEnrollment(),
        mentoredEnrollment({
          id: 'e2',
          student: enrolledStudent({
            id: 's2',
            name: 'Dina Disabled',
            email: 'dina@example.com',
            disabled: true,
          }),
          track: { id: 't2', code: 'SWE', name: 'Software Engineering' },
        }),
        mentoredEnrollment({
          id: 'e3',
          student: enrolledStudent({
            id: 's3',
            name: 'Rick Retired',
            email: 'rick@example.com',
            studentRole: false,
          }),
        }),
      ]),
    );

    const { element } = await open();

    await vi.waitFor(() => expect(element.textContent).toContain('Sam Student'));
    const active = rowOf(element, 'Sam Student');
    const disabled = rowOf(element, 'Dina Disabled');
    const withoutRole = rowOf(element, 'Rick Retired');
    expect(active?.textContent).toContain('sam@example.com');
    expect(active?.textContent).toContain('QA · Quality Assurance');
    expect(active?.textContent).toContain('2026');
    expect(active?.textContent).not.toContain('Disabled');
    expect(active?.textContent).not.toContain('No student role');
    expect(disabled?.textContent).toContain('SWE · Software Engineering');
    expect(disabled?.textContent).toContain('Disabled');
    expect(disabled?.textContent).not.toContain('No student role');
    expect(withoutRole?.textContent).toContain('No student role');
    expect(withoutRole?.textContent).not.toContain('Disabled');
  });

  it('says when the user leads no student yet', async () => {
    api.resolve(listMyStudents, mentoredEnrollmentPage([]));

    const { element } = await open();

    await vi.waitFor(() => expect(element.textContent).toMatch(/no students|any students/i));
  });

  it('shows the list a page at a time', async () => {
    api.resolve(listMyStudents, mentoredEnrollmentPage([mentoredEnrollment()], { totalItems: 40 }));

    const { element, settle } = await open();
    await vi.waitFor(() => expect(element.textContent).toContain('Sam Student'));
    const next = element.querySelector<HTMLButtonElement>('button[aria-label="Next page"]');
    expect(next, 'the paginator').not.toBeNull();
    next?.click();
    await settle();

    await vi.waitFor(() =>
      expect(api.callsFor(listMyStudents)).toContainEqual(expect.objectContaining({ page: 1 })),
    );
  });
});
