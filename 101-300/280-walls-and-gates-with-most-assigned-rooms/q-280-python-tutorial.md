# Walls and Gates with the Most Assigned Rooms in Python

#### Problem Statement
[https://codezym.com/question/280-walls-and-gates-with-most-assigned-rooms](https://codezym.com/question/280-walls-and-gates-with-most-assigned-rooms)

---

## Core Idea

The question asks, for every empty room, which gate is nearest to it. The trick that makes this easy is to flip the question around and ask instead, which rooms does a gate reach first.

Once we flip it, we can let every gate start spreading at the same moment, one step per round, like water rising from many taps at once. The first gate to touch a room becomes the owner of that room, because no other gate could have reached it in fewer steps.

That single sweep is a **multi source BFS**, and it is the only real "pattern" this problem needs. We pair it with one small structural choice: flatten the 2D grid into a 1D list so that each gate has a single integer id, which lets us store ownership in one plain integer per cell.

Two patterns that sound attractive here, a **Strategy** object for the tie-break rule and a **priority queue based Dijkstra**, both turn out to be extra weight. We will see why in a moment, after we look at how the answer falls out of the BFS ordering for free.

---

## Reading the Grid

Each input string is one row of comma separated values.

* `-1` is a wall that cannot be entered or crossed.
* `0` is a gate.
* `2147483647` is an empty room.

We must return every gate that owns the most rooms, written as `"row,column"`, sorted by row and then by column. Gates that own zero rooms still take part, so when no room is assigned to anybody, every gate ties at zero and all of them are returned. If the grid has no gates at all, the answer is an empty list.

---

## Solution 1: Brute Force, One BFS Per Room

The most direct reading of the statement gives the most direct code. For every empty room, walk outward level by level until we bump into a gate. If several gates appear at the same level, keep the one with the smaller row, then the smaller column. Add one to that gate's counter.

```
for every empty room:
    run a BFS outward from that room
    stop at the first level that contains one or more gates
    pick the smallest (row, column) among them and count it
```

This is correct, and it is also far too slow.

Each room's search can crawl the whole grid before it finds a gate, so the work is roughly `(rows * cols)` searches each costing `(rows * cols)` steps. At the limit of 500 by 500 that is about 62 billion steps.

The deeper problem is repeated work. Two rooms sitting next to each other rediscover almost exactly the same neighbourhood, and neither of them remembers anything for the other.

---

## Solution 2: One BFS Per Gate

Gates are usually fewer than rooms, so a natural next step is to reverse the search direction. Start a BFS from a gate, and for every cell it reaches record the distance and the gate that reached it. Repeat for the next gate, overwriting a cell only when the new distance is strictly smaller.

If we loop over the gates in row order and then column order, ties settle themselves. The earlier gate wrote the distance first, and a later gate needs a strictly smaller distance to take the cell away, so an equal distance never overwrites.

This is a real improvement when gates are rare, but it is still `gates * rows * cols`. A grid can be almost entirely gates, and then we are back to scanning 250,000 cells 250,000 times.

The inefficiency is easy to name: we walk the whole grid once per gate, when one walk should be enough.

---

## Solution 3: A Single Multi Source BFS

Instead of running the gates one after another, put all of them into the same queue before the search starts, each marked as owning itself. Then run one ordinary BFS.

```
queue starts with every gate, each owning itself
while the queue is not empty:
    pop a cell
    for each of its 4 neighbours:
        skip it if it is a wall or if someone already claimed it
        give it the same owner as the popped cell
        add one to that owner's room count
        push it to the back of the queue
```

Every cell is claimed exactly once and never re-examined, so the whole grid is processed in one pass.

### Why the first claim is always the nearest gate

Every move costs exactly one step. A FIFO queue therefore hands cells back in increasing order of distance: all cells at distance 0 come out first, then all cells at distance 1, and so on.

So when a room is claimed, it is being claimed from a cell that sits at the smallest possible distance from some gate. No later gate can beat it, which is why we never need to revisit or update a cell.

### Why the tie-break is free

This is the part worth slowing down for. When two gates are equally near a room, the room must go to the gate with the smaller row, and then the smaller column.

We get that without writing a single comparison, just by choosing the order in which gates enter the queue.

Scan the grid row by row and push gates in that order. At distance 0, the queue is already sorted by row and then column.

Now notice that neighbours are pushed in the order their parents are popped. The gates come out in sorted order, so the distance 1 cells enter the queue grouped by their owners in that same sorted order. Repeat the argument one level at a time and the property holds forever: **inside any distance level, cells leave the queue in ascending order of their owning gate.**

So if two gates can both reach a room in the same number of steps, the cell belonging to the smaller gate is popped first and takes the room.

A tiny example makes it concrete. Take the single row grid `["0,2147483647,0"]`.

```
gate(0,0)   room(0,1)   gate(0,2)
```

The room is one step from both gates. The queue starts as `[(0,0), (0,2)]`, so `(0,0)` is popped first and claims the room. Gate `(0,0)` ends with one room and gate `(0,2)` with zero, and the answer is `["0,0"]`, exactly as the tie-break rule demands.

### Why no extra design pattern helps

**Strategy for the tie-break.** It is tempting to wrap the rule "smaller row, then smaller column" into a comparison function and call it whenever two gates compete for the same room. It would work, and in a codebase where the rule changed often it would be the right call. Here it buys nothing, because seeding the queue in sorted order deletes the comparison entirely. A pattern whose only job is to run a rule we can remove is pure overhead, and it would sit inside the hottest loop in the program.

**Dijkstra with a priority queue.** Shortest distance plus a preference sounds like a job for a heap. But Dijkstra exists to handle edges of different costs, and here every move costs exactly one. A plain FIFO queue already produces cells in distance order, so `heapq` would add a `log` factor and a tuple per cell to achieve the ordering we already have.

What we do keep is the flattening trick, where cell `(r, c)` becomes index `r * cols + c`. It is small, but it is what lets a gate's identity live in a single integer, which in turn lets ownership, counting and the queue all be plain flat lists instead of nested structures.

---

## Data Structures and Why Each One Is There

**`cell`, the flattened grid list.** Storing the grid as one list instead of a list of lists turns every cell into a single number. Neighbour math stays simple integer arithmetic, and more importantly a gate's position becomes an id we can use directly as a list index.

**`owner`, one entry per cell.** It holds the flat index of the gate that claimed the cell, or `-1` when nobody has. Because "claimed" and "visited" mean the same thing in this BFS, this list doubles as the visited marker and we never need a separate one.

**`rooms_owned`, indexed by the gate's own flat index.** A dictionary keyed by `"row,col"` would also work, but we already have an integer id per gate, so a flat list is both simpler to read and faster, with no string building inside the loop.

**`deque` as the queue.** `popleft` on a `deque` is a constant time operation, while `pop(0)` on a normal list shifts every remaining element and would quietly turn the search quadratic. That single choice is what keeps the BFS linear.

**`gates`.** Collected during the row by row scan, so it is already in ascending row and then column order. That means the final answer needs no sorting, we just walk this list and keep the gates whose count equals the maximum.

---

## Edge Cases Handled

* **No gates in the grid.** We return an empty list before the BFS starts.
* **Gates with zero rooms.** The maximum is taken over all gates, so a maximum of zero is a valid outcome and every gate is returned.
* **Gate cells are not rooms.** Gates are claimed during the seeding step, so the BFS never claims one again, and the counter only increases when a brand new cell is claimed.
* **Rooms sealed off by walls.** They are never reached, so they stay unclaimed and count for nobody.

---

## Python Code

```python
from collections import deque


class WallsAndGatesMostAssignedRooms:
    """Assigns every reachable empty room to its nearest gate with a single
    multi source BFS, then returns the gate or gates owning the most rooms."""

    WALL = -1
    GATE = 0

    def __init__(self):
        pass

    def findMostCommonGates(self, grid):
        if not grid:
            return []

        # Flatten the comma separated rows into one row major list.
        rows = len(grid)
        cell = []
        for row in grid:
            cell.extend(int(v) for v in row.split(","))
        cols = len(cell) // rows

        # owner[i] = flat index of the gate that claimed cell i, -1 if unclaimed.
        # This also acts as the visited marker, so no separate list is needed.
        owner = [-1] * (rows * cols)

        # Room counter for each gate, indexed by the gate's own flat index.
        rooms_owned = [0] * (rows * cols)

        # Scanning row by row keeps gates sorted by row and then column,
        # which gives the tie-break rule and the output order for free.
        gates = []
        queue = deque()
        for i, value in enumerate(cell):
            if value == self.GATE:
                gates.append(i)
                owner[i] = i          # a gate owns itself at distance zero
                queue.append(i)
        if not gates:
            return []

        # Single BFS starting from all gates at the same time.
        while queue:
            current = queue.popleft()
            mine = owner[current]
            r, c = divmod(current, cols)
            for nr, nc in ((r - 1, c), (r + 1, c), (r, c - 1), (r, c + 1)):
                if nr < 0 or nr >= rows or nc < 0 or nc >= cols:
                    continue

                nxt = nr * cols + nc
                if cell[nxt] == self.WALL or owner[nxt] != -1:
                    continue

                # First gate to reach this cell keeps it forever.
                owner[nxt] = mine
                rooms_owned[mine] += 1
                queue.append(nxt)

        # Gates with zero rooms still compete, so the best count can be zero.
        best = max(rooms_owned[g] for g in gates)
        return ["%d,%d" % divmod(g, cols) for g in gates if rooms_owned[g] == best]
```

---

## Complexity

**Time: O(rows * cols).** Parsing touches every value once. In the BFS each cell is pushed and popped at most once, and each pop looks at four neighbours, so the search is linear in the number of cells. Collecting the answer is one more pass over the gates.

**Space: O(rows * cols).** Three flat lists of one entry per cell, for the grid, the owner and the counters, plus the queue.

---

## Summary

The whole solution rests on one change of viewpoint. Searching from rooms to gates repeats work, searching from one gate at a time repeats work, but searching from all gates at once does the job in a single pass.

The BFS queue then does two things for us at the same time. It orders cells by distance, which makes the first claim the nearest one, and it preserves the sorted order of the gates, which makes the tie-break rule happen on its own instead of being checked in code.