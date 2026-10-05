# Matrix Traversal Strategies in Python


#### Problem Statement
[https://codezym.com/question/358-matrix-traversal-strategies](https://codezym.com/question/358-matrix-traversal-strategies)


The key idea is to separate **which traversal we choose** from **how it visits the matrix**. The **Strategy design pattern** fits this problem and is explicitly required. Each traversal gets its own class, and a map selects the strategy using `SPIRAL`, `ROW`, or `COLUMN`. We parse the input once, then ask that strategy to return the elements in order.


## Start with a simple approach

A first attempt is to put all three algorithms inside conditional branches in `traverseMatrix`. This can return the correct values, but it does not meet the required class design.


Move each algorithm into its own class. The main method now only parses the input, selects a strategy, and calls it. The traversal time stays the same.


## Give each traversal its own strategy

- `TraversalStrategy` defines a common `traverse` method that takes a numeric matrix and returns a list.
- `RowTraversal`, `ColumnTraversal`, and `SpiralTraversal` each implement one visiting order.
- `MatrixTraversal` holds a dictionary from strategy codes to strategy objects. It calls them through the common interface.


Each strategy creates its result and loop variables inside `traverse`, so its object can be reused across calls.


Strategy alone is enough. State is less suitable because no internal status drives the behavior. The caller directly chooses an algorithm.


To add another traversal, create a new strategy class and register its code in the map. The existing traversal classes stay unchanged.


## Parse the matrix once

Each input row is a string such as `"1,2,3,4"`. Split it on commas and convert the parts to integers, storing the rows in a list of integer lists.


Strategies can then read cells by row and column, without repeatedly splitting strings. The numeric matrix is a new object, so the supplied input stays unchanged.


The returned list stores values in the order we visit them. `MatrixTraversal` is a dataclass with a no-argument constructor. Its `__post_init__` method fills the strategy dictionary. The three stateless strategies use ordinary classes.


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


## Complete Python code

```python
from abc import ABC, abstractmethod
from dataclasses import dataclass, field


class TraversalStrategy(ABC):
    @abstractmethod
    def traverse(self, matrix: list[list[int]]) -> list[int]:
        """Return every element in the strategy's visiting order."""
        pass


class RowTraversal(TraversalStrategy):
    def traverse(self, matrix: list[list[int]]) -> list[int]:
        result = []

        for row in range(len(matrix)):
            for column in range(len(matrix[0])):
                result.append(matrix[row][column])

        return result


class ColumnTraversal(TraversalStrategy):
    def traverse(self, matrix: list[list[int]]) -> list[int]:
        result = []

        for column in range(len(matrix[0])):
            for row in range(len(matrix)):
                result.append(matrix[row][column])

        return result


class SpiralTraversal(TraversalStrategy):
    def traverse(self, matrix: list[list[int]]) -> list[int]:
        result = []
        top = 0
        bottom = len(matrix) - 1
        left = 0
        right = len(matrix[0]) - 1

        while top <= bottom and left <= right:
            # Visit the top row, then remove it from the remaining rectangle.
            for column in range(left, right + 1):
                result.append(matrix[top][column])
            top += 1

            # Visit the right column, then move its boundary inward.
            for row in range(top, bottom + 1):
                result.append(matrix[row][right])
            right -= 1

            # A single remaining row may already have been visited.
            if top <= bottom:
                for column in range(right, left - 1, -1):
                    result.append(matrix[bottom][column])
                bottom -= 1

            # A single remaining column may already have been visited.
            if left <= right:
                for row in range(bottom, top - 1, -1):
                    result.append(matrix[row][left])
                left += 1

        return result


@dataclass
class MatrixTraversal:
    strategies: dict[str, TraversalStrategy] = field(init=False)

    def __post_init__(self):
        self.strategies = {
            "SPIRAL": SpiralTraversal(),
            "ROW": RowTraversal(),
            "COLUMN": ColumnTraversal(),
        }

    def traverseMatrix(self, matrix: list[str], strategyCode: str) -> list[int]:
        # Parse each row once into a separate numeric matrix.
        values = []

        for row in matrix:
            values.append([int(value) for value in row.split(",")])

        # Only the selected strategy decides the visiting order.
        strategy = self.strategies[strategyCode]
        return strategy.traverse(values)
```
