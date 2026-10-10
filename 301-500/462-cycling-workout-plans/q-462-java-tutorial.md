# Count Cycling Workout Plans in Java

#### Problem Statement
[https://codezym.com/question/462-cycling-workout-plans](https://codezym.com/question/462-cycling-workout-plans)

Every valid workout plan has the shape of a mountain: it climbs, reaches one peak, then drops. The core trick is to stop counting whole plans directly and instead **let every stage take a turn as the peak**.

Once the peak is fixed, a plan is just a climb that ends at the peak glued to a drop that starts at the peak. The two halves can be chosen independently, so the number of plans for that peak is simply `(ways to climb) × (ways to drop)`.

We first count climbs and drops with plain nested loops in O(n²). Then we replace the slow inner loop with a Fenwick tree, a small array that keeps running totals, and bring the whole solution down to O(n log n).

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
plans with peak j = climbWays[j] × dropWays[j]
```

- `climbWays[j]`: number of strictly increasing selections that end at stage `j` and have at least one stage before `j`
- `dropWays[j]`: number of strictly decreasing selections that start at stage `j` and have at least one stage after `j`

If a stage has no climb or no drop, its product is `0` and it adds nothing. No special case is needed.

### Counting the climbs

Look at stage `i`, the one kept right before `j` in the climb. It must be earlier (`i < j`) and lower (`levels[i] < levels[j]`). The climb up to `i` is either:

- stage `i` on its own: **1** way
- a longer climb that already ends at `i`: **climbWays[i]** ways

Adding this up over every possible `i`:

```
climbWays[j] = sum of (1 + climbWays[i])   for all i < j with levels[i] < levels[j]
```

Drops are the mirror image, counted from the right side:

```
dropWays[j] = sum of (1 + dropWays[k])   for all k > j with levels[k] < levels[j]
```

The strict `<` is what handles repeated levels. An equal level never counts as a step up or a step down.

### Walkthrough: `[4, 7, 10, 6]`

| Stage | Level | climbWays | dropWays | Plans with this peak |
|-------|-------|-----------|----------|----------------------|
| 0 | 4 | 0 | 0 | 0 |
| 1 | 7 | 1 | 1 | 1 |
| 2 | 10 | 3 | 1 | 3 |
| 3 | 6 | 1 | 0 | 0 |

- Climbs into `10`: `[4, 10]`, `[7, 10]` and `[4, 7, 10]`, so `climbWays[2] = 3`.
- Drops from `10`: only `[10, 6]`, so `dropWays[2] = 1`.

Total = `1 + 3 = 4`, which matches the expected output.

### About the modulo

These counts grow very fast. A list that only goes up already has about 2ⁿ climbs. So every stored value is kept modulo `1,000,000,007`.

Each stored value is below that number, so the product of two of them is at most about 10¹⁸. That fits easily in a `long`, whose limit is about 9.2 × 10¹⁸.

## Solution 1: Nested Loops, O(n²)

This solution writes the two formulas exactly as they are.

- One pass from left to right fills `climbWays`. When we reach `j`, every `climbWays[i]` with `i < j` is already final.
- One pass from right to left fills `dropWays`. When we reach `j`, every `dropWays[k]` with `k > j` is already final.
- A last pass adds up `climbWays[j] × dropWays[j]` over every stage.

We copy the levels into an `int[]` first, so every lookup inside the loops is a quick array read.

```java
import java.util.*;

public class CountCyclingWorkoutPlans {

    long MOD = 1_000_000_007L;

    public CountCyclingWorkoutPlans() {
    }

    public int countWorkoutPlans(List<Integer> resistanceLevels) {
        if (resistanceLevels == null || resistanceLevels.size() < 3) {
            return 0;
        }
        int n = resistanceLevels.size();

        // Copy into an array so every lookup is a fast index read
        int[] levels = new int[n];
        for (int i = 0; i < n; i++) {
            levels[i] = resistanceLevels.get(i);
        }

        // climbWays[j] = strictly increasing selections that END at stage j
        //                and have at least one stage before j
        long[] climbWays = new long[n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < j; i++) {
                if (levels[i] < levels[j]) {
                    // stage i alone (1 way) or any longer climb that ends at i
                    climbWays[j] = (climbWays[j] + 1 + climbWays[i]) % MOD;
                }
            }
        }

        // dropWays[j] = strictly decreasing selections that START at stage j
        //               and have at least one stage after j
        long[] dropWays = new long[n];
        for (int j = n - 1; j >= 0; j--) {
            for (int k = j + 1; k < n; k++) {
                if (levels[k] < levels[j]) {
                    // stage k alone (1 way) or any longer drop that starts at k
                    dropWays[j] = (dropWays[j] + 1 + dropWays[k]) % MOD;
                }
            }
        }

        // Every stage gets one turn as the peak
        long total = 0;
        for (int j = 0; j < n; j++) {
            total = (total + climbWays[j] * dropWays[j]) % MOD;
        }
        return (int) total;
    }
}
```

**Complexity**

- Time: O(n²). For every stage we look at all stages on its left, then all stages on its right.
- Space: O(n) for the two count arrays.

## Solution 2: Fenwick Tree, O(n log n)

### What is slow in Solution 1

For each stage `j`, the inner loop scans every earlier stage just to add up the values of the lower ones. With 100,000 stages that is about 10¹⁰ steps.

Here is the only question the left to right pass really asks at stage `j`:

> Among the stages seen so far, what is the total of `(1 + climbWays[i])` over those with a level **lower** than `levels[j]`?

After answering, it records stage `j`'s own value `(1 + climbWays[j])` so later stages can use it. So we need running totals grouped by level, with two quick operations:

1. **add** a value at some level
2. **ask** for the total of all values at levels below some level

### Why a Fenwick tree

- A plain array indexed by level makes "add" O(1), but "ask" is O(n) because the cells must be summed one by one.
- A prefix sum array makes "ask" O(1), but "add" is O(n) because every later prefix must change.

A **Fenwick tree** (also called a Binary Indexed Tree) sits in the middle: both operations take O(log n). It is still just one array.

Cell `i` stores the total of a small block of positions that ends at `i`. The block size is the lowest set bit of `i`, which `i & (-i)` gives us. For a tree of size 8:

| Cell | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|------|---|---|---|---|---|---|---|---|
| Covers | 1 | 1..2 | 3 | 1..4 | 5 | 5..6 | 7 | 1..8 |

- `sumUpTo(rank)` walks down with `i -= i & (-i)`. For example, `sumUpTo(7)` reads cells 7, 6 and 4, which together cover `1..7`.
- `add(rank, value)` walks up with `i += i & (-i)`. For example, `add(5, value)` updates cells 5, 6 and 8, the only cells that cover position 5.

Each move down removes one set bit from `i`, and each move up lands on a bigger block. So both operations touch only about log n cells.

### Turning levels into ranks

The Fenwick tree needs small positions `1, 2, 3, ...`, but levels can be huge or negative. Luckily only the **order** of levels matters, never the actual numbers. So we replace each level with its rank among the distinct levels:

```
levels          : [40, -3, 40, 7]
sorted distinct : [-3, 7, 40]
ranks           : [ 3,  1,  3, 2]
```

We drop duplicates with a `HashSet`, sort what is left, and store each level's rank in a `HashMap` for quick lookup.

Now "levels lower than `levels[j]`" just means "ranks `1` to `ranks[j] - 1`", so the ask becomes `sumUpTo(ranks[j] - 1)`. Equal levels share the same rank, so they are left out on their own. That is exactly the strictness rule.

### The two passes

```
left to right, for each stage j:
    climbWays[j] = leftTree.sumUpTo(ranks[j] - 1)
    leftTree.add(ranks[j], 1 + climbWays[j])

