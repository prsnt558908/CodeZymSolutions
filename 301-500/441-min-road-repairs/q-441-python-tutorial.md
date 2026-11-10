# Minimum Road Repairs Between Cities in Python

#### Problem Statement
[https://codezym.com/question/441-min-road-repairs](https://codezym.com/question/441-min-road-repairs)

Think of the road map as a grid where every step has a price. Stepping onto a clear section is free, and stepping onto a damaged section costs 1 repair. So we need the **cheapest path** from Riverton (top-left) to Lakeside (bottom-right).

Since every step costs either 0 or 1, we can use a neat trick called **0-1 BFS**. It works like normal BFS, but it uses a double-ended queue (deque).

Sections reached by a free step go to the front of the deque, and sections reached by a paid step go to the back. This way the cheapest sections are always handled first, and the whole grid is solved in linear time.

We will build up to it step by step. First we see why plain BFS fails, then we fix it with Dijkstra's algorithm, and finally we swap Dijkstra's heap for a deque.

---

## Reading the Road Map

Each row comes as a string like `"C,D,C,C,D"`. We split it by commas and turn every section into a number:

- `C` (clear) becomes `0`
- `D` (damaged) becomes `1`

This number is the cost of **stepping onto** that section. We keep these numbers in a new grid called `cost`, so the supplied road map is never changed.

A repaired section stays clear, but we don't need to track that. A cheapest path never needs to visit the same section twice, so no section is ever paid for twice.

---

## First Try: Plain BFS (Why It Fails)

BFS is the usual tool for shortest paths in a grid. But BFS finds the path with the **fewest moves**, and we want the path with the **fewest repairs**. These are not the same thing.

```
C C C
D D C
C C C
C D D
C C C
```

Every shortest route here takes 6 moves and needs at least 1 repair. The winding route along the clear roads takes 10 moves but needs 0 repairs.

BFS would pick a 6-move route, but the correct answer is 0. We need an algorithm that compares repairs, not moves.

---

## Approach 1: Dijkstra's Algorithm

Dijkstra's algorithm finds the cheapest path when steps can have different costs.

We keep a grid called `repairs`, where `repairs[r][c]` is the fewest repairs found so far to reach section `(r, c)`. It starts as `float("inf")` everywhere, except `0` at the start.

We also keep a min-heap of sections using `heapq`. Each entry is a tuple `(repairs_so_far, row, col)`. Python compares tuples by their first item, so the section with the fewest repairs always comes out first.

We take out the cheapest section and check its 4 neighbors. If a neighbor can now be reached with fewer repairs, we save the new count and push it onto the heap.

```python
# Each entry is (repairs_so_far, row, col). Smallest comes out first.
heap = [(0, 0, 0)]
repairs[0][0] = 0

while heap:
    repairs_so_far, row, col = heapq.heappop(heap)

    for dr, dc in self.directions:
        next_row = row + dr
        next_col = col + dc
        if not self.isInside(next_row, next_col, rows, columns):
            continue

        new_repairs = repairs_so_far + cost[next_row][next_col]
        if new_repairs < repairs[next_row][next_col]:
            repairs[next_row][next_col] = new_repairs
            heapq.heappush(heap, (new_repairs, next_row, next_col))

return repairs[rows - 1][columns - 1]
```

`cost`, `repairs`, `self.directions` and `isInside()` are the same as in the final code below. You also need `import heapq` at the top.

This is correct and runs in **O(N log N)** time, where `N = rows * columns`.

**The problem:** every heap operation costs `O(log N)`. A heap can sort any costs, but our costs are only 0 or 1. We are paying for sorting power that we don't need.

---

## Approach 2: 0-1 BFS with a Deque (Optimal)

### Key Observation

Say we are on a section that needed `k` repairs. Stepping from it to a neighbor gives:

- `k` repairs, if the neighbor is clear
- `k + 1` repairs, if the neighbor is damaged

So the sections waiting to be handled only ever have two repair counts: `k` and `k + 1`. Keeping just two values in order does not need a heap. A **deque** is enough:

- **Clear neighbor:** add it to the **front** with `appendleft()`. It is as cheap as the current section, so it goes next.
- **Damaged neighbor:** add it to the **back** with `append()`. It costs one more repair, so it can wait.

Now the front of the deque always holds a cheapest section, just like the top of the heap. But adding to or removing from a deque takes only **O(1)**.

### Picture It as Layers

0-1 BFS spreads out in layers. First it handles every section that needs 0 repairs, then every section that needs 1 repair, then 2, and so on.

Clear roads let a layer keep spreading. A damaged section starts the next layer.

Here are the fewest repairs needed to reach each section in Example 1:

```
Road map          Repairs needed
C D C C D         0 1 1 1 2
D D C D C         1 2 1 2 2
C C D D C         1 1 2 3 2
D C C D C         2 1 1 2 2
```

Lakeside, at the bottom-right, needs **2** repairs. That is the answer.

### Dry Run

Let's trace Example 3: `["C,D", "D,C"]`. Neighbors are checked in the order down, up, right, left.

| Step | Take from front | Its repairs | What happens | Deque after |
|---|---|---|---|---|
| 1 | (0,0) | 0 | (1,0) and (0,1) are damaged. Both need 1 repair, so add them to the back | (1,0), (0,1) |
| 2 | (1,0) | 1 | (1,1) is clear and needs 1 repair, so add it to the **front** | (1,1), (0,1) |
| 3 | (1,1) | 1 | No neighbor gets cheaper | (0,1) |
| 4 | (0,1) | 1 | No neighbor gets cheaper | empty |

Notice how (1,1) jumps ahead of (0,1) in step 2. When the deque is empty, `repairs[1][1] = 1`, so the answer is **1**.

### Important Data Structures

- **`cost` grid:** each section's cost is checked many times. Converting the strings to numbers once keeps every check simple and fast.
- **`repairs` grid:** remembers the best repair count for each section, so a worse path is never accepted.
- **`deque` (from `collections`):** holds `(row, col)` pairs that are waiting to be handled. It adds and removes at both ends in O(1). A plain list would be slow here, because inserting at the front of a list shifts every other item.

### Code

```python
from collections import deque
from typing import List


class CityRoadPlanner:

    def __init__(self):
        # The four possible moves: down, up, right, left
        self.directions = [(1, 0), (-1, 0), (0, 1), (0, -1)]

    def findMinimumRepairs(self, roadMap: List[str]) -> int:
        cost = self.buildCostGrid(roadMap)
        rows = len(cost)
        columns = len(cost[0])

        # repairs[r][c] = fewest repairs found so far to reach section (r, c)
        repairs = [[float("inf")] * columns for _ in range(rows)]

        # Each entry is (row, col). Cheaper sections stay at the front.
        to_visit = deque()
        repairs[0][0] = 0  # the start section is always clear
        to_visit.appendleft((0, 0))

        while to_visit:
            row, col = to_visit.popleft()

            for dr, dc in self.directions:
                next_row = row + dr
                next_col = col + dc
                if not self.isInside(next_row, next_col, rows, columns):
                    continue

                new_repairs = repairs[row][col] + cost[next_row][next_col]
                if new_repairs < repairs[next_row][next_col]:
                    repairs[next_row][next_col] = new_repairs
                    if cost[next_row][next_col] == 0:
                        # Clear section: same repair count, so handle it next
                        to_visit.appendleft((next_row, next_col))
                    else:
                        # Damaged section: one more repair, so it can wait
                        to_visit.append((next_row, next_col))

        return repairs[rows - 1][columns - 1]

    def buildCostGrid(self, roadMap: List[str]) -> List[List[int]]:
        """Converts rows like "C,D,C" into numbers: 0 for clear, 1 for damaged.
        A new grid is built, so the supplied road map is never modified."""
        cost = []
        for line in roadMap:
            row = []
            for section in line.split(","):
                row.append(1 if section.strip() == "D" else 0)
            cost.append(row)
        return cost

    def isInside(self, row: int, col: int, rows: int, columns: int) -> bool:
        return 0 <= row < rows and 0 <= col < columns
```

### Complexity

- **Time:** `O(rows * columns)`. Each section enters the deque at most once, and each deque operation is O(1).
- **Space:** `O(rows * columns)` for the `cost` grid, the `repairs` grid and the deque.

Why at most once? The deque hands out sections from cheapest to costliest, and stepping onto a section costs the same from every side. So the first count we find for a section is already its best.

---

## Comparing the Approaches

| Approach | Main data structure | Time | Correct? |
|---|---|---|---|
| Plain BFS | deque used as a queue | O(N) | No, it counts moves, not repairs |
| Dijkstra | heapq | O(N log N) | Yes |
| 0-1 BFS | deque | O(N) | Yes |

Here `N = rows * columns`.