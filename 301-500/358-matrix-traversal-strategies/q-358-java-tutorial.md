# Matrix Traversal Strategies in Java


#### Problem Statement
[https://codezym.com/question/358-matrix-traversal-strategies](https://codezym.com/question/358-matrix-traversal-strategies)


The key idea is to separate **which traversal we choose** from **how it visits the matrix**. The **Strategy design pattern** fits this problem and is explicitly required. Each traversal gets its own class, and a map selects the strategy using `SPIRAL`, `ROW`, or `COLUMN`. We parse the input once, then ask that strategy to return the elements in order.


## Start with a simple approach

A first attempt is to put all three algorithms inside conditional branches in `traverseMatrix`. This can return the correct values, but it does not meet the required class design.


Move each algorithm into its own class. The main method now only parses the input, selects a strategy, and calls it. The traversal time stays the same.


## Give each traversal its own strategy

- `TraversalStrategy` defines a common `traverse` method that takes a numeric matrix and returns a list.
- `RowTraversal`, `ColumnTraversal`, and `SpiralTraversal` each implement one visiting order.
- `MatrixTraversal` holds a `HashMap` from strategy codes to strategy objects. It calls them through the common interface.


Each strategy creates its result and loop variables inside `traverse`, so its object can be reused across calls.


Strategy alone is enough. State is less suitable because no internal status drives the behavior. The caller directly chooses an algorithm.


To add another traversal, create a new strategy class and register its code in the map. The existing traversal classes stay unchanged.


## Parse the matrix once

Each input row is a string such as `"1,2,3,4"`. Split it on commas and convert the parts to integers, storing the rows in an `int[][]` array.


Strategies can then read cells by row and column, without repeatedly splitting strings. The numeric matrix is a new object, so the supplied input stays unchanged.


The returned `ArrayList<Integer>` stores values in the order we visit them. The map has just three entries, so no separate factory class is needed.


## Row and column traversal

- **ROW:** loop over rows from top to bottom. Inside each row, loop over columns from left to right.
- **COLUMN:** loop over columns from left to right. Inside each column, loop over rows from top to bottom.


The only difference is which loop comes first.


For example:

```text
matrix = ["1,2,3,4", "5,6,7,8", "9,10,11,12"]

ROW    = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
COLUMN = [1, 5, 9, 2, 6, 10, 3, 7, 11, 4, 8, 12]
SPIRAL = [1, 2, 3, 4, 8, 12, 11, 10, 9, 5, 6, 7]
```


## Spiral traversal with four boundaries

Think of the unvisited cells as a rectangle. Its edges are `top`, `bottom`, `left`, and `right`. Initially, they cover the whole matrix.


For each round:

1. Read the top row from left to right, then increase `top`.
2. Read the right column from top to bottom, then decrease `right`.
3. If `top <= bottom`, read the bottom row from right to left, then decrease `bottom`.
4. If `left <= right`, read the left column from bottom to top, then increase `left`.


Repeat while `top <= bottom` and `left <= right`.


Moving each boundary immediately after visiting its side keeps the corners out of the next side's traversal. The two checks prevent revisiting a final row or column that was already consumed.


In the example above, the outer boundary gives `1, 2, 3, 4, 8, 12, 11, 10, 9, 5`. The remaining row contributes `6, 7`. Once we visit that row, `top > bottom`, so it is not visited again as a bottom row.


## Why every element appears exactly once

Row and column traversal visit every row-column pair once, in the required order.


For spiral traversal, the boundaries describe the unvisited rectangle. Each visited side is removed before the next side is processed. The rectangle shrinks until it is empty, without skipping or repeating cells. This also handles single-row and single-column matrices.


## Complexity

For `R` rows and `C` columns, parsing and traversal each take `O(R * C)` time. The total time is `O(R * C)`, which is optimal because every element must appear in the output.


The parsed matrix and returned list each use `O(R * C)` space. Spiral traversal uses `O(1)` working space apart from its result, because boundary variables replace a visited set.


## Complete Java code

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

interface TraversalStrategy {
    List<Integer> traverse(int[][] matrix);
}

class RowTraversal implements TraversalStrategy {
    public List<Integer> traverse(int[][] matrix) {
        List<Integer> result = new ArrayList<>();

        for (int row = 0; row < matrix.length; row++) {
            for (int column = 0; column < matrix[0].length; column++) {
                result.add(matrix[row][column]);
            }
        }

        return result;
    }
}

class ColumnTraversal implements TraversalStrategy {
    public List<Integer> traverse(int[][] matrix) {
        List<Integer> result = new ArrayList<>();

        for (int column = 0; column < matrix[0].length; column++) {
            for (int row = 0; row < matrix.length; row++) {
                result.add(matrix[row][column]);
            }
        }

        return result;
    }
}

class SpiralTraversal implements TraversalStrategy {
    public List<Integer> traverse(int[][] matrix) {
        List<Integer> result = new ArrayList<>();
        int top = 0;
        int bottom = matrix.length - 1;
        int left = 0;
        int right = matrix[0].length - 1;

        while (top <= bottom && left <= right) {
            // Visit the top row, then remove it from the remaining rectangle.
            for (int column = left; column <= right; column++) {
                result.add(matrix[top][column]);
            }
            top++;

            // Visit the right column, then move its boundary inward.
            for (int row = top; row <= bottom; row++) {
                result.add(matrix[row][right]);
            }
            right--;

            // A single remaining row may already have been visited.
            if (top <= bottom) {
                for (int column = right; column >= left; column--) {
                    result.add(matrix[bottom][column]);
                }
                bottom--;
            }

            // A single remaining column may already have been visited.
            if (left <= right) {
                for (int row = bottom; row >= top; row--) {
                    result.add(matrix[row][left]);
                }
                left++;
            }
        }

        return result;
    }
}

public class MatrixTraversal {
    Map<String, TraversalStrategy> strategies;

    public MatrixTraversal() {
        strategies = new HashMap<>();
        strategies.put("SPIRAL", new SpiralTraversal());
        strategies.put("ROW", new RowTraversal());
        strategies.put("COLUMN", new ColumnTraversal());
    }

    public List<Integer> traverseMatrix(List<String> matrix, String strategyCode) {
        // Parse each row once into a separate numeric matrix.
        int[][] values = new int[matrix.size()][];
        int row = 0;

        for (String rowText : matrix) {
            String[] parts = rowText.split(",");
            values[row] = new int[parts.length];

            for (int column = 0; column < parts.length; column++) {
                values[row][column] = Integer.parseInt(parts[column]);
            }
            row++;
        }

        // Only the selected strategy decides the visiting order.
        TraversalStrategy strategy = strategies.get(strategyCode);
        return strategy.traverse(values);
    }
}
```
