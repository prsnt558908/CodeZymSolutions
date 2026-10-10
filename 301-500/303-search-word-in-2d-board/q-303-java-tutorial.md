# Search for a Word in a 2D Board in Java

#### Problem Statement
[https://codezym.com/question/303-search-word-in-2d-board](https://codezym.com/question/303-search-word-in-2d-board)


We are given a grid of letters and a word. We need to check if the word can be traced on the grid.

Each next letter must be in a cell directly left, right, above or below the previous one. A cell can be used only once.

The core idea is to walk on the grid one letter at a time. Start at a cell that matches the first letter.

Then move to a neighbor that matches the next letter. Keep going until the whole word is matched.

If a walk gets stuck, we step back one cell and try another direction. This "try, and undo if it fails" method is called **backtracking**. It is simply a depth first search (DFS) on the grid.

We will first write a simple backtracking solution. Then we will add two cheap checks that skip most of the useless work.


## Understanding the Problem

The board comes as a list of strings. Each string is one row, like `"A,B,C"`. We remove the commas and keep only the letters.

Here is Example 2. The word is `ABCDEFGHI`.

```
A → B → C
        ↓
H ← G   D
↓   ↑   ↓
I   F ← E
```

Every step goes to a cell that shares a side with the current cell. No cell is used twice. So the answer is `true`.

Diagonal moves are not allowed. We also compare letters exactly, so `a` and `A` are different letters.


## Solution 1: Simple Backtracking

### Idea

Try every cell as the starting cell.

From the start cell, run a DFS. The DFS answers one simple question:

> Can I match the rest of the word, starting at this cell?

### How the DFS Works

`dfs(r, c, index)` checks if `word[index...]` can be formed starting at cell `(r, c)`.

1. **Bad cell?** If the cell is outside the board, is already on the path, or has the wrong letter, return `false`.
2. **Last letter matched?** Then the whole word is found. Return `true`.
3. **Mark the cell as used**, so the path cannot come back to it.
4. **Try the 4 neighbors** for the next letter. The `||` operator stops at the first neighbor that works.
5. **Unmark the cell.** This is the backtracking step.

Step 5 is important. A failed path must not leave cells blocked. Another path may need those cells later.

### Data Structures

- **`char[][] grid`**: the board as a 2D array of letters. The row `"A,B,C"` becomes `['A', 'B', 'C']`. Now any cell is just `grid[r][c]`.
- **`boolean[][] visited`**: one flag per cell. `true` means the cell is on the current path. This is how we follow the "use a cell only once" rule.

The class has three methods:

- `exist()` sets things up and tries every start cell.
- `dfs()` does the backtracking search.
- `parseBoard()` turns rows like `"A,B,C"` into the grid.

### Code

```java
import java.util.*;

public class WordSearch {

    char[][] grid;        // the board letters, grid[row][col]
    boolean[][] visited;  // true for cells used on the current path
    int rows, cols;
    String word;

    public WordSearch() {
    }

    public boolean exist(List<String> board, String word) {
        this.grid = parseBoard(board);
        this.rows = grid.length;
        this.cols = grid[0].length;
        this.visited = new boolean[rows][cols];
        this.word = word;

        // Try every cell as the starting cell of the word.
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (dfs(r, c, 0)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if word[index...] can be formed starting at cell (r, c).
     */
    boolean dfs(int r, int c, int index) {
        // Outside the board: dead end.
        if (r < 0 || r >= rows || c < 0 || c >= cols) {
            return false;
        }
        // Cell already on the path, or wrong letter: dead end.
        if (visited[r][c] || grid[r][c] != word.charAt(index)) {
            return false;
        }
        // This cell matches the last letter, so the whole word is found.
        if (index == word.length() - 1) {
            return true;
        }

        visited[r][c] = true;  // use this cell

        // Look for the next letter in the 4 neighbors.
        boolean found = dfs(r + 1, c, index + 1)   // down
                || dfs(r - 1, c, index + 1)        // up
                || dfs(r, c + 1, index + 1)        // right
                || dfs(r, c - 1, index + 1);       // left

        visited[r][c] = false; // backtrack: free the cell for other paths
        return found;
    }

    /**
     * Converts rows like "A,B,C" into a grid of letters.
     */
    char[][] parseBoard(List<String> board) {
        char[][] letters = new char[board.size()][];
        for (int r = 0; r < board.size(); r++) {
            // "A,B,C" -> "ABC" (also drops any spaces)
            String row = board.get(r).replace(",", "").replace(" ", "");
            letters[r] = row.toCharArray();
        }
        return letters;
    }
}
```

### Complexity

Let `R` and `C` be the number of rows and columns. Let `L` be the length of the word.

- **Time:** `O(R × C × 3^L)`. We may start from any of the `R × C` cells. After the first step, each step has at most 3 new directions, because the 4th one is the cell we just came from.
- **Space:** `O(R × C)` for the grid and the visited array, plus `O(L)` for the recursion.

### Problems with Solution 1

Solution 1 is correct. But it can do a lot of useless work.

Take a 6 × 6 board where every cell is `A`. Search for `AAAAAAAAAAAAAAB`, which is 14 `A`s followed by one `B`.

There is no `B` on the board. So the answer is clearly `false`.

But Solution 1 does not know that. It walks every possible path of 14 `A`s, from every cell, before it gives up. That is about **9 million** DFS calls for a tiny board.

We can avoid this with two cheap checks.


## Solution 2: Backtracking with Pruning

Pruning means skipping searches that can never succeed.

The DFS stays exactly the same. We only add two checks before it starts.

### Improvement 1: Count the Letters

Count each letter on the board. Count each letter in the word.

If the word needs a letter more times than the board has it, return `false` right away.

In the example above, the word needs one `B` and the board has none. We return `false` with zero DFS calls.

We also check one easy thing before counting. A word longer than the number of cells can never fit.

**Why a `HashMap`?** It maps each letter to its count, with fast lookups and updates. There are at most 52 different letters (`A` to `Z` and `a` to `z`), so the maps stay tiny.

### Improvement 2: Search from the Rarer End

If a path spells the word, the same path walked backwards spells the reversed word. So the word exists exactly when its reverse exists.

Why does this help? The DFS only goes deep from cells that match the first letter. Fewer matching cells means fewer paths to explore.

So we compare the first and last letters of the word. If the first letter is more common on the board than the last letter, we search for the reversed word instead.

Look at this board. The two `B` cells touch only diagonally.

```
A A A A A A
A A A A A A
A A A A A A
A A A A A A
A A A A A B
A A A A B A
```

Search for `AAAAAAAAAAAAABB`. The letter count check passes, because the board has two `B`s.

Searching from the front, the DFS goes deep from all 34 `A` cells. It makes about **3.4 million** DFS calls.

Searching for the reverse, `BBAAAAAAAAAAAAA`, the DFS goes deep only from the 2 `B` cells. Neither `B` has another `B` next to it. So the search ends after just **44** DFS calls.

### Code

```java
import java.util.*;

public class WordSearch {

    char[][] grid;        // the board letters, grid[row][col]
    boolean[][] visited;  // true for cells used on the current path
    int rows, cols;
    String word;

    public WordSearch() {
    }

    public boolean exist(List<String> board, String word) {
        this.grid = parseBoard(board);
        this.rows = grid.length;
        this.cols = grid[0].length;

        // A path can never be longer than the number of cells.
        if (word.length() > rows * cols) {
            return false;
        }

        // Improvement 1: the board must have enough copies of every letter.
        Map<Character, Integer> boardCount = new HashMap<>();
        for (char[] row : grid) {
            for (char ch : row) {
                boardCount.put(ch, boardCount.getOrDefault(ch, 0) + 1);
            }
        }
        Map<Character, Integer> wordCount = new HashMap<>();
        for (char ch : word.toCharArray()) {
            wordCount.put(ch, wordCount.getOrDefault(ch, 0) + 1);
        }
        for (char ch : wordCount.keySet()) {
            if (wordCount.get(ch) > boardCount.getOrDefault(ch, 0)) {
                return false;
            }
        }

        // Improvement 2: start from the end of the word that is rarer on the board.
        // A path for the reversed word is the same path walked backwards.
        char first = word.charAt(0);
        char last = word.charAt(word.length() - 1);
        if (boardCount.get(first) > boardCount.get(last)) {
            word = new StringBuilder(word).reverse().toString();
        }

        this.word = word;
        this.visited = new boolean[rows][cols];

        // Try every cell as the starting cell of the word.
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (dfs(r, c, 0)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if word[index...] can be formed starting at cell (r, c).
     */
    boolean dfs(int r, int c, int index) {
        // Outside the board: dead end.
        if (r < 0 || r >= rows || c < 0 || c >= cols) {
            return false;
        }
        // Cell already on the path, or wrong letter: dead end.
        if (visited[r][c] || grid[r][c] != word.charAt(index)) {
            return false;
        }
        // This cell matches the last letter, so the whole word is found.
        if (index == word.length() - 1) {
            return true;
        }

        visited[r][c] = true;  // use this cell

        // Look for the next letter in the 4 neighbors.
        boolean found = dfs(r + 1, c, index + 1)   // down
                || dfs(r - 1, c, index + 1)        // up
                || dfs(r, c + 1, index + 1)        // right
                || dfs(r, c - 1, index + 1);       // left

        visited[r][c] = false; // backtrack: free the cell for other paths
        return found;
    }

    /**
     * Converts rows like "A,B,C" into a grid of letters.
     */
    char[][] parseBoard(List<String> board) {
        char[][] letters = new char[board.size()][];
        for (int r = 0; r < board.size(); r++) {
            // "A,B,C" -> "ABC" (also drops any spaces)
            String row = board.get(r).replace(",", "").replace(" ", "");
            letters[r] = row.toCharArray();
        }
        return letters;
    }
}
```

### Complexity

- **Time:** Counting takes `O(R × C + L)`. The worst case of the DFS is still `O(R × C × 3^L)`. But the two checks remove the common slow cases, so the real work is usually far smaller.
- **Space:** `O(R × C + L)`, same as before. Each map holds at most 52 letters.


## Summary

| | Solution 1 | Solution 2 |
|---|---|---|
| Core idea | Backtracking from every cell | Same backtracking |
| Extra checks | None | Letter counts, search from the rarer end |
| All `A` board, word `AAAAAAAAAAAAAAB` | About 9 million DFS calls | 0 DFS calls |
| Worst case time | `O(R × C × 3^L)` | `O(R × C × 3^L)` |