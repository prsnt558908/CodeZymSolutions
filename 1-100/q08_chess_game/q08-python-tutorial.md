# Design of Chess Game in Python for Low Level Design Interviews using Factory and Strategy Design Patterns

Design of Game of Chess is all about different pieces and their moves. There are only two things that you need to figure out and rest is easy.

- How will you create the different chess pieces
- How will you implement the different moves of these pieces.

This article primarily aims to prepare you for this question in context of a Low Level Design Interview.

We will not go into design of a large system like chess.com which has tens of functionalities and hundreds of classes.

Read complete problem statement, submit and test your code here:

https://codezym.com/question/8-design-chess-game

Please go through above problem statement before reading ahead.

We will discuss the requirements, then build our solution using multiple classes. We will also see how using Factory design pattern makes it simpler to create different chess pieces. Also, strategy pattern makes it easy to reuse the common moves of different pieces.

Finally, we will have complete Python code, and you can test it on above CodeZym link.

## Just a bit of recap..


Game of Chess is played on a board with 8 rows and 8 columns.

There are total 32 pieces, 16 of white and 16 of black. 6 different types of pieces are there.

King can move 1 step and Queen can move any number of steps in vertical, horizontal or diagonal direction.

Rook can move vertically or horizontally, and bishop can only move diagonally

Knight makes 2+1 moves and it can also jump over other pieces in its path.

Pawn moves only 1 step forward, it moves straight and kills diagonally.

## Representing the ChessBoard

We will represent our chess board using a 2-d array. class ChessBoard will keep this 2-d array and also store the game state.

Press enter or click to view image in full size

Chess board, low level design of chess

W for white B for Black,

R for rook, B for Bishop, H for knight, Q for Queen, K for king and P for pawn

e.g. WB is white bishop, BK is black king, empty strings represent empty spaces.

## Requirements

- check whether we can move a piece which is at starting row, column to another row, column
- get game status whether game is in progress or black has won or white has won
- get whose turn is it to play next white, black or if game is already finished

## Breaking in multiple classes

We will start with the classes which don’t depend on other classes and gradually move to other classes. Ours will be a bottom to top approach for class design.


Lets start with the move classes. All though each piece has their own move, if you look closely, you will find that Queen makes straight and diagonal moves, rook makes straight moves and bishop makes diagonal moves. hence there is scope of reusing Straight and diagonal moves to create these classes.

We will use strategy design pattern. Class Move will be the generic class and both StraightMove and DiagonalMove will extend it. StraightMove contains logic for both horizontal and vertical move.

```python
class Move:
    def can_move(self, board, start_row, start_col, end_row, end_col) -> bool:
        pass


class DiagonalMove(Move):
    def can_move(self, board, start_row, start_col, end_row, end_col) -> bool:
        row_delta = end_row - start_row
        col_delta = end_col - start_col
        if abs(row_delta) != abs(col_delta):
            return False
        col_delta = 1 if col_delta > 0 else -1
        row_delta = 1 if row_delta > 0 else -1
        while start_row != end_row:
            if board.get_piece(start_row, start_col) is not None:
                return False
            start_row += row_delta
            start_col += col_delta
        return True


class StraightMove(Move):
    def can_move(self, board, start_row, start_col, end_row, end_col) -> bool:
        row_delta = end_row - start_row
        col_delta = end_col - start_col
        if end_row != start_row and end_col != start_col:
            return False

        # testing vertical move
        if start_row == end_row:
           col_delta = 1 if col_delta > 0 else -1
           start_col += col_delta
           while start_col != end_col:
                if board.get_piece(start_row, start_col) is not None:
                    return False
                start_col += col_delta
           return True  
  
        # testing horizontal move
        if start_col == end_col:
            row_delta = 1 if row_delta > 0 else -1
            start_row += row_delta
            while start_row != end_row:
                if board.get_piece(start_row, start_col) is not None:
                    return False
                start_row += row_delta

        return True
```

