# AI engineering-use record

## Prompt used

> Implement the scoped course registration slice in this Java starter. Inspect
> the existing project first; if it has no model or build/test setup, use a
> small in-memory Java service. Cover enrollment, duplicate requests, drops,
> FIFO waitlist promotion, prerequisites, invalid requests, and concurrency
> where appropriate. State the prerequisite and stale-waitlist assumptions,
> provide focused runnable tests, and document design, setup, risks, and how
> AI suggestions were reviewed and verified.

## Relevant AI response summary

The repository has no application code or test framework, so a dependency-free
single-offering service and executable Java test harness are appropriate.
Synchronize the state transition, reject learners who fail prerequisite checks,
and re-check eligibility at promotion. Remove and skip a waiter whose
eligibility has changed, preserving FIFO order among those still eligible.
Document the in-memory and single-process limitation rather than introducing a
database or unrelated product features.

## Review decisions

| Suggestion | Decision | Application |
| --- | --- | --- |
| Use a minimal in-memory Java service because the starter has no model | Accepted | Implemented `CourseRegistrationService` with one offering per instance. |
| Check prerequisites at request time and again at promotion | Accepted | Injected a prerequisite predicate and called it at both transition points. |
| Skip and remove waitlisted learners who become ineligible | Accepted | The drop/promotion loop removes stale waiters permanently before continuing. |
| Synchronize state-changing operations and verify concurrency | Accepted | Enrollment and drop are synchronized; added a concurrent enrollment test. |
| Add a framework, persistence, or a full registration product | Rejected | These are outside the bounded scope; plain `javac` is sufficient. |

## Verification evidence

After implementation, `./run-tests.sh` compiled both source sets with
`javac --release 8` and ran the focused test harness, which reported:

```text
All CourseRegistrationService tests passed.
```

The tests independently check capacity and collection contents, duplicate
enrollment behavior, FIFO promotion past a newly ineligible waiter, prerequisite
rejection, invalid and repeated drops, invalid capacity, and concurrent
capacity/uniqueness invariants.

## Per-test status output prompt and use record

### Prompt used

> I want to know status of each test function after its execution. Add relavant
> displays to mention the same to the user for verification.

### Relevant AI response summary

Wrap each existing test invocation with a small runner that prints `PASS` after
the test completes successfully and prints `FAIL` before rethrowing an assertion,
runtime, or interruption failure. Preserve test order and fail-fast behavior.

### Review decision

| Suggestion | Decision | Application |
| --- | --- | --- |
| Display each executed test function's result | Accepted | Added `runTest` and wrapped each test invocation in `CourseRegistrationServiceTest`. A failed test is reported and its original failure is rethrown; later tests are not run. |

### Verification evidence

Ran `sh run-tests.sh` in the terminal. The test output showed an individual
`PASS: <test function>` line for every successful test, followed by
`All CourseRegistrationService tests passed.` The latest complete output is
recorded in the following verification section.

## Follow-up prompt and use record

### Prompt used

> Include test functions to validate the service when every id in waitlist is
> ineligible to be enrolled.

### Relevant AI response summary

Add a focused test that makes every queued learner ineligible before the
enrolled learner drops. Verify the drop succeeds without promoting anyone, all
stale waiters are removed, and the freed seat can be enrolled by an eligible
learner.

### Review decision

| Suggestion | Decision | Application |
| --- | --- | --- |
| Cover a waitlist where every learner has become ineligible | Accepted | Added `leavesSeatOpenWhenAllWaitlistedLearnersBecomeIneligible` to `CourseRegistrationServiceTest`; it checks the drop result, empty enrollment and waitlist snapshots, and successful enrollment into the open seat. No production service change was needed. |

### Verification evidence

After adding the test, ran `sh run-tests.sh` in the terminal. It compiled the
Java sources with `javac --release 8` and reported:

```text
PASS: enrollsWhenCapacityIsAvailable
PASS: queuesEligibleLearnersOnceWhenFull
PASS: promotesEarliestWaitlistedLearner
PASS: skipsWaitlistedLearnersWhoBecomeIneligible
PASS: leavesSeatOpenWhenAllWaitlistedLearnersBecomeIneligible
PASS: rejectsLearnersMissingPrerequisites
PASS: rejectsInvalidAndRepeatedDropsWithoutChangingState
PASS: concurrentRequestsPreserveCapacityAndUniqueness
All CourseRegistrationService tests passed.
```

The compiler also emitted Java 8 source/target obsolescence warnings; compilation
and all tests completed successfully.

Follow-up prompt and use record

### Manual testing - Test sensitivity check

