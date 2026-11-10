# Longest Net Transaction Sequence in Java

#### Problem Statement
[https://codezym.com/question/452-longest-net-transaction-sequence](https://codezym.com/question/452-longest-net-transaction-sequence)

The core idea is to keep a **running balance** while we walk through the transactions.

The net amount of any group of consecutive transactions is simply: the balance at the end of the group minus the balance just before the group started.

So at each transaction we ask one question: "Did the balance `currentBalance - targetNetAmount` appear earlier, and when did it first appear?" A hash map that remembers the first time each balance appeared answers this instantly.

This solves the problem in a single pass, in O(n) time. We will first look at a simple brute force solution, see why it is slow, and then build the fast solution step by step.

## Understanding the Problem

We get a list of transaction amounts in time order. Positive means money received, negative means money sent, and zero is also allowed.

We need the longest group of **consecutive** transactions whose sum is exactly `targetNetAmount`. We return only its length, or `0` if no such group exists.

Example: `[200, -500, 100, -200, 300, -100]` with target `-300`.

The group `-500, 100, -200, 300` sums to `-300` and has 4 transactions. No longer group works, so the answer is `4`.

## Basic Solution: Check Every Sequence

Pick every possible start. From that start, extend the end one transaction at a time and keep adding to a running sum. Whenever the sum equals the target, record the length.

```java
int n = transactionAmounts.size();
int longest = 0;

for (int start = 0; start < n; start++) {
    long net = 0;
    for (int end = start; end < n; end++) {
        net += transactionAmounts.get(end);
        if (net == targetNetAmount) {
            longest = Math.max(longest, end - start + 1);
        }
    }
}
return longest;
```

**Problem:** this checks every (start, end) pair, which is about n² / 2 pairs. With 200,000 transactions, that is around 20 billion steps. Far too slow.

### Why a Sliding Window Does Not Help

A sliding window works when all numbers are positive. Adding a transaction always grows the sum and removing one always shrinks it, so we always know which side to move.

Here amounts can be negative, so adding a transaction can make the sum smaller. The window has no clear rule for moving, so we need a different idea.

## Better Solution: Running Balance + Hash Map

### Step 1: Net Amount From Two Balances

Let `balance[i]` be the sum of the first `i` transactions, with `balance[0] = 0`. This running balance is the well known **prefix sum** idea.

The group that starts right after transaction `j` and ends at transaction `i` has:

```
net amount = balance[i] - balance[j]
length     = i - j
```

For example, in `[200, -500, 100, -200, 300, -100]` the balance after 1 transaction is `200` and after 5 transactions is `-100`. So transactions 2 to 5 have a net amount of `-100 - 200 = -300` and a length of `5 - 1 = 4`.

The net amount of any group is now a single subtraction.

### Step 2: Look Up the Balance We Need

We want `balance[i] - balance[j] = targetNetAmount`. Rearranging it:

```
balance[j] = balance[i] - targetNetAmount
```

So when we stand at transaction `i`, we do not need to try every start. We only need to know if the balance `balance[i] - targetNetAmount` appeared earlier.

We keep the balances we have seen in a `HashMap`. It tells us in O(1) time whether a balance appeared before. A list would force us to scan all earlier balances again, which brings back the slow brute force.

### Step 3: Keep Only the First Time a Balance Appears

The same balance can appear many times. The length is `i - j`, so a smaller `j` means a longer group. That is why the map stores only the **first** count at which each balance appeared, and we never overwrite it.

Example: `[100, -100, 200]` with target `200`. The balances are `0, 100, 0, 200`.

At the last transaction we need the balance `200 - 200 = 0`. It appeared after 0 transactions and again after 2. The first one gives length `3 - 0 = 3` (all three transactions), while the later one gives only `3 - 2 = 1`.

### Putting It Together

1. Put balance `0` with count `0` in the map. This allows groups that start at the very first transaction.
2. For each transaction, add it to `balance` and increase `count`.
3. If `balance - targetNetAmount` is in the map, the group from that point to here hits the target. Update `longest` with its length.
4. If `balance` is not in the map yet, store it with the current `count`.

### Dry Run

Transactions `[200, -500, 100, -200, 300, -100]`, target `-300`. The map starts as `{0: 0}`.

| Count | Amount | Balance | Needed balance | Match in map? | Longest |
|---|---|---|---|---|---|
| 1 | 200 | 200 | 500 | No | 0 |
| 2 | -500 | -300 | 0 | Yes, at count 0, length 2 | 2 |
| 3 | 100 | -200 | 100 | No | 2 |
| 4 | -200 | -400 | -100 | No | 2 |
| 5 | 300 | -100 | 200 | Yes, at count 1, length 4 | 4 |
| 6 | -100 | -200 | 100 | No | 4 |

At count 6, the balance `-200` is already in the map from count 3, so we keep count 3.

The answer is `4`.

### Why `long` for the Balance?

There can be 200,000 transactions of up to 10,000 each, so the balance can reach 2 billion. That is close to the `int` limit of about 2.1 billion, and `balance - targetNetAmount` can go past it. Using `long` keeps every value safe.

### Complexity

- **Time:** O(n). We visit each transaction once, and each map lookup or insert is O(1) on average.
- **Space:** O(n). In the worst case every balance is different, and all of them go into the map.

## Java Code

```java
import java.util.*;

public class TransactionAnalyzer {

    public TransactionAnalyzer() {
    }

    /**
     * Returns the length of the longest run of consecutive transactions
     * whose net amount is exactly targetNetAmount, or 0 if there is none.
     */
    public int longestNetSequence(List<Integer> transactionAmounts, int targetNetAmount) {
        // balance -> count of transactions done when this balance was first seen
        Map<Long, Integer> firstSeenAt = new HashMap<>();

        // Before any transaction the balance is 0.
        // This lets a sequence start at the very first transaction.
        firstSeenAt.put(0L, 0);

        long balance = 0;   // long keeps large totals safe
        int count = 0;      // transactions processed so far
        int longest = 0;

        for (int amount : transactionAmounts) {
            balance += amount;
            count++;

            // A sequence ending here hits the target
            // if an earlier balance was (balance - targetNetAmount)
            long neededBalance = balance - targetNetAmount;
            if (firstSeenAt.containsKey(neededBalance)) {
                longest = Math.max(longest, count - firstSeenAt.get(neededBalance));
            }

            // Store only the first time we see a balance.
            // The earliest start gives the longest sequence.
            if (!firstSeenAt.containsKey(balance)) {
                firstSeenAt.put(balance, count);
            }
        }

        return longest;
    }
}
```