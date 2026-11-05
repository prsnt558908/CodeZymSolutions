# Count Distinct Islands in a Grid in Java

#### Problem Statement

[https://codezym.com/question/190-count-distinct-islands-grid](https://codezym.com/question/190-count-distinct-islands-grid)

The key idea is to describe an island by its shape instead of its location. Use breadth-first search, or BFS, to find its land cells. Record each cell's position relative to the island's first cell, then store that description in a set. Islands with the same shape produce the same description, so the set size gives the answer.

## Start with a simple solution

Find each island and build a description of its shape. Keep the descriptions in a list, adding a new one only when it is not already present.

```java
List<String> distinctShapes = new ArrayList<>();

// Run this for each island found during the grid scan.
String shape = collectShape(gridRows, visited, row, col);
if (!distinctShapes.contains(shape)) {
    distinctShapes.add(shape);
}
```

This works, but `contains` may compare the new shape with every stored shape. With `I` islands and descriptions of length at most `K`, these comparisons can take `O(I² × K)` time in the worst case.

We can improve this by using a hash set. First, we need a reliable way to describe a shape.

## Describe cells relative to a starting cell

Scan the grid row by row. When an unvisited land cell appears, use it as the island's starting cell, `(startRow, startCol)`.

For every cell `(row, col)` reached by BFS, record:

```text
(row - startRow, col - startCol)
```

For example, these two islands have identical relative positions:

- Cells `(0, 0), (0, 1), (1, 0)` become `(0, 0), (0, 1), (1, 0)`.
- Cells `(2, 3), (2, 4), (3, 3)` also become `(0, 0), (0, 1), (1, 0)`.

Their positions differ, but their shape descriptions match. We never rotate or reflect the positions. Shapes that match only after such a change therefore keep different descriptions.

Column offsets can be negative if an island extends left below its starting cell. Keep those negative values in the description.

## Count unique descriptions with a set

Always explore neighbors in the same order: up, right, down, left. Because translated copies start at corresponding cells, BFS records their relative positions in the same order. No sorting is needed.

Join the pairs into a string such as `0,0|0,1|1,0|`. The comma and vertical bar separate the values and cells clearly.

The solution uses three simple data structures:

- `visited` prevents a land cell from being added more than once.
- An `ArrayList` holds the BFS queue. A `head` index reads its next cell without removing from the front.
- A `HashSet<String>` stores each distinct shape description once.

Mark a cell visited when adding it to the queue. All working data is created inside the method call, so the same object can safely process another grid. The input is left unchanged.

## Why this works

BFS follows only shared sides, so it visits exactly one island at a time. The outer scan eventually finds every island.

Shifting an island also shifts its starting cell by the same amount. Subtracting that starting cell removes the shift. A fixed BFS order therefore gives identical descriptions for identical shapes.

If two descriptions match, their relative land cells match exactly. One island can be shifted to cover the other. The set therefore keeps exactly one entry per distinct shape.

## Complexity

For `R` rows and `C` columns, expected time is `O(R × C)`, including building and hashing the descriptions. Each land cell is queued once and has four neighbors checked.

Extra space is `O(R × C)` for visited cells, the queue, and all stored descriptions. Iterative BFS also avoids a deep recursive call stack.

## Java solution

```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CountDistinctIslands {

    public CountDistinctIslands() {
    }

    public int countDistinctIslands(List<String> grid) {
        // An array gives direct row access for any List implementation.
        String[] gridRows = grid.toArray(new String[0]);
        int rows = gridRows.length;
        int cols = gridRows[0].length();
        boolean[][] visited = new boolean[rows][cols];
        Set<String> distinctShapes = new HashSet<>();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (gridRows[row].charAt(col) == '1' && !visited[row][col]) {
                    distinctShapes.add(collectShape(gridRows, visited, row, col));
                }
            }
        }

        return distinctShapes.size();
    }

    /** Returns the island's description relative to its starting cell. */
    public String collectShape(String[] grid, boolean[][] visited,
                               int startRow, int startCol) {
        int rows = grid.length;
        int cols = grid[0].length();
        int[][] directions = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};
        List<int[]> queue = new ArrayList<>();
        StringBuilder shape = new StringBuilder();

        queue.add(new int[]{startRow, startCol});
        visited[startRow][startCol] = true;
        int head = 0;

        while (head < queue.size()) {
            int[] cell = queue.get(head++);
            int row = cell[0];
            int col = cell[1];

            // Ignore the absolute position, but preserve the orientation.
            shape.append(row - startRow).append(',')
                 .append(col - startCol).append('|');

            for (int[] direction : directions) {
                int nextRow = row + direction[0];
                int nextCol = col + direction[1];

                if (nextRow < 0 || nextRow >= rows
                        || nextCol < 0 || nextCol >= cols) {
                    continue;
                }

                if (grid[nextRow].charAt(nextCol) == '1'
                        && !visited[nextRow][nextCol]) {
                    // Mark on insertion so no cell enters the queue twice.
                    visited[nextRow][nextCol] = true;
                    queue.add(new int[]{nextRow, nextCol});
                }
            }
        }

        return shape.toString();
    }
}
```
