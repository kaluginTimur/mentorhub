# Platform

## Purpose

What every API operation and every screen relies on, whatever the feature: how errors look, how lists are paged, what the client learns before anyone signs in, what is served at all, and what happens when the application cannot start or the client cannot reach it. Who may call an operation is described in the authorization capability (PERM) and the identity capability; this capability describes the shape of the answer and the shared plumbing.

## Requirements

### PLT-1 Errors are Problem Details

The system SHALL answer every failed API call with an RFC 9457 problem document (`application/problem+json`) that has `type`, `title` and `status`, and usually `detail`. An error that has no MentorHub problem type SHALL have the type `about:blank`. The problem types of the API are listed in the API contract; the refusals 401 and 403 are described by the identity capability and (PERM-3), and the refusal 404 by (PERM-6), and each feature capability names its own conflict types. A response SHALL NOT expose exception text, stack traces or other internals.

#### PLT-1.1 A refusal from an operation is a problem document

- GIVEN an active user who holds the permission an operation needs
- WHEN the user calls the operation for an id that does not exist
- THEN the response is 404 with the content type `application/problem+json`
- AND the problem has `type` `urn:mentorhub:problem:not-found`, a `title` and `status` 404

#### PLT-1.2 An unexpected failure is a generic 500

- GIVEN an operation that fails with an unexpected error
- WHEN an active user calls it
- THEN the response is 500 as a problem document with `type` `about:blank`
- AND the response does not contain the text of the error

#### PLT-1.3 A failure before the operation is reached is also a problem document

- GIVEN a trusted identity provider whose keys cannot be obtained
- WHEN a caller sends a request with a token of that provider
- THEN the response is 500 as a problem document with `type` `about:blank`
- AND the problem names the path of the request as its `instance`
- AND the response does not contain the text of the error

### PLT-2 Invalid requests are validation failures

The system SHALL answer a request with an invalid parameter or body field with 400 and the problem type `urn:mentorhub:problem:validation-failed`, and SHALL change nothing. The problem has an `errors` list with one entry, a `field` and a `message`, for each invalid query parameter, path parameter or body property, named as the client sent it. A value that cannot be converted to the expected type is invalid in the same way: an unknown value of a fixed set is reported with the allowed values, and a malformed identifier says it must be a UUID. A body that is not valid JSON is a plain 400 with the type `about:blank` and no `errors`.

#### PLT-2.1 An invalid body field is named

- GIVEN a request body in which several fields are invalid or missing
- WHEN an active user who holds the permission sends it
- THEN the response is 400 `validation-failed`
- AND `errors` has one entry for each invalid field, naming the field
- AND nothing is changed

#### PLT-2.2 An invalid parameter is named

- GIVEN an operation with a UUID in its path and a query parameter that accepts a fixed set of values
- WHEN an active user who holds the permission sends a path value that is not a UUID, or a query value outside the set
- THEN the response is 400 `validation-failed`
- AND `errors` names the parameter, and says it must be a UUID or lists the allowed values

#### PLT-2.3 A body that is not JSON is a plain bad request

- GIVEN an operation that takes a JSON body
- WHEN an active user who holds the permission sends a body that is not valid JSON
- THEN the response is 400 with the type `about:blank` and no `errors`
- AND nothing is changed

### PLT-3 Lists are paged

The system SHALL return a list of items that can grow without a bound as a page. The request takes a zero-based `page` (default 0) and a `size` from 1 to 100 (default 25). The response has `items`, `page`, `size` (the size that was requested) and `totalItems` (the number of matching items on all pages). A page past the last one has no items. A value outside these ranges SHALL be rejected, never corrected. Which items a list holds and in what order is described by the capability that owns the list.

#### PLT-3.1 Defaults apply when no paging is requested

- GIVEN more than 25 items in a list
- WHEN an authorized caller asks for the list without `page` and `size`
- THEN the response holds the first 25 items
- AND `page` is 0, `size` is 25 and `totalItems` is the number of all matching items

#### PLT-3.2 A requested page is returned with the total

- GIVEN four items in a list
- WHEN an authorized caller asks for page 1 with size 2, and for page 5 with size 2
- THEN page 1 holds the third and fourth items in the order of the list, with `page` 1, `size` 2 and `totalItems` 4
- AND page 5 has no items and `totalItems` 4

#### PLT-3.3 A page or size out of range is rejected

- GIVEN a list
- WHEN an authorized caller asks with a `page` below 0, or a `size` below 1 or above 100
- THEN the response is 400 `validation-failed`
- AND `errors` names each of `page` and `size` that is out of range

### PLT-4 Public service information

The system SHALL offer the application name and version, and the client configuration, to anyone without signing in. A token sent with such a request SHALL be ignored, so a stale or invalid token cannot make the request fail. The client configuration SHALL hold only values that are public by nature: the identity provider's issuer, the client id and the scopes the client requests to sign in, and whether invitation emails can be requested (true when a mail server is configured). It SHALL NOT contain a secret.

#### PLT-4.1 Name, version and configuration are public

- GIVEN a caller who is not signed in
- WHEN the caller asks for the application information and for the client configuration
- THEN the responses are 200, with the name and version, and with the issuer, the client id, the scopes and the invitation emails flag

#### PLT-4.2 An invalid token does not fail a public request

- GIVEN a caller who sends an invalid token
- WHEN the caller asks for the application information or the client configuration
- THEN the response is 200, as without a token

