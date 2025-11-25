from dataclasses import dataclass
from bisect import bisect_right
from typing import List, Dict, Tuple, Optional


# /**
#  * TrainPlatformManager
#  *
#  * Rules implemented per problem statement:
#  * - Platforms are indexed 0..platformCount-1.
#  * - assignPlatform(trainId, arrivalTime, waitTime) schedules a single contiguous interval
#  *   of length = waitTime (inclusive endpoints) on exactly one platform.
#  * - If no platform is immediately free at arrivalTime, we choose the platform that becomes
#  *   available the earliest (minimum delay). Ties broken by lower platform index.
#  * - Interval is inclusive: if a train ends at time t, the next can start at t+1.
#  * - getTrainAtPlatform(p, t) returns the occupying trainId at timestamp t or "" if none.
#  * - getPlatformOfTrain(trainId, t) returns the platform index (0-based, per examples) if the
#  *   train occupies that timestamp, else -1.
#  */


@dataclass(frozen=True)
class Assignment:
    # final String trainId;
    trainId: str
    # final int platform;
    platform: int
    # final int start;   // inclusive
    start: int
    # final int end;     // inclusive
    end: int
    # final int delay;
    delay: int


class TrainPlatformManager:
    """
    Python port of the provided Java implementation.

    Notes / invariants mirrored from the original:
    - Platforms are indexed 0..platformCount-1 (0-based, matching the examples and Java).
    - assignPlatform(trainId, arrivalTime, waitTime):
        * Duration = waitTime (inclusive endpoints), i.e., scheduled as [start .. start+waitTime-1].
        * If no platform is free right at arrivalTime, pick the platform with the smallest delay
          (earliest feasible start). Tie-break by lowest platform index.
        * departureTime = arrivalTime + waitTime - 1 + delay (inclusive).
    - getTrainAtPlatform(p, t) returns the occupying trainId at time t, or "" if none.
    - getPlatformOfTrain(trainId, t) returns the 0-based platform index if the train occupies t, else -1.
    """

    def __init__(self, platformCount: int):
        # if (platformCount <= 0) throw new IllegalArgumentException("platformCount must be >= 1");
        if platformCount <= 0:
            raise ValueError("platformCount must be >= 1")
        # this.platformCount = platformCount;
        self.platformCount: int = platformCount
        # this.schedules = new ArrayList<>(platformCount);
        # For each platform, keep parallel arrays: starts (sorted) and items (Assignment in start order)
        # schedules[p] = (starts_list, items_list)
        self.schedules: List[Tuple[List[int], List[Assignment]]] = [
            ([], []) for _ in range(platformCount)
        ]
        # this.trainIndex = new HashMap<>();
        # Lookup: trainId -> its (single) scheduled assignment
        self.trainIndex: Dict[str, Assignment] = {}

    # /**
    #  * Schedules the train and returns "platformIndex,delay".
    #  * - Duration = waitTime
    #  * - departureTime = arrivalTime + waitTime - 1 + delay (inclusive)
    #  */
    def assignPlatform(self, trainId: str, arrivalTime: int, waitTime: int) -> str:
        # if (trainId == null || trainId.isEmpty()) throw new IllegalArgumentException("trainId blank");
        if trainId is None or len(trainId) == 0:
            raise ValueError("trainId blank")
        # if (waitTime <= 0) throw new IllegalArgumentException("waitTime must be >= 1");
        if waitTime <= 0:
            raise ValueError("waitTime must be >= 1")

        # // If already assigned, return the existing platform,delay
        # Assignment existing = trainIndex.get(trainId);
        existing: Optional[Assignment] = self.trainIndex.get(trainId)
        # if (existing != null) { return existing.platform + "," + existing.delay; }
        if existing is not None:
            return f"{existing.platform},{existing.delay}"

        # int bestPlatform = -1;
        bestPlatform: int = -1
        # int bestStart = Integer.MAX_VALUE;
        bestStart: int = 10**18
        # int bestDelay = Integer.MAX_VALUE;
        bestDelay: int = 10**18

        # // Evaluate each platform: earliest feasible start >= arrivalTime for a block of length waitTime
        for p in range(self.platformCount):
            starts, items = self.schedules[p]

            # int candidateStart = arrivalTime;
            candidateStart: int = arrivalTime

            # // Scan intervals in chronological order to find first gap of length >= waitTime
            for iv in items:
                # if (iv.end < candidateStart) { continue; }
                if iv.end < candidateStart:
                    # // This interval ends before we plan to start; keep searching forward
                    continue

                # if (iv.start <= candidateStart && candidateStart <= iv.end) { candidateStart = iv.end + 1; }
                if iv.start <= candidateStart <= iv.end:
                    # // We are inside an existing interval; push start to immediately after it
                    candidateStart = iv.end + 1
                # else if (candidateStart < iv.start) { ... }
                elif candidateStart < iv.start:
                    # // Gap = [candidateStart .. iv.start-1]
                    gapLen = iv.start - candidateStart  # inclusive length is gapLen
                    # if (gapLen >= waitTime) { break; } else { candidateStart = iv.end + 1; }
                    if gapLen >= waitTime:
                        # // Fits before this interval
                        break  # candidateStart is valid
                    else:
                        # // Not enough room before iv; move to after iv and continue
                        candidateStart = iv.end + 1

            # int delay = Math.max(0, candidateStart - arrivalTime);
            delay: int = max(0, candidateStart - arrivalTime)

            # if (delay < bestDelay || (delay == bestDelay && p < bestPlatform)) { ... }
            if (delay < bestDelay) or (delay == bestDelay and (bestPlatform == -1 or p < bestPlatform)):
                bestDelay = delay
                bestPlatform = p
                bestStart = candidateStart

        # int start = bestStart;
        start: int = bestStart
        # int end = start + waitTime - 1;
        end: int = start + waitTime - 1

        # Assignment placed = new Assignment(trainId, bestPlatform, start, end, bestDelay);
        placed = Assignment(trainId=trainId, platform=bestPlatform, start=start, end=end, delay=bestDelay)
        # schedules.get(bestPlatform).put(start, placed);
        s_starts, s_items = self.schedules[bestPlatform]
        insert_at = bisect_right(s_starts, start)
        s_starts.insert(insert_at, start)
        s_items.insert(insert_at, placed)
        # trainIndex.put(trainId, placed);
        self.trainIndex[trainId] = placed

        # return bestPlatform + "," + bestDelay;
        return f"{bestPlatform},{bestDelay}"

    # /**
    #  * Returns trainId occupying the platform at the given timestamp, or "" if none.
    #  */
    def getTrainAtPlatform(self, platformNumber: int, timestamp: int) -> str:
        # if (platformNumber < 0 || platformNumber >= platformCount) return "";
        if platformNumber < 0 or platformNumber >= self.platformCount:
            return ""
        # TreeMap<Integer, Assignment> timeline = schedules.get(platformNumber);
        starts, items = self.schedules[platformNumber]
        # Map.Entry<Integer, Assignment> e = timeline.floorEntry(timestamp);
        # floor by start time: find rightmost start <= timestamp
        idx = bisect_right(starts, timestamp) - 1
        # if (e == null) return "";
        if idx < 0:
            return ""
        # Assignment iv = e.getValue();
        iv = items[idx]
        # return (timestamp >= iv.start && timestamp <= iv.end) ? iv.trainId : "";
        return iv.trainId if (iv.start <= timestamp <= iv.end) else ""

    # /**
    #  * Returns 0-based platform index if the given train occupies the timestamp, or -1 otherwise.
    #  * (Follows examples where platform indices are 0-based.)
    #  */
    def getPlatformOfTrain(self, trainId: str, timestamp: int) -> int:
        # Assignment iv = trainIndex.get(trainId);
        iv = self.trainIndex.get(trainId)
        # if (iv == null) return -1;
        if iv is None:
            return -1
        # return (timestamp >= iv.start && timestamp <= iv.end) ? iv.platform : -1;
        return iv.platform if (iv.start <= timestamp <= iv.end) else -1
