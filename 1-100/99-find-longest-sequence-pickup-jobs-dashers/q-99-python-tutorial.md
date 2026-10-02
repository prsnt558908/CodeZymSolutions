# Find Longest Sequence of Pickup Jobs for Dashers in Python

#### Problem Statement
[https://codezym.com/question/99-find-longest-sequence-pickup-jobs-dashers](https://codezym.com/question/99-find-longest-sequence-pickup-jobs-dashers)

This is the classic **Longest Common Subsequence (LCS)** problem with one twist. A plan that both dashers can do together is a sequence of merchants that appears in both lists in the same order, with skips allowed. The twist is the tie-break: if many longest plans exist, we must return the smallest one in dictionary order.

The core idea is **dynamic programming (DP)**. For every pair of starting points `(i, j)`, we work out the best plan using only the pickups that are left: list 1 from index `i` and list 2 from index `j`. With these small answers saved in a table, we build the final answer from left to right, always picking the smallest merchant that still lets us reach the maximum length.

We will start with brute force, then write a simple DP that stores whole sequences, and finally improve it to a DP that stores only numbers.

## What Exactly Are We Looking For?

- A **common sequence** appears in both lists in the same relative order. We may skip pickups, but we cannot reorder them.
- We want the **longest** common sequence.
- If there is a tie, we return the **smallest** one: compare the first merchants, if they are equal compare the second ones, and so on.

Python's `<` compares two names in dictionary order for us. A space comes before any letter, so `"burger king"` is smaller than `"burgerking"`.

Example 4: for `["a", "b", "c"]` and `["a", "c", "b"]`, both `["a", "b"]` and `["a", "c"]` have length 2. `["a", "b"]` is smaller, so it is the answer.

## Brute Force: Try Every Sequence

The simplest idea is to generate every subsequence of list 1, check whether it also appears in list 2, and keep the best one.

Checking one sequence is easy with two pointers. The problem is the count. A list of `n` pickups has `2^n` subsequences, and with `n = 200` that is far too many.

Brute force is also wasteful. It keeps answering the same small question again and again:

> What is the best plan using list 1 from index `i` and list 2 from index `j`?

There are only `(n + 1) * (m + 1)` such questions. If we answer each one once and save the result, we get dynamic programming.

## Solution 1: Store the Best Sequence in Every Cell

Let `a = dasher1Pickups` and `b = dasher2Pickups`, with sizes `n` and `m`.

We keep a 2D table `best`, where `best[i][j]` is the best common sequence of `a[i:]` and `b[j:]` (longest first, then smallest). Each cell holds a real Python list, so a tie can be settled by simply comparing two lists.

We fill the table from the back (bottom-right corner first), because every cell depends only on the cells below it and to its right. The answer is `best[0][0]`.

### Rules for each cell

**Base case:** if `i == n` or `j == m`, one dasher has no pickups left, so the best plan is an empty list. That is why the table has one extra row and one extra column.

**Front merchants match (`a[i] == b[j]`):** take this merchant, then add the best plan for the rest.

`best[i][j] = [a[i]] + best[i + 1][j + 1]`

Taking it is always safe. Suppose a longest plan from here started with some other merchant. Its first pickup cannot be at `i` or `j` (both hold `a[i]`), so the whole plan sits after `i` and `j`. Then we could put `a[i]` in front of it and get a longer plan, which is impossible. So every longest plan from here starts with `a[i]`.

**Front merchants differ:** at least one of these two pickups is not used. Try skipping each one and keep the better result.

`best[i][j] = self.better(best[i + 1][j], best[i][j + 1])`

`better()` returns the longer list. If both lists have the same length, it returns the smaller one. Python compares two lists element by element, which is exactly our tie-break rule, so `first <= second` does the job.

### Why not store only lengths?

The usual LCS solution stores only lengths. That finds *a* longest plan, but not always the smallest one.

In Example 4, after taking `"a"` we are at `(1, 1)`. Skipping `"b"` in list 1 leads to `["c"]`, and skipping `"c"` in list 2 leads to `["b"]`. Both have length 1, so lengths alone cannot tell us which way to go. Storing whole sequences solves this by direct comparison.

### Code

```python
from typing import List


class LongestPickupSequence:
    def __init__(self):
        pass

    def longestPickupSequence(self, dasher1Pickups: List[str], dasher2Pickups: List[str]) -> List[str]:
        a, b = dasher1Pickups, dasher2Pickups
        n, m = len(a), len(b)

        # best[i][j] = best common sequence of a[i:] and b[j:]
        # ("best" = longest, and among equally long ones, lexicographically smallest).
        # Row n and column m stay empty: one dasher has no pickups left.
        best = [[[] for _ in range(m + 1)] for _ in range(n + 1)]

        # Fill from the back, so the cells below and to the right are always ready.
        for i in range(n - 1, -1, -1):
            for j in range(m - 1, -1, -1):
                if a[i] == b[j]:
                    # Same merchant in front of both lists: take it, then add the best of the rest.
                    best[i][j] = [a[i]] + best[i + 1][j + 1]
                else:
                    # One of the two front pickups must be skipped. Keep the better result.
                    # Lists are never changed after creation, so sharing them between cells is safe.
                    best[i][j] = self.better(best[i + 1][j], best[i][j + 1])

        return best[0][0]

    def better(self, first: List[str], second: List[str]) -> List[str]:
        """Returns the longer list. If both have the same length, returns the lexicographically smaller one."""
        if len(first) != len(second):
            return first if len(first) > len(second) else second
        # Python compares lists element by element, exactly like the tie-break rule.
        return first if first <= second else second
```

### Complexity

Let `L` be the length of the answer (at most `min(n, m)`).

- **Time:** `O(n * m * L)`. A cell may copy or compare a list of up to `L` names.
- **Space:** `O(n * m * L)` in the worst case, since many cells hold their own list.

This passes the limits, but it is heavy. We build a full list in many cells, yet in the end we only need the one at `best[0][0]`.

## Solution 2: Store Only Lengths, Then Build the Answer Greedily

Instead of keeping a whole list in every cell, we keep just one number per cell, and then build the answer once, at the end.

### Step 1: Fill a table of lengths

`longest[i][j]` = length of the longest common sequence of `a[i:]` and `b[j:]`.

- If `a[i] == b[j]`: `longest[i][j] = 1 + longest[i + 1][j + 1]`
- Else: `longest[i][j] = max(longest[i + 1][j], longest[i][j + 1])`

This is a plain 2D list of integers, much lighter than a table of lists. It answers one key question instantly: "standing at `(p, q)`, how many pickups can we still collect?"

### Step 2: Build the answer one pickup at a time

Start at `(i, j) = (0, 0)`. We still need `need = longest[i][j]` pickups.

A pair `(p, q)` with `p >= i` and `q >= j` is a **safe next pickup** when:

1. `a[p] == b[q]` (same merchant in both lists), and
2. `longest[p][q] == need` (after taking it, the remaining `need - 1` pickups are still possible).

Among all safe pairs, take the one with the smallest merchant. Add it to the answer, jump to `(p + 1, q + 1)`, and repeat until `need` becomes 0.

Picking the smallest *common* merchant without this check is a trap. In Example 1, `"albertsons"` is the smallest common name, but a plan that starts with it reaches only 2 pickups instead of 4.

**Same merchant in several safe pairs?** Use the earliest pair. Taking it early leaves more pickups for later, so whatever can be done after a later pair can also be done after the earliest one.

In code, we scan `p` and `q` from small to large and replace our pick only for a *strictly* smaller merchant. So the earliest pair is kept automatically.

### A small speed trick

Values in `longest` never go up when we move down or right, because fewer pickups left can never give a longer plan. So:

- in row `p`, once `longest[p][q] < need`, the rest of that row cannot be safe, and
- once `longest[p][j] < need`, no later row can be safe either.

We stop scanning at those points.

Each step of the walk has a different `need`, and every cell we check in a step has value `need`. So no cell is checked in two different steps, and the whole walk costs `O(n * m)`.

### Dry run (Example 4)

`a = ["a", "b", "c"]` and `b = ["a", "c", "b"]`. The `longest` table:

| | j=0 "a" | j=1 "c" | j=2 "b" | j=3 |
|---|---|---|---|---|
| **i=0 "a"** | 2 | 1 | 1 | 0 |
| **i=1 "b"** | 1 | 1 | 1 | 0 |
| **i=2 "c"** | 1 | 1 | 0 | 0 |
| **i=3** | 0 | 0 | 0 | 0 |

1. At `(0, 0)`, `need = 2`. Only `(0, 0)` has value 2, and `"a" == "a"`. Take `"a"` and move to `(1, 1)`.
2. At `(1, 1)`, `need = 1`. The safe pairs are `(1, 2)` with `"b"` and `(2, 1)` with `"c"`. Take the smaller one, `"b"`, and move to `(2, 3)`.
3. `need` is now 0, so we stop. Answer: `["a", "b"]`.

### Code

```python
from typing import List


class LongestPickupSequence:
    def __init__(self):
        pass

    def longestPickupSequence(self, dasher1Pickups: List[str], dasher2Pickups: List[str]) -> List[str]:
        a, b = dasher1Pickups, dasher2Pickups
        n, m = len(a), len(b)

        # Step 1: longest[i][j] = length of the longest common sequence of a[i:] and b[j:].
        # Row n and column m stay 0 (one dasher has no pickups left).
        longest = [[0] * (m + 1) for _ in range(n + 1)]
        for i in range(n - 1, -1, -1):
            for j in range(m - 1, -1, -1):
                if a[i] == b[j]:
                    longest[i][j] = 1 + longest[i + 1][j + 1]
                else:
                    longest[i][j] = max(longest[i + 1][j], longest[i][j + 1])

        # Step 2: build the answer from the front, one pickup at a time.
        answer = []
        i, j = 0, 0
        while longest[i][j] > 0:
            need = longest[i][j]  # pickups we still have to collect
            pick_i, pick_j = -1, -1

            # Visit only cells whose value is still `need`. Values never grow
            # as p or q grows, so we stop as soon as a value drops below need.
            p = i
            while p < n and longest[p][j] == need:
                q = j
                while q < m and longest[p][q] == need:
                    # Here longest[p][q] == need, so a matching pair is a safe next pickup.
                    # Keep the smallest merchant. "Strictly smaller" keeps the earliest pair for equal names.
                    if a[p] == b[q] and (pick_i == -1 or a[p] < a[pick_i]):
                        pick_i, pick_j = p, q
                    q += 1
                p += 1

            answer.append(a[pick_i])
            i, j = pick_i + 1, pick_j + 1

        return answer
```

### Complexity

- **Time:** `O(n * m)` to fill the table plus `O(n * m)` for the walk, so `O(n * m)` overall.
- **Space:** `O(n * m)` for the table of integers.

Each name comparison is counted as one step, since names have at most 50 characters.

## Summary

| Approach | Time | Space |
|---|---|---|
| Brute force | `O(2^n * (n + m))` | `O(n)` |
| Solution 1: sequence in every cell | `O(n * m * L)` | `O(n * m * L)` |
| Solution 2: lengths + greedy build | `O(n * m)` | `O(n * m)` |