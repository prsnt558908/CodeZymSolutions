# Maximum Delivery Route Earnings in Python

#### Problem Statement
[https://codezym.com/question/485-max-delivery-route-earnings](https://codezym.com/question/485-max-delivery-route-earnings)

---

Every route in a tree has one **highest depot**, the one closest to the root. From that depot, the route can only go down into its left side, its right side, or both.


So the whole problem becomes: for every depot, find the best money we can collect by walking **straight down** from it. Once we know that for both children, scoring a depot as the "top" of a route is just one addition.


We will first build a simple version that recomputes these downward values again and again. Then we will improve it so that each value is computed only once, from the bottom of the tree up to the root. The final solution runs in `O(n)` time and uses no recursion.

---

## Understanding the Problem

Depots form a binary tree. Each depot has an amount. It can be positive (earning) or negative (cost).


We need to pick one route that:

- starts and ends at any depot
- follows the roads (tree edges)
- never visits a depot twice
- has at least one depot


We add all amounts on the route and return the **largest possible total**. The answer can be negative.

![Input list and delivery tree with the best route highlighted](https://d3jug84e56mcss.cloudfront.net/question_images/0/q-485-img-2-sm-100-80.jpg)

In Example 1, the best route is `8 → -3 → 5 → 7 → 6 = 23`. Note that it passes through `-3`, a cost, because it opens the door to `8`.

---

## Step 1: Building the Tree From the List

The input is a level order list like `["-15", "4", "10", "#", "#", "-2", "9", "7", "3"]`.


We read it like this:

- The first entry is the root.
- For each real depot, in level order, the next two entries are its left and right child.
- `"#"` means no child. Missing entries at the end also mean no child.


Instead of creating a `Node` class, we give every depot an **id**: `0, 1, 2 ...` in the order we read them. Then we keep three plain lists.

| List | Meaning |
|---|---|
| `value[i]` | amount at depot `i` |
| `left[i]` | id of left child, or `-1` |
| `right[i]` | id of right child, or `-1` |


**Why lists?** They are simple, fast and need no extra class. Ids also give us a very useful property for free:

> A child is always read **after** its parent, so a child's id is always **bigger** than its parent's id.

We will use this property in the optimal solution.

---

## Step 2: The Key Observation

Look at the best route of Example 1: `8 → -3 → 5 → 7 → 6`.


Its highest depot is `5`. From `5`:

- the left arm goes down: `-3 → 8`
- the right arm goes down: `7 → 6`


This is true for **every** route in a tree. There is one top depot, and at most two arms hanging down from it.


So let us define:

> `best_down(x)` = the best total of a route that **starts at depot `x` and only moves down**.


How do we find it?

```
best_down(x) = value[x] + max(0, best_down(left child), best_down(right child))
```

- We must include `x` itself.
- Then we may continue into **one** child, whichever gives more.
- If both children lose money, we stop at `x` (that is the `0`).


Why only one child? Because a parent above `x` will use `best_down(x)` as one of its arms. An arm is a straight line. It cannot split into two directions.


Now the best route where `x` is the **top** is:

```
route_with_top(x) = value[x] + max(0, best_down(left)) + max(0, best_down(right))
```

Here we may use **both** arms, because `x` is where the route turns.


The answer is the largest `route_with_top(x)` over all depots.


**What about all negative amounts?** In Example 3 (`-8, -2, -6`), every arm loses money, so both gains become `0`. Then `route_with_top(x)` is just `value[x]`, and the answer is the biggest single depot, `-2`. This matches the rule that a route needs at least one depot.

---

## Solution 1: Simple Approach (Recompute Every Time)

The most direct way is:

1. Try every depot as the top.
2. For each one, call a recursive `best_down` on its left and right child.


```python
import sys


class MaximumDeliveryRouteEarnings:
    def __init__(self):
        # value[i], left[i], right[i] describe depot i. -1 means "no child".
        self.value = []
        self.left = []
        self.right = []
        # deep trees need many nested calls
        sys.setrecursionlimit(100000)

    def bestRouteEarnings(self, depotEarnings):
        self.build_tree(depotEarnings)
        best = float("-inf")

        # try every depot as the highest point of the route
        for node in range(len(self.value)):
            left_gain = max(0, self.best_down(self.left[node]))
            right_gain = max(0, self.best_down(self.right[node]))
            best = max(best, self.value[node] + left_gain + right_gain)

        return best

    def best_down(self, node):
        """
        Best total of a route that starts at node and only moves down.
        Computed from scratch on every call, which is the slow part.
        """
        if node == -1:
            return 0
        left_gain = max(0, self.best_down(self.left[node]))
        right_gain = max(0, self.best_down(self.right[node]))
        return self.value[node] + max(left_gain, right_gain)

    def build_tree(self, depotEarnings):
        """Reads the level order list. Depots get ids 0, 1, 2 ... in level order."""
        self.value = [int(depotEarnings[0])]
        self.left = []
        self.right = []
        next_pos = 1  # index of the next entry to read

        # value grows while we read, so this visits depots in level order
        node = 0
        while node < len(self.value):
            self.left.append(self.read_child(depotEarnings, next_pos))
            self.right.append(self.read_child(depotEarnings, next_pos + 1))
            next_pos += 2
            node += 1

    def read_child(self, depotEarnings, pos):
        """Creates a depot from entry at index pos and returns its id, or -1 if missing."""
        if pos >= len(depotEarnings) or depotEarnings[pos] == "#":
            return -1
        self.value.append(int(depotEarnings[pos]))
        return len(self.value) - 1
```

### Problems With This Approach

This gives correct answers, but it has two big issues.


**1. It repeats the same work.** `best_down` of a depot is computed again for every depot above it. In a tree shaped like a long line of `30,000` depots, that is about `30,000 × 30,000 / 2 = 450 million` calls. In Python, that takes far too long.


**2. Deep recursion is risky.** In that same long line, `best_down` calls itself `30,000` levels deep. Python stops at about `1,000` nested calls by default and raises a `RecursionError`. We raised the limit with `sys.setrecursionlimit`, but leaning on very deep recursion is fragile.


| | Value |
|---|---|
| Time | `O(n × height)`, which is `O(n²)` for a line shaped tree |
| Space | `O(n)` for lists, plus `O(height)` for recursion |

---

## Solution 2: Optimal Bottom Up Approach (Compute Each Value Once)

### The Fix

`best_down(x)` never changes. So we compute it **once** and store it in a list `best_down`.


To compute `best_down[x]`, both children must already be done. In other words, we must visit **children before parents**.


Remember the id property from Step 1: a child's id is always bigger than its parent's id. So if we simply loop ids from **last to first**, every child is handled before its parent.


No recursion. No stack. Just one loop.


In the same loop, we also score the depot as the top of a route and update the answer.

### Dry Run on Example 1

Input: `["5", "-3", "7", "8", "2", "-4", "6"]`


Ids: `0:5`, `1:-3`, `2:7`, `3:8`, `4:2`, `5:-4`, `6:6`

Children: `0 → (1, 2)`, `1 → (3, 4)`, `2 → (5, 6)`


| id | value | left_gain | right_gain | route with this top | best_down |
|---|---|---|---|---|---|
| 6 | 6 | 0 | 0 | 6 | 6 |
| 5 | -4 | 0 | 0 | -4 | -4 |
| 4 | 2 | 0 | 0 | 2 | 2 |
| 3 | 8 | 0 | 0 | 8 | 8 |
| 2 | 7 | max(0, -4) = 0 | 6 | 13 | 13 |
| 1 | -3 | 8 | 2 | 7 | 5 |
| 0 | 5 | 5 | 13 | **23** | 18 |


The best value seen is `23`. It comes from top `5`, left arm `-3 → 8` and right arm `7 → 6`.

### Code

```python
class MaximumDeliveryRouteEarnings:
    def __init__(self):
        # value[i], left[i], right[i] describe depot i. -1 means "no child".
        self.value = []
        self.left = []
        self.right = []

    def bestRouteEarnings(self, depotEarnings):
        self.build_tree(depotEarnings)
        count = len(self.value)

        # best_down[i] = best total of a route that starts at depot i
        # and only moves down into one side of its subtree
        best_down = [0] * count
        best = float("-inf")

        # A child always gets a bigger id than its parent.
        # So walking ids from last to first handles children before parents.
        for node in range(count - 1, -1, -1):
            left_child = self.left[node]
            right_child = self.right[node]

            # take a side only if it adds money, else skip it (gain 0)
            left_gain = 0 if left_child == -1 else max(0, best_down[left_child])
            right_gain = 0 if right_child == -1 else max(0, best_down[right_child])

            # best route where this depot is the highest point
            best = max(best, self.value[node] + left_gain + right_gain)

            # a parent can extend only one side of this depot
            best_down[node] = self.value[node] + max(left_gain, right_gain)

        return best

    def build_tree(self, depotEarnings):
        """Reads the level order list. Depots get ids 0, 1, 2 ... in level order."""
        self.value = [int(depotEarnings[0])]
        self.left = []
        self.right = []
        next_pos = 1  # index of the next entry to read

        # value grows while we read, so this visits depots in level order
        node = 0
        while node < len(self.value):
            self.left.append(self.read_child(depotEarnings, next_pos))
            self.right.append(self.read_child(depotEarnings, next_pos + 1))
            next_pos += 2
            node += 1

    def read_child(self, depotEarnings, pos):
        """Creates a depot from entry at index pos and returns its id, or -1 if missing."""
        if pos >= len(depotEarnings) or depotEarnings[pos] == "#":
            return -1
        self.value.append(int(depotEarnings[pos]))
        return len(self.value) - 1
```

### Why These Pieces?

**`best_down` list:** it stores each depot's downward best, so no value is computed twice. This is what turns `O(n²)` into `O(n)`.


**Reverse id loop:** it gives us the "children first" order without recursion. So even a `30,000` deep tree needs no recursion limit changes.


**`max(0, ...)` on each side:** a side that loses money is simply not taken. The depot itself is always counted, so a route always has at least one depot.


**Starting `best` at `float("-inf")`:** any real route total beats it, so the first depot always replaces it. The final answer is always a normal integer.

### Complexity

| | Value |
|---|---|
| Time | `O(n)`. Each depot is read once and processed once |
| Space | `O(n)` for the lists |

---

## Summary

- Every route in a tree has one top depot with at most two downward arms.
- `best_down(x)` is the best straight downward route from `x`. A parent can use only one side of it.
- The best route with top `x` is `value[x]` plus the positive parts of both children's `best_down`.
- Level order ids put every child after its parent, so a reverse loop computes everything bottom up in one pass, with no recursion.