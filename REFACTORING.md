# Responsibility-based refactoring

## Scope and acceptance criteria

Split the service and UI monoliths into small modules with explicit responsibilities.
Preserve HTTP routes, request/response JSON, validation, financial calculations,
transaction boundaries, pessimistic locks, authentication and the Russian UI.
Database migrations and deployment configuration are outside this refactoring.

## Stages

1. Establish the existing backend test and frontend build baseline.
2. Extract backend services by use case: people, pricing, subscriptions,
   finalized subscription edits, attendance, charges, payments, balances and reports.
   Route HTTP requests through focused controllers. Reorganize service tests around
   these responsibilities while retaining API and concurrency regression coverage.
3. Extract frontend feature screens, forms and dialogs. Share only common UI,
   formatting and request coordination. Separate payload construction and actions
   from rendering. Remove the two unused legacy subscription implementations.
4. Add frontend regression tests and run them in the Maven verification lifecycle.
   Rerun focused tests after each extraction, then perform a full verification.
5. Independently review the resulting changes, fix actionable findings and rerun
   affected checks before the final review.

## High-risk behavior to retain

- A payment and its initial allocation either both persist or both roll back.
- Concurrent allocations and charge edits cannot overallocate a payment/charge.
- Finalized subscription changes preserve unaffected charges and require
  confirmation before changing allocated payments.
- Attendance and drop-in capacity rules remain unchanged.
- Financial reports continue to use allocation totals and subscription snapshots.
- UI mutations retain their submit lock and success/error reporting.
- Selecting a different person cannot display a late balance response for the
  previously selected person.
- Nested dialogs retain native close-event isolation.

## Validation

Use the existing Java 21/Maven and Node versions. Run local Java tests against the
test H2 database; no production database or external application run is needed.
Retain the API/OpenAPI tests as contract checks. New frontend tests should cover
payloads, asynchronous coordination and reusable UI behavior without adding a
second application framework.

## Resulting boundaries

- `api`: separate HTTP controllers for people, pricing, subscriptions,
  attendance, charges, payments, balances, financial reports and accounts.
- `api/dto`: 38 individually named request/response records, retaining the
  original JSON fields, constraints, constructor overloads and schema names.
- `service`: use-case services backed by small subscription access, member
  selection, fee planning, charge reconciliation and view mapping helpers.
  There is no forwarding bookkeeping facade or replacement service monolith.
- `frontend/src/features`: feature screens, their forms/dialogs, request actions
  and pure models. Subscription drafts, allocation calculations, attendance
  ordering and report export can be tested separately from rendering.
- `frontend/src/hooks`: authentication, loading, mutation coordination and
  selected balance/report state. `App.jsx` composes these with navigation.

The original 31 HTTP method/path pairs remain available. Backend regression
coverage includes API contracts, allocation concurrency and real transaction
rollback without an enclosing test transaction. Frontend regression coverage
includes interactions, payloads, request races and modal lifetimes.

The full Maven verification builds the UI, runs both test suites and packages the
executable JAR from a clean output directory. Native browser focus behavior and
PostgreSQL-specific runtime behavior remain outside these H2/React renderer tests.

Final local verification passed: 49 Java tests, 25 frontend tests, production UI
build and executable JAR packaging. The local Windows sandbox required an explicit
Mockito Java agent and access to the existing npm cache; dependency versions were
retained, with only the matching React test renderer added as a dev dependency.
Independent review found no actionable issues after the dialog lifetime fix.
