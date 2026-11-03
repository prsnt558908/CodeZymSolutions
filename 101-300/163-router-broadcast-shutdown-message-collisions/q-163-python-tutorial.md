# Router Broadcast and Shutdown Message With Collisions in Python

#### Problem Statement
[https://codezym.com/question/163-router-broadcast-shutdown-message-collisions](https://codezym.com/question/163-router-broadcast-shutdown-message-collisions)


This problem is about following a message as it spreads through a network of routers. Without collisions, it would be a plain BFS. But collisions depend on exactly **when** messages arrive, so we must follow the message in time order.

We do this the same way Dijkstra's shortest path algorithm does. A min-heap always gives us the router that the message reaches next, and the travel time between two routers is simply their distance.

The key trick is that each router only needs to remember its **two earliest arrival times**. If they are within `0.1` of each other, the router got a collision and stays silent. Otherwise it forwards the message once and shuts down.


## The Rules in Simple Words

- A message from router `A` to router `B` takes `distance(A, B)` time, and it can only be sent if that distance is at most `range`.
- A router only cares about its **earliest** message. Anything that arrives later is ignored.
- If two or more messages land at that earliest time (within `0.1`), they collide and the router stays silent.
- A router that receives the message cleanly broadcasts it once and then shuts down.


## Basic Solution: Plain BFS

If there were no collisions, this would be a simple reachability check.

Think of every router as a node, with an edge between two routers that are within range. A BFS from the source tells us if the destination can be reached.

```python
# Basic idea: plain BFS, every router passes the message on once
queue = deque([source])
visited = [False] * len(all_routers)
visited[source] = True

while queue:
    current = queue.popleft()
    if current == destination:
        return True
    for nxt, other in enumerate(all_routers):
        if not visited[nxt] and all_routers[current].distanceTo(other) <= range:
            visited[nxt] = True
            queue.append(nxt)
return False
```

**The problem:** BFS has no idea about time.

In Example 1, router `D` gets two messages at time `10.0`, one from `B` and one from `C`. They collide, so the answer is `False`. But BFS simply marks `D` as visited and returns `True`.

To catch collisions, we need the exact time at which every message lands.


## Better Solution: Follow the Message in Time Order

### Step 1: Pick routers by arrival time

A router that receives the message at time `t` broadcasts right away. Its message reaches a router at distance `d` at time `t + d`.

So we replace the BFS queue with a **min-heap** ordered by arrival time. We always pick the router that the message reaches next.

This is the same idea as Dijkstra's shortest path, where the distance between two routers is the edge weight.


### Step 2: Remember only the two earliest times

A router only cares about two things: its earliest message, and whether another message lands within `0.1` of it. Every later message is ignored.

So each router keeps just two numbers, `first_time` and `second_time`.

```python
def receive(self, time: float) -> bool:
    if time < self.first_time:   # new earliest time
        self.second_time = self.first_time
        self.first_time = time
        return True
    if time < self.second_time:  # new second earliest time
        self.second_time = time
    return False

def isCollided(self) -> bool:
    return self.second_time - self.first_time <= 0.1
```

`receive()` returns `True` only when the earliest time gets better. That is the only moment we need to add the router to the heap.

When a router comes out of the heap:

- If `isCollided()` is `True`, it stays silent.
- Otherwise it broadcasts to every router in range that is still listening, and then shuts down.

**Why not just count the messages?** Messages do not reach a router in time order. A router we process later can send a message that lands earlier than one we already saw.

When the earliest time changes, a simple counter cannot tell which old messages are still within `0.1` of the new earliest time. Keeping the two smallest times always gives the right answer.


### Step 3: Why we can decide as soon as a router comes out of the heap

Say a router comes out of the heap at time `t`. Every router that already broadcast has already recorded its message at each router in range. Every router that has not broadcast yet will do it at time `t` or later.

Coordinates are integers, so two routers at different points are at least `1` unit apart. Any new message would land at `t + 1` or later, which is far outside the `0.1` window.

So the two earliest times of this router are already final, and we can decide right away.


### Step 4: Stop at the destination

When the destination comes out of the heap, its fate is decided. Return `True` if it did not collide, otherwise return `False`.

If the heap becomes empty first, the message never reached the destination cleanly, so return `False`.


### Edge Cases

- If the source or the destination id is not in the list, return `False`.
- If the source is the same as the destination, return `True`.


### Dry Run on Example 1

`routers = ["A,0,0", "B,3,4", "C,4,3", "D,7,7"]`, source `A`, destination `D`, `range = 6`

| Heap gives | Two earliest times | What happens |
|---|---|---|
| `A` at `0` | `0`, none | Broadcasts. `B` gets `5`, `C` gets `5`. `D` is `9.9` away, out of range. |
| `B` at `5` | `5`, none | Broadcasts. `C` gets `5 + 1.41 = 6.41`, `D` gets `10`. |
| `C` at `5` | `5`, `6.41` | Gap is `1.41`, no collision. Broadcasts. `D` gets `10`. |
| `D` at `10` | `10`, `10` | Gap is `0`, collision. Return `False`. |


## Important Classes and Data Structures

- **`Router`**: stores the position, the two earliest arrival times and a `done` flag. `done` becomes `True` once the router has broadcast or collided, so we never touch it again.
- **`index_by_id` (dict)**: maps a router id to its index in the list. It lets us find the source and the destination quickly and check that they exist.
- **`queue` (a list used as a min-heap with `heapq`)**: each entry is a tuple `(arrival time, router index)`. Tuples compare by time first and then by index, so the smallest time always comes out first.

`heapq` cannot update an entry in place. So when a router gets an earlier time, we just push a new entry.

The old entry comes out later and is skipped, because by then the router is already marked `done`.


## Python Code

```python
import heapq
import math
from typing import List


class Router:
    """One router: its position and the two earliest times a message reached it."""

    def __init__(self, x: int, y: int):
        self.x = x
        self.y = y
        self.first_time = math.inf   # earliest arrival time
        self.second_time = math.inf  # second earliest arrival time
        self.done = False            # True once the router has broadcast or collided

    def distanceTo(self, other: "Router") -> float:
        dx = self.x - other.x
        dy = self.y - other.y
        return math.sqrt(dx * dx + dy * dy)

    def receive(self, time: float) -> bool:
        """Records a message that arrives at the given time.
        Returns True if this message is the new earliest arrival."""
        if time < self.first_time:
            self.second_time = self.first_time
            self.first_time = time
            return True
        if time < self.second_time:
            self.second_time = time
        return False

    def isCollided(self) -> bool:
        """A second message within 0.1 of the earliest one means a collision."""
        return self.second_time - self.first_time <= 0.1


class RouterBroadcastWithCollisions:

    def __init__(self):
        pass

    def canReachDestination(self, routers: List[str], sourceRouter: str,
                            destinationRouter: str, range: float) -> bool:
        # note: the parameter "range" hides Python's built-in range() inside this method

        # parse "routerId,x,y" and give every router an index
        all_routers = []
        index_by_id = {}
        for text in routers:
            parts = text.split(",")
            index_by_id[parts[0]] = len(all_routers)
            all_routers.append(Router(int(parts[1]), int(parts[2])))

        if sourceRouter not in index_by_id or destinationRouter not in index_by_id:
            return False
        if sourceRouter == destinationRouter:
            return True
        source = index_by_id[sourceRouter]
        destination = index_by_id[destinationRouter]

        # min-heap of (arrival time, router index): smallest time first, ties go to the smaller index
        all_routers[source].receive(0.0)
        queue = [(0.0, source)]

        while queue:
            time, index = heapq.heappop(queue)
            router = all_routers[index]

            if router.done:
                continue  # old heap entry, this router is already finished
            router.done = True

            if index == destination:
                return not router.isCollided()
            if router.isCollided():
                continue  # collided routers never broadcast

            # broadcast to every router in range that is still listening
            for i, other in enumerate(all_routers):
                if other.done:
                    continue
                distance = router.distanceTo(other)
                if distance > range:
                    continue
                arrival_time = time + distance
                if other.receive(arrival_time):
                    heapq.heappush(queue, (arrival_time, i))

        return False
```


## Complexity

- **Time:** `O(n^2 log n)` in the worst case. Each router broadcasts at most once and checks all `n` routers, and each check can add one entry to the heap. In practice only a few checks add entries, so it runs close to `O(n^2)`.
- **Space:** `O(n)` for the routers and the dict, plus one heap entry for every time a router finds an earlier arrival time.