# UI guidelines

Screens use Angular Material and the theme tokens only (see `docs/development/frontend.md`). UI text is English.

## Application shell

- Route `/` and everything an active user with roles can open lives inside `frontend/src/app/features/shell/shell.ts`: a toolbar with "MentorHub" and the account menu, then the routed page.
- The account menu button is named "Account menu for <name>"; the menu shows the name and email and the action "Sign out".
- The user's name is first and last name, or the one that is set, or the email.
- Each page has one `h1`. The home page shows "Welcome, <first name>" and the application version in its footer.

## Access screens

One layout (`frontend/src/app/features/access/access-screen.ts`), a centered card without the account menu, a title (the `h1`, focused on open), a text and the action "Sign out":

| Route | Title |
|---|---|
| `/access/invitation-only` | Access by invitation only |
| `/access/ended` | Your access has ended |
| `/access/pending` | Your access is being set up |

Every route resolves to the screen matching the user's status; unknown paths redirect to `/`.

## States

- Loading: the status text "Loading" with a spinner, in a `main` landmark; `aria-busy` is set on the container of the routed page. It shows during the initial load, including sign-in. Later navigations show no indicator.
- Unavailable: a card with "MentorHub is unavailable. Try again later." (`role="alert"`) and "Retry" (`frontend/src/app/shared/unavailable/unavailable.ts`). It is a full page for startup failures (Retry reloads the page). For a failed load it replaces the page's content while the address bar keeps the requested URL (internally `/unavailable`, without changing the address), so Retry repeats the failed load. A browser reload does the same only when the failure happened on the initial load or back/forward; after an in-app navigation the address bar still shows the previous page, and after a sign-in callback a reload goes to `/`, not to the page the user opened.
- Permission refused by the API (403 `forbidden`): a snackbar "You don't have permission to do this."; the user stays on the page.