Modified the code in below function: 

>private static void promotesEarliestWaitlistedLearner() {
        CourseRegistrationService service = service(1, setOf("ada", "ben","cam"));
        service.enroll("ada");
        service.enroll("ben");
        service.enroll("cam");

Modified: To remove "ben" from eligible list --> To test if the next eligible learner id gets promoted to enrolled list and the one expected in test was missing.

  >private static void promotesEarliestWaitlistedLearner() {
        CourseRegistrationService service = service(1, setOf("ada","cam"));
        service.enroll("ada");
        service.enroll("ben");
        service.enroll("cam");

### Verification evidence

After adding the test, ran `sh run-tests.sh` in the terminal. It compiled the
Java sources with `javac --release 8` and reported:

```text
warning: [options] source value 8 is obsolete and will be removed in a future release
warning: [options] target value 8 is obsolete and will be removed in a future release
warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.
3 warnings
PASS: enrollsWhenCapacityIsAvailable
PASS: queuesEligibleLearnersOnceWhenFull
FAIL: promotesEarliestWaitlistedLearner
Exception in thread "main" java.lang.AssertionError: expected <ben> but was <cam>
        at courseregistration.CourseRegistrationServiceTest.equal(CourseRegistrationServiceTest.java:167)
        at courseregistration.CourseRegistrationServiceTest.promotesEarliestWaitlistedLearner(CourseRegistrationServiceTest.java:67)
        at courseregistration.CourseRegistrationServiceTest.runTest(CourseRegistrationServiceTest.java:31)
        at courseregistration.CourseRegistrationServiceTest.main(CourseRegistrationServiceTest.java:16)
```

The compiler also emitted Java 8 source/target obsolescence warnings; compilation
was successful, but the test function: failed due to below reason.

result.getPromotedLearner() returned "cam" as that was promoted once "ada" was dropped, the line 
 >equal("ben", result.getPromotedLearner().orElse(null)); 

--> expected "ben" to be returned instead.

## Follow-up prompt and use record

### Prompt used

> Can the code be fixed for below points
> - Partial state change if prerequisite evaluation throws: `drop` removes the
>   enrolled learner before checking waitlist eligibility. If the predicate
>   throws at the promotion check, the exception propagates, but the seat
>   remains vacant and the waitlist is not processed.

### Relevant AI response summary

Stage waitlist eligibility decisions before mutating enrollment or waitlist
state. If a prerequisite check throws, allow the exception to propagate while
leaving both collections unchanged. Add a regression test that also verifies
an earlier ineligible waiter is not removed when a later eligibility check
throws.

### Review decision

| Suggestion | Decision | Application |
| --- | --- | --- |
| Avoid partial drop and waitlist cleanup when promotion eligibility throws | Accepted | `drop` checks waitlist candidates before applying the staged removal/promotion; added `preservesStateWhenPromotionEligibilityCheckThrows`. Updated `DESIGN.md` with the failure guarantee. |

### Verification evidence

Ran `./run-tests.sh`. It compiled the Java sources with `javac --release 8`
and all tests passed, including:

```text
PASS: preservesStateWhenPromotionEligibilityCheckThrows
All CourseRegistrationService tests passed.
```

The compiler emitted Java 8 source/target obsolescence warnings; compilation
and all tests completed successfully.

## Follow-up prompt and use record

### Prompt used

> State the eligibility impacted on learner ids based on the white spaces
> included when user prompts - in design and AI usage file if not included

### Relevant AI response summary

The user clarified that IDs consisting only of Unicode whitespace should be
accepted and leading/trailing whitespace should be trimmed. Implemented
Unicode `White_Space` trimming at enrollment and drop boundaries. Non-empty
whitespace-only input normalizes to the empty ID; truly empty input remains
invalid. Internal whitespace is retained. The prerequisite predicate and
duplicate checks operate on normalized IDs.

### Review decision

| Suggestion | Decision | Application |
| Recognize Unicode whitespace, allow whitespace-only IDs, and normalize IDs | Accepted | Added Unicode boundary trimming in `CourseRegistrationService`, added `normalizesUnicodeWhitespaceInLearnerIds`, and updated `DESIGN.md`. |

### Verification evidence

Ran `./run-tests.sh`; the test validates each Unicode `White_Space` code point,
boundary normalization for enrollment and drop, duplicate detection,
whitespace-only input, truly empty input, and preserved internal whitespace.
It passed, and the complete test harness reported:

```text
PASS: normalizesUnicodeWhitespaceInLearnerIds
All CourseRegistrationService tests passed.
```

