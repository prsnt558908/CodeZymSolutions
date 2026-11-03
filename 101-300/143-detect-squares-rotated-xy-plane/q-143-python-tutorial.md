# Detect Squares Rotated along the XY Plane in Python

#### Problem Statement
[https://codezym.com/question/143-detect-squares-rotated-xy-plane](https://codezym.com/question/143-detect-squares-rotated-xy-plane)


## Core Idea

We need to count squares where the query point is one corner and three stored points are the other three corners. The square can be tilted at any angle.


The main trick is simple: **one side is enough to know the whole square**. If we know the query point `A` and the corner `B` right after it, we turn the side `A -> B` by 90 degrees and directly get the last two corners `C` and `D`.


So we keep a hash map that counts how many times each point was added. For a query, we try each stored point as `B`, compute `C` and `D`, look them up in the map and multiply their copies. That is one loop, instead of trying every group of three points.


## Brute Force: Try Every Group of 3 Points

Store every added point in a list, copies included. For a query, try every group of 3 stored points and check if they make a square with the query point.

```python
self.points = []  # every added point, copies included

def count(self, point: List[int]) -> int:
    total = 0
    n = len(self.points)
    for i in range(n):
        for j in range(i + 1, n):
            for k in range(j + 1, n):
                if self.isSquare(point, self.points[i], self.points[j], self.points[k]):
                    total += 1
    return total
```

`isSquare()` compares the 6 squared distances between the 4 points. A square has 4 equal sides and 2 equal, longer diagonals.


Copies are separate items in the list, so duplicates are counted correctly.


**The problem:** one `count()` call checks about `n³ / 6` groups. With 3000 stored points, that is around 4.5 billion checks for just one query.


## Better Approach: Pick One Corner, Compute the Other Two

We improve the brute force in three small steps.


### Step 1: One side fixes the whole square

Call the query point `A`. Pick a stored point `B` and treat `A -> B` as one side of the square.


This side moves `dx = Bx - Ax` along x and `dy = By - Ay` along y.


Turn it 90 degrees to the left and `(dx, dy)` becomes `(-dy, dx)`. For example, `(1, 0)` pointing right becomes `(0, 1)` pointing up. The length stays the same.


This turned side is the next side of the square, so:

- `C = B + (-dy, dx)`
- `D = A + (-dy, dx)`


Here is Example 1, with `A = (0, 1)` and `B = (1, 0)`:

```
y
2   .   D   .
1   A   .   C
0   .   B   .
    0   1   2   x
```

- Side `A -> B` is `(1, -1)`. Turned left, it becomes `(1, 1)`.
- `C = (1, 0) + (1, 1) = (2, 1)`
- `D = (0, 1) + (1, 1) = (1, 2)`


All values are whole numbers, so tilted squares need no square roots or decimals.


Now we loop over **one** stored point instead of three.


### Step 2: Use a hash map to find corners and handle copies

Scanning the list to check if `C` and `D` exist would cost O(n) every time. So we keep a hash map (a dictionary) `point_count` from a point to how many times it was added. Each lookup is now O(1).


The map also takes care of duplicates. If `B`, `C` and `D` were added `b`, `c` and `d` times, we can pick one copy of each in `b * c * d` ways. So we loop over each **different** point only once and multiply.


The tuple `(x, y)` works directly as a dictionary key.


If `C` or `D` falls outside the grid, like `(-1, 2)`, it was never added, so the map simply returns 0.


### Step 3: Count each square only once

From side `A -> B`, a square could be built on its left or on its right.


But every square has two sides touching `A`. It sits on the left of one of them and on the right of the other. If we built both, each square would be counted twice.


So we only build the square on the left. Each square is then found exactly once.


We also skip `B` when it is on the same spot as `A` (this happens when the query point itself was added before), since that side has zero length.


## Dry Run (Example 2)

Stored points: `(1, 0)` once, `(2, 1)` twice, `(1, 2)` once. Query `A = (0, 1)`.

| B | Turned side | C | D | Ways |
|---|---|---|---|---|
| (1, 0) | (1, 1) | (2, 1): 2 copies | (1, 2): 1 copy | 1 × 2 × 1 = 2 |
| (2, 1) | (0, 2) | (2, 3): missing | (0, 3): missing | 0 |
| (1, 2) | (-1, 1) | (0, 3): missing | (-1, 2): missing | 0 |

The answer is `2`.


## Final Code

```python
from typing import List


class DetectSquares:

    def __init__(self):
        # point (x, y) -> how many times it was added
        self.point_count = {}

    def add(self, point: List[int]) -> None:
        key = (point[0], point[1])
        self.point_count[key] = self.point_count.get(key, 0) + 1

    def count(self, point: List[int]) -> int:
        """
        The query point is corner A. Every different stored point is tried as
        corner B, the corner right after A. Side A -> B, turned 90 degrees to the
        left, gives the last two corners C and D.
        """
        ax, ay = point[0], point[1]
        total = 0

        for (bx, by), count_b in self.point_count.items():
            # side A -> B
            dx = bx - ax
            dy = by - ay

            # B is on the same spot as A, so A -> B is not a real side
            if dx == 0 and dy == 0:
                continue

            # turned left, side (dx, dy) becomes (-dy, dx)
            # C = B + (-dy, dx) and D = A + (-dy, dx)
            count_c = self.frequency(bx - dy, by + dx)
            count_d = self.frequency(ax - dy, ay + dx)

            # pick one copy of B, one of C and one of D
            total += count_b * count_c * count_d

        return total

    def frequency(self, x: int, y: int) -> int:
        # how many times point (x, y) was added, 0 if never
        return self.point_count.get((x, y), 0)
```


## Complexity

- `add()`: O(1)
- `count()`: O(U), where `U` is the number of different stored points
- Space: O(U)

