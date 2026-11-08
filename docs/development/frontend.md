# Frontend development

Angular 22 (standalone, zoneless, signals), Angular Material (M3), Vitest, angular-eslint, Prettier, ng-openapi-gen. Node 24 via nvm (`frontend/.nvmrc`). Commands run from `frontend/`; paths in this document are relative to the repository root.

## Structure

- `frontend/src/app/core/`: infrastructure (API client, auth, interceptors).
- `frontend/src/app/shared/`: reusable components; look there before writing a new one.
- `frontend/src/app/features/<feature>/`: one lazy-loaded route tree per feature.

## API client (decision 0002)

- `ng-openapi-gen` generates the client from `api/openapi.yaml` into `frontend/src/app/core/api/` (`npm run generate:api`, run automatically before `start`, `build`, `test` and `lint`). It is gitignored; never edit or commit it.
- Calls: `inject(Api).invoke(fn, params)` with functions from `frontend/src/app/core/api/fn/**`. Load data in components with `resource()`, as in `frontend/src/app/features/shell/home.ts`. Guard `value()` with `hasValue()` (or check `error()` first): in the error state `value()` throws.
- Nullable properties (`type: [string, 'null']`) are generated as required `(string | null)`; an optional property that may be absent (for example `Me.user`) is generated as `user?: MeUser`. Handle absent and `null` separately.
- Enums are generated as string unions (`Role`, `Permission`), with `*-array.ts` helpers listing the values.

## Angular conventions

TypeScript:
- Strict type checking; prefer inference when the type is obvious; no `any` (use `unknown`).
- Comment sparingly: only non-obvious infrastructure or algorithmic points. No comments that restate the code.

Components and templates:
- Standalone components; do not set `standalone: true` (default since v20) or `changeDetection: ChangeDetectionStrategy.OnPush` (default since v22).
- Inline templates for small components; external template and style paths relative to the component file.
- `input()`, `output()`, `model()` for two-way binding; `computed()` for derived state; `linkedSignal()` for state derived from several sources that must stay in sync.
- Import only the directives and pipes the template uses, never `CommonModule`.
- Use the async pipe for observables; do not assume globals such as `new Date()` are available in templates.
- `NgOptimizedImage` for static images (not for inline base64).
- Forms: Signal Forms (`@angular/forms/signals`) for new forms; otherwise reactive forms.

State and services:
- Signals for local state, changed with `update` and `set`.
- Lazy loading for feature routes.

Accessibility:
- Must pass AXE checks and meet WCAG AA: focus management, color contrast, ARIA attributes.

## Authentication

Sign-in is OIDC Authorization Code + PKCE through `angular-auth-oidc-client`. Code is in `frontend/src/app/core/auth/`.

- The client configuration (`GET /api/v1/system/client-config`) is loaded at startup and gives the OIDC settings and the UI feature flags. The flags come from `ClientConfigService` (`frontend/src/app/core/client-config.service.ts`) and are false until the configuration has loaded. Tests replace the service with a stub such as `{ invitationEmails: () => true }`.
- `accessGuard` (`canActivateChild` on the root route tree) signs the visitor in, reloads `GET /api/v1/me` on every navigation and routes by status. Child routes declare the status they are for in `data.access`; the nearest ancestor route that declares it decides. A failed load redirects to `/unavailable` with `skipLocationChange`, so the address bar keeps the requested URL, and "Retry" repeats the load for the remembered target. Do the same for any new failed-load redirect.
- Public routes, such as the invitation landing page, sit outside `accessGuard`. A secret token from a link is kept only in `sessionStorage` across sign-in (as `InvitationSession` does), never in a URL, a redirect URI or the OIDC `state`.
- `authInterceptor` adds the bearer token to same-origin `/api/` requests except the public ones: add a new public endpoint to `frontend/src/app/core/auth/api-urls.ts`.
- `errorInterceptor`: 401 starts sign-in again once (a 401 after a fresh sign-in shows the startup error); 403 `not-invited` / `access-ended` reload the caller and open the access screen; 403 `forbidden` shows a snackbar. Handle other errors in the component that made the call.
- The current user is `CurrentUserService`. Build names with `displayName()` from `frontend/src/app/core/auth/user-name.ts`.
- The local Keycloak client comes from `infra/keycloak/mentorhub-realm.json`. The frontend relies on it being a public client with PKCE S256, with `http://localhost:4200/*` as redirect and post-logout URI.

