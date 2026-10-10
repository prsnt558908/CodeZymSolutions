
# Search for a Word in a 2D Board in Python

#### Problem Statement

[https://codezym.com/question/303-search-word-in-2d-board](https://codezym.com/question/303-search-word-in-2d-board)


The core idea is to walk on the grid one letter at a time. Start at a cell that matches the first letter. Then move to a neighbor that matches the next letter. If a path gets stuck, step back and try another direction. This is **backtracking**, using depth first search (DFS). No special object-oriented design pattern is needed.

We are given a grid of letters and a word. We need to check if the word can be traced on the grid.

Each next letter must be in a cell directly left, right, above or below the previous one. A cell can be used only once in a path.

We will first write a simple backtracking solution. Then we will add two small improvements that can skip a lot of useless work.

## Understanding the Problem

The board comes as a list of strings. Each string is one row, like `"A,B,C"`. We remove the commas and keep only the letters.

Here is Example 2. The word is `ABCDEFGHI`.

```python
board = [
    "A,B,C",
    "H,G,D",
    "I,F,E",
]
word = "ABCDEFGHI"
```

Start at `A` in the top-left corner. Move right through `B` and `C`. Move down through `D` and `E`. Then move left to `F`, up to `G`, left to `H`, and down to `I`.

Every step goes to a cell that shares a side with the current cell. No cell is used twice. So the answer is `True`.

Diagonal moves are not allowed. We also compare letters exactly, so `a` and `A` are different letters.

Both versions below assume a non-empty rectangular board and a non-empty word.

## Solution 1: Simple Backtracking

### Idea

Try every cell as the starting cell.

From the start cell, run a DFS. The DFS answers one simple question:

> Can I match the rest of the word, starting at this cell?

### How the DFS Works

`dfs(r, c, index)` checks if `word[index:]` can be formed starting at cell `(r, c)`.

1. **Bad cell?** If the cell is outside the board, is already on the path, or has the wrong letter, return `False`.
2. **Last letter matched?** Then the whole word is found. Return `True`.
3. **Mark the cell as used**, so the path cannot come back to it.
4. **Try the 4 neighbors** for the next letter. Python's `or` operator stops at the first neighbor that works.
5. **Unmark the cell.** This is the backtracking step.

Step 5 is important. A failed path must not leave cells blocked. Another path may need those cells later.

The code also unmarks cells when a path succeeds. It stores the result in `found`, frees the cell, and then returns the result.

### Data Structures

- **`grid`**: a list of lists of letters. The row `"A,B,C"` becomes `['A', 'B', 'C']`. Now any cell is just `grid[r][c]`.
- **`visited`**: a list of lists of Boolean flags. `True` means the cell is on the current path. This is how we follow the "use a cell only once" rule.

The class has three methods:

- `exist()` sets things up and tries every start cell.
- `dfs()` does the backtracking search.
- `parse_board()` turns rows like `"A,B,C"` into the grid.

We use a Python `dataclass` to declare the search state. It supplies the no-argument constructor, so we can create an object with `WordSearch()`, as in the starter code.

`init=False` keeps the internal fields out of the constructor's arguments. `default_factory=list` gives each object its own lists.

### Code

```python
from dataclasses import dataclass, field
from typing import List


@dataclass
class WordSearch:
    grid: List[List[str]] = field(default_factory=list, init=False)  # the board letters, grid[row][col]
    visited: List[List[bool]] = field(default_factory=list, init=False)  # True for cells used on the current path
    rows: int = field(default=0, init=False)
    cols: int = field(default=0, init=False)
    word: str = field(default="", init=False)

    def exist(self, board: List[str], word: str) -> bool:
        self.grid = self.parse_board(board)
        self.rows = len(self.grid)
        self.cols = len(self.grid[0])
        self.visited = [[False] * self.cols for _ in range(self.rows)]
        self.word = word

        # Try every cell as the starting cell of the word.
        for r in range(self.rows):
            for c in range(self.cols):
                if self.dfs(r, c, 0):
                    return True
        return False

    def dfs(self, r: int, c: int, index: int) -> bool:
        """Checks if word[index:] can be formed starting at cell (r, c)."""
        # Outside the board: dead end.
        if r < 0 or r >= self.rows or c < 0 or c >= self.cols:
            return False
        # Cell already on the path, or wrong letter: dead end.
        if self.visited[r][c] or self.grid[r][c] != self.word[index]:
            return False
        # This cell matches the last letter, so the whole word is found.
        if index == len(self.word) - 1:
            return True

        self.visited[r][c] = True  # use this cell

        # Look for the next letter in the 4 neighbors.
        found = (
            self.dfs(r + 1, c, index + 1)  # down
            or self.dfs(r - 1, c, index + 1)  # up
            or self.dfs(r, c + 1, index + 1)  # right
            or self.dfs(r, c - 1, index + 1)  # left
        )

        self.visited[r][c] = False  # backtrack: free the cell for other paths
        return found

    def parse_board(self, board: List[str]) -> List[List[str]]:
        """Converts rows like "A,B,C" into a grid of letters."""
        letters = []
        for r in range(len(board)):
            # "A,B,C" -> "ABC" (also drops any spaces)
            row = board[r].replace(",", "").replace(" ", "")
            letters.append(list(row))
        return letters
```

Each row in `visited` is created separately. Avoid `[[False] * self.cols] * self.rows`, because that repeats references to the same row. Changing one row would then change all of them.

### Complexity

Let `R` and `C` be the number of rows and columns. Let `L` be the length of the word.

- **Time:** `O(R × C × 3^L)`. We may start from any of the `R × C` cells. The first move has at most 4 choices. After that, each move has at most 3 new directions, because the 4th one is the cell we just came from. Calls to invalid or visited cells stop immediately.
- **Space:** `O(R × C)` for the grid and the visited lists, plus `O(L)` for the recursion.

### Problems with Solution 1

Solution 1 is correct. But it can do a lot of useless work.

Take a 6 × 6 board where every cell is `A`. Search for `AAAAAAAAAAAAAAB`, which is 14 `A`s followed by one `B`.

There is no `B` on the board. So the answer is clearly `False`.

But Solution 1 does not know that. It explores paths of `A`s from every cell before it gives up. That is about **9 million** DFS calls for a tiny board.

We can avoid this with two small improvements.

## Solution 2: Backtracking with Pruning

Pruning means skipping searches that can never succeed.

The DFS stays exactly the same. Before it starts, we reject impossible letter counts and choose which end of the word to start from.

### Improvement 1: Count the Letters

Count each letter on the board. Count each letter in the word.

If the word needs a letter more times than the board has it, return `False` right away.

In the example above, the word needs one `B` and the board has none. We return `False` with zero DFS calls.

We also check one easy thing before counting. A word longer than the number of cells can never fit.

**Why a dictionary?** It maps each letter to its count, with fast lookups and updates. `counts.get(ch, 0)` returns the current count, or `0` if the letter is missing. With uppercase and lowercase English letters, each dictionary holds at most 52 different letters.

### Improvement 2: Search from the Rarer End

If a path spells the word, the same path walked backwards spells the reversed word. So the word exists exactly when its reverse exists.

Why does this help? The DFS only goes deep from cells that match the first letter. Fewer matching cells means fewer starting points for a deeper search.

So we compare the first and last letters of the word. If the first letter is more common on the board than the last letter, we search for the reversed word instead.

In Python, `word[::-1]` creates the reversed word. This choice often reduces the work, though it does not guarantee a faster search for every board.

Look at this board. The two `B` cells touch only diagonally.

```python
board = [
    "A,A,A,A,A,A",
    "A,A,A,A,A,A",
    "A,A,A,A,A,A",
    "A,A,A,A,A,A",
    "A,A,A,A,A,B",
    "A,A,A,A,B,A",
]
```

Search for `AAAAAAAAAAAAABB`, which is 13 `A`s followed by two `B`s. The letter count check passes, because the board has two `B`s.

Searching from the front, the DFS goes deep from all 34 `A` cells. It makes about **3.4 million** DFS calls.

Searching for the reverse, `BBAAAAAAAAAAAAA`, the DFS goes deep only from the 2 `B` cells. Neither `B` has another `B` next to it. So the search ends after just **44** DFS calls.

These counts include every call to `dfs()`, including calls that immediately return because of a wrong letter, a visited cell, or an out-of-bounds position. In the reversed search, there are 36 starting calls and 4 neighbor calls from each of the 2 `B` cells: `36 + 2 × 4 = 44`.

### Code

```python
from dataclasses import dataclass, field
from typing import List


@dataclass
class WordSearch:
    grid: List[List[str]] = field(default_factory=list, init=False)  # the board letters, grid[row][col]
    visited: List[List[bool]] = field(default_factory=list, init=False)  # True for cells used on the current path
    rows: int = field(default=0, init=False)
    cols: int = field(default=0, init=False)
    word: str = field(default="", init=False)

    def exist(self, board: List[str], word: str) -> bool:
        self.grid = self.parse_board(board)
        self.rows = len(self.grid)
        self.cols = len(self.grid[0])

        # A path can never be longer than the number of cells.
        if len(word) > self.rows * self.cols:
            return False

        # Improvement 1: the board must have enough copies of every letter.
        board_count = {}
        for row in self.grid:
            for ch in row:
                board_count[ch] = board_count.get(ch, 0) + 1

        word_count = {}
        for ch in word:
            word_count[ch] = word_count.get(ch, 0) + 1

        for ch in word_count:
            if word_count[ch] > board_count.get(ch, 0):
                return False

        # Improvement 2: start from the end of the word that is rarer on the board.
        # A path for the reversed word is the same path walked backwards.
        first = word[0]
        last = word[-1]
        if board_count[first] > board_count[last]:
            word = word[::-1]

        self.word = word
        self.visited = [[False] * self.cols for _ in range(self.rows)]

        # Try every cell as the starting cell of the word.
        for r in range(self.rows):
            for c in range(self.cols):
                if self.dfs(r, c, 0):
                    return True
        return False

    def dfs(self, r: int, c: int, index: int) -> bool:
        """Checks if word[index:] can be formed starting at cell (r, c)."""
        # Outside the board: dead end.
        if r < 0 or r >= self.rows or c < 0 or c >= self.cols:
            return False
        # Cell already on the path, or wrong letter: dead end.
        if self.visited[r][c] or self.grid[r][c] != self.word[index]:
            return False
        # This cell matches the last letter, so the whole word is found.
        if index == len(self.word) - 1:
            return True

        self.visited[r][c] = True  # use this cell

        # Look for the next letter in the 4 neighbors.
        found = (
            self.dfs(r + 1, c, index + 1)  # down
            or self.dfs(r - 1, c, index + 1)  # up
            or self.dfs(r, c + 1, index + 1)  # right
            or self.dfs(r, c - 1, index + 1)  # left
        )

        self.visited[r][c] = False  # backtrack: free the cell for other paths
        return found

    def parse_board(self, board: List[str]) -> List[List[str]]:
        """Converts rows like "A,B,C" into a grid of letters."""
        letters = []
        for r in range(len(board)):
            # "A,B,C" -> "ABC" (also drops any spaces)
            row = board[r].replace(",", "").replace(" ", "")
            letters.append(list(row))
        return letters
```

Each code block is a complete solution. Use either version of `WordSearch`.

### Complexity

- **Time:** Counting takes `O(R × C + L)`. Reversing the word, when needed, takes `O(L)`. The worst case of the DFS is still `O(R × C × 3^L)`. The improvements often make the actual work much smaller.
- **Space:** `O(R × C + L)`, same as before. Each dictionary holds at most 52 letters. The reversed string, when created, uses `O(L)` space.

## Choosing a Solution

Solution 1 is a clear starting point. It tries every starting cell and uses backtracking to obey the rule that a cell can appear only once in a path.

Solution 2 keeps the same DFS. It checks the word length and letter counts first, then searches from the rarer end. On the all-`A` example, this cuts the work from about 9 million DFS calls to zero. On the diagonal-`B` example, searching from the rarer end cuts it from about 3.4 million calls to 44.

Both versions have the same worst-case time complexity. Use Solution 2 when you want to avoid these common slow cases with only a little extra code.
