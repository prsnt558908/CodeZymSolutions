# Design Website Customer Visit Tracking Service in Python

#### Problem Statement
[https://codezym.com/question/209-design-website-customer-visit-tracker](https://codezym.com/question/209-design-website-customer-visit-tracker)


The service has to answer two questions quickly: **"When did this customer last visit?"** and **"Who is the earliest customer that has visited only once?"**


The first question is easy. A dict (hash map) from customer id to a small `Customer` object answers it in `O(1)`.


For the second question, we keep one-time visitors in a **min heap**, sorted by first visit time and then by customer id.


When a customer visits again, we do not dig them out of the heap right away. We throw them out later, only when they reach the top. After this cleanup, the top of the heap is always the answer.

---

## Solution 1: Brute Force


Keep one dict: `self.customers`, from customer id to `Customer`.


`Customer` is a small class that holds everything about one customer: `customer_id`, `visit_count`, `first_visit_timestamp` and `last_visit_timestamp`. Keeping these together means one dict lookup gives us all the facts about a customer.


- `postCustomerVisit`: on the first visit, create a new `Customer`. On later visits, increase `visit_count` and update `last_visit_timestamp`.
- `getLastLoginTimestamp`: look up the customer. Return `-1` if they are not in the dict.
- `getFirstOneTimeVisitor`: check every customer and keep the best one-time visitor.


The scan looks like this:

```python
def getFirstOneTimeVisitor(self) -> str:
    # Scan every customer and keep only the one-time visitors
    one_time = [c for c in self.customers.values() if c.visit_count == 1]
    if not one_time:
        return ""
    # Earliest first visit wins. A tie goes to the smaller id.
    best = min(one_time,
               key=lambda c: (c.first_visit_timestamp, c.customer_id))
    return best.customer_id
```


The `key` returns a tuple. Python compares tuples item by item, so it compares the time first and the id only on a tie. That is exactly the order we need.


### Problem With This Approach


The first two methods are `O(1)`. But `getFirstOneTimeVisitor` checks every customer on every call, which is `O(N)`.


There can be up to 1,000,000 calls. If 500,000 different customers visit first and then we get 500,000 queries, that is 500,000 × 500,000 = 250 billion steps. Far too slow.


We need a way to find the best one-time visitor without looking at everyone.

---

## Solution 2: Min Heap with Lazy Removal


We keep the same `Customer` class and `customers` dict. We add one more structure: a min heap of one-time visitors.


### Key Observation


A customer only moves forward:


**new customer → one-time visitor → recurrent visitor**


They become a one-time visitor exactly once, on their first visit. They stop being one exactly once, on their second visit. After that, they can never be a one-time visitor again.


### Why a Min Heap


We want the "smallest" one-time visitor, where smaller means:

1. an earlier first visit, and
2. on a tie, the smaller customer id.


A min heap always keeps the smallest item on top. Reading the top is `O(1)`. Adding an item or removing the top is `O(log N)`.


In Python, the `heapq` module turns a plain list into a min heap. We push `(first_visit_timestamp, customer_id)` tuples into it. Thanks to the same tuple rule as before, the heap orders them by time first and id second, with no extra code.


### Why Not a Simple Queue?


Timestamps never go down, so customers become one-time visitors in time order. A plain queue looks like enough.


Ties break it. In Example 3, `cust2` arrives before `cust1`, both at time `100`. A queue would return `cust2`, but the answer is `cust1`. The heap sorts ties by id, so it gets this right.


### Lazy Removal


When a customer visits for the second time, they should leave the heap. But a heap can only remove its top quickly. Removing an item from the middle is `O(N)`: we have to find it first and then repair the heap.


So we leave them in the heap and just increase their `visit_count`.


Later, `getFirstOneTimeVisitor` looks at the top of the heap. The heap item only holds a time and an id, so we use the id to read the customer's `visit_count` from the dict:

- If the heap is empty, return `""`.
- If the top customer has exactly one visit, return their id.
- Otherwise, pop them and look again.


Popping is always safe because of the key observation: a recurrent customer never becomes a one-time visitor again.


### Dry Run (Example 2)

| Call | What happens | Heap (smallest first) | Output |
|---|---|---|---|
| `postCustomerVisit("john", 20)` | john is new, add him to the heap | `(20, "john")` | |
| `postCustomerVisit("mike", 25)` | mike is new, add him to the heap | `(20, "john")`, `(25, "mike")` | |
| `postCustomerVisit("john", 30)` | john now has 2 visits, heap is not touched | `(20, "john")`, `(25, "mike")` | |
| `getFirstOneTimeVisitor()` | top john is recurrent, pop him. New top mike has 1 visit | `(25, "mike")` | `"mike"` |


### Complexity

`N` is the number of distinct customers.

| Method | Time |
|---|---|
| `postCustomerVisit` | `O(log N)` on a first visit, `O(1)` otherwise |
| `getLastLoginTimestamp` | `O(1)` |
| `getFirstOneTimeVisitor` | `O(log N)` amortized |


Amortized means "on average over all calls". A single call may pop many customers, but each customer enters the heap once and leaves it at most once. So all the pops together cost at most `O(N log N)`.


**Space:** `O(N)`. One `Customer` in the dict and one small tuple in the heap for each customer.


### Code

```python
import heapq


class Customer:
    """Everything we know about one customer."""

    def __init__(self, customer_id: str, timestamp: int):
        self.customer_id = customer_id
        self.visit_count = 1
        self.first_visit_timestamp = timestamp
        self.last_visit_timestamp = timestamp


class CustomerVisitTracker:

    def __init__(self):
        # customer id -> Customer, gives O(1) lookups
        self.customers = {}

        # Min heap of (first_visit_timestamp, customer_id) for customers
        # who may still be one-time visitors.
        # Top = earliest first visit. On a tie, the smaller customer id.
        # Recurrent customers are removed lazily, only when they reach the top.
        self.one_time_visitors = []

    def postCustomerVisit(self, customerId: str,
                          currentTimestamp: int) -> None:
        customer = self.customers.get(customerId)

        if customer is None:
            # First visit: a new one-time visitor
            customer = Customer(customerId, currentTimestamp)
            self.customers[customerId] = customer
            heapq.heappush(self.one_time_visitors,
                           (customer.first_visit_timestamp, customerId))
            return

        # Second or later visit: the customer is now recurrent.
        # We leave it in the heap. getFirstOneTimeVisitor() removes it later.
        customer.visit_count += 1

        # Timestamps never go down, so the newest visit is the latest login
        customer.last_visit_timestamp = currentTimestamp

    def getLastLoginTimestamp(self, customerId: str) -> int:
        customer = self.customers.get(customerId)
        return -1 if customer is None else customer.last_visit_timestamp

    def getFirstOneTimeVisitor(self) -> str:
        while self.one_time_visitors:
            _, customer_id = self.one_time_visitors[0]  # peek at the top
            if self.customers[customer_id].visit_count == 1:
                return customer_id
            # A recurrent customer never becomes a one-time visitor again,
            # so it is safe to throw it away for good
            heapq.heappop(self.one_time_visitors)
        return ""
```