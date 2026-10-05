# Shortest Distance and Nearest Landmarks in Python

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
2. A **priority queue** holds `(distance, point)` pairs and always gives back the smallest distance first.
3. Take the nearest point out of the queue. Its distance is now final. Any other route to it would have to pass through a point still in the queue, which is already at least as far, and extra road can never make a trip shorter.
4. Look at every road leaving this point. If going through it gives a neighbor a shorter distance, update the neighbor and push it into the queue.

A point can be pushed more than once, once for every shorter route we discover. A `done` list lets us skip the older, longer copies.

The important result: **points come out of the queue in increasing order of distance.**

---

## Solution 1: Run Dijkstra on Every Call

This is the most direct approach. For each query, find the distance from the start to every point, then read off the answer.

### Data structures and why we need them

- `point_index` (dict): turns point ids like `"A"` into numbers `0, 1, 2 ...`. With numbers we can keep coordinates and distances in plain lists.
- `x`, `y` (lists): coordinates of each point.
- `neighbors` (list of lists): the adjacency list. `neighbors[i]` holds a `(next point, road length)` pair for every road at point `i`. Each road is added in both directions, and its length is computed only once, in the constructor.
- `landmarks_by_type` (dict): landmarks grouped by type as `(landmark id, point index)` pairs, so a query looks only at landmarks of the requested type.
- `heapq`: Python's priority queue on a plain list. Each entry is a `(distance, point)` tuple and the smallest distance comes out first.

### How each method works

**getShortestDistance**: run Dijkstra from the start and read the distance of the end point. If it is still infinity, the end was never reached, so return `-1.0`.

**getNearestLandmarks**: run Dijkstra from the given point. Keep the landmarks of the requested type whose point was reached, as `(distance, landmark id)` tuples. Python sorts tuples item by item, so a plain `sort()` orders them by distance and then by id, which is exactly the rule we need. Return the first `limit` ids.

### Code

