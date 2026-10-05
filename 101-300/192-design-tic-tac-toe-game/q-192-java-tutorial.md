# Design Tic Tac Toe Game in Java

#### Problem Statement
[https://codezym.com/question/192-design-tic-tac-toe-game](https://codezym.com/question/192-design-tic-tac-toe-game)

Two players take turns placing marks on an `m x m` board. After every move we return the player's number if that move makes them win, otherwise `0`. A player wins by filling a whole row, a whole column, the main diagonal or the anti-diagonal.


The key observation: a new mark at `(row, col)` can only complete the lines that pass through that cell. That is its own row, its own column, and a diagonal only if the cell lies on it. So we never need to look at the whole board.


We will start with a simple solution that stores the board and checks only those few lines after each move. Then we will make it faster by keeping a running count of marks for every line, so each move takes O(1) time no matter how big the board is. Finally, a small +1 / -1 trick lets us keep just one counter per line.

## Which Cells Are on a Diagonal?

For `m = 3`:

```text
Main diagonal: row == col       Anti-diagonal: row + col == m - 1

  X . .                           . . X
  . X .                           . X .
  . . X                           X . .
```

- Main diagonal cells are `(0,0)`, `(1,1)`, `(2,2)`. Row and column are equal.
- Anti-diagonal cells are `(0,2)`, `(1,1)`, `(2,0)`. Row + column is always `2`, which is `m - 1`.

When `m` is odd, the center cell is on both diagonals. So the two diagonal checks must be independent of each other (two separate `if`s, not `if / else`).

## Solution 1: Check Only the Lines of the Last Move

### Idea

The first idea that comes to mind is to check every row, column and diagonal after each move. It works, but it looks at the whole board on every single move, which is a lot of wasted work.


Instead, we place the mark and check only the lines that pass through `(row, col)`:

- row `row`
- column `col`
- the main diagonal, only if `row == col`
- the anti-diagonal, only if `row + col == m - 1`

If the current player owns all `m` cells of any of these lines, they win. Otherwise we return `0`.

### Why a 2D Board?

`int[][] board` remembers who owns each cell: `0` means empty, `1` or `2` means that player. Without it we would have no way to scan a line.

### Complexity

- **Time:** O(m) per move, since we scan at most 4 lines of `m` cells.
- **Space:** O(m²) for the board.

### Code

```java
/**
 * Solution 1: store the board and, after each move, check only the lines
 * that pass through the cell that was just marked.
 */
public class TicTacGame {
    int m;
    // board[row][col] is 0 when empty, otherwise the player number (1 or 2)
    int[][] board;

    public TicTacGame(int m) {
        this.m = m;
        this.board = new int[m][m];
    }

    public int doMove(int row, int col, int player) {
        board[row][col] = player;

        // only the lines passing through (row, col) can become full now
        if (isRowFilled(row, player)
                || isColumnFilled(col, player)
                || (row == col && isDiagonalFilled(player))
                || (row + col == m - 1 && isAntiDiagonalFilled(player))) {
            return player;
        }
        return 0;
    }

    // Each helper returns true if the player owns every cell of that line.

    boolean isRowFilled(int row, int player) {
        for (int col = 0; col < m; col++) {
            if (board[row][col] != player) {
                return false;
            }
        }
        return true;
    }

    boolean isColumnFilled(int col, int player) {
        for (int row = 0; row < m; row++) {
            if (board[row][col] != player) {
                return false;
            }
        }
        return true;
    }

    // main diagonal: cells where row == col
    boolean isDiagonalFilled(int player) {
        for (int i = 0; i < m; i++) {
            if (board[i][i] != player) {
                return false;
            }
        }
        return true;
    }

    // anti-diagonal: cells where row + col == m - 1
    boolean isAntiDiagonalFilled(int player) {
        for (int i = 0; i < m; i++) {
            if (board[i][m - 1 - i] != player) {
                return false;
            }
        }
        return true;
    }
}
```

### What Can Be Improved?

Each scan walks over cells we have already seen in earlier moves. For example, when a player places their 5th mark in a row, the scan can read their 4 old marks again.


All we really need to know is how many cells of a line the player owns. If we keep that number updated after every move, we never have to scan.

## Solution 2: Count the Marks in Every Line

### Idea

Keep a counter for every line, separately for each player. When a player marks `(row, col)`, add `1` to that player's count for:

- row `row`
- column `col`
- the main diagonal, if `row == col`
- the anti-diagonal, if `row + col == m - 1`

A line has exactly `m` cells and each cell is marked only once. So when a player's count for a line reaches `m`, that player owns the whole line and wins.


We do not need the board anymore, because the problem promises that every move is on an empty cell.


Checking both diagonal counts on every move is safe: they only change when the move is on that diagonal, and if a diagonal were already full, the game would have ended earlier.

### Data Structures Used

- `int[][] rowCount` and `int[][] colCount`: `rowCount[player][row]` is how many marks the player has in that row. The outer array has size `3`, so we can use the player number (`1` or `2`) directly as the index. Index `0` is never used.
- `int[] diagonalCount` and `int[] antiDiagonalCount`: there is only one main diagonal and one anti-diagonal, so one number per player is enough.

### Dry Run (Example 1, m = 3)

The counts shown belong to the player who just moved.

| Move | Player | Updated counts | Result |
|---|---|---|---|
| (0, 0) | 1 | row 0 = 1, col 0 = 1, diagonal = 1 | no count is 3, return 0 |
| (0, 1) | 2 | row 0 = 1, col 1 = 1 | return 0 |
| (1, 1) | 1 | row 1 = 1, col 1 = 1, diagonal = 2, anti-diagonal = 1 | return 0 |
| (0, 2) | 2 | row 0 = 2, col 2 = 1, anti-diagonal = 1 | return 0 |
| (2, 2) | 1 | row 2 = 1, col 2 = 1, diagonal = 3 | diagonal is 3, return 1 |

### Complexity

- **Time:** O(1) per move.
- **Space:** O(m) for the counters.

### Code

```java
/**
 * Solution 2: keep a running count of each player's marks in every row,
 * column and diagonal, so a win is detected in O(1) per move.
 */
public class TicTacGame {
    int m;
    // rowCount[player][row] = number of marks this player has in that row.
    // Index 0 is unused, so the player number (1 or 2) is used directly.
    int[][] rowCount;
    // colCount[player][col] = number of marks this player has in that column
    int[][] colCount;
    // marks of each player on the main diagonal (row == col)
    int[] diagonalCount;
    // marks of each player on the anti-diagonal (row + col == m - 1)
    int[] antiDiagonalCount;

    public TicTacGame(int m) {
        this.m = m;
        this.rowCount = new int[3][m];
        this.colCount = new int[3][m];
        this.diagonalCount = new int[3];
        this.antiDiagonalCount = new int[3];
    }

    public int doMove(int row, int col, int player) {
        // add the new mark to every line that passes through (row, col)
        rowCount[player][row]++;
        colCount[player][col]++;
        if (row == col) {
            diagonalCount[player]++;
        }
        if (row + col == m - 1) {
            antiDiagonalCount[player]++;
        }

        // a count of m means this player owns every cell of that line
        if (rowCount[player][row] == m
                || colCount[player][col] == m
                || diagonalCount[player] == m
                || antiDiagonalCount[player] == m) {
            return player;
        }
        return 0;
    }
}
```

## Solution 3: One Counter per Line (+1 / -1 Trick)

### Idea

We can merge the two players' counts of a line into a single number. Player 1 adds `+1` and player 2 adds `-1`.

- The total becomes `m` only when all `m` cells belong to player 1.
- The total becomes `-m` only when all `m` cells belong to player 2.
- If the line still has an empty cell, or has marks from both players, the total can never reach `m` or `-m`.

So after a move, if `Math.abs()` of the row, column or a diagonal total equals `m`, the current player wins.


This needs half the counters of Solution 2, and the code gets a little shorter.

### Complexity

- **Time:** O(1) per move.
- **Space:** O(m), half of Solution 2.

### Code

```java
/**
 * Solution 3: one counter per line. Player 1 adds +1 and player 2 adds -1,
 * so a line is won when its counter reaches m or -m.
 */
public class TicTacGame {
    int m;
    int[] rows;       // sum of marks in each row
    int[] cols;       // sum of marks in each column
    int diagonal;     // sum of marks on the main diagonal (row == col)
    int antiDiagonal; // sum of marks on the anti-diagonal (row + col == m - 1)

    public TicTacGame(int m) {
        this.m = m;
        this.rows = new int[m];
        this.cols = new int[m];
    }

    public int doMove(int row, int col, int player) {
        int value = (player == 1) ? 1 : -1;

        rows[row] += value;
        cols[col] += value;
        if (row == col) {
            diagonal += value;
        }
        if (row + col == m - 1) {
            antiDiagonal += value;
        }

        // m means player 1 filled the line, -m means player 2 filled it
        if (Math.abs(rows[row]) == m
                || Math.abs(cols[col]) == m
                || Math.abs(diagonal) == m
                || Math.abs(antiDiagonal) == m) {
            return player;
        }
        return 0;
    }
}
```

## Summary

| Solution | Time per move | Space |
|---|---|---|
| 1. Check only the lines of the last move | O(m) | O(m²) |
| 2. Count marks per player for every line | O(1) | O(m) |
| 3. One +1 / -1 counter per line | O(1) | O(m) |