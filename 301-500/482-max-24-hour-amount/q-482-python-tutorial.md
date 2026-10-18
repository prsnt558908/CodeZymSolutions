# Max Total Amount in Any 24-Hour Period in Python

#### Problem Statement
[https://codezym.com/question/482-max-24-hour-amount](https://codezym.com/question/482-max-24-hour-amount)

---

The core idea is simple. Put all transactions in order of time, then slide a 24-hour "window" over them from left to right.

As the window moves, new transactions enter from the right and old ones leave from the left. We keep a running total of what is inside and remember the biggest total we ever see.

Both methods use the exact same logic. The only difference is which transactions we keep before sliding: one account, or all accounts.

We will first build a simple brute force solution, see why it is slow, and then improve it to a fast sliding window solution.

---

## Understanding the Problem

Each transaction looks like `"trxId,accountId,timestamp,amount"`. The list is **not sorted**.


A window that starts at `start` covers timestamps where `start ≤ timestamp < start + 86400`.


Notice the `<` on the right side. Two transactions that are **exactly** 86,400 seconds (24 hours) apart can never be in the same window.


We need two methods:

- `maxSpendForAccount`: best 24-hour total for one account.
- `maxSpendAllAccounts`: best 24-hour total when all accounts are added together.

If there are no matching transactions, the answer is `0`.

---

## A Useful Observation

A window can start at any second. Do we need to try every second? No.


Take any window and slide its start to the right until it touches the first transaction inside it. Nothing inside drops out, because the end of the window also moves right. We might even pick up more transactions at the end.


So the best window can always **start exactly at some transaction's timestamp**. We only need to try those starting points.

---

## Solution 1: Brute Force

Try every transaction as the start of a window. For each start, loop over all transactions and add up the ones that fall inside `[start, start + 86400)`.

```python
from typing import List


class MaxTotalAmount24Hour:
    def __init__(self):
        # Length of one window: 24 hours in seconds
        self.window_size = 86400

    def maxSpendForAccount(self, transactions: List[str], accountId: str) -> int:
        return self.max_window_sum(self.collect(transactions, accountId))

    def maxSpendAllAccounts(self, transactions: List[str]) -> int:
        # None account id means "keep transactions of every account"
        return self.max_window_sum(self.collect(transactions, None))

    def collect(self, transactions: List[str], account_id) -> List[tuple]:
        """
        Parses "trxId,accountId,timestamp,amount" strings.
        Returns a list of (timestamp, amount) pairs.
        If account_id is None, all transactions are kept,
        otherwise only the ones of that account.
        """
        items = []
        for trx in transactions:
            parts = trx.split(",")
            if account_id is None or parts[1] == account_id:
                items.append((int(parts[2]), int(parts[3])))
        return items

    def max_window_sum(self, items: List[tuple]) -> int:
        """
        Tries every transaction as the start of a window.
        Adds up everything that falls inside [start, start + 24h).
        """
        best = 0
        for start, _ in items:
            total = 0
            for timestamp, amount in items:
                if start <= timestamp < start + self.window_size:
                    total += amount
            best = max(best, total)
        return best
```

### What is wrong with it?

This gives the correct answer, but it is **O(n²)**.


With 100,000 transactions, that is about 10 billion checks. It is far too slow.


The real waste is this: two windows that start close to each other share most of their transactions. Yet we add all of them again from zero every time.

---

## Solution 2: Sort + Sliding Window (Optimal)

### Step 1: Sort by time

Once the transactions are sorted by timestamp, every 24-hour window becomes a **continuous block** in the list. No more jumping around.

### Step 2: Use two pointers

We keep two pointers, `left` and `right`. Everything between them is our current window, and `window_sum` is its total.

For each `right`:

1. **Add** the transaction at `right` to `window_sum`.
2. **Shrink from the left** while the oldest transaction is 24 hours or more behind the newest one:
   `time[right] - time[left] >= 86400`. Subtract it and move `left` forward.
3. **Update** the best answer with `window_sum`.


We use `>=` and not `>` because transactions exactly 24 hours apart cannot share a window.


Transactions with the same timestamp have a difference of `0`, so they always stay together and each one is counted.

### Dry Run

Let us run `maxSpendAllAccounts` on Example 1. After sorting:

| Index | Timestamp | Amount |
|-------|-----------|--------|
| 0 | 0 | 90 |
| 1 | 3600 | 50 |
| 2 | 90000 | 70 |
| 3 | 93600 | 110 |
| 4 | 100000 | 20 |

| right | Action | Window | Sum | Best |
|-------|--------|--------|-----|------|
| 0 | add 90 | [0] | 90 | 90 |
| 1 | add 50 | [0, 1] | 140 | 140 |
| 2 | add 70. 90000 - 0 ≥ 86400, remove 90. 90000 - 3600 = 86400, remove 50 | [2] | 70 | 140 |
| 3 | add 110 | [2, 3] | 180 | 180 |
| 4 | add 20 | [2, 3, 4] | 200 | 200 |


The answer is **200**, which matches the expected output.


Step `right = 2` is a nice check of the window rule. Timestamps `3600` and `90000` are exactly 24 hours apart, so `3600` is removed.

### Why is it fast?

Each transaction is added once and removed at most once. So the sliding part is **O(n)**.


Sorting costs **O(n log n)**, which is the biggest cost overall.

### Important data structures

**List of `(timestamp, amount)` tuples.** After filtering, the transaction ID and account ID are not needed anymore. We only care about when money was spent and how much. A small tuple keeps this simple and easy to sort.


**Sorting the list.** This is what turns "find all transactions within 24 hours" into a block of neighbors. Without sorting, the two pointer trick does not work.


**Big numbers.** Timestamps go up to 10¹² and the total can reach 100,000 × 10⁹ = 10¹⁴. Python integers grow as needed, so we do not need any special type here.

### Sharing code between the two methods

Both methods do the same work. The `collect` helper filters and parses the input:

- With an account ID, it keeps only that account's transactions.
- With `None`, it keeps everything.

Then both methods call the same `max_window_sum`. This keeps the code short and avoids copy paste.

### Code

```python
from typing import List


class MaxTotalAmount24Hour:
    def __init__(self):
        # Length of one window: 24 hours in seconds
        self.window_size = 86400

    def maxSpendForAccount(self, transactions: List[str], accountId: str) -> int:
        return self.max_window_sum(self.collect(transactions, accountId))

    def maxSpendAllAccounts(self, transactions: List[str]) -> int:
        # None account id means "keep transactions of every account"
        return self.max_window_sum(self.collect(transactions, None))

    def collect(self, transactions: List[str], account_id) -> List[tuple]:
        """
        Parses "trxId,accountId,timestamp,amount" strings.
        Returns a list of (timestamp, amount) pairs.
        If account_id is None, all transactions are kept,
        otherwise only the ones of that account.
        """
        items = []
        for trx in transactions:
            parts = trx.split(",")
            if account_id is None or parts[1] == account_id:
                items.append((int(parts[2]), int(parts[3])))
        return items

    def max_window_sum(self, items: List[tuple]) -> int:
        """
        Sliding window over transactions sorted by time.
        The window [left, right] always keeps transactions
        whose timestamps are less than 24 hours apart.
        """
        # sort by timestamp
        items.sort(key=lambda item: item[0])

        best = 0
        window_sum = 0
        left = 0

        for right in range(len(items)):
            # add the new transaction to the window
            window_sum += items[right][1]

            # drop old transactions that are 24 hours or more behind
            while items[right][0] - items[left][0] >= self.window_size:
                window_sum -= items[left][1]
                left += 1

            best = max(best, window_sum)
        return best
```

---

## Complexity

| Solution | Time | Space |
|----------|------|-------|
| Brute Force | O(n²) | O(n) |
| Sort + Sliding Window | O(n log n) | O(n) |

Here `n` is the number of transactions.

---

## Key Takeaways

- When a question asks for the best total inside a time range, **sort by time and slide a window**.
- The best window always starts at a real data point, so there is no need to try every second.
- Read the window boundary carefully. Here, `< start + 86400` means we shrink when the gap is `>= 86400`.
- Python integers do not overflow, so large timestamps and totals are safe.