# Frontend development

Angular 22 (standalone, zoneless, signals), Angular Material (M3), Vitest, angular-eslint, Prettier, ng-openapi-gen. Node 24 via nvm (`frontend/.nvmrc`). Commands run from `frontend/`; paths in this document are relative to the repository root.

## Structure

- `frontend/src/app/core/`: infrastructure (API client, auth, interceptors).
- `frontend/src/app/shared/`: reusable components (`loading`, `unavailable`).
- `frontend/src/app/features/<feature>/`: one lazy-loaded route tree per feature.

## API client (ADR 0002)

- `ng-openapi-gen` generates the client from `api/openapi.yaml` into `frontend/src/app/core/api/` (`npm run generate:api`, run automatically before `start`, `build`, `test` and `lint`). It is gitignored; never edit or commit it.
- Calls: `inject(Api).invoke(fn, params)` with functions from `frontend/src/app/core/api/fn/**`. Load data in components with `resource()`, as in `frontend/src/app/features/shell/home.ts`. Guard `value()` with `hasValue()` (or check `error()` first): in the error state `value()` throws.
- Nullable properties (`type: [string, 'null']`) are generated as required `(string | null)`; an optional property that may be absent (for example `Me.user`) is generated as `user?: MeUser`. Handle absent and `null` separately.
- Enums are generated as string unions (`Role`, `Permission`), with `*-array.ts` helpers listing the values.
- `frontend/src/app/app.config.ts` sets `provideApiConfiguration('')` (see [Pitfalls](#pitfalls)). In dev, `/api` is proxied to `:8080` (`frontend/proxy.conf.json`).

## Angular conventions

TypeScript:
- Strict type checking; prefer inference when the type is obvious; no `any` (use `unknown`).

Components and templates:
- Standalone components; do not set `standalone: true` (default since v20) or `changeDetection: ChangeDetectionStrategy.OnPush` (default since v22).
- Small, single-responsibility components; inline templates for small ones; external template and style paths relative to the component file.
- `input()`, `output()`, `model()` for two-way binding; `computed()` for derived state; `linkedSignal()` for state derived from several sources that must stay in sync.
- No `@HostBinding` / `@HostListener`: use the `host` object of the decorator.
- Native control flow (`@if`, `@for`, `@switch`); `class` and `style` bindings instead of `ngClass` / `ngStyle`; import only the directives and pipes the template uses, never `CommonModule`.
- Keep templates simple; use the async pipe for observables; do not assume globals such as `new Date()` are available in templates.
- `NgOptimizedImage` for static images (not for inline base64).
- Forms: Signal Forms (`@angular/forms/signals`) for new forms; otherwise reactive forms.

State and services:
- Signals for local state; pure, predictable transformations; `update` / `set`, never `mutate`.
- Single-responsibility services; prefer the `@Service` decorator for new singletons (v22+), otherwise `providedIn: 'root'`; `inject()` instead of constructor injection.
- Lazy loading for feature routes.

Accessibility:
- Must pass AXE checks and meet WCAG AA: focus management, color contrast, ARIA attributes.

## Authentication

Sign-in is OIDC Authorization Code + PKCE through `angular-auth-oidc-client` 22.x (works zoneless: it uses rxjs and signals and needs no `zone.js`). Code is in `frontend/src/app/core/auth/`.

- The OIDC settings come from `GET /api/v1/system/client-config` at runtime (`oidcConfigLoader` in `frontend/src/app/app.config.ts`); redirect and post-logout URIs are the application origin. Refresh tokens renew the session.
- `AuthService` completes the sign-in callback, starts sign-in (remembering the route in `sessionStorage`), signs out at Keycloak and owns the `startupError` signal that makes `App` show the full-page error.
- `accessGuard` (`canActivateChild` on the root route tree) signs the visitor in, reloads `GET /api/v1/me` whenever the router activates a route (the router does not re-run it for reused routes with unchanged parameters, for example query-only navigations; that is intended) and routes by status. Child routes declare the status they are for in `data.access` (`full`, `pending`, `ended`, `invitation-only`). The expected status is taken from the nearest ancestor route that declares `data.access`, and the guard runs once per navigation. A failed load shows the unavailable state through a redirect to `/unavailable` with `skipLocationChange`, so the address bar keeps the requested URL; "Retry" repeats the load. A browser reload repeats it only when the failure happened on the initial load (or back/forward); after an in-app navigation the address bar still shows the previous URL, and after a sign-in callback the library has rewritten the address to `/`, so a reload goes there, not to the return route. Retry always uses the remembered target. Do the same for any new failed-load redirect.
- `authInterceptor` adds the bearer token only to non-public `/api/` requests of the same origin (public paths are listed in `frontend/src/app/core/auth/api-urls.ts`, taken from the generated `*.PATH` constants; add new public endpoints there).
- `errorInterceptor`: 401 starts sign-in again once (a 401 after a fresh sign-in shows the startup error); 403 `not-invited` / `access-ended` reload the caller and open the access screen; 403 `forbidden` shows the snackbar "You don't have permission to do this.". Handle other errors in the component that made the call.
- Tokens are renewed 30 s before they expire (`renewTimeBeforeTokenExpiresInSeconds`). The client configuration is cached after the first load, so sign-out also works while the API is down (the local session is ended even if the Keycloak logout fails).
- Sign-out ends the Keycloak session; if the library has no end-session URL it ends the local session and returns to `/`. `authorize()` reports discovery failures nowhere, so a 15 s watchdog turns a missing redirect into the startup error.
- The current user is `CurrentUserService` (`me`, `user`, `name`). Build names with `displayName()` from `frontend/src/app/core/auth/user-name.ts`.
- Keycloak must have the client `mentorhub-web` (public, PKCE S256) with redirect and post-logout URIs `http://localhost:4200/*`.

## Styling

- Angular Material M3; the theme is in `frontend/src/styles.scss`.
- Components take colors, typography and shapes only from the system tokens (`var(--mat-sys-primary)`, `var(--mat-sys-body-medium)`, ...). No literal colors or `font-family` in components: the corporate brand will arrive later and must stay a theme change. A project hook flags violations.
- Screens follow `docs/ui/guidelines.md`.
- UI text is in English.

## Tests

- Vitest. Replace the `Api` service via `providers` (see `frontend/src/app/features/shell/shell.spec.ts`).
- Authentication tests replace `OidcSecurityService` (see `frontend/src/app/core/auth/auth.service.spec.ts`) or `AuthService` and `CurrentUserService` (guard, interceptors, components); no test talks to a real identity provider.

## Pitfalls

- `provideApiConfiguration('')`, never `'/'`: contract paths already start with `/api` and the generated client joins `rootUrl` and the path, so `'/'` produces `//api/...`. A project hook flags it.
- Angular requires Node ^24.15. An older default Node fails with `EBADENGINE`, including the Angular CLI MCP server.
- Angular conventions changed recently (standalone and `OnPush` by default, signal APIs, native control flow); older examples use the previous style. Follow [Angular conventions](#angular-conventions).

## Commands

| Task | Command |
|---|---|
| Install | `npm ci` |
| Dev server on :4200 (`/api` proxied to :8080) | `npm start` |
| Format | `npm run format` |
| Regenerate the API client | `npm run generate:api` |

## Verification

The definition of done for frontend work. Run each command exactly in this form, in separate shell calls. `~/.nvm/nvm-exec` runs the command with the Node version from `.nvmrc`, and these forms match the allow rules in `.claude/settings.json` (change both together):

```bash
cd frontend && ~/.nvm/nvm-exec npm run format:check
cd frontend && ~/.nvm/nvm-exec npm run lint
cd frontend && ~/.nvm/nvm-exec npm test -- --watch=false
cd frontend && ~/.nvm/nvm-exec npm run build
```

- If `node_modules` is missing, run `cd frontend && ~/.nvm/nvm-exec npm ci` first.
- Formatting failures are fixed with `cd frontend && ~/.nvm/nvm-exec npm run format`.
- Without nvm, check `node -v` (^24.15) and run the same `npm` commands directly.

## Checking an API

- Package version: `node_modules/<package>/package.json`.
- Exports: the package's `.d.ts` files (see `types` / `exports` in its `package.json`).
- The generated client: `frontend/src/app/core/api/` (run `npm run generate:api` if it is missing).
- The Angular CLI MCP server (`.mcp.json`) offers best practices, documentation search and examples for the installed Angular version. It starts with the default Node, so the default must satisfy `.nvmrc` (`nvm alias default 24`).

## Mock mode

Not implemented yet. A dev-server mode that serves data generated from `api/openapi.yaml`, so screens can be reviewed before the backend exists, needs an ADR first.
