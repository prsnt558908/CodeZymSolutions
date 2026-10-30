# Minimum Time to Reach All Targets in Java

#### Problem Statement

[https://codezym.com/question/406-min-time-to-reach-all-targets](https://codezym.com/question/406-min-time-to-reach-all-targets)

Think of the signals as waves spreading from all sources together. Put every source into one queue and use breadth-first search, or BFS, to visit cells in order of arrival time. This is called **multi-source BFS**. The first signal to reach a target is its fastest possible signal, so the answer is the arrival time of the last target reached.


## Start simple: search from each target

For each target, run a separate BFS until it finds a source. Movement works in both directions, so this gives the shortest route from a source to that target.

Return the largest of these distances. If any search cannot find a source, return `-1`.

The following sketch assumes that `targets` contains target coordinates and `bfsToNearestSource` performs one ordinary BFS.

```java
int answer = 0;
for (int[] target : targets) {
    int time = bfsToNearestSource(grid, target[0], target[1]);
    if (time == -1) {
        return -1;
    }
    answer = Math.max(answer, time);
}
return answer;
```

With `K` targets and an `R × C` grid, this can take `O(K × R × C)` time. Many searches repeat the same work. We can avoid that by spreading all signals in a single search.


## Better approach: one BFS from every source

All sources start at time `0`. Add them all to the queue **before** processing any cell. BFS then discovers cells at time `1`, followed by time `2`, and so on.

We need three things:

- A queue of cell coordinates to process the earliest arrivals first.
- A `distance` grid, initially `-1`, to store arrival times and identify unvisited cells.
- A counter for targets that have not received a signal.

For every cell removed from the queue, check its four neighbors. Skip neighbors outside the grid, blockers, and cells that already have a distance.

For each new neighbor, set its distance to the current distance plus `1`. Set this **before adding it to the queue**, so two signals cannot add the same cell twice.

When the neighbor is a target, decrease the counter. If it becomes zero, return that neighbor's distance. Otherwise, add the neighbor to the queue, including target cells, because signals can pass through them.

If the queue becomes empty first, at least one target is unreachable. Return `-1`. The constraints guarantee at least one source and one target.

`ArrayDeque` provides efficient queue operations. Each `int[]` stores only a row and column, so we do not need a separate cell class.

The code copies the row references into a `String[]`. This keeps row access fast even if the supplied `List` is a `LinkedList`. It leaves the input unchanged.


## Walk through an example

For `grid = ["S.BT", "..B.", "T..S"]`, both sources enter the queue at time `0`. Coordinates below use zero-based indices.

The source at `(2, 3)` reaches target `(0, 3)` in `2` steps. The source at `(0, 0)` reaches target `(2, 0)` in `2` steps.

The result is `2`, not `4`, because both signals spread together.

Targets do not stop a signal. For `grid = ["STTT"]`, the result is `3` because the signal passes through the first two targets to reach the third.


## Why this works

BFS processes cells in increasing distance from the sources. When it first discovers a cell at distance `d`, a shorter route would have reached that cell from an earlier queue entry. Therefore, its first distance is its minimum arrival time from any source.

Each target is counted exactly once. When the counter reaches zero, the current target has the largest arrival time, so its distance is exactly when all targets have received a signal.

If a target is still unvisited after the queue empties, no allowed path connects it to a source.


## Complexity

Each cell enters the queue at most once, and we check at most four neighbors per cell.

Time is `O(R × C)`. Extra space is `O(R × C)` for the distance grid and queue. This is optimal in the worst case because the input itself can require examining every cell.


## Follow-up idea: remove one blocker

A simple exact approach is to try every blocker. For each candidate, change only that cell to `'.'` in a copy of the grid and run the same multi-source BFS. Compare candidates using a fresh grid each time.

Among candidates that reach every target, keep the one with the smallest completion time. Visit blockers in row order, then column order, and update the answer only for a strictly smaller time. This automatically preserves the required tie-breaker. If none works, completing all targets is impossible. If there are no blockers, removing exactly one is not possible.

With `B` blockers, this takes `O(B × R × C)` time and `O(R × C)` extra space. It is a straightforward follow-up solution, but can be too slow for large grids with many blockers.

The same blocker must be removed for every target. Letting each path independently break a different blocker would solve a different problem. The required method below handles the original grid without removing blockers.


## Complete Java code

Save this as `MinimumSignalTime.java`.

```java
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

public class MinimumSignalTime {

    public MinimumSignalTime() {
    }

    public int minimumSignalTime(List<String> grid) {
        // Copy the row references for constant-time access with any List type.
        String[] city = grid.toArray(new String[0]);
        int rows = city.length;
        int columns = city[0].length();
        int[][] distance = new int[rows][columns];
        Queue<int[]> queue = new ArrayDeque<>();
        int remainingTargets = 0;

        for (int row = 0; row < rows; row++) {
            Arrays.fill(distance[row], -1);

            for (int column = 0; column < columns; column++) {
                char cell = city[row].charAt(column);

                if (cell == 'S') {
                    // Every source starts at time zero.
                    distance[row][column] = 0;
                    queue.offer(new int[] {row, column});
                } else if (cell == 'T') {
                    remainingTargets++;
                }
            }
        }

        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int row = current[0];
            int column = current[1];

            for (int[] direction : directions) {
                int nextRow = row + direction[0];
                int nextColumn = column + direction[1];

                if (nextRow < 0 || nextRow >= rows
                        || nextColumn < 0 || nextColumn >= columns) {
                    continue;
                }

                if (city[nextRow].charAt(nextColumn) == 'B'
                        || distance[nextRow][nextColumn] != -1) {
                    continue;
                }

                // Mark on discovery so two signals cannot enqueue it twice.
                distance[nextRow][nextColumn] = distance[row][column] + 1;

                if (city[nextRow].charAt(nextColumn) == 'T') {
                    remainingTargets--;
                    if (remainingTargets == 0) {
                        return distance[nextRow][nextColumn];
                    }
                }

                // Reached targets also let signals pass through.
                queue.offer(new int[] {nextRow, nextColumn});
            }
        }

        // At least one target remains unreachable.
        return -1;
    }
}
```
