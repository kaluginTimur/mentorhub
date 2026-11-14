import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ToastService } from '../../shared/toast/toast.service';
import { Me } from '../api/models/me';
import { Problem } from '../api/models/problem';
import { AuthService } from './auth.service';
import { CurrentUserService } from './current-user.service';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  const auth = { handleUnauthorized: vi.fn(), confirmSignIn: vi.fn() };
  const currentUser: { load: ReturnType<typeof vi.fn>; failedUrl: string | null } = {
    load: vi.fn(),
    failedUrl: null,
  };
  const router = { navigateByUrl: vi.fn(() => Promise.resolve(true)), url: '/students' };
  const toast = { show: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: CurrentUserService, useValue: currentUser },
        { provide: Router, useValue: router },
        { provide: ToastService, useValue: toast },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  function fail(url: string, status: number, type?: string): void {
    http.get(url).subscribe({ error: () => undefined });
    controller
      .expectOne(url)
      .flush(type ? ({ type, title: 't', status } satisfies Problem) : null, {
        status,
        statusText: 'x',
      });
  }

  it('starts sign-in again on 401', () => {
    fail('/api/v1/me', 401);
    expect(auth.handleUnauthorized).toHaveBeenCalledOnce();
  });

  it('ignores 401 from a public endpoint', () => {
    fail('/api/v1/system/info', 401);
    expect(auth.handleUnauthorized).not.toHaveBeenCalled();
  });

  it('confirms the sign-in after a successful API response', () => {
    http.get('/api/v1/me').subscribe();
    controller.expectOne('/api/v1/me').flush({});
    expect(auth.confirmSignIn).toHaveBeenCalledOnce();
  });

  it('PERM-7.1 shows a toast on 403 forbidden and stays on the page', async () => {
    fail('/api/v1/users', 403, 'urn:mentorhub:problem:forbidden');
    await vi.waitFor(() =>
      expect(toast.show).toHaveBeenCalledWith("You don't have permission to do this."),
    );
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it.each<[string, Me, string]>([
    ['urn:mentorhub:problem:access-ended', { status: 'DISABLED' }, '/access/ended'],
    ['urn:mentorhub:problem:not-invited', { status: 'NOT_INVITED' }, '/access/invitation-only'],
  ])('IDN-10.4 reloads the caller and opens the access screen on %s', async (type, me, path) => {
    currentUser.load.mockResolvedValue(me);
    fail('/api/v1/users', 403, type);
    await vi.waitFor(() => expect(router.navigateByUrl).toHaveBeenCalledWith(path));
    expect(toast.show).not.toHaveBeenCalled();
  });

  it('opens the unavailable state, keeping the address, when reloading the caller fails', async () => {
    currentUser.load.mockRejectedValue(new Error('down'));
    fail('/api/v1/users', 403, 'urn:mentorhub:problem:access-ended');
    await vi.waitFor(() =>
      expect(router.navigateByUrl).toHaveBeenCalledWith('/unavailable', {
        skipLocationChange: true,
      }),
    );
    expect(currentUser.failedUrl).toBe('/students');
  });

  it('does not navigate when reloading the caller fails with 401', async () => {
    currentUser.load.mockRejectedValue(new HttpErrorResponse({ status: 401 }));
    fail('/api/v1/users', 403, 'urn:mentorhub:problem:access-ended');
    await vi.waitFor(() => expect(currentUser.load).toHaveBeenCalled());
    await Promise.resolve();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
