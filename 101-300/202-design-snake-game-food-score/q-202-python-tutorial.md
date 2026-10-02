# Design Snake Game With Food And Score in Python

#### Problem Statement
[https://codezym.com/question/202-design-snake-game-food-score](https://codezym.com/question/202-design-snake-game-food-score)

A moving snake behaves exactly like a **queue**. On every move a new cell is added at the head, and one cell leaves from the tail (unless the snake just ate). So this problem is mostly about picking the right data structures:

- a **queue** that keeps the snake's cells in order, from tail to head.
- a **set** that holds the same cells, so we can instantly check if the snake is already on a cell.

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

Keep the snake in a list of `(row, col)` tuples. Index `0` is the tail and the last index is the head.

For each move:

1. Read the head (last cell) and compute the next cell.
2. If the next cell is outside the board, the game is over.
3. Parse the current food string and check if it sits on the next cell.
4. If the snake eats, add 1 to the score and point to the next food. Otherwise, remove the tail (index `0`).
5. Look through the whole list. If any cell equals the next cell, the snake hit itself.
6. Add the next cell at the end. It is the new head.

Step 4 comes **before** step 5 on purpose. When the snake is not eating, the tail leaves first, so the head is free to enter the old tail cell.

### Code

```python
class SnakeGame:
    def __init__(self, width, height, food):
        self.width = width
        self.height = height
        self.food = food
        self.food_index = 0  # index of the food currently on the board
        self.score = 0
        self.game_over = False

        # snake cells in order: tail at index 0, head at the last index
        self.snake = [(0, 0)]

    def move(self, direction):
        if self.game_over:
            return -1

        # 1. find the next head position
        row, col = self.snake[-1]
        if direction == "U":
            row -= 1
        elif direction == "D":
            row += 1
        elif direction == "L":
            col -= 1
        elif direction == "R":
            col += 1

        # 2. hitting the border ends the game
        if row < 0 or row >= self.height or col < 0 or col >= self.width:
            self.game_over = True
            return -1

        # 3. is the current food in the next cell?
        eats_food = False
        if self.food_index < len(self.food):
            food_row, food_col = self.food[self.food_index].split(",")
            eats_food = int(food_row) == row and int(food_col) == col

        # 4. eat the food (tail stays) or move the tail forward
        if eats_food:
            self.score += 1
            self.food_index += 1
        else:
            self.snake.pop(0)  # removes the tail, every other cell shifts one step left

        # 5. check every body cell, one by one
        if (row, col) in self.snake:
            self.game_over = True
            return -1

        # 6. the next cell becomes the new head
        self.snake.append((row, col))
        return self.score
```

### Why is this slow?

The answers are correct, but every move does extra work:

- **Self collision check:** `(row, col) in self.snake` visits every cell of the snake. The snake can grow to about 10,000 cells and there can be 10,000 moves, so this adds up to around 50 million checks in the worst case.
- **Moving the tail:** `pop(0)` on a list shifts every remaining cell one place to the left. That is another full pass over the snake.
- **Reading food:** the same food string is split and parsed again on every move until it is eaten.

So each move costs O(L), where L is the length of the snake.

**What about a 2D grid?** A grid of `True`/`False` values would make the collision check instant. But the board can be 10,000 x 10,000, which is 100 million cells. That is far too much memory, while the snake never covers more than about 10,000 of them. We only want to remember the cells the snake is actually on.

---

## Solution 2: Queue + Set (Optimal)

Let's fix each problem one by one.

### Fix 1: A queue for the snake's body

Watch how the body changes: the new head is always added at one end, and the tail always leaves from the other end. The first cell that went in is the first cell to come out. That is a **queue** (first in, first out).

In Python, the queue to use is `collections.deque`. `append` puts a cell at the back and `popleft` takes the tail from the front, both in O(1) with no shifting.

### Fix 2: A set for collision checks

A queue keeps the order, but finding a cell inside it still means scanning it. So we keep a **set** that holds exactly the same cells. A set answers `in` checks in O(1).

The two always change together. Every cell added to the deque is added to the set, and every cell removed from the deque is removed from the set.

### Fix 3: Tuples as cells, food parsed once

Python tuples are compared by value and can be stored in a set, so a cell is simply `(row, col)`. A check like `(1, 2) in occupied` works exactly as we want.

We also parse all food strings once in the constructor and store them as tuples, so checking for food is a single comparison. The head is always the last item of the deque, `body[-1]`.

### Steps of one move

1. If the game is already over, return `-1`.
2. Find the next head cell using the direction.
3. If it is outside the board, the game is over.
4. If the next cell has the current food, add 1 to the score and move to the next food (the tail stays). Otherwise, remove the tail from both the deque and the set.
5. If the set still contains the next cell, the snake hit itself and the game is over.
6. Add the next cell to the deque and the set. It is the new head.
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

```python
from collections import deque


class SnakeGame:
    def __init__(self, width, height, food):
        self.width = width
        self.height = height

        # parse every "row,column" string once, so move() never has to
        self.food = []
        for item in food:
            row, col = item.split(",")
            self.food.append((int(row), int(col)))
        self.food_index = 0  # index of the food currently on the board
        self.score = 0
        self.game_over = False

        self.body = deque([(0, 0)])  # snake cells in order: tail on the left, head on the right
        self.occupied = {(0, 0)}     # the same cells, for O(1) "is the snake here?" checks

    def move(self, direction):
        if self.game_over:
            return -1

        # 1. find the next head position
        row, col = self.body[-1]
        if direction == "U":
            row -= 1
        elif direction == "D":
            row += 1
        elif direction == "L":
            col -= 1
        elif direction == "R":
            col += 1

        # 2. hitting the border ends the game
        if row < 0 or row >= self.height or col < 0 or col >= self.width:
            self.game_over = True
            return -1

        next_cell = (row, col)
        eats_food = self.food_index < len(self.food) and self.food[self.food_index] == next_cell

        # 3. eat the food (tail stays, snake grows) or move the tail forward
        if eats_food:
            self.score += 1
            self.food_index += 1  # the next food appears now
        else:
            tail = self.body.popleft()  # tail leaves its cell before the head moves in
            self.occupied.remove(tail)

        # 4. hitting its own body ends the game
        if next_cell in self.occupied:
            self.game_over = True
            return -1

        # 5. move the head into the new cell
        self.body.append(next_cell)
        self.occupied.add(next_cell)
        return self.score
```

### Complexity

| | Solution 1 (List) | Solution 2 (Queue + Set) |
|---|---|---|
| Constructor | O(1) | O(F) |
| `move` | O(L) | O(1) |
| Extra space | O(L) | O(L + F) |

L is the length of the snake and F is the number of food items.