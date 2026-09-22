
# Design Snake and Ladder Game

#### Problem Statement
[https://codezym.com/question/130-design-snake-and-ladder-game](https://codezym.com/question/130-design-snake-and-ladder-game)

## Core Idea

A Snake and Ladder board looks complicated because it has two different things on it, snakes and ladders, but from a code's point of view they do the exact same job.

If a player lands on a snake's head, they get sent down to a lower cell. If a player lands on the bottom of a ladder, they get sent up to a higher cell. Either way, landing on one specific cell teleports the player to another cell. Once you see that, you do not need two separate ideas for snakes and ladders, you just need one lookup table that says "if you land here, you actually end up there."

The second thing to notice is that players always move in a fixed, repeating order. That is exactly what a queue is good at. Whoever is at the front of the queue is the current player. Once that player finishes their turn, they go to the back of the line and wait for their next chance.

This problem does not really need a full design pattern like State or Strategy. It might look like a fit, since the game moves between an "in progress" state and a "completed" state, and a dice roll needs some interpretation before it can be used. But writing separate State classes for two states, or a Strategy class for one simple sum the dice rule, adds extra files and indirection without making the logic any easier to follow. A single field for the winner, plus one small helper method that reads the dice values, gives the same correctness with far less machinery. That is the approach used here.

## A Simpler First Attempt

Before landing on the final design, it helps to see what a rougher first attempt looks like, and why it is worth improving.

A first instinct is usually to keep two separate maps, one for snakes and one for ladders. Every time a player lands on a cell, you would check the snake map first, and if that misses, check the ladder map too. This works, but it means writing and checking the same "did I land on a special cell" logic twice.

Turn order could also be tracked with a plain list of player ids and an index that moves forward with `(index + 1) % players.size()` after every turn. This also works, but modulo based index math is an easy place to make an off by one mistake, and it reads less naturally than simply saying "the current player is whoever is at the front of the line."

Neither of these choices is wrong, and both would pass the test cases. They are just more code than necessary. Merging the snake and ladder maps into one, and swapping the index counter for a queue, gives the same behavior in a simpler, easier to follow way.

## The Better Approach

The final design keeps track of four things.

**A jump map** (`Map<Integer, Integer>`) stores every snake and every ladder as one entry, the starting cell maps to the cell the player ends up on. A snake's head maps to its tail, and a ladder's start maps to its end. Since the problem guarantees no cell is the start of more than one snake or ladder, a plain map is enough, and one lookup after every move tells us whether the player needs to jump.

**A positions map** (`Map<String, Integer>`) stores the current cell of every player, keyed by player id. This gives instant access to any player's position without scanning through a list.

**A turn queue** (`Queue<String>`) stores player ids in the order they play. The player at the front is always the current player. After a valid turn that does not win the game, that player is removed from the front and added back at the end, so the next player is automatically at the front for the following turn.

**A winner field** (`String`) starts empty and is set the moment a player reaches cell `100`. Checking this single field is all the "game state" this problem really needs.

Every operation here, checking the current player, looking up a jump, reading a position, is a direct map or queue operation. That means a single turn runs in constant time, no matter how many turns have already been played.

## How a Turn Plays Out

Each call to `playTurn` follows the same steps.

First, check if the game is already won. If it is, return `"GAME COMPLETED"` right away and touch nothing else.

Next, check if the given player is actually the one at the front of the turn queue. If not, return `"INVALID MOVE"` and leave the game state exactly as it was.

Then work out the effective dice value. If the list is exactly three sixes, the value is `0` and the player does not move at all. Otherwise, the effective value is just the sum of the numbers in the list.

Add that value to the player's current cell. If the total goes past `100`, the player does not move, so their position stays the same. If the total lands exactly on a cell that is in the jump map, the player is immediately moved to the mapped cell, this is what makes a snake or a ladder trigger.

If the player's final cell is exactly `100`, they have won. Record them as the winner and return `"WIN"`.

Otherwise, move the current player from the front of the queue to the back, and return the result in the format `"playerId,cell,nextPlayerId,CONTINUE"`.

## Java Solution

```java
import java.util.*;

/**
 * Simulates a multi player Snake and Ladder game.
 *
 * Snakes and ladders are stored in a single map because they behave the same way at
 * runtime: landing on their starting cell moves the player to another cell.
 * Turn order is handled with a queue, the player at the front is always the current player.
 */
public class SnakeAndLadderGame {

    private static final int BOARD_SIZE = 100;
    private static final int START_CELL = 1;

    // Combined lookup for snakes (head -> tail) and ladders (start -> end).
    private final Map<Integer, Integer> jumps;

    // Current cell of every player, keyed by player id.
    private final Map<String, Integer> positions;

    // Player at the front of the queue is always the current player.
    private final Queue<String> turnOrder;

    private String winner;

    public SnakeAndLadderGame(List<String> playerIds, List<String> snakes, List<String> ladders) {
        this.jumps = new HashMap<>();
        this.positions = new HashMap<>();
        this.turnOrder = new LinkedList<>();
        this.winner = "";

        for (String playerId : playerIds) {
            positions.put(playerId, START_CELL);
            turnOrder.offer(playerId);
        }

        for (String snake : snakes) {
            String[] parts = snake.split(",");
            int head = Integer.parseInt(parts[0].trim());
            int tail = Integer.parseInt(parts[1].trim());
            jumps.put(head, tail);
        }

        for (String ladder : ladders) {
            String[] parts = ladder.split(",");
            int start = Integer.parseInt(parts[0].trim());
            int end = Integer.parseInt(parts[1].trim());
            jumps.put(start, end);
        }
    }

    public String playTurn(String playerId, List<Integer> diceValues) {
        if (!winner.isEmpty()) {
            return "GAME COMPLETED";
        }

        if (!playerId.equals(turnOrder.peek())) {
            return "INVALID MOVE";
        }

        int effectiveValue = calculateEffectiveDiceValue(diceValues);

        int currentPosition = positions.get(playerId);
        int newPosition = currentPosition + effectiveValue;

        if (newPosition > BOARD_SIZE) {
            // Overshooting the last cell means the player simply stays put.
            newPosition = currentPosition;
        } else if (jumps.containsKey(newPosition)) {
            // Landed on a snake head or a ladder start, jump immediately.
            newPosition = jumps.get(newPosition);
        }

        positions.put(playerId, newPosition);

        if (newPosition == BOARD_SIZE) {
            winner = playerId;
            return "WIN";
        }

        // Valid, non winning turn: move current player to the back of the line.
        turnOrder.poll();
        turnOrder.offer(playerId);

        return playerId + "," + newPosition + "," + turnOrder.peek() + ",CONTINUE";
    }

    public int getPlayerPosition(String playerId) {
        return positions.get(playerId);
    }

    public String getWinner() {
        return winner;
    }

    // Three consecutive sixes cancel the turn, every other case is a plain sum.
    private int calculateEffectiveDiceValue(List<Integer> diceValues) {
        boolean isTripleSix = diceValues.size() == 3
                && diceValues.get(0) == 6
                && diceValues.get(1) == 6
                && diceValues.get(2) == 6;

        if (isTripleSix) {
            return 0;
        }

        int sum = 0;
        for (int value : diceValues) {
            sum += value;
        }
        return sum;
    }
}
```
