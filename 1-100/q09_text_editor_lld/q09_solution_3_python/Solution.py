from dataclasses import dataclass, field
from typing import List, Dict, Optional


class Solution:
  
    def __init__(self):
        self.factory = CharFlyweightFactory()
        self.rows: List[TextRow] = []

    def init(self, helper):
        self.helper = helper

    def add_character(self, row: int, column: int, ch: str, font_name: str,
                      font_size: int, is_bold: bool, is_italic: bool):
        """
        Add a character at a specific row and column.
        """
        while row >= len(self.rows):
            self.rows.append(TextRow())
        
        flyweight = self.factory.create_style(ch, font_name, font_size, is_bold, is_italic)
        self.rows[row].add_character(flyweight, column)

    def get_style(self, row: int, col: int) -> str:
        """
        Retrieve the style of a character at a specific row and column.
        """
        #return "abdbcj";;
        if row < 0 or row >= len(self.rows):
            return ""
        flyweight = self.rows[row].get_flyweight(col)
        return flyweight.get_char_and_style() if flyweight else ""

    def read_line(self, row: int) -> str:
        """
        Read a full line at a specific row.
        """
        if row < 0 or row >= len(self.rows):
            return ""
        flyweights = self.rows[row].read_line()
        return ''.join(flyweight.get_char() for flyweight in flyweights)

    def delete_character(self, row: int, col: int) -> bool:
        """
        Delete a character at a specific row and column.
        """
        if row < 0 or row >= len(self.rows):
            return False
        return self.rows[row].delete_character(col)


@dataclass
class CharFlyweight:
    """
    Represents a character with its font style attributes.
    """
    ch: str
    font_name: str
    font_size: int
    is_bold: bool
    is_italic: bool

    def get_char(self) -> str:
        """Returns the character."""
        return self.ch

    def get_char_and_style(self) -> str:
        """Returns a formatted string with character and its style."""
        style = f"{self.ch}-{self.font_name}-{self.font_size}"
        if self.is_bold:
            style += "-b"
        if self.is_italic:
            style += "-i"
        return style


class CharFlyweightFactory:
    """
    Factory for creating and reusing CharFlyweight instances.
    """
    def __init__(self):
        self.map: Dict[str, CharFlyweight] = {}

    def create_style(self, ch: str, font_name: str, font_size: int,
                     is_bold: bool, is_italic: bool) -> 'CharFlyweight':
        key = f"{ch}-{font_name}-{font_size}"
        if is_bold:
            key += "-b"
        if is_italic:
            key += "-i"
        
        if key not in self.map:
            self.map[key] = CharFlyweight(ch, font_name, font_size, is_bold, is_italic)
        
        return self.map[key]


class TextRow:
    """
    Represents a single row in the text editor.
    """
    def __init__(self):
        self.data: List[CharFlyweight] = []

    def add_character(self, ch: CharFlyweight, column: int):
        """
        Add a character at a specific column.
        """
        self.data.append(ch)
        current = len(self.data) - 1
        while current > 0 and current > column:
            self.data[current], self.data[current - 1] = self.data[current - 1], self.data[current]
            current -= 1

    def get_flyweight(self, column: int) -> Optional['CharFlyweight']:
        """
        Retrieve a CharFlyweight at the given column.
        """
        if 0 <= column < len(self.data):
            return self.data[column]
        return None

    def read_line(self) -> List['CharFlyweight']:
        """
        Return the entire row as a list of CharFlyweights.
        """
        return self.data

    def delete_character(self, col: int) -> bool:
        """
        Delete a character at a specific column.
        """
        if 0 <= col < len(self.data):
            self.data.pop(col)
            return True
        return False

