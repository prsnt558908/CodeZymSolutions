# Dasher Coverage of Connected User Areas in Python

#### Problem Statement
[https://codezym.com/question/275-dasher-coverage-connected-user-area](https://codezym.com/question/275-dasher-coverage-connected-user-area)


## Core Idea

This is the classic "count the islands" problem with one extra check.

User cells (`'1'` in `userClusters`) that touch each other up, down, left or right form one user cluster. Think of each cluster as an island on a map.

We scan the grid one cell at a time. When we reach a user cell we have not seen before, we have found a new cluster. We visit every cell of that cluster and check if each one also has a Dasher (`'1'` in `dasherAvailability`) at the same position. If all of them do, we count the cluster.

We will first write a simple recursive DFS (depth first search). Then we will see why it can crash on big grids, and fix it with a BFS (breadth first search) that uses a queue.


## A Quick Example

Here is Example 2:

```
userClusters        dasherAvailability
1 1 1 0             1 0 1 0
1 0 0 0             1 0 0 0
0 1 1 1             0 1 1 1
```

- **Upper cluster:** cells `(0,0)`, `(0,1)`, `(0,2)` and `(1,0)`. Cell `(0,1)` has no Dasher, so this cluster is **not** covered.
- **Lower cluster:** cells `(2,1)`, `(2,2)` and `(2,3)`. All of them have a Dasher, so this cluster **is** covered.

Cells `(1,0)` and `(2,1)` touch only diagonally, so they belong to different clusters.

Answer: `1`


## Approach 1: Recursive DFS (Simple Version)

We keep a 2D `visited` list. It remembers every user cell that already belongs to an explored cluster. Because of it, the search never goes back and forth between two cells forever, and no cluster is counted twice.

We also use a small helper, `isUserCell(row, col)`. It returns `True` if the cell is inside the grid and has users on it.

For every user cell that is not visited yet, we call `dfs(row, col)`. It marks the cell as visited, checks the Dasher at that cell, and then calls itself on the 4 neighbours. It returns `True` only if this cell and every cell reached from it have a Dasher.

```python
# main loop: every unvisited user cell starts a new cluster
for row in range(self.rows):
    for col in range(self.cols):
        if self.isUserCell(row, col) and not self.visited[row][col] and self.dfs(row, col):
            coveredCount += 1


# returns True if every cell reachable from (row, col) has a Dasher
def dfs(self, row: int, col: int) -> bool:
    if not self.isUserCell(row, col) or self.visited[row][col]:
        return True
    self.visited[row][col] = True

    covered = self.dasherAvailability[row][col] == '1'

    # visit all 4 neighbours, even if this cell has no Dasher
    down = self.dfs(row + 1, col)
    up = self.dfs(row - 1, col)
    right = self.dfs(row, col + 1)
    left = self.dfs(row, col - 1)

    return covered and down and up and right and left
```


### A Common Mistake

It is tempting to write the last part in one line:

```python
return covered and self.dfs(row + 1, col) and self.dfs(row - 1, col) and self.dfs(row, col + 1) and self.dfs(row, col - 1)
```

This gives wrong answers. `and` stops at the first `False`, so the remaining `dfs` calls never run and part of the cluster stays unvisited.

For example, take users `"11"` and Dashers `"01"`. The first cell has no Dasher, so `dfs` returns at once and never visits the second cell. The main loop then treats the second cell as a new cluster, finds a Dasher there, and counts it. The answer becomes `1` instead of `0`.

So even after we find a missing Dasher, we must finish visiting the whole cluster.


### Problem With This Approach

Each recursive call stays on the call stack until all its neighbours are done.

A single cluster can fill the whole `500 x 500` grid. For such a cluster, the recursion can go `250,000` calls deep, one call for every cell. Python's default recursion limit is only `1000`, so the program fails with a `RecursionError`.

Raising the limit with `sys.setrecursionlimit()` may work on some setups, but it is not reliable.

The logic is correct. We just need to stop depending on the call stack.


## Approach 2: BFS With a Queue (Final Solution)

Instead of recursion, we keep our own queue of cells that are waiting to be explored.

**Why a deque?** We use `deque` from the `collections` module as our queue. It does the job of the call stack, but it has no depth limit, so it can easily hold all `250,000` cells. Each entry is a tuple `(row, col)`.

Adding at the back (`append`) and removing from the front (`popleft`) are both `O(1)` for a deque. A plain list would be slow here, because `list.pop(0)` shifts every remaining item.

Using our own stack instead of a queue would also work, because the order of visiting does not matter. We only need to reach every cell of the cluster once.

Here is how `exploreCluster(startRow, startCol)` works:

1. Add the starting cell to the queue and mark it as visited.
2. Take a cell out of the queue. If it has no Dasher, set `covered = False`, but keep going.
3. Check its 4 neighbours. Each neighbour that is a user cell and not visited yet is marked as visited and added to the queue.
4. Repeat until the queue is empty. Now every cell of the cluster has been visited.
5. Return `covered`.

We mark a cell as visited when we **add** it to the queue, not when we take it out. This way, the same cell can never be added to the queue twice.

The main loop stays the same. It calls `exploreCluster` for each unvisited user cell and counts the cluster if the result is `True`.


### Python Code

```python
from collections import deque
from typing import List


class DasherCoverage:

    def __init__(self):
        self.dasherAvailability = []
        self.userClusters = []
        self.rows = 0
        self.cols = 0
        # visited[r][c] becomes True once cell (r, c) is part of an explored cluster
        self.visited = []
        # the 4 allowed moves: down, up, right, left (no diagonal moves)
        self.directions = [(1, 0), (-1, 0), (0, 1), (0, -1)]

    def countFullyCoveredUserClusters(self, dasherAvailability: List[str], userClusters: List[str]) -> int:
        self.dasherAvailability = dasherAvailability
        self.userClusters = userClusters
        self.rows = len(userClusters)
        self.cols = len(userClusters[0])
        self.visited = [[False] * self.cols for _ in range(self.rows)]

        coveredCount = 0
        for row in range(self.rows):
            for col in range(self.cols):
                if self.isUserCell(row, col) and not self.visited[row][col]:
                    # a new cluster starts here: explore all of it and check its coverage
                    if self.exploreCluster(row, col):
                        coveredCount += 1
        return coveredCount

    def exploreCluster(self, startRow: int, startCol: int) -> bool:
        """
        Visits every cell of the cluster that contains (startRow, startCol) using BFS.
        Returns True if every cell of this cluster has an available Dasher.
        """
        queue = deque([(startRow, startCol)])
        self.visited[startRow][startCol] = True
        covered = True

        while queue:
            row, col = queue.popleft()

            # one missing Dasher fails the cluster, but we keep going
            # so that every cell of this cluster gets marked as visited
            if self.dasherAvailability[row][col] != '1':
                covered = False

            for moveRow, moveCol in self.directions:
                nextRow = row + moveRow
                nextCol = col + moveCol
                if self.isUserCell(nextRow, nextCol) and not self.visited[nextRow][nextCol]:
                    # mark it now, so the same cell is never added to the queue twice
                    self.visited[nextRow][nextCol] = True
                    queue.append((nextRow, nextCol))

        return covered

    def isUserCell(self, row: int, col: int) -> bool:
        """True if (row, col) is inside the grid and has users on it."""
        return 0 <= row < self.rows and 0 <= col < self.cols and self.userClusters[row][col] == '1'
```


### Complexity

- **Time:** `O(rows * cols)`. Each cell enters the queue at most once, and for each cell we check only 4 neighbours.
- **Space:** `O(rows * cols)` for the `visited` list and the queue.