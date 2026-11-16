# Maximum Calories within Budget in Java

#### Problem Statement
[https://codezym.com/question/101-maximum-calories-within-budget](https://codezym.com/question/101-maximum-calories-within-budget)

## Core Idea

We have a menu where every item has a price and some calories. We can buy any item as many times as we like, and we want the most calories without going over the budget.

The key observation is simple: **after we buy one item, we are left with the same problem, just with less money.** So the best answer for `b` dollars can be built from the best answers for smaller amounts of money.

We keep a table `best[0..budget]` and fill it from the smallest amount of money to the largest. Each cell is computed only once, using cells that are already filled. The last cell, `best[budget]`, is the answer.

This is the classic **unbounded knapsack** problem ("unbounded" means each item can be picked any number of times), solved with **dynamic programming**.

We will start with a simple brute force, see why it is slow, and then improve it.

## Approach 1: Brute Force

Think of shopping one item at a time. At any moment we have some `money` left, and we can:

- Stop shopping. This adds `0` calories.
- Pick any item we can afford, pay for it, and keep shopping with the money that is left.

So the best calories for `money` dollars is the largest of:

- `0`
- `calories[i] + bestCalories(money - prices[i])`, for every item `i` with `prices[i] <= money`

```java
// Best calories we can get when we still have "money" dollars to spend
int bestCalories(List<Integer> prices, List<Integer> calories, int money) {
    int best = 0; // stopping here is always allowed
    for (int i = 0; i < prices.size(); i++) {
        int price = prices.get(i);
        if (price <= money) {
            // Buy item i once, then keep shopping with the money left
            best = Math.max(best, calories.get(i) + bestCalories(prices, calories, money - price));
        }
    }
    return best;
}
```

The answer is `bestCalories(prices, calories, budget)`.

### Why is it slow?

Every call can start up to `n` new calls, and the chain of calls can be very long. With a `1` dollar item and a budget of `10000`, we can buy `10000` items in a row. The number of calls grows exponentially.

Most of this work is repeated. In Example 1, buying item 0 then item 1, or item 1 then item 0, leaves `1` dollar in both cases. So the question "best calories with 1 dollar" gets solved again and again.

But money can only be a whole number from `0` to `budget`. So there are only `budget + 1` different questions. If we answer each question only once, the solution becomes fast.

## Approach 2: Dynamic Programming (Optimal)

### Idea

We save the answer for every amount of money in an array.

`best[b]` = maximum calories we can buy by spending **at most** `b` dollars.

To fill `best[b]`, we only need `best[b - price]`, which is a smaller amount. So if we go from `b = 1` up to `b = budget`, every value we need is already filled. This is the same brute force, but each answer is saved and reused.

A loop is also safer than recursion here. With a `1` dollar item and a budget of `10000`, recursion would go `10000` calls deep, which risks a stack overflow.

### Filling the table

- Every `best[b]` starts at `0`, because buying nothing is always allowed. This also handles the case when no item is affordable.
- For each `b` from `1` to `budget`, try every item `i` with `prices[i] <= b`:

```
best[b] = max(best[b], best[b - prices[i]] + calories[i])
```

**Why does this allow buying the same item many times?** Because `best[b - prices[i]]` may already include item `i`. Adding item `i` on top of it simply means buying it one more time.

### Dry run on Example 1

`prices = [20, 10]`, `calories = [200, 50]`, `budget = 31`

| Money `b` | Best way to spend it | `best[b]` |
|---|---|---|
| 0 to 9 | nothing is affordable | 0 |
| 10 to 19 | item 1 once | 50 |
| 20 to 29 | item 0 once (better than item 1 twice, which gives 100) | 200 |
| 30 to 31 | item 0 once + item 1 once | 250 |

Here is how `best[31]` is computed:

- Try item 0: `best[31 - 20] + 200 = best[11] + 200 = 50 + 200 = 250`
- Try item 1: `best[31 - 10] + 50 = best[21] + 50 = 200 + 50 = 250`

So the answer is `250`.

### Why an array?

Money is a whole number from `0` to `budget`, so it can be used directly as an index. A plain `int[]` gives the fastest lookups and uses very little memory.

### Complexity

- **Time:** `O(n * budget)`, where `n` is the number of items. In the worst case this is `200 * 10000 = 2,000,000` simple steps.
- **Space:** `O(budget)` for the `best` array.

The largest possible answer is `10000 * 1000 = 10,000,000` calories, which easily fits in an `int`.

### Java Code

```java
import java.util.*;

public class MaximumCaloriesWithinBudget {

    public MaximumCaloriesWithinBudget() {

    }

    /**
     * Returns the maximum calories we can buy without spending more than budget.
     * Every item can be bought any number of times.
     */
    public int maxCalories(List<Integer> prices, List<Integer> calories, int budget) {
        // best[b] = maximum calories we can get by spending at most b dollars.
        // All cells start at 0 because buying nothing is always allowed.
        int[] best = new int[budget + 1];

        // Solve small amounts first. Bigger amounts reuse those answers.
        for (int b = 1; b <= budget; b++) {
            for (int i = 0; i < prices.size(); i++) {
                int price = prices.get(i);

                // Buy item i once, then spend the money left (b - price) in the best way
                if (price <= b) {
                    best[b] = Math.max(best[b], best[b - price] + calories.get(i));
                }
            }
        }

        return best[budget];
    }
}
```