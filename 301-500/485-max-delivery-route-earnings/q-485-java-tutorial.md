# Maximum Delivery Route Earnings in Java

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


Instead of creating a `Node` class, we give every depot an **id**: `0, 1, 2 ...` in the order we read them. Then we keep three plain arrays.

| Array | Meaning |
|---|---|
| `value[i]` | amount at depot `i` |
| `left[i]` | id of left child, or `-1` |
| `right[i]` | id of right child, or `-1` |


**Why arrays?** They are simple, fast and need no extra class. Ids also give us a very useful property for free:

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

> `bestDown(x)` = the best total of a route that **starts at depot `x` and only moves down**.


How do we find it?

```
bestDown(x) = value[x] + max(0, bestDown(left child), bestDown(right child))
```

- We must include `x` itself.
- Then we may continue into **one** child, whichever gives more.
- If both children lose money, we stop at `x` (that is the `0`).


Why only one child? Because a parent above `x` will use `bestDown(x)` as one of its arms. An arm is a straight line. It cannot split into two directions.


Now the best route where `x` is the **top** is:

```
routeWithTop(x) = value[x] + max(0, bestDown(left)) + max(0, bestDown(right))
```

Here we may use **both** arms, because `x` is where the route turns.


The answer is the largest `routeWithTop(x)` over all depots.


**What about all negative amounts?** In Example 3 (`-8, -2, -6`), every arm loses money, so both gains become `0`. Then `routeWithTop(x)` is just `value[x]`, and the answer is the biggest single depot, `-2`. This matches the rule that a route needs at least one depot.

---

## Solution 1: Simple Approach (Recompute Every Time)

The most direct way is:

1. Try every depot as the top.
2. For each one, call a recursive `bestDown` on its left and right child.


```java
import java.util.*;

public class MaximumDeliveryRouteEarnings {

    // value[i], left[i], right[i] describe depot i. -1 means "no child".
    int[] value;
    int[] left;
    int[] right;
    int count; // number of depots in the tree

    public MaximumDeliveryRouteEarnings() {
    }

    public int bestRouteEarnings(List<String> depotEarnings) {
        buildTree(depotEarnings);
        int best = Integer.MIN_VALUE;

        // try every depot as the highest point of the route
        for (int node = 0; node < count; node++) {
            int leftGain = Math.max(0, bestDown(left[node]));
            int rightGain = Math.max(0, bestDown(right[node]));
            best = Math.max(best, value[node] + leftGain + rightGain);
        }
        return best;
    }

    /**
     * Best total of a route that starts at node and only moves down.
     * Computed from scratch on every call, which is the slow part.
     */
    int bestDown(int node) {
        if (node == -1) {
            return 0;
        }
        int leftGain = Math.max(0, bestDown(left[node]));
        int rightGain = Math.max(0, bestDown(right[node]));
        return value[node] + Math.max(leftGain, rightGain);
    }

    /**
     * Reads the level order list and fills value, left and right.
     * Depots get ids 0, 1, 2 ... in level order.
     */
    void buildTree(List<String> depotEarnings) {
        int size = depotEarnings.size();
        value = new int[size];
        left = new int[size];
        right = new int[size];

        value[0] = Integer.parseInt(depotEarnings.get(0));
        count = 1;
        int next = 1; // index of the next entry to read

        // count grows while we read, so this visits depots in level order
        for (int node = 0; node < count; node++) {
            left[node] = readChild(depotEarnings, next);
            next++;
            right[node] = readChild(depotEarnings, next);
            next++;
        }
    }

    /** Creates a depot from entry at index pos and returns its id, or -1 if missing. */
    int readChild(List<String> depotEarnings, int pos) {
        if (pos >= depotEarnings.size() || depotEarnings.get(pos).equals("#")) {
            return -1;
        }
        value[count] = Integer.parseInt(depotEarnings.get(pos));
        count++;
        return count - 1;
    }
}
```

### Problems With This Approach

This gives correct answers, but it has two big issues.


**1. It repeats the same work.** `bestDown` of a depot is computed again for every depot above it. In a tree shaped like a long line of `30,000` depots, that is about `30,000 × 30,000 / 2 = 450 million` steps.


**2. Deep recursion can crash.** In that same long line, `bestDown` calls itself `30,000` levels deep. Java can run out of call stack space and throw a `StackOverflowError`.