right to left, for each stage j:
    dropWays[j] = rightTree.sumUpTo(ranks[j] - 1)
    rightTree.add(ranks[j], 1 + dropWays[j])
```

Inside each step we **ask first and add second**, so a stage never counts itself.

```java
import java.util.*;

public class CountCyclingWorkoutPlans {

    long MOD = 1_000_000_007L;

    public CountCyclingWorkoutPlans() {
    }

    public int countWorkoutPlans(List<Integer> resistanceLevels) {
        if (resistanceLevels == null || resistanceLevels.size() < 3) {
            return 0;
        }
        int n = resistanceLevels.size();
        int[] ranks = toRanks(resistanceLevels);

        // Left to right: climbWays[j] = total of (1 + climbWays[i])
        // over earlier stages i with a lower level
        long[] climbWays = new long[n];
        FenwickTree leftTree = new FenwickTree(n); // ranks never go above n
        for (int j = 0; j < n; j++) {
            climbWays[j] = leftTree.sumUpTo(ranks[j] - 1); // strictly lower levels only
            leftTree.add(ranks[j], 1 + climbWays[j]);
        }

        // Right to left: dropWays[j] = total of (1 + dropWays[k])
        // over later stages k with a lower level
        long[] dropWays = new long[n];
        FenwickTree rightTree = new FenwickTree(n);
        for (int j = n - 1; j >= 0; j--) {
            dropWays[j] = rightTree.sumUpTo(ranks[j] - 1);
            rightTree.add(ranks[j], 1 + dropWays[j]);
        }

        // Every stage gets one turn as the peak
        long total = 0;
        for (int j = 0; j < n; j++) {
            total = (total + climbWays[j] * dropWays[j]) % MOD;
        }
        return (int) total;
    }

