# Most Used Airport Rest Pod in Python

#### Problem Statement
[https://codezym.com/question/418-most-used-airport-rest-pod](https://codezym.com/question/418-most-used-airport-rest-pod)


## Core Idea

This is a simulation problem. We sort the reservations by start time, give each one a pod by following the rules, and count how many reservations every pod gets.


For every reservation, we only need answers to two questions:

1. Which free pod has the smallest number?
2. If no pod is free, which busy pod becomes free first?


A simple solution answers both questions by scanning all the pods every time. A faster solution keeps free pods and busy pods in two min-heaps, so both answers are always sitting at the top.


## Approach 1: Simple Simulation (Scan All Pods)

First, sort the reservations by start time. The input can come in any order, but the rules handle reservations in start order.


Keep a list `free_time`, where `free_time[pod]` is the time when that pod becomes free. All values start at `0`, so every pod is free in the beginning.


For each reservation:

- Scan pods from `0` upward and pick the first pod with `free_time[pod] <= start`. That is the free pod with the smallest number.
- If no pod is free, pick the pod with the smallest `free_time`. The reservation is delayed to that time but keeps its original duration.

```python
free_time = [0] * podCount  # when each pod becomes free
use_count = [0] * podCount  # reservations given to each pod

for start, end in sorted_reservations:
    duration = end - start

    # smallest numbered pod that is free at the original start time
    chosen_pod = -1
    for pod in range(podCount):
        if free_time[pod] <= start:
            chosen_pod = pod
            break

    actual_start = start
    if chosen_pod == -1:
        # every pod is busy: wait for the pod that becomes free first
        chosen_pod = 0
        for pod in range(1, podCount):
            if free_time[pod] < free_time[chosen_pod]:
                chosen_pod = pod
        actual_start = free_time[chosen_pod]

    # the reservation keeps its original duration
    free_time[chosen_pod] = actual_start + duration
    use_count[chosen_pod] += 1
```


Three small details make this match the rules:

- `free_time[pod] <= start` uses `<=` because a pod becomes free at the exact time its reservation ends.
- `free_time[pod] < free_time[chosen_pod]` uses strict `<`, so if two pods become free at the same time, the smaller pod number wins.
- Reservations are handled in start order, so a waiting traveler with an earlier start always gets a pod before a later one.


### Problem with this approach

Each reservation may scan all the pods twice. So the scanning alone costs `O(n * podCount)`.


Most of that scanning is wasted. We never need the full list of pods. We only need the smallest free pod and the busy pod that becomes free first. A min-heap keeps exactly this kind of "smallest item" at its top.


## Approach 2: Two Min-Heaps (Optimal)

We split the pods into two groups and keep each group in its own min-heap. Python's `heapq` module turns a plain list into a min-heap.


**`free_pods`**: a min-heap of pod numbers. Its top is always the free pod with the smallest number. This is exactly what the first rule asks for.


**`busy_pods`**: a min-heap of `(free_time, pod)` tuples. Its top is the pod that becomes free first. This is exactly what the delay rule asks for.


Python compares tuples item by item. So when two pods become free at the same time, the smaller pod number comes first automatically. No custom comparison is needed.


For each reservation, in start order:

1. **Release finished pods.** While the top of `busy_pods` has `free_time <= start`, move that pod into `free_pods`.
2. **If a pod is free,** take the top of `free_pods`. The reservation starts on time.
3. **If no pod is free,** take the top of `busy_pods`. The reservation is delayed until that pod's `free_time`.
4. **Book the pod.** Push the pod into `busy_pods` with free time `actual_start + duration`, and add 1 to its count.


At the end, scan `use_count` from pod `0` and keep the highest count. Strict `>` keeps the smaller pod number on ties.


### Dry Run (Example 1)

`podCount = 2`, reservations sorted by start: `[2,9]`, `[3,6]`, `[4,8]`, `[7,10]`, `[8,11]`

| Reservation | Free pods at start | Pod given | Actual time | busy_pods after (free_time, pod) |
|---|---|---|---|---|
| [2,9] | 0, 1 | 0 | [2, 9) | (9, 0) |
| [3,6] | 1 | 1 | [3, 6) | (6, 1), (9, 0) |
| [4,8] | none | 1 | [6, 10) | (9, 0), (10, 1) |
| [7,10] | none | 0 | [9, 12) | (10, 1), (12, 0) |
| [8,11] | none | 1 | [10, 13) | (12, 0), (13, 1) |


Pod `0` is used 2 times and pod `1` is used 3 times, so the answer is `1`.


### Python Code

```python
import heapq
from typing import List


class AirportRestPodPlanner:

    def __init__(self):
        pass

    def findMostUsedPod(self, podCount: int, reservations: List[str]) -> int:
        sorted_reservations = self.parseReservations(reservations)

        # free pods: the smallest pod number is always on top
        free_pods = list(range(podCount))
        heapq.heapify(free_pods)

        # busy pods: (free_time, pod) tuples
        # earliest free time on top, smaller pod number wins a tie
        busy_pods = []

        # use_count[pod] = number of reservations given to that pod
        use_count = [0] * podCount

        for start, end in sorted_reservations:
            duration = end - start

            # 1. release every pod that is free at this start time
            while busy_pods and busy_pods[0][0] <= start:
                _, freed_pod = heapq.heappop(busy_pods)
                heapq.heappush(free_pods, freed_pod)

            if free_pods:
                # 2. a pod is free right now: take the smallest number
                pod = heapq.heappop(free_pods)
                actual_start = start
            else:
                # 3. every pod is busy: wait for the pod that frees up first
                actual_start, pod = heapq.heappop(busy_pods)

            # 4. the pod stays busy until this reservation ends
            heapq.heappush(busy_pods, (actual_start + duration, pod))
            use_count[pod] += 1

        # highest count wins, strict ">" keeps the smaller pod number on ties
        best_pod = 0
        for pod in range(1, podCount):
            if use_count[pod] > use_count[best_pod]:
                best_pod = pod
        return best_pod

    def parseReservations(self, reservations: List[str]) -> List[List[int]]:
        """Converts "start,end" strings into [start, end] pairs sorted by start time."""
        parsed = []
        for reservation in reservations:
            start_text, end_text = reservation.split(",")
            parsed.append([int(start_text), int(end_text)])
        parsed.sort(key=lambda pair: pair[0])
        return parsed
```


### Complexity

- **Time:** `O(n log n)`. Sorting takes `O(n log n)`. Each reservation adds one entry to `busy_pods`, and each entry is removed at most once, so all heap work is `O(n log podCount)`.
- **Space:** `O(n + podCount)` for the sorted reservations, the two heaps and the counts.