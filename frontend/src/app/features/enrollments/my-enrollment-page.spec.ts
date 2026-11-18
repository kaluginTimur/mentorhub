import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { listMyEnrollments } from '../../core/api/fn/enrollments/list-my-enrollments';
import { MeUser } from '../../core/api/models/me-user';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { contact, myEnrollment } from './test-data';
import { MyEnrollmentPage } from './my-enrollment-page';

describe('MyEnrollmentPage', () => {
  const api = createApiStub();
  const student: MeUser = {
    id: 'u1',
    email: 'sam@example.com',
    firstName: 'Sam',
    lastName: 'Student',
    phone: null,
    roles: ['STUDENT'],
    permissions: [],
  };

  beforeEach(() => {
    api.reset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'my-enrollment', component: MyEnrollmentPage }]),
        provideApiStub(api),
        {
          provide: CurrentUserService,
          useValue: { user: signal(student), hasPermission: () => false },
        },
      ],
    });
  });

  async function open() {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/my-enrollment');
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    return harness.routeNativeElement as HTMLElement;
  }

  it('shows the track, the start date and the coordinators to contact with their emails and phones', async () => {
    api.resolve(listMyEnrollments, {
      items: [
        myEnrollment({
          coordinators: [
            contact(),
            contact({ name: 'Boris Coordinator', email: 'boris@example.com', phone: null }),
          ],
        }),
      ],
    });

    const element = await open();

    await vi.waitFor(() => expect(element.textContent).toContain('Quality Assurance'));
    expect(element.textContent).toContain('QA');
    expect(element.textContent).toContain('2026');
    expect(element.textContent).toContain('Clara Coordinator');
    expect(element.textContent).toContain('clara@example.com');
    expect(element.textContent).toContain('+77011234567');
    expect(element.textContent).toContain('Boris Coordinator');
    expect(element.textContent).toContain('boris@example.com');
    expect(element.textContent).not.toContain('This track has no coordinator to contact');
  });

  it('says when the track has no coordinator to contact', async () => {
    api.resolve(listMyEnrollments, { items: [myEnrollment({ coordinators: [] })] });

    const element = await open();

    await vi.waitFor(() =>
      expect(element.textContent).toContain('This track has no coordinator to contact'),
    );
    expect(element.textContent).toContain('Quality Assurance');
  });

  it('says when the user is not enrolled yet', async () => {
    api.resolve(listMyEnrollments, { items: [] });

    const element = await open();

    await vi.waitFor(() => expect(element.textContent).toContain('You are not enrolled yet'));
  });

  it('shows the mentors to contact with their emails and phones', async () => {
    api.resolve(listMyEnrollments, {
      items: [
        myEnrollment({
          mentors: [
            contact({ name: 'Mia Mentor', email: 'mia@example.com', phone: '+77019998877' }),
            contact({ name: 'Omar Mentor', email: 'omar@example.com', phone: null }),
          ],
        }),
      ],
    });

    const element = await open();

    await vi.waitFor(() => expect(element.textContent).toContain('Mia Mentor'));
    expect(element.textContent).toContain('mia@example.com');
    expect(element.textContent).toContain('+77019998877');
    expect(element.textContent).toContain('Omar Mentor');
    expect(element.textContent).toContain('omar@example.com');
    expect(element.textContent).toContain('Clara Coordinator');
    expect(element.textContent).not.toMatch(/no mentor/i);
  });

  it('says when the enrollment has no mentor to contact, apart from the coordinators', async () => {
    api.resolve(listMyEnrollments, { items: [myEnrollment({ mentors: [] })] });

    const element = await open();

    await vi.waitFor(() => expect(element.textContent).toMatch(/no mentor/i));
    expect(element.textContent).toContain('Clara Coordinator');
    expect(element.textContent).not.toContain('no coordinator to contact');
  });

  it('says when the track has no coordinator but the enrollment has a mentor', async () => {
    api.resolve(listMyEnrollments, {
      items: [myEnrollment({ coordinators: [], mentors: [contact({ name: 'Mia Mentor' })] })],
    });

    const element = await open();

    await vi.waitFor(() =>
      expect(element.textContent).toContain('This track has no coordinator to contact'),
    );
    expect(element.textContent).toContain('Mia Mentor');
    expect(element.textContent).not.toMatch(/no mentor/i);
  });
});
