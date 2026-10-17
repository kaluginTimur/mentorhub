import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { AccessScreen } from './access-screen';

describe('AccessScreen', () => {
  const auth = { signOut: vi.fn() };

  async function open(access: string) {
    TestBed.configureTestingModule({
      imports: [AccessScreen],
      providers: [
        { provide: AuthService, useValue: auth },
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
});
