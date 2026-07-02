# Design Text Editor Word Processor in Python

Problem Statement: https://codezym.com/question/9-design-text-editor-word-processor

The core idea behind this solution is to design a simple text editor where characters can be inserted, deleted, read, and queried along with their styles. Since many characters in a text editor can have the same character value and the same styling, this solution uses the **Flyweight Design Pattern** with a **Factory**. The flyweight object stores the character and its style, while each text row stores only references to these shared objects. This helps avoid creating duplicate objects for the same character-style combination.

## High Level Design

We divide the text editor into a few simple classes:

- `Solution` acts as the main entry point used by the platform.
- `TextRow` represents one line of text.
- `CharFlyweight` represents a character with its style.
- `CharFlyweightFactory` creates and reuses `CharFlyweight` objects.

This keeps the design clean because each class has one clear responsibility.

## Why Flyweight Pattern?

In a word processor, many characters may share the same formatting.

For example:

```text
a-Arial-12-b
a-Arial-12-b
a-Arial-12-b
```

Instead of creating a new object every time for the same character and style, we can reuse the same object.

That is exactly what the `CharFlyweightFactory` does. It creates a unique key using:

```text
character + font name + font size + bold flag + italic flag
```

If the same key already exists, the factory returns the existing object. Otherwise, it creates a new one.

## Important Classes

## 1. CharFlyweight

`CharFlyweight` stores the character and its formatting information.

It contains:

- character
- font name
- font size
- bold information
- italic information

This class is used as the shared object in the Flyweight Pattern.

## 2. CharFlyweightFactory

`CharFlyweightFactory` is responsible for creating and reusing `CharFlyweight` objects.

It uses a dictionary where:

- key = character and style combination
- value = `CharFlyweight` object

This avoids duplicate objects for the same character-style combination.

## 3. TextRow

`TextRow` represents a single row or line in the editor.

It stores a list of `CharFlyweight` objects. Each object represents one character in that row.

The row supports:

- adding a character at a column
- deleting a character from a column
- reading the full row
- getting the style of a character

A list is used here because the editor needs ordered characters in each row.

## 4. Solution

`Solution` is the main class that exposes all required operations.

It supports:

- `add_character`
- `get_style`
- `read_line`
- `delete_character`

It also maintains all rows of the editor.

## Flow of Add Character

When we add a character:

1. We make sure the required row exists.
2. We ask the factory for a `CharFlyweight` object.
3. The factory either returns an existing object or creates a new one.
4. We insert that object into the required row and column.

## Flow of Get Style

When we call `get_style(row, col)`:

1. We check whether the row is valid.
2. We get the character object at the given column.
3. We return the character and style in the required format.

For example:

```text
a-Arial-12-b-i
```

This means:

- character is `a`
- font name is `Arial`
- font size is `12`
- character is bold
- character is italic

## Flow of Read Line

When we call `read_line(row)`:

1. We get all character objects from that row.
2. We extract only the actual character from each object.
3. We join them and return the final string.

## Flow of Delete Character

When we call `delete_character(row, col)`:

1. We check whether the row is valid.
2. We remove the character at the given column.
3. We return `True` if deletion was successful, otherwise `False`.

## Time Complexity

Let `n` be the number of characters in a row.

| Operation | Time Complexity | Reason |
|---|---:|---|
| Add Character | `O(n)` | Insertion may require shifting characters |
| Get Style | `O(1)` | Direct list access |
| Read Line | `O(n)` | Reads all characters in the row |
| Delete Character | `O(n)` | Deletion may shift characters |

## Space Complexity

The editor stores all characters row by row.

The factory additionally stores unique character-style combinations.

So the space complexity is:

```text
O(total characters + unique character-style combinations)
```

Because of the Flyweight Pattern, repeated character-style combinations reuse the same object.

## Python Code