Both above move classes are used by chess Piece class to implement default. Piece class is the generic class which takes a list of Move objects and in its can_move() method check whether it is possible to go from start to end position using any of the moves that the Piece object has.

```python
class Piece:
    def __init__(self, color: str, type_: str, moves: Optional[List['Move']] = None):
        self.color = color
        self.type = type_
        self.moves = moves if moves is not None else []

    def can_move(self, board, start_row, start_col, end_row, end_col) -> bool:
        for move in self.moves:
            if move.can_move(board, start_row, start_col, end_row, end_col):
                return True
        return False

    def get_color(self) -> str:
        return self.color

    def get_type(self) -> str:
        return self.type
```

Also King, Knight and Pawn implement their own can_move() method. They don’t share any moves. All of these are subclasses of class Piece.

```python
class KingPiece(Piece):
    def can_move(self, board, start_row, start_col, end_row, end_col) -> bool:
        row_delta = abs(end_row - start_row)
        col_delta = abs(end_col - start_col)
        return row_delta <= 1 and col_delta <= 1


class KnightPiece(Piece):
    def can_move(self, board, start_row, start_col, end_row, end_col) -> bool:
        row_delta = abs(end_row - start_row)
        col_delta = abs(end_col - start_col)
        return (row_delta == 2 and col_delta == 1) or (row_delta == 1 and col_delta == 2)


class PawnPiece(Piece):
    def can_move(self, board, start_row, start_col, end_row, end_col) -> bool:
        pawn = board.get_piece(start_row, start_col)
        if board.get_piece(end_row, end_col) is None:
            if start_col != end_col:
                return False
            return (pawn.get_color() == 'W' and end_row - start_row == 1) or \
                   (pawn.get_color() == 'B' and end_row - start_row == -1)
        else:
            if abs(start_col - end_col) != 1:
                return False
            return (pawn.get_color() == 'W' and end_row - start_row == 1) or \
                   (pawn.get_color() == 'B' and end_row - start_row == -1)
```

If you look at how these Piece objects are created, then it will become clear. Lets go through the code in ChessPieceFactory class.

```python
class ChessPieceFactory:

    def __init__(self):
        self.straight_move = StraightMove()
        self.diagonal_move = DiagonalMove()

    def create_piece(self, color: str, type_: str):
        if type_ == 'Q':
            return Piece(color, type_, [self.straight_move, self.diagonal_move])
        elif type_ == 'R':
            return Piece(color, type_, [self.straight_move])
        elif type_ == 'B':
            return Piece(color, type_, [self.diagonal_move])
        elif type_ == 'K':
            return KingPiece(color, type_)
        elif type_ == 'H':
            return KnightPiece(color, type_)
        elif type_ == 'P':
            return PawnPiece(color, type_)
        return None
```

As you can see in above create_piece() method,

- queen is initialized with stratight_move and diagonal_move,
- rook objects are initialized with straight_move
- while bishop objects are initialized with diagonal_move.

Since KingPiece, KnightPiece and PawnPiece don’t use either straight or diagonal moves so moves list is not passed in their constructor.

Using a factory makes the creation logic of chess pieces simple, just pass the type and color and you get a chess piece object with its moves.

Finally let's see the ChessBoard class which controls the game. It is initialized with 2-d string array that we saw above. And in its constructor it creates Piece objects.

In its move() method it uses the can_move() method in Piece objects before making a move at corresponding start position.

It also tracks game_state and next_turn.

