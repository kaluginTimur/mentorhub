import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { AccessChannel } from '../../core/auth/access-channel';
import { AuthService } from '../../core/auth/auth.service';
import { AccessScreen } from './access-screen';

describe('AccessScreen', () => {
  const auth = { signOut: vi.fn() };
  const router = { navigateByUrl: vi.fn() };
  let changed: () => void;
  const unsubscribe = vi.fn();
  const channel = {
    onChanged: vi.fn((handler: () => void) => {
      changed = handler;
      return unsubscribe;
    }),
  };

  beforeEach(() => vi.clearAllMocks());

  async function open(access: string) {
    TestBed.configureTestingModule({
      imports: [AccessScreen],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: AccessChannel, useValue: channel },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { access } } } },
      ],
    });
    const fixture = TestBed.createComponent(AccessScreen);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it.each([
    ['invitation-only', 'Access by invitation only', 'by invitation'],
    ['ended', 'Your access has ended', 'Contact the training center'],
    ['pending', 'Your access is being set up', 'Contact an administrator.'],
  ])('shows the %s message (AC-3, AC-5, AC-7)', async (access, title, text) => {
    const element = await open(access);
    const headings = element.querySelectorAll('h1');
    expect(headings).toHaveLength(1);
    expect(headings[0].textContent).toBe(title);
    expect(element.textContent).toContain(text);
  });

  it('focuses the title and signs out on request', async () => {
    const element = await open('ended');
    expect(document.activeElement).toBe(element.querySelector('h1'));
    element.querySelector('button')?.click();
    expect(auth.signOut).toHaveBeenCalledOnce();
  });

  it('re-checks access when another tab reports a change', async () => {
    await open('invitation-only');
    changed();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('re-checks access when the tab becomes visible, not when it is hidden', async () => {
    await open('pending');
    const state = vi.spyOn(document, 'visibilityState', 'get');
    state.mockReturnValue('hidden');
    document.dispatchEvent(new Event('visibilitychange'));
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    state.mockReturnValue('visible');
    document.dispatchEvent(new Event('visibilitychange'));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
    state.mockRestore();
  });

  it('stops listening once the screen is gone', async () => {
    TestBed.configureTestingModule({
      imports: [AccessScreen],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: AccessChannel, useValue: channel },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { access: 'ended' } } } },
      ],
    });
    const fixture = TestBed.createComponent(AccessScreen);
    await fixture.whenStable();
    fixture.destroy();
    expect(unsubscribe).toHaveBeenCalledOnce();
    const state = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    document.dispatchEvent(new Event('visibilitychange'));
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    state.mockRestore();
  });
});
