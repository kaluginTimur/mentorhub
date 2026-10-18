import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { getAccessToken: () => Promise.resolve('tok') } },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  async function authorizationOf(url: string): Promise<string | null> {
    http.get(url).subscribe();
    await Promise.resolve();
    await Promise.resolve();
    const request = controller.expectOne(url);
    const header = request.request.headers.get('Authorization');
    request.flush({});
    return header;
  }

  it('attaches the token to a non-public API request', async () => {
    expect(await authorizationOf('/api/v1/me')).toBe('Bearer tok');
  });

  it('does not attach the token to public API endpoints', async () => {
    expect(await authorizationOf('/api/v1/system/info')).toBeNull();
    expect(await authorizationOf('/api/v1/system/client-config')).toBeNull();
    expect(await authorizationOf('/api/v1/invitations/preview')).toBeNull();
  });

  it('attaches the token to accept and the management endpoints', async () => {
    expect(await authorizationOf('/api/v1/invitations/accept')).toBe('Bearer tok');
    expect(await authorizationOf('/api/v1/invitations')).toBe('Bearer tok');
  });

  it('does not attach the token to other URLs', async () => {
    expect(await authorizationOf('https://example.org/api/v1/me')).toBeNull();
    expect(await authorizationOf('/assets/data.json')).toBeNull();
    expect(await authorizationOf('http://localhost:8180/realms/mentorhub/x')).toBeNull();
  });
});
