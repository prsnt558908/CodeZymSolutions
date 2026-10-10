# Count Special Subsets in Java

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

```java
long[] bruteForce(List<Integer> a) {
    int n = a.size();
    long count = 0, sumOfSums = 0, sumOfProducts = 0;

    // Bit i of mask tells if index i is selected.
    for (long mask = 0; mask < (1L << n); mask++) {
        long sum = 0, product = 1;
        long maxLeftOut = Long.MIN_VALUE;   // largest value we did not select
        for (int i = 0; i < n; i++) {
            if ((mask >> i & 1) == 1) {
                sum += a.get(i);
                product = product * a.get(i) % MOD;
            } else {
                maxLeftOut = Math.max(maxLeftOut, a.get(i));
            }
        }
        if (sum >= maxLeftOut) {   // special subset
            count++;
            sumOfSums += sum;
            sumOfProducts += product;
        }
    }
    return new long[]{mod(count), mod(sumOfSums), mod(sumOfProducts)};
}
```

When every index is selected, `maxLeftOut` stays at `Long.MIN_VALUE`. So the check passes, which matches the rule that selecting everything is always special.

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

Let `suffixSum[k + 1]` be the sum of the forced values `b[k + 1 .. n - 1]`. Then the chosen free values must add up to at least:

```
need = b[k] - suffixSum[k + 1]
```

We fill `suffixSum` once, from right to left. After that, the forced sum for any `k` is a single lookup.

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

### Why an array with an offset

Every sum is a whole number in a small range. With at most `100` values of size at most `1000`, every sum lies between `-100,000` and `100,000`.

So a plain array works. It is simpler and faster than a map.

Array indexes cannot be negative. So we shift every sum by `offset`, the sum of all absolute values. Sum `s` lives at index `s + offset`.

We also keep `lo` and `hi`, the smallest and largest sum we can reach so far. The loops only visit that range.

### Why we copy before growing

When a pick takes `b[k]`, we must read `ways[s]` as it was **before** this step. If we read a value that was already changed in this step, a pick could take `b[k]` twice.

So we first copy the old part of the table. Then we read from the copy and write into the table.

---

## Follow-Up 1: Sum of Sums

All picks counted in `ways[s]` have the same free sum `s`. Add the forced values, and each of them has the full sum `s + suffixSum[k + 1]`.

So at step `k` the total grows by:

```
ways[s] * (s + suffixSum[k + 1])    for every s >= need
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

The forced values multiply every product by the same number, `suffixProd[k + 1]`. So at step `k` the total grows by:

```
products[s] * suffixProd[k + 1]    for every s >= need
```

At the end we add the product of the whole list.

On `[2, 3, 4]` this gives `12`, then `(1 + 2) * 4 = 12`, then `6`, then `24` for everything. The total is `54`.

---

## Small Details

- **The check uses real sums.** The table index is the real sum, so `s >= need` is an ordinary integer check. Only the stored counts and products are kept modulo `1,000,000,007`.

- **Negative numbers.** Values and products can be negative. The helper `mod(x)` returns `((x % MOD) + MOD) % MOD`, which is always between `0` and `MOD - 1`.

- **The empty subset.** It shows up at the last step as the empty free pick with nothing forced. It passes only when `0 >= largest value`, which is exactly the rule in the statement.

- **One pass, three answers.** The three methods usually get the same list. So `solve` fills all three answers at once and remembers the last list. Calling the other two methods on that list costs almost nothing.

---

## Complexity

Let `S` be the sum of absolute values. `S <= 100 * 1000 = 100,000`.

- **Time:** `O(n * S)`. Each of the `n` steps visits at most `2S + 1` sums. That is at most about `2 * 10^7` simple steps.
- **Space:** `O(S)` for the two tables.

---

## Java Code

```java
import java.util.*;

public class CountSpecialSubsets {

    long MOD = 1_000_000_007L;

    // Answers for the last list we solved. One pass fills all three.
    List<Integer> lastInput = null;
    long count, sumOfSums, sumOfProducts;

    public CountSpecialSubsets() {
    }

