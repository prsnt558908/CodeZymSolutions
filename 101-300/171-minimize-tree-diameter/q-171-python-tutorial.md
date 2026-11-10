# Use Path Operations To Minimize Tree Diameter in Python

#### Problem Statement
[https://codezym.com/question/171-minimize-tree-diameter](https://codezym.com/question/171-minimize-tree-diameter)

## Core Idea

The smallest diameter a tree with 3 or more vertices can have is 2. The only tree with diameter 2 is a **star**, where one center vertex is directly connected to every other vertex.

So the real question is: how many operations does it take to turn our tree into a star?

The answer depends only on the **leaves** (vertices with just one edge). We pick the vertex that already touches the most leaves as the center. Every other leaf costs exactly one operation.

**Answer = total leaves - (most leaves touching a single vertex)**

A tree with only 2 vertices is already as small as it can get, so its answer is 0. Everything runs in O(n) with two simple counting lists.

## How One Operation Works

You pick `start` and `end`. Every vertex on the path between them gets connected directly to `start`. The path is folded into a small star around `start`.

Example: tree `1 - 2 - 3 - 4`, operation with `start = 2` and `end = 4`.

```
Before:   1 - 2 - 3 - 4

After:        1
              |
          3 - 2 - 4
```

The path `2, 3, 4` lost its edges `(2,3)` and `(3,4)` and got `(2,3)` and `(2,4)` instead. Vertex 2 now touches everyone, so the diameter dropped from 3 to 2.

Two small facts we will use later:

1. Only edges **on the path** change. The rest of the tree stays the same.
2. A leaf has only one edge, so it can never be in the **middle** of a path. A leaf on the path is always `start` or `end`.

## Step 1: What Is The Target?

- **2 vertices:** the tree is one edge with diameter 1. Nothing is smaller, so the answer is `0`.
- **3 or more vertices:** diameter 1 would need every pair of vertices to be directly connected, which is impossible in a tree. Diameter 2 is possible, and a tree has diameter 2 only when it is a star.

So for 3 or more vertices, our goal is to make the tree a star.

## Step 2: Far Leaves

Pick any vertex `c` as the future center of the star.

Call a leaf **far** if it is not `c` and not a direct neighbor of `c`. A star centered at `c` has no far leaves.

**One operation can fix one far leaf.**

Do the operation with `start = c` and `end = far_leaf`. Now `far_leaf` touches `c`.

Middle vertices of the path that turn into leaves are attached to `c`, so they are not far. All other leaves are off the path, so nothing changes for them.

**One operation can never fix two far leaves.**

Leaves off the path keep their one edge, so they stay as they were. On the path, only `start` and `end` can be leaves.

- If `start` is `c`, then `start` is not a far leaf. Only `end` can get fixed.
- If `start` is not `c`, then `end` gets connected to `start`, not to `c`, so it stays far. Only `start` can get fixed, because it gains new edges and stops being a leaf.

**No far leaves means the tree is a star.**

If some vertex is 2 or more steps away from `c`, keep walking away from `c`. You will end at a leaf that is also 2 or more steps away, which is a far leaf. So with zero far leaves, every vertex touches `c`.

**Putting it together**

For a chosen center `c`, we need exactly as many operations as `c` has far leaves.

The final star has some center. Whichever vertex that is, we needed at least as many operations as its far leaves at the start.

So the answer is the **smallest number of far leaves over all choices of `c`**.

## Brute Force: Try Every Center

The direct way: for every vertex `c`, go through all leaves and count the far ones. Keep the smallest count.

```python
best = float("inf")
for center in range(1, vertexCount + 1):
    far_leaves = 0
    for leaf in leaves:
        touches_center = leaf in neighbors[center]
        if leaf != center and not touches_center:
            far_leaves += 1
    best = min(best, far_leaves)
return best
```

Here `leaves` is a list of all leaves and `neighbors[v]` is a set with the neighbors of `v`.

**The problem:** for every center we scan every leaf. A tree can have almost `n` leaves (a star, for example), so this is O(n²). With `n = 200,000` that is around 40 billion steps. Way too slow.

## Optimized: Count Leaf Neighbors In One Pass

Flip the question. Instead of counting far leaves for each center, count the leaves that **touch** each center.

If `c` is not a leaf:

```
far leaves of c = total_leaves - leaf_neighbors[c]
```

Here `leaf_neighbors[c]` is the number of leaves directly connected to `c`.

So the best center is simply the vertex with the **largest** `leaf_neighbors` value.

What about a leaf as the center? It is never better than its only neighbor, because that neighbor already touches it. So we can ignore leaves as centers. The one exception is the 2 vertex tree, where both vertices are leaves, and Step 1 already handles it.

**Filling `leaf_neighbors` in one pass:** a leaf has exactly one edge. So for every edge `(a, b)`, if `a` is a leaf it adds 1 to `b`, and if `b` is a leaf it adds 1 to `a`.

### Data Structures We Need

- **`degree` list:** tells us which vertices are leaves (degree 1).
- **`leaf_neighbors` list:** for every vertex, how many leaves touch it.
- **`edges` list:** the parsed `(a, b)` pairs. We loop over them a second time, because we can only tell who is a leaf after all degrees are counted.

We do not need an adjacency list at all. We never walk through the tree, and every edge already tells us which vertex a leaf is attached to.

## Dry Run: Example 4

Edges: `1-2, 2-3, 3-4, 3-5, 2-6, 6-7`

```
1 - 2 - 3 - 4
    |   |
    6   5
    |
    7
```

| Vertex | Degree | Leaves touching it |
|---|---|---|
| 1 | 1 (leaf) | 0 |
| 2 | 3 | 1 (vertex 1) |
| 3 | 3 | 2 (vertices 4, 5) |
| 4 | 1 (leaf) | 0 |
| 5 | 1 (leaf) | 0 |
| 6 | 2 | 1 (vertex 7) |
| 7 | 1 (leaf) | 0 |

Total leaves = 4. The best center is vertex 3, which touches 2 leaves.

Answer = 4 - 2 = **2**

The two operations:

1. `start = 3, end = 1`: path `3, 2, 1` folds, so 1 now touches 3.
2. `start = 3, end = 7`: path `3, 2, 6, 7` folds, so 6 and 7 now touch 3.

Vertex 3 now touches every other vertex. The tree is a star.

## Complexity

- **Time:** O(n). We read the edges once, loop over them once more, then scan all vertices once.
- **Space:** O(n) for the lists.

## Python Code

```python
from typing import List


class TreeDiameterPathOperation:

    def __init__(self):
        pass

    def minimumOperationsToMinimizeDiameter(self, vertexCount: int, connections: List[str]) -> int:
        """Returns the minimum number of path operations needed
        to make the tree diameter as small as possible."""

        # A single edge already has diameter 1, the smallest possible.
        if vertexCount <= 2:
            return 0

        # degree[v] = number of edges touching v. A leaf has degree 1.
        degree = [0] * (vertexCount + 1)
        edges = []

        for connection in connections:
            a, b = connection.split(",")
            a, b = int(a), int(b)
            edges.append((a, b))
            degree[a] += 1
            degree[b] += 1

        # leaf_neighbors[v] = number of leaves directly connected to v.
        # A leaf has only one edge, so it adds 1 to the vertex on the other side.
        leaf_neighbors = [0] * (vertexCount + 1)
        for a, b in edges:
            if degree[a] == 1:
                leaf_neighbors[b] += 1
            if degree[b] == 1:
                leaf_neighbors[a] += 1

        total_leaves = 0
        for v in range(1, vertexCount + 1):
            if degree[v] == 1:
                total_leaves += 1

        # Best center = vertex touching the most leaves.
        # Every other leaf needs exactly one operation.
        return total_leaves - max(leaf_neighbors)
```