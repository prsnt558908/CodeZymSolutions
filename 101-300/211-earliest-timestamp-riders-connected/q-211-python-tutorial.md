# Earliest Timestamp When All Riders Are Connected in Python

#### Problem Statement
[https://codezym.com/question/211-earliest-timestamp-riders-connected](https://codezym.com/question/211-earliest-timestamp-riders-connected)


## Core Idea

Think of every rider as a dot, and every shared ride as a connection (a line) between two dots. Riders who can reach each other through these connections form one **group**.

At the start, every rider is a group of their own. Each share can join two groups into one. So the answer is simply the first moment when only **one group** is left.

The best tool for this is **Union-Find** (also called Disjoint Set Union). It joins two groups and keeps a live count of groups, and each step takes almost constant time.

The follow-up adds `BLOCK` events that remove a connection. Union-Find can join groups, but it cannot split them. So we also keep a record of the active connections and rebuild Union-Find from it, but only in the rare moments when it really matters.


## Part 1: Only Share Events

### Brute Force: Run BFS After Every Log

The most direct idea is to build the graph one log at a time. After adding each connection, run a BFS from any rider and count how many riders it reaches. If it reaches everyone, return the current timestamp.

```python
if len(riders) == 1:
    return 0

graph = {rider: [] for rider in riders}

for log in logs:
    timestamp, rider1, rider2 = log.split(",")
    graph[rider1].append(rider2)
    graph[rider2].append(rider1)

    # BFS from the first rider and count everyone we can reach
    if self.countReachable(graph, riders[0]) == len(riders):
        return int(timestamp)
return -1
```

**Problem:** every BFS can walk over all riders and all connections again. With 100,000 riders and 200,000 logs, that is billions of steps.

Most of this work is repeated. One share only joins two groups, and everything else stays the same. We need something that remembers the groups and updates them in one quick step.


### Better Solution: Union-Find

**Step 1: Give every rider a number.**

Union-Find works with lists, so `buildRiderIds()` maps each rider name to a number from `0` to `n - 1` using a dictionary.

**Step 2: Track groups with the `UnionFind` class.**

Every group has a **leader**.

- `parent[i]` points toward the leader of rider `i`. A leader points to itself.
- `find(x)` follows these pointers until it reaches the leader.
- `union(a, b)` finds both leaders. If they are different, one leader is placed under the other, and the two groups become one.

**Step 3: Count the groups.**

`components` starts at `n`. Every real merge reduces it by one. The moment it becomes `1`, everyone is connected, and the current timestamp is the answer.

Two small tricks keep Union-Find fast:

- **Union by size:** always attach the smaller group under the bigger one, so the trees stay short.
- **Path compression:** while `find` walks up, it makes riders point closer to the leader, so later lookups are faster.

Duplicate shares need no extra code. If two riders are already in the same group, `union` does nothing.

If there is only one rider, they are already connected, so we return `0` right away.

**Example 1 walk-through:**

| Log | Groups after the log | Count |
|---|---|---|
| start | {Ava} {Ben} {Cara} {Dev} | 4 |
| `101,Ava,Ben` | {Ava, Ben} {Cara} {Dev} | 3 |
| `105,Cara,Dev` | {Ava, Ben} {Cara, Dev} | 2 |
| `110,Ben,Cara` | {Ava, Ben, Cara, Dev} | 1, so return `110` |


## Follow-up: Share and Block Events

A `BLOCK` removes a direct connection. This breaks our plan, because Union-Find can only join groups. It can never split them.

For example, after `SHARE A,B` and then `BLOCK A,B`, Union-Find still thinks A and B are in the same group.


### Simple Fix: Rebuild After Every Event

Keep a set of the **active** connections. A share adds a connection and a block removes it. After every event, build a fresh Union-Find from the active connections and check the count.

```python
for log in logs:
    # parse the log, then add or remove the connection in active_edges

    groups = self.rebuild(n, active_edges)
    if groups.components == 1:
        return int(timestamp)
return -1
```

This is correct, but each rebuild touches every rider and every active connection. Doing it after all 10,000 events can cost around 100 million steps, and most of those rebuilds change nothing.


### Better Solution: Rebuild Only When Needed

Three simple observations remove almost all the rebuilds.

**1. A block can never connect everyone.**

A block only removes a connection. Before the block, riders were not all connected (otherwise we would have returned already). So after the block, they are still not connected. We just remove the connection. No check is needed.

**2. A share only adds a connection.**

So we can `union` it into our current Union-Find, exactly like Part 1. No rebuild is needed.

**3. An outdated Union-Find can only be "too connected".**

After a block, Union-Find may still remember the removed connection. So it may think some riders are together when they are not. But it never misses a real connection, because every active connection was added to it.

This gives a simple rule:

- If Union-Find says **more than one group**, it is surely right.
- If it says **one group**, but a block has removed a connection since the last rebuild, we cannot trust it. Only then do we rebuild from the active connections and check again.

The boolean `outdated` tracks this. A block that really removes a connection sets it to `True`. A rebuild sets it back to `False`.

**Why a `set` of pairs for active connections?**

Each connection is stored as a tuple of the smaller and the larger rider number, like `(2, 5)`. So `A,B` and `B,A` become the same pair, and duplicate shares never create a second connection.

A block can check and remove the pair in constant time, and a rebuild can simply loop over the pairs.

**Example 5 walk-through** (riders A, B, C):

| Log | What happens | Union-Find groups | Real groups |
|---|---|---|---|
| `10,SHARE,A,B` | union A and B | {A, B} {C} | {A, B} {C} |
| `15,BLOCK,A,B` | remove A-B, set `outdated = True` | {A, B} {C} (outdated) | {A} {B} {C} |
| `20,SHARE,B,C` | union gives 1 group, but it is outdated, so rebuild and set `outdated = False` | {A} {B, C} | {A} {B, C} |
| `25,SHARE,A,C` | union gives 1 group, not outdated | {A, B, C} | {A, B, C}, so return `25` |

Events with the same timestamp are still handled one at a time. If everyone becomes connected in the middle of them, that timestamp is the answer.


## Complexity

`N` = number of riders, `L` = number of logs.

| Method | Time | Space |
|---|---|---|
| `earliestFullConnectivityTimestamp` | `O(N + L)`, since each union is almost constant time | `O(N)` |
| `earliestFullConnectivityTimestampWithBlocks` | `O(N + L)`, plus `O(N + L)` for each rebuild | `O(N + L)` |

Every rebuild needs at least one block before it, so there are never more rebuilds than blocks. In practice, there are usually only a few.


## Final Python Code

```python
from typing import Dict, List, Set, Tuple


class UnionFind:
    """Union-Find (Disjoint Set Union).
    Keeps riders in groups and keeps a live count of how many groups exist.
    """

    def __init__(self, n: int):
        self.parent = list(range(n))  # parent[i] points toward the leader of i's group
        self.size = [1] * n           # size[leader] = number of riders in that group
        self.components = n           # number of separate groups right now

    def find(self, x: int) -> int:
        """Returns the leader of x's group."""
        while self.parent[x] != x:
            # path compression: point x to its grandparent, so the next lookup is shorter
            self.parent[x] = self.parent[self.parent[x]]
            x = self.parent[x]
        return x

    def union(self, a: int, b: int) -> None:
        """Joins the groups of a and b. Does nothing if they are already in the same group."""
        leader_a = self.find(a)
        leader_b = self.find(b)
        if leader_a == leader_b:
            return
        # union by size: attach the smaller group under the bigger one to keep trees short
        if self.size[leader_a] < self.size[leader_b]:
            leader_a, leader_b = leader_b, leader_a
        self.parent[leader_b] = leader_a
        self.size[leader_a] += self.size[leader_b]
        self.components -= 1


class SharedRideConnectivity:

    def __init__(self):
        pass

    def earliestFullConnectivityTimestamp(self, riders: List[str], logs: List[str]) -> int:
        rider_ids = self.buildRiderIds(riders)
        n = len(rider_ids)
        if n == 1:
            return 0

        groups = UnionFind(n)
        for log in logs:
            timestamp, rider1, rider2 = log.split(",")
            groups.union(rider_ids[rider1], rider_ids[rider2])
            if groups.components == 1:
                return int(timestamp)
        return -1

    def earliestFullConnectivityTimestampWithBlocks(self, riders: List[str], logs: List[str]) -> int:
        rider_ids = self.buildRiderIds(riders)
        n = len(rider_ids)
        if n == 1:
            return 0

        # (smaller id, larger id) for every share connection that is active now
        active_edges = set()
        groups = UnionFind(n)
        # True when a BLOCK removed a connection that "groups" still remembers
        outdated = False

        for log in logs:
            timestamp, event_type, rider1, rider2 = log.split(",")
            a = rider_ids[rider1]
            b = rider_ids[rider2]
            edge = (min(a, b), max(a, b))

            if event_type == "BLOCK":
                # Removing a connection can never connect everyone, so there is nothing to check.
                if edge in active_edges:
                    active_edges.remove(edge)
                    outdated = True
                continue

            # SHARE event
            active_edges.add(edge)
            groups.union(a, b)

            # "groups" never misses a real connection, so "more than one group" is always true.
            # But if it is outdated, "one group" may be wrong, so rebuild and check again.
            if groups.components == 1 and outdated:
                groups = self.rebuild(n, active_edges)
                outdated = False
            if groups.components == 1:
                return int(timestamp)
        return -1

    def buildRiderIds(self, riders: List[str]) -> Dict[str, int]:
        """Gives every distinct rider a number from 0 to n - 1, so Union-Find can use lists."""
        rider_ids = {}
        for rider in riders:
            if rider not in rider_ids:
                rider_ids[rider] = len(rider_ids)
        return rider_ids

    def rebuild(self, n: int, active_edges: Set[Tuple[int, int]]) -> UnionFind:
        """Builds a fresh Union-Find using only the connections that are active right now."""
        groups = UnionFind(n)
        for a, b in active_edges:
            groups.union(a, b)
        return groups
```