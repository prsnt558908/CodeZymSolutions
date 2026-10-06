# Design a Ludo Board Game in Java

#### Problem Statement
[https://codezym.com/question/394-ludo-board-game](https://codezym.com/question/394-ludo-board-game)


Store each token's progress as one relative position, then convert it to a shared board square only when checking a capture. A map keeps games separate, and a small `Game` class holds one match. No design pattern is needed for these fixed rules, ordinary classes and helper methods keep the solution clear.


## Start simple, then improve the lookup

A first version could search a list of games on every call. That gets slower as more games are created.

Use a map from `gameId` to `Game` for constant-time lookup on average.

Inside one game, keep the scans. There are at most 16 tokens, so indexing occupied squares adds little benefit.


## Store only the state we need

Each token stores its relative position:

- `-1` means yard.
- `0` through `51` mean common track.
- `52` through `57` mean private home lane.
- `58` means finished.

`Game` stores the player order, a two-dimensional array of positions, the turn index and the winner. Copy the player list so later caller changes cannot alter the order.

The first index selects a player. The second is `tokenNumber - 1`. Each row starts with four `-1` values.

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


## Java implementation

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Holds the players, token positions, turn and winner of one game. */
class Game {
    List<String> playerIds;
    int[][] positions;
    int turnIndex;
    String winner;

    Game(List<String> playerIds) {
        // Copy the order so outside changes cannot affect the game.
        this.playerIds = new ArrayList<>(playerIds);
        positions = new int[playerIds.size()][4];
        for (int[] playerPositions : positions) {
            Arrays.fill(playerPositions, -1);
        }
        turnIndex = 0;
        winner = null;
    }
}

/** Manages independent Ludo games using the supplied dice values. */
public class LudoGame {
    Map<String, Game> games;
    Set<Integer> safePositions;

    public LudoGame() {
        games = new HashMap<>();
        safePositions = new HashSet<>(
            Arrays.asList(0, 8, 13, 21, 26, 34, 39, 47)
        );
    }

    public void createGame(String gameId, List<String> playerIds) {
        games.put(gameId, new Game(playerIds));
    }

    public String playTurn(
        String gameId, String playerId, int diceValue, int tokenNumber
    ) {
        Game game = games.get(gameId);
        if (game.winner != null) {
            return "GAME_OVER";
        }

        int playerIndex = game.turnIndex;
        if (!game.playerIds.get(playerIndex).equals(playerId)) {
            return "INVALID_MOVE";
        }

        // A pass is allowed only when all four tokens are unable to move.
        if (tokenNumber == 0) {
            for (int position : game.positions[playerIndex]) {
                if (canMove(position, diceValue)) {
                    return "INVALID_MOVE";
                }
            }
            advanceTurn(game, diceValue);
            return "PASSED";
        }

        int tokenIndex = tokenNumber - 1;
        int position = game.positions[playerIndex][tokenIndex];
        if (!canMove(position, diceValue)) {
            return "INVALID_MOVE";
        }

        // Entering from the yard consumes the six and places the token at zero.
        int newPosition = position == -1 ? 0 : position + diceValue;
        game.positions[playerIndex][tokenIndex] = newPosition;

        String result = "MOVED";
        if (newPosition == 58) {
            if (hasWon(game, playerIndex)) {
                game.winner = playerId;
                return "PLAYER_WON";
            }
            result = "TOKEN_FINISHED";
        } else if (captureOpponents(game, playerIndex, newPosition)) {
            result = "MOVED_AND_CAPTURED";
        }

        advanceTurn(game, diceValue);
        return result;
    }

    boolean canMove(int position, int diceValue) {
        if (position == -1) {
            return diceValue == 6;
        }
        return position < 58 && position + diceValue <= 58;
    }

    int absolutePosition(int playerIndex, int position) {
        return (13 * playerIndex + position) % 52;
    }

    boolean captureOpponents(Game game, int playerIndex, int position) {
        // Home lanes and finished positions are outside the common track.
        if (position > 51) {
            return false;
        }

        int square = absolutePosition(playerIndex, position);
        if (safePositions.contains(square)) {
            return false;
        }

        boolean captured = false;
        for (int opponentIndex = 0;
             opponentIndex < game.playerIds.size(); opponentIndex++) {
            if (opponentIndex == playerIndex) {
                continue;
            }
            for (int tokenIndex = 0; tokenIndex < 4; tokenIndex++) {
                int opponentPosition = game.positions[opponentIndex][tokenIndex];
                if (opponentPosition >= 0 && opponentPosition <= 51
                    && absolutePosition(opponentIndex, opponentPosition) == square) {
                    // Capture every opposing token on this square.
                    game.positions[opponentIndex][tokenIndex] = -1;
                    captured = true;
                }
            }
        }
        return captured;
    }

    boolean hasWon(Game game, int playerIndex) {
        for (int position : game.positions[playerIndex]) {
            if (position != 58) {
                return false;
            }
        }
        return true;
    }

    void advanceTurn(Game game, int diceValue) {
        if (diceValue != 6) {
            game.turnIndex = (game.turnIndex + 1) % game.playerIds.size();
        }
    }

    public List<String> getGameState(String gameId) {
        Game game = games.get(gameId);
        List<String> result = new ArrayList<>();
        String currentPlayer = game.winner == null
            ? game.playerIds.get(game.turnIndex) : "NONE";
        String winner = game.winner == null ? "NONE" : game.winner;
        result.add("turn," + currentPlayer);
        result.add("winner," + winner);

        // Iterate the stored order instead of depending on map iteration order.
        for (int playerIndex = 0;
             playerIndex < game.playerIds.size(); playerIndex++) {
            String playerId = game.playerIds.get(playerIndex);
            for (int tokenIndex = 0; tokenIndex < 4; tokenIndex++) {
                int position = game.positions[playerIndex][tokenIndex];
                result.add(
                    playerId + "," + (tokenIndex + 1) + ","
                    + location(position) + "," + position
                );
            }
        }
        return result;
    }

    String location(int position) {
        if (position == -1) {
            return "YARD";
        }
        if (position <= 51) {
            return "TRACK";
        }
        if (position <= 57) {
            return "HOME";
        }
        return "FINISHED";
    }
}
```
