# Purchase Maximum Number of Consecutive Products in Java

#### Problem Statement
[https://codezym.com/question/265-purchase-max-consecutive-products](https://codezym.com/question/265-purchase-max-consecutive-products)

## Core Idea

Each query asks one thing: if we start buying at `startIndex` and keep moving right, how many products can we buy before the total goes over `budget`?

We could add prices one by one for every query, but that is too slow when there are many queries.

Instead, we build a **prefix sum** array once. With it, the total price of any group of consecutive products takes just one subtraction. And since every price is positive, buying more always costs more. So for each query, we can **binary search** the number of products we can afford.

---

## Solution 1: Brute Force

Do exactly what the problem says. Start at `startIndex`, keep adding prices, and stop as soon as the total goes over the budget.

For `prices = [2, 4, 5, 8, 11]` and query `"1,17"`:

```
buy 1 product  :  4               = 4    fits
buy 2 products :  4 + 5           = 9    fits
buy 3 products :  4 + 5 + 8       = 17   fits
buy 4 products :  4 + 5 + 8 + 11  = 28   over budget, stop
```

The answer is `3`.

```java
int count = 0;
long total = 0;
for (int i = start; i < prices.size(); i++) {
    total += prices.get(i);
    if (total > budget) {
        break;
    }
    count++;
}
```

### What is wrong with it?

One query can walk through all `n` products. With `100,000` products and `100,000` queries, that is up to `10,000,000,000` additions. Far too slow.

Also, different queries keep adding the same prices again and again. Removing this repeated work is the first fix.

---

## Solution 2: Prefix Sum + Binary Search

We improve the brute force in two steps.

### Step 1: Prefix Sum Array

We build an array `prefix` of size `n + 1`, where `prefix[i]` is the total price of the first `i` products. `prefix[0]` is `0`.

```
index :  0  1  2   3   4   5
prices: [2, 4, 5,  8, 11]
prefix: [0, 2, 6, 11, 19, 30]
```

Now the total price of `k` products starting at `start` is:

```
cost = prefix[start + k] - prefix[start]
```

For example, `3` products from index `1` cost `prefix[4] - prefix[1] = 19 - 2 = 17`, which matches `4 + 5 + 8`.

**Why a prefix array?** We build it once in `O(n)` time and every query reuses it. No query has to add prices again.

**Why `long`?** A price can be `1,000,000,000` and there can be `100,000` products, so a total can reach `10^14`. The budget can go up to `10^18`. Both are too big for an `int`.

Each cost check is now fast, but we may still try `k = 1, 2, 3, ...` one by one. That is still `O(n)` per query. Step 2 fixes this.

### Step 2: Binary Search on the Count

Look at the costs from the brute force example again:

```
products bought :  1     2     3     4
total cost      :  4     9     17    28
fits in 17?     :  yes   yes   yes   no
```

Every price is at least `1`, so buying one more product always raises the total. This means all the "yes" answers come first, and all the "no" answers come after.

When answers line up like this, binary search can find the last "yes" without checking every count:

- Pick the middle count `mid`.
- If `mid` products fit the budget, save `mid` as `best` and try a bigger count.
- If not, try a smaller count.

We search counts from `1` up to the number of products left, which is `n - start`.

`best` starts at `0` because buying nothing always fits. This also handles the case where even the first product costs more than the budget.

> Binary search works here because every price is positive. The sorted order of the prices is not needed for it.

### Dry Run

`prices = [2, 4, 5, 8, 11]` and query `"1,17"`. So `start = 1` and `4` products are left.

| low | high | mid | cost of `mid` products | fits? | best |
|-----|------|-----|------------------------|-------|------|
| 1 | 4 | 2 | `prefix[3] - prefix[1] = 9` | yes | 2 |
| 3 | 4 | 3 | `prefix[4] - prefix[1] = 17` | yes | 3 |
| 4 | 4 | 4 | `prefix[5] - prefix[1] = 28` | no | 3 |

Now `low = 4` and `high = 3`, so the loop stops. The answer is `3`.

### Complexity

| Solution | Time | Extra Space |
|----------|------|-------------|
| Brute force | `O(n * q)` | `O(1)` |
| Prefix sum + binary search | `O(n + q log n)` | `O(n)` |

Here `n` is the number of products and `q` is the number of queries.

---

## Final Code

`maximumProducts` builds the prefix array once and reads each query. `countProducts` runs the binary search for one query.

```java
import java.util.*;

public class PurchaseMaximumConsecutiveProducts {

    public PurchaseMaximumConsecutiveProducts() {
    }

    public List<Integer> maximumProducts(List<Integer> prices, List<String> queries) {
        int n = prices.size();

        // prefix[i] = total price of the first i products
        long[] prefix = new long[n + 1];
        for (int i = 0; i < n; i++) {
            prefix[i + 1] = prefix[i] + prices.get(i);
        }

        List<Integer> answers = new ArrayList<>();
        for (String query : queries) {
            // query format: "startIndex,budget"
            String[] parts = query.split(",");
            int start = Integer.parseInt(parts[0].trim());
            long budget = Long.parseLong(parts[1].trim());

            answers.add(countProducts(prefix, start, budget));
        }
        return answers;
    }

    // Returns the largest count of products, starting at 'start', whose total fits in the budget
    int countProducts(long[] prefix, int start, long budget) {
        int low = 1;
        int high = prefix.length - 1 - start; // number of products from 'start' to the end
        int best = 0;                          // buying nothing always fits

        while (low <= high) {
            int mid = (low + high) / 2;
            long cost = prefix[start + mid] - prefix[start]; // total price of 'mid' products

            if (cost <= budget) {
                best = mid;      // mid products fit, try buying more
                low = mid + 1;
            } else {
                high = mid - 1;  // too costly, try buying fewer
            }
        }
        return best;
    }
}
```