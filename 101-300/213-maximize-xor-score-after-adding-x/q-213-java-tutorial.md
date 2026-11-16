# Maximize XOR Score After Adding X in Java

#### Problem Statement
[https://codezym.com/question/213-maximize-xor-score-after-adding-x](https://codezym.com/question/213-maximize-xor-score-after-adding-x)


## Core Idea

Every number in the list has only two choices: keep it as it is, or add `x` to it.

The score only adds up neighbor pairs. So when we move to the next number, the only thing we need from the past is whether the previous number was kept or changed.

This lets us walk the list once from left to right. At every number we remember just two values: the best score so far if this number is kept, and the best score so far if it gets `x`.

This "carry the best answer forward for each choice" idea is called dynamic programming. It runs in `O(n)` time and `O(1)` extra space.


---


## Understanding the Problem

Take `nums = [4, 2, 7]` and `x = 3`. Each index can take one of two values:

| Index | Keep | Add `x` |
|-------|------|---------|
| 0     | 4    | 7       |
| 1     | 2    | 5       |
| 2     | 7    | 10      |

We pick one value for every index and add up the XOR (`^`) of each neighbor pair.

Picking `[7, 5, 10]` gives `(7 ^ 5) + (5 ^ 10) = 2 + 15 = 17`, which is the best possible.

Adding `x` is not always good. The original list `[4, 2, 7]` scores `11`. If we add `x` only to index `1`, we get `[4, 5, 7]`, which scores just `3`.

So we have to compare choices, not just add `x` everywhere.


---


## Solution 1: Brute Force

The simplest idea is to try every possible set of indices.

A bitmask makes this easy. If bit `i` of `mask` is `1`, then index `i` gets `x`.

```java
int n = nums.size();
long best = 0;
for (int mask = 0; mask < (1 << n); mask++) {
    // build the final list for this mask
    long[] b = new long[n];
    for (int i = 0; i < n; i++) {
        b[i] = nums.get(i);
        if ((mask & (1 << i)) != 0) b[i] += x;
    }

    // score of this final list
    long score = 0;
    for (int i = 0; i + 1 < n; i++) {
        score += b[i] ^ b[i + 1];
    }
    best = Math.max(best, score);
}
```

### Why it is slow

There are `2^n` sets, and scoring each one takes `O(n)`. So the total work is `O(n * 2^n)`.

Even `n = 30` means more than a billion sets. With `n` up to `100,000`, this will never finish.


---


## Key Observation

Index `i` is part of at most two pairs: `(i - 1, i)` and `(i, i + 1)`.

Now think of all the ways to choose for indices `0` to `i`. If two of them end the same way at index `i`, the numbers after `i` can't tell them apart. So for each ending, we only need to remember the best score.

There are only two possible endings, which gives us two states:

- `keepScore`: best score of the list up to index `i`, when `nums[i]` is kept.
- `addScore`: best score of the list up to index `i`, when `nums[i]` gets `x`.


---


## Solution 2: Optimal (Two States, One Pass)

### The Transition

At index `i`, let:

- `prev` and `prevWithX` be the two possible values of `nums[i - 1]`.
- `curr` and `currWithX` be the two possible values of `nums[i]`.

If `nums[i]` is kept, the previous number could be in either state. We take the better one:

```
newKeepScore = max(keepScore + (prev ^ curr),
                   addScore  + (prevWithX ^ curr))
```

If `nums[i]` gets `x`, same idea:

```
newAddScore = max(keepScore + (prev ^ currWithX),
                  addScore  + (prevWithX ^ currWithX))
```

**Start:** index `0` has no pair yet, so `keepScore = 0` and `addScore = 0`.

**Answer:** `max(keepScore, addScore)` after the last index.

### Why only two variables?

We could store a full table with two scores for every index. But each step only reads the step right before it.

So two variables are enough, and the extra space drops from `O(n)` to `O(1)`.

### Why `long`?

`nums[i] + x` can reach `2,000,000,000`, which is very close to the `int` limit.

The score adds up almost `100,000` XOR values, and each one can be around `2` billion. So values and scores are stored as `long`.

### Dry Run

`nums = [4, 2, 7]`, `x = 3`

**i = 0:** no pair yet, so `keepScore = 0` and `addScore = 0`.

**i = 1:** current number is `2` or `5`.

- `keepScore = max(0 + (4 ^ 2), 0 + (7 ^ 2)) = max(6, 5) = 6`
- `addScore = max(0 + (4 ^ 5), 0 + (7 ^ 5)) = max(1, 2) = 2`

**i = 2:** current number is `7` or `10`.

- `keepScore = max(6 + (2 ^ 7), 2 + (5 ^ 7)) = max(11, 4) = 11`
- `addScore = max(6 + (2 ^ 10), 2 + (5 ^ 10)) = max(14, 17) = 17`

**Answer:** `max(11, 17) = 17`

### Complexity

- **Time:** `O(n)`, each index is visited once.
- **Space:** `O(1)`, only a few variables.

### Java Code

```java
import java.util.*;

public class MaximizeXorScore {

    public MaximizeXorScore() {
    }

    /**
     * Returns the maximum sum of XOR of adjacent pairs, where x
     * can be added to any set of indices (possibly none).
     */
    public long maximizeXorScore(List<Integer> nums, int x) {
        // Best scores for the list up to the current index.
        // At index 0 there is no pair yet, so both start at 0.
        long keepScore = 0; // current number is kept as it is
        long addScore = 0;  // current number gets x added

        for (int i = 1; i < nums.size(); i++) {
            // two possible values of the previous number
            long prev = nums.get(i - 1);
            long prevWithX = prev + x;

            // two possible values of the current number
            long curr = nums.get(i);
            long currWithX = curr + x;

            // current number kept: previous one can be kept or changed
            long newKeepScore = Math.max(keepScore + (prev ^ curr),
                    addScore + (prevWithX ^ curr));

            // current number gets x: previous one can be kept or changed
            long newAddScore = Math.max(keepScore + (prev ^ currWithX),
                    addScore + (prevWithX ^ currWithX));

            keepScore = newKeepScore;
            addScore = newAddScore;
        }

        return Math.max(keepScore, addScore);
    }
}
```