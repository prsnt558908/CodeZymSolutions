# Minimum Time To Burn Tree in Python

#### Problem Statement
[https://codezym.com/question/232-minimum-time-to-burn-tree](https://codezym.com/question/232-minimum-time-to-burn-tree)

The whole problem comes down to one simple idea: **the fire has to reach both ends of the longest path in the tree**. So the best place to light the fire is the middle of that longest path, and the answer is half of its length, rounded up.

We will start with a simple brute force that tries every node as the starting point. It is correct, but too slow. Then we will see that only the longest path matters, and we will find it with just two BFS runs. That gives us an `O(n)` solution.

---

## Understanding the Problem

We get a tree with `n` nodes and `n - 1` edges. Each edge comes as a string like `"1,3"`.

We light one node. In every time unit, the fire jumps from each burning node to all of its neighbors.

So for a chosen start node, the burn time is simply **the distance to the node farthest from it**. We want the start node that makes this time as small as possible.

Here is the tree from Example 1:

```
0 --- 1 --- 3 --- 4
      |
      2
```

If we start at node `1`, nodes `0`, `2` and `3` catch fire at time `1`, and node `4` catches fire at time `2`. No start node does better, so the answer is `2`.

---

## Approach 1: Brute Force (Try Every Start Node)

The most direct idea is to try every node as the starting point.

For each start node, we run a BFS. BFS explores the tree level by level, exactly the way the fire spreads. The largest distance it finds is the burn time for that start node.

We keep the smallest burn time over all start nodes.

```python
best = float("inf")
for start in range(n):
    dist = self.bfs(graph, start)  # distance of every node from start
    best = min(best, max(dist))
return best
```

### What is wrong with it?

One BFS takes `O(n)` time, and we run it `n` times. That is `O(n²)` in total.

With `n = 100,000`, this means about `10^10` (10 billion) steps. Far too slow.

Instead of trying every start node, we need a way to find the best one directly.

---

## Key Observation: Only the Longest Path Matters

Take the longest path in the tree. It is also called the **diameter** of the tree. Say it has `D` edges, and its two ends are `A` and `B`.

**1. We can never finish faster than `D / 2`, rounded up.**

Wherever the fire starts, it must reach both `A` and `B`. The distance from the start to `A`, plus the distance from the start to `B`, is at least `D`.

So at least one of the two ends is `D / 2` or more steps away. Distances are whole numbers, so this means `D / 2` rounded up.

**2. Starting in the middle of the longest path reaches exactly this time.**

Light the fire at the middle node of the longest path. Both `A` and `B` burn within `D / 2` steps, rounded up. When `D` is odd there are two middle nodes, and either one works. This is why Example 1 says node `1` or node `3`.

What about all the other nodes? None of them can be more than `D / 2` (rounded up) steps away from the middle.

If one were, we could walk from it, through the middle, to the end on the other side. That walk would be longer than `D`. This is impossible, because `D` is already the longest path.

So the answer is:

```
answer = (D + 1) // 2    # D / 2 rounded up, using integer division
```

Let us check it on the examples:

| Example | Longest path | D | Answer |
|---|---|---|---|
| 1 | `0 → 1 → 3 → 4` | 3 | 2 |
| 2 | `0 → 1 → 2 → 3 → 4 → 5` | 5 | 3 |
| 3 | just node `0` | 0 | 0 |

The problem is now much smaller. **We only need the length of the longest path.**

---

## Approach 2: Find the Longest Path With Two BFS (Optimal)

There is a neat, well-known trick to find the longest path in a tree:

1. Run BFS from any node, say node `0`. The farthest node it finds is `A`. This node is always one end of a longest path.
2. Run BFS again, this time from `A`. The farthest node it finds is `B`, and the distance from `A` to `B` is `D`.
3. Return `(D + 1) // 2`.

Just two BFS runs, no matter how big the tree is.

### Why is the farthest node always an end of a longest path?

`A` is the farthest node from node `0`, so it sits at an outer tip of the tree.

Take any longest path and swap one of its ends for `A`. Since nothing is farther from node `0` than `A`, the branch out to `A` is at least as long as the piece it replaces. So the new path is never shorter.

This means some longest path always ends at `A`. The second BFS, started from `A`, finds the other end of it.

### Dry run on Example 1

- BFS from `0` gives distances `0:0, 1:1, 2:2, 3:2, 4:3`. The farthest node is `4`, so `A = 4`.
- BFS from `4` gives distances `4:0, 3:1, 1:2, 0:3, 2:3`. The largest distance is `3`, so `D = 3`.
- The answer is `(3 + 1) // 2 = 2`.

### Data structures we need

- **Adjacency list (`List[List[int]]`)**: `graph[u]` holds all neighbors of node `u`. BFS needs to jump from a node to its neighbors quickly, and for a tree this list takes only `O(n)` memory.

- **Queue (`deque`)**: BFS must handle nodes in the order they were found, nearest first. A queue gives exactly this order, which is also the order in which the fire spreads. We use `deque` because it removes from the front in `O(1)`, while `list.pop(0)` is slow.

- **Distance list (`dist`)**: stores how far each node is from the BFS start. A value of `-1` means "not visited yet", so we do not need a separate visited set.

### Complexity

- **Time:** `O(n)`. Each BFS visits every node and edge once, and we run only two of them.
- **Space:** `O(n)` for the graph, the queue and the distance list.

---

## Python Code

```python
from collections import deque
from typing import List


class TreeBurning:

    def __init__(self):
        pass

    def minimumBurnTime(self, n: int, edges: List[str]) -> int:
        """Returns the minimum time to burn the whole tree.
        It is half the length of the longest path, rounded up."""
        graph = self.buildGraph(n, edges)

        # 1st BFS: the farthest node from node 0 is one end (A) of the longest path
        dist_from_zero = self.bfs(graph, 0)
        end_a = self.farthestNode(dist_from_zero)

        # 2nd BFS: the farthest node from A is the other end (B)
        dist_from_a = self.bfs(graph, end_a)
        end_b = self.farthestNode(dist_from_a)
        longest_path = dist_from_a[end_b]

        # Light the fire in the middle of the longest path
        return (longest_path + 1) // 2

    def buildGraph(self, n: int, edges: List[str]) -> List[List[int]]:
        """Builds an adjacency list from edges like "u,v"."""
        graph = [[] for _ in range(n)]
        for edge in edges:
            u, v = edge.split(",")
            u, v = int(u), int(v)
            graph[u].append(v)
            graph[v].append(u)
        return graph

    def bfs(self, graph: List[List[int]], start: int) -> List[int]:
        """BFS from start. Returns the distance of every node from start.
        This is also the time at which each node catches fire."""
        dist = [-1] * len(graph)  # -1 means "not visited yet"

        queue = deque([start])
        dist[start] = 0

        while queue:
            node = queue.popleft()
            for neighbor in graph[node]:
                if dist[neighbor] == -1:
                    dist[neighbor] = dist[node] + 1
                    queue.append(neighbor)
        return dist

    def farthestNode(self, dist: List[int]) -> int:
        """Returns the node that has the largest distance."""
        return dist.index(max(dist))
```