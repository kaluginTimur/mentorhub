import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Api } from '../../core/api/api';
import { AuthService } from '../../core/auth/auth.service';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { displayName } from '../../core/auth/user-name';
import { avatarColor } from '../../shared/avatar/avatar';
import { Home } from './home';
import { Shell } from './shell';

describe('shell', () => {
  const auth = { signOut: vi.fn() };
  const user = signal({
    id: 'u1',
    email: 'anna@example.com',
    firstName: 'Anna' as string | null,
    lastName: 'Petrova' as string | null,
    roles: ['MENTOR'],
    permissions: [],
    phone: null,
  });
  const currentUser = {
    user,
    name: () => displayName(user()),
    hasPermission: (p: string) => (user().permissions as string[]).includes(p),
  };
  const api = { invoke: vi.fn() };
  const accountTrigger = (element: HTMLElement) =>
    element.querySelector<HTMLButtonElement>('button[aria-label^="Account menu for"]');

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'users', component: Home },
          { path: 'invitations', component: Home },
        ]),
        { provide: AuthService, useValue: auth },
        { provide: CurrentUserService, useValue: currentUser },
        { provide: Api, useValue: api },
      ],
    });
  });

  it('shows the email in the account menu and signs out', async () => {
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    (accountTrigger(element) as HTMLButtonElement).click();
    await fixture.whenStable();
    const overlay = document.body.textContent;
    expect(overlay).toContain('anna@example.com');
    const signOut = [...document.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === 'Sign out',
    );
    signOut?.click();
    expect(auth.signOut).toHaveBeenCalledOnce();
  });

  it.each([
    ['both names', 'Anna', 'Petrova', 'Anna Petrova'],
    ['only the first name', 'Anna', null, 'Anna'],
    ['only the last name', null, 'Petrova', 'Petrova'],
    ['the email when no name is set', null, null, 'anna@example.com'],
  ])('IDN-11.1 shows %s in the account menu', async (_case, firstName, lastName, expected) => {
    user.update((u) => ({ ...u, firstName, lastName }));
    try {
      const fixture = TestBed.createComponent(Shell);
      await fixture.whenStable();
      const trigger = accountTrigger(fixture.nativeElement);
      expect(trigger?.getAttribute('aria-label')).toBe(`Account menu for ${expected}`);
      trigger?.click();
      await fixture.whenStable();
      const menu = [...document.querySelectorAll('[role="menu"]')].at(-1);
      // An element holding exactly the name, so "Anna null" would not pass as "Anna".
      const texts = [...(menu?.querySelectorAll('*') ?? [])].map((e) => e.textContent?.trim());
      expect(texts).toContain(expected);
    } finally {
      user.update((u) => ({ ...u, firstName: 'Anna', lastName: 'Petrova' }));
    }
  });

  it('greets the user and shows the version on the home page', async () => {
    api.invoke.mockResolvedValue({ name: 'MentorHub', version: '0.1.0' });
    const fixture = TestBed.createComponent(Home);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('h1')).toHaveLength(1);
    expect(element.querySelector('h1')?.textContent).toBe('Welcome, Anna');
    expect(element.querySelector('footer')?.textContent).toContain('MentorHub 0.1.0');
  });

  it('hides the version when the system info cannot be loaded', async () => {
    api.invoke.mockRejectedValue(new Error('down'));
    const fixture = TestBed.createComponent(Home);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('h1')?.textContent).toBe('Welcome, Anna');
    expect(element.querySelector('footer')?.textContent?.trim()).toBe('');
  });

  it('wraps the toolbar in a header landmark', async () => {
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('header mat-toolbar'),
    ).not.toBeNull();
  });

  it('shows the Users link only with USERS_READ', async () => {
    const link = (element: HTMLElement) =>
      [...element.querySelectorAll('a')].find((a) => a.textContent?.trim() === 'Users');
    const original = user();
    user.set({ ...original, permissions: ['USERS_READ'] as never[] });
    let fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(link(fixture.nativeElement)?.getAttribute('href')).toBe('/users');

    user.set({ ...original, permissions: [] });
    fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(link(fixture.nativeElement)).toBeUndefined();
  });

  it('shows the Invitations link only with USERS_INVITE', async () => {
    const link = (element: HTMLElement) =>
      [...element.querySelectorAll('a')].find((a) => a.textContent?.trim() === 'Invitations');
    const original = user();
    user.set({ ...original, permissions: ['USERS_READ', 'USERS_INVITE'] as never[] });
    let fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(link(fixture.nativeElement)?.getAttribute('href')).toBe('/invitations');

    user.set({ ...original, permissions: ['USERS_READ'] as never[] });
    fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(link(fixture.nativeElement)).toBeUndefined();
    user.set(original);
  });

  it('has Profile above Sign out in the account menu', async () => {
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    accountTrigger(fixture.nativeElement)?.click();
    await fixture.whenStable();
    const items = [...document.querySelectorAll('[mat-menu-item]')].map((i) =>
      i.textContent?.trim(),
    );
    expect(items).toEqual(['Profile', 'Sign out']);
    expect(document.querySelector('a[mat-menu-item]')?.getAttribute('href')).toBe('/profile');
  });

  it('links the application name to the home page', async () => {
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    const brand = (fixture.nativeElement as HTMLElement).querySelector('a.brand');
    expect(brand?.textContent?.trim()).toBe('MentorHub');
    expect(brand?.getAttribute('href')).toBe('/');
  });

  it('shows initials and a color that follows the user id on the account button', async () => {
    const hueOf = async () => {
      const fixture = TestBed.createComponent(Shell);
      await fixture.whenStable();
      const avatar = (fixture.nativeElement as HTMLElement).querySelector('.avatar') as HTMLElement;
      return [avatar.textContent, avatar.style.getPropertyValue('--avatar-color')];
    };
    const [text, hue] = await hueOf();
    expect(text).toBe('AP');
    expect(hue).toBe(avatarColor('u1'));
    expect(hue).toMatch(/^var\(--app-avatar-(10|[1-9])\)$/);
    expect((await hueOf())[1]).toBe(hue);
  });

  describe('color scheme', () => {
    afterEach(() => {
      localStorage.clear();
      document.documentElement.style.colorScheme = '';
    });

    it('offers system, light and dark, with system chosen by default', async () => {
      const fixture = TestBed.createComponent(Shell);
      await fixture.whenStable();
      const trigger = (fixture.nativeElement as HTMLElement).querySelector(
        'button.theme',
      ) as HTMLButtonElement;
      expect(trigger.getAttribute('aria-label')).toBe('Color scheme: System');
      trigger.click();
      await fixture.whenStable();
      const items = [...document.querySelectorAll('[mat-menu-item]')];
      expect(items.map((i) => i.textContent?.replace('check', '').trim())).toEqual([
        'System',
        'Light',
        'Dark',
      ]);
      expect(items.map((i) => i.getAttribute('aria-checked'))).toEqual(['true', 'false', 'false']);
    });

    it('applies and keeps the chosen scheme', async () => {
      const fixture = TestBed.createComponent(Shell);
      await fixture.whenStable();
      (fixture.nativeElement as HTMLElement)
        .querySelector<HTMLButtonElement>('button.theme')
        ?.click();
      await fixture.whenStable();
      [...document.querySelectorAll<HTMLElement>('[mat-menu-item]')]
        .find((i) => i.textContent?.includes('Dark'))
        ?.click();
      await fixture.whenStable();
      expect(document.documentElement.style.colorScheme).toBe('dark');
      expect(localStorage.getItem('mentorhub.theme')).toBe('dark');
      expect(
        (fixture.nativeElement as HTMLElement)
          .querySelector('button.theme')
          ?.getAttribute('aria-label'),
      ).toBe('Color scheme: Dark');
    });
  });

  describe('sections menu for narrow screens', () => {
    const sectionLinks = async (permissions: string[]) => {
      const original = user();
      user.set({ ...original, permissions: permissions as never[] });
      try {
        const fixture = TestBed.createComponent(Shell);
        await fixture.whenStable();
        const trigger = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
          'button.sections',
        );
        if (!trigger) return null;
        trigger.click();
        await fixture.whenStable();
        return [...document.querySelectorAll('a[mat-menu-item]')].map((a) => [
          a.textContent?.trim(),
          a.getAttribute('href'),
        ]);
      } finally {
        user.set(original);
      }
    };

    it('holds the links the permissions allow', async () => {
      expect(await sectionLinks(['USERS_READ', 'USERS_INVITE'])).toEqual([
        ['Users', '/users'],
        ['Invitations', '/invitations'],
      ]);
    });

    it('holds only Users without USERS_INVITE', async () => {
      expect(await sectionLinks(['USERS_READ'])).toEqual([['Users', '/users']]);
    });

    it('marks the current section with aria-current', async () => {
      await TestBed.inject(Router).navigateByUrl('/invitations');
      await sectionLinks(['USERS_READ', 'USERS_INVITE']);
      const current = [...document.querySelectorAll('a[mat-menu-item]')].filter(
        (a) => a.getAttribute('aria-current') === 'page',
      );
      expect(current.map((a) => a.getAttribute('href'))).toEqual(['/invitations']);
    });

    it('comes before the application name in the bar, then theme and account', async () => {
      const original = user();
      user.set({ ...original, permissions: ['USERS_READ'] as never[] });
      try {
        const fixture = TestBed.createComponent(Shell);
        await fixture.whenStable();
        const bar = (fixture.nativeElement as HTMLElement).querySelector('mat-toolbar')!;
        const order = [
          ...bar.querySelectorAll('button.sections, a.brand, button.theme, button.account'),
        ].map((el) =>
          ['sections', 'brand', 'theme', 'account'].find((c) => el.classList.contains(c)),
        );
        expect(order).toEqual(['sections', 'brand', 'theme', 'account']);
      } finally {
        user.set(original);
      }
    });

    it('is absent when no section is allowed', async () => {
      expect(await sectionLinks([])).toBeNull();
    });
  });
});