    public int countSpecialSubsets(List<Integer> a) {
        solve(a);
        return (int) count;
    }

    public int sumOfSpecialSubsetSums(List<Integer> a) {
        solve(a);
        return (int) sumOfSums;
    }

    public int sumOfSpecialSubsetProducts(List<Integer> a) {
        solve(a);
        return (int) sumOfProducts;
    }

    /**
     * Fills count, sumOfSums and sumOfProducts for the list a.
     * We sort the values and fix b[k] as the largest unselected value.
     * Values after k must be selected. Values before k are free to pick or skip.
     */
    void solve(List<Integer> a) {
        // The three methods usually get the same list. Skip the work if we already solved it.
        if (a.equals(lastInput)) return;
        lastInput = new ArrayList<>(a);  // a copy, so later changes to the caller's list cannot fool this check

        List<Integer> b = new ArrayList<>(a);
        Collections.sort(b);
        int n = b.size();

        // suffixSum[k] and suffixProd[k] describe the forced values b[k..n-1].
        int[] suffixSum = new int[n + 1];
        long[] suffixProd = new long[n + 1];
        suffixProd[n] = 1;
        for (int k = n - 1; k >= 0; k--) {
            suffixSum[k] = suffixSum[k + 1] + b.get(k);
            suffixProd[k] = suffixProd[k + 1] * mod(b.get(k)) % MOD;
        }

        // Every subset sum lies in [-offset, offset]. Sum s is stored at index s + offset.
        int offset = 0;
        for (int v : b) offset += Math.abs(v);

        // ways[s]     = number of free picks whose sum is s
        // products[s] = total of the products of those picks
        long[] ways = new long[2 * offset + 1];
        long[] products = new long[2 * offset + 1];
        ways[offset] = 1;      // the empty pick has sum 0
        products[offset] = 1;  // and product 1
        int lo = 0, hi = 0;    // smallest and largest reachable sum so far

        count = 0;
        sumOfSums = 0;
        sumOfProducts = 0;

        for (int k = 0; k < n; k++) {
            int v = b.get(k);

            // Step 1: b[k] stays out and everything after it is selected.
            // The free picks must add at least "need" so the total reaches b[k].
            int need = v - suffixSum[k + 1];
            long goodWays = 0, goodSums = 0, goodProducts = 0;
            for (int s = Math.max(lo, need); s <= hi; s++) {
                int i = s + offset;
                goodWays += ways[i];          // stays below 2 * 10^14, fits in a long
                goodProducts += products[i];
                // Full sum of these picks is s + suffixSum[k + 1]. Adding MOD keeps it positive.
                goodSums = (goodSums + ways[i] * (s + suffixSum[k + 1] + MOD)) % MOD;
            }
            count = (count + goodWays) % MOD;
            sumOfSums = (sumOfSums + goodSums) % MOD;
            // The forced values multiply every product by suffixProd[k + 1].
            sumOfProducts = (sumOfProducts + goodProducts % MOD * suffixProd[k + 1]) % MOD;

            // Step 2: b[k] becomes a free value for the next steps.
            // Every old pick can skip v (stays at s) or take v (moves to s + v).
            // We read old values from a copy, so no pick can take v twice.
            long vMod = mod(v);
            long[] oldWays = Arrays.copyOfRange(ways, lo + offset, hi + offset + 1);
            long[] oldProducts = Arrays.copyOfRange(products, lo + offset, hi + offset + 1);
            for (int s = lo; s <= hi; s++) {
                int to = s + v + offset;
                ways[to] = (ways[to] + oldWays[s - lo]) % MOD;
                products[to] = (products[to] + oldProducts[s - lo] * vMod) % MOD;
            }
            if (v < 0) lo += v;
            else hi += v;
        }

        // Selecting every index is always special.
        count = (count + 1) % MOD;
        sumOfSums = (sumOfSums + mod(suffixSum[0])) % MOD;
        sumOfProducts = (sumOfProducts + suffixProd[0]) % MOD;
    }

    /** Remainder in the range [0, MOD), also for negative x. */
    long mod(long x) {
        return ((x % MOD) + MOD) % MOD;
    }
}
```