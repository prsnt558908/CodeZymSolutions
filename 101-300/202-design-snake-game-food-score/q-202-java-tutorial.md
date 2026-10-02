# Design Snake Game With Food And Score in Java

#### Problem Statement
[https://codezym.com/question/202-design-snake-game-food-score](https://codezym.com/question/202-design-snake-game-food-score)

A moving snake behaves exactly like a **queue**. On every move a new cell is added at the head, and one cell leaves from the tail (unless the snake just ate). So this problem is mostly about picking the right data structures:

- a **queue** that keeps the snake's cells in order, from tail to head.
- a **hash set** that holds the same cells, so we can instantly check if the snake is already on a cell.

With these two, every move is just a few quick O(1) steps: find the next cell, check the border, check for food, move the tail, check if the snake bit itself, and place the new head.

We will first build a simple version that scans the whole snake on every move, see why it is slow, and then improve it step by step.

---

## Understanding the Rules

The board has `height` rows and `width` columns. The snake starts at `(0, 0)`, the top-left cell.

`U` and `D` change the row by -1 and +1. `L` and `R` change the column by -1 and +1.

On every move:

- If the head leaves the board, the game is over.
- If the head reaches the current food, the score goes up by 1 and the tail stays where it is. That is how the snake grows. Then the next food appears.
- Otherwise the tail moves away from its cell, so the length stays the same.
- If the head runs into the snake's own body, the game is over.
- Once the game is over, every move returns `-1`.

**The one tricky rule:** the head may move into the cell where the tail is right now (when it is not eating), because the tail leaves that cell in the same move.

---

## Solution 1: Simple List (Brute Force)

### Idea

Keep the snake in a list of `{row, col}` arrays. Index `0` is the tail and the last index is the head.

For each move:

1. Read the head (last cell) and compute the next cell.
2. If the next cell is outside the board, the game is over.
3. Parse the current food string and check if it sits on the next cell.
4. If the snake eats, add 1 to the score and point to the next food. Otherwise, remove the tail (index `0`).
5. Loop over the whole list. If any cell equals the next cell, the snake hit itself.
6. Add the next cell at the end. It is the new head.

Step 4 comes **before** step 5 on purpose. When the snake is not eating, the tail leaves first, so the head is free to enter the old tail cell.

### Code

```java
import java.util.*;

public class SnakeGame {
    int width, height;
    List<String> food;
    int foodIndex = 0;        // index of the food currently on the board
    int score = 0;
    boolean gameOver = false;

    // snake cells in order: tail at index 0, head at the last index. Each cell is {row, col}
    List<int[]> snake = new ArrayList<>();

    public SnakeGame(int width, int height, List<String> food) {
        this.width = width;
        this.height = height;
        this.food = food;
        snake.add(new int[]{0, 0});
    }

    public int move(String direction) {
        if (gameOver) return -1;

        // 1. find the next head position
        int[] head = snake.get(snake.size() - 1);
        int row = head[0], col = head[1];
        switch (direction) {
            case "U": row--; break;
            case "D": row++; break;
            case "L": col--; break;
            case "R": col++; break;
        }

        // 2. hitting the border ends the game
        if (row < 0 || row >= height || col < 0 || col >= width) {
            gameOver = true;
            return -1;
        }

        // 3. is the current food in the next cell?
        boolean eatsFood = false;
        if (foodIndex < food.size()) {
            String[] parts = food.get(foodIndex).split(",");
            int foodRow = Integer.parseInt(parts[0].trim());
            int foodCol = Integer.parseInt(parts[1].trim());
            eatsFood = (foodRow == row && foodCol == col);
        }

        // 4. eat the food (tail stays) or move the tail forward
        if (eatsFood) {
            score++;
            foodIndex++;
        } else {
            snake.remove(0);    // removes the tail, every other cell shifts one step left
        }

        // 5. check every body cell, one by one
        for (int[] cell : snake) {
            if (cell[0] == row && cell[1] == col) {
                gameOver = true;
                return -1;
            }
        }

        // 6. the next cell becomes the new head
        snake.add(new int[]{row, col});
        return score;
    }
}
```

### Why is this slow?

The answers are correct, but every move does extra work:

- **Self collision check:** step 5 visits every cell of the snake. The snake can grow to about 10,000 cells and there can be 10,000 moves, so this adds up to around 50 million checks in the worst case.
- **Moving the tail:** `remove(0)` on an `ArrayList` shifts every remaining cell one place to the left. That is another full pass over the snake.
- **Reading food:** the same food string is split and parsed again on every move until it is eaten.

So each move costs O(L), where L is the length of the snake.

**What about a 2D grid?** A `boolean[height][width]` grid would make the collision check instant. But the board can be 10,000 x 10,000, which is 100 million cells (about 100 MB), while the snake never covers more than about 10,000 of them. We only want to remember the cells the snake is actually on.

---

## Solution 2: Queue + Hash Set (Optimal)

Let's fix each problem one by one.

### Fix 1: A queue for the snake's body

Watch how the body changes: the new head is always added at one end, and the tail always leaves from the other end. The first cell that went in is the first cell to come out. That is a **queue** (first in, first out).

A queue adds at the back and removes from the front in O(1), with no shifting. In Java, a `LinkedList` works as a `Queue`: `add` puts a cell at the back and `poll` takes the tail from the front.

### Fix 2: A hash set for collision checks

A queue keeps the order, but finding a cell inside it still means scanning it. So we keep a **hash set** that holds exactly the same cells. A set answers `contains` in O(1).

The two always change together. Every cell added to the queue is added to the set, and every cell removed from the queue is removed from the set.

### Fix 3: One number for each cell

To put cells in a `HashSet`, we need a key that Java compares by value. An `int[]` will not work, because Java compares arrays by reference, so two `{1, 2}` arrays count as different keys.

Instead, we give every cell its own number, row by row, like seat numbers in a cinema:

```
cell = row * width + col
```

With `width = 4`, the cells are numbered like this:

```
row 0:   0   1   2   3
row 1:   4   5   6   7
row 2:   8   9  10  11
```

Every cell gets a different number, so a plain `Integer` works as a key. The biggest number is below 10,000 x 10,000 = 100 million, which fits easily in an `int`.

We compute this number only **after** the border check. Otherwise an outside cell like `(1, -1)` would become `3`, which is a real cell on the board.

We also parse all food strings once in the constructor and store them as these numbers, so checking for food is a single comparison. The head's row and column are kept in two variables, so we never need to turn a number back into a cell.

### Steps of one move

1. If the game is already over, return `-1`.
2. Find the next head cell using the direction.
3. If it is outside the board, the game is over.
4. If the next cell has the current food, add 1 to the score and move to the next food (the tail stays). Otherwise, remove the tail from both the queue and the set.
5. If the set still contains the next cell, the snake hit itself and the game is over.
6. Add the next cell to the queue and the set. It is the new head.
7. Return the score.

### Why the tail is removed before the collision check

Take a snake that fills a 2 x 2 board. The numbers show the order from tail (1) to head (4):

```
+---+---+
| 1 | 2 |
+---+---+
| 4 | 3 |
+---+---+
```

Moving `U` sends the head into cell 1, which is the tail. The snake is not eating, so the tail leaves at the same time and the move is valid. This snake can keep circling forever.

If we checked for a collision before removing the tail, we would wrongly end the game here.

### Walkthrough of Example 3

`width = 2`, `height = 2`, `food = ["0,1", "1,1"]`

| Move | Next cell | Eats? | Snake after the move (tail to head) | Returns |
|---|---|---|---|---|
| R | (0,1) | yes | (0,0) (0,1) | 1 |
| D | (1,1) | yes | (0,0) (0,1) (1,1) | 2 |
| L | (1,0) | no, tail (0,0) leaves | (0,1) (1,1) (1,0) | 2 |
| R | (1,1) | no, tail (0,1) leaves | (1,1) is still part of the body, game over | -1 |

### Code

```java
import java.util.*;

public class SnakeGame {
    int width, height;
    int[] foodCells;          // food positions in order, each stored as one number (see toCell)
    int foodIndex = 0;        // index of the food currently on the board
    int score = 0;
    boolean gameOver = false;

    int headRow = 0, headCol = 0;               // where the head is right now
    Queue<Integer> body = new LinkedList<>();   // snake cells in order: tail at the front, head at the back
    Set<Integer> occupied = new HashSet<>();    // the same cells, for O(1) "is the snake here?" checks

    public SnakeGame(int width, int height, List<String> food) {
        this.width = width;
        this.height = height;

        // parse every "row,column" string once, so move() never has to
        foodCells = new int[food.size()];
        for (int i = 0; i < food.size(); i++) {
            String[] parts = food.get(i).split(",");
            int row = Integer.parseInt(parts[0].trim());
            int col = Integer.parseInt(parts[1].trim());
            foodCells[i] = toCell(row, col);
        }

        // the snake starts at the top-left cell with length 1
        body.add(toCell(0, 0));
        occupied.add(toCell(0, 0));
    }

    public int move(String direction) {
        if (gameOver) return -1;

        // 1. find the next head position
        int row = headRow, col = headCol;
        switch (direction) {
            case "U": row--; break;
            case "D": row++; break;
            case "L": col--; break;
            case "R": col++; break;
        }

        // 2. hitting the border ends the game
        if (row < 0 || row >= height || col < 0 || col >= width) {
            gameOver = true;
            return -1;
        }

        int nextCell = toCell(row, col);
        boolean eatsFood = foodIndex < foodCells.length && foodCells[foodIndex] == nextCell;

        // 3. eat the food (tail stays, snake grows) or move the tail forward
        if (eatsFood) {
            score++;
            foodIndex++;                        // the next food appears now
        } else {
            occupied.remove(body.poll());       // tail leaves its cell before the head moves in
        }

        // 4. hitting its own body ends the game
        if (occupied.contains(nextCell)) {
            gameOver = true;
            return -1;
        }

        // 5. move the head into the new cell
        body.add(nextCell);
        occupied.add(nextCell);
        headRow = row;
        headCol = col;
        return score;
    }

    // numbers the cells row by row: (0,0) -> 0, (0,1) -> 1, ... (1,0) -> width, and so on
    int toCell(int row, int col) {
        return row * width + col;
    }
}
```

### Complexity

| | Solution 1 (List) | Solution 2 (Queue + Set) |
|---|---|---|
| Constructor | O(1) | O(F) |
| `move` | O(L) | O(1) |
| Extra space | O(L) | O(L + F) |

L is the length of the snake and F is the number of food items.