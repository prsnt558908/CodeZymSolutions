class NotepadEditor:
    def __init__(self, lines, linesPerPage):
        # Initialize document lines (no embedded '\n' inside a line)
        if not lines:
            # If lines is None or empty, start with a single empty line
            self.lines = [""]
        else:
            # Copy and normalize None entries to empty strings
            self.lines = [(s if s is not None else "") for s in lines]

        # Page size must be at least 1
        self.linesPerPage = max(1, linesPerPage)

        # Cursor starts at (0, 0)
        self.row = 0
        self.col = 0

    # move cursor one position left (within the same line)
    def moveLeft(self):
        if self.col > 0:
            self.col -= 1
        # If at column 0, do nothing (no move to previous line).

    # move cursor one position right (within the same line)
    def moveRight(self):
        line = self._current_line()
        length = len(line)
        # Cursor can go from 0..length, but cannot go past length
        if self.col < length:
            self.col += 1
        # If already after the rightmost character, do nothing.

    # move cursor one line up, trying to keep current column;
    # if target line is shorter, clamp to its end
    def moveUp(self):
        if self.row == 0:
            # No row above
            return

        desired_col = self.col
        target_row = self.row - 1
        target_len = self._line_length(target_row)

        self.row = target_row
        self.col = self._clamp_column(desired_col, target_len)

    # move cursor one line down, trying to keep current column;
    # if target line is shorter, clamp to its end
    def moveDown(self):
        if self.row >= len(self.lines) - 1:
            # No row below
            return

        desired_col = self.col
        target_row = self.row + 1
        target_len = self._line_length(target_row)

        self.row = target_row
        self.col = self._clamp_column(desired_col, target_len)

    # move cursor up by linesPerPage lines if possible; otherwise no-op
    def pageUp(self):
        target_row = self.row - self.linesPerPage
        if target_row < 0:
            # Not enough lines above for a full page jump
            return

        desired_col = self.col
        target_len = self._line_length(target_row)

        self.row = target_row
        self.col = self._clamp_column(desired_col, target_len)

    # move cursor down by linesPerPage lines if possible; otherwise no-op
    def pageDown(self):
        target_row = self.row + self.linesPerPage
        if target_row >= len(self.lines):
            # Not enough lines below for a full page jump
            return

        desired_col = self.col
        target_len = self._line_length(target_row)

        self.row = target_row
        self.col = self._clamp_column(desired_col, target_len)

    # Returns characters from index 0 up to (col - 1) of current line.
    # If col is beyond line length, return the full line.
    def readLeft(self):
        line = self._current_line()
        length = len(line)

        end_exclusive = self.col
        if end_exclusive < 0:
            end_exclusive = 0
        elif end_exclusive > length:
            end_exclusive = length

        return line[:end_exclusive]

    # -------- helpers --------

    def _current_line(self):
        if self.row < 0 or self.row >= len(self.lines):
            return ""
        s = self.lines[self.row]
        return "" if s is None else s

    def _line_length(self, row):
        if row < 0 or row >= len(self.lines):
            return 0
        s = self.lines[row]
        return 0 if s is None else len(s)

    def _clamp_column(self, desired_col, line_len):
        col = desired_col
        if col < 0:
            col = 0
        elif col > line_len:
            col = line_len
        return col
