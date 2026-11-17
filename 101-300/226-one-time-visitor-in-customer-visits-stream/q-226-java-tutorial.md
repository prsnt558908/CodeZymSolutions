# First One Time Visitor in Stream of Customer Visits in Java

#### Problem Statement
[https://codezym.com/question/226-one-time-visitor-in-customer-visits-stream](https://codezym.com/question/226-one-time-visitor-in-customer-visits-stream)


## Core Idea

For every customer we need to know two things: how many times they have visited, and when they first showed up.

A `HashMap` stores the visit counts. An ordered structure, like a queue, keeps customers in the order of their first visit, so the earliest one is always at the front.

The whole solution rests on one simple fact: **a visit count only goes up**. Once a customer visits a second time, they can never be a one-time visitor again. So we can remove them for good, and once they are gone, the answer is simply the first customer left in the order.

We will start with a simple brute force, see where it wastes time, and then improve it in two steps.


## Brute Force: Scan From the Start Every Time

The most direct idea is to keep two things:

- `visitCount`: a map from customer id to their number of visits.
- `firstVisitOrder`: a list of customers in the order of their first visit.

To answer a query, walk the list from the start and return the first customer whose count is 1.

```java
public void postCustomerVisit(int customerId) {
    int count = visitCount.getOrDefault(customerId, 0) + 1;
    visitCount.put(customerId, count);
    if (count == 1) {
        firstVisitOrder.add(customerId);   // remember when they first came
    }
}

public int getFirstOneTimeVisitor() {
    for (int customerId : firstVisitOrder) {
        if (visitCount.get(customerId) == 1) {
            return customerId;
        }
    }
    return -1;
}
```

Recording a visit is O(1). But a query may walk the whole list, so it costs O(d), where d is the number of distinct customers.

**Where it wastes time:** say 25,000 customers each visit twice, and then we call `getFirstOneTimeVisitor()` 50,000 times. Every call walks past the same 25,000 repeat visitors and finds nothing. That is 50,000 x 25,000 = 1.25 billion checks.

We keep checking customers who can never be the answer again.


## Solution 1: Queue + HashMap (Lazy Removal)

The fix comes straight from the core idea. When a repeat visitor is at the front, do not just skip them, **throw them away**. We will never need to look at them again.

We add customers at the back and remove them from the front. That is exactly what a queue does.

### Why these data structures?

- **`HashMap<Integer, Integer> visitCount`**: customer ids go up to 1,000,000,000, which is far too big for an array indexed by id. A map stores only the ids we actually see, with O(1) lookups.
- **`Queue<Integer> firstVisitOrder`**: keeps customers in the order of their first visit. Adding at the back and removing from the front are both O(1).

### How it works

**`postCustomerVisit(customerId)`**

1. Increase the visit count of the customer.
2. If this is their first visit, add them to the back of the queue.
3. If it is a repeat visit, leave the queue alone.

**`getFirstOneTimeVisitor()`**

1. While the customer at the front has visited more than once, remove them.
2. The customer now at the front is the answer. If the queue is empty, return -1.

Repeat visitors may sit in the middle of the queue for a while. That is fine, because we only ever read the front. They get removed once they reach it.

This is called **lazy removal**: we clean up only when we need the answer, not when the visit happens.

### Dry Run (Examples 1 and 2)

| Call | Visit Counts | Queue (front first) | Returns |
|---|---|---|---|
| `postCustomerVisit(10)` | 10:1 | 10 | |
| `postCustomerVisit(20)` | 10:1, 20:1 | 10, 20 | |
| `postCustomerVisit(10)` | **10:2**, 20:1 | 10, 20 | |
| `postCustomerVisit(30)` | 10:2, 20:1, 30:1 | 10, 20, 30 | |
| `getFirstOneTimeVisitor()` | no change | 20, 30 (10 removed) | **20** |
| `postCustomerVisit(20)` | 10:2, **20:2**, 30:1 | 20, 30 | |
| `postCustomerVisit(40)` | 10:2, 20:2, 30:1, 40:1 | 20, 30, 40 | |
| `getFirstOneTimeVisitor()` | no change | 30, 40 (20 removed) | **30** |

### Complexity

Every customer is added to the queue once and removed at most once. So even if one query removes many customers, the total work over all calls is O(n), where n is the number of calls.

- `postCustomerVisit`: O(1)
- `getFirstOneTimeVisitor`: O(1) amortized, which means O(1) on average across all calls
- Space: O(d)

### Java Code

```java
import java.util.*;

/**
 * Finds the earliest customer who has visited exactly once.
 * Repeat visitors are removed from the queue lazily, only when they reach the front.
 */
public class FirstOneTimeVisitor {

    // customerId -> number of visits so far
    Map<Integer, Integer> visitCount;

    // customers in the order of their first visit, earliest at the front
    Queue<Integer> firstVisitOrder;

    public FirstOneTimeVisitor() {
        visitCount = new HashMap<>();
        firstVisitOrder = new ArrayDeque<>();
    }

    public void postCustomerVisit(int customerId) {
        int count = visitCount.getOrDefault(customerId, 0) + 1;
        visitCount.put(customerId, count);

        // only the first visit adds a customer to the queue
        if (count == 1) {
            firstVisitOrder.add(customerId);
        }
    }

    public int getFirstOneTimeVisitor() {
        // drop repeat visitors from the front, they can never be the answer again
        while (!firstVisitOrder.isEmpty() && visitCount.get(firstVisitOrder.peek()) > 1) {
            firstVisitOrder.poll();
        }
        return firstVisitOrder.isEmpty() ? -1 : firstVisitOrder.peek();
    }
}
```


## Solution 2: LinkedHashSet + HashMap (Instant Removal)

Solution 1 is fast overall, but one query can still be slow if many repeat visitors have piled up at the front.

We can avoid that by removing a customer **the moment they visit a second time**. For that we need a structure that can:

- keep customers in the order they were added,
- remove any customer, even from the middle, in O(1),
- give us the first customer in O(1).

A queue cannot remove from the middle quickly. A `LinkedHashSet` can do all three.

### Why LinkedHashSet?

It is a hash set that also links its elements in the order they were added. The hash part finds any customer in O(1). The linked part remembers the order, so the first element is always the oldest one still in the set.

Think of it as a queue that can also delete from the middle in O(1).

### How it works

We keep the same `visitCount` map and replace the queue with `LinkedHashSet<Integer> oneTimeVisitors`. It holds only customers who have visited exactly once.

- On a first visit, add the customer to `oneTimeVisitors`.
- On a second visit, remove them from it. On the third or later visit they are already gone, so the remove does nothing.
- The answer is simply the first element of `oneTimeVisitors`, or -1 if it is empty.

No cleanup loop is needed, because the set never holds a repeat visitor.

### Complexity

- `postCustomerVisit`: O(1)
- `getFirstOneTimeVisitor`: O(1), with no cleanup loop
- Space: O(d)

### Java Code

```java
import java.util.*;

/**
 * Finds the earliest customer who has visited exactly once.
 * Repeat visitors are removed right away, so the first element is always the answer.
 */
public class FirstOneTimeVisitor {

    // customerId -> number of visits so far
    Map<Integer, Integer> visitCount;

    // customers with exactly one visit, kept in the order they visited
    LinkedHashSet<Integer> oneTimeVisitors;

    public FirstOneTimeVisitor() {
        visitCount = new HashMap<>();
        oneTimeVisitors = new LinkedHashSet<>();
    }

    public void postCustomerVisit(int customerId) {
        int count = visitCount.getOrDefault(customerId, 0) + 1;
        visitCount.put(customerId, count);

        if (count == 1) {
            oneTimeVisitors.add(customerId);      // first visit
        } else {
            oneTimeVisitors.remove(customerId);   // repeat visit, no longer a one-time visitor
        }
    }

    public int getFirstOneTimeVisitor() {
        if (oneTimeVisitors.isEmpty()) {
            return -1;
        }
        // the oldest element still in the set is the earliest one-time visitor
        return oneTimeVisitors.iterator().next();
    }
}
```


## Summary

| Approach | `postCustomerVisit` | `getFirstOneTimeVisitor` | Space |
|---|---|---|---|
| Brute Force | O(1) | O(d) | O(d) |
| Queue + HashMap | O(1) | O(1) amortized | O(d) |
| LinkedHashSet + HashMap | O(1) | O(1) | O(d) |

Both final solutions handle 100,000 calls easily. Solution 1 needs only a queue and a map. Solution 2 is a little shorter and removes repeat visitors right away, so no single query has to do a big cleanup.