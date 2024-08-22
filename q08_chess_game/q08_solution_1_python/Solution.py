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
