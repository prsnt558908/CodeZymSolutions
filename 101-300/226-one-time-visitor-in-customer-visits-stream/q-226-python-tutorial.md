# First One Time Visitor in Stream of Customer Visits in Python

#### Problem Statement
[https://codezym.com/question/226-one-time-visitor-in-customer-visits-stream](https://codezym.com/question/226-one-time-visitor-in-customer-visits-stream)


## Core Idea

For every customer we need to know two things: how many times they have visited, and when they first showed up.

A `dict` stores the visit counts. An ordered structure, like a queue, keeps customers in the order of their first visit, so the earliest one is always at the front.

The whole solution rests on one simple fact: **a visit count only goes up**. Once a customer visits a second time, they can never be a one-time visitor again. So we can remove them for good, and once they are gone, the answer is simply the first customer left in the order.

We will start with a simple brute force, see where it wastes time, and then improve it in two steps.


## Brute Force: Scan From the Start Every Time

The most direct idea is to keep two things:

- `visitCount`: a dict from customer id to their number of visits.
- `firstVisitOrder`: a list of customers in the order of their first visit.

To answer a query, walk the list from the start and return the first customer whose count is 1.

```python
def postCustomerVisit(self, customerId: int) -> None:
    count = self.visitCount.get(customerId, 0) + 1
    self.visitCount[customerId] = count
    if count == 1:
        self.firstVisitOrder.append(customerId)  # remember when they first came

def getFirstOneTimeVisitor(self) -> int:
    for customerId in self.firstVisitOrder:
        if self.visitCount[customerId] == 1:
            return customerId
    return -1
```

Recording a visit is O(1). But a query may walk the whole list, so it costs O(d), where d is the number of distinct customers.

**Where it wastes time:** say 25,000 customers each visit twice, and then we call `getFirstOneTimeVisitor()` 50,000 times. Every call walks past the same 25,000 repeat visitors and finds nothing. That is 50,000 x 25,000 = 1.25 billion checks.

We keep checking customers who can never be the answer again.


## Solution 1: Queue + Dict (Lazy Removal)

The fix comes straight from the core idea. When a repeat visitor is at the front, do not just skip them, **throw them away**. We will never need to look at them again.

We add customers at the back and remove them from the front. That is exactly what a queue does.

### Why these data structures?

- **`visitCount` (dict)**: customer ids go up to 1,000,000,000, which is far too big for a list indexed by id. A dict stores only the ids we actually see, with O(1) lookups.
- **`firstVisitOrder` (deque)**: keeps customers in the order of their first visit. `append()` adds at the back and `popleft()` removes from the front, both in O(1). A plain list would be slow here, because `pop(0)` shifts every remaining element, which is O(n).

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

### Python Code

```python
from collections import deque


class FirstOneTimeVisitor:
    """
    Finds the earliest customer who has visited exactly once.
    Repeat visitors are removed from the queue lazily, only when they reach the front.
    """

    def __init__(self):
        # customerId -> number of visits so far
        self.visitCount = {}
        # customers in the order of their first visit, earliest at the front
        self.firstVisitOrder = deque()

    def postCustomerVisit(self, customerId: int) -> None:
        count = self.visitCount.get(customerId, 0) + 1
        self.visitCount[customerId] = count

        # only the first visit adds a customer to the queue
        if count == 1:
            self.firstVisitOrder.append(customerId)

    def getFirstOneTimeVisitor(self) -> int:
        # drop repeat visitors from the front, they can never be the answer again
        while self.firstVisitOrder and self.visitCount[self.firstVisitOrder[0]] > 1:
            self.firstVisitOrder.popleft()
        return self.firstVisitOrder[0] if self.firstVisitOrder else -1
```


## Solution 2: OrderedDict + Dict (Instant Removal)

Solution 1 is fast overall, but one query can still be slow if many repeat visitors have piled up at the front.

We can avoid that by removing a customer **the moment they visit a second time**. For that we need a structure that can:

- keep customers in the order they were added,
- remove any customer, even from the middle, in O(1),
- give us the first customer in O(1).

A deque cannot remove from the middle quickly. An `OrderedDict` can do all three.

### Why OrderedDict?

Python has no built-in ordered set, so we use an `OrderedDict` and only care about its keys.

It is a dict that also links its keys in the order they were added. The dict part finds any customer in O(1). The linked part remembers the order, so the first key is always the oldest one still present.

Think of it as a queue that can also delete from the middle in O(1).

A plain `dict` also keeps insertion order, but finding its first key gets slower and slower when many keys are deleted from the front. `OrderedDict` does not have this problem.

### How it works

We keep the same `visitCount` dict and replace the deque with an `OrderedDict` called `oneTimeVisitors`. It holds only customers who have visited exactly once.

- On a first visit, add the customer to `oneTimeVisitors`.
- On a second visit, remove them from it. On the third or later visit they are already gone, so `pop(customerId, None)` does nothing.
- The answer is simply the first key of `oneTimeVisitors`, or -1 if it is empty.

No cleanup loop is needed, because `oneTimeVisitors` never holds a repeat visitor.

### Complexity

- `postCustomerVisit`: O(1)
- `getFirstOneTimeVisitor`: O(1), with no cleanup loop
- Space: O(d)

### Python Code

```python
from collections import OrderedDict


class FirstOneTimeVisitor:
    """
    Finds the earliest customer who has visited exactly once.
    Repeat visitors are removed right away, so the first key is always the answer.
    """

    def __init__(self):
        # customerId -> number of visits so far
        self.visitCount = {}
        # customers with exactly one visit, kept in the order they visited
        # only the keys matter, the values are just placeholders
        self.oneTimeVisitors = OrderedDict()

    def postCustomerVisit(self, customerId: int) -> None:
        count = self.visitCount.get(customerId, 0) + 1
        self.visitCount[customerId] = count

        if count == 1:
            self.oneTimeVisitors[customerId] = True  # first visit
        else:
            # repeat visit, no longer a one-time visitor
            self.oneTimeVisitors.pop(customerId, None)

    def getFirstOneTimeVisitor(self) -> int:
        if not self.oneTimeVisitors:
            return -1
        # the oldest key still present is the earliest one-time visitor
        return next(iter(self.oneTimeVisitors))
```


## Summary

| Approach | `postCustomerVisit` | `getFirstOneTimeVisitor` | Space |
|---|---|---|---|
| Brute Force | O(1) | O(d) | O(d) |
| Queue (deque) + Dict | O(1) | O(1) amortized | O(d) |
| OrderedDict + Dict | O(1) | O(1) | O(d) |

Both final solutions handle 100,000 calls easily. Solution 1 needs only a queue and a dict. Solution 2 is a little shorter and removes repeat visitors right away, so no single query has to do a big cleanup.