    /**
     * Replaces every level with its rank among the distinct levels (1 based).
     * Only the order of levels matters, so ranks keep every comparison the same
     * while giving the Fenwick tree small positions to work with.
     */
    int[] toRanks(List<Integer> resistanceLevels) {
        List<Integer> sortedLevels = new ArrayList<>(new HashSet<>(resistanceLevels));
        Collections.sort(sortedLevels);

        Map<Integer, Integer> rankOf = new HashMap<>();
        for (int r = 0; r < sortedLevels.size(); r++) {
            rankOf.put(sortedLevels.get(r), r + 1);
        }

        int[] ranks = new int[resistanceLevels.size()];
        for (int i = 0; i < ranks.length; i++) {
            ranks[i] = rankOf.get(resistanceLevels.get(i));
        }
        return ranks;
    }
}

/**
 * Fenwick tree (Binary Indexed Tree) over ranks 1..size.
 * add(rank, value) and sumUpTo(rank) both take O(log size).
 * All totals are kept modulo 1,000,000,007.
 */
class FenwickTree {

    long MOD = 1_000_000_007L;
    long[] tree;

    FenwickTree(int size) {
        tree = new long[size + 1]; // index 0 is not used
    }

    // Adds value at the given rank
    void add(int rank, long value) {
        for (int i = rank; i < tree.length; i += i & (-i)) {
            tree[i] = (tree[i] + value) % MOD;
        }
    }

    // Total of all values at ranks 1..rank (0 when rank is 0)
    long sumUpTo(int rank) {
        long sum = 0;
        for (int i = rank; i > 0; i -= i & (-i)) {
            sum = (sum + tree[i]) % MOD;
        }
        return sum;
    }
}
```

**Complexity**

- Time: O(n log n). Sorting the distinct levels takes O(n log n), and every stage does a few tree operations of O(log n) each.
- Space: O(n) for the ranks, the two count arrays and the two trees.

## Summary

| Approach | Time | Space |
|----------|------|-------|
| Brute force over all selections | O(2ⁿ × n) | O(n) |
| Solution 1: nested loops | O(n²) | O(n) |
| Solution 2: Fenwick tree | O(n log n) | O(n) |

Fix the peak, count climbs and drops separately, and multiply. Solution 1 counts them with plain loops. Solution 2 keeps the exact same counting but answers "total over all lower levels seen so far" with a Fenwick tree instead of a full scan.