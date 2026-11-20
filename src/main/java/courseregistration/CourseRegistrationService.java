package courseregistration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * In-memory registration state for one course offering.
 */
public final class CourseRegistrationService {
    public enum EnrollmentStatus {
        ENROLLED,
        WAITLISTED,
        ALREADY_REGISTERED,
        PREREQUISITE_NOT_MET,
        INVALID_REQUEST
    }

    public enum DropStatus {
        DROPPED,
        NOT_ENROLLED,
        INVALID_REQUEST
    }

    public static final class DropResult {
        private final DropStatus status;
        private final String promotedLearner;

        private DropResult(DropStatus status, String promotedLearner) {
            this.status = status;
            this.promotedLearner = promotedLearner;
        }

        public DropStatus getStatus() {
            return status;
        }

        public Optional<String> getPromotedLearner() {
            return Optional.ofNullable(promotedLearner);
        }
    }

    private final int capacity;
    private final Predicate<String> prerequisiteSatisfied;
    private final Set<String> enrolled = new LinkedHashSet<>();
    private final ArrayDeque<String> waitlist = new ArrayDeque<>();

    /**
     * @param capacity the positive seat limit for this offering
     * @param prerequisiteSatisfied checks a learner's current prerequisite status
     */
    public CourseRegistrationService(int capacity, Predicate<String> prerequisiteSatisfied) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.prerequisiteSatisfied = Objects.requireNonNull(
                prerequisiteSatisfied, "prerequisiteSatisfied");
    }

    public synchronized EnrollmentStatus enroll(String learnerId) {
        if (!isValidLearnerId(learnerId)) {
            return EnrollmentStatus.INVALID_REQUEST;
        }
        learnerId = normalizeLearnerId(learnerId);
        if (enrolled.contains(learnerId) || waitlist.contains(learnerId)) {
            return EnrollmentStatus.ALREADY_REGISTERED;
        }
        if (!prerequisiteSatisfied.test(learnerId)) {
            return EnrollmentStatus.PREREQUISITE_NOT_MET;
        }
        if (enrolled.size() < capacity) {
            enrolled.add(learnerId);
            return EnrollmentStatus.ENROLLED;
        }
        waitlist.addLast(learnerId);
        return EnrollmentStatus.WAITLISTED;
    }

    public synchronized DropResult drop(String learnerId) {
        if (!isValidLearnerId(learnerId)) {
            return new DropResult(DropStatus.INVALID_REQUEST, null);
        }
        learnerId = normalizeLearnerId(learnerId);
        if (!enrolled.contains(learnerId)) {
            return new DropResult(DropStatus.NOT_ENROLLED, null);
        }

        List<String> ineligible = new ArrayList<>();
        String promotedLearner = null;
        for (String next : waitlist) {
            if (prerequisiteSatisfied.test(next)) {
                promotedLearner = next;
                break;
            }
            ineligible.add(next);
        }

        enrolled.remove(learnerId);
        for (String learner : ineligible) {
            waitlist.remove(learner);
        }
        if (promotedLearner != null) {
            waitlist.removeFirstOccurrence(promotedLearner);
            enrolled.add(promotedLearner);
        }
        return new DropResult(DropStatus.DROPPED, promotedLearner);
    }

    public synchronized int getEnrollmentCount() {
        return enrolled.size();
    }

    public int getCapacity() {
        return capacity;
    }

    public synchronized List<String> getEnrolledLearners() {
        return Collections.unmodifiableList(new ArrayList<>(enrolled));
    }

    public synchronized List<String> getWaitlistedLearners() {
        return Collections.unmodifiableList(new ArrayList<>(waitlist));
    }

    private static boolean isValidLearnerId(String learnerId) {
        return learnerId != null && !learnerId.isEmpty();
    }

    private static String normalizeLearnerId(String learnerId) {
        int start = 0;
        int end = learnerId.length();
        while (start < end) {
            int codePoint = learnerId.codePointAt(start);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = learnerId.codePointBefore(end);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return learnerId.substring(start, end);
    }

    private static boolean isUnicodeWhitespace(int codePoint) {
        // Unicode White_Space includes no-break spaces excluded by Character.isWhitespace.
        return (codePoint >= 0x0009 && codePoint <= 0x000D)
                || codePoint == 0x0020
                || codePoint == 0x0085
                || codePoint == 0x00A0
                || codePoint == 0x1680
                || (codePoint >= 0x2000 && codePoint <= 0x200A)
                || codePoint == 0x2028
                || codePoint == 0x2029
                || codePoint == 0x202F
                || codePoint == 0x205F
                || codePoint == 0x3000;
    }
}
