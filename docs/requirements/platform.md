# Platform

## Purpose

What every API operation and every screen relies on, whatever the feature: how errors look, how lists are paged, what the client learns before anyone signs in, what is served at all, and what happens when the application cannot start or the client cannot reach it. Who may call an operation is described by the identity capability (IDN) and the authorization capability (PERM).

## Requirements

### PLT-1 Errors are Problem Details

The system SHALL answer every failed API call with an RFC 9457 problem document, with the type `about:blank` when the error has no MentorHub problem type, and SHALL NOT expose exception text or other internals.

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

The system SHALL answer a request with an invalid parameter or body field with 400 `validation-failed`, naming each invalid one in `errors`, and SHALL change nothing.

#### PLT-2.1 An invalid body field is named

- GIVEN a request body in which several fields are invalid or missing
- WHEN an active user who holds the permission sends it
- THEN the response is 400 `validation-failed`
- AND `errors` has one entry for each invalid field, naming the field
- AND nothing is changed

#### PLT-2.2 A path id that is not a UUID is named

- GIVEN an operation with an id in its path
- WHEN an active user who holds the permission sends an id that is not a UUID
- THEN the response is 400 `validation-failed`
- AND `errors` names the id and says it must be a UUID

#### PLT-2.3 A body that is not JSON is a plain bad request

- GIVEN an operation that takes a JSON body
- WHEN an active user who holds the permission sends a body that is not valid JSON
- THEN the response is 400 with the type `about:blank` and no `errors`
- AND nothing is changed

#### PLT-2.4 A query value outside the set lists the allowed values

- GIVEN an operation with a query parameter that accepts a fixed set of values
- WHEN an active user who holds the permission sends a value outside the set
- THEN the response is 400 `validation-failed`
- AND `errors` names the parameter and lists the allowed values

### PLT-3 Lists are paged

The system SHALL return a list that can grow without a bound as a page, as the API contract defines it. A `page` or `size` outside the ranges of the contract SHALL be rejected, never corrected. The capability that owns the list says which items it holds and in what order.

#### PLT-3.1 Defaults apply when no paging is requested

- GIVEN more items in a list than the default size
- WHEN an authorized caller asks for the list without `page` and `size`
- THEN the response is the first page of the default size
- AND `totalItems` is the number of all matching items

#### PLT-3.2 A requested page is returned with the total

- GIVEN four items in a list
- WHEN an authorized caller asks for page 1 with size 2
- THEN the response holds the third and fourth items in the order of the list, with `page` 1, `size` 2 and `totalItems` 4

#### PLT-3.3 A page or size out of range is rejected

- GIVEN a list
- WHEN an authorized caller asks with a `page` or a `size` outside the ranges of the contract
- THEN the response is 400 `validation-failed`
- AND `errors` names each of `page` and `size` that is out of range

#### PLT-3.4 A page past the last one is empty

- GIVEN four items in a list
- WHEN an authorized caller asks for page 5 with size 2
- THEN the response has no items and `totalItems` 4

### PLT-4 Public service information

The system SHALL offer the application name and version, and the client configuration, to anyone without signing in, and SHALL ignore a token sent with such a request. The client configuration SHALL hold only values that are public by nature and SHALL NOT contain a secret.

#### PLT-4.1 Name, version and configuration are public

- GIVEN a caller who is not signed in
- WHEN the caller asks for the application information and for the client configuration
- THEN the responses are 200, with the name and version, and with the configuration, which tells whether invitation emails can be requested

#### PLT-4.2 An invalid token does not fail a public request

- GIVEN a caller who sends an invalid token
- WHEN the caller asks for the application information or the client configuration
- THEN the response is 200, as without a token

### PLT-5 Required configuration is checked at startup

The system SHALL refuse to start, in every profile, when a required setting is missing or invalid, and the failure message SHALL name the setting. The client configuration (the issuer and the client id) is required; other capabilities name their own required settings.

#### PLT-5.1 Missing client configuration stops the application

- GIVEN a configuration without the issuer or the client id of the client
- WHEN the application starts
- THEN the start fails
- AND the failure message names both missing settings

### PLT-6 Only the API and health are served

The system SHALL serve only the API, the public service information and the health check, and SHALL deny any other path. In production that holds for everyone, administrators included.

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

The client SHALL send the access token only with requests to operations of the application's own API that need it, and SHALL NOT send it to public operations or to any other address, including the identity provider.

#### PLT-7.1 A protected API request carries the token

- GIVEN a signed-in user
- WHEN the client calls an API operation that is not public
- THEN the request carries the access token

#### PLT-7.2 A public API request does not carry the token

- GIVEN a signed-in user
- WHEN the client calls a public API operation
- THEN the request carries no access token

#### PLT-7.3 A request to another address does not carry the token

- GIVEN a signed-in user
- WHEN the client requests an address outside the application's own API, on this or another site
- THEN the request carries no access token

### PLT-8 The client says when it cannot work

The client SHALL show a full-page message that MentorHub is unavailable, with a Retry action, when sign-in cannot start or the current user cannot be loaded because the API fails. Retry SHALL NOT repeat a sign-in callback that has already been used.

#### PLT-8.1 Sign-in that cannot start shows the unavailable message

- GIVEN the client configuration or the identity provider's description cannot be loaded
- WHEN a visitor opens the application
- THEN the message that MentorHub is unavailable is shown with Retry
- AND the visitor is not sent to the identity provider

#### PLT-8.2 A failing API shows the unavailable message in place of the screen

- GIVEN a signed-in user
- WHEN the current user cannot be loaded because the API fails
- THEN the unavailable message is shown in place of the requested screen, and the address stays the one requested

#### PLT-8.3 Retry after a failed sign-in start drops the callback

- GIVEN the unavailable message after a sign-in that could not start, on an address with sign-in callback parameters and other parameters
- WHEN the user chooses Retry
- THEN the page reloads on the same address without the callback parameters and with the other parameters

#### PLT-8.4 Retry repeats the failed navigation

- GIVEN the unavailable message in place of a requested screen
- WHEN the user chooses Retry
- THEN the client navigates to the requested screen again

### PLT-9 List pages

Every screen that shows a paged list (PLT-3) with a search or filters SHALL behave alike, and the capability that owns the list names only its own filters and order. The search, filters and page SHALL survive a reload, a change of the search or a filter SHALL show the first page, and an empty list SHALL say whether nothing exists yet or nothing matches.

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
