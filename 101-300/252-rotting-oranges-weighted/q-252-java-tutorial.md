# Rotting Oranges With Different Connection Times in Java

#### Problem Statement
[https://codezym.com/question/252-rotting-oranges-weighted](https://codezym.com/question/252-rotting-oranges-weighted)

## Core Idea

This is a shortest path problem hidden inside a grid.

Think of every orange as a point, and every connection as a road between two points with its own travel time. Rotting starts from all rotten oranges at time 0, so every rotten orange is a starting point.

A fresh orange rots at the earliest moment rotting can reach it. That is just its shortest travel time from any rotten orange. The answer is the largest of these times, because we have to wait for the last orange.

To find all these shortest times together, we use **Dijkstra's algorithm** with a **min heap**, started from every rotten orange at once. And since a connection only joins two side-by-side cells, we store connections in a small table of 4 directions per cell, instead of building a full graph.

## Understanding the Problem

- Rotting moves only through the given connections, not to every neighbor.
- A connection that touches an empty cell can never be used.
- If an orange can be reached in more than one way, the earliest time wins.
- The answer is the time when the last fresh orange rots. If some fresh orange is never reached, the answer is `-1`.
- If there are no fresh oranges at all, the answer is `0`.

## Approach 1: BFS That Keeps Updating (Simple but Slow)

### Why normal BFS fails

In the classic Rotting Oranges problem, every step takes exactly 1 minute. BFS works there because the first time BFS reaches an orange is also the fastest time.

Here each connection has its own time, so that is no longer true.

In Example 2, orange `[1,1]` is 2 steps away from the rotten orange `[0,0]` on both sides:

- through `[0,1]`: `5 + 10 = 15` minutes
- through `[1,0]`: `1 + 3 = 4` minutes

Both paths have the same number of steps, so BFS cannot tell which one is faster. It keeps whichever path reaches `[1,1]` first, and that may be the slow one.

In general, BFS only counts steps. But a path with more steps can still take less time.

### The fix: allow updates

Do not lock an orange's time on the first visit. Whenever we find a faster time for an orange, update it and put it back in the queue, so its neighbors also get a chance to improve.

```java
// all rotten oranges start in the queue with rotTime = 0, every other cell has rotTime = infinity
while (!queue.isEmpty()) {
    int[] cell = queue.poll();
    for (int[] next : neighbors(cell)) {            // next = {row, col, travelTime}
        long newTime = rotTime[cell[0]][cell[1]] + next[2];
        if (newTime < rotTime[next[0]][next[1]]) {
            rotTime[next[0]][next[1]] = newTime;    // found a faster way
            queue.add(new int[]{next[0], next[1]}); // its neighbors may improve too
        }
    }
}
```

Here `neighbors(cell)` gives every orange connected to `cell`, along with the connection time. When the queue becomes empty, every orange has its correct time.

### Problem with this approach

The same orange can be updated again and again. A slow path may reach it first, then a faster one, then an even faster one.

Every update re-checks all its neighbors, and their updates spread even further. On a `1000 x 1000` grid this repeated work can become extremely slow.

## Approach 2: Always Rot the Earliest Orange First (Optimal)

The fix is to process oranges in the right order. Instead of a normal queue, we use a **min heap**, which always gives us the orange with the smallest rotting time.

### Why this works

When an orange comes out of the heap with time `T`, every orange still waiting in the heap has a time of `T` or more.

Any faster path to our orange would have to go through one of those waiting oranges. But that orange already needs `T` or more, and the connection adds at least 1 more minute. So no path can beat `T`.

The time is final, and the orange never needs an update again.

This is **Dijkstra's shortest path algorithm**. The only twist is that we start it from all rotten oranges at the same time.

### Storing the connections

Every connection joins two side-by-side cells. So a cell can have at most 4 connections: up, down, left and right.

That means we don't need a full graph with lists. A simple array is enough:

`connectionTime[d][r][c]` = minutes needed to spread from cell `(r, c)` to its neighbor in direction `d`.

- `0` means no connection. This is safe because a real time is always at least 1.
- Each connection is saved in both directions, since rotting can go both ways.
- If the same pair is given more than once, we keep the smaller time.
- A connection touching an empty cell is skipped. So the empty cell rule is handled once, right here.

Why this array? Its size depends only on the grid, not on the number of connections. And finding the neighbors of a cell is the same 4-direction loop as in the classic problem.

The helper `getDirection()` finds which direction cell 2 is from cell 1, by trying all 4 directions.

### Other data structures

- `rotTime[r][c]`: the earliest time found so far for each cell. It starts at infinity, and at 0 for rotten oranges. It is a `long` because a sum like `time + travelTime` can go above the `int` range while we explore.
- `minHeap`: a `PriorityQueue` of `{time, row, col}`, smallest time first. This is what makes the algorithm fast.
- `freshCount`: fresh oranges that have not rotted yet. When it reaches 0, we can stop early.

### Algorithm

1. Read the grid and fill `connectionTime` from the connections.
2. Count fresh oranges. Push every rotten orange into the heap with time 0.
3. Pop the orange with the smallest time. An orange can be in the heap more than once, because we push it again every time we find a faster time. If the popped time is bigger than its `rotTime`, it is an old entry, so skip it.
4. If it is a fresh orange, its time is now final. Decrease `freshCount` and save this time as `lastRotTime`.
5. For each direction that has a connection, compute `newTime = time + travelTime`. If it beats the neighbor's `rotTime`, update it and push the neighbor.
6. Repeat until the heap is empty or `freshCount` becomes 0.

Oranges leave the heap in increasing order of time. So `lastRotTime` belongs to the orange that rots last, and that is the answer.

If `freshCount` is still above 0, some orange was never reached, so we return `-1`.

### Dry run on Example 2

Grid:

```
2 1
1 1
```

Connections: `[0,0]-[0,1]: 5`, `[0,0]-[1,0]: 1`, `[1,0]-[1,1]: 3`, `[0,1]-[1,1]: 10`

| Popped | Time | What happens |
|---|---|---|
| `[0,0]` | 0 | `[1,0]` gets 1, `[0,1]` gets 5 |
| `[1,0]` | 1 | `[1,1]` gets `1 + 3 = 4` |
| `[1,1]` | 4 | `[0,1]` could get `4 + 10 = 14`, but 5 is already better |
| `[0,1]` | 5 | the last fresh orange rots, answer is **5** |

The heap picked `[1,0]` (time 1) before `[0,1]` (time 5). So `[1,1]` got its correct time 4 on the first try, with no extra updates.

### Complexity

Let `N = rows x cols` and `M` = number of connections.

- **Time:** `O(M + N log N)`. Reading the connections is `O(M)`. Each orange enters the heap at most 4 times (once per connection), so the heap work is `O(N log N)`.
- **Space:** `O(N)`. Every array is the size of the grid. Even a million connections fit into the fixed `4 x rows x cols` table.

### Java Code

```java
import java.util.*;

public class RottingOrangesWithDifferentConnectionTimes {

    // Row and column change for each direction: up, down, left, right
    int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    public RottingOrangesWithDifferentConnectionTimes() {
    }

    public int minTimeToRotAllOranges(List<String> grid, List<String> connections) {
        int rows = grid.size();
        int[][] cells = new int[rows][];
        for (int r = 0; r < rows; r++) {
            cells[r] = parseNumbers(grid.get(r));
        }
        int cols = cells[0].length;

        // connectionTime[d][r][c] = minutes needed to spread from (r, c) to its neighbor in direction d
        // 0 means there is no usable connection in that direction
        int[][][] connectionTime = new int[4][rows][cols];
        for (String connection : connections) {
            int[] parts = parseNumbers(connection);
            int r1 = parts[0];
            int c1 = parts[1];
            int r2 = parts[2];
            int c2 = parts[3];
            int time = parts[4];

            // rotting can not pass through an empty cell, so this connection is useless
            if (cells[r1][c1] == 0 || cells[r2][c2] == 0) {
                continue;
            }

            int forward = getDirection(r1, c1, r2, c2);
            int backward = getDirection(r2, c2, r1, c1);
            if (forward == -1) {
                continue; // the two cells are not neighbors
            }

            // the same pair can appear more than once, keep the fastest connection
            int oldTime = connectionTime[forward][r1][c1];
            if (oldTime == 0 || time < oldTime) {
                connectionTime[forward][r1][c1] = time;
                connectionTime[backward][r2][c2] = time;
            }
        }

        // rotTime[r][c] = earliest time found so far at which orange (r, c) becomes rotten
        long[][] rotTime = new long[rows][cols];

        // entries are {time, row, col}, the orange with the smallest time comes out first
        PriorityQueue<long[]> minHeap = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));

        int freshCount = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                rotTime[r][c] = Long.MAX_VALUE;
                if (cells[r][c] == 1) {
                    freshCount++;
                } else if (cells[r][c] == 2) {
                    rotTime[r][c] = 0;
                    minHeap.add(new long[]{0, r, c});
                }
            }
        }

        long lastRotTime = 0;
        while (!minHeap.isEmpty() && freshCount > 0) {
            long[] top = minHeap.poll();
            long time = top[0];
            int r = (int) top[1];
            int c = (int) top[2];

            // a faster time was already found for this orange, skip this old entry
            if (time > rotTime[r][c]) {
                continue;
            }

            // this time is final, oranges come out of the heap in increasing order of time
            if (cells[r][c] == 1) {
                freshCount--;
                lastRotTime = time;
            }

            // try to rot the connected neighbors
            for (int d = 0; d < 4; d++) {
                int travelTime = connectionTime[d][r][c];
                if (travelTime == 0) {
                    continue; // no connection in this direction
                }
                int nr = r + directions[d][0];
                int nc = c + directions[d][1];
                long newTime = time + travelTime;
                if (newTime < rotTime[nr][nc]) {
                    rotTime[nr][nc] = newTime;
                    minHeap.add(new long[]{newTime, nr, nc});
                }
            }
        }

        return freshCount == 0 ? (int) lastRotTime : -1;
    }

    // Returns the direction index to move from (fromRow, fromCol) to (toRow, toCol).
    // Returns -1 if the two cells are not neighbors.
    int getDirection(int fromRow, int fromCol, int toRow, int toCol) {
        for (int d = 0; d < 4; d++) {
            if (fromRow + directions[d][0] == toRow && fromCol + directions[d][1] == toCol) {
                return d;
            }
        }
        return -1;
    }

    // Converts a text like "2,1,0" into the numbers [2, 1, 0]
    int[] parseNumbers(String text) {
        String[] parts = text.split(",");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = Integer.parseInt(parts[i].trim());
        }
        return numbers;
    }
}
```