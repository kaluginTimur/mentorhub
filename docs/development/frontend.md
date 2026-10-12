# Frontend development

Angular 22 (standalone, zoneless, signals), Angular Material (M3), Vitest, angular-eslint, Prettier, ng-openapi-gen. Node 24 via nvm (`frontend/.nvmrc`). Commands run from `frontend/`; paths in this document are relative to the repository root.

## Structure

- `frontend/src/app/core/`: infrastructure (API client, auth, interceptors).
- `frontend/src/app/shared/`: reusable components (planned).
- `frontend/src/app/features/<feature>/`: one lazy-loaded route tree per feature.

## API client (ADR 0002)

- `ng-openapi-gen` generates the client from `api/openapi.yaml` into `frontend/src/app/core/api/` (`npm run generate:api`, run automatically before `start`, `build`, `test` and `lint`). It is gitignored; never edit or commit it.
- Calls: `inject(Api).invoke(fn, params)` with functions from `frontend/src/app/core/api/fn/**`. Load data in components with `resource()`, as in `frontend/src/app/app.ts`.
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

## Styling

- Angular Material M3; the theme is in `frontend/src/styles.scss`.
- Components take colors, typography and shapes only from the system tokens (`var(--mat-sys-primary)`, `var(--mat-sys-body-medium)`, ...). No literal colors or `font-family` in components: the corporate brand will arrive later and must stay a theme change. A project hook flags violations.
- Screens follow `docs/ui/guidelines.md` once it exists.
- UI text is in English.

## Tests

- Vitest. Replace the `Api` service via `providers` (see `frontend/src/app/app.spec.ts`).

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
