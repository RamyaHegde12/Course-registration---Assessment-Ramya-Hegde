# Course registration service design

## Task decomposition

1. Define one offering's capacity, eligibility check, enrollment state, and
   waitlist state.
2. Implement enrollment and drop as atomic operations, including FIFO
   promotion and explicit result statuses.
3. Exercise normal, duplicate, ineligible, invalid, stale-waitlist, and
   concurrent cases with a dependency-free test harness.
4. Document how to run the tests, the AI-assisted decisions, and the limits of
   the in-memory implementation.

## Service and data boundary

`CourseRegistrationService` represents exactly one offering. Its constructor
takes a positive capacity and a `Predicate<String>` that answers whether a
learner currently satisfies the offering's prerequisite. The predicate is the
boundary to prerequisite/learner data; the service does not own those records.
The API is `enroll(learnerId)`, `drop(learnerId)`, and read-only state snapshots.
Enrollment and drop return explicit statuses; a successful drop also reports
an optional promoted learner.

Enrolled IDs are held in insertion order, and waitlisted IDs in FIFO order.
The service is in-memory and has no external API, database, course catalog, or
identity integration.

## Assumptions and invariants

- IDs must be non-null and non-empty. Leading and trailing Unicode
  `White_Space` characters are trimmed before eligibility, duplicate, enrollment,
  or drop checks. A non-empty whitespace-only input is accepted and normalizes
  to the empty ID; an actually empty input is invalid. Internal whitespace is
  preserved, and the prerequisite predicate receives the normalized ID.
- Prerequisite completion is checked at each enrollment request. A learner
  missing the prerequisite is rejected and is never added to either collection.
- Prerequisite eligibility may change while a learner waits. At promotion, the
  service checks current eligibility again; newly ineligible waitlisted learners
  are removed permanently and skipped. The first currently eligible learner
  in FIFO order is promoted.
- A learner may appear at most once across enrolled and waitlisted state.
- Enrollment count never exceeds capacity; dropping a non-enrolled learner
  does not alter either collection.
- The eligibility predicate is expected to be safe to call from synchronized
  service operations. Its source may change externally, but changes are
  observed only when the predicate is next evaluated.
- Drop evaluates and stages waitlist decisions before changing enrollment state.
  If a prerequisite check throws during promotion, the exception propagates and
  both enrollment and waitlist state remain unchanged.

## Concurrency and persistence

Each state-changing operation and state snapshot synchronizes on the service,
so capacity checks, duplicate checks, drops, and promotions are atomic within a
single process. The eligibility check is made within that operation's critical
section. The concurrent test verifies a burst of distinct enrollment requests
cannot exceed capacity or lose a registration.

This is not durable or multi-process locking: process exit discards all state,
and multiple service instances do not coordinate. A persistent deployment
would need transactional storage and a lock/serialization strategy covering
capacity and FIFO promotion; that is deliberately outside this exercise.

## Run and verify

From the repository root, run `./run-tests.sh`. It uses `javac --release 8`
and `java -ea`; no build tool or third-party dependency is needed. The test
harness covers free capacity, full capacity, duplicate requests, FIFO
promotion, eligibility failure and revalidation, invalid/repeated drops, and
concurrent enrollment. It also checks that all Unicode `White_Space` characters
are trimmed at ID boundaries, whitespace-only non-empty inputs normalize to the
empty ID, truly empty inputs are invalid, and internal whitespace is preserved.

## Limitations and follow-up risks

- No persistence, recovery, cross-process concurrency, authentication, or
  service/API transport is provided.
- Prerequisite truth and its freshness are delegated to the supplied predicate;
  predicate failures propagate to the caller rather than being converted to a
  successful result. A failure during drop's promotion check does not partially
  apply the drop or waitlist cleanup.
- Waitlist cleanup occurs when a seat is freed, not continuously. An ineligible
  learner remains in the displayed waitlist until promotion attempts it, then
  is removed and skipped.
