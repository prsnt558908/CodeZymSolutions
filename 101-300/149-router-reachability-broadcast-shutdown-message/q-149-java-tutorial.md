# Router Reachability on Broadcast and Shutdown Message in Java

#### Problem Statement
[https://codezym.com/question/149-router-reachability-broadcast-shutdown-message](https://codezym.com/question/149-router-reachability-broadcast-shutdown-message)

The message spreads like a wave. A working router passes it to every working router in range, and then shuts down. So the real question is: can we hop from the source to the destination, using only working routers that are in range of each other?

That is a graph reachability problem, and BFS (breadth first search) solves it. The shutdown rule only means that every router is handled once, which BFS does anyway.

We start with a simple BFS that compares every pair of routers. Then we make it fast by splitting the map into small grid cells, so a router only looks at routers near it.

## Key Observations

- **Defective routers never take part.** They can't send or receive, so we leave them out. If the source or the destination is defective, the answer is `false`.

- **No square roots needed.** Two routers can talk if `dx * dx + dy * dy <= range * range`. Coordinates go up to 10^6, so these values can reach 8 × 10^12. We use `long`.

## Solution 1: Brute Force BFS

First, each router string is parsed once into a small `Router` object with `id`, `x`, `y` and `working`. Then we find `start` (the source) and `target` (the destination). If either one is defective, we return `false`.

Now run BFS. Put the source in a queue. Take a router out and compare it with every router in the network. If the other router is working, has not received the message yet, and is in range, it receives the message and joins the queue.

```java
Set<Router> received = new HashSet<>();
Queue<Router> queue = new ArrayDeque<>();
received.add(start);
queue.add(start);

while (!queue.isEmpty()) {
    Router current = queue.poll();
    if (current == target) {
        return true;
    }
    // Compare with every router in the network
    for (Router other : routerList) {
        if (other.working && !received.contains(other) && isInRange(current, other, rangeSquared)) {
            // It gets the message once, then it shuts down
            received.add(other);
            queue.add(other);
        }
    }
}
return false;
```

```java
/** Compares squared distances, so there are no square roots and no rounding errors. */
boolean isInRange(Router a, Router b, long rangeSquared) {
    long dx = a.x - b.x;
    long dy = a.y - b.y;
    return dx * dx + dy * dy <= rangeSquared;
}
```

**Problem:** every router that gets the message is compared with all `n` routers. That is O(n²). With 100,000 routers it can be 10^10 checks, which is far too slow.

Most of these checks are wasted. A router can only reach routers close to it. So we need a quick way to find "routers near me".

## Solution 2: BFS over Grid Cells

### Step 1: Split the map into small cells

Cut the map into square cells. Each side is half the range, rounded up, and at least 1.

```java
int cellSize = Math.max(1, (range + 1) / 2);
```

A router at `(x, y)` goes to cell `(floorDiv(x, cellSize), floorDiv(y, cellSize))`. `Math.floorDiv` rounds down even for negative numbers, so every cell has the same size. (Plain `/` would round `-1 / 3` to `0`, and cell `0` would become almost twice as wide.)

This cell size gives us two useful facts.

**Fact 1: all routers inside one cell can talk to each other.** Coordinates are whole numbers, so two routers in the same cell are at most `cellSize - 1` apart on each axis. That is at most `range / 2`. So their distance is at most about `0.71 × range`.

This means that as soon as one router of a cell gets the message, the whole cell gets it. We can treat a whole cell like one big router.

**Fact 2: a router in range is at most 2 cells away on each axis.** This is because `range <= 2 × cellSize`. So a cell only needs to look at the 5 × 5 block of cells around it.

Why not use cells with side `range`? Then only 9 cells would matter, but two routers in the same cell could be out of range. We would lose Fact 1, and crowded cells would get checked again and again.

### Step 2: BFS over cells

The grid is a map from a cell key (like `"-3,7"`) to its cell. Only cells that hold working routers are stored.

1. The source's cell gets the message first. If the destination is in the same cell, return `true`.
2. Take a cell out of the queue. Look at the 24 cells around it that are still in the grid.
3. If at least one of our routers can reach at least one router of a nearby cell, that cell gets the message. Remove it from the grid and add it to the queue.
4. If the destination's cell gets the message, return `true`. If the queue runs out, return `false`.

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
- Cell `(1, 1)` looks around. `B` to `C` is exactly 5, so cell `(2, 2)` gets the message. It is the destination's cell, so we return `true`.

### Why these classes and data structures

- **`Router`**: parses `"routerId,x,y,status"` once, so the rest of the code works with plain fields.
- **`Cell`**: a group of routers that always share the message. It keeps its position (to find nearby cells) and its box (to skip far routers quickly).
- **`HashMap<String, Cell>` grid**: coordinates go from -10^6 to 10^6, so a 2D array of cells would be huge. A map stores only the cells that have routers, and finds a cell in O(1).
- **`ArrayDeque<Cell>`**: the BFS queue of cells that still have to broadcast.

### Complexity

- **Time:** parsing and building the grid takes O(n). Each cell enters the queue once and looks at 24 nearby cells, and the box test throws away far routers in O(1). For real inputs the whole search is close to O(n). A specially built input, where two crowded cells look close but never connect, can still force many router to router checks, so the strict worst case is O(n²).
- **Space:** O(n) for the routers and the grid.

### Java Code

```java
import java.util.*;

/**
 * One router, parsed from a "routerId,x,y,status" string.
 */
class Router {
    String id;
    int x;
    int y;
    boolean working;

    Router(String description) {
        String[] parts = description.split(",");
        id = parts[0];
        x = Integer.parseInt(parts[1]);
        y = Integer.parseInt(parts[2]);
        working = parts[3].equals("WORKING");
    }
}

/**
 * One square of the grid.
 * It keeps its working routers and the smallest box that holds all of them.
 */
class Cell {
    int cellX;
    int cellY;
    List<Router> routers = new ArrayList<>();
    int minX = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxY = Integer.MIN_VALUE;

    Cell(int cellX, int cellY) {
        this.cellX = cellX;
        this.cellY = cellY;
    }

    /** Adds a router and grows the box so it still holds every router of the cell. */
    void add(Router router) {
        routers.add(router);
        minX = Math.min(minX, router.x);
        maxX = Math.max(maxX, router.x);
        minY = Math.min(minY, router.y);
        maxY = Math.max(maxY, router.y);
    }
}

public class RouterReachability {

    public RouterReachability() {
    }

    public boolean canReachDestination(List<String> routers, int range, String source, String destination) {
        // Parse every router and keep it by its id
        Map<String, Router> routerById = new HashMap<>();
        for (String description : routers) {
            Router router = new Router(description);
            routerById.put(router.id, router);
        }
        Router start = routerById.get(source);
        Router target = routerById.get(destination);

        // A defective router can neither start nor receive the message
        if (!start.working || !target.working) {
            return false;
        }

        // Half the range, rounded up, and at least 1.
        // With this size, all routers inside one cell can talk to each other.
        int cellSize = Math.max(1, (range + 1) / 2);
        long rangeSquared = (long) range * range;

        // Put every working router into its cell. Defective routers are left out.
        Map<String, Cell> grid = new HashMap<>();
        for (Router router : routerById.values()) {
            if (!router.working) {
                continue;
            }
            // floorDiv rounds down, so negative coordinates also land in the right cell
            int cellX = Math.floorDiv(router.x, cellSize);
            int cellY = Math.floorDiv(router.y, cellSize);
            String key = cellKey(cellX, cellY);
            if (!grid.containsKey(key)) {
                grid.put(key, new Cell(cellX, cellY));
            }
            grid.get(key).add(router);
        }

        String startKey = cellKey(Math.floorDiv(start.x, cellSize), Math.floorDiv(start.y, cellSize));
        String targetKey = cellKey(Math.floorDiv(target.x, cellSize), Math.floorDiv(target.y, cellSize));

        // The whole cell of the source gets the message right away
        if (startKey.equals(targetKey)) {
            return true;
        }

        // Cells that got the message and still have to broadcast it.
        // A cell leaves the grid when it joins the queue, so it is handled only once.
        Queue<Cell> queue = new ArrayDeque<>();
        queue.add(grid.remove(startKey));

        while (!queue.isEmpty()) {
            Cell current = queue.poll();

            // A router in range is at most 2 cells away on each axis
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    String key = cellKey(current.cellX + dx, current.cellY + dy);
                    if (!grid.containsKey(key) || !canReach(current, grid.get(key), rangeSquared)) {
                        continue;
                    }
                    if (key.equals(targetKey)) {
                        return true;
                    }
                    queue.add(grid.remove(key));
                }
            }
        }
        return false;
    }

    /** True if at least one router of fromCell is in range of at least one router of toCell. */
    boolean canReach(Cell fromCell, Cell toCell, long rangeSquared) {
        for (Router a : fromCell.routers) {
            // Too far from the box means too far from every router inside it
            if (!isBoxInRange(a, toCell, rangeSquared)) {
                continue;
            }
            for (Router b : toCell.routers) {
                if (isInRange(a, b, rangeSquared)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Checks the distance from the router to the closest point of the cell's box. */
    boolean isBoxInRange(Router router, Cell cell, long rangeSquared) {
        long dx = Math.max(0, Math.max(cell.minX - router.x, router.x - cell.maxX));
        long dy = Math.max(0, Math.max(cell.minY - router.y, router.y - cell.maxY));
        return dx * dx + dy * dy <= rangeSquared;
    }

    /** Compares squared distances, so there are no square roots and no rounding errors. */
    boolean isInRange(Router a, Router b, long rangeSquared) {
        long dx = a.x - b.x;
        long dy = a.y - b.y;
        return dx * dx + dy * dy <= rangeSquared;
    }

    /** Text key of a grid cell, for example "-3,7". */
    String cellKey(int cellX, int cellY) {
        return cellX + "," + cellY;
    }
}
```