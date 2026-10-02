# Debugging: Dasher Assignment Service for Order Delivery in Java

#### Problem Statement
[https://codezym.com/question/274-dasher-assignment-service-order-delivery](https://codezym.com/question/274-dasher-assignment-service-order-delivery)

Think of this service as a pool of dashers. `pickDasher` takes a dasher out of the pool for an order, and `orderDelivered` puts that dasher back. Built-in hash maps and hash sets are not allowed, so the real challenge is choosing the right data structures. No design pattern is needed here: a few small classes, each with one clear job, give the simplest and fastest solution.

The solution has just two building blocks:

1. A **custom hash set with 16 buckets** that keeps available dashers in the correct pick order.
2. A **custom hash map** that remembers which dasher is working on which order.

We will start with a simple version that uses only lists, spot its slow parts, and then fix them one by one.

---

## Understanding the Rules

**Which bucket does a dasher go to?**

```java
bucketIndex = (int) (((long) dasherId * 31 + 17) % 16);
```

The `(long)` cast is important. `dasherId` can be up to `100,000,000`, and `100,000,000 * 31` is too big for an `int`. Without the cast, the multiplication overflows and you can get a negative bucket index.

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

- **Strategy** for the "which dasher to pick" rule. Strategy is useful when you need to switch between different rules. This problem has only one fixed rule (lowest bucket first, then first in first out), so a Strategy interface would only add extra classes.
- **State** for the dasher life cycle (available or busy). State is useful when an object has many states that behave differently. A dasher has only two states, and where it is stored already tells us its state: inside the available set means available, inside the assignment map means busy.

The problem itself warns about **unnecessary abstractions**. So we skip patterns and put our effort into the data structures.

---

## Solution 1: Simple Approach Using Lists

### Idea

- Keep **16 `ArrayList` buckets** for available dashers.
- Keep active assignments in **two lists side by side**: `activeOrders.get(i)` is being delivered by `activeDashers.get(i)`.
- Answer every question ("is this order active?", "is this dasher busy?") by scanning these lists.

### Code

```java
import java.util.ArrayList;
import java.util.List;

public class DasherAssignmentService {
    // 16 buckets of available dashers, each bucket keeps arrival (FIFO) order
    List<List<Integer>> buckets = new ArrayList<>();

    // Active assignments kept side by side:
    // activeOrders.get(i) is being delivered by activeDashers.get(i)
    List<Integer> activeOrders = new ArrayList<>();
    List<Integer> activeDashers = new ArrayList<>();

    public DasherAssignmentService() {
        for (int i = 0; i < 16; i++) {
            buckets.add(new ArrayList<>());
        }
    }

    // Bucket formula from the problem. Cast to long first, or dasherId * 31 can overflow.
    int bucketIndex(int dasherId) {
        return (int) (((long) dasherId * 31 + 17) % 16);
    }

    public void addDasher(int dasherId) {
        List<Integer> bucket = buckets.get(bucketIndex(dasherId));
        if (bucket.contains(dasherId)) return;          // already available
        if (activeDashers.contains(dasherId)) return;   // busy with an active order
        bucket.add(dasherId);                           // append to the end
    }

    public int pickDasher(int orderId) {
        if (activeOrders.contains(orderId)) return -1;  // order already has a dasher
        for (List<Integer> bucket : buckets) {          // always start from bucket 0
            if (!bucket.isEmpty()) {
                int dasherId = bucket.remove(0);        // first in, first out
                activeOrders.add(orderId);
                activeDashers.add(dasherId);
                return dasherId;
            }
        }
        return -1;                                      // no dasher is available
    }

    public void orderDelivered(int orderId) {
        int i = activeOrders.indexOf(orderId);
        if (i == -1) return;                            // order is not active
        activeOrders.remove(i);                         // remove by index
        int dasherId = activeDashers.remove(i);
        buckets.get(bucketIndex(dasherId)).add(dasherId); // back to the end of its bucket
    }
}
```

### Problems With This Approach

Let `A` be the number of active orders and `k` be the number of dashers in one bucket.

- **Duplicate order check** in `pickDasher` scans all active orders: **O(A)**.
- **Busy dasher check** in `addDasher` scans all active dashers: **O(A)**.
- **Finding and removing** an order in `orderDelivered` scans the list and then shifts every element after it: **O(A)**.
- **`bucket.remove(0)`** on an `ArrayList` shifts every remaining dasher one step left: **O(k)**.

With thousands of active orders, every call has to walk through the whole assignment list. Let's fix that.

---

## Solution 2: Optimized Approach Using a Custom Hash Map

The logic stays the same. We only replace the slow parts with better data structures.

### Improvement 1: `LinkedList` buckets

A `LinkedList` removes its first element in **O(1)** because nothing has to shift. It is still a `List<Integer>`, so it follows the problem's rule for buckets.

### Improvement 2: A small custom hash map for assignments

Instead of one long list, we spread the assignments over many small buckets, just like a real hash map does:

- `key % number of buckets` decides the bucket.
- Each bucket holds a few `MapEntry(key, value)` pairs.
- When the map has more entries than buckets, it **doubles** the number of buckets, so every bucket stays short.

Now `get`, `put` and `remove` only look inside one short bucket, which is **O(1)** on average.

We use this map **twice**:

- `orderToDasher`: tells us if an order already has a dasher, and which dasher to free on delivery.
- `dasherToOrder`: tells us if a dasher is busy right now, without scanning every assignment.

All IDs are `0` or more, so `-1` can safely mean "not found". This keeps the code free of `null` checks.

### Improvement 3: One class for each job

| Class | Why we need it |
|---|---|
| `AvailableDasherSet` | The 16-bucket hash set. It owns the bucket formula and the pick order. |
| `IntHashMap` | A custom int to int map, because built-in maps are not allowed. |
| `MapEntry` | One key and value pair inside an `IntHashMap` bucket. |
| `DasherAssignmentService` | Connects the pieces and applies the rules. |

With this split, every service method is only a few lines and reads almost like the problem statement.

### How a Dasher Moves Through the System

```
addDasher(16)        available: bucket 1 = [16]   orderToDasher { }         dasherToOrder { }
pickDasher(101)      available: bucket 1 = [ ]    orderToDasher {101: 16}   dasherToOrder {16: 101}
orderDelivered(101)  available: bucket 1 = [16]   orderToDasher { }         dasherToOrder { }
```

### Code

```java
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Connects the pool of available dashers with the active order assignments.
 * Each method is a direct translation of the rules in the problem.
 */
public class DasherAssignmentService {
    AvailableDasherSet availableDashers;
    IntHashMap orderToDasher;   // active order -> its dasher
    IntHashMap dasherToOrder;   // busy dasher  -> its order

    public DasherAssignmentService() {
        availableDashers = new AvailableDasherSet();
        orderToDasher = new IntHashMap();
        dasherToOrder = new IntHashMap();
    }

    public void addDasher(int dasherId) {
        if (availableDashers.contains(dasherId)) return;   // already available
        if (dasherToOrder.containsKey(dasherId)) return;   // busy with an active order
        availableDashers.add(dasherId);
    }

    public int pickDasher(int orderId) {
        if (orderToDasher.containsKey(orderId)) return -1; // order already has a dasher
        int dasherId = availableDashers.removeNext();
        if (dasherId == -1) return -1;                     // no dasher is available
        orderToDasher.put(orderId, dasherId);
        dasherToOrder.put(dasherId, orderId);
        return dasherId;
    }

    public void orderDelivered(int orderId) {
        int dasherId = orderToDasher.remove(orderId);
        if (dasherId == -1) return;                        // order is not active
        dasherToOrder.remove(dasherId);
        availableDashers.add(dasherId);                    // back to the end of its bucket
    }
}

/**
 * Custom hash set of available dashers with exactly 16 buckets.
 * Inside a bucket, dashers stay in the order they arrived (FIFO).
 */
class AvailableDasherSet {
    int bucketCount = 16;
    List<List<Integer>> buckets = new ArrayList<>();

    AvailableDasherSet() {
        for (int i = 0; i < bucketCount; i++) {
            buckets.add(new LinkedList<>()); // LinkedList: removing the first dasher is O(1)
        }
    }

    // Formula from the problem. Cast to long first, or dasherId * 31 can overflow.
    int bucketIndex(int dasherId) {
        return (int) (((long) dasherId * 31 + 17) % bucketCount);
    }

    // Searches only the dasher's own bucket
    boolean contains(int dasherId) {
        return buckets.get(bucketIndex(dasherId)).contains(dasherId);
    }

    // Appends the dasher to the end of its bucket. Caller makes sure it is not already here.
    void add(int dasherId) {
        buckets.get(bucketIndex(dasherId)).add(dasherId);
    }

    // Removes and returns the first dasher of the lowest non-empty bucket, or -1 if all are empty
    int removeNext() {
        for (List<Integer> bucket : buckets) {
            if (!bucket.isEmpty()) return bucket.remove(0);
        }
        return -1;
    }
}

/**
 * A small hash map from int keys to int values, because built-in maps are not allowed.
 * IDs are never negative, so -1 safely means "not found".
 */
class IntHashMap {
    List<List<MapEntry>> buckets;
    int size = 0;

    IntHashMap() {
        buckets = createBuckets(16);
    }

    List<List<MapEntry>> createBuckets(int count) {
        List<List<MapEntry>> newBuckets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            newBuckets.add(new ArrayList<>());
        }
        return newBuckets;
    }

    List<MapEntry> bucketFor(int key) {
        return buckets.get(key % buckets.size());
    }

    // Returns the value stored for key, or -1 if key is missing
    int get(int key) {
        for (MapEntry entry : bucketFor(key)) {
            if (entry.key == key) return entry.value;
        }
        return -1;
    }

    boolean containsKey(int key) {
        return get(key) != -1;
    }

    // Adds or updates key. Doubles the buckets when the map gets crowded.
    void put(int key, int value) {
        List<MapEntry> bucket = bucketFor(key);
        for (MapEntry entry : bucket) {
            if (entry.key == key) {
                entry.value = value;
                return;
            }
        }
        bucket.add(new MapEntry(key, value));
        size++;
        if (size > buckets.size()) resize();
    }

    // Removes key and returns its value, or -1 if key is missing
    int remove(int key) {
        List<MapEntry> bucket = bucketFor(key);
        for (int i = 0; i < bucket.size(); i++) {
            if (bucket.get(i).key == key) {
                size--;
                return bucket.remove(i).value;
            }
        }
        return -1;
    }

    // Spreads all entries over twice as many buckets, so every bucket stays short
    void resize() {
        List<List<MapEntry>> oldBuckets = buckets;
        buckets = createBuckets(oldBuckets.size() * 2);
        for (List<MapEntry> bucket : oldBuckets) {
            for (MapEntry entry : bucket) {
                bucketFor(entry.key).add(entry);
            }
        }
    }
}

/** One key and value pair stored inside an IntHashMap bucket. */
class MapEntry {
    int key;
    int value;

    MapEntry(int key, int value) {
        this.key = key;
        this.value = value;
    }
}
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

- **Overflow in the bucket formula:** `(long) (dasherId * 31)` is still wrong, because the multiplication overflows before the cast. Cast first: `(long) dasherId * 31`.
- **Wrong `remove`:** on a `List<Integer>`, `list.remove(dasherId)` with an `int` removes by **index**, not by value. Use `list.remove(Integer.valueOf(dasherId))` to remove by value.
- **`Integer == Integer`:** comparing two `Integer` objects with `==` only works for small values (-128 to 127). Compare `int` values or use `equals`.
- **Scan not restarting:** every `pickDasher` call must start again from bucket `0`.
- **Freed dasher in the wrong place:** a delivered dasher goes to the **end** of its bucket, not the front.
- **Missing checks:** without the active order check, one order can get two dashers. Without the busy check, a dasher can be available and busy at the same time.