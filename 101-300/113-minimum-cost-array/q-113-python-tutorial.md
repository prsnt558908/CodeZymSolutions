# Minimum Cost Array in Python

#### Problem Statement
[https://codezym.com/question/113-minimum-cost-array](https://codezym.com/question/113-minimum-cost-array)

<br>

## Core Idea

This is a dynamic programming problem. It is very close to the classic "climbing stairs with cost" problem.


The only tricky part is the backward step. Once we notice that a backward step can be used in only **one fixed shape** (jump 2, step back 1, jump 2), the problem becomes simple.


So from every index we stand on, we really have just two choices: a **short jump** of 2, or a **zig-zag** that moves us 3 places forward. We find the cheapest cost to stand on every index from left to right, and then pick the cheapest way to finish.

<br>

## Understanding the Problem

- We start on index `1` (1-based) and pay its cost.
- From index `i` we can move to `i + 2` or to `i - 1`.
- Every index we land on adds its cost.
- Each index can be visited only once.
- We finish when we land on index `n`, or jump to any index after `n` (this jump is free).


For example, with `costs = [3, 100, 2, 1]`:

```
index : 1    2    3    4
cost  : 3   100   2    1

path  : 1 -> 3 -> 5 (past the end)
total : 3 + 2 = 5
```

We skipped the expensive index 2 and even skipped index 4 by jumping past the end.

<br>

## Solution 1: Brute Force (Try Every Path)

The most direct idea is to try every possible path.


From each index, we try both moves. We keep a `visited` list so we never land on the same index twice. Whenever we reach index `n` or jump past it, we compare the total with the best answer seen so far.


**Why a `visited` list of booleans?** The rules say each index can be used only once. A list of `True` / `False` values lets us check this in constant time, and we can simply set it back to `False` when we return from the recursion (backtracking).

```python
class MinimumCostArray:
    def __init__(self):
        self.best = 0

    def minimumCost(self, costs):
        n = len(costs)

        # visited[i] is True if index i (1-based) is already used
        visited = [False] * (n + 1)
        self.best = float("inf")

        # we start on index 1 and pay its cost
        visited[1] = True
        self.explore(costs, 1, costs[0], visited)

        return self.best

    # try every possible move from index 'pos', 'total' is the cost paid so far
    def explore(self, costs, pos, total, visited):
        n = len(costs)

        # reached the last index, record the answer
        if pos == n:
            self.best = min(self.best, total)
            return

        # option 1: jump forward by 2
        nxt = pos + 2
        if nxt > n:
            # crossed the array, no extra cost
            self.best = min(self.best, total)
        elif not visited[nxt]:
            visited[nxt] = True
            self.explore(costs, nxt, total + costs[nxt - 1], visited)
            visited[nxt] = False

        # option 2: step back by 1
        prev = pos - 1
        if prev >= 1 and not visited[prev]:
            visited[prev] = True
            self.explore(costs, prev, total + costs[prev - 1], visited)
            visited[prev] = False
```

### Problem with this approach

At many indexes the path splits into two. So the number of paths grows **exponentially** with `n`.


With `n` up to 100,000 this will never finish in time. The recursion can also go up to `n` levels deep, which is far beyond Python's default recursion limit of about 1000.


This version is only good for understanding the problem and for checking a faster solution on small inputs.

<br>

## Key Observation: The Back Step Has Only One Shape

Let us look closely at when a back step can actually be used.


**1. A back step always comes right after a jump.**

At the start we stand on index 1. A back step would take us to index 0, which is not allowed.


Two back steps in a row are also impossible. Say we jumped from `j - 2` to `j` and then stepped back to `j - 1`. Stepping back again would land on `j - 2`, which we already visited.


**2. A back step is always followed by a jump.**

After stepping back to `j - 1`, the index behind us (`j - 2`) is already visited. So the only move left is to jump forward to `j + 1`.


So every back step looks exactly like this:

```
standing on i
jump       i   -> i+2
step back  i+2 -> i+1
jump       i+1 -> i+3
```

We can treat these three moves as **one single move**. Let us call it a **zig-zag**. It takes us from `i` to `i + 3`, and we pay `cost(i+1) + cost(i+2) + cost(i+3)`.


One more useful fact: whenever we stand on index `i` (after a short jump or after a zig-zag), indexes `i + 1` and `i + 2` are still unvisited, because we have never gone past `i`. So both moves are always allowed, and we **do not need a visited list anymore**.


We also never need to stop in the middle of a zig-zag. If `i + 2` is the last index, a plain short jump already gets us there for less.


Now every index gives us just two choices:

| Move | From | To | Cost added |
|---|---|---|---|
| Short jump | `i` | `i + 2` | `cost(i+2)` |
| Zig-zag | `i` | `i + 3` | `cost(i+1) + cost(i+2) + cost(i+3)` |

This is now similar to climbing stairs, where you can climb 2 steps or 3 steps at a time. The 3 step move simply pays for three indexes.

<br>

## Solution 2: Dynamic Programming

### What we store

`best[i]` = the cheapest total cost to **stand** on index `i`.


**Why a simple list?** Each value depends only on `best[i - 2]` and `best[i - 3]`, which are already computed. So filling a plain list from left to right is enough.

### How to fill it

Looking at the two moves backwards, we can reach index `i` in two ways:

- **Short jump from `i - 2`** : `best[i - 2] + cost(i)`
- **Zig-zag from `i - 3`** : `best[i - 3] + cost(i - 2) + cost(i - 1) + cost(i)`


`best[i]` is the smaller of the two.

### Starting values

- `best[1] = cost(1)`, because we start here.
- `best[2] = infinity`. The only way to touch index 2 is in the middle of a zig-zag from index 1, and then we must keep going to index 4. So we can never stand on index 2.
- From index 3 onwards, the loop fills everything.

### Final answer

We can finish in two ways:

- Land on index `n` : `best[n]`
- Stand on index `n - 1` and jump to `n + 1`, which is past the end and free : `best[n - 1]`


So the answer is `min(best[n], best[n - 1])`. If `n = 1`, we are already at the end and the answer is just `cost(1)`.

### Dry run

`costs = [1000000, 1, 1000000, 1, 1000000, 1]`

| Index | Cost | Short jump from i - 2 | Zig-zag from i - 3 | best[i] |
|---|---|---|---|---|
| 1 | 1000000 | start | start | **1000000** |
| 2 | 1 | not possible | not possible | **infinity** |
| 3 | 1000000 | 1000000 + 1000000 = 2000000 | not possible | **2000000** |
| 4 | 1 | not possible | 1000000 + 1 + 1000000 + 1 = 2000002 | **2000002** |
| 5 | 1000000 | 2000000 + 1000000 = 3000000 | not possible | **3000000** |
| 6 | 1 | 2000002 + 1 = 2000003 | 2000000 + 1 + 1000000 + 1 = 3000002 | **2000003** |


Answer = `min(best[6], best[5]) = min(2000003, 3000000) = 2000003`.


The winning path is `1 -> 3 -> 2 -> 4 -> 6`. It is a zig-zag (1 to 4) followed by a short jump (4 to 6).

### A note on "infinity"

We use `float("inf")` to mark index 2 as "cannot stand here". Adding a cost to it still gives infinity, so it can never win a `min()`.


Only `best[2]` stays infinity. Every index from 3 onwards gets a real number, so the final answer is always a normal integer. Python integers never overflow, so large totals need no extra care.

### Code

```python
class MinimumCostArray:
    def __init__(self):
        pass

    def minimumCost(self, costs):
        n = len(costs)

        # cost[i] is the cost of index i (1-based, same as the problem)
        cost = [0] + list(costs)

        # best[i] = cheapest total cost to stand on index i
        infinity = float("inf")
        best = [infinity] * (n + 1)

        # we start on index 1 and pay its cost
        best[1] = cost[1]

        # best[2] stays infinity: index 2 is only touched in the
        # middle of a zig-zag that starts at index 1
        for i in range(3, n + 1):
            # short jump: (i - 2) -> i
            best[i] = best[i - 2] + cost[i]

            # zig-zag: (i - 3) -> (i - 1) -> (i - 2) -> i
            if i >= 4:
                zig_zag = best[i - 3] + cost[i - 1] + cost[i - 2] + cost[i]
                best[i] = min(best[i], zig_zag)

        if n == 1:
            return best[1]

        # either land on index n, or stand on index n - 1
        # and jump past the end for free
        return min(best[n], best[n - 1])
```

### Complexity

- **Time:** `O(n)`, we visit each index once.
- **Space:** `O(n)` for the `best` list.


Since we only look back 3 places, the list could be replaced by three variables. The list version is kept here because it is easier to read and debug.