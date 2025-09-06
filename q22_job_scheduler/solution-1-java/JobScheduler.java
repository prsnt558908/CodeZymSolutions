import java.util.*;
import java.util.stream.Collectors;

/**
 * Capability-Aware, Criteria-Driven Job Scheduler.
 *
 * Methods to implement (as per prompt):
 *  - void addMachine(String machineId, String[] capabilities)
 *  - String assignMachineToJob(String jobId, String[] capabilitiesRequired, int criteria)
 *  - void jobCompleted(String jobId)
 *
 * Features:
 *  - Capability matching is case-insensitive (normalized: trim, lowercase, single-space).
 *  - Assignment criteria are pluggable via a Strategy registry:
 *      0 -> Least Unfinished Jobs (tie-break: lexicographically smallest machineId)
 *      1 -> Most Finished Jobs (tie-break: lexicographically smallest machineId)
 *  - If multiple machines tie under a criterion, pick lexicographically smallest machineId.
 *  - If no compatible machine exists, return "" and do NOT record the job.
 *  - If assignMachineToJob is called again with an existing jobId, return its previously assigned machineId.
 */
public class JobScheduler {

    // ==== Core State ====
    private final Map<String, Machine> machines = new HashMap<>();
    private final Map<String, Job> jobs = new HashMap<>();
    // capability (normalized) -> set of machineIds
    private final Map<String, Set<String>> capabilityIndex = new HashMap<>();

    // criteria code -> strategy
    private final Map<Integer, AssignmentStrategy> strategies = new HashMap<>();

    public JobScheduler() {
        // Register default strategies (extensible)
        strategies.put(0, new LeastUnfinishedStrategy());
        strategies.put(1, new MostFinishedStrategy());
    }

    // ========== Public API ==========

    /**
     * Add a machine with its capabilities.
     * machineId must be unique and non-blank.
     * capabilities are case-insensitive tokens; duplicates and spacing are normalized.
     */
    public synchronized void addMachine(String machineId, String[] capabilities) {
        String id = normalizeId(machineId);
        requireNonBlank(id, "machineId");

        if (machines.containsKey(id)) {
            throw new IllegalArgumentException("machineId already exists: " + id);
        }

        Set<String> caps = normalizeCapabilities(capabilities);
        Machine m = new Machine(id, caps);
        machines.put(id, m);

        // Update inverted index
        for (String cap : caps) {
            capabilityIndex.computeIfAbsent(cap, k -> new HashSet<>()).add(id);
        }
    }

    /**
     * Assign a compatible machine to the given job according to the criteria.
     * Returns machineId, or "" if no compatible machine exists (job is not recorded in that case).
     * If called again with an existing jobId, returns the already assigned machineId.
     */
    public synchronized String assignMachineToJob(String jobId, String[] capabilitiesRequired, int criteria) {
        String jid = normalizeId(jobId);
        requireNonBlank(jid, "jobId");

        // If job already exists, return the previously assigned machine id
        Job existing = jobs.get(jid);
        if (existing != null) {
            return existing.assignedMachineId; // always non-null if recorded
        }

        // Find compatible machines via capability intersection
        Set<String> required = normalizeCapabilities(capabilitiesRequired);

        Set<String> candidateIds;
        if (required.isEmpty()) {
            // If no capabilities required, all machines are candidates
            candidateIds = new HashSet<>(machines.keySet());
        } else {
            List<Set<String>> lists = new ArrayList<>();
            for (String cap : required) {
                Set<String> ids = capabilityIndex.get(cap);
                if (ids == null || ids.isEmpty()) {
                    return ""; // no machine has one of the required caps
                }
                lists.add(ids);
            }
            // Intersect starting from the smallest set to short-circuit early
            lists.sort(Comparator.comparingInt(Set::size));
            candidateIds = new HashSet<>(lists.get(0));
            for (int i = 1; i < lists.size() && !candidateIds.isEmpty(); i++) {
                candidateIds.retainAll(lists.get(i));
            }
            if (candidateIds.isEmpty()) {
                return "";
            }
        }

        // Map to Machine objects
        List<Machine> candidates = candidateIds.stream()
                .map(machines::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return "";
        }

        // Select using requested strategy (default to 0 if unknown)
        AssignmentStrategy strategy = strategies.getOrDefault(criteria, strategies.get(0));
        Machine chosen = strategy.select(candidates);

        // Record job + update counters
        Job job = new Job(jid, chosen.id);
        jobs.put(jid, job);
        chosen.unfinishedCount += 1;

        return chosen.id;
    }

