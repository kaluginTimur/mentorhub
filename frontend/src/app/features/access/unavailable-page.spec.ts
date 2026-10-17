import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { UnavailablePage } from './unavailable-page';

describe('UnavailablePage', () => {
  const router = { navigateByUrl: vi.fn() };
  const currentUser = { failedUrl: '/students' as string | null };

  async function open(): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      imports: [UnavailablePage],
      providers: [
        { provide: Router, useValue: router },
        { provide: CurrentUserService, useValue: currentUser },
      ],
    });
    const fixture = TestBed.createComponent(UnavailablePage);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('Retry repeats the failed navigation', async () => {
    const element = await open();
    element.querySelector('button')?.click();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/students', {
      onSameUrlNavigation: 'reload',
    });
  });

  it('Retry goes home when there is nothing to repeat', async () => {
    currentUser.failedUrl = null;
    const element = await open();
    element.querySelector('button')?.click();
    expect(router.navigateByUrl).toHaveBeenLastCalledWith('/', { onSameUrlNavigation: 'reload' });
  });
});