```python
class ChessBoard:
    def __init__(self, chessboard: List[List[str]]):
        self.board = [[None for _ in range(len(chessboard[0]))] for _ in range(len(chessboard))]
        self.factory = ChessPieceFactory()
        self.game_state = 0  # 0 for game in progress, 1 for white has won, 2 for black has won
        self.next_turn = 0  # 0 for white, 1 for black

        for row in range(len(chessboard)):
            for col in range(len(chessboard[0])):
                if len(chessboard[row][col]) >= 2:
                    color = chessboard[row][col][0]
                    type_ = chessboard[row][col][1]
                    self.board[row][col] = self.factory.create_piece(color, type_)

    def move(self, start_row: int, start_col: int, end_row: int, end_col: int) -> str:
        if self.game_state != 0:
            return "invalid"
        start_piece = self.get_piece(start_row, start_col)
        end_piece = self.get_piece(end_row, end_col)
        if start_piece is None or not self.is_valid(end_row, end_col):
            return "invalid"
        if end_piece is not None and end_piece.get_color() == start_piece.get_color():
            return "invalid"
        if not start_piece.can_move(self, start_row, start_col, end_row, end_col):
            return "invalid"
        self.board[start_row][start_col] = None
        self.board[end_row][end_col] = start_piece
        self.next_turn = 1 if self.next_turn == 0 else 0
        if end_piece is not None and end_piece.get_type() == 'K':
            self.game_state = 1 if end_piece.get_color() == 'B' else 2
        if end_piece is None:
            return ""
        return f"{end_piece.get_color()}{end_piece.get_type()}"

    def get_piece(self, row: int, col: int):
        if not self.is_valid(row, col):
            return None
        return self.board[row][col]

    def get_next_turn(self) -> int:
        return self.next_turn

    def get_game_state(self) -> int:
        return self.game_state

    def is_valid(self, row: int, col: int) -> bool:
        return 0 <= row < len(self.board) and 0 <= col < len(self.board[0])
```

## Video Explanation

