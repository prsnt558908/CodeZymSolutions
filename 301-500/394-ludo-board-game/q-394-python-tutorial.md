# Design a Ludo Board Game in Python

#### Problem Statement
[https://codezym.com/question/394-ludo-board-game](https://codezym.com/question/394-ludo-board-game)


Store each token's progress as one relative position, then convert it to a shared board square only when checking a capture. A dictionary keeps games separate, and a small `Game` dataclass holds one match. No design pattern is needed for these fixed rules, ordinary classes and helper methods keep the solution clear.


## Start simple, then improve the lookup

A first version could search a list of games on every call. That gets slower as more games are created.

Use a dictionary from `gameId` to `Game` for constant-time lookup on average.

Inside one game, keep the scans. There are at most 16 tokens, so indexing occupied squares adds little benefit.


## Store only the state we need

Each token stores its relative position:

- `-1` means yard.
- `0` through `51` mean common track.
- `52` through `57` mean private home lane.
- `58` means finished.

`Game` stores the player order, a list of lists of positions, the turn index and the winner. Copy the player list so later caller changes cannot alter the order.

The first index selects a player. The second is `tokenNumber - 1`. The list comprehension creates a separate row for every player.

`LudoGame` owns the games and a set of safe absolute squares for quick safety checks.


## Decide whether an action is legal

Return `GAME_OVER` if there is a winner. Otherwise, reject a player whose turn it is not. Do this before changing anything.

The `canMove` helper applies three rules:

- A token in the yard needs a six.
- A finished token cannot move.
- Every other token can move only if its position plus the dice value is at most `58`.

For token number `0`, use this helper on all four tokens. Reject the pass if any token can move. Otherwise, accept it.

For a selected token, reject an illegal move before updating it. A six from the yard places it at `0`. All later moves add the dice value.


## Compare absolute squares when capturing

Players can share a square while having different relative positions. For player index `i` and track position `p`, calculate:

```text
absolute square = (13 * i + p) % 52
```

With players `[alpha, beta]`, alpha at relative position `14` and beta at relative position `1` both occupy absolute square `14`. Beta landing there captures alpha.

On a non-safe landing square, return every matching opposing token to `-1`. Do not stop after the first capture. Friendly tokens and tokens passed along the way stay in place.

Skip captures on safe squares, in home lanes and at the finish. Home lanes belong to individual players, so never compare them using the common-track formula.


## Finish the move and update the turn

Reaching exactly `58` returns `TOKEN_FINISHED`. If all four tokens are now at `58`, record the winner and return `PLAYER_WON` instead.

A capture returns `MOVED_AND_CAPTURED`. Other moves return `MOVED`. A valid pass returns `PASSED`.

After a valid move or pass, keep the turn for a six. Otherwise, advance the turn index with wraparound. Capturing or finishing alone gives no extra turn. Consecutive sixes have no penalty.

Invalid actions return `INVALID_MOVE` and leave everything unchanged.


## Return the state in the required order

Return a new list containing the turn and winner, then the tokens in player order and token-number order. Derive each token's location from its position.

The reported position stays relative. After a win, report `turn,NONE` and the winner's identifier.


## Why plain classes fit this problem

Strategy would help with selectable movement rules. Here, one fixed rule set makes separate implementations unnecessary.

State classes for each location would add several classes. The position already tells us the location and allowed movement.


## Complexity

With `P` players, creating a game, scanning for captures and returning state take `O(P)` time. A turn uses `O(1)` extra space. Game lookup is `O(1)` on average.

For `G` games with up to `P` players each, storage is `O(G * P)`. A snapshot uses `O(P)` space. Since `P` is at most four, each API call does constant game work.


## Python implementation

```python
from dataclasses import dataclass, field
from typing import List, Optional


@dataclass
class Game:
    """Holds the players, token positions, turn and winner of one game."""

    playerIds: List[str]
    positions: List[List[int]] = field(init=False)
    turnIndex: int = 0
    winner: Optional[str] = None

    def __post_init__(self):
        # Copy the order so outside changes cannot affect the game.
        self.playerIds = list(self.playerIds)
        self.positions = [[-1] * 4 for _ in self.playerIds]


class LudoGame:
    """Manages independent Ludo games using the supplied dice values."""

    def __init__(self):
        self.games = {}
        self.safePositions = {0, 8, 13, 21, 26, 34, 39, 47}

    def createGame(self, gameId, playerIds):
        self.games[gameId] = Game(playerIds)

    def playTurn(self, gameId, playerId, diceValue, tokenNumber):
        game = self.games[gameId]
        if game.winner is not None:
            return "GAME_OVER"

        playerIndex = game.turnIndex
        if game.playerIds[playerIndex] != playerId:
            return "INVALID_MOVE"

        # A pass is allowed only when all four tokens are unable to move.
        if tokenNumber == 0:
            for position in game.positions[playerIndex]:
                if self.canMove(position, diceValue):
                    return "INVALID_MOVE"
            self.advanceTurn(game, diceValue)
            return "PASSED"

        tokenIndex = tokenNumber - 1
        position = game.positions[playerIndex][tokenIndex]
        if not self.canMove(position, diceValue):
            return "INVALID_MOVE"

        # Entering from the yard consumes the six and places the token at zero.
        newPosition = 0 if position == -1 else position + diceValue
        game.positions[playerIndex][tokenIndex] = newPosition

        result = "MOVED"
        if newPosition == 58:
            if self.hasWon(game, playerIndex):
                game.winner = playerId
                return "PLAYER_WON"
            result = "TOKEN_FINISHED"
        elif self.captureOpponents(game, playerIndex, newPosition):
            result = "MOVED_AND_CAPTURED"

        self.advanceTurn(game, diceValue)
        return result

    def canMove(self, position, diceValue):
        if position == -1:
            return diceValue == 6
        return position < 58 and position + diceValue <= 58

    def absolutePosition(self, playerIndex, position):
        return (13 * playerIndex + position) % 52

    def captureOpponents(self, game, playerIndex, position):
        # Home lanes and finished positions are outside the common track.
        if position > 51:
            return False

        square = self.absolutePosition(playerIndex, position)
        if square in self.safePositions:
            return False

        captured = False
        for opponentIndex in range(len(game.playerIds)):
            if opponentIndex == playerIndex:
                continue
            for tokenIndex in range(4):
                opponentPosition = game.positions[opponentIndex][tokenIndex]
                if (
                    0 <= opponentPosition <= 51
                    and self.absolutePosition(opponentIndex, opponentPosition) == square
                ):
                    # Capture every opposing token on this square.
                    game.positions[opponentIndex][tokenIndex] = -1
                    captured = True
        return captured

    def hasWon(self, game, playerIndex):
        for position in game.positions[playerIndex]:
            if position != 58:
                return False
        return True

    def advanceTurn(self, game, diceValue):
        if diceValue != 6:
            game.turnIndex = (game.turnIndex + 1) % len(game.playerIds)

    def getGameState(self, gameId):
        game = self.games[gameId]
        currentPlayer = (
            game.playerIds[game.turnIndex] if game.winner is None else "NONE"
        )
        winner = "NONE" if game.winner is None else game.winner
        result = [f"turn,{currentPlayer}", f"winner,{winner}"]

        # Iterate the stored order instead of depending on map iteration order.
        for playerIndex, playerId in enumerate(game.playerIds):
            for tokenIndex in range(4):
                position = game.positions[playerIndex][tokenIndex]
                result.append(
                    f"{playerId},{tokenIndex + 1},{self.location(position)},{position}"
                )
        return result

    def location(self, position):
        if position == -1:
            return "YARD"
        if position <= 51:
            return "TRACK"
        if position <= 57:
            return "HOME"
        return "FINISHED"
```
