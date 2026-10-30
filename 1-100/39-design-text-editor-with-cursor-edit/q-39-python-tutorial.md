# Design a Text Editor with Cursor Operations and Basic Editing in Python

#### Problem Statement
[https://codezym.com/question/39-design-text-editor-with-cursor-edit](https://codezym.com/question/39-design-text-editor-with-cursor-edit)


We are building **NotepadEditor**, a small in-memory notepad. It stores text as lines and keeps a cursor that can move around, type text and delete characters.

The core idea is simple: **keep every line as its own string in a list, and keep the cursor as two numbers, `row` and `col`.** Each operation then works on just one line. Most of this problem is about one question: where does the cursor land after each move?

No design pattern is needed here. One class with one small helper method is the clearest design. Command and Memento are the usual text editor patterns, but they exist for undo and redo, which this problem does not ask for.


## Key Rules

These are the rules that are easy to miss:

- **The cursor can sit past the end of its line.** `moveRight()` always moves one column right, even after the last character. It never jumps to the next line.
- **Moving up or down keeps the column if it can.** If the new line is shorter, the cursor lands just after its last character. The old column is not remembered: in Example 3, `(1,5)` goes up to `(0,2)` and then back down to `(1,2)`. `moveUp()` on the first row does nothing.
- **Moving down never fails.** `moveDown()` on the last row creates an empty row, so the cursor lands at column `0`. `pageDown()` creates as many rows as it needs.
- **`pageUp()` moves all the way or not at all.** If there are fewer than `linesPerPage` rows above, nothing happens.
- **Typing past the end fills the gap with `-`.** On `"abcde"` with the cursor at column `8`, `addString("pqr")` gives `"abcde---pqr"`.
- **`deleteChar()` at column `0` removes the whole row, along with its text.** The text is not moved up to the row above. The cursor goes to the end of the row above.
- **`deleteChar()` past the end** finds no character at `col - 1`, so it deletes nothing. The cursor still moves one column left.
- **`readLeft()` past the end** returns the whole line.


## Solution 1: The Whole Document as One String

The most direct picture of a document is the text itself: one string, with `"\n"` between the lines. So `"hi\nworld"` has two lines. The cursor is still `row` and `col`.

Before we can do anything, we have to find where the cursor's row starts. We walk the text from the beginning and jump over one `"\n"` per row.

```python
def __init__(self, linesPerPage: int):
    self.lines_per_page = linesPerPage
    self.doc = ""  # the whole document, lines joined by "\n"
    self.row = 0
    self.col = 0

def row_start(self, r):
    """Index in doc where row r starts."""
    index = 0
    for _ in range(r):
        index = self.doc.index("\n", index) + 1  # jump past the next "\n"
    return index

def row_length(self, r):
    """Number of characters in row r."""
    start = self.row_start(r)
    end = self.doc.find("\n", start)
    if end == -1:
        end = len(self.doc)  # the last row has no "\n" after it
    return end - start
```

Typing then cuts the document at the cursor and glues the new text in between.

```python
def addString(self, text: str) -> None:
    if not text:
        return
    start = self.row_start(self.row)
    length = self.row_length(self.row)
    padding = "-" * max(0, self.col - length)  # fills the gap when the cursor is past the end
    at = start + min(self.col, length)
    self.doc = self.doc[:at] + padding + text + self.doc[at:]  # copies the whole document
    self.col += len(text)
```

`deleteChar()` works the same way: find the row, then cut one character out.

This gives correct answers, but it is slow:

- **Every call scans from the top.** To reach row 50,000, `row_start()` walks past 50,000 lines. Even `moveUp()` and `moveDown()` need `row_length()` to place the cursor, so a simple move scans too.
- **Every edit copies the whole document.** A string can't be changed, so adding one letter builds a new string of the full text.

So most calls cost O(N), where N is the size of the whole document, even though only one line ever changes.


## Solution 2: A List of Lines

### Idea

Every operation touches only the cursor's line, and moves go up or down one row at a time. So let's store each line on its own: `self.lines`, a list of strings.

- `self.lines[self.row]` gives the cursor's line at once, with no scanning.
- An edit rebuilds only that line. The rest of the document is never copied.

**Why a list?** It gives any row by its index in O(1). Adding a row at the end with `append()`, which is what `moveDown()` does, is O(1) too. Removing a row with `pop(row)` shifts the rows below it, but that only happens on a backspace at column `0`.

**Why plain strings for the lines?** An edit builds the new line with slicing, like `line[:col] + text + line[col:]`. That costs O(L) for one line, which is fine. The big win over Solution 1 is that we never copy the whole document.

**Why no `Cursor` class?** The cursor is just two numbers. Every cursor rule also needs line lengths, so the cursor logic sits best right next to `self.lines`. A separate class would only hold two numbers, with no logic of its own.


### Which design pattern fits?

**None.** The whole state is a list of lines and two numbers. One class keeps it all in one place and is easy to follow.

**Command, not needed.** Each action could become a command object, like `MoveUpCommand` or `AddStringCommand`, with an `execute()` method. That pays off when actions must be stored, undone, redone or replayed. Here every action runs once and is forgotten. We would add a class for each of the eight actions and gain nothing.

**Memento, not needed.** Memento saves snapshots of the editor so we can go back to one later. That is also a tool for undo, and here we never go back.


### How it works

**One helper for every row change.** `moveUp()`, `moveDown()`, `pageUp()` and `pageDown()` all end the same way: go to the new row, then pull the column back if that row is too short. That is `move_to_row()`:

```python
def move_to_row(self, new_row: int) -> None:
    self.row = new_row
    self.col = min(self.col, len(self.lines[self.row]))
```

`moveDown()` on the last row first appends an empty row, then calls the same helper. The new row is empty, so `col` becomes `0` with no extra code.

**Paging is a loop.** `pageDown()` simply calls `moveDown()` `linesPerPage` times. Jumping straight to `row + linesPerPage` looks faster, but it gives wrong answers. Every short row on the way pulls the cursor left, and a jump skips those rows.

Take rows `"abcde"`, `""` and `"abcde"`, with the cursor at `(0,5)` and `linesPerPage = 2`:

| Step | Cursor |
|---|---|
| start | `(0,5)` |
| `moveDown()` | `(1,0)`, because row 1 is empty |
| `moveDown()` | `(2,0)` |

The right answer is `(2,0)`. A direct jump to row 2 would wrongly give `(2,5)`.

`pageUp()` works the same way, after first checking that there are at least `linesPerPage` rows above.

**Reading needs no extra check.** `readLeft()` is just `line[:col]`. A slice stops at the end of the string, so a cursor past the end already gives the whole line.


### Code

```python
class NotepadEditor:
    """An in-memory notepad.

    The text is kept as a list of lines and the cursor is just (row, col).
    Every operation works on the cursor's line only.
    """

    def __init__(self, linesPerPage: int):
        # how many rows pageUp() and pageDown() move
        self.lines_per_page = linesPerPage
        # self.lines[r] is row r. The editor starts with one empty line.
        self.lines = [""]
        # cursor position, both 0-based. col can be past the end of its line.
        self.row = 0
        self.col = 0

    def addString(self, text: str) -> None:
        """Inserts text at the cursor and places the cursor just after it.

        If the cursor is past the end of the line, the gap is filled with '-' first.
        """
        if not text:
            return  # nothing to insert, so no '-' filling either
        line = self.lines[self.row]
        if len(line) < self.col:
            line += "-" * (self.col - len(line))
        self.lines[self.row] = line[:self.col] + text + line[self.col:]
        self.col += len(text)

    def deleteChar(self) -> None:
        """Works like backspace.

        col > 0: deletes the character at col - 1 (if the line has one) and moves one column left.
        col == 0: removes the whole current row and moves to the end of the row above.
        """
        if self.col > 0:
            line = self.lines[self.row]
            if self.col - 1 < len(line):
                self.lines[self.row] = line[:self.col - 1] + line[self.col:]
            self.col -= 1
        elif self.row > 0:
            self.lines.pop(self.row)
            self.row -= 1
            self.col = len(self.lines[self.row])
        # at (0, 0) there is nothing to delete

    def moveLeft(self) -> None:
        """Moves one column left. Does nothing at column 0."""
        if self.col > 0:
            self.col -= 1

    def moveRight(self) -> None:
        """Moves one column right, even past the last character. The row never changes."""
        self.col += 1

    def moveUp(self) -> None:
        """Moves one row up. Does nothing on the first row."""
        if self.row > 0:
            self.move_to_row(self.row - 1)

    def moveDown(self) -> None:
        """Moves one row down. On the last row, an empty row is created first."""
        if self.row == len(self.lines) - 1:
            # the new row is empty, so move_to_row() puts the cursor at column 0
            self.lines.append("")
        self.move_to_row(self.row + 1)

    def pageUp(self) -> None:
        """Same as calling moveUp() linesPerPage times, but only if there are enough rows above."""
        if self.row < self.lines_per_page:
            return
        for _ in range(self.lines_per_page):
            self.moveUp()

    def pageDown(self) -> None:
        """Same as calling moveDown() linesPerPage times. Missing rows are created on the way."""
        for _ in range(self.lines_per_page):
            self.moveDown()

    def readLeft(self) -> str:
        """Text of the current line before the cursor.

        A slice stops at the end of the string, so a cursor past the end gives the whole line.
        """
        return self.lines[self.row][:self.col]

    def move_to_row(self, new_row: int) -> None:
        """Puts the cursor on another row and keeps its column if it can.

        If that row is shorter, the cursor lands just after its last character.
        """
        self.row = new_row
        self.col = min(self.col, len(self.lines[self.row]))
```


### Complexity

Let `L` be the length of the cursor's line (after any `-` filling), `T` the length of the inserted text, `K` the value of `linesPerPage` and `R` the number of rows.

| Method | Time |
|---|---|
| `addString` | O(L + T) |
| `deleteChar` | O(L), or O(R) when it removes a row |
| `moveLeft`, `moveRight`, `moveUp`, `moveDown` | O(1) |
| `pageUp`, `pageDown` | O(K) |
| `readLeft` | O(L) |

Space is O(total characters + R).