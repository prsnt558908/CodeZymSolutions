# Validate Chessboard Colours in Java

#### Problem Statement
[https://codezym.com/question/439-validate-chessboard-colours](https://codezym.com/question/439-validate-chessboard-colours)

In a valid chessboard, the colour flips every time you move one step right or one step down. So the top-left cell decides the colour of every other cell. A cell at `(row, col)` must have the top-left colour when `row + col` is even, and the opposite colour when `row + col` is odd.

The solution visits every cell once, reads its colour straight from the row string without splitting it, and compares it with the colour it should have. This runs in `O(n²)` time and uses only `O(1)` extra space.

---

## Solution 1: Build a Grid and Check Neighbours

Let us start by following the rule exactly as it is written.

First, split every row string by commas and store the numbers in a 2D array. The 2D array lets us look up any cell by its `(row, col)` position.

Then compare every cell with its right neighbour and its bottom neighbour. We do not need to check the left and top neighbours, because those pairs were already checked from the other cell.

```java
public boolean isValidChessboard(List<String> board) {
    int n = board.size();
    int[][] grid = new int[n][n];

    // Turn every "1,0,1" row into numbers
    for (int row = 0; row < n; row++) {
        String[] values = board.get(row).split(",");
        for (int col = 0; col < n; col++) {
            grid[row][col] = Integer.parseInt(values[col]);
        }
    }

    // Compare each cell with its right and bottom neighbours
    for (int row = 0; row < n; row++) {
        for (int col = 0; col < n; col++) {
            if (col + 1 < n && grid[row][col] == grid[row][col + 1]) {
                return false;
            }
            if (row + 1 < n && grid[row][col] == grid[row + 1][col]) {
                return false;
            }
        }
    }
    return true;
}
```

### Problem with this approach

The time is fine. We touch each cell only a few times, so it is `O(n²)`.

But the grid needs `O(n²)` extra memory. For `n = 1000`, that is one million numbers. `split()` also creates a new small string for every cell.

The problem asks for `O(1)` extra space. So the grid has to go.

---

## Solution 2: Follow the Chessboard Pattern (Optimal)

We fix this with two small ideas.

### Idea 1: Read a cell straight from the row string

Every cell value is a single character, `0` or `1`, and a comma sits between two values.

```
character : 1  ,  0  ,  1  ,  0
position  : 0  1  2  3  4  5  6
column    : 0     1     2     3
```

The cell values sit at the even positions. So the cell in column `col` is at position `2 * col`, and we can read it with `line.charAt(2 * col)`.

No splitting and no arrays.

### Idea 2: The top-left cell decides everything

With Idea 1 alone, we could keep comparing neighbours. But there is an even simpler check: each cell is compared just once, and there are no boundary checks like `col + 1 < n`.

Every step right or down must flip the colour. To reach `(row, col)` from the top-left cell, you take `row` steps down and `col` steps right. So the colour flips `row + col` times.

- If `row + col` is even, the colour is back to the top-left colour.
- If `row + col` is odd, it is the opposite colour.

Here is the pattern when the top-left cell is `1`. It is exactly the board from Example 1.

```
row + col        expected colour
0  1  2  3       1  0  1  0
1  2  3  4       0  1  0  1
2  3  4  5       1  0  1  0
3  4  5  6       0  1  0  1
```

So if every cell matches its expected colour, the board is valid. Two neighbours always differ by exactly 1 in `row + col`, so one of them expects the top-left colour and the other expects the opposite colour.

The top-left cell can be `0` or `1`. That is why we read it from the board instead of assuming a fixed colour.

In the code, `topLeft` stores that colour and `other` stores the opposite one. Besides a few loop variables, these two characters are all we keep.

### Quick Check with the Examples

**Example 2**

```
0,1,0
1,0,1
0,1,1
```

The top-left cell is `0`. Cell `(2, 2)` has `row + col = 4`, which is even, so it must be `0`. It is `1`, so we return `false`.

**Example 3**

A one-cell board only has the top-left cell. It always matches itself, so we return `true`.

### Java Code

```java
import java.util.*;

public class ChessboardValidator {

    public ChessboardValidator() {
    }

    /**
     * Returns true if every pair of side-adjacent cells has different colours.
     * Time: O(n^2), extra space: O(1).
     */
    public boolean isValidChessboard(List<String> board) {
        int n = board.size();

        // The top-left cell decides the colour of every other cell
        char topLeft = board.get(0).charAt(0);
        char other = (topLeft == '0') ? '1' : '0';

        for (int row = 0; row < n; row++) {
            String line = board.get(row);

            for (int col = 0; col < n; col++) {
                // Cell values sit at even positions: "1,0,1" has them at 0, 2, 4
                char colour = line.charAt(2 * col);

                // Even row + col: same as top-left. Odd row + col: the other colour.
                char expected = ((row + col) % 2 == 0) ? topLeft : other;

                if (colour != expected) {
                    return false;
                }
            }
        }
        return true;
    }
}
```

### Complexity

- **Time:** `O(n²)`, because each cell is checked once.
- **Space:** `O(1)`, because we only keep a few variables. We never split strings, never build a grid, and never change the input list.