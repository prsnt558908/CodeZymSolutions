# Minimum Road Repairs Between Cities in Java

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

We keep a grid called `repairs`, where `repairs[r][c]` is the fewest repairs found so far to reach section `(r, c)`. It starts as `Integer.MAX_VALUE` everywhere, except `0` at the start.

We also keep a `PriorityQueue` (min-heap) of sections ordered by repair count. We always take out the cheapest section and check its 4 neighbors. If a neighbor can now be reached with fewer repairs, we save the new count and add it to the heap.

```java
// Each entry is {repairsSoFar, row, col}. The smallest repairsSoFar comes out first.
PriorityQueue<int[]> heap = new PriorityQueue<>((a, b) -> a[0] - b[0]);
repairs[0][0] = 0;
heap.add(new int[]{0, 0, 0});

while (!heap.isEmpty()) {
    int[] top = heap.poll();
    int repairsSoFar = top[0];
    int row = top[1];
    int col = top[2];

    for (int[] direction : directions) {
        int nextRow = row + direction[0];
        int nextCol = col + direction[1];
        if (!isInside(nextRow, nextCol, rows, columns)) {
            continue;
        }

        int newRepairs = repairsSoFar + cost[nextRow][nextCol];
        if (newRepairs < repairs[nextRow][nextCol]) {
            repairs[nextRow][nextCol] = newRepairs;
            heap.add(new int[]{newRepairs, nextRow, nextCol});
        }
    }
}
return repairs[rows - 1][columns - 1];
```

`cost`, `repairs`, `directions` and `isInside()` are the same as in the final code below.

This is correct and runs in **O(N log N)** time, where `N = rows * columns`.

**The problem:** every heap operation costs `O(log N)`. A heap can sort any costs, but our costs are only 0 or 1. We are paying for sorting power that we don't need.

---

## Approach 2: 0-1 BFS with a Deque (Optimal)

### Key Observation

Say we are on a section that needed `k` repairs. Stepping from it to a neighbor gives:

- `k` repairs, if the neighbor is clear
- `k + 1` repairs, if the neighbor is damaged

So the sections waiting to be handled only ever have two repair counts: `k` and `k + 1`. Keeping just two values in order does not need a heap. A **deque** is enough:

- **Clear neighbor:** add it to the **front**. It is as cheap as the current section, so it goes next.
- **Damaged neighbor:** add it to the **back**. It costs one more repair, so it can wait.

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
- **`Deque<int[]>` (an `ArrayDeque`):** holds `{row, col}` pairs that are waiting to be handled. It can add at both ends in O(1), which is exactly what free and paid steps need.

### Code

```java
import java.util.*;

public class CityRoadPlanner {

    // The four possible moves: down, up, right, left
    int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public CityRoadPlanner() {
    }

    public int findMinimumRepairs(List<String> roadMap) {
        int[][] cost = buildCostGrid(roadMap);
        int rows = cost.length;
        int columns = cost[0].length;

        // repairs[r][c] = fewest repairs found so far to reach section (r, c)
        int[][] repairs = new int[rows][columns];
        for (int[] repairRow : repairs) {
            Arrays.fill(repairRow, Integer.MAX_VALUE);
        }

        // Each entry is {row, col}. Cheaper sections stay at the front.
        Deque<int[]> toVisit = new ArrayDeque<>();
        repairs[0][0] = 0; // the start section is always clear
        toVisit.addFirst(new int[]{0, 0});

        while (!toVisit.isEmpty()) {
            int[] current = toVisit.pollFirst();
            int row = current[0];
            int col = current[1];

            for (int[] direction : directions) {
                int nextRow = row + direction[0];
                int nextCol = col + direction[1];
                if (!isInside(nextRow, nextCol, rows, columns)) {
                    continue;
                }

                int newRepairs = repairs[row][col] + cost[nextRow][nextCol];
                if (newRepairs < repairs[nextRow][nextCol]) {
                    repairs[nextRow][nextCol] = newRepairs;
                    if (cost[nextRow][nextCol] == 0) {
                        // Clear section: same repair count, so handle it next
                        toVisit.addFirst(new int[]{nextRow, nextCol});
                    } else {
                        // Damaged section: one more repair, so it can wait
                        toVisit.addLast(new int[]{nextRow, nextCol});
                    }
                }
            }
        }

        return repairs[rows - 1][columns - 1];
    }

    // Converts rows like "C,D,C" into numbers: 0 for clear, 1 for damaged.
    // A new grid is built, so the supplied road map is never modified.
    int[][] buildCostGrid(List<String> roadMap) {
        int[][] cost = new int[roadMap.size()][];
        for (int r = 0; r < roadMap.size(); r++) {
            String[] sections = roadMap.get(r).split(",");
            cost[r] = new int[sections.length];
            for (int c = 0; c < sections.length; c++) {
                cost[r][c] = sections[c].trim().equals("D") ? 1 : 0;
            }
        }
        return cost;
    }

    boolean isInside(int row, int col, int rows, int columns) {
        return row >= 0 && row < rows && col >= 0 && col < columns;
    }
}
```

### Complexity

- **Time:** `O(rows * columns)`. Each section enters the deque at most once, and each deque operation is O(1).
- **Space:** `O(rows * columns)` for the `cost` grid, the `repairs` grid and the deque.

Why at most once? The deque hands out sections from cheapest to costliest, and stepping onto a section costs the same from every side. So the first count we find for a section is already its best.

---

## Comparing the Approaches

| Approach | Main data structure | Time | Correct? |
|---|---|---|---|
| Plain BFS | Queue | O(N) | No, it counts moves, not repairs |
| Dijkstra | PriorityQueue | O(N log N) | Yes |
| 0-1 BFS | Deque | O(N) | Yes |

Here `N = rows * columns`.