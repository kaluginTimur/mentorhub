import { signal } from '@angular/core';
import { DeferBlockBehavior, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { App } from './app';
import { AuthService } from './core/auth/auth.service';

describe('App', () => {
  const startupError = signal(false);
  const redirecting = signal(false);

  afterEach(() => vi.restoreAllMocks());

  beforeEach(() => {
    startupError.set(false);
    redirecting.set(false);
    TestBed.configureTestingModule({
      imports: [App],
      deferBlockBehavior: DeferBlockBehavior.Playthrough,
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { startupError, redirecting } },
      ],
    });
  });

  it('shows the startup error as an alert with a Retry action (AC-28)', async () => {
    startupError.set(true);
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'MentorHub is unavailable. Try again later.',
    );
    expect(element.querySelector('button')?.textContent).toContain('Retry');
  });

  it('shows a loading status inside a landmark while sign-in redirects', async () => {
    redirecting.set(true);
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('main [role="status"]')?.textContent).toContain('Loading');
    expect(element.querySelector('[aria-busy="true"]')).not.toBeNull();
  });

  it('hides the loading status after the initial navigation ends', async () => {
    const router = TestBed.inject(Router);
    router.resetConfig([{ path: '', children: [] }]);
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="status"]')).not.toBeNull();
    await router.navigateByUrl('/');
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[aria-busy="true"]')).toBeNull();
  });

  it('hides the loading status after a failed initial navigation', async () => {
    const router = TestBed.inject(Router);
    router.resetConfig([
      { path: '', canActivate: [() => Promise.reject(new Error('x'))], children: [] },
    ]);
    const fixture = TestBed.createComponent(App);
    await router.navigateByUrl('/').catch(() => undefined);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
  });

  it('clears the redirecting state when restored from the back/forward cache', async () => {
    redirecting.set(true);
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }));
    expect(redirecting()).toBe(false);
  });

  it('Retry reloads without the sign-in callback parameters', async () => {
    startupError.set(true);
    const assign = vi.fn();
    vi.spyOn(globalThis, 'location', 'get').mockReturnValue({
      href: 'http://localhost:4200/students?code=a&state=b&tab=1',
      origin: 'http://localhost:4200',
      assign,
    } as unknown as Location);
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    (fixture.nativeElement as HTMLElement).querySelector('button')?.click();
    expect(assign).toHaveBeenCalledWith('/students?tab=1');
  });
});
