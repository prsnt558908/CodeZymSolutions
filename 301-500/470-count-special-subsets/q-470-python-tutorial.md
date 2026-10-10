# Count Special Subsets in Python

#### Problem Statement
[https://codezym.com/question/470-count-special-subsets](https://codezym.com/question/470-count-special-subsets)

A subset is special when its sum is at least every value we left out. So only the **largest left out value** really matters.

We sort the list and try each value as that largest left out value. Every value after it in sorted order must be selected. Every value before it is free to pick or skip.

Now each case is one simple question: how many picks of the free values reach a target sum?

A small table `ways[s]`, which counts the picks with sum `s`, answers it. The same table also answers the first follow-up, and one more table handles products. All three answers come out of a single pass over the sorted list.

---

## Understanding the Problem

Take `a = [2, 3, 4]`.

Pick `[4]`. Its sum is `4`. We left out `2` and `3`. Both are at most `4`, so `[4]` is special.

Pick `[2]`. Its sum is `2`. We left out `3` and `4`. Since `2 < 4`, `[2]` is not special.

Notice that we only had to compare with the biggest left out value. If the sum beats the biggest one, it beats all of them.

---

## Approach 1: Brute Force

The simplest idea is to try every subset. For each one we find its sum, its product and the largest left out value. Then we check the rule.

```python
def brute_force(self, a):
    MOD = self.MOD
    n = len(a)
    count = sum_of_sums = sum_of_products = 0

    # Bit i of mask tells if index i is selected.
    for mask in range(1 << n):
        total, product = 0, 1
        max_left_out = float("-inf")   # largest value we did not select
        for i in range(n):
            if mask >> i & 1:
                total += a[i]
                product = product * a[i] % MOD
            else:
                max_left_out = max(max_left_out, a[i])
        if total >= max_left_out:   # special subset
            count += 1
            sum_of_sums += total
            sum_of_products += product
    return count % MOD, sum_of_sums % MOD, sum_of_products % MOD
```

When every index is selected, `max_left_out` stays at `float("-inf")`. So the check passes, which matches the rule that selecting everything is always special.

**The problem:** there are `2^n` subsets and each one takes `O(n)` to check. With `n = 100` that is about `10^30` subsets. It will never finish.

---

## Key Observation: Fix the Largest Left Out Value

Sort the list and call it `b`.

Take any subset that does not select everything. Look at the **last position `k` in `b` that is not selected**.

- `b[k]` is the largest left out value.
- Every position after `k` is selected. We call these values **forced**.
- Every position before `k` may or may not be selected. We call these values **free**.

So the rule becomes:

```
(sum of chosen free values) + (sum of forced values) >= b[k]
```

Let `suffix_sum[k + 1]` be the sum of the forced values `b[k + 1 .. n - 1]`. Then the chosen free values must add up to at least:

```
need = b[k] - suffix_sum[k + 1]
```

We fill `suffix_sum` once, from right to left. After that, the forced sum for any `k` is a single lookup.

Every subset has exactly one last left out position. So each subset is counted exactly once, even when values repeat.

Only one subset has no left out position at all: selecting everything. It is always special, so we add it at the end.

Here is the idea on `a = [2, 3, 4]`, which is already sorted:

| k | Left out `b[k]` | Forced values | `need` | Good free picks | Special subsets |
|---|---|---|---|---|---|
| 0 | 2 | 3, 4 (sum 7) | 2 - 7 = -5 | `{}` | `[3, 4]` |
| 1 | 3 | 4 (sum 4) | 3 - 4 = -1 | `{}`, `{2}` | `[4]`, `[2, 4]` |
| 2 | 4 | none (sum 0) | 4 - 0 = 4 | `{2, 3}` | `[2, 3]` |
| end | none | all | - | - | `[2, 3, 4]` |

That is `5` special subsets, which matches the expected answer.

---

## Approach 2: A Table of Sums

Trying every free pick is still exponential. But we never need the picks themselves. We only need **how many picks reach each sum**.

So we keep one table:

```
ways[s] = number of free picks whose sum is s
```

At the start nothing is free. The only pick is the empty one, with sum `0`. So `ways[0] = 1`.

We walk through `b` from left to right. At step `k`, the table holds exactly the free values `b[0 .. k - 1]`. So each step does two things:

1. **Answer:** add up `ways[s]` for every `s >= need`.

2. **Grow:** make `b[k]` free for the next steps. Every old pick can skip `b[k]` and stay at sum `s`, or take it and move to sum `s + b[k]`. So `ways[s + b[k]]` gets `ways[s]` added to it.

This is the classic way to count subsets by their sum.

Let us run it on `[2, 3, 4]`:

| Step | Free values | Table before the step (`sum: ways`) | Good picks (`s >= need`) |
|---|---|---|---|
| k = 0 | none | `0:1` | `need = -5`, so `1` |
| k = 1 | 2 | `0:1, 2:1` | `need = -1`, so `1 + 1 = 2` |
| k = 2 | 2, 3 | `0:1, 2:1, 3:1, 5:1` | `need = 4`, so `1` |

Add `1` for selecting everything and we get `1 + 2 + 1 + 1 = 5`.

### Why a list with an offset

Every sum is a whole number in a small range. With at most `100` values of size at most `1000`, every sum lies between `-100,000` and `100,000`.

So a plain list works. It is simpler and faster than a dictionary.

A negative index in Python counts from the end of the list, which is not what we want. So we shift every sum by `offset`, the sum of all absolute values. Sum `s` lives at index `s + offset`.

We also keep `lo` and `hi`, the smallest and largest sum we can reach so far. We only look at that range.

### Why we copy before growing

When a pick takes `b[k]`, we must read `ways[s]` as it was **before** this step. If we read a value that was already changed in this step, a pick could take `b[k]` twice.

A Python slice is a copy. So we first slice out the old part of the table, for sums `lo .. hi`. Then we add it, element by element, into the part where those picks land, sums `lo + b[k] .. hi + b[k]`.

---

## Follow-Up 1: Sum of Sums

All picks counted in `ways[s]` have the same free sum `s`. Add the forced values, and each of them has the full sum `s + suffix_sum[k + 1]`.

So at step `k` the total grows by:

```
ways[s] * (s + suffix_sum[k + 1])    for every s >= need
```

No new table is needed. At the end we add the sum of the whole list, for the "select everything" subset.

On `[2, 3, 4]` this gives `7`, then `4 + 6`, then `5`, then `9` for everything. The total is `31`.

---

## Follow-Up 2: Sum of Products

Here `ways[s]` is not enough. Two picks can have the same sum but different products. For example `{1, 4}` and `{2, 3}` both sum to `5`, but their products are `4` and `6`.

So we keep a second table that grows the same way:

```
products[s] = total of the products of all free picks whose sum is s
```

- Start with `products[0] = 1`, because the empty pick has product `1`.
- When a pick takes `v`, its product is multiplied by `v`. So `products[s + v]` gets `products[s] * v` added to it.

The forced values multiply every product by the same number, `suffix_prod[k + 1]`. So at step `k` the total grows by:

```
products[s] * suffix_prod[k + 1]    for every s >= need
```

At the end we add the product of the whole list.

On `[2, 3, 4]` this gives `12`, then `(1 + 2) * 4 = 12`, then `6`, then `24` for everything. The total is `54`.

---

## Small Details

- **The check uses real sums.** The table index is the real sum, so `s >= need` is an ordinary integer check. Only the stored counts and products are kept modulo `1,000,000,007`.

- **Negative numbers.** Values and products can be negative. With a positive `MOD`, Python's `%` always gives a result between `0` and `MOD - 1`. So no extra helper is needed.

- **Big totals.** Python integers never overflow. So we add up a whole window with `sum()` and take the remainder once per step.

- **The empty subset.** It shows up at the last step as the empty free pick with nothing forced. It passes only when `0 >= largest value`, which is exactly the rule in the statement.

- **One pass, three answers.** The three methods usually get the same list. So `solve` fills all three answers at once and remembers the last list. Calling the other two methods on that list costs almost nothing.

---

## Complexity

Let `S` be the sum of absolute values. `S <= 100 * 1000 = 100,000`.

- **Time:** `O(n * S)`. Each of the `n` steps visits at most `2S + 1` sums. That is at most about `2 * 10^7` simple steps.
- **Space:** `O(S)` for the two tables.

---

## Python Code

```python
class CountSpecialSubsets:
    def __init__(self):
        self.MOD = 1_000_000_007
        # Answers for the last list we solved. One pass fills all three.
        self.last_input = None
        self.count = 0
        self.sum_of_sums = 0
        self.sum_of_products = 0

    def countSpecialSubsets(self, a):
        self.solve(a)
        return self.count

    def sumOfSpecialSubsetSums(self, a):
        self.solve(a)
        return self.sum_of_sums

    def sumOfSpecialSubsetProducts(self, a):
        self.solve(a)
        return self.sum_of_products

    def solve(self, a):
        """
        Fills count, sum_of_sums and sum_of_products for the list a.
        We sort the values and fix b[k] as the largest unselected value.
        Values after k must be selected. Values before k are free to pick or skip.
        """
        # The three methods usually get the same list. Skip the work if we already solved it.
        if self.last_input == list(a):
            return
        self.last_input = list(a)  # a copy, so later changes to the caller's list cannot fool this check

        MOD = self.MOD
        b = sorted(a)
        n = len(b)

        # suffix_sum[k] and suffix_prod[k] describe the forced values b[k..n-1].
        suffix_sum = [0] * (n + 1)
        suffix_prod = [1] * (n + 1)
        for k in range(n - 1, -1, -1):
            suffix_sum[k] = suffix_sum[k + 1] + b[k]
            suffix_prod[k] = suffix_prod[k + 1] * b[k] % MOD

        # Every subset sum lies in [-offset, offset]. Sum s is stored at index s + offset.
        offset = sum(abs(v) for v in b)

        # ways[s]     = number of free picks whose sum is s
        # products[s] = total of the products of those picks
        ways = [0] * (2 * offset + 1)
        products = [0] * (2 * offset + 1)
        ways[offset] = 1       # the empty pick has sum 0
        products[offset] = 1   # and product 1
        lo, hi = 0, 0          # smallest and largest reachable sum so far

        count = sum_of_sums = sum_of_products = 0

        for k in range(n):
            v = b[k]

            # Step 1: b[k] stays out and everything after it is selected.
            # The free picks must add at least "need" so the total reaches b[k].
            need = v - suffix_sum[k + 1]
            first = max(lo, need)
            good_ways = ways[first + offset: hi + offset + 1]          # picks with sum first..hi
            good_products = products[first + offset: hi + offset + 1]
            count += sum(good_ways)
            # A good pick with free sum s has full sum s + suffix_sum[k + 1].
            sum_of_sums += sum(w * (s + suffix_sum[k + 1])
                               for w, s in zip(good_ways, range(first, hi + 1)))
            # The forced values multiply every product by suffix_prod[k + 1].
            sum_of_products += sum(good_products) * suffix_prod[k + 1]
            count %= MOD
            sum_of_sums %= MOD
            sum_of_products %= MOD

            # Step 2: b[k] becomes a free value for the next steps.
            # Every old pick can skip v (stays at s) or take v (moves to s + v).
            # We read old values from a copy, so no pick can take v twice.
            old_ways = ways[lo + offset: hi + offset + 1]               # sums lo..hi
            old_products = products[lo + offset: hi + offset + 1]
            start, end = lo + v + offset, hi + v + offset + 1           # sums lo + v .. hi + v
            ways[start:end] = [(cur + old) % MOD
                               for cur, old in zip(ways[start:end], old_ways)]
            products[start:end] = [(cur + old * v) % MOD
                                   for cur, old in zip(products[start:end], old_products)]
            if v < 0:
                lo += v
            else:
                hi += v

        # Selecting every index is always special.
        self.count = (count + 1) % MOD
        self.sum_of_sums = (sum_of_sums + suffix_sum[0]) % MOD
        self.sum_of_products = (sum_of_products + suffix_prod[0]) % MOD
```