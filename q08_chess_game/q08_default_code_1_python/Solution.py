class Solution:
    def __init__(self):
        self.helper = None

    def init(self, helper, chessboard: list[list[str]]):
        self.helper = helper
        #self.helper.println(f"board initialized")

    def move(self, start_row: int, start_col: int, end_row: int, end_col: int) -> str:
        return ""

    def get_game_status(self) -> int:
        return 0

    def get_next_turn(self) -> int:
        return -1  