    /**
     * Mark the given job as completed. Assumes jobId is valid and was assigned.
     * Idempotent: re-calling for a completed job has no effect.
     */
    public synchronized void jobCompleted(String jobId) {
        String jid = normalizeId(jobId);
        requireNonBlank(jid, "jobId");

        Job job = jobs.get(jid);
        if (job == null) {
            throw new IllegalArgumentException("Unknown jobId: " + jid);
        }
        if (job.status == JobStatus.COMPLETED) {
            return; // idempotent no-op
        }
        Machine m = machines.get(job.assignedMachineId);
        if (m == null) {
            throw new IllegalStateException("Assigned machine not found for job: " + jid);
        }

        // Update counters atomically within synchronized method
        if (m.unfinishedCount > 0) m.unfinishedCount -= 1;
        m.finishedCount += 1;
        job.status = JobStatus.COMPLETED;
    }

    // ========== Strategy Pattern ==========

    private interface AssignmentStrategy {
        Machine select(Collection<Machine> candidates);
    }

    /** Criteria 0: Least Unfinished Jobs; tie-break by lexicographically smallest machineId. */
    private static class LeastUnfinishedStrategy implements AssignmentStrategy {
        @Override
        public Machine select(Collection<Machine> candidates) {
            return candidates.stream()
                    .min(Comparator
                            .comparingInt((Machine m) -> m.unfinishedCount)
                            .thenComparing(m -> m.id)) // lexicographic tie-break
                    .orElseThrow(() -> new IllegalStateException("No candidates"));
        }
    }

    /** Criteria 1: Most Finished Jobs; tie-break by lexicographically smallest machineId. */
    private static class MostFinishedStrategy implements AssignmentStrategy {
        @Override
        public Machine select(Collection<Machine> candidates) {
            return candidates.stream()
                    .min(Comparator
                            .comparingInt((Machine m) -> -m.finishedCount) // invert to "max"
                            .thenComparing(m -> m.id))
                    .orElseThrow(() -> new IllegalStateException("No candidates"));
        }
    }

    // ========== Model Types ==========

    private enum JobStatus { ASSIGNED, COMPLETED }

    private static final class Job {
        final String jobId;
        final String assignedMachineId;
        JobStatus status = JobStatus.ASSIGNED;

        Job(String jobId, String assignedMachineId) {
            this.jobId = jobId;
            this.assignedMachineId = assignedMachineId;
        }
    }

    private static final class Machine {
        final String id;
        final Set<String> capabilities; // normalized tokens
        int unfinishedCount = 0;
        int finishedCount = 0;

        Machine(String id, Set<String> capabilities) {
            this.id = id;
            this.capabilities = Collections.unmodifiableSet(new HashSet<>(capabilities));
        }

        @Override
        public String toString() {
            return "Machine{" +
                    "id='" + id + '\'' +
                    ", unfinished=" + unfinishedCount +
                    ", finished=" + finishedCount +
                    '}';
        }
    }

    // ========== Helpers ==========

    private static void requireNonBlank(String s, String field) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
    }

    private static String normalizeId(String s) {
        return s == null ? null : s.trim();
    }

    private static Set<String> normalizeCapabilities(String[] caps) {
        Set<String> out = new HashSet<>();
        if (caps == null) return out;
        for (String c : caps) {
            if (c == null) continue;
            String norm = normalizeCapabilityToken(c);
            if (!norm.isEmpty()) out.add(norm);
        }
        return out;
    }

    /** Normalize a capability token: trim, lowercase, collapse internal whitespace to single space. */
    private static String normalizeCapabilityToken(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        // collapse multiple spaces/tabs/newlines into single space
        s = s.replaceAll("\\s+", " ");
        return s;
    }

   
}