```python
import heapq
import math
from typing import List


class LandmarkGraph:
    def __init__(self, points: List[str], roads: List[str], landmarks: List[str]):
        # point id -> index (0, 1, 2 ...), so that we can use plain lists
        self.point_index = {}
        # coordinates of every point, stored by index
        self.x = []
        self.y = []
        for i, point in enumerate(points):
            point_id, x, y = point.split(",")
            self.point_index[point_id.strip()] = i
            self.x.append(float(x))
            self.y.append(float(y))

        # neighbors[i] = list of (next point, road length) for every road at point i
        self.neighbors = [[] for _ in points]
        for road in roads:
            first, second = road.split(",")
            a = self.point_index[first.strip()]
            b = self.point_index[second.strip()]
            length = self.distance_between(a, b)
            # roads can be traveled in both directions
            self.neighbors[a].append((b, length))
            self.neighbors[b].append((a, length))

        # landmark type -> list of (landmark id, point index)
        self.landmarks_by_type = {}
        for landmark in landmarks:
            landmark_id, landmark_type, point_id = landmark.split(",")
            point = self.point_index[point_id.strip()]
            self.landmarks_by_type.setdefault(landmark_type.strip(), []).append((landmark_id.strip(), point))

    def distance_between(self, a: int, b: int) -> float:
        """Straight-line distance between two points. It is also the length of a road between them."""
        dx = self.x[a] - self.x[b]
        dy = self.y[a] - self.y[b]
        return math.sqrt(dx * dx + dy * dy)

    def shortest_distances_from(self, start: int) -> List[float]:
        """Dijkstra: shortest road distance from start to every point. Unreachable points stay at infinity."""
        dist = [math.inf] * len(self.x)
        dist[start] = 0.0
        done = [False] * len(self.x)
        queue = [(0.0, start)]  # (distance, point), the smallest distance comes out first

        while queue:
            distance, point = heapq.heappop(queue)
            if done[point]:
                continue  # an old copy with a longer distance
            done[point] = True
            for next_point, length in self.neighbors[point]:
                new_dist = distance + length
                if new_dist < dist[next_point]:
                    dist[next_point] = new_dist
                    heapq.heappush(queue, (new_dist, next_point))
        return dist

    def getShortestDistance(self, startPointId: str, endPointId: str) -> float:
        start = self.point_index.get(startPointId)
        end = self.point_index.get(endPointId)
        if start is None or end is None:
            return -1.0

        distance = self.shortest_distances_from(start)[end]
        return -1.0 if distance == math.inf else distance

    def getNearestLandmarks(self, pointId: str, landmarkType: str, limit: int) -> List[str]:
        start = self.point_index.get(pointId)
        if start is None or landmarkType not in self.landmarks_by_type or limit <= 0:
            return []

        dist = self.shortest_distances_from(start)
        reachable = [(dist[point], landmark_id)
                     for landmark_id, point in self.landmarks_by_type[landmarkType]
                     if dist[point] != math.inf]
        # nearer landmarks first, for the same distance the smaller id first
        reachable.sort()
        return [landmark_id for _, landmark_id in reachable[:limit]]
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

The `component` list answers this in `O(1)`, and building it costs only `O(P + R)` once.

### Improvement 2: Head toward the destination (A\* search)

Dijkstra grows like a circle around the start. It explores every direction equally, even the ones moving away from the destination.

But we know exactly where the destination is. Every road is a straight line, so the straight-line distance from a point to the destination is the shortest trip that could possibly be left. Real roads can only be longer or equal.

So we order the queue by:

```
distance traveled so far + straight-line distance left
```

Points that move toward the destination get small values and come out first. Points heading the wrong way get pushed back. This is called **A\* search**, and the code is Dijkstra with one extra addition.

Is the answer still correct? Yes. The straight-line guess never overestimates, so when the destination comes out of the queue, no other route can beat it. Also, one side of a triangle is never longer than the other two sides together. Because of this, just like in Dijkstra, a point's distance is final the first time it leaves the queue, so the same `done` list still works.

A nice bonus: the guess uses the same `distance_between()` method as the road lengths.

### Improvement 3: Collect landmarks while walking outward

Dijkstra hands out points nearest first. So if we check each point for landmarks of the requested type at the moment it leaves the queue, we meet the landmarks in order of their distance. We do not need to finish the search.

To make "does this point have any schools?" a quick check, the landmark index becomes `type → {point → landmark ids at that point}`. That is one dict lookup per point.

**When can we stop?** When we already hold at least `limit` landmarks and the point leaving the queue is **strictly farther** than the `limit`-th landmark we found. Every point still in the queue is at least that far, so nothing new can enter the answer.

**Why strictly farther?** Because of ties. A landmark at the exact same distance could still be waiting in the queue, and it might have a smaller id. So we keep going while the distance is equal, then sort the collected `(distance, landmark id)` tuples and keep the first `limit`.

Example 2: from `Center`, both `Left` and `Right` are `5.0` away and `limit = 1`. Say `Right` leaves the queue first, so we now hold `betaSchool`. Then `Left` leaves at `5.0`, which is not strictly farther, so we also collect `alphaSchool`. After sorting, `alphaSchool` comes first and is returned.

### Code

```python
import heapq
import math
from typing import List


