# Shortest Distance and Nearest Landmarks in Java

#### Problem Statement
[https://codezym.com/question/399-nearest-landmarks](https://codezym.com/question/399-nearest-landmarks)

Think of the input as a small map. Points are places, roads join two places, and landmarks are labels like `school` or `hospital` pinned to places. Both questions boil down to one thing: **how far is every point from a starting point, if we can travel only on roads?**

The standard tool for this is **Dijkstra's algorithm**. It spreads out from the start and hands us points in order of their road distance, nearest first. Getting points "nearest first" is the key idea of this whole problem.

We will first write a simple version that runs a full Dijkstra on every call. Then we will make it faster in three small steps: answer unreachable trips instantly, steer the search toward the destination (A\* search), and stop the landmark search as soon as the nearest landmarks are known.

---

## Key Observations

- The length of a road is the straight-line distance between its two points: `sqrt(dx * dx + dy * dy)`.
- Roads have different lengths, so plain BFS (which counts roads, not length) will not work. We need Dijkstra.
- A landmark's distance is simply the distance of the point it stands on.
- Landmarks are ordered by distance first, and by id when distances are equal.

Example 1: from `A` to `C` there are two routes. `A → B → C` is `5 + 5 = 10`, and `A → D → C` is `8 + 6 = 14`. So the answer is `10.0`.

---

## How Dijkstra Works (in plain words)

1. Every point gets a "best distance found so far". The start gets `0`, every other point gets infinity.
2. A **priority queue** holds `{distance, point}` pairs and always gives back the smallest distance first.
3. Take the nearest point out of the queue. Its distance is now final. Any other route to it would have to pass through a point still in the queue, which is already at least as far, and extra road can never make a trip shorter.
4. Look at every road leaving this point. If going through it gives a neighbor a shorter distance, update the neighbor and push it into the queue.

A point can be pushed more than once, once for every shorter route we discover. A `done` array lets us skip the older, longer copies.

The important result: **points come out of the queue in increasing order of distance.**

---

## Solution 1: Run Dijkstra on Every Call

This is the most direct approach. For each query, find the distance from the start to every point, then read off the answer.

### Data structures and why we need them

- `Map<String, Integer> pointIndex`: turns point ids like `"A"` into numbers `0, 1, 2 ...`. With numbers we can keep coordinates and distances in plain arrays, which are much faster than maps.
- `double[] x, y`: coordinates of each point, used to compute road lengths.
- `List<List<Integer>> neighbors`: the adjacency list. `neighbors.get(i)` lists every point joined to point `i` by a road. Each road is added in both directions.
- `Map<String, List<Landmark>> landmarksByType`: landmarks grouped by type, so a query looks only at landmarks of the requested type.
- `Landmark`: a tiny class that keeps a landmark id together with the index of its point.
- `PriorityQueue<double[]>`: the Dijkstra queue. Each entry is `{distance, point}` and the smallest distance comes out first.

### How each method works

**getShortestDistance**: run Dijkstra from the start and read the distance of the end point. If it is still infinity, the end was never reached, so return `-1.0`.

**getNearestLandmarks**: run Dijkstra from the given point. Keep the landmarks of the requested type whose point was reached. Sort them by distance, then by id, and return the first `limit` ids.

### Code

```java
import java.util.*;

/** A landmark together with the index of the point where it stands. */
class Landmark {
    String id;
    int point;

    Landmark(String id, int point) {
        this.id = id;
        this.point = point;
    }
}

public class LandmarkGraph {
    // point id -> index (0, 1, 2 ...), so that we can use fast arrays
    Map<String, Integer> pointIndex = new HashMap<>();
    // coordinates of every point, stored by index
    double[] x, y;
    // neighbors.get(i) = all points joined to point i by a road
    List<List<Integer>> neighbors = new ArrayList<>();
    // landmark type -> all landmarks of that type
    Map<String, List<Landmark>> landmarksByType = new HashMap<>();

    public LandmarkGraph(List<String> points, List<String> roads, List<String> landmarks) {
        int n = points.size();
        x = new double[n];
        y = new double[n];
        for (int i = 0; i < n; i++) {
            String[] parts = points.get(i).split(",");
            pointIndex.put(parts[0].trim(), i);
            x[i] = Double.parseDouble(parts[1].trim());
            y[i] = Double.parseDouble(parts[2].trim());
            neighbors.add(new ArrayList<>());
        }
        for (String road : roads) {
            String[] parts = road.split(",");
            int a = pointIndex.get(parts[0].trim());
            int b = pointIndex.get(parts[1].trim());
            // roads can be traveled in both directions
            neighbors.get(a).add(b);
            neighbors.get(b).add(a);
        }
        for (String landmark : landmarks) {
            String[] parts = landmark.split(",");
            int point = pointIndex.get(parts[2].trim());
            landmarksByType.computeIfAbsent(parts[1].trim(), k -> new ArrayList<>())
                    .add(new Landmark(parts[0].trim(), point));
        }
    }

    /** Straight-line distance between two points. It is also the length of a road between them. */
    double distanceBetween(int a, int b) {
        double dx = x[a] - x[b];
        double dy = y[a] - y[b];
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Dijkstra: shortest road distance from start to every point. Unreachable points stay at infinity. */
    double[] shortestDistancesFrom(int start) {
        int n = x.length;
        double[] dist = new double[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[start] = 0;
        boolean[] done = new boolean[n];
        // each entry is {distance, point}, the smallest distance comes out first
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        queue.add(new double[]{0, start});

        while (!queue.isEmpty()) {
            int point = (int) queue.poll()[1];
            if (done[point]) continue; // an old copy with a longer distance
            done[point] = true;
            for (int next : neighbors.get(point)) {
                double newDist = dist[point] + distanceBetween(point, next);
                if (newDist < dist[next]) {
                    dist[next] = newDist;
                    queue.add(new double[]{newDist, next});
                }
            }
        }
        return dist;
    }

    public double getShortestDistance(String startPointId, String endPointId) {
        Integer start = pointIndex.get(startPointId);
        Integer end = pointIndex.get(endPointId);
        if (start == null || end == null) return -1.0;

        double distance = shortestDistancesFrom(start)[end];
        return distance == Double.POSITIVE_INFINITY ? -1.0 : distance;
    }

    public List<String> getNearestLandmarks(String pointId, String landmarkType, int limit) {
        List<String> answer = new ArrayList<>();
        Integer start = pointIndex.get(pointId);
        if (start == null || !landmarksByType.containsKey(landmarkType)) return answer;

        double[] dist = shortestDistancesFrom(start);
        List<Landmark> reachable = new ArrayList<>();
        for (Landmark landmark : landmarksByType.get(landmarkType)) {
            if (dist[landmark.point] != Double.POSITIVE_INFINITY) reachable.add(landmark);
        }
        // nearer landmarks first, for the same distance the smaller id first
        reachable.sort((a, b) -> {
            if (dist[a.point] != dist[b.point]) return Double.compare(dist[a.point], dist[b.point]);
            return a.id.compareTo(b.id);
        });
        for (int i = 0; i < reachable.size() && i < limit; i++) {
            answer.add(reachable.get(i).id);
        }
        return answer;
    }
}
```

### Complexity

Let `P` = number of points, `R` = number of roads, `L` = number of landmarks.

- Constructor: `O(P + R + L)`.
- Each query: `O((P + R) log P)` for Dijkstra, plus `O(L log L)` to sort the landmarks of one type.

### What is slow here?

- Dijkstra always runs until the queue is empty, even when the destination is the very next point.
- For an unreachable destination, we explore everything reachable from the start just to learn that the answer is `-1.0`.
- We need at most `100` landmarks, yet we measure the distance to every point and sort every landmark of that type.

---

## Solution 2: Stop the Search as Early as Possible

We keep the same graph and improve the searches one step at a time.

### Improvement 1: Answer "unreachable" instantly

In the constructor, we split the points into **connected components**. A component is a group of points that can all reach each other. We run a simple flood fill from every point that has no component yet, and every point it reaches gets the same component id.

A route between two points exists only if they are in the same component. So `getShortestDistance` returns `-1.0` right away when the ids differ, without any search.

`int[] component` answers this in `O(1)`, and building it costs only `O(P + R)` once.

### Improvement 2: Head toward the destination (A\* search)

Dijkstra grows like a circle around the start. It explores every direction equally, even the ones moving away from the destination.

But we know exactly where the destination is. Every road is a straight line, so the straight-line distance from a point to the destination is the shortest trip that could possibly be left. Real roads can only be longer or equal.

So we order the queue by:

```
distance traveled so far + straight-line distance left
```

Points that move toward the destination get small values and come out first. Points heading the wrong way get pushed back. This is called **A\* search**, and the code is Dijkstra with one extra addition.

Is the answer still correct? Yes. The straight-line guess never overestimates, so when the destination comes out of the queue, no other route can beat it. Also, one side of a triangle is never longer than the other two sides together. Because of this, just like in Dijkstra, a point's distance is final the first time it leaves the queue, so the same `done` array still works.

A nice bonus: the guess uses the same `distanceBetween()` method as the road lengths.

### Improvement 3: Collect landmarks while walking outward

Dijkstra hands out points nearest first. So if we check each point for landmarks of the requested type at the moment it leaves the queue, we meet the landmarks in order of their distance. We do not need to finish the search.

To make "does this point have any schools?" a quick check, the landmark index becomes `type → (point → landmarks at that point)`. That is one map lookup per point.

**When can we stop?** When we already hold at least `limit` landmarks and the point leaving the queue is **strictly farther** than the `limit`-th landmark we found. Every point still in the queue is at least that far, so nothing new can enter the answer.

**Why strictly farther?** Because of ties. A landmark at the exact same distance could still be waiting in the queue, and it might have a smaller id. So we keep going while the distance is equal, then sort what we collected by distance and id, and keep the first `limit`.

Example 2: from `Center`, both `Left` and `Right` are `5.0` away and `limit = 1`. Say `Right` leaves the queue first, so we now hold `betaSchool`. Then `Left` leaves at `5.0`, which is not strictly farther, so we also collect `alphaSchool`. After sorting by id, `alphaSchool` comes first and is returned.

### Code

```java
import java.util.*;

/** A landmark together with the index of the point where it stands. */
class Landmark {
    String id;
    int point;

    Landmark(String id, int point) {
        this.id = id;
        this.point = point;
    }
}

public class LandmarkGraph {
    // point id -> index (0, 1, 2 ...), so that we can use fast arrays
    Map<String, Integer> pointIndex = new HashMap<>();
    // coordinates of every point, stored by index
    double[] x, y;
    // neighbors.get(i) = all points joined to point i by a road
    List<List<Integer>> neighbors = new ArrayList<>();
    // component[i] = id of the group of connected points that point i belongs to
    int[] component;
    // landmark type -> (point index -> landmarks of that type at that point)
    Map<String, Map<Integer, List<Landmark>>> landmarksByType = new HashMap<>();

    public LandmarkGraph(List<String> points, List<String> roads, List<String> landmarks) {
        int n = points.size();
        x = new double[n];
        y = new double[n];
        for (int i = 0; i < n; i++) {
            String[] parts = points.get(i).split(",");
            pointIndex.put(parts[0].trim(), i);
            x[i] = Double.parseDouble(parts[1].trim());
            y[i] = Double.parseDouble(parts[2].trim());
            neighbors.add(new ArrayList<>());
        }
        for (String road : roads) {
            String[] parts = road.split(",");
            int a = pointIndex.get(parts[0].trim());
            int b = pointIndex.get(parts[1].trim());
            // roads can be traveled in both directions
            neighbors.get(a).add(b);
            neighbors.get(b).add(a);
        }
        for (String landmark : landmarks) {
            String[] parts = landmark.split(",");
            int point = pointIndex.get(parts[2].trim());
            landmarksByType.computeIfAbsent(parts[1].trim(), k -> new HashMap<>())
                    .computeIfAbsent(point, k -> new ArrayList<>())
                    .add(new Landmark(parts[0].trim(), point));
        }
        findComponents();
    }

    /** Straight-line distance between two points. It is also the length of a road between them. */
    double distanceBetween(int a, int b) {
        double dx = x[a] - x[b];
        double dy = y[a] - y[b];
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Gives the same component id to all points that can reach each other. */
    void findComponents() {
        int n = x.length;
        component = new int[n];
        Arrays.fill(component, -1);
        int nextId = 0;
        for (int first = 0; first < n; first++) {
            if (component[first] != -1) continue;
            // flood fill: every point reachable from `first` gets the same id
            ArrayDeque<Integer> stack = new ArrayDeque<>();
            stack.push(first);
            component[first] = nextId;
            while (!stack.isEmpty()) {
                int point = stack.pop();
                for (int next : neighbors.get(point)) {
                    if (component[next] == -1) {
                        component[next] = nextId;
                        stack.push(next);
                    }
                }
            }
            nextId++;
        }
    }

    /** A* search: Dijkstra that also adds the straight-line distance still left to the destination. */
    public double getShortestDistance(String startPointId, String endPointId) {
        Integer start = pointIndex.get(startPointId);
        Integer end = pointIndex.get(endPointId);
        // points in different components can never reach each other
        if (start == null || end == null || component[start] != component[end]) return -1.0;

        int n = x.length;
        double[] dist = new double[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[start] = 0;
        boolean[] done = new boolean[n];
        // each entry is {distance traveled + straight-line distance left, point}
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        queue.add(new double[]{distanceBetween(start, end), start});

        while (!queue.isEmpty()) {
            int point = (int) queue.poll()[1];
            if (point == end) return dist[end]; // the first time it comes out, it is the shortest
            if (done[point]) continue;
            done[point] = true;
            for (int next : neighbors.get(point)) {
                double newDist = dist[point] + distanceBetween(point, next);
                if (newDist < dist[next]) {
                    dist[next] = newDist;
                    queue.add(new double[]{newDist + distanceBetween(next, end), next});
                }
            }
        }
        return -1.0;
    }

    /** Dijkstra that visits points nearest first and stops as soon as the answer cannot change. */
    public List<String> getNearestLandmarks(String pointId, String landmarkType, int limit) {
        List<String> answer = new ArrayList<>();
        Integer start = pointIndex.get(pointId);
        Map<Integer, List<Landmark>> landmarksAt = landmarksByType.get(landmarkType);
        if (start == null || landmarksAt == null || limit <= 0) return answer;

        int n = x.length;
        double[] dist = new double[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[start] = 0;
        boolean[] done = new boolean[n];
        // each entry is {distance, point}, the smallest distance comes out first
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        queue.add(new double[]{0, start});
        List<Landmark> found = new ArrayList<>(); // landmarks in the order we reach them

        while (!queue.isEmpty()) {
            double[] top = queue.poll();
            int point = (int) top[1];
            if (done[point]) continue;
            // We already have `limit` landmarks and this point is farther than the last one
            // we need. Every point still in the queue is at least this far, so stop.
            if (found.size() >= limit && top[0] > dist[found.get(limit - 1).point]) break;
            done[point] = true;
            if (landmarksAt.containsKey(point)) found.addAll(landmarksAt.get(point));

            for (int next : neighbors.get(point)) {
                double newDist = dist[point] + distanceBetween(point, next);
                if (newDist < dist[next]) {
                    dist[next] = newDist;
                    queue.add(new double[]{newDist, next});
                }
            }
        }
        // nearer landmarks first, for the same distance the smaller id first
        found.sort((a, b) -> {
            if (dist[a.point] != dist[b.point]) return Double.compare(dist[a.point], dist[b.point]);
            return a.id.compareTo(b.id);
        });
        for (int i = 0; i < found.size() && i < limit; i++) {
            answer.add(found.get(i).id);
        }
        return answer;
    }
}
```

### Complexity

- Constructor: `O(P + R + L)`, including building the components.
- `getShortestDistance`: `O(1)` when the points are in different components. Otherwise the worst case is still `O((P + R) log P)`, but A\* usually visits only a small part of the map.
- `getNearestLandmarks`: `O((P' + R') log P' + K log K)`, where `P'` and `R'` are the points and roads visited before stopping, and `K` is the number of landmarks collected (about `limit`, plus ties).
- Each query also creates arrays of size `P`, which is a quick `O(P)` step.

If fewer than `limit` matching landmarks can be reached, the landmark search still has to visit everything reachable, which is the same work as Solution 1. In every other case it stops as soon as the answer is certain, which is usually much earlier.

---

## Summary

| | Solution 1 | Solution 2 |
|---|---|---|
| Unreachable destination | explores everything reachable | instant `-1.0` using components |
| Shortest distance | full Dijkstra | A\* search, stops at the destination |
| Nearest landmarks | full Dijkstra, then sorts every landmark of the type | stops once the nearest `limit` landmarks are certain |