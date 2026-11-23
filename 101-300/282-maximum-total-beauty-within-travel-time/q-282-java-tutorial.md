# Maximum Total Beauty Within Travel Time in Java

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

We store the roads in an adjacency list. `graph.get(u)` holds every road that touches location `u`, as `{otherLocation, travelTime}`.

Roads are two-way, so each road is added to both of its locations. Now the DFS can get all roads of a location directly, without scanning the whole road list.

### Counting Visits

A route can pass the same location many times. In Example 2, the route `0 → 1 → 2 → 1 → 3 → 1 → 0` passes location `1` three times, but its beauty is counted only once.

So we keep `visitCount[u]`, the number of times location `u` appears in the current walk.

- When we step into a location, we add its beauty only if its count is `0`, and then increase the count.
- When we step back, we decrease the count. This puts everything back the way it was before the move.

### The DFS

We start with `visitCount[0] = 1` and call `dfs(0, 0, beautyValues.get(0))`.

```java
void dfs(int location, int timeUsed, int beauty) {
    // back at location 0, so this walk is a complete valid route
    if (location == 0) {
        maxBeauty = Math.max(maxBeauty, beauty);
    }

    for (int[] road : graph.get(location)) {
        int nextLocation = road[0];
        int newTime = timeUsed + road[1];

        // skip this road if it takes us over the time limit
        if (newTime > maxTime) continue;

        // beauty is added only on the first visit
        int gain = (visitCount[nextLocation] == 0) ? beautyValues.get(nextLocation) : 0;

        visitCount[nextLocation]++;
        dfs(nextLocation, newTime, beauty + gain);
        visitCount[nextLocation]--; // step back (backtrack)
    }
}
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

Dijkstra uses a priority queue, which always gives us the location with the smallest known time first. The first time a location comes out of the queue, its time is final, and we use it to improve the times of its neighbors.

### The New Check

Before taking a road, we now check that the time left after the move is enough to get back:

```java
// skip this road if the time left is not enough to get back to location 0
if (shortestTimeToZero[nextLocation] > maxTime - newTime) continue;
```

This one check replaces the old one. The time to get back is never negative, so a move that goes over `maxTime` is also skipped.

We only skip walks that can never end at location `0` in time, so the answer stays the same.

### Example

Take Example 1: beauty values `[7, 15, 9, 20]`, roads `0-1` (time `10`), `1-2` (time `10`), `0-3` (time `12`), and `maxTime = 32`.

The shortest times back to location `0` are `[0, 10, 20, 12]`.

At location `1` we have used `10` time. Moving to location `2` brings it to `20`, leaving only `12`. But getting back from location `2` needs `20`, so we skip this road.

Solution 1 would walk `0 → 1 → 2 → 1` before finding out it is stuck.

The route `0 → 3 → 0` takes `24` time and collects `7 + 20 = 27`, which is the answer.

### Complete Java Code

```java
import java.util.*;

public class MaximumTotalBeautyWithinTravelTime {

    // graph.get(u) has every road touching location u, stored as {otherLocation, travelTime}
    List<List<int[]>> graph;

    // shortestTimeToZero[u] = least time needed to travel from u back to location 0
    int[] shortestTimeToZero;

    // visitCount[u] = how many times location u appears in the current walk
    int[] visitCount;

    List<Integer> beautyValues;
    int maxTime;
    int maxBeauty;

    public MaximumTotalBeautyWithinTravelTime() {
    }

    public int findMaximumBeauty(List<Integer> beautyValues, List<Integer> travelTimes,
                                 List<String> connections, int maxTime) {
        this.beautyValues = beautyValues;
        this.maxTime = maxTime;
        int n = beautyValues.size();

        buildGraph(n, travelTimes, connections);
        shortestTimeToZero = findShortestTimeToZero(n);

        // the walk starts at location 0, so it is already visited once
        visitCount = new int[n];
        visitCount[0] = 1;
        maxBeauty = 0;

        dfs(0, 0, beautyValues.get(0));
        return maxBeauty;
    }

    void buildGraph(int n, List<Integer> travelTimes, List<String> connections) {
        graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }

        for (int i = 0; i < connections.size(); i++) {
            String[] parts = connections.get(i).split(",");
            int u = Integer.parseInt(parts[0].trim());
            int v = Integer.parseInt(parts[1].trim());
            int time = travelTimes.get(i);

            // roads are two-way, so add the road to both locations
            graph.get(u).add(new int[]{v, time});
            graph.get(v).add(new int[]{u, time});
        }
    }

    // Dijkstra's algorithm from location 0.
    // Roads are two-way, so time from 0 to u is the same as time from u back to 0.
    int[] findShortestTimeToZero(int n) {
        int[] shortest = new int[n];
        Arrays.fill(shortest, Integer.MAX_VALUE);
        shortest[0] = 0;

        // each entry is {location, time}, the smallest time comes out first
        PriorityQueue<int[]> queue = new PriorityQueue<>((a, b) -> a[1] - b[1]);
        queue.add(new int[]{0, 0});

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int location = current[0];
            int time = current[1];

            // old entry, a faster way to this location was already found
            if (time > shortest[location]) continue;

            for (int[] road : graph.get(location)) {
                int nextLocation = road[0];
                int newTime = time + road[1];

                if (newTime < shortest[nextLocation]) {
                    shortest[nextLocation] = newTime;
                    queue.add(new int[]{nextLocation, newTime});
                }
            }
        }
        return shortest;
    }

    // Tries every walk that can still get back to location 0 in time.
    // timeUsed = time spent so far, beauty = beauty collected so far
    void dfs(int location, int timeUsed, int beauty) {
        // back at location 0, so this walk is a complete valid route
        if (location == 0) {
            maxBeauty = Math.max(maxBeauty, beauty);
        }

        for (int[] road : graph.get(location)) {
            int nextLocation = road[0];
            int newTime = timeUsed + road[1];

            // skip this road if the time left is not enough to get back to location 0
            if (shortestTimeToZero[nextLocation] > maxTime - newTime) continue;

            // beauty is added only on the first visit
            int gain = (visitCount[nextLocation] == 0) ? beautyValues.get(nextLocation) : 0;

            visitCount[nextLocation]++;
            dfs(nextLocation, newTime, beauty + gain);
            visitCount[nextLocation]--; // step back (backtrack)
        }
    }
}
```

### Complexity

Let `n` be the number of locations and `m` the number of roads.

- **Time:** `O((n + m) log n)` to build the graph and run Dijkstra, plus about `4^10` DFS steps in the worst case. The new check usually makes the DFS much smaller.
- **Space:** `O(n + m)` for the graph and the arrays. The recursion goes at most `10` levels deep.