from dataclasses import dataclass
from enum import Enum, auto
from typing import List


class TextEditor:
    """
    In-memory Text Editor with Undo/Redo.

    Rules (per problem statement):
    - Document starts with 0 rows; each row starts empty.
    - addText(row == currentRowCount) creates a new empty row, then inserts.
    - deleteText never removes rows; empty rows persist.
    - New edits clear the redo stack.
    - undo/redo are no-ops if their stacks are empty.

    Implementation:
    - Rows stored as List[str].
    - History stored as two stacks of immutable Action records.
    - Inverse operations:
        * ADD  -> inverse is DEL at same row/col for payload length
        * DEL  -> inverse is ADD at same row/col with deleted payload
    """

    # --- Internal types ---

    class _Type(Enum):
        ADD = auto()
        DEL = auto()

    @dataclass(frozen=True)
    class _Action:
        type: "_Type"
        row: int
        col: int
        payload: str  # for ADD: inserted text; for DEL: deleted text

    def __init__(self):
        # starts empty: 0 rows
        self._rows: List[str] = []
        self._undo: List[TextEditor._Action] = []
        self._redo: List[TextEditor._Action] = []

    # Ensures the target row exists for addText:
    # - If row == len(_rows): append a new empty row
    # - If row < len(_rows): OK
    # - If row > len(_rows): invalid per spec; guard to avoid silent bugs
    def _ensure_row_for_add(self, row: int) -> None:
        if row == len(self._rows):
            self._rows.append("")
        elif row < len(self._rows):
            # nothing to do
            pass
        else:
            raise ValueError(f"Row index skips rows: row={row}, currentRowCount={len(self._rows)}")

    def addText(self, row: int, column: int, text: str) -> None:
        """
        Insert 'text' into given row at 'column'.
        If row == current row count, create a new empty row first.
        Clears redo history.
        """
        self._ensure_row_for_add(row)
        s = self._rows[row]
        # Assuming inputs are valid per spec: 0 ≤ column ≤ len(s)
        self._rows[row] = s[:column] + text + s[column:]

        # Record action and clear redo
        self._undo.append(TextEditor._Action(TextEditor._Type.ADD, row, column, text))
        self._redo.clear()

    def deleteText(self, row: int, startColumn: int, length: int) -> None:
        """
        Delete 'length' characters starting at (row, startColumn).
        Row persists even if it becomes empty. Clears redo history.
        """
        s = self._rows[row]
        # Assuming inputs are valid per spec: 0 ≤ startColumn+length ≤ len(s)
        deleted = s[startColumn:startColumn + length]
        self._rows[row] = s[:startColumn] + s[startColumn + length:]

        # Record action and clear redo
        self._undo.append(TextEditor._Action(TextEditor._Type.DEL, row, startColumn, deleted))
        self._redo.clear()

    def undo(self) -> None:
        """
        Revert the most recent change (ADD or DEL).
        Pushes the reverted action onto the redo stack (as the original action entry).
        """
        if not self._undo:
            return
        a = self._undo.pop()
        s = self._rows[a.row]

        if a.type == TextEditor._Type.ADD:
            # Inverse of ADD is delete of the same payload at the same position
            self._rows[a.row] = s[:a.col] + s[a.col + len(a.payload):]
        else:
            # Inverse of DEL is re-insert the deleted payload at the same position
            self._rows[a.row] = s[:a.col] + a.payload + s[a.col:]

        # Move this action to redo (so redo can re-apply it)
        self._redo.append(a)

    def redo(self) -> None:
        """
        Reapply the most recently undone change.
        Pushes the action back onto the undo stack.
        """
        if not self._redo:
            return
        a = self._redo.pop()
        s = self._rows[a.row]

        if a.type == TextEditor._Type.ADD:
            # Re-apply original ADD
            self._rows[a.row] = s[:a.col] + a.payload + s[a.col:]
        else:
            # Re-apply original DEL
            self._rows[a.row] = s[:a.col] + s[a.col + len(a.payload):]

        # Back onto undo history
        self._undo.append(a)

    def readLine(self, row: int) -> str:
        """
        Return the entire content of the specified row (possibly "").
        """
        return self._rows[row]
