import java.util.*;

/**
 * TrainPlatformManager
 *
 * Rules implemented per problem statement:
 * - Platforms are indexed 0..platformCount-1.
 * - assignPlatform(trainId, arrivalTime, waitTime) schedules a single contiguous interval
 *   of length = waitTime (inclusive endpoints) on exactly one platform.
 * - If no platform is immediately free at arrivalTime, we choose the platform that becomes
 *   available the earliest (minimum delay). Ties broken by lower platform index.
 * - Interval is inclusive: if a train ends at time t, the next can start at t+1.
 * - getTrainAtPlatform(p, t) returns the occupying trainId at timestamp t or "" if none.
 * - getPlatformOfTrain(trainId, t) returns the platform index (0-based, per examples) if the
 *   train occupies that timestamp, else -1.
 */
public class TrainPlatformManager {

    private static final class Assignment {
        final String trainId;
        final int platform;
        final int start;   // inclusive
        final int end;     // inclusive
        final int delay;

        Assignment(String trainId, int platform, int start, int end, int delay) {
            this.trainId = trainId;
            this.platform = platform;
            this.start = start;
            this.end = end;
            this.delay = delay;
        }
    }

    private final int platformCount;
    // For each platform, a sorted map: startTime -> Assignment (non-overlapping, increasing by start)
    private final List<TreeMap<Integer, Assignment>> schedules;
    // Lookup: trainId -> its (single) scheduled assignment
    private final Map<String, Assignment> trainIndex;

    public TrainPlatformManager(int platformCount) {
        if (platformCount <= 0) throw new IllegalArgumentException("platformCount must be >= 1");
        this.platformCount = platformCount;
        this.schedules = new ArrayList<>(platformCount);
        for (int i = 0; i < platformCount; i++) {
            schedules.add(new TreeMap<>());
        }
        this.trainIndex = new HashMap<>();
    }

    /**
     * Schedules the train and returns "platformIndex,delay".
     * - Duration = waitTime
     * - departureTime = arrivalTime + waitTime - 1 + delay (inclusive)
     */
    public String assignPlatform(String trainId, int arrivalTime, int waitTime) {
        if (trainId == null || trainId.isEmpty()) throw new IllegalArgumentException("trainId blank");
        if (waitTime <= 0) throw new IllegalArgumentException("waitTime must be >= 1");

        // If already assigned, return the existing platform,delay
        Assignment existing = trainIndex.get(trainId);
        if (existing != null) {
            return existing.platform + "," + existing.delay;
        }

        int bestPlatform = -1;
        int bestStart = Integer.MAX_VALUE;
        int bestDelay = Integer.MAX_VALUE;

        // Evaluate each platform: earliest feasible start >= arrivalTime for a block of length waitTime
        for (int p = 0; p < platformCount; p++) {
            TreeMap<Integer, Assignment> timeline = schedules.get(p);

            int candidateStart = arrivalTime;

            // Scan intervals in chronological order to find first gap of length >= waitTime
            for (Assignment iv : timeline.values()) {
                if (iv.end < candidateStart) {
                    // This interval ends before we plan to start; keep searching forward
                    continue;
                }

                if (iv.start <= candidateStart && candidateStart <= iv.end) {
                    // We are inside an existing interval; push start to immediately after it
                    candidateStart = iv.end + 1;
                } else if (candidateStart < iv.start) {
                    // Gap = [candidateStart .. iv.start-1]
                    int gapLen = iv.start - candidateStart; // inclusive length is gapLen
                    if (gapLen >= waitTime) {
                        // Fits before this interval
                        break; // candidateStart is valid
                    } else {
                        // Not enough room before iv; move to after iv and continue
                        candidateStart = iv.end + 1;
                    }
                }
            }

            int delay = Math.max(0, candidateStart - arrivalTime);

            if (delay < bestDelay || (delay == bestDelay && p < bestPlatform)) {
                bestDelay = delay;
                bestPlatform = p;
                bestStart = candidateStart;
            }
        }

        int start = bestStart;
        int end = start + waitTime - 1;

        Assignment placed = new Assignment(trainId, bestPlatform, start, end, bestDelay);
        schedules.get(bestPlatform).put(start, placed);
        trainIndex.put(trainId, placed);

        return bestPlatform + "," + bestDelay;
    }

    /**
     * Returns trainId occupying the platform at the given timestamp, or "" if none.
     */
    public String getTrainAtPlatform(int platformNumber, int timestamp) {
        if (platformNumber < 0 || platformNumber >= platformCount) return "";
        TreeMap<Integer, Assignment> timeline = schedules.get(platformNumber);
        Map.Entry<Integer, Assignment> e = timeline.floorEntry(timestamp);
        if (e == null) return "";
        Assignment iv = e.getValue();
        return (timestamp >= iv.start && timestamp <= iv.end) ? iv.trainId : "";
    }

    /**
     * Returns 0-based platform index if the given train occupies the timestamp, or -1 otherwise.
     * (Follows examples where platform indices are 0-based.)
     */
    public int getPlatformOfTrain(String trainId, int timestamp) {
        Assignment iv = trainIndex.get(trainId);
        if (iv == null) return -1;
        return (timestamp >= iv.start && timestamp <= iv.end) ? iv.platform : -1;
    }
}