### PLT-5 Required configuration is checked at startup

The system SHALL refuse to start, in every profile, when a required setting is missing or invalid, and the failure message SHALL name the setting. The client configuration (the issuer and the client id) is required. The settings required by other capabilities, such as the trusted issuers, are named in those capabilities.

#### PLT-5.1 Missing client configuration stops the application

- GIVEN a configuration without the issuer or the client id of the client
- WHEN the application starts
- THEN the start fails
- AND the failure message names both missing settings

### PLT-6 Only the API and health are served

The system SHALL serve the API, the public service information and the health check, and nothing else. In production, no API documentation, API description or management endpoint other than health is served to anyone, administrators included, and the health check shows no details. A path outside the API is denied. These development tools are available only when the application runs in development mode, where they need no sign-in.

#### PLT-6.1 Development tools are not served in production

- GIVEN the application runs in production mode
- WHEN anyone requests the API documentation pages, the API description or a management endpoint other than health, with or without a token, even one of an administrator
- THEN the answer is 401 without a token and 403 with one
- AND the documentation is not served

#### PLT-6.2 Health is public and shows no details

- GIVEN the application runs in production mode
- WHEN anyone who is not signed in asks for the health check
- THEN the response is 200 with the status `UP`
- AND the response has no components or details

#### PLT-6.3 A path outside the API is denied

- GIVEN an active user
- WHEN the user or a visitor requests a path that is neither in the API nor a health or documentation path
- THEN the answer is 401 without a token and 403 with one

### PLT-7 The access token goes only to the API

The client SHALL send the access token only with requests to the application's own API, and only to operations that need it. It SHALL NOT send it with requests to public API operations or to any other address, including the identity provider.

#### PLT-7.1 A protected API request carries the token

- GIVEN a signed-in user
- WHEN the client calls an API operation that is not public
- THEN the request carries the access token

#### PLT-7.2 Other requests do not carry the token

- GIVEN a signed-in user
- WHEN the client calls a public API operation, or requests an address outside the application's own API, on this or another site
- THEN the request carries no access token

### PLT-8 The client says when it cannot work

The client SHALL show a full-page message that MentorHub is unavailable, with a Retry action, when it cannot start sign-in because the client configuration or the identity provider's description cannot be loaded. When the current user cannot be loaded because the API fails, the client SHALL show the same message in place of the requested screen and keep the requested address, and Retry SHALL repeat that navigation, or go to the home page when nothing was requested. Retry SHALL NOT repeat a sign-in callback that has already been used.

#### PLT-8.1 Sign-in that cannot start shows the unavailable message

- GIVEN the client configuration or the identity provider's description cannot be loaded
- WHEN a visitor opens an address that carries sign-in callback parameters and other parameters
- THEN the message that MentorHub is unavailable is shown with Retry, and the visitor is not sent to the identity provider
- AND Retry reloads the page on the same address without the callback parameters and with the other parameters

#### PLT-8.2 A failing API shows the unavailable message in place of the screen

- GIVEN a signed-in user
- WHEN the current user cannot be loaded because the API fails
- THEN the unavailable message is shown in place of the requested screen, and the address stays the one requested
- AND Retry navigates to the requested screen again

### PLT-9 List pages

Every screen that shows a paged list (PLT-3) with a search or filters SHALL behave alike, whatever the list holds; the capability that owns the list names only its own filters and order. The search, the filters and the page are kept in the address, so a reload or a shared address shows the same list. A change of the search or of a filter shows the first page. A list with nothing in it says so, and the message differs between nothing existing yet and nothing matching the search or filters; the second offers to clear the filters. A load that fails, other than by a refusal, says that MentorHub is unavailable and offers Retry, which loads the list again. A page past the last one says that there are no items on that page, and the user can still move to another page. A list the API refuses with 403 takes the user to the home page. While the next page loads, the rows already shown stay.

#### PLT-9.1 The query is kept, and a change shows the first page

- GIVEN a list screen with a search, filters and a page other than the first
- WHEN the user reloads the page or opens the same address
- THEN the search, the filters and the page are as before, and so is the list
- AND when the user then changes the search or a filter, the first page of the new result is shown

#### PLT-9.2 Nothing found differs from nothing yet

- GIVEN a list with no items
- WHEN the screen shows it without a search or filters, and then with a search or a filter that matches nothing
- THEN the first says that nothing exists yet
- AND the second says that nothing matches and offers to clear the filters, which shows the full list

#### PLT-9.3 A failed load offers Retry

- GIVEN the API fails to answer the list
- WHEN the screen loads
- THEN it says that MentorHub is unavailable and offers Retry
- AND Retry loads the list again

#### PLT-9.4 A page past the last one says so

- GIVEN a list with items and an address whose page is past the last one
- WHEN the screen loads
- THEN it says that there are no items on this page
- AND the user can move to another page

#### PLT-9.5 A refused list takes the user home

- GIVEN a user for whom the API refuses the list with 403
- WHEN the screen loads
- THEN the user is taken to the home page

## Known limitations

- A caller without the permission for an operation may get 400 `validation-failed` for an invalid body instead of 403: the body is checked before the permission. 403 before 400 is not guaranteed.

## Out of scope

- Limits on the rate or size of requests.
- A version of the API other than the current one.
- Translation of error messages: the `message` of a validation error is for developers, and the client may show its own text.
