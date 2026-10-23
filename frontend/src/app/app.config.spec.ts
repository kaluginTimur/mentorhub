import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { oidcConfigLoader } from './app.config';

describe('oidcConfigLoader', () => {
  const clientConfig = { issuer: 'http://kc/realms/m', clientId: 'web', scope: 'openid' };

  function setup() {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const loader = oidcConfigLoader(TestBed.inject(HttpClient));
    return { loader, controller: TestBed.inject(HttpTestingController) };
  }

  it('maps the client config and does not load it again, so sign-out works while the API is down', async () => {
    const { loader, controller } = setup();
    const first = firstValueFrom(loader.loadConfigs());
    controller.expectOne('/api/v1/system/client-config').flush(clientConfig);
    const [config] = await first;
    expect(config).toMatchObject({
      authority: clientConfig.issuer,
      clientId: 'web',
      autoUserInfo: false,
      renewTimeBeforeTokenExpiresInSeconds: 30,
    });

    await firstValueFrom(loader.loadConfigs());
    controller.expectNone('/api/v1/system/client-config');
  });

  it('reports the client config to the callback', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const onConfig = vi.fn();
    const loader = oidcConfigLoader(TestBed.inject(HttpClient), onConfig);
    const first = firstValueFrom(loader.loadConfigs());
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/system/client-config')
      .flush({ ...clientConfig, invitationEmails: true });
    await first;
    expect(onConfig).toHaveBeenCalledWith(expect.objectContaining({ invitationEmails: true }));
  });

  it('retries after a failure', async () => {
    const { loader, controller } = setup();
    const failed = firstValueFrom(loader.loadConfigs()).catch(() => 'failed');
    controller
      .expectOne('/api/v1/system/client-config')
      .flush(null, { status: 500, statusText: 'x' });
    expect(await failed).toBe('failed');

    const second = firstValueFrom(loader.loadConfigs());
    controller.expectOne('/api/v1/system/client-config').flush(clientConfig);
    expect(await second).toHaveLength(1);
  });
});
