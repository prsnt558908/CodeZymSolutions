# Minimum Knight Moves on Infinite Chessboard in Python

#### Problem Statement
[https://codezym.com/question/250-min-knight-moves-chess](https://codezym.com/question/250-min-knight-moves-chess)


## Core Idea

Every knight move costs the same: exactly one move. So the problem is really asking for the **shortest path** from `(0, 0)` to `(x, y)`.


When every step costs the same, **Breadth First Search (BFS)** is the natural tool. It explores the board in rings: first every square 1 move away, then every square 2 moves away, and so on. The first time it reaches the target, we have the answer.


We start with a plain BFS and then improve it in two steps:

1. Use the **symmetry** of the board, so we only search one quarter of it.
2. Flip the direction. Start at the target and **walk back** to `(0, 0)`, remembering answers we already found. This visits only the squares between the target and the origin.


---

## Approach 1: Simple BFS (Brute Force)

A knight jumps 2 squares along one axis and 1 square along the other. That gives 8 possible moves:

```python
moves = [
    (1, 2), (2, 1), (2, -1), (1, -2),
    (-1, -2), (-2, -1), (-2, 1), (-1, 2),
]
```


We start at `(0, 0)` and process the board level by level. Level `k` holds every square that is exactly `k` moves away.


We need two data structures:

- **Queue (`deque`)**: it hands out squares in the same order we found them. So every square at level `k` is processed before any square at level `k + 1`. We use `deque` because `popleft()` is fast, while `list.pop(0)` has to shift every element.

- **Set of visited squares**: without it, the knight keeps jumping back and forth between the same squares and the queue blows up in size. We store each square as an `(x, y)` tuple. A set of tuples works fine with negative coordinates, which a plain 2D list cannot handle without extra offset math.

```python
queue = deque([(0, 0)])
visited = {(0, 0)}

steps = 0
while queue:
    for _ in range(len(queue)):
        cur_x, cur_y = queue.popleft()
        if cur_x == x and cur_y == y:
            return steps

        for dx, dy in moves:
            next_x, next_y = cur_x + dx, cur_y + dy
            if (next_x, next_y) not in visited:
                visited.add((next_x, next_y))
                queue.append((next_x, next_y))
    steps += 1
```


### The problem

BFS spreads out evenly in **all four directions**. But the target sits in just one quarter of the board, so about 3/4 of the work is wasted.


For the target `(300, 0)`, this BFS visits about **316,000** squares.


---

## Approach 2: BFS on One Quarter of the Board

### Using symmetry

The board looks the same if we flip it left to right or top to bottom. So reaching `(x, y)` takes exactly as many moves as reaching `(-x, y)`, `(x, -y)` or `(-x, -y)`.


This means we can replace the target with `(|x|, |y|)`. It always sits in the top-right quarter, so that is the only quarter BFS needs to explore.


### One small catch

If we block **every** negative square, the answer for `(1, 1)` comes out wrong.


The real shortest path is `(0, 0) -> (2, -1) -> (1, 1)`, which is 2 moves. It dips to `y = -1` for one step.


With negative squares blocked, the best BFS can find is `(0, 0) -> (2, 1) -> (4, 2) -> (3, 0) -> (1, 1)`, which is 4 moves.


So we allow a border of **one square** outside the quarter: `x >= -1` and `y >= -1`. This small border is enough for every target in this problem.


For `(300, 0)`, this BFS visits about **80,000** squares, around 4 times fewer than before.


### Code

```python
from collections import deque


class MinimumKnightMoves:

    def __init__(self):
        # all 8 ways a knight can jump: 2 squares on one axis, 1 on the other
        self.moves = [
            (1, 2), (2, 1), (2, -1), (1, -2),
            (-1, -2), (-2, -1), (-2, 1), (-1, 2),
        ]

    def minKnightMoves(self, x: int, y: int) -> int:
        # the board is symmetric, so (x, y) and (|x|, |y|) need the same moves
        x, y = abs(x), abs(y)

        queue = deque([(0, 0)])
        # squares already added to the queue, stored as (x, y) tuples
        visited = {(0, 0)}

        steps = 0
        while queue:
            # every square in the queue right now is exactly 'steps' moves away
            for _ in range(len(queue)):
                cur_x, cur_y = queue.popleft()
                if cur_x == x and cur_y == y:
                    return steps

                for dx, dy in self.moves:
                    next_x, next_y = cur_x + dx, cur_y + dy

                    # stay in the top-right quarter, plus a 1 square border
                    # because some short paths near the axes dip below them
                    if next_x < -1 or next_y < -1:
                        continue

                    if (next_x, next_y) not in visited:
                        visited.add((next_x, next_y))
                        queue.append((next_x, next_y))
            steps += 1

        return -1  # never reached, the target is always reachable
```


### What can still be better?

BFS still grows in rings around the origin, in every direction inside the quarter. While looking for `(300, 0)`, it also visits squares like `(0, 290)`, which are nowhere near the target.


---

## Approach 3: Walk Back from the Target (Recursion + Memoization)

### Flip the question

Instead of spreading out from the origin, start at the target and ask: **what is the fewest number of moves to bring the knight home to `(0, 0)`?**


The knight is at `(x, y)` in the top-right quarter, so home is down and to the left. Only 2 of the 8 moves go both down and left. They take the knight to:

- `(x - 1, y - 2)`
- `(x - 2, y - 1)`


Except for a few squares right next to the origin (we handle those below), a move that goes up or right never gets the knight home faster. So we try only these 2 moves and keep the better one:

```
countMoves(x, y) = 1 + min(countMoves(|x - 1|, |y - 2|), countMoves(|x - 2|, |y - 1|))
```


### Why the absolute value?

Near an axis, a down-left move can cross it. For example, from `(1, 0)` the move `(x - 1, y - 2)` lands on `(0, -2)`.


By symmetry, `(0, -2)` needs exactly as many moves as `(0, 2)`. So we flip it back with `abs()`, and the knight stays in the top-right quarter. This is how the walk back still finds paths that dip below an axis.


### Base cases

Very close to the origin, the down-left rule can go in circles. From `(1, 1)` it goes to `(0, 1)`, and from `(0, 1)` it comes right back to `(1, 1)`.


So we answer the squares nearest to home directly:

- `(0, 0)` needs 0 moves.
- `(1, 1)`, `(2, 0)` and `(0, 2)` each need exactly 2 moves. All three have `x + y == 2`, so one check covers them.


### Why memoization?

Each call makes 2 more calls, so without help the number of calls doubles at every level.


But many paths land on the same square. From `(x, y)`, the paths "option 1 then option 2" and "option 2 then option 1" both reach `(x - 3, y - 3)`.


So we save every answer in a **dictionary** with the key `(x, y)`. Each square is solved only once. The dictionary lives in the class, so later calls to `minKnightMoves` can reuse answers from earlier calls too.


The recursion goes about 300 calls deep in the worst case, well within Python's default limit of 1000.


### Quick walkthrough: target `(4, 0)`

- `(4, 0)` tries `(3, 2)` and `(2, 1)`.
- `(2, 1)` tries `(1, 1)` (2 moves) and `(0, 0)` (0 moves). So `(2, 1)` needs `1 + 0 = 1` move.
- `(3, 2)` tries `(2, 0)` (2 moves) and `(1, 1)` (2 moves). So `(3, 2)` needs `1 + 2 = 3` moves.
- So `(4, 0)` needs `1 + min(3, 1) = 2` moves.


For `(300, 0)`, the walk back visits fewer than **900** squares.


### Code

```python
class MinimumKnightMoves:

    def __init__(self):
        # answers we already found, key is (x, y), value is the minimum moves
        self.memo = {}

    def minKnightMoves(self, x: int, y: int) -> int:
        # the board is symmetric, so only the top-right quarter matters
        return self.countMoves(abs(x), abs(y))

    def countMoves(self, x: int, y: int) -> int:
        """Minimum moves to bring the knight from (x, y) back to (0, 0).

        x and y are never negative here.
        """
        if x + y == 0:  # already home
            return 0
        if x + y == 2:  # (1, 1), (2, 0) and (0, 2)
            return 2

        if (x, y) in self.memo:
            return self.memo[(x, y)]

        # try the two moves that go down and left, towards (0, 0)
        # abs() flips the knight back if it crosses an axis
        option1 = self.countMoves(abs(x - 1), abs(y - 2))
        option2 = self.countMoves(abs(x - 2), abs(y - 1))
        result = min(option1, option2) + 1

        self.memo[(x, y)] = result
        return result
```


---

## Complexity

Let `X = |x|`, `Y = |y|` and `N = max(X, Y)`.

| Approach | Time | Space |
|---|---|---|
| Simple BFS | O(N²) | O(N²) |
| BFS on one quarter | O(N²), about 4 times less work | O(N²) |
| Walk back with memo | O(X · Y) | O(X · Y) |


BFS visits every square that is closer to the origin than the target. That area grows with the square of the distance, so it is O(N²).


The walk back only visits squares inside the rectangle from `(0, 0)` to `(X + 2, Y + 2)`, and solves each one only once.