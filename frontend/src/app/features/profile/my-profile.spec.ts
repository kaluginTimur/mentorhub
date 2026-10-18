import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Api } from '../../core/api/api';
import { updateMyProfile } from '../../core/api/fn/users/update-my-profile';
import { MeUser } from '../../core/api/models/me-user';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { MyProfile } from './my-profile';
import { meUser } from '../users/test-data';

describe('MyProfile (AC-27)', () => {
  const api = { invoke: vi.fn() };
  const me = signal<{ status: string; user?: MeUser }>({ status: 'ACTIVE', user: meUser() });
  const currentUser = { me, user: () => me().user ?? null };

  beforeEach(() => {
    api.invoke.mockReset();
    me.set({ status: 'ACTIVE', user: meUser({ roles: ['STUDENT'], permissions: [] }) });
    TestBed.configureTestingModule({
      providers: [
        { provide: Api, useValue: api },
        { provide: CurrentUserService, useValue: currentUser },
      ],
    });
  });

  async function open() {
    const fixture = TestBed.createComponent(MyProfile);
    await fixture.whenStable();
    return fixture;
  }
  const save = (el: HTMLElement) =>
    [...el.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Save');

  it('shows the profile form with the email read-only and no status or roles', async () => {
    const fixture = await open();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('h1')).toHaveLength(1);
    expect(el.querySelector('h1')?.textContent).toBe('Profile');
    expect(el.textContent).toContain('anna@example.com');
    expect(el.querySelectorAll('input')).toHaveLength(3);
    expect(el.textContent).toContain('For example +7 701 123 45 67');
    expect(el.textContent).not.toContain('Student');
    expect(el.textContent).not.toContain('Active');
    expect(el.textContent).not.toContain('Cancel');
  });

  it('saves and updates the current user, so the toolbar name changes', async () => {
    api.invoke.mockResolvedValue({
      status: 'ACTIVE',
      user: meUser({ firstName: 'Anya', phone: '+77011234567', roles: ['STUDENT'] }),
    });
    const fixture = await open();
    const el = fixture.nativeElement as HTMLElement;
    const inputs = [...el.querySelectorAll('input')] as HTMLInputElement[];
    inputs[0].value = 'Anya';
    inputs[0].dispatchEvent(new Event('input'));
    inputs[2].value = '8 701 123 45 67';
    inputs[2].dispatchEvent(new Event('input'));
    inputs[1].value = '   ';
    inputs[1].dispatchEvent(new Event('input'));
    await fixture.whenStable();
    save(el)?.click();
    await fixture.whenStable();
    expect(api.invoke).toHaveBeenCalledWith(updateMyProfile, {
      body: { firstName: 'Anya', lastName: null, phone: '8 701 123 45 67' },
    });
    expect(me().user?.firstName).toBe('Anya');
    expect(inputs[2].value).toBe('+77011234567');
  });

  it('shows the server error under the phone field', async () => {
    api.invoke.mockRejectedValue(
      new HttpErrorResponse({
        status: 400,
        error: {
          type: 'urn:mentorhub:problem:validation-failed',
          errors: [{ field: 'phone', message: 'x' }],
        },
      }),
    );
    const fixture = await open();
    const el = fixture.nativeElement as HTMLElement;
    save(el)?.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.textContent).toContain('Enter a valid phone number');
  });
});
