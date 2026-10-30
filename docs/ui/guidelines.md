# UI guidelines

Screens use Angular Material and the theme tokens only (see `docs/development/frontend.md`). UI text is English.

## Application shell

- Route `/` and everything an active user with roles can open lives inside `frontend/src/app/features/shell/shell.ts`: a toolbar with "MentorHub", the "Users" link (only with the `USERS_READ` permission; marked as the current page on `/users` and `/users/{id}`), the "Invitations" link (only with the `USERS_INVITE` permission; marked as the current page on `/invitations`) and the account menu, then the routed page.
- The account menu button is named "Account menu for <name>"; the menu shows the name and email and the actions "Profile" (opens `/profile`) and "Sign out".
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

The public invitation landing page `/invite` (`frontend/src/app/features/invite/`) uses the same centered card with an `h1` (focused on open), a text and actions, and is open to visitors who are not signed in. Its title depends on the state (for example "You're invited to MentorHub", "Invitation not valid", "Wrong account"); "Sign out" appears only in the states where a signed-in visitor has to switch accounts.

Link view (`frontend/src/app/features/invitations/link-view.ts`, shown in the invite dialog after an invitation is created and after "Get a new link" on an open row): the text says the link can be copied again from the invitations list while the invitation is pending; the link is in a read-only field that is focused and selects its text on focus, with the "Copy link" action and "Done". "Copy link" shows a polite snackbar "Link copied"; if the clipboard is not available, it selects the field text instead so the user can copy it by hand. The same component has a copy mode (no "Copy link" button, text "Copy this link and send it to <email>…") that the "Copy link" row action of a pending invitation opens when no clipboard write works. That row action fetches the link on click and writes it with `navigator.clipboard.write` and a pending `ClipboardItem` (Safari needs the write to start inside the click), then `writeText`; the row's actions are disabled while it runs.

Invitation emails (only when `ClientConfig.invitationEmails` is true; otherwise none of this is rendered): the invite dialog has the checkbox "Send the invitation by email to this address" below the roles and the "Get a new link" confirmation has "Send the new link by email", both checked by default. When the email was requested, the link view reads "An email with this link is on its way to <email>. It expires on <date>. You can also copy the link, and copy it again from the invitations list while it is pending." A pending row has the action "Send email" before "Copy link" (accessible name "Send invitation email to <email>"): it sends without a confirmation, disables the row's actions while it runs, replaces the row with the server's answer and shows the polite snackbar "Email to <email> queued". Its 409 snackbars ("An email to <email> is already on its way.", "Sending email is not available. Reload the page and try again.") are assertive; in the invite dialog the latter is an inline alert and the dialog keeps its values. Whenever an invitation has a latest email, whatever its status, the Status column shows a further secondary line: "Sending email…", "Email sent <date>", "Email failed <date>" (in the error color, the words carry the meaning) or "Email not sent".

## States

- Loading: the status text "Loading" with a spinner, in a `main` landmark; `aria-busy` is set on the container of the routed page. It shows during the initial load, including sign-in. Later navigations show no indicator.
- Unavailable: a card with "MentorHub is unavailable. Try again later." (`role="alert"`) and "Retry" (`frontend/src/app/shared/unavailable/unavailable.ts`). It is a full page for startup failures (Retry reloads the page). For a failed load it replaces the page's content while the address bar keeps the requested URL (internally `/unavailable`, without changing the address), so Retry repeats the failed load. A browser reload does the same only when the failure happened on the initial load or back/forward; after an in-app navigation the address bar still shows the previous page, and after a sign-in callback a reload goes to `/`, not to the page the user opened.
- Permission refused by the API (403 `forbidden`): a snackbar "You don't have permission to do this."; the user stays on the page.

## Pages and dialogs

- List page (`/users`): `h1`, a filter row (search applied 300 ms after typing stops, selects), a Material table with column headers, a paginator without a page-size selector. While loading, a labelled progress bar shows and the previous rows stay. Empty: a message, with "Clear filters" when filters are set. The paginator stays when a page past the last one is empty. Search, filters and page are kept in the URL query.
- Detail page (`/users/{id}`): a back link to the list (with its last query), `h1` with a status chip, outlined cards with `h2` headings (Profile, Access, Roles, History). Not found is a page with a link back; a failed load is the unavailable card.
- Dialogs: a title, the content, "Cancel" and the confirming action. Focus moves to the first field, is trapped, and returns to the opener. Confirmation dialogs name the object ("Enable <name>?"). Forms in dialogs show server field errors under their fields and keep the dialog open.
- Chips are display only: a chip set with a label uses `role="list"` and its chips `role="listitem"` (the default `presentation` role must not carry a label).
- Snackbars: "Dismiss" action; success messages are polite, the 409 messages ("You can't lock yourself out.", "MentorHub must keep at least one active administrator.") are assertive.
- A control disabled for a reason the user should read stays focusable (`disabledInteractive`) and is linked to a visible hint with `aria-describedby`.
