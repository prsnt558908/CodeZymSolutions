# Debugging: Dasher Assignment Service for Order Delivery in Python

#### Problem Statement
[https://codezym.com/question/274-dasher-assignment-service-order-delivery](https://codezym.com/question/274-dasher-assignment-service-order-delivery)

Think of this service as a pool of dashers. `pickDasher` takes a dasher out of the pool for an order, and `orderDelivered` puts that dasher back. Built-in hash maps and hash sets (`dict` and `set`) are not allowed, so the real challenge is choosing the right data structures. No design pattern is needed here: a few small classes, each with one clear job, give the simplest and fastest solution.

The solution has just two building blocks:

1. A **custom hash set with 16 buckets** that keeps available dashers in the correct pick order.
2. A **custom hash map** that remembers which dasher is working on which order.

We will start with a simple version that uses only lists, spot its slow parts, and then fix them one by one.

---

## Understanding the Rules

**Which bucket does a dasher go to?**

```python
bucket_index = (dasher_id * 31 + 17) % 16
```

The formula in the statement uses a `long` cast to avoid overflow. Python integers never overflow, so we can use the formula directly.

**Which dasher gets picked?**

Scan buckets from `0` to `15` and take the **first** dasher of the **first non-empty** bucket. So a lower bucket always wins, and inside one bucket the oldest dasher wins (first in, first out).

**When does a dasher come back?**

A picked dasher stays busy until its order is delivered. Then the dasher is added back to the **end** of its bucket.

Here is Example 1 from the problem, step by step:

| Call | Bucket 1 | Bucket 5 | Returns |
|---|---|---|---|
| `addDasher` 12, 16, 32 | [16, 32] | [12] | |
| `pickDasher(101)` | [32] | [12] | 16 |
| `pickDasher(102)` | [ ] | [12] | 32 |
| `pickDasher(103)` | [ ] | [ ] | 12 |
| `pickDasher(104)` | [ ] | [ ] | -1 |
| `orderDelivered(101)` | [16] | [ ] | |
| `pickDasher(104)` | [ ] | [ ] | 16 |

---

## Do We Need a Design Pattern?

Two patterns can look tempting here:

- **Strategy** for the "which dasher to pick" rule. Strategy is useful when you need to switch between different rules. This problem has only one fixed rule (lowest bucket first, then first in first out), so a Strategy class would only add extra code.
- **State** for the dasher life cycle (available or busy). State is useful when an object has many states that behave differently. A dasher has only two states, and where it is stored already tells us its state: inside the available set means available, inside the assignment map means busy.

The problem itself warns about **unnecessary abstractions**. So we skip patterns and put our effort into the data structures.

---

## Solution 1: Simple Approach Using Lists

### Idea

- Keep **16 lists** as buckets for available dashers.
- Keep active assignments in **two lists side by side**: `active_orders[i]` is being delivered by `active_dashers[i]`.
- Answer every question ("is this order active?", "is this dasher busy?") by scanning these lists.

### Code

```python
class DasherAssignmentService:
    def __init__(self):
        # 16 buckets of available dashers, each bucket keeps arrival (FIFO) order
        self.buckets = [[] for _ in range(16)]
        # Active assignments kept side by side:
        # active_orders[i] is being delivered by active_dashers[i]
        self.active_orders = []
        self.active_dashers = []

    def bucket_index(self, dasher_id):
        # Bucket formula from the problem
        return (dasher_id * 31 + 17) % 16

    def addDasher(self, dasherId):
        bucket = self.buckets[self.bucket_index(dasherId)]
        if dasherId in bucket:                  # already available
            return
        if dasherId in self.active_dashers:     # busy with an active order
            return
        bucket.append(dasherId)                 # append to the end

    def pickDasher(self, orderId):
        if orderId in self.active_orders:       # order already has a dasher
            return -1
        for bucket in self.buckets:             # always start from bucket 0
            if bucket:
                dasher_id = bucket.pop(0)       # first in, first out
                self.active_orders.append(orderId)
                self.active_dashers.append(dasher_id)
                return dasher_id
        return -1                               # no dasher is available

    def orderDelivered(self, orderId):
        if orderId not in self.active_orders:   # order is not active
            return
        i = self.active_orders.index(orderId)
        self.active_orders.pop(i)
        dasher_id = self.active_dashers.pop(i)
        # back to the end of its bucket
        self.buckets[self.bucket_index(dasher_id)].append(dasher_id)
```

### Problems With This Approach

Let `A` be the number of active orders and `k` be the number of dashers in one bucket.

- **Duplicate order check** in `pickDasher` scans all active orders: **O(A)**.
- **Busy dasher check** in `addDasher` scans all active dashers: **O(A)**.
- **Finding and removing** an order in `orderDelivered` scans the list and then shifts every element after it: **O(A)**.
- **`bucket.pop(0)`** on a list shifts every remaining dasher one step left: **O(k)**.

With thousands of active orders, every call has to walk through the whole assignment list. Let's fix that.

---

## Solution 2: Optimized Approach Using a Custom Hash Map

The logic stays the same. We only replace the slow parts with better data structures.

### Improvement 1: `deque` buckets

A `deque` (double-ended queue from `collections`) keeps items in order just like a list, but `popleft()` removes the first item in **O(1)** because nothing has to shift.

If your interviewer insists on a plain `list`, you can keep `pop(0)`. Everything else stays the same.

### Improvement 2: A small custom hash map for assignments

Instead of one long list, we spread the assignments over many small buckets, just like a real hash map does:

- `key % number of buckets` decides the bucket.
- Each bucket holds a few `MapEntry(key, value)` pairs.
- When the map has more entries than buckets, it **doubles** the number of buckets, so every bucket stays short.

Now `get`, `put` and `remove` only look inside one short bucket, which is **O(1)** on average.

We use this map **twice**:

- `order_to_dasher`: tells us if an order already has a dasher, and which dasher to free on delivery.
- `dasher_to_order`: tells us if a dasher is busy right now, without scanning every assignment.

All IDs are `0` or more, so `-1` can safely mean "not found". This keeps the code free of `None` checks.

### Improvement 3: One class for each job

| Class | Why we need it |
|---|---|
| `AvailableDasherSet` | The 16-bucket hash set. It owns the bucket formula and the pick order. |
| `IntHashMap` | A custom int to int map, because `dict` is not allowed. |
| `MapEntry` | One key and value pair inside an `IntHashMap` bucket. |
| `DasherAssignmentService` | Connects the pieces and applies the rules. |

With this split, every service method is only a few lines and reads almost like the problem statement.

### How a Dasher Moves Through the System

```
addDasher(16)        available: bucket 1 = [16]   order_to_dasher { }         dasher_to_order { }
pickDasher(101)      available: bucket 1 = [ ]    order_to_dasher {101: 16}   dasher_to_order {16: 101}
orderDelivered(101)  available: bucket 1 = [16]   order_to_dasher { }         dasher_to_order { }
```

### Code

```python
from collections import deque


class DasherAssignmentService:
    """
    Connects the pool of available dashers with the active order assignments.
    Each method is a direct translation of the rules in the problem.
    """

    def __init__(self):
        self.available_dashers = AvailableDasherSet()
        self.order_to_dasher = IntHashMap()   # active order -> its dasher
        self.dasher_to_order = IntHashMap()   # busy dasher  -> its order

    def addDasher(self, dasherId):
        if self.available_dashers.contains(dasherId):      # already available
            return
        if self.dasher_to_order.contains_key(dasherId):    # busy with an active order
            return
        self.available_dashers.add(dasherId)

    def pickDasher(self, orderId):
        if self.order_to_dasher.contains_key(orderId):     # order already has a dasher
            return -1
        dasher_id = self.available_dashers.remove_next()
        if dasher_id == -1:                                # no dasher is available
            return -1
        self.order_to_dasher.put(orderId, dasher_id)
        self.dasher_to_order.put(dasher_id, orderId)
        return dasher_id

    def orderDelivered(self, orderId):
        dasher_id = self.order_to_dasher.remove(orderId)
        if dasher_id == -1:                                # order is not active
            return
        self.dasher_to_order.remove(dasher_id)
        self.available_dashers.add(dasher_id)              # back to the end of its bucket


class AvailableDasherSet:
    """
    Custom hash set of available dashers with exactly 16 buckets.
    Inside a bucket, dashers stay in the order they arrived (FIFO).
    """

    def __init__(self):
        self.bucket_count = 16
        # deque: removing the first dasher is O(1)
        self.buckets = [deque() for _ in range(self.bucket_count)]

    def bucket_index(self, dasher_id):
        # Bucket formula from the problem
        return (dasher_id * 31 + 17) % self.bucket_count

    def contains(self, dasher_id):
        # Searches only the dasher's own bucket
        return dasher_id in self.buckets[self.bucket_index(dasher_id)]

    def add(self, dasher_id):
        # Appends the dasher to the end of its bucket. Caller makes sure it is not already here.
        self.buckets[self.bucket_index(dasher_id)].append(dasher_id)

    def remove_next(self):
        # Removes and returns the first dasher of the lowest non-empty bucket, or -1 if none
        for bucket in self.buckets:
            if bucket:
                return bucket.popleft()
        return -1


class IntHashMap:
    """
    A small hash map from int keys to int values, because built-in dicts are not allowed.
    IDs are never negative, so -1 safely means "not found".
    """

    def __init__(self):
        self.buckets = [[] for _ in range(16)]
        self.size = 0

    def bucket_for(self, key):
        return self.buckets[key % len(self.buckets)]

    def get(self, key):
        # Returns the value stored for key, or -1 if key is missing
        for entry in self.bucket_for(key):
            if entry.key == key:
                return entry.value
        return -1

    def contains_key(self, key):
        return self.get(key) != -1

    def put(self, key, value):
        # Adds or updates key. Doubles the buckets when the map gets crowded.
        bucket = self.bucket_for(key)
        for entry in bucket:
            if entry.key == key:
                entry.value = value
                return
        bucket.append(MapEntry(key, value))
        self.size += 1
        if self.size > len(self.buckets):
            self.resize()

    def remove(self, key):
        # Removes key and returns its value, or -1 if key is missing
        bucket = self.bucket_for(key)
        for i, entry in enumerate(bucket):
            if entry.key == key:
                bucket.pop(i)
                self.size -= 1
                return entry.value
        return -1

    def resize(self):
        # Spreads all entries over twice as many buckets, so every bucket stays short
        old_buckets = self.buckets
        self.buckets = [[] for _ in range(len(old_buckets) * 2)]
        for bucket in old_buckets:
            for entry in bucket:
                self.bucket_for(entry.key).append(entry)


class MapEntry:
    """One key and value pair stored inside an IntHashMap bucket."""

    def __init__(self, key, value):
        self.key = key
        self.value = value
```

### Complexity

Here `k` is the number of dashers in one bucket and `A` is the number of active orders.

| Method | Solution 1 | Solution 2 |
|---|---|---|
| `addDasher` | O(k + A) | O(k) |
| `pickDasher` | O(A + k) | O(1) average |
| `orderDelivered` | O(A) | O(1) average |

`addDasher` stays O(k) because the problem asks us to search the dasher's own bucket to find duplicates. With exactly 16 buckets, that search is part of the rules.

Space is O(N + A) for both solutions, where N is the number of available dashers.

---

## Common Bugs to Watch For

In the real interview you get buggy code to fix, so these are worth checking first:

- **Shared buckets:** `[[]] * 16` creates 16 references to one single list, so all buckets share the same dashers. Use `[[] for _ in range(16)]`.
- **Dasher `0` treated as missing:** `if not dasher_id:` is true for dasher `0`. Compare with `-1` instead.
- **Using `dict` or `set`:** the rules forbid them, so the interviewer will flag them even if the output is right.
- **Scan not restarting:** every `pickDasher` call must start again from bucket `0`.
- **Freed dasher in the wrong place:** a delivered dasher goes to the **end** of its bucket (`append`, not `insert(0, ...)` or `appendleft`).
- **Missing checks:** without the active order check, one order can get two dashers. Without the busy check, a dasher can be available and busy at the same time.