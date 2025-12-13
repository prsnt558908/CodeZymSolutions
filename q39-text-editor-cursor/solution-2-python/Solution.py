from dataclasses import dataclass, field
from typing import List


@dataclass
class NotepadEditor:
    """
    Design an in-memory Notepad text editor that stores text as lines
    and maintains a cursor. Supports cursor movement, character insertion,
    deletion, and reading the current line.
    """

    # Number of lines to move on pageUp/pageDown
    linesPerPage: int

    # Document represented as list of mutable lines (each line is a string)
    lines: List[str] = field(default_factory=list)

    # Cursor position (0-based)
    row: int = 0
    col: int = 0

    def __post_init__(self) -> None:
        # Initially: one empty line, cursor at (0, 0)
        if not self.lines:
            self.lines.append("")
        self.row = 0
        self.col = 0

    def addString(self, text: str) -> None:
        """
        Insert a string at the current cursor position.
        If cursor is beyond last character of the line, fill the gap with '-' first.
        Cursor ends just after the last inserted character.
        """
        if text is None or text == "":
            return

        line = self.lines[self.row]
        length = len(line)

        # Hyphen-fill if cursor is beyond last character
        if self.col > length:
            to_fill = self.col - length
            line = line + ("-" * to_fill)
            length = len(line)  # now length == col

        # Insert the text at current column
        line = line[:self.col] + text + line[self.col:]
        self.lines[self.row] = line

        # Cursor moves just after the last inserted character
        self.col += len(text)

    def deleteChar(self) -> None:
        """
        Delete character at column-1 relative to cursor.
        - If col > 0: delete char at col-1 within current line (if it exists) and move cursor to col-1.
        - If col == 0 and row > 0: remove current row, move cursor to end of previous row.
        - If at (0, 0): no-op.
        """
        # If at very start of document, nothing to delete
        if self.row == 0 and self.col == 0:
            return

        if self.col > 0:
            line = self.lines[self.row]
            index_to_delete = self.col - 1

            # Delete if within current line
            if 0 <= index_to_delete < len(line):
                line = line[:index_to_delete] + line[index_to_delete + 1:]
                self.lines[self.row] = line

            # Cursor moves left by one column (even if no character actually deleted)
            self.col -= 1
        else:
            # col == 0 and row > 0
            # Remove current line and move cursor to end of previous line
            self.lines.pop(self.row)
            self.row -= 1
            previous_line = self.lines[self.row]
            self.col = len(previous_line)

    def moveLeft(self) -> None:
        """
        Move cursor one position left within the current line.
        - If col > 0: col--.
        - If at column 0: no-op.
        Cursor never moves to a different line.
        """
        if self.col > 0:
            self.col -= 1
        # else: at column 0, do nothing

    def moveRight(self) -> None:
        """
        Move cursor one position right within the current line.
        - Always increments col by 1, even if already after the last character.
          (Cursor can move arbitrarily far to the right within this line.
           addString() will hyphen-fill any gap if needed.)
        Cursor never moves to a different line.
        """
        self.col += 1

    def moveUp(self) -> None:
        """
        Move cursor one line up, trying to keep the same column.
        If target line is shorter, place cursor just after last character of that line.
        If already on first line, no-op.
        """
        if self.row == 0:
            # No line above
            return

        self.row -= 1
        line_above = self.lines[self.row]
        length = len(line_above)

        # Try to keep same column; clamp to line length (just after last character)
        if self.col > length:
            self.col = length

    def moveDown(self) -> None:
        """
        Move cursor one line down, trying to keep the same column.
        If there is no row below, create an empty row below and place cursor at column 0.
        If there is a row below but shorter, clamp to that line's length.
        """
        if self.row + 1 < len(self.lines):
            # Move to existing next line
            self.row += 1
            line_below = self.lines[self.row]
            length = len(line_below)
            if self.col > length:
                self.col = length
        else:
            # No row below: create a new empty line and move to its beginning
            self.lines.append("")
            self.row += 1
            self.col = 0

    def pageUp(self) -> None:
        """
        Move cursor up by up to linesPerPage lines.
        If there are not enough lines above to complete a full page jump, this is a no-op.
        Otherwise, equivalent to calling moveUp() linesPerPage times.
        """
        if self.linesPerPage <= 0:
            return

        # If not enough lines above for a full jump, no-op
        if self.row < self.linesPerPage:
            return

        for _ in range(self.linesPerPage):
            self.moveUp()  # safe: we know we can go up linesPerPage times

    def pageDown(self) -> None:
        """
        Move cursor down by up to linesPerPage lines.
        Equivalent to calling moveDown() linesPerPage times.
        If there are not enough rows below, new empty rows are created and cursor
        is placed at the beginning of the final row.
        """
        if self.linesPerPage <= 0:
            return

        for _ in range(self.linesPerPage):
            self.moveDown()

    def readLeft(self) -> str:
        """
        Return characters from index 0 up to cursorCol - 1 on the current line.
        If the logical line is shorter than cursorCol, return up to the last character.
        """
        line = self.lines[self.row]
        length = len(line)

        # Number of characters to return is min(col, len)
        end = min(self.col, length)
        return line[:end]