class LandmarkGraph:
    def __init__(self, points: List[str], roads: List[str], landmarks: List[str]):
        # point id -> index (0, 1, 2 ...), so that we can use plain lists
        self.point_index = {}
        # coordinates of every point, stored by index
        self.x = []
        self.y = []
        for i, point in enumerate(points):
            point_id, x, y = point.split(",")
            self.point_index[point_id.strip()] = i
            self.x.append(float(x))
            self.y.append(float(y))

        # neighbors[i] = list of (next point, road length) for every road at point i
        self.neighbors = [[] for _ in points]
        for road in roads:
            first, second = road.split(",")
            a = self.point_index[first.strip()]
            b = self.point_index[second.strip()]
            length = self.distance_between(a, b)
            # roads can be traveled in both directions
            self.neighbors[a].append((b, length))
            self.neighbors[b].append((a, length))

        # landmark type -> {point index -> ids of the landmarks of that type at that point}
        self.landmarks_by_type = {}
        for landmark in landmarks:
            landmark_id, landmark_type, point_id = landmark.split(",")
            point = self.point_index[point_id.strip()]
            points_of_type = self.landmarks_by_type.setdefault(landmark_type.strip(), {})
            points_of_type.setdefault(point, []).append(landmark_id.strip())

        # component[i] = id of the group of connected points that point i belongs to
        self.component = self.find_components()

    def distance_between(self, a: int, b: int) -> float:
        """Straight-line distance between two points. It is also the length of a road between them."""
        dx = self.x[a] - self.x[b]
        dy = self.y[a] - self.y[b]
        return math.sqrt(dx * dx + dy * dy)

    def find_components(self) -> List[int]:
        """Gives the same component id to all points that can reach each other."""
        component = [-1] * len(self.x)
        next_id = 0
        for first in range(len(self.x)):
            if component[first] != -1:
                continue
            # flood fill: every point reachable from `first` gets the same id
            component[first] = next_id
            stack = [first]
            while stack:
                point = stack.pop()
                for next_point, _ in self.neighbors[point]:
                    if component[next_point] == -1:
                        component[next_point] = next_id
                        stack.append(next_point)
            next_id += 1
        return component

    def getShortestDistance(self, startPointId: str, endPointId: str) -> float:
        """A* search: Dijkstra that also adds the straight-line distance still left to the destination."""
        start = self.point_index.get(startPointId)
        end = self.point_index.get(endPointId)
        # points in different components can never reach each other
        if start is None or end is None or self.component[start] != self.component[end]:
            return -1.0

        dist = [math.inf] * len(self.x)
        dist[start] = 0.0
        done = [False] * len(self.x)
        # (distance traveled + straight-line distance left, point)
        queue = [(self.distance_between(start, end), start)]

        while queue:
            _, point = heapq.heappop(queue)
            if point == end:
                return dist[end]  # the first time it comes out, it is the shortest
            if done[point]:
                continue
            done[point] = True
            for next_point, length in self.neighbors[point]:
                new_dist = dist[point] + length
                if new_dist < dist[next_point]:
                    dist[next_point] = new_dist
                    heapq.heappush(queue, (new_dist + self.distance_between(next_point, end), next_point))
        return -1.0

    def getNearestLandmarks(self, pointId: str, landmarkType: str, limit: int) -> List[str]:
        """Dijkstra that visits points nearest first and stops as soon as the answer cannot change."""
        start = self.point_index.get(pointId)
        landmarks_at = self.landmarks_by_type.get(landmarkType)
        if start is None or landmarks_at is None or limit <= 0:
            return []

        dist = [math.inf] * len(self.x)
        dist[start] = 0.0
        done = [False] * len(self.x)
        queue = [(0.0, start)]  # (distance, point), the smallest distance comes out first
        found = []  # (distance, landmark id) in the order we reach them

        while queue:
            distance, point = heapq.heappop(queue)
            if done[point]:
                continue
            # We already have `limit` landmarks and this point is farther than the last one
            # we need. Every point still in the queue is at least this far, so stop.
            if len(found) >= limit and distance > found[limit - 1][0]:
                break
            done[point] = True
            for landmark_id in landmarks_at.get(point, []):
                found.append((distance, landmark_id))

            for next_point, length in self.neighbors[point]:
                new_dist = distance + length
                if new_dist < dist[next_point]:
                    dist[next_point] = new_dist
                    heapq.heappush(queue, (new_dist, next_point))

        # nearer landmarks first, for the same distance the smaller id first
        found.sort()
        return [landmark_id for _, landmark_id in found[:limit]]
```

### Complexity

- Constructor: `O(P + R + L)`, including building the components.
- `getShortestDistance`: `O(1)` when the points are in different components. Otherwise the worst case is still `O((P + R) log P)`, but A\* usually visits only a small part of the map.
- `getNearestLandmarks`: `O((P' + R') log P' + K log K)`, where `P'` and `R'` are the points and roads visited before stopping, and `K` is the number of landmarks collected (about `limit`, plus ties).
- Each query also creates lists of size `P`, which is a quick `O(P)` step.

If fewer than `limit` matching landmarks can be reached, the landmark search still has to visit everything reachable, which is the same work as Solution 1. In every other case it stops as soon as the answer is certain, which is usually much earlier.

---

## Summary

| | Solution 1 | Solution 2 |
|---|---|---|
| Unreachable destination | explores everything reachable | instant `-1.0` using components |
| Shortest distance | full Dijkstra | A\* search, stops at the destination |
| Nearest landmarks | full Dijkstra, then sorts every landmark of the type | stops once the nearest `limit` landmarks are certain |