| | Value |
|---|---|
| Time | `O(n × height)`, which is `O(n²)` for a line shaped tree |
| Space | `O(n)` for arrays, plus `O(height)` for recursion |

---

## Solution 2: Optimal Bottom Up Approach (Compute Each Value Once)

### The Fix

`bestDown(x)` never changes. So we compute it **once** and store it in an array `bestDown[]`.


To compute `bestDown[x]`, both children must already be done. In other words, we must visit **children before parents**.


Remember the id property from Step 1: a child's id is always bigger than its parent's id. So if we simply loop ids from **last to first**, every child is handled before its parent.


No recursion. No stack. Just one loop.


In the same loop, we also score the depot as the top of a route and update the answer.

### Dry Run on Example 1

Input: `["5", "-3", "7", "8", "2", "-4", "6"]`


Ids: `0:5`, `1:-3`, `2:7`, `3:8`, `4:2`, `5:-4`, `6:6`

Children: `0 → (1, 2)`, `1 → (3, 4)`, `2 → (5, 6)`


| id | value | leftGain | rightGain | route with this top | bestDown |
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

```java
import java.util.*;

public class MaximumDeliveryRouteEarnings {

    // value[i], left[i], right[i] describe depot i. -1 means "no child".
    int[] value;
    int[] left;
    int[] right;
    int count; // number of depots in the tree

    public MaximumDeliveryRouteEarnings() {
    }

    public int bestRouteEarnings(List<String> depotEarnings) {
        buildTree(depotEarnings);

        // bestDown[i] = best total of a route that starts at depot i
        // and only moves down into one side of its subtree
        int[] bestDown = new int[count];
        int best = Integer.MIN_VALUE;

        // A child always gets a bigger id than its parent.
        // So walking ids from last to first handles children before parents.
        for (int node = count - 1; node >= 0; node--) {
            // take a side only if it adds money, else skip it (gain 0)
            int leftGain = left[node] == -1 ? 0 : Math.max(0, bestDown[left[node]]);
            int rightGain = right[node] == -1 ? 0 : Math.max(0, bestDown[right[node]]);

            // best route where this depot is the highest point
            best = Math.max(best, value[node] + leftGain + rightGain);

            // a parent can extend only one side of this depot
            bestDown[node] = value[node] + Math.max(leftGain, rightGain);
        }
        return best;
    }

    /**
     * Reads the level order list and fills value, left and right.
     * Depots get ids 0, 1, 2 ... in level order.
     */
    void buildTree(List<String> depotEarnings) {
        int size = depotEarnings.size();
        value = new int[size];
        left = new int[size];
        right = new int[size];

        value[0] = Integer.parseInt(depotEarnings.get(0));
        count = 1;
        int next = 1; // index of the next entry to read

        // count grows while we read, so this visits depots in level order
        for (int node = 0; node < count; node++) {
            left[node] = readChild(depotEarnings, next);
            next++;
            right[node] = readChild(depotEarnings, next);
            next++;
        }
    }

    /** Creates a depot from entry at index pos and returns its id, or -1 if missing. */
    int readChild(List<String> depotEarnings, int pos) {
        if (pos >= depotEarnings.size() || depotEarnings.get(pos).equals("#")) {
            return -1;
        }
        value[count] = Integer.parseInt(depotEarnings.get(pos));
        count++;
        return count - 1;
    }
}
```

### Why These Pieces?

**`bestDown[]` array:** it stores each depot's downward best, so no value is computed twice. This is what turns `O(n²)` into `O(n)`.


**Reverse id loop:** it gives us the "children first" order without recursion. So even a `30,000` deep tree cannot overflow the stack.


**`max(0, ...)` on each side:** a side that loses money is simply not taken. The depot itself is always counted, so a route always has at least one depot.


**`int` is enough:** the largest possible total is `30,000 × 1,000 = 30,000,000`, which easily fits in an `int`.

### Complexity

| | Value |
|---|---|
| Time | `O(n)`. Each depot is read once and processed once |
| Space | `O(n)` for the arrays |

---

## Summary

- Every route in a tree has one top depot with at most two downward arms.
- `bestDown(x)` is the best straight downward route from `x`. A parent can use only one side of it.
- The best route with top `x` is `value[x]` plus the positive parts of both children's `bestDown`.
- Level order ids put every child after its parent, so a reverse loop computes everything bottom up in one pass, with no recursion.