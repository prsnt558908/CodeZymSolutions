# Router Reachability on Broadcast and Shutdown Message in Python

#### Problem Statement
[https://codezym.com/question/149-router-reachability-broadcast-shutdown-message](https://codezym.com/question/149-router-reachability-broadcast-shutdown-message)

The message spreads like a wave. A working router passes it to every working router in range, and then shuts down. So the real question is: can we hop from the source to the destination, using only working routers that are in range of each other?

That is a graph reachability problem, and BFS (breadth first search) solves it. The shutdown rule only means that every router is handled once, which BFS does anyway.

We start with a simple BFS that compares every pair of routers. Then we make it fast by splitting the map into small grid cells, so a router only looks at routers near it.

## Key Observations

- **Defective routers never take part.** They can't send or receive, so we leave them out. If the source or the destination is defective, the answer is `False`.

- **No square roots needed.** Two routers can talk if `dx * dx + dy * dy <= range * range`. These values can reach 8 × 10^12, which is fine because Python integers never overflow.

## Solution 1: Brute Force BFS

First, each router string is parsed once into a small `Router` object with `id`, `x`, `y` and `working`. Then we find `start` (the source) and `target` (the destination). If either one is defective, we return `False`.

Now run BFS. Put the source in a queue. Take a router out and compare it with every router in the network. If the other router is working, has not received the message yet, and is in range, it receives the message and joins the queue.

```python
received = {start}
queue = deque([start])

while queue:
    current = queue.popleft()
    if current is target:
        return True
    # Compare with every router in the network
    for other in routerList:
        if other.working and other not in received and self.isInRange(current, other, rangeSquared):
            # It gets the message once, then it shuts down
            received.add(other)
            queue.append(other)
return False
```

```python
def isInRange(self, a: Router, b: Router, rangeSquared: int) -> bool:
    """Compares squared distances, so there are no square roots and no rounding errors."""
    dx = a.x - b.x
    dy = a.y - b.y
    return dx * dx + dy * dy <= rangeSquared
```

**Problem:** every router that gets the message is compared with all `n` routers. That is O(n²). With 100,000 routers it can be 10^10 checks, which is far too slow.

Most of these checks are wasted. A router can only reach routers close to it. So we need a quick way to find "routers near me".

## Solution 2: BFS over Grid Cells

### Step 1: Split the map into small cells

Cut the map into square cells. Each side is half the range, rounded up, and at least 1.

```python
cellSize = max(1, (range + 1) // 2)
```

A router at `(x, y)` goes to cell `(x // cellSize, y // cellSize)`. Python's `//` always rounds down, even for negative numbers (`-1 // 3` is `-1`), so every cell has the same size.

This cell size gives us two useful facts.

**Fact 1: all routers inside one cell can talk to each other.** Coordinates are whole numbers, so two routers in the same cell are at most `cellSize - 1` apart on each axis. That is at most `range / 2`. So their distance is at most about `0.71 × range`.

This means that as soon as one router of a cell gets the message, the whole cell gets it. We can treat a whole cell like one big router.

**Fact 2: a router in range is at most 2 cells away on each axis.** This is because `range <= 2 × cellSize`. So a cell only needs to look at the 5 × 5 block of cells around it.

Why not use cells with side `range`? Then only 9 cells would matter, but two routers in the same cell could be out of range. We would lose Fact 1, and crowded cells would get checked again and again.

### Step 2: BFS over cells

The grid is a dictionary from a cell key, the tuple `(cellX, cellY)`, to its cell. Only cells that hold working routers are stored.

1. The source's cell gets the message first. If the destination is in the same cell, return `True`.
2. Take a cell out of the queue. Look at the 24 cells around it that are still in the grid.
3. If at least one of our routers can reach at least one router of a nearby cell, that cell gets the message. Remove it from the grid and add it to the queue.
4. If the destination's cell gets the message, return `True`. If the queue runs out, return `False`.

A cell is removed from the grid when it joins the queue. So it is never checked again, just like a router that has shut down.

### Step 3: Skip far routers with a box

One slow case is still left. Two crowded cells can sit close to each other without any pair in range. Think of 50,000 routers on one spot and 50,000 routers on a spot just out of range. Comparing every pair means 2.5 × 10^9 checks.

So each cell also keeps the smallest box that holds all its routers. Before comparing a router with the routers of a cell, we compare it with the box. If the box is out of range, every router inside is out of range too, and we skip the whole cell in one step.

The distance from a point to a box is simple. `dx` is how far the point is to the left or right of the box (0 if it is within the box's x span). `dy` is the same for up and down. Then we compare `dx * dx + dy * dy` with `range * range`.

### Walkthrough with Example 2

Routers `A(0,0)`, `B(3,4)`, `C(6,8)` and `range = 5`. So `cellSize = 3`.

| Router | Cell |
|---|---|
| A | (0, 0) |
| B | (1, 1) |
| C | (2, 2) |

- Start with cell `(0, 0)`. The destination `C` is in cell `(2, 2)`.
- Cell `(0, 0)` looks around. `A` to `B` is exactly 5, so cell `(1, 1)` gets the message. The box of cell `(2, 2)` is 10 away from `A`, so that cell is skipped for now.
- Cell `(1, 1)` looks around. `B` to `C` is exactly 5, so cell `(2, 2)` gets the message. It is the destination's cell, so we return `True`.

### Why these classes and data structures

- **`Router`**: parses `"routerId,x,y,status"` once, so the rest of the code works with plain fields.
- **`Cell`**: a group of routers that always share the message. It keeps its position (to find nearby cells) and its box (to skip far routers quickly).
- **`dict` grid**: coordinates go from -10^6 to 10^6, so a 2D list of cells would be huge. A dictionary stores only the cells that have routers, and finds a cell in O(1).
- **`deque`**: the BFS queue of cells that still have to broadcast. `popleft()` is O(1), unlike `list.pop(0)`.

### Complexity

- **Time:** parsing and building the grid takes O(n). Each cell enters the queue once and looks at 24 nearby cells, and the box test throws away far routers in O(1). For real inputs the whole search is close to O(n). A specially built input, where two crowded cells look close but never connect, can still force many router to router checks, so the strict worst case is O(n²).
- **Space:** O(n) for the routers and the grid.

### Python Code

```python
from collections import deque
from typing import List


class Router:
    """One router, parsed from a "routerId,x,y,status" string."""

    def __init__(self, description: str):
        parts = description.split(",")
        self.id = parts[0]
        self.x = int(parts[1])
        self.y = int(parts[2])
        self.working = parts[3] == "WORKING"


class Cell:
    """
    One square of the grid.
    It keeps its working routers and the smallest box that holds all of them.
    """

    def __init__(self, cellX: int, cellY: int):
        self.cellX = cellX
        self.cellY = cellY
        self.routers = []
        self.minX = float("inf")
        self.maxX = float("-inf")
        self.minY = float("inf")
        self.maxY = float("-inf")

    def add(self, router: Router):
        """Adds a router and grows the box so it still holds every router of the cell."""
        self.routers.append(router)
        self.minX = min(self.minX, router.x)
        self.maxX = max(self.maxX, router.x)
        self.minY = min(self.minY, router.y)
        self.maxY = max(self.maxY, router.y)


class RouterReachability:

    def __init__(self):
        pass

    def canReachDestination(self, routers: List[str], range: int, source: str, destination: str) -> bool:
        # Parse every router and keep it by its id
        routerById = {}
        for description in routers:
            router = Router(description)
            routerById[router.id] = router
        start = routerById[source]
        target = routerById[destination]

        # A defective router can neither start nor receive the message
        if not start.working or not target.working:
            return False

        # Half the range, rounded up, and at least 1.
        # With this size, all routers inside one cell can talk to each other.
        cellSize = max(1, (range + 1) // 2)
        rangeSquared = range * range

        # Put every working router into its cell. Defective routers are left out.
        grid = {}
        for router in routerById.values():
            if not router.working:
                continue
            # // rounds down, so negative coordinates also land in the right cell
            cellX = router.x // cellSize
            cellY = router.y // cellSize
            key = self.cellKey(cellX, cellY)
            if key not in grid:
                grid[key] = Cell(cellX, cellY)
            grid[key].add(router)

        startKey = self.cellKey(start.x // cellSize, start.y // cellSize)
        targetKey = self.cellKey(target.x // cellSize, target.y // cellSize)

        # The whole cell of the source gets the message right away
        if startKey == targetKey:
            return True

        # Cells that got the message and still have to broadcast it.
        # A cell leaves the grid when it joins the queue, so it is handled only once.
        queue = deque([grid.pop(startKey)])

        while queue:
            current = queue.popleft()

            # A router in range is at most 2 cells away on each axis.
            # The offsets are written out because the parameter "range" hides Python's range().
            for dx in (-2, -1, 0, 1, 2):
                for dy in (-2, -1, 0, 1, 2):
                    key = self.cellKey(current.cellX + dx, current.cellY + dy)
                    if key not in grid or not self.canReach(current, grid[key], rangeSquared):
                        continue
                    if key == targetKey:
                        return True
                    queue.append(grid.pop(key))
        return False

    def canReach(self, fromCell: Cell, toCell: Cell, rangeSquared: int) -> bool:
        """True if at least one router of fromCell is in range of at least one router of toCell."""
        for a in fromCell.routers:
            # Too far from the box means too far from every router inside it
            if not self.isBoxInRange(a, toCell, rangeSquared):
                continue
            for b in toCell.routers:
                if self.isInRange(a, b, rangeSquared):
                    return True
        return False

    def isBoxInRange(self, router: Router, cell: Cell, rangeSquared: int) -> bool:
        """Checks the distance from the router to the closest point of the cell's box."""
        dx = max(0, cell.minX - router.x, router.x - cell.maxX)
        dy = max(0, cell.minY - router.y, router.y - cell.maxY)
        return dx * dx + dy * dy <= rangeSquared

    def isInRange(self, a: Router, b: Router, rangeSquared: int) -> bool:
        """Compares squared distances, so there are no square roots and no rounding errors."""
        dx = a.x - b.x
        dy = a.y - b.y
        return dx * dx + dy * dy <= rangeSquared

    def cellKey(self, cellX: int, cellY: int) -> tuple:
        """Key of a grid cell, for example (-3, 7)."""
        return (cellX, cellY)
```