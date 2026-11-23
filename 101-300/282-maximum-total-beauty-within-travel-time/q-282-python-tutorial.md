# Maximum Total Beauty Within Travel Time in Python

#### Problem Statement
[https://codezym.com/question/282-maximum-total-beauty-within-travel-time](https://codezym.com/question/282-maximum-total-beauty-within-travel-time)


The key to this problem is hidden in the constraints. A route can only use a few roads, and every location has only a few roads, so the number of possible routes is small. This means we can simply try every route.

We do this with DFS and backtracking. We start at location `0`, take a road, keep walking, and when we can't go further, we step back and try the next road.

Every time the walk is back at location `0`, it is a complete route, so we update the answer.

We first build this simple version. Then we make it faster by skipping roads that would take us too far to get back to location `0` in time.

For that, we find the shortest time from every location back to location `0` once, using Dijkstra's algorithm.


## Why Trying Every Route Works

- Every road takes at least `10` time, and `maxTime` is at most `100`. So a route uses at most `10` roads.
- Every location has at most `4` roads. So at each step we have at most `4` choices.

So the DFS goes at most `10` levels deep, with at most `4` choices at each level. In the worst case that is about `4^10`, a bit over a million walks. A computer can try all of them quickly.


## Solution 1: Try Every Route With DFS

### Storing the Roads

We store the roads in an adjacency list. `graph[u]` holds every road that touches location `u`, as `(otherLocation, travelTime)`.

Roads are two-way, so each road is added to both of its locations. Now the DFS can get all roads of a location directly, without scanning the whole road list.

### Counting Visits

A route can pass the same location many times. In Example 2, the route `0 → 1 → 2 → 1 → 3 → 1 → 0` passes location `1` three times, but its beauty is counted only once.

So we keep `visitCount[u]`, the number of times location `u` appears in the current walk.

- When we step into a location, we add its beauty only if its count is `0`, and then increase the count.
- When we step back, we decrease the count. This puts everything back the way it was before the move.

### The DFS

We start with `visitCount[0] = 1` and call `dfs(0, 0, beautyValues[0])`.

```python
def dfs(self, location: int, timeUsed: int, beauty: int) -> None:
    # back at location 0, so this walk is a complete valid route
    if location == 0:
        self.maxBeauty = max(self.maxBeauty, beauty)

    for nextLocation, roadTime in self.graph[location]:
        newTime = timeUsed + roadTime

        # skip this road if it takes us over the time limit
        if newTime > self.maxTime:
            continue

        # beauty is added only on the first visit
        gain = self.beautyValues[nextLocation] if self.visitCount[nextLocation] == 0 else 0

        self.visitCount[nextLocation] += 1
        self.dfs(nextLocation, newTime, beauty + gain)
        self.visitCount[nextLocation] -= 1  # step back (backtrack)
```

The very first call is already at location `0`, so staying at location `0` without moving is counted as a route too.

### The Problem With This Approach

This DFS only stops when time runs out. It never asks whether it can still get back to location `0`.

Say `maxTime` is `100`, we have used `70`, and the fastest way back to location `0` takes `40`. No walk from here can finish in time, but Solution 1 still tries all of them.

In a large graph, many walks keep moving away from location `0`, so a lot of work is wasted on walks that can never come back.


## Solution 2: Stop Early When We Can't Get Back in Time

### Shortest Time Back to Location 0

Before the DFS, we compute `shortestTimeToZero[u]`: the least time needed to travel from location `u` back to location `0`.

We find it with Dijkstra's algorithm, starting from location `0`. Roads are two-way, so the shortest time from `0` to `u` is also the shortest time from `u` back to `0`.

Dijkstra uses a priority queue (Python's `heapq`), which always gives us the location with the smallest known time first. The first time a location comes out of the queue, its time is final, and we use it to improve the times of its neighbors.

### The New Check

Before taking a road, we now check that the time left after the move is enough to get back:

```python
# skip this road if the time left is not enough to get back to location 0
if self.shortestTimeToZero[nextLocation] > self.maxTime - newTime:
    continue
```

This one check replaces the old one. The time to get back is never negative, so a move that goes over `maxTime` is also skipped.

We only skip walks that can never end at location `0` in time, so the answer stays the same.

### Example

Take Example 1: beauty values `[7, 15, 9, 20]`, roads `0-1` (time `10`), `1-2` (time `10`), `0-3` (time `12`), and `maxTime = 32`.

The shortest times back to location `0` are `[0, 10, 20, 12]`.

At location `1` we have used `10` time. Moving to location `2` brings it to `20`, leaving only `12`. But getting back from location `2` needs `20`, so we skip this road.

Solution 1 would walk `0 → 1 → 2 → 1` before finding out it is stuck.

The route `0 → 3 → 0` takes `24` time and collects `7 + 20 = 27`, which is the answer.

### Complete Python Code

```python
import heapq
from typing import List


class MaximumTotalBeautyWithinTravelTime:

    def __init__(self):
        # graph[u] has every road touching location u, stored as (otherLocation, travelTime)
        self.graph = []
        # shortestTimeToZero[u] = least time needed to travel from u back to location 0
        self.shortestTimeToZero = []
        # visitCount[u] = how many times location u appears in the current walk
        self.visitCount = []
        self.beautyValues = []
        self.maxTime = 0
        self.maxBeauty = 0

    def findMaximumBeauty(self, beautyValues: List[int], travelTimes: List[int],
                          connections: List[str], maxTime: int) -> int:
        self.beautyValues = beautyValues
        self.maxTime = maxTime
        n = len(beautyValues)

        self.buildGraph(n, travelTimes, connections)
        self.shortestTimeToZero = self.findShortestTimeToZero(n)

        # the walk starts at location 0, so it is already visited once
        self.visitCount = [0] * n
        self.visitCount[0] = 1
        self.maxBeauty = 0

        self.dfs(0, 0, beautyValues[0])
        return self.maxBeauty

    def buildGraph(self, n: int, travelTimes: List[int], connections: List[str]) -> None:
        self.graph = [[] for _ in range(n)]

        for i, connection in enumerate(connections):
            u, v = map(int, connection.split(","))
            time = travelTimes[i]

            # roads are two-way, so add the road to both locations
            self.graph[u].append((v, time))
            self.graph[v].append((u, time))

    # Dijkstra's algorithm from location 0.
    # Roads are two-way, so time from 0 to u is the same as time from u back to 0.
    def findShortestTimeToZero(self, n: int) -> List[float]:
        shortest = [float("inf")] * n
        shortest[0] = 0

        # each entry is (time, location), the smallest time comes out first
        queue = [(0, 0)]

        while queue:
            time, location = heapq.heappop(queue)

            # old entry, a faster way to this location was already found
            if time > shortest[location]:
                continue

            for nextLocation, roadTime in self.graph[location]:
                newTime = time + roadTime

                if newTime < shortest[nextLocation]:
                    shortest[nextLocation] = newTime
                    heapq.heappush(queue, (newTime, nextLocation))

        return shortest

    # Tries every walk that can still get back to location 0 in time.
    # timeUsed = time spent so far, beauty = beauty collected so far
    def dfs(self, location: int, timeUsed: int, beauty: int) -> None:
        # back at location 0, so this walk is a complete valid route
        if location == 0:
            self.maxBeauty = max(self.maxBeauty, beauty)

        for nextLocation, roadTime in self.graph[location]:
            newTime = timeUsed + roadTime

            # skip this road if the time left is not enough to get back to location 0
            if self.shortestTimeToZero[nextLocation] > self.maxTime - newTime:
                continue

            # beauty is added only on the first visit
            gain = self.beautyValues[nextLocation] if self.visitCount[nextLocation] == 0 else 0

            self.visitCount[nextLocation] += 1
            self.dfs(nextLocation, newTime, beauty + gain)
            self.visitCount[nextLocation] -= 1  # step back (backtrack)
```

### Complexity

Let `n` be the number of locations and `m` the number of roads.

- **Time:** `O((n + m) log n)` to build the graph and run Dijkstra, plus about `4^10` DFS steps in the worst case. The new check usually makes the DFS much smaller.
- **Space:** `O(n + m)` for the graph and the arrays. The recursion goes at most `10` levels deep.