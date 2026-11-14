# 0007. Tailwind CSS v4 and Spartan UI for the frontend

## Context

MentorHub began with Angular Material (M3). Customizing Material for corporate branding, a top-bar workspace shell, light and dark themes, custom responsive list-to-card layouts, and offline font bundling meant overriding Material internal DOM structure and typography tokens. As the frontend grows from user administration into tracking, assignments, and scheduling, the styling and component layer needs a foundation that stays directly under the repository's control.

## Decision

- Styling uses Tailwind CSS v4 configured in `frontend/src/styles/tailwind.css` with semantic CSS custom properties (`:root`, `.dark`, `@theme inline`). Components reference only those semantic tokens; literal colors and `font-family` declarations remain banned outside `frontend/src/styles/tailwind.css`.
- Accessible UI primitives come from `@spartan-ng/brain` paired with source-owned `@spartan-ng/helm` components generated into `frontend/src/app/shared/ui/`.
- Icons use `@ng-icons/core` with `@ng-icons/lucide`, and typography is self-hosted locally through `@fontsource-variable/*` packages (`plus-jakarta-sans`, `inter`, `jetbrains-mono`) so production builds need no network access.
- Theme switching (`light`, `dark`, `system`) is handled client-side by `ThemeService` (`frontend/src/app/core/theme/theme.service.ts`).
- `@angular/material` is not used (`@angular/cdk` stays only as a transitive dependency of `@spartan-ng/brain` portals/overlays).

## Why

- Separating utility layout classes, semantic theme tokens, and owned Helm components lets screens evolve custom layouts and keep WCAG AA contrast across light and dark modes without fighting library specificity.
- Self-hosted variable fonts remove external Google Fonts requests during `ng build` and in production.
- Cost: generated Helm primitives in `frontend/src/app/shared/ui/` live in this repository and must be maintained and audited for accessibility here, rather than delegated to a single upstream component library.

## Rejected alternatives

- **Keeping Angular Material (M3).** Faster for standard form screens, but custom corporate styling, dark surface contrast tuning, and bespoke layouts required brittle overrides around Material internals.
- **Adopting PrimeNG or NG-ZORRO.** Both bring large third-party styling systems with similar customization and bundle trade-offs.
