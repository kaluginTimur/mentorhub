import {
  HttpClient,
  HttpErrorResponse,
  HttpResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { devToolsInterceptors, devToolsProviders } from '../../../environments/dev-tools';
import { Problem } from '../api/models/problem';
import { UserPage } from '../api/models/user-page';
import { DEV_TOOL } from './dev-tool';

// The failure modes as a developer uses them: chosen with the switch, seen on the requests the client makes.
describe('failure modes', () => {
  const KEYCLOAK_TOKEN = 'http://localhost:8180/realms/mentorhub/protocol/openid-connect/token';

  let fixture: ComponentFixture<unknown>;
  let http: HttpClient;
  let backend: HttpTestingController;
  let responses: HttpResponse<unknown>[];
  let errors: HttpErrorResponse[];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors(devToolsInterceptors)),
        provideHttpClientTesting(),
        ...devToolsProviders,
      ],
    });
    fixture = TestBed.createComponent(TestBed.inject(DEV_TOOL));
    await fixture.whenStable();
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    responses = [];
    errors = [];
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  async function choose(mode: string): Promise<void> {
    const select = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>(
      '[data-testid="failure-switch"]',
    );
    expect(select).not.toBeNull();
    select!.value = mode;
    select!.dispatchEvent(new Event('input'));
    select!.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  function get(url: string): void {
    http.get(url, { observe: 'response' }).subscribe({
      next: (response) => responses.push(response),
      error: (error: HttpErrorResponse) => errors.push(error),
    });
  }

  it('answers an API request with a generic 500 problem without sending it in server-error mode', async () => {
    await choose('server-error');

    get('/api/v1/users');

    backend.expectNone('/api/v1/users');
    expect(responses).toEqual([]);
    expect(errors.map((error) => error.status)).toEqual([500]);
    expect(errors[0].error).toMatchObject({
      type: 'about:blank',
      status: 500,
    } satisfies Partial<Problem>);
    backend.verify();
  });

  it('fails an API request with status 0 without sending it in offline mode', async () => {
    await choose('offline');

    get('/api/v1/me');

    backend.expectNone('/api/v1/me');
    expect(responses).toEqual([]);
    expect(errors.map((error) => error.status)).toEqual([0]);
    backend.verify();
  });

  it('fails the client configuration too, which sign-in needs', async () => {
    await choose('server-error');

    get('/api/v1/system/client-config');

    backend.expectNone('/api/v1/system/client-config');
    expect(errors.map((error) => error.status)).toEqual([500]);
  });

  it('sends an API request after 3 s in slow mode and passes its response through', async () => {
    await choose('slow');
    vi.useFakeTimers();

    get('/api/v1/users');
    vi.advanceTimersByTime(2_900);
    backend.expectNone('/api/v1/users');

    const emptyPage: UserPage = { items: [], page: 0, size: 25, totalItems: 0 };
    vi.advanceTimersByTime(100);
    backend.expectOne('/api/v1/users').flush(emptyPage, { status: 200, statusText: 'OK' });

    expect(responses.map((response) => [response.status, response.body])).toEqual([
      [200, emptyPage],
    ]);
    expect(errors).toEqual([]);
    backend.verify();
  });

  it.each(['server-error', 'offline', 'slow'])(
    'leaves requests to Keycloak alone in %s mode, so sign-in keeps working',
    async (mode) => {
      await choose(mode);

      http
        .post(KEYCLOAK_TOKEN, 'grant_type=refresh_token', { observe: 'response' })
        .subscribe((response) => responses.push(response));

      backend.expectOne(KEYCLOAK_TOKEN).flush({ access_token: 't' });
      expect(responses.map((response) => response.body)).toEqual([{ access_token: 't' }]);
      backend.verify();
    },
  );

  it('sends requests again and hides the indicator once normal is chosen back', async () => {
    await choose('server-error');
    await choose('normal');

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="failure-indicator"]'),
    ).toBeNull();
    const emptyPage: UserPage = { items: [], page: 0, size: 25, totalItems: 0 };
    get('/api/v1/users');
    backend.expectOne('/api/v1/users').flush(emptyPage);
    expect(responses.map((response) => response.body)).toEqual([emptyPage]);
    expect(errors).toEqual([]);
    backend.verify();
  });
});
