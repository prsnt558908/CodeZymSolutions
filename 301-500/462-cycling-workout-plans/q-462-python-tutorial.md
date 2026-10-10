# Count Cycling Workout Plans in Python

#### Problem Statement
[https://codezym.com/question/462-cycling-workout-plans](https://codezym.com/question/462-cycling-workout-plans)

Every valid workout plan has the shape of a mountain: it climbs, reaches one peak, then drops. The core trick is to stop counting whole plans directly and instead **let every stage take a turn as the peak**.

Once the peak is fixed, a plan is just a climb that ends at the peak glued to a drop that starts at the peak. The two halves can be chosen independently, so the number of plans for that peak is simply `(ways to climb) × (ways to drop)`.

We first count climbs and drops with plain nested loops in O(n²). Then we replace the slow inner loop with a Fenwick tree, a small list that keeps running totals, and bring the whole solution down to O(n log n).

## Understanding the Problem

We are given resistance levels in stage order. We can keep any stages we like, but we cannot reorder them. The kept levels form a valid plan when:

- they strictly increase up to one peak
- they strictly decrease after the peak
- there is at least one stage before the peak and at least one after it, so a plan always has 3 or more stages

"Strictly" means two equal levels can never sit next to each other in a plan.

We count **selections of positions**, not distinct level patterns. For `[4, 4, 9, 6, 6]` every plan has levels `[4, 9, 6]`, but there are 2 choices for the `4` and 2 choices for the `6`, so the answer is `2 × 2 = 4`.

## Starting Point: Brute Force

Each stage is either kept or skipped, so there are 2ⁿ possible selections. We could build each one and check whether it has the mountain shape.

That costs O(2ⁿ × n). It is fine for 15 stages and hopeless for 1,000.

The waste is easy to spot. The same climb, like `[4, 7]`, is rebuilt again and again for every drop it gets paired with. If we count climbs and drops once and reuse those counts, the explosion goes away.

## Key Idea: Fix the Peak

Every plan has exactly one peak, its highest stage. So if we go over every stage `j` and count only the plans whose peak is `j`, each plan gets counted exactly once.

A plan with peak `j` has two parts:

- a **climb**: kept stages before `j` whose levels strictly rise into `levels[j]`
- a **drop**: kept stages after `j` whose levels strictly fall from `levels[j]`

The climb only uses stages before `j` and the drop only uses stages after `j`, so they never get in each other's way. **Any climb can be paired with any drop.** That gives:

```
plans with peak j = climb_ways[j] × drop_ways[j]
```

- `climb_ways[j]`: number of strictly increasing selections that end at stage `j` and have at least one stage before `j`
- `drop_ways[j]`: number of strictly decreasing selections that start at stage `j` and have at least one stage after `j`

If a stage has no climb or no drop, its product is `0` and it adds nothing. No special case is needed.

### Counting the climbs

Look at stage `i`, the one kept right before `j` in the climb. It must be earlier (`i < j`) and lower (`levels[i] < levels[j]`). The climb up to `i` is either:

- stage `i` on its own: **1** way
- a longer climb that already ends at `i`: **climb_ways[i]** ways

Adding this up over every possible `i`:

```
climb_ways[j] = sum of (1 + climb_ways[i])   for all i < j with levels[i] < levels[j]
```

Drops are the mirror image, counted from the right side:

```
drop_ways[j] = sum of (1 + drop_ways[k])   for all k > j with levels[k] < levels[j]
```

The strict `<` is what handles repeated levels. An equal level never counts as a step up or a step down.

### Walkthrough: `[4, 7, 10, 6]`

| Stage | Level | climb_ways | drop_ways | Plans with this peak |
|-------|-------|------------|-----------|----------------------|
| 0 | 4 | 0 | 0 | 0 |
| 1 | 7 | 1 | 1 | 1 |
| 2 | 10 | 3 | 1 | 3 |
| 3 | 6 | 1 | 0 | 0 |

- Climbs into `10`: `[4, 10]`, `[7, 10]` and `[4, 7, 10]`, so `climb_ways[2] = 3`.
- Drops from `10`: only `[10, 6]`, so `drop_ways[2] = 1`.

Total = `1 + 3 = 4`, which matches the expected output.

### About the modulo

These counts grow very fast. A list that only goes up already has about 2ⁿ climbs.

Python integers never overflow, but numbers with hundreds or thousands of digits make every addition slower. So every stored value is kept modulo `1,000,000,007`, which keeps all the numbers small and the loops fast.

## Solution 1: Nested Loops, O(n²)

This solution writes the two formulas exactly as they are.

- One pass from left to right fills `climb_ways`. When we reach `j`, every `climb_ways[i]` with `i < j` is already final.
- One pass from right to left fills `drop_ways`. When we reach `j`, every `drop_ways[k]` with `k > j` is already final.
- A last pass adds up `climb_ways[j] × drop_ways[j]` over every stage.

```python
MOD = 1_000_000_007


class CountCyclingWorkoutPlans:
    def __init__(self):
        pass

    def countWorkoutPlans(self, resistanceLevels):
        if resistanceLevels is None or len(resistanceLevels) < 3:
            return 0
        levels = resistanceLevels
        n = len(levels)

        # climb_ways[j] = strictly increasing selections that END at stage j
        #                 and have at least one stage before j
        climb_ways = [0] * n
        for j in range(n):
            for i in range(j):
                if levels[i] < levels[j]:
                    # stage i alone (1 way) or any longer climb that ends at i
                    climb_ways[j] = (climb_ways[j] + 1 + climb_ways[i]) % MOD

        # drop_ways[j] = strictly decreasing selections that START at stage j
        #                and have at least one stage after j
        drop_ways = [0] * n
        for j in range(n - 1, -1, -1):
            for k in range(j + 1, n):
                if levels[k] < levels[j]:
                    # stage k alone (1 way) or any longer drop that starts at k
                    drop_ways[j] = (drop_ways[j] + 1 + drop_ways[k]) % MOD

        # Every stage gets one turn as the peak
        total = 0
        for j in range(n):
            total = (total + climb_ways[j] * drop_ways[j]) % MOD
        return total
```

**Complexity**

- Time: O(n²). For every stage we look at all stages on its left, then all stages on its right.
- Space: O(n) for the two count lists.

## Solution 2: Fenwick Tree, O(n log n)

### What is slow in Solution 1

For each stage `j`, the inner loop scans every earlier stage just to add up the values of the lower ones. With 100,000 stages that is about 10¹⁰ steps.

Here is the only question the left to right pass really asks at stage `j`:

> Among the stages seen so far, what is the total of `(1 + climb_ways[i])` over those with a level **lower** than `levels[j]`?

After answering, it records stage `j`'s own value `(1 + climb_ways[j])` so later stages can use it. So we need running totals grouped by level, with two quick operations:

1. **add** a value at some level
2. **ask** for the total of all values at levels below some level

### Why a Fenwick tree

- A plain list indexed by level makes "add" O(1), but "ask" is O(n) because the cells must be summed one by one.
- A prefix sum list makes "ask" O(1), but "add" is O(n) because every later prefix must change.

A **Fenwick tree** (also called a Binary Indexed Tree) sits in the middle: both operations take O(log n). It is still just one list.

Cell `i` stores the total of a small block of positions that ends at `i`. The block size is the lowest set bit of `i`, which `i & (-i)` gives us. For a tree of size 8:

| Cell | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|------|---|---|---|---|---|---|---|---|
| Covers | 1 | 1..2 | 3 | 1..4 | 5 | 5..6 | 7 | 1..8 |

- `sum_up_to(rank)` walks down with `i -= i & (-i)`. For example, `sum_up_to(7)` reads cells 7, 6 and 4, which together cover `1..7`.
- `add(rank, value)` walks up with `i += i & (-i)`. For example, `add(5, value)` updates cells 5, 6 and 8, the only cells that cover position 5.

Each move down removes one set bit from `i`, and each move up lands on a bigger block. So both operations touch only about log n cells.

### Turning levels into ranks

The Fenwick tree needs small positions `1, 2, 3, ...`, but levels can be huge or negative. Luckily only the **order** of levels matters, never the actual numbers. So we replace each level with its rank among the distinct levels:

```
levels          : [40, -3, 40, 7]
sorted distinct : [-3, 7, 40]
ranks           : [ 3,  1,  3, 2]
```

We drop duplicates with a `set`, sort what is left with `sorted`, and store each level's rank in a `dict` for quick lookup.

Now "levels lower than `levels[j]`" just means "ranks `1` to `ranks[j] - 1`", so the ask becomes `sum_up_to(ranks[j] - 1)`. Equal levels share the same rank, so they are left out on their own. That is exactly the strictness rule.

### The two passes

```
left to right, for each stage j:
    climb_ways[j] = left_tree.sum_up_to(ranks[j] - 1)
    left_tree.add(ranks[j], 1 + climb_ways[j])

right to left, for each stage j:
    drop_ways[j] = right_tree.sum_up_to(ranks[j] - 1)
    right_tree.add(ranks[j], 1 + drop_ways[j])
```

Inside each step we **ask first and add second**, so a stage never counts itself.

```python
MOD = 1_000_000_007


class FenwickTree:
    """
    Fenwick tree (Binary Indexed Tree) over ranks 1..size.
    add(rank, value) and sum_up_to(rank) both take O(log size).
    All totals are kept modulo 1,000,000,007.
    """

    def __init__(self, size):
        self.tree = [0] * (size + 1)  # index 0 is not used

    def add(self, rank, value):
        """Adds value at the given rank."""
        i = rank
        while i < len(self.tree):
            self.tree[i] = (self.tree[i] + value) % MOD
            i += i & (-i)

    def sum_up_to(self, rank):
        """Total of all values at ranks 1..rank (0 when rank is 0)."""
        total = 0
        i = rank
        while i > 0:
            total = (total + self.tree[i]) % MOD
            i -= i & (-i)
        return total


class CountCyclingWorkoutPlans:
    def __init__(self):
        pass

    def countWorkoutPlans(self, resistanceLevels):
        if resistanceLevels is None or len(resistanceLevels) < 3:
            return 0
        n = len(resistanceLevels)
        ranks = self.to_ranks(resistanceLevels)

        # Left to right: climb_ways[j] = total of (1 + climb_ways[i])
        # over earlier stages i with a lower level
        climb_ways = [0] * n
        left_tree = FenwickTree(n)  # ranks never go above n
        for j in range(n):
            # ranks 1 .. ranks[j] - 1 are the strictly lower levels
            climb_ways[j] = left_tree.sum_up_to(ranks[j] - 1)
            left_tree.add(ranks[j], 1 + climb_ways[j])

        # Right to left: drop_ways[j] = total of (1 + drop_ways[k])
        # over later stages k with a lower level
        drop_ways = [0] * n
        right_tree = FenwickTree(n)
        for j in range(n - 1, -1, -1):
            drop_ways[j] = right_tree.sum_up_to(ranks[j] - 1)
            right_tree.add(ranks[j], 1 + drop_ways[j])

        # Every stage gets one turn as the peak
        total = 0
        for j in range(n):
            total = (total + climb_ways[j] * drop_ways[j]) % MOD
        return total

    def to_ranks(self, levels):
        """
        Replaces every level with its rank among the distinct levels (1 based).
        Only the order of levels matters, so ranks keep every comparison the
        same while giving the Fenwick tree small positions to work with.
        """
        sorted_levels = sorted(set(levels))
        rank_of = {}
        for r, level in enumerate(sorted_levels):
            rank_of[level] = r + 1
        return [rank_of[level] for level in levels]
```

**Complexity**

- Time: O(n log n). Sorting the distinct levels takes O(n log n), and every stage does a few tree operations of O(log n) each.
- Space: O(n) for the ranks, the two count lists and the two trees.

## Summary

| Approach | Time | Space |
|----------|------|-------|
| Brute force over all selections | O(2ⁿ × n) | O(n) |
| Solution 1: nested loops | O(n²) | O(n) |
| Solution 2: Fenwick tree | O(n log n) | O(n) |

Fix the peak, count climbs and drops separately, and multiply. Solution 1 counts them with plain loops. Solution 2 keeps the exact same counting but answers "total over all lower levels seen so far" with a Fenwick tree instead of a full scan.