# Design a Text Editor with Cursor Operations in Python

#### Problem Statement
[https://codezym.com/question/40-design-text-editor-cursor-operations](https://codezym.com/question/40-design-text-editor-cursor-operations)


A cursor in a text editor is just two numbers: the row it is on, and its column inside that row. So we keep the document as a list of lines, and the cursor as `row` and `col`.


Moving left or right only changes `col`. Moving up, down, page up or page down all do the same job: jump to another row, then fit the column into that row. So we write this "jump and fit" logic once, in a single helper method. The four vertical moves just call it with a different number of rows.


No design pattern is needed here. There is one cursor, a handful of moves, and no undo or history to manage. A plain class with one shared helper is the simplest and cleanest design. Later we will see why Command and Strategy, two patterns that may come to mind, do not help.


We will start with a simple solution, see where it breaks, and then fix it.


## Understanding the Rules

Think of the cursor as sitting **between** characters. `col` is the number of characters to its left. In `"hello"`, `col` can be anything from `0` (before `h`) to `5` (after `o`).

- **Left and right** stay on the same line. They do nothing at the start or the end of the line.
- **Up and down** move one line and try to keep the same column. If the new line is shorter, the cursor stops at the end of that line. If there is no line above (or below), nothing happens.
- **Page up and page down** jump exactly `linesPerPage` lines, keeping the column the same way. If there are not that many lines, the cursor does not move at all.
- **`readLeft()`** returns the text on the cursor's line that is to the left of the cursor.


In Example 2, the cursor is at the end of `"world"` and moves up to `"hi"`. Since `"hi"` is shorter, the cursor lands right after `i`, and `readLeft()` returns `"hi"`.


## Solution 1: Press "Down" Many Times

The quickest idea is to write up and down directly, and build a page move by pressing "down" (or "up") again and again:

```python
def moveUp(self) -> None:
    if self.row > 0:
        self.row -= 1
        self.col = min(self.col, len(self.lines[self.row]))

def moveDown(self) -> None:
    if self.row < len(self.lines) - 1:
        self.row += 1
        self.col = min(self.col, len(self.lines[self.row]))

# page down = press "down" linesPerPage times
def pageDown(self) -> None:
    for _ in range(self.lines_per_page):
        self.moveDown()
```

`pageUp()` is the same loop with `moveUp()`. Left, right and `readLeft()` are already simple, and they stay the same in the final code.


### Problems with this solution

1. **It moves part of the way.** In Example 4, the lines are `["aaa", "bbbb"]`, `linesPerPage = 3`, and the cursor is at the end of `"bbbb"`. `pageUp()` must do nothing, because there are not 3 lines above. But the loop still moves up one line, so `readLeft()` returns `"aaa"` instead of `"bbbb"`.
2. **It forgets the column.** Every step fits the column into the line it passes through. Take the lines `["abcdef", "", "abcdef"]` with `linesPerPage = 2` and the cursor at `(0, 4)`. The loop passes the empty middle line, so `col` drops to `0`, and the cursor ends at `(2, 0)`. The right answer is `(2, 4)`, because only the target line matters.
3. **It is slow and repeats itself.** One page move costs `linesPerPage` steps. And the "change the row, then fix the column" code is written more than once.


Problems 1 and 2 are real bugs, and both come from the same root: a page move is done as many small moves, instead of one jump.


## Solution 2: One Helper for All Vertical Moves

### The key observation

Up, down, page up and page down are the **same move**. Only the number of rows is different:

| Method | Rows to move |
|---|---|
| `moveUp()` | `-1` |
| `moveDown()` | `+1` |
| `pageUp()` | `-linesPerPage` |
| `pageDown()` | `+linesPerPage` |


So one helper, `move_rows(count)`, does all the work:

1. Find the target row: `row + count`.
2. If that row does not exist, return. The cursor does not move at all.
3. Otherwise jump straight to it, and fit the column: `col = min(col, length of the new line)`.

```python
def move_rows(self, count: int) -> None:
    target_row = self.row + count
    if target_row < 0 or target_row >= len(self.lines):
        return
    self.row = target_row
    self.col = min(self.col, len(self.lines[self.row]))
```

This fixes all three problems:

- Step 2 gives the "all or nothing" rule for page moves.
- We jump straight to the target row, so the lines in between never touch the column.
- Every vertical move is now O(1), and the vertical logic lives in one place.


### The rule that keeps everything safe

Every method keeps this rule true: **`0 <= col <= length of the current line`**.

- `moveLeft()` stops at `0`.
- `moveRight()` stops at the end of the line.
- `move_rows()` fits `col` into the new line.


Because of this rule, `readLeft()` is just the slice `line[:col]`, and `col` always points to a real position in the line.


The problem also asks `readLeft()` to return the whole line when the line is shorter than the cursor's column. That case is already handled: when the cursor moves to a shorter line, `col` becomes the line length, so `line[:col]` is the whole line.


### What we store and why

- **`self.lines`**: the document, a list of strings. A list gives us any line by its row number in O(1), which is exactly what every vertical move needs. We copy the input with `list(lines)`, so changes made outside cannot affect the editor.
- **`self.row` and `self.col`**: the cursor. Two plain numbers are all we need, so a separate `Cursor` class would only add code.


One small detail: if the document is empty, we add one empty line, just like a real notepad. This way `self.lines[self.row]` always works.


### Why not use a design pattern?

**Command pattern:** Text editors are the classic example for Command. Each action, like "move left" or "page down", becomes an object with an `execute()` method. Command shines when we need undo and redo, macros, or a history of actions. This problem needs none of them, so Command would only add one class per action.


**Strategy pattern:** We could write one strategy for line moves and another for page moves. But the two differ only by a number: `1` or `linesPerPage`. Passing that number to `move_rows(count)` does the same job without any extra classes.


### Walkthrough with Example 3

The lines are `["short", "world", "hi", "there"]` and `linesPerPage = 2`.

```
start              cursor = (0, 0)
moveRight() x 3    cursor = (0, 3), between "sho" and "rt"
pageDown()         move_rows(2): row 2 exists, so jump to it
                   col = min(3, len("hi")) = min(3, 2) = 2
                   cursor = (2, 2)
readLeft()         "hi"[:2] = "hi"
```


### Complexity

| Method | Solution 1 | Solution 2 |
|---|---|---|
| `moveLeft`, `moveRight` | O(1) | O(1) |
| `moveUp`, `moveDown` | O(1) | O(1) |
| `pageUp`, `pageDown` | O(linesPerPage) | O(1) |
| `readLeft` | O(col) | O(col) |

`readLeft()` costs O(col) only because it builds a new string of `col` characters. Memory is O(total length of all lines) in both solutions.


### Code

```python
from typing import List


class NotepadEditor:
    """
    A notepad editor that keeps the document as a list of lines
    and a cursor at (row, col), both 0-based.

    Rule we always keep: 0 <= col <= length of the current line.
    """

    def __init__(self, lines: List[str], linesPerPage: int):
        # our own copy, so changes made outside cannot affect the editor
        self.lines = list(lines)
        # an empty document still has one empty line, like a real notepad
        if not self.lines:
            self.lines.append("")
        self.lines_per_page = linesPerPage
        # cursor position
        self.row = 0
        self.col = 0

    def moveLeft(self) -> None:
        if self.col > 0:
            self.col -= 1

    def moveRight(self) -> None:
        if self.col < len(self.lines[self.row]):
            self.col += 1

    def moveUp(self) -> None:
        self.move_rows(-1)

    def moveDown(self) -> None:
        self.move_rows(1)

    def pageUp(self) -> None:
        self.move_rows(-self.lines_per_page)

    def pageDown(self) -> None:
        self.move_rows(self.lines_per_page)

    def readLeft(self) -> str:
        # col never goes past the end of the line, so this is the text before the cursor
        return self.lines[self.row][:self.col]

    def move_rows(self, count: int) -> None:
        """
        Shared logic of all vertical moves.
        A negative count moves up, a positive count moves down.
        If the target row does not exist, the cursor does not move at all.
        Otherwise it keeps its column, or stops at the end of a shorter line.
        """
        target_row = self.row + count
        if target_row < 0 or target_row >= len(self.lines):
            return
        self.row = target_row
        self.col = min(self.col, len(self.lines[self.row]))
```