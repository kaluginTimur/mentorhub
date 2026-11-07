# UI guidelines

Page patterns and conventions shared by screens. Styling rules are in `docs/development/frontend.md`.

## Application shell

- Route `/` and everything an active user with roles can open lives inside the shell (`frontend/src/app/features/shell/shell.ts`): a toolbar with "MentorHub", a link to each section the user's permissions allow, and the account menu, then the routed page. A section's link is marked as the current page on all of its routes.
- Each page has one `h1`.

## Pages outside the shell

The access screens (`frontend/src/app/features/access/access-screen.ts`) and the public invitation landing page share one layout: a centered card without the account menu, an `h1` focused on open, a text and the actions. "Sign out" appears where a signed-in visitor has to leave or switch accounts.

## States

- A failed load shows the shared unavailable card with Retry (`frontend/src/app/shared/unavailable/unavailable.ts`) in place of the page's content, through the redirect the frontend guide describes; a startup failure shows it as a full page.

## Pages and dialogs

- List page (for example `/users`): `h1`, a filter row (search applied 300 ms after typing stops, selects), a Material table with column headers, a paginator without a page-size selector. While loading, a labelled progress bar shows and the previous rows stay. Empty: a message, with "Clear filters" when filters are set.
- Detail page (for example `/users/{id}`): a back link to the list (with its last query), `h1` with a status chip, outlined cards with `h2` headings. Not found is a page with a link back; a failed load is the unavailable card.
- Dialogs: a title, the content, "Cancel" and the confirming action. Focus moves to the first field, is trapped, and returns to the opener. Confirmation dialogs name the object ("Enable <name>?"). Forms in dialogs show server field errors under their fields and keep the dialog open.
- Chips are display only: a chip set with a label uses `role="list"` and its chips `role="listitem"` (the default `presentation` role must not carry a label).
- Snackbars: "Dismiss" action; success messages are polite, conflict (409) messages are assertive.
- Color never carries meaning alone: a state shown in the error color also says it in words.
- A control disabled for a reason the user should read stays focusable (`disabledInteractive`) and is linked to a visible hint with `aria-describedby`.