[![Design of Chess Game in Python for Low Level Design Interviews](https://img.youtube.com/vi/VWUuQWxmXYQ/hqdefault.jpg)](https://www.youtube.com/watch?v=VWUuQWxmXYQ)

YouTube Video : https://www.youtube.com/watch?v=VWUuQWxmXYQ

## Complete Python Code

```python
from dataclasses import dataclass
from typing import List, Optional

class Move:
    def can_move(self, board, start_row, start_col, end_row, end_col) -> bool:
        pass

class Solution:
    def __init__(self):
        self.helper = None
        self.board = None

    def init(self, helper, chessboard: List[List[str]]):
        self.helper = helper
        self.board = ChessBoard(chessboard)
        self.helper.println(f"board initialized")

    def move(self, start_row: int, start_col: int, end_row: int, end_col: int) -> str:
        return self.board.move(start_row, start_col, end_row, end_col)

    def get_game_status(self) -> int:
        return self.board.get_game_state()

    def get_next_turn(self) -> int:
        if self.board.get_game_state() != 0:
            return -1
        return self.board.get_next_turn()

class ChessBoard:
    def __init__(self, chessboard: List[List[str]]):
        self.board = [[None for _ in range(len(chessboard[0]))] for _ in range(len(chessboard))]
        self.factory = ChessPieceFactory()
        self.game_state = 0  # 0 for game in progress, 1 for white has won, 2 for black has won
        self.next_turn = 0  # 0 for white, 1 for black

        for row in range(len(chessboard)):
            for col in range(len(chessboard[0])):
                if len(chessboard[row][col]) >= 2:
                    color = chessboard[row][col][0]
                    type_ = chessboard[row][col][1]
                    self.board[row][col] = self.factory.create_piece(color, type_)

    def move(self, start_row: int, start_col: int, end_row: int, end_col: int) -> str:
        if self.game_state != 0:
            return "invalid"
        start_piece = self.get_piece(start_row, start_col)
        end_piece = self.get_piece(end_row, end_col)
        if start_piece is None or not self.is_valid(end_row, end_col):
            return "invalid"
        if end_piece is not None and end_piece.get_color() == start_piece.get_color():
            return "invalid"
        if not start_piece.can_move(self, start_row, start_col, end_row, end_col):
            return "invalid"
        self.board[start_row][start_col] = None
        self.board[end_row][end_col] = start_piece
        self.next_turn = 1 if self.next_turn == 0 else 0
        if end_piece is not None and end_piece.get_type() == 'K':
            self.game_state = 1 if end_piece.get_color() == 'B' else 2
        if end_piece is None:
            return ""
        return f"{end_piece.get_color()}{end_piece.get_type()}"

    def get_piece(self, row: int, col: int):
        if not self.is_valid(row, col):
            return None
        return self.board[row][col]

    def get_next_turn(self) -> int:
        return self.next_turn

    def get_game_state(self) -> int:
        return self.game_state

    def is_valid(self, row: int, col: int) -> bool:
        return 0 <= row < len(self.board) and 0 <= col < len(self.board[0])

class ChessPieceFactory:

    def __init__(self):
        self.straight_move = StraightMove()
        self.diagonal_move = DiagonalMove()

    def create_piece(self, color: str, type_: str):
        if type_ == 'Q':
            return Piece(color, type_, [self.straight_move, self.diagonal_move])
        elif type_ == 'R':
            return Piece(color, type_, [self.straight_move])
        elif type_ == 'B':
            return Piece(color, type_, [self.diagonal_move])
        elif type_ == 'K':
            return KingPiece(color, type_)
        elif type_ == 'H':
            return KnightPiece(color, type_)
        elif type_ == 'P':
            return PawnPiece(color, type_)
        return None

class Piece:
    def __init__(self, color: str, type_: str, moves: Optional[List['Move']] = None):
        self.color = color
        self.type = type_
        self.moves = moves if moves is not None else []

    def can_move(self, board, start_row, start_col, end_row, end_col) -> bool:
        for move in self.moves:
            if move.can_move(board, start_row, start_col, end_row, end_col):
                return True
        return False

    def get_color(self) -> str:
        return self.color

    def get_type(self) -> str:
        return self.type

class KingPiece(Piece):
    def can_move(self, board, start_row, start_col, end_row, end_col) -> bool:
        row_delta = abs(end_row - start_row)
        col_delta = abs(end_col - start_col)
        return row_delta <= 1 and col_delta <= 1

class KnightPiece(Piece):
    def can_move(self, board, start_row, start_col, end_row, end_col) -> bool:
        row_delta = abs(end_row - start_row)
        col_delta = abs(end_col - start_col)
        return (row_delta == 2 and col_delta == 1) or (row_delta == 1 and col_delta == 2)

class PawnPiece(Piece):
    def can_move(self, board, start_row, start_col, end_row, end_col) -> bool:
        pawn = board.get_piece(start_row, start_col)
        if board.get_piece(end_row, end_col) is None:
            if start_col != end_col:
                return False
            return (pawn.get_color() == 'W' and end_row - start_row == 1) or \
                   (pawn.get_color() == 'B' and end_row - start_row == -1)
        else:
            if abs(start_col - end_col) != 1:
                return False
            return (pawn.get_color() == 'W' and end_row - start_row == 1) or \
                   (pawn.get_color() == 'B' and end_row - start_row == -1)

class DiagonalMove(Move):
    def can_move(self, board, start_row, start_col, end_row, end_col) -> bool:
        row_delta = end_row - start_row
        col_delta = end_col - start_col
        if abs(row_delta) != abs(col_delta):
            return False
        col_delta = 1 if col_delta > 0 else -1
        row_delta = 1 if row_delta > 0 else -1
        while start_row != end_row:
            if board.get_piece(start_row, start_col) is not None:
                return False
            start_row += row_delta
            start_col += col_delta
        return True

class StraightMove(Move):
    def can_move(self, board, start_row, start_col, end_row, end_col) -> bool:
        row_delta = end_row - start_row
        col_delta = end_col - start_col
        if end_row != start_row and end_col != start_col:
            return False

        if start_row == end_row:
           col_delta = 1 if col_delta > 0 else -1
           start_col += col_delta
           while start_col != end_col:
                if board.get_piece(start_row, start_col) is not None:
                    return False
                start_col += col_delta
           return True    

        if start_col == end_col:
            row_delta = 1 if row_delta > 0 else -1
            start_row += row_delta
            while start_row != end_row:
                if board.get_piece(start_row, start_col) is not None:
                    return False
                start_row += row_delta

        return True
```