```python
from dataclasses import dataclass


class Solution:
    def __init__(self):
        self.factory = CharFlyweightFactory()
        self.rows = []

    def init(self, helper):
        self.helper = helper

    def add_character(
        self,
        row: int,
        column: int,
        ch: str,
        font_name: str,
        font_size: int,
        is_bold: bool,
        is_italic: bool,
    ):
        """
        Add a character at a specific row and column.
        If the row does not exist, create empty rows until that row exists.
        """

        if row < 0 or column < 0:
            return

        while row >= len(self.rows):
            self.rows.append(TextRow())

        flyweight = self.factory.create_style(
            ch,
            font_name,
            font_size,
            is_bold,
            is_italic,
        )

        self.rows[row].add_character(flyweight, column)

    def get_style(self, row: int, col: int) -> str:
        """
        Return character and style at given row and column.
        Return empty string if row or column is invalid.
        """

        if row < 0 or row >= len(self.rows):
            return ""

        flyweight = self.rows[row].get_flyweight(col)

        if flyweight is None:
            return ""

        return flyweight.get_char_and_style()

    def read_line(self, row: int) -> str:
        """
        Return complete text of a row.
        Return empty string if row is invalid.
        """

        if row < 0 or row >= len(self.rows):
            return ""

        flyweights = self.rows[row].read_line()

        result = []
        for flyweight in flyweights:
            result.append(flyweight.get_char())

        return "".join(result)

    def delete_character(self, row: int, col: int) -> bool:
        """
        Delete character at given row and column.
        Return True if deleted, otherwise False.
        """

        if row < 0 or row >= len(self.rows):
            return False

        return self.rows[row].delete_character(col)

    # Optional camelCase wrappers.
    # These are useful if the platform calls Java-style method names.

    def addCharacter(
        self,
        row: int,
        column: int,
        ch: str,
        fontName: str,
        fontSize: int,
        isBold: bool,
        isItalic: bool,
    ):
        return self.add_character(
            row,
            column,
            ch,
            fontName,
            fontSize,
            isBold,
            isItalic,
        )

    def getStyle(self, row: int, col: int) -> str:
        return self.get_style(row, col)

    def readLine(self, row: int) -> str:
        return self.read_line(row)

    def deleteCharacter(self, row: int, col: int) -> bool:
        return self.delete_character(row, col)


@dataclass
class CharFlyweight:
    """
    Shared object for one character and its style.

    Flyweight pattern:
    same character + same style can reuse the same object.
    """

    ch: str
    font_name: str
    font_size: int
    is_bold: bool
    is_italic: bool

    def get_char(self) -> str:
        return self.ch

    def get_char_and_style(self) -> str:
        style = str(self.ch) + "-" + str(self.font_name) + "-" + str(self.font_size)

        if self.is_bold:
            style += "-b"

        if self.is_italic:
            style += "-i"

        return style


class CharFlyweightFactory:
    """
    Factory for creating and reusing CharFlyweight objects.
    """

    def __init__(self):
        self.map = {}

    def create_style(
        self,
        ch: str,
        font_name: str,
        font_size: int,
        is_bold: bool,
        is_italic: bool,
    ):
        key = str(ch) + "-" + str(font_name) + "-" + str(font_size)

        if is_bold:
            key += "-b"

        if is_italic:
            key += "-i"

        if key not in self.map:
            self.map[key] = CharFlyweight(
                ch,
                font_name,
                font_size,
                is_bold,
                is_italic,
            )

        return self.map[key]


class TextRow:
    """
    Represents one row of the text editor.
    """

    def __init__(self):
        self.data = []

    def add_character(self, ch, column: int):
        """
        Insert character at given column.
        If column is greater than row length, append at the end.
        """

        if column < 0:
            return

        if column >= len(self.data):
            self.data.append(ch)
        else:
            self.data.insert(column, ch)

    def get_flyweight(self, column: int):
        if 0 <= column < len(self.data):
            return self.data[column]

        return None

    def read_line(self):
        return self.data

    def delete_character(self, col: int) -> bool:
        if 0 <= col < len(self.data):
            self.data.pop(col)
            return True

        return False
```
