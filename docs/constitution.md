# Constitution

The rules every change to MentorHub must keep, for people and agents alike. Detailed behavior lives in `docs/requirements/` and architectural choices in `docs/architecture/decisions/`; neither relaxes a rule here.

## Amending

- Only the project owner amends this document. Anyone may propose a rule, including a decision that introduces one.
- A change that conflicts with a rule needs the project owner's decision. Requirements, decisions and plans carry no exceptions to a rule.

## Product

- **C-1.** Keep MentorHub the internal place for one training center's mentoring process; do not grow it into a general-purpose LMS.
  Why: it is built and changed by the team that uses it, and generality would cost without serving anyone.

## Engineering

- **C-2.** Define every API change in `api/openapi.yaml` before the code that implements or uses it (decision 0002).
  Why: both sides and the tests rely on one agreed contract, and a mismatch fails the build, not production.
- **C-3.** Deliver a feature as a vertical slice: contract, migration, backend, frontend, tests.
  Why: a partial slice is behavior nobody can verify.
- **C-4.** Work is done when the verification in `docs/development/` passes for every area it touches.
  Why: "should work" is not evidence.

## Spec-driven development

- **C-5.** Living requirements are the only description of behavior: a behavior change goes through them, and the code follows.
  Why: a document that disagrees with the code misleads everyone who trusts it.
- **C-6.** Requirements say what the system does, never how it is built.
  Why: implementation details change more often than behavior, and make requirements large and stale.
- **C-7.** Write tests from the requirements before the code; each test cites the scenario it checks.
  Why: a test written from the implementation only confirms the implementation.
- **C-8.** Never change a test to make the code pass; when a test and the code disagree, the requirement decides.
  Why: a test bent to fit the code proves nothing.
- **C-9.** Fix a flawed decision or requirement where it is written; do not work around it in the code.
  Why: workarounds and buried exceptions make the documents untrue.

## Language

- **C-10.** Write all project artifacts in English: code, documents, UI text, commit messages.
  Why: one language keeps them searchable and consistent.