## Permissions and list state

- The check is `CurrentUserService.hasPermission(permission)` (typed with the generated `Permission`); use it in components instead of reading `permissions` directly.
- A route tree that needs a permission declares `canActivateChild: [requirePermission('USERS_READ')]` (`frontend/src/app/core/auth/permission.guard.ts`); users without it are redirected to `/`. It uses `canActivateChild` so it runs again between sibling child routes. The API is still the authority: a 403 `forbidden` from a page's load sends the user to `/` (the error interceptor shows the snackbar).
- List state (search, filters, page) lives in the URL query and the page reads it from `ActivatedRoute.queryParamMap`, so reload and back restore it (see `frontend/src/app/features/users/users-list.ts`). Write the user's text untrimmed to the URL and trim only when calling the API. Detail pages link back with the list's last query (`UsersListState`).

## Styling

- Angular Material M3; the theme is in `frontend/src/styles.scss`.
- Components take colors, typography and shapes only from the system tokens (`var(--mat-sys-primary)`, `var(--mat-sys-body-medium)`, ...). No literal colors or `font-family` in components: the corporate brand will arrive later and must stay a theme change. A project hook flags violations.
- Screens follow `docs/ui/guidelines.md`.

## Tests

- Vitest. Replace the `Api` service via `providers` (see `frontend/src/app/features/shell/shell.spec.ts`).
- Authentication tests replace `OidcSecurityService` (see `frontend/src/app/core/auth/auth.service.spec.ts`) or `AuthService` and `CurrentUserService` (guard, interceptors, components); no test talks to a real identity provider.
- A shared piece (a pure function, a shared component, a guard factory, a handler several actions use) is tested once, in its own spec or through the one host whose scenario it serves, one test per branch. Other hosts test only that they use it.
- Assert the result (the route, what is rendered, the request sent), not how often a dependency was called, and never what exists only in a test double. Stub a signal with a signal, so the test reads its state.
- Extend a test rather than copy it.
- No fixed waits: advance fake timers past a debounce, or wait for the result (`vi.waitFor`), rather than sleeping for real time.

## Pitfalls

- `provideApiConfiguration('')`, never `'/'`: contract paths already start with `/api` and the generated client joins `rootUrl` and the path, so `'/'` produces `//api/...`. A project hook flags it.
- Material and CDK code reached by a static import in `app.config.ts`, interceptors or guards lands in the initial bundle together with everything lazy routes use from it. Load rarely used pieces with `import()` (for example the snack bar in `error.interceptor.ts`) and watch the initial-size budget in the build output.
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
- The production build downloads font CSS from Google Fonts, so it needs internet access. `frontend/.npmrc` gives Node a longer connect attempt per address family, without which the download times out on networks without IPv6 (WSL); it is temporary until the fonts are served from the app (#12).

## Checking an API

The `api-lookup` subagent follows this section. Implementation agents ask it and never inspect a dependency themselves: it answers from the exact versions on a small model, and the lookup stays out of their context.

- Package version: `node_modules/<package>/package.json`.
- Exports: the package's `.d.ts` files (see `types` / `exports` in its `package.json`).
- The generated client: `frontend/src/app/core/api/` (run `npm run generate:api` if it is missing).
- The Angular CLI MCP server (`.mcp.json`) offers best practices, documentation search and examples for the installed Angular version. It starts with the default Node, so the default must satisfy `.nvmrc` (`nvm alias default 24`).

## Mock mode

Not implemented yet. A dev-server mode that serves data generated from `api/openapi.yaml`, so screens can be reviewed before the backend exists, needs a decision first.
