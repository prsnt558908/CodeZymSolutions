from dataclasses import dataclass
import re

class Spreadsheet:
    """
    Spreadsheet data model (5x5 initial, 0-based indices).
    - addRow(index): inserts an empty row and shifts rows at/after index down by 1.
    - addColumn(index): inserts an empty column and shifts columns at/after index right by 1.
    - addEntry(r,c,text,fontName,fontSize,isBold,isItalic): creates/replaces content at (r,c).
      * Constraints: text has no '-', fontName is [a-z]+, 8 <= fontSize <= 72.
    - getEntry(r,c): "text-fontName-fontSize[-b][-i]" or "" if cell empty.

    Single-threaded, deterministic behavior.
    """

    # private static final int INITIAL_SIZE = 5;
    INITIAL_SIZE = 5

    # private final List<List<Cell>> grid;
    def __init__(self):
        # grid = new ArrayList<>(INITIAL_SIZE);
        # for (int r = 0; r < INITIAL_SIZE; r++) {
        #     // Start with 5 columns, all cells empty (null entries are considered empty)
        #     grid.add(new ArrayList<>(Collections.nCopies(INITIAL_SIZE, (Cell) null)));
        # }
        self.grid = []
        for _ in range(self.INITIAL_SIZE):
            # Start with 5 columns, all cells empty (null entries are considered empty)
            self.grid.append([None] * self.INITIAL_SIZE)

    def addRow(self, index: int) -> None:
        """Insert a new empty row at index; 0 <= index <= currentRows"""
        # int currentCols = getCurrentCols();
        current_cols = self.getCurrentCols()
        # validateAddRowIndex(index);
        self.validateAddRowIndex(index)
        # grid.add(index, new ArrayList<>(Collections.nCopies(currentCols, (Cell) null)));
        self.grid.insert(index, [None] * current_cols)

    def addColumn(self, index: int) -> None:
        """Insert a new empty column at index; 0 <= index <= currentCols"""
        # validateAddColumnIndex(index);
        self.validateAddColumnIndex(index)
        # for (List<Cell> row : grid) { row.add(index, null); }
        for row in self.grid:
            row.insert(index, None)

    def addEntry(
        self,
        row: int,
        column: int,
        text: str,
        fontName: str,
        fontSize: int,
        isBold: bool,
        isItalic: bool,
    ) -> None:
        """
        Create/replace the entry at (row, column) with given text and style.
        Validity: 0 <= row < currentRows && 0 <= column < currentCols
        Constraints:
        - text contains no '-' (hyphen)
        - fontName matches [a-z]+
        - 8 <= fontSize <= 72
        """
        # validateCellPosition(row, column);
        self.validateCellPosition(row, column)
        # validateEntryInput(text, fontName, fontSize);
        self.validateEntryInput(text, fontName, fontSize)
        # grid.get(row).set(column, new Cell(text, fontName, fontSize, isBold, isItalic));
        self.grid[row][column] = Spreadsheet.Cell(text, fontName, fontSize, isBold, isItalic)

    def getEntry(self, row: int, column: int) -> str:
        """
        Return serialized content at (row, column) as:
        "text-fontName-fontSize[-b][-i]"
        If empty, return "".
        Validity: 0 <= row < currentRows && 0 <= column < currentCols
        """
        # validateCellPosition(row, column);
        self.validateCellPosition(row, column)
        # Cell cell = grid.get(row).get(column);
        cell = self.grid[row][column]
        # if (cell == null) return "";
        if cell is None:
            return ""

        # StringBuilder sb = new StringBuilder();
        # sb.append(cell.text).append('-').append(cell.fontName).append('-').append(cell.fontSize);
        # if (cell.isBold) sb.append("-b");
        # if (cell.isItalic) sb.append("-i");
        # return sb.toString();
        s = f"{cell.text}-{cell.fontName}-{cell.fontSize}"
        if cell.isBold:
            s += "-b"
        if cell.isItalic:
            s += "-i"
        return s

    # ---------- Helpers ----------
    def getCurrentRows(self) -> int:
        # return grid.size();
        return len(self.grid)

    def getCurrentCols(self) -> int:
        # return grid.isEmpty() ? 0 : grid.get(0).size();
        return 0 if not self.grid else len(self.grid[0])

    def validateAddRowIndex(self, index: int) -> None:
        # int rows = getCurrentRows();
        rows = self.getCurrentRows()
        # if (index < 0 || index > rows) { throw new IllegalArgumentException(...); }
        if index < 0 or index > rows:
            raise ValueError(f"addRow index out of range: {index} (rows={rows})")

    def validateAddColumnIndex(self, index: int) -> None:
        # int cols = getCurrentCols();
        cols = self.getCurrentCols()
        # if (index < 0 || index > cols) { throw new IllegalArgumentException(...); }
        if index < 0 or index > cols:
            raise ValueError(f"addColumn index out of range: {index} (cols={cols})")

    def validateCellPosition(self, row: int, column: int) -> None:
        # int rows = getCurrentRows();
        # int cols = getCurrentCols();
        rows = self.getCurrentRows()
        cols = self.getCurrentCols()
        # if (row < 0 || row >= rows) { throw new IllegalArgumentException(...); }
        if row < 0 or row >= rows:
            raise ValueError(f"Row out of range: {row} (rows={rows})")
        # if (column < 0 || column >= cols) { throw new IllegalArgumentException(...); }
        if column < 0 or column >= cols:
            raise ValueError(f"Column out of range: {column} (cols={cols})")

    def validateEntryInput(self, text: str, fontName: str, fontSize: int) -> None:
        # if (text == null) { throw new IllegalArgumentException("text must not be null"); }
        if text is None:
            raise ValueError("text must not be null")
        # if (text.indexOf('-') >= 0) { throw new IllegalArgumentException("text must not contain hyphen '-' : " + text); }
        if "-" in text:
            raise ValueError(f"text must not contain hyphen '-': {text}")
        # if (fontName == null || !fontName.matches("[a-z]+")) { throw new IllegalArgumentException(...); }
        if fontName is None or re.fullmatch(r"[a-z]+", fontName) is None:
            raise ValueError(f"fontName must be lowercase a-z only: {fontName}")
        # if (fontSize < 8 || fontSize > 72) { throw new IllegalArgumentException(...); }
        if fontSize < 8 or fontSize > 72:
            raise ValueError(f"fontSize must be in [8,72]: {fontSize}")

    # ---------- Cell model ----------
    @dataclass(frozen=True)
    class Cell:
        # final String text;
        # final String fontName;
        # final int fontSize;
        # final boolean isBold;
        # final boolean isItalic;
        text: str
        fontName: str
        fontSize: int
        isBold: bool
        isItalic: bool
