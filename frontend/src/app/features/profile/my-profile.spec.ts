import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { updateMyProfile } from '../../core/api/fn/users/update-my-profile';
import { Me } from '../../core/api/models/me';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { MyProfile } from './my-profile';
import { meUser } from '../users/test-data';

describe('MyProfile', () => {
  const api = createApiStub();
  const me = signal<Me>({ status: 'ACTIVE', user: meUser() });
  const currentUser = { me, user: () => me().user ?? null };

  beforeEach(() => {
    api.reset();
    me.set({ status: 'ACTIVE', user: meUser({ roles: ['STUDENT'], permissions: [] }) });
    TestBed.configureTestingModule({
      providers: [provideApiStub(api), { provide: CurrentUserService, useValue: currentUser }],
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
    expect(el.textContent?.match(/anna@example\.com/g)).toHaveLength(1);
    expect(el.querySelectorAll('input')).toHaveLength(3);
    expect(el.textContent).toContain('For example +7 701 123 45 67');
    expect(el.textContent).not.toContain('Student');
    expect(el.textContent).not.toContain('Active');
    expect(el.textContent).not.toContain('Cancel');
  });

  it('saves, updates the current user so the toolbar name changes, and shows no status or roles', async () => {
    api.resolve(updateMyProfile, {
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
    expect(api.callsFor(updateMyProfile)).toEqual([
      {
        body: {
          firstName: 'Anya',
          lastName: null,
          phone: '8 701 123 45 67',
        },
      },
    ]);
    expect(me().user?.firstName).toBe('Anya');
    expect(inputs[2].value).toBe('+77011234567');
    expect(document.querySelector('app-toast-host')?.textContent).toContain('Profile saved');
    expect(el.textContent).not.toContain('Student');
    expect(el.textContent).not.toContain('Active');
  });
});
