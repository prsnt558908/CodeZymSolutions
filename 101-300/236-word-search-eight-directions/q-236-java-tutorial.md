# Word Search Eight Directions in Java

#### Problem Statement
[https://codezym.com/question/236-word-search-eight-directions](https://codezym.com/question/236-word-search-eight-directions)


Once a direction is picked, it can never change. So every match is just a **straight line** on the board.


A straight line is fully decided by two things: a **start cell** and one of the **8 directions**. That gives us a simple plan: try every start cell with every direction, and compare letters as we walk.


We begin with this plain brute force. Next, we add a few cheap checks that throw away impossible cases before any walking. Finally, we read the board as rows, columns and diagonals, and let a built-in substring search do the matching for us.


---

## Understanding the Problem

From any cell we can move in 8 directions. Each direction is a fixed step in row and column:

```
  (-1,-1)   (-1, 0)   (-1, 1)
     ↖         ↑         ↗
  ( 0,-1) ←  start  → ( 0, 1)
     ↙         ↓         ↘
  ( 1,-1)   ( 1, 0)   ( 1, 1)
```


If we start at cell `(r, c)` and keep taking the same step `(dr, dc)`, the letter at index `i` of the word must be at:

```
row = r + i * dr
col = c + i * dc
```


Let us check Example 3, where the word is `ABF`:

```
A B C D
E F G H
I J K L
```

`A` to `B` is a step to the right. So the next letter must be `C`, the cell right of `B`. The word needs `F`, so this fails. Going down from `B` to `F` would change the direction, which is not allowed.


---

## Approach 1: Brute Force

Try every cell as the start. From each cell, try all 8 directions and walk while the letters match.

```java
int[][] directions = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};

for (int r = 0; r < rows; r++) {
    for (int c = 0; c < cols; c++) {
        for (int[] d : directions) {
            int i = 0, x = r, y = c;
            // walk while we are inside the board and letters match
            while (i < word.length() && x >= 0 && x < rows && y >= 0 && y < cols
                    && board.get(x).charAt(y) == word.charAt(i)) {
                i++;
                x += d[0];
                y += d[1];
            }
            if (i == word.length()) return true; // every letter matched
        }
    }
}
return false;
```


This is correct, but it wastes a lot of work:

1. **The word may be too long to fit anywhere.** No straight line on a 200 × 200 board has more than 200 cells, but the word can have 40,000 letters. Brute force still tries every cell and every direction.

2. **It finds out too late that the word runs off the board.** Think of a row full of `A` and the word `AAAA...A` with 150 letters. Starting at column 100 and going right, we match 100 letters before we hit the edge. All of that work is thrown away.

3. **It tries cells that cannot even start the word**, because their letter is not the first letter of the word.


Let `R` be the number of rows, `C` the number of columns and `L` the word length.

- **Time:** O(R × C × min(L, max(R, C))). We try 8 directions from every cell, and each walk stops at a mismatch or at the edge.
- **Space:** O(1) extra.


---

## Approach 2: Brute Force with Quick Checks

We keep the same idea, but throw away impossible cases before walking.


**Check 1: Is the word too long?** A straight line has at most `max(R, C)` cells. If the word is longer, return `false` right away. A 40,000 letter word is now handled instantly.


**Check 2: Does the cell hold the first letter?** If not, skip it. If the word has just 1 letter, finding it is enough, so return `true`.


**Check 3: Does the word end inside the board?** Before walking, find where the last letter would land:

```
endRow = r + (L - 1) * dr
endCol = c + (L - 1) * dc
```

If this cell is outside the board, skip this direction right away, without walking.


The board is a rectangle, so if the first and last cells are inside, every cell between them is inside too. That is why the walk needs no more edge checks.


Only when all checks pass do we compare letters, and we stop at the first mismatch.


### Why the `directions` array?

It keeps the 8 `(row step, column step)` pairs in one place. One loop over it handles every direction, so we never write 8 copies of the same walking code.


### Code

```java
import java.util.*;

public class WordSearch {

    // Row step and column step for each of the 8 directions
    int[][] directions = {
        {-1, -1}, {-1, 0}, {-1, 1}, // up-left, up, up-right
        {0, -1}, {0, 1},            // left, right
        {1, -1}, {1, 0}, {1, 1}     // down-left, down, down-right
    };

    public WordSearch() {
    }

    public boolean exists(List<String> board, String word) {
        int rows = board.size();
        int cols = board.get(0).length();

        // Check 1: no straight line on the board is longer than max(rows, cols)
        if (word.length() > Math.max(rows, cols)) return false;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                // Check 2: the start cell must hold the first letter
                if (board.get(r).charAt(c) != word.charAt(0)) continue;

                // A one letter word needs no direction
                if (word.length() == 1) return true;

                for (int[] d : directions) {
                    if (matches(board, word, r, c, d[0], d[1])) return true;
                }
            }
        }
        return false;
    }

    // Checks if word lies on the board starting at (r, c) and moving (dr, dc) on every step
    boolean matches(List<String> board, String word, int r, int c, int dr, int dc) {
        int last = word.length() - 1;
        int endRow = r + last * dr;
        int endCol = c + last * dc;

        // Check 3: the last letter must land inside the board
        if (endRow < 0 || endRow >= board.size() || endCol < 0 || endCol >= board.get(0).length()) {
            return false;
        }

        // Compare the remaining letters and stop at the first mismatch
        for (int i = 1; i <= last; i++) {
            if (board.get(r + i * dr).charAt(c + i * dc) != word.charAt(i)) return false;
        }
        return true;
    }
}
```


### Complexity

- **Time:** O(R × C × L) in the worst case, where Check 1 makes sure `L ≤ max(R, C)`. Most cells and directions are now thrown away in O(1).
- **Space:** O(1) extra.


### What is still slow?

The checks remove most bad cases, but not all of them. Take a 200 × 200 board full of `A` and the word `AAAA...AB` with 100 letters.


Every cell holds `A`, and the word fits in many directions. So more than 100,000 walks each match 99 letters before failing at `B`. Our own loop does all of this work, one letter at a time.


---

## Approach 3: Read the Board as Lines

Every straight path lies on one of 4 kinds of lines, and each kind covers 2 opposite directions:

| Line | Covers | Cells on one line share the same |
|---|---|---|
| Row | → and ← | `row` |
| Column | ↓ and ↑ | `col` |
| Diagonal | ↘ and ↖ | `row - col` |
| Anti-diagonal | ↙ and ↗ | `row + col` |


**The reverse trick:** reading a line backwards is the same as looking for the reversed word forwards. If `CAT` goes ← in some row, then that row, read left to right, contains `TAC`.


So we turn every row, column, diagonal and anti-diagonal into a string. The word exists if any line contains the word or its reverse. For the board `ABC / DEF / GHI` the lines are:

```
Rows:            ABC   DEF   GHI
Columns:         ADG   BEH   CFI
Diagonals:       C   BF   AEI   DH   G
Anti-diagonals:  A   BD   CEG   FH   I
```

The word `GEC` goes ↗ from `G`. Its reverse `CEG` is one of the anti-diagonals, so the word exists.


Why is this faster? The 8 directions shrink to 4 kinds of lines, and the letter by letter matching now runs inside `String.contains`, a built-in method that is highly optimized. Our own code only builds the lines once.


### Why `row - col` and `row + col`?

Moving ↘ adds 1 to both the row and the column, so `row - col` never changes along that line. Moving ↙ adds 1 to the row and takes 1 from the column, so `row + col` never changes.


So these values work as list indexes for the lines. `row - col` can be negative, so we add `cols - 1` to make the smallest index 0.


### Why `List<StringBuilder>`?

Each line grows one letter at a time while we scan the board. `StringBuilder` adds a letter cheaply, and the list lets us jump straight to the right line by its index.


### Code

```java
import java.util.*;

public class WordSearch {

    public WordSearch() {
    }

    public boolean exists(List<String> board, String word) {
        int rows = board.size();
        int cols = board.get(0).length();

        // No straight line on the board is longer than max(rows, cols)
        if (word.length() > Math.max(rows, cols)) return false;

        // Reading a line backwards is the same as reading the reversed word forwards
        String reversedWord = new StringBuilder(word).reverse().toString();

        for (String line : buildLines(board)) {
            if (line.contains(word) || line.contains(reversedWord)) return true;
        }
        return false;
    }

    // Returns every row, column, diagonal and anti-diagonal of the board as a string
    List<String> buildLines(List<String> board) {
        int rows = board.size();
        int cols = board.get(0).length();

        List<StringBuilder> columns = new ArrayList<>();
        for (int c = 0; c < cols; c++) columns.add(new StringBuilder());

        // A board has rows + cols - 1 diagonals and the same number of anti-diagonals
        List<StringBuilder> diagonals = new ArrayList<>();
        List<StringBuilder> antiDiagonals = new ArrayList<>();
        for (int i = 0; i < rows + cols - 1; i++) {
            diagonals.add(new StringBuilder());
            antiDiagonals.add(new StringBuilder());
        }

        // Visit cells top to bottom, left to right and add each letter to its lines
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char letter = board.get(r).charAt(c);
                columns.get(c).append(letter);
                diagonals.get(r - c + cols - 1).append(letter); // reads down-right
                antiDiagonals.get(r + c).append(letter);        // reads down-left
            }
        }

        List<String> lines = new ArrayList<>(board); // rows are already strings
        for (StringBuilder line : columns) lines.add(line.toString());
        for (StringBuilder line : diagonals) lines.add(line.toString());
        for (StringBuilder line : antiDiagonals) lines.add(line.toString());
        return lines;
    }
}
```


### Complexity

- **Time:** Building the lines visits each cell once, so O(R × C). The `contains` checks have the same worst case as before, O(R × C × L), but run much faster in practice, especially on hard boards like the all `A` board above.
- **Space:** O(R × C) for the line strings.