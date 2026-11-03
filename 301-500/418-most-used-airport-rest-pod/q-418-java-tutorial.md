# Most Used Airport Rest Pod in Java

#### Problem Statement
[https://codezym.com/question/418-most-used-airport-rest-pod](https://codezym.com/question/418-most-used-airport-rest-pod)


## Core Idea

This is a simulation problem. We sort the reservations by start time, give each one a pod by following the rules, and count how many reservations every pod gets.


For every reservation, we only need answers to two questions:

1. Which free pod has the smallest number?
2. If no pod is free, which busy pod becomes free first?


A simple solution answers both questions by scanning all the pods every time. A faster solution keeps free pods and busy pods in two min-heaps (priority queues), so both answers are always sitting at the top.


## Approach 1: Simple Simulation (Scan All Pods)

First, sort the reservations by start time. The input can come in any order, but the rules handle reservations in start order.


Keep an array `freeTime`, where `freeTime[pod]` is the time when that pod becomes free. All values start at `0`, so every pod is free in the beginning.


For each reservation:

- Scan pods from `0` upward and pick the first pod with `freeTime[pod] <= start`. That is the free pod with the smallest number.
- If no pod is free, pick the pod with the smallest `freeTime`. The reservation is delayed to that time but keeps its original duration.

```java
long[] freeTime = new long[podCount]; // when each pod becomes free
int[] useCount = new int[podCount];   // reservations given to each pod

for (int[] reservation : sortedReservations) {
    int start = reservation[0];
    int duration = reservation[1] - reservation[0];

    // smallest numbered pod that is free at the original start time
    int chosenPod = -1;
    for (int pod = 0; pod < podCount; pod++) {
        if (freeTime[pod] <= start) {
            chosenPod = pod;
            break;
        }
    }

    long actualStart = start;
    if (chosenPod == -1) {
        // every pod is busy: wait for the pod that becomes free first
        chosenPod = 0;
        for (int pod = 1; pod < podCount; pod++) {
            if (freeTime[pod] < freeTime[chosenPod]) {
                chosenPod = pod;
            }
        }
        actualStart = freeTime[chosenPod];
    }

    // the reservation keeps its original duration
    freeTime[chosenPod] = actualStart + duration;
    useCount[chosenPod]++;
}
```


Three small details make this match the rules:

- `freeTime[pod] <= start` uses `<=` because a pod becomes free at the exact time its reservation ends.
- `freeTime[pod] < freeTime[chosenPod]` uses strict `<`, so if two pods become free at the same time, the smaller pod number wins.
- Reservations are handled in start order, so a waiting traveler with an earlier start always gets a pod before a later one.


### Problem with this approach

Each reservation may scan all the pods twice. So the scanning alone costs `O(n * podCount)`.


Most of that scanning is wasted. We never need the full list of pods. We only need the smallest free pod and the busy pod that becomes free first. A min-heap keeps exactly this kind of "smallest item" at its top.


## Approach 2: Two Min-Heaps (Optimal)

We split the pods into two groups and keep each group in its own min-heap.


**`freePods`**: a min-heap of pod numbers. Its top is always the free pod with the smallest number. This is exactly what the first rule asks for.


**`busyPods`**: a min-heap of `{freeTime, podNumber}` pairs. Its top is the pod that becomes free first. If two pods become free at the same time, the smaller pod number comes first. This is exactly what the delay rule asks for.


For each reservation, in start order:

1. **Release finished pods.** While the top of `busyPods` has `freeTime <= start`, move that pod into `freePods`.
2. **If a pod is free,** take the top of `freePods`. The reservation starts on time.
3. **If no pod is free,** take the top of `busyPods`. The reservation is delayed until that pod's `freeTime`.
4. **Book the pod.** Push the pod into `busyPods` with free time `actualStart + duration`, and add 1 to its count.


At the end, scan `useCount` from pod `0` and keep the highest count. Strict `>` keeps the smaller pod number on ties.


**Why `long` for time?** Delays keep adding up. With one pod and 100,000 long reservations, end times can reach tens of billions, which does not fit in an `int`.


### Dry Run (Example 1)

`podCount = 2`, reservations sorted by start: `[2,9]`, `[3,6]`, `[4,8]`, `[7,10]`, `[8,11]`

| Reservation | Free pods at start | Pod given | Actual time | busyPods after (freeTime, pod) |
|---|---|---|---|---|
| [2,9] | 0, 1 | 0 | [2, 9) | (9, 0) |
| [3,6] | 1 | 1 | [3, 6) | (6, 1), (9, 0) |
| [4,8] | none | 1 | [6, 10) | (9, 0), (10, 1) |
| [7,10] | none | 0 | [9, 12) | (10, 1), (12, 0) |
| [8,11] | none | 1 | [10, 13) | (12, 0), (13, 1) |


Pod `0` is used 2 times and pod `1` is used 3 times, so the answer is `1`.


### Java Code

```java
import java.util.*;

public class AirportRestPodPlanner {

    public AirportRestPodPlanner() {
    }

    public int findMostUsedPod(int podCount, List<String> reservations) {
        List<int[]> sortedReservations = parseReservations(reservations);

        // free pods: the smallest pod number is always on top
        PriorityQueue<Integer> freePods = new PriorityQueue<>();
        for (int pod = 0; pod < podCount; pod++) {
            freePods.add(pod);
        }

        // busy pods: each entry is {freeTime, podNumber}
        // earliest free time on top, smaller pod number wins a tie
        PriorityQueue<long[]> busyPods = new PriorityQueue<>((a, b) -> {
            if (a[0] != b[0]) {
                return Long.compare(a[0], b[0]);
            }
            return Long.compare(a[1], b[1]);
        });

        // useCount[pod] = number of reservations given to that pod
        int[] useCount = new int[podCount];

        for (int[] reservation : sortedReservations) {
            int start = reservation[0];
            int duration = reservation[1] - reservation[0];

            // 1. release every pod that is free at this start time
            while (!busyPods.isEmpty() && busyPods.peek()[0] <= start) {
                long[] freedPod = busyPods.poll();
                freePods.add((int) freedPod[1]);
            }

            int pod;
            long actualStart;
            if (!freePods.isEmpty()) {
                // 2. a pod is free right now: take the smallest number
                pod = freePods.poll();
                actualStart = start;
            } else {
                // 3. every pod is busy: wait for the pod that frees up first
                long[] earliestPod = busyPods.poll();
                pod = (int) earliestPod[1];
                actualStart = earliestPod[0];
            }

            // 4. the pod stays busy until this reservation ends
            busyPods.add(new long[]{actualStart + duration, pod});
            useCount[pod]++;
        }

        // highest count wins, strict ">" keeps the smaller pod number on ties
        int bestPod = 0;
        for (int pod = 1; pod < podCount; pod++) {
            if (useCount[pod] > useCount[bestPod]) {
                bestPod = pod;
            }
        }
        return bestPod;
    }

    /**
     * Converts "start,end" strings into {start, end} pairs sorted by start time.
     */
    List<int[]> parseReservations(List<String> reservations) {
        List<int[]> parsed = new ArrayList<>();
        for (String reservation : reservations) {
            String[] parts = reservation.split(",");
            int start = Integer.parseInt(parts[0].trim());
            int end = Integer.parseInt(parts[1].trim());
            parsed.add(new int[]{start, end});
        }
        parsed.sort((a, b) -> Integer.compare(a[0], b[0]));
        return parsed;
    }
}
```


### Complexity

- **Time:** `O(n log n)`. Sorting takes `O(n log n)`. Each reservation adds one entry to `busyPods`, and each entry is removed at most once, so all heap work is `O(n log podCount)`.
- **Space:** `O(n + podCount)` for the sorted reservations, the two heaps and the counts.