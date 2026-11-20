package courseregistration;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class CourseRegistrationServiceTest {
    public static void main(String[] args) throws InterruptedException {
        runTest("enrollsWhenCapacityIsAvailable",
                CourseRegistrationServiceTest::enrollsWhenCapacityIsAvailable);
        runTest("queuesEligibleLearnersOnceWhenFull",
                CourseRegistrationServiceTest::queuesEligibleLearnersOnceWhenFull);
        runTest("promotesEarliestWaitlistedLearner",
                CourseRegistrationServiceTest::promotesEarliestWaitlistedLearner);
        runTest("skipsWaitlistedLearnersWhoBecomeIneligible",
                CourseRegistrationServiceTest::skipsWaitlistedLearnersWhoBecomeIneligible);
        runTest("preservesStateWhenPromotionEligibilityCheckThrows",
                CourseRegistrationServiceTest::preservesStateWhenPromotionEligibilityCheckThrows);
        runTest("leavesSeatOpenWhenAllWaitlistedLearnersBecomeIneligible",
                CourseRegistrationServiceTest::leavesSeatOpenWhenAllWaitlistedLearnersBecomeIneligible);
        runTest("rejectsLearnersMissingPrerequisites",
                CourseRegistrationServiceTest::rejectsLearnersMissingPrerequisites);
        runTest("normalizesUnicodeWhitespaceInLearnerIds",
                CourseRegistrationServiceTest
                        ::normalizesUnicodeWhitespaceInLearnerIds);
        runTest("rejectsInvalidAndRepeatedDropsWithoutChangingState",
                CourseRegistrationServiceTest::rejectsInvalidAndRepeatedDropsWithoutChangingState);
        runTest("concurrentRequestsPreserveCapacityAndUniqueness",
                CourseRegistrationServiceTest::concurrentRequestsPreserveCapacityAndUniqueness);
        System.out.println("All CourseRegistrationService tests passed.");
    }

    private static void runTest(String name, TestCase test) throws InterruptedException {
        try {
            test.run();
            System.out.println("PASS: " + name);
        } catch (AssertionError | RuntimeException | InterruptedException failure) {
            System.out.println("FAIL: " + name);
            throw failure;
        }
    }

    private static void enrollsWhenCapacityIsAvailable() {
        CourseRegistrationService service = service(2, setOf("ada"));

        equal(CourseRegistrationService.EnrollmentStatus.ENROLLED, service.enroll("ada"));
        equal(1, service.getEnrollmentCount());
        equal(Collections.singletonList("ada"), service.getEnrolledLearners());
        equal(2, service.getCapacity());
    }

    private static void queuesEligibleLearnersOnceWhenFull() {
        CourseRegistrationService service = service(1, setOf("ada", "ben"));
        equal(CourseRegistrationService.EnrollmentStatus.ENROLLED, service.enroll("ada"));
        equal(CourseRegistrationService.EnrollmentStatus.WAITLISTED, service.enroll("ben"));

        equal(CourseRegistrationService.EnrollmentStatus.ALREADY_REGISTERED, service.enroll("ben"));
        equal(CourseRegistrationService.EnrollmentStatus.ALREADY_REGISTERED, service.enroll("ada"));
        equal(Collections.singletonList("ben"), service.getWaitlistedLearners());
        equal(1, service.getEnrollmentCount());
    }

    private static void promotesEarliestWaitlistedLearner() {
        CourseRegistrationService service = service(1, setOf("ada", "ben","cam"));
        service.enroll("ada");
        service.enroll("ben");
        service.enroll("cam");

        CourseRegistrationService.DropResult result = service.drop("ada");

        equal("ben", result.getPromotedLearner().orElse(null));
        equal(Collections.singletonList("cam"), service.getWaitlistedLearners());
    }

    private static void skipsWaitlistedLearnersWhoBecomeIneligible() {
        Set<String> eligible = setOf("ada", "ben", "cam");
        CourseRegistrationService service = service(1, eligible);
        service.enroll("ada");
        service.enroll("ben");
        service.enroll("cam");
        eligible.remove("ben");

        CourseRegistrationService.DropResult result = service.drop("ada");

        equal(CourseRegistrationService.DropStatus.DROPPED, result.getStatus());
        equal("cam", result.getPromotedLearner().orElse(null));
        equal(Collections.singletonList("cam"), service.getEnrolledLearners());
        equal(Collections.emptyList(), service.getWaitlistedLearners());
    }

    private static void preservesStateWhenPromotionEligibilityCheckThrows() {
        Set<String> eligible = setOf("ada", "ben", "cam");
        boolean[] throwForCam = {false};
        CourseRegistrationService service = new CourseRegistrationService(1, learnerId -> {
            if (throwForCam[0] && learnerId.equals("cam")) {
                throw new IllegalStateException("prerequisite lookup failed");
            }
            return eligible.contains(learnerId);
        });
        service.enroll("ada");
        service.enroll("ben");
        service.enroll("cam");
        eligible.remove("ben");
        throwForCam[0] = true;

        expectThrows(IllegalStateException.class, () -> service.drop("ada"));

        equal(Collections.singletonList("ada"), service.getEnrolledLearners());
        equal(Arrays.asList("ben", "cam"), service.getWaitlistedLearners());
        equal(1, service.getEnrollmentCount());

        throwForCam[0] = false;
        equal("cam", service.drop("ada").getPromotedLearner().orElse(null));
    }

    private static void leavesSeatOpenWhenAllWaitlistedLearnersBecomeIneligible() {
        Set<String> eligible = setOf("ada", "ben", "cam", "dan");
        CourseRegistrationService service = service(1, eligible);
        service.enroll("ada");
        service.enroll("ben");
        service.enroll("cam");
        eligible.remove("ben");
        eligible.remove("cam");

        CourseRegistrationService.DropResult result = service.drop("ada");

        equal(CourseRegistrationService.DropStatus.DROPPED, result.getStatus());
        equal(false, result.getPromotedLearner().isPresent());
        equal(Collections.emptyList(), service.getEnrolledLearners());
        equal(Collections.emptyList(), service.getWaitlistedLearners());
        equal(CourseRegistrationService.EnrollmentStatus.ENROLLED, service.enroll("dan"));
        equal(Collections.singletonList("dan"), service.getEnrolledLearners());
    }

    private static void rejectsLearnersMissingPrerequisites() {
        CourseRegistrationService service = service(1, setOf("ada"));

        equal(CourseRegistrationService.EnrollmentStatus.PREREQUISITE_NOT_MET,
                service.enroll("ben"));
        service.enroll("ada");
        equal(CourseRegistrationService.EnrollmentStatus.PREREQUISITE_NOT_MET,
                service.enroll("ben"));
        equal(Collections.singletonList("ada"), service.getEnrolledLearners());
        equal(Collections.emptyList(), service.getWaitlistedLearners());
    }

    private static void normalizesUnicodeWhitespaceInLearnerIds() {
        int[] unicodeWhitespace = {
            0x0009, 0x000A, 0x000B, 0x000C, 0x000D, 0x0020, 0x0085, 0x00A0,
            0x1680, 0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006,
            0x2007, 0x2008, 0x2009, 0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000
        };
        for (int codePoint : unicodeWhitespace) {
            String whitespace = new String(Character.toChars(codePoint));
            CourseRegistrationService service = service(1, setOf("ada"));
            equal(CourseRegistrationService.EnrollmentStatus.ENROLLED,
                    service.enroll(whitespace + "ada" + whitespace));
            equal(Collections.singletonList("ada"), service.getEnrolledLearners());
            equal(CourseRegistrationService.EnrollmentStatus.ALREADY_REGISTERED,
                    service.enroll("ada"));
            equal(CourseRegistrationService.DropStatus.DROPPED,
                    service.drop(whitespace + "ada" + whitespace).getStatus());
        }

        CourseRegistrationService whitespaceOnlyService = service(1, setOf(""));
        equal(CourseRegistrationService.EnrollmentStatus.ENROLLED,
                whitespaceOnlyService.enroll(
                        new String(Character.toChars(0x00A0))
                                + new String(Character.toChars(0x3000))));
        equal(Collections.singletonList(""),
                whitespaceOnlyService.getEnrolledLearners());
        equal(CourseRegistrationService.DropStatus.DROPPED,
                whitespaceOnlyService.drop(
                        new String(Character.toChars(0x0085))
                                + new String(Character.toChars(0x2000))).getStatus());
        equal(CourseRegistrationService.EnrollmentStatus.INVALID_REQUEST,
                whitespaceOnlyService.enroll(""));

        CourseRegistrationService internalWhitespaceService = service(1, setOf("ad a"));
        equal(CourseRegistrationService.EnrollmentStatus.ENROLLED,
                internalWhitespaceService.enroll("ad a"));
        equal(Collections.singletonList("ad a"),
                internalWhitespaceService.getEnrolledLearners());
    }

    private static void rejectsInvalidAndRepeatedDropsWithoutChangingState() {
        CourseRegistrationService service = service(1, setOf("ada"));
        equal(CourseRegistrationService.EnrollmentStatus.INVALID_REQUEST, service.enroll(""));
        equal(CourseRegistrationService.EnrollmentStatus.INVALID_REQUEST, service.enroll(null));
        equal(CourseRegistrationService.DropStatus.INVALID_REQUEST, service.drop(null).getStatus());
        equal(CourseRegistrationService.DropStatus.NOT_ENROLLED, service.drop("ben").getStatus());

        service.enroll("ada");
        equal(CourseRegistrationService.DropStatus.DROPPED, service.drop("ada").getStatus());
        equal(CourseRegistrationService.DropStatus.NOT_ENROLLED, service.drop("ada").getStatus());
        equal(0, service.getEnrollmentCount());
        equal(Collections.emptyList(), service.getWaitlistedLearners());

        expectThrows(IllegalArgumentException.class,
                () -> new CourseRegistrationService(0, learnerId -> true));
    }

    private static void concurrentRequestsPreserveCapacityAndUniqueness()
            throws InterruptedException {
        final int requestCount = 12;
        CourseRegistrationService service = new CourseRegistrationService(1, learnerId -> true);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] threads = new Thread[requestCount];

        for (int i = 0; i < requestCount; i++) {
            final String learnerId = "learner-" + i;
            threads[i] = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    service.enroll(learnerId);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    failure.compareAndSet(null, error);
                } catch (RuntimeException error) {
                    failure.compareAndSet(null, error);
                }
            });
            threads[i].start();
        }

        ready.await();
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        if (failure.get() != null) {
            throw new AssertionError("concurrent enrollment failed", failure.get());
        }
        equal(1, service.getEnrollmentCount());
        equal(requestCount - 1, service.getWaitlistedLearners().size());
        equal(requestCount, service.getEnrolledLearners().size()
                + service.getWaitlistedLearners().size());
    }

    private static CourseRegistrationService service(int capacity, Set<String> eligible) {
        return new CourseRegistrationService(capacity, eligible::contains);
    }

    private static Set<String> setOf(String... learnerIds) {
        return new HashSet<>(Arrays.asList(learnerIds));
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void expectThrows(Class<? extends Throwable> expected, Runnable action) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                return;
            }
            throw new AssertionError("expected " + expected.getSimpleName()
                    + " but was " + actual, actual);
        }
        throw new AssertionError("expected " + expected.getSimpleName() + " to be thrown");
    }

    @FunctionalInterface
    private interface TestCase {
        void run() throws InterruptedException;
    }
}
