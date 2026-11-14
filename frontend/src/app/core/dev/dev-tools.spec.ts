import {
  HttpClient,
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, Provider, signal } from '@angular/core';
import { ComponentFixture, DeferBlockBehavior, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import {
  devToolsInterceptors as developmentInterceptors,
  devToolsProviders as developmentProviders,
} from '../../../environments/dev-tools';
import {
  devToolsInterceptors as productionInterceptors,
  devToolsProviders as productionProviders,
} from '../../../environments/dev-tools.prod';
import { App } from '../../app';
import { Problem } from '../api/models/problem';
import { AuthService } from '../auth/auth.service';

interface DevTools {
  providers: Provider[];
  interceptors: HttpInterceptorFn[];
}

const development: DevTools = {
  providers: developmentProviders,
  interceptors: developmentInterceptors,
};
const production: DevTools = {
  providers: productionProviders,
  interceptors: productionInterceptors,
};

@Component({ template: '<h1>A screen</h1>' })
class Screen {}

describe('dev tools', () => {
  const startupError = signal(false);

  function configure(tools: DevTools): void {
    startupError.set(false);
    TestBed.configureTestingModule({
      imports: [App],
      deferBlockBehavior: DeferBlockBehavior.Playthrough,
      providers: [
        provideRouter([{ path: '', component: Screen }]),
        provideHttpClient(withInterceptors(tools.interceptors)),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { startupError, redirecting: signal(false) } },
        ...tools.providers,
      ],
    });
  }

  async function showScreen(): Promise<ComponentFixture<App>> {
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl('/');
    await fixture.whenStable();
    return fixture;
  }

  async function showUnavailable(fixture: ComponentFixture<App>): Promise<void> {
    startupError.set(true);
    await fixture.whenStable();
    expect(element(fixture).textContent).toContain('MentorHub is unavailable');
  }

  function element(fixture: ComponentFixture<App>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function failureSwitch(fixture: ComponentFixture<App>): Element | null {
    return element(fixture).querySelector('[data-testid="failure-switch"]');
  }

  function indicator(fixture: ComponentFixture<App>): Element | null {
    return element(fixture).querySelector('[data-testid="failure-indicator"]');
  }

  it('DEV-3.1 The switch is absent outside development mode', async () => {
    configure(production);
    const fixture = await showScreen();
    expect(element(fixture).textContent).toContain('A screen');
    expect(failureSwitch(fixture)).toBeNull();

    await showUnavailable(fixture);
    expect(failureSwitch(fixture)).toBeNull();
  });

  it('DEV-3.2 Requests are not altered outside development mode', () => {
    configure(production);
    const http = TestBed.inject(HttpClient);
    const backend = TestBed.inject(HttpTestingController);
    const responses: HttpResponse<unknown>[] = [];
    const errors: HttpErrorResponse[] = [];

    http
      .post('/api/v1/invitations', { email: 'anna@example.com' }, { observe: 'response' })
      .subscribe((response) => responses.push(response));
    const sent = backend.expectOne('/api/v1/invitations');
    expect(sent.request.method).toBe('POST');
    expect(sent.request.body).toEqual({ email: 'anna@example.com' });
    expect(sent.request.headers.keys()).toEqual([]);
    sent.flush({ id: 'i1' }, { status: 201, statusText: 'Created' });
    expect(responses.map((response) => [response.status, response.body])).toEqual([
      [201, { id: 'i1' }],
    ]);

    http.get('/api/v1/me').subscribe({ error: (error: HttpErrorResponse) => errors.push(error) });
    const problem: Problem = { type: 'about:blank', title: 'Service Unavailable', status: 503 };
    backend
      .expectOne('/api/v1/me')
      .flush(problem, { status: 503, statusText: 'Service Unavailable' });
    expect(errors.map((error) => [error.status, error.error])).toEqual([[503, problem]]);
    backend.verify();
  });

  it.each([
    ['slow', /slow/i],
    ['server-error', /server[\s-]error/i],
    ['offline', /offline/i],
  ])('DEV-3.3 A failure mode on is shown (%s)', async (mode, name) => {
    configure(development);
    const fixture = await showScreen();
    expect(indicator(fixture)).toBeNull();
    const select = failureSwitch(fixture);
    expect(select).toBeInstanceOf(HTMLSelectElement);

    (select as HTMLSelectElement).value = mode;
    select?.dispatchEvent(new Event('input'));
    select?.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(indicator(fixture)?.textContent).toMatch(name);

    await showUnavailable(fixture);
    expect(indicator(fixture)?.textContent).toMatch(name);

    TestBed.resetTestingModule();
    configure(development);
    const reloaded = await showScreen();
    expect(indicator(reloaded)).toBeNull();
    expect((failureSwitch(reloaded) as HTMLSelectElement | null)?.value).toBe('normal');
  });
});
