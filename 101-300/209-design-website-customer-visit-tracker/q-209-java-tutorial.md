# Design Website Customer Visit Tracking Service in Java

#### Problem Statement
[https://codezym.com/question/209-design-website-customer-visit-tracker](https://codezym.com/question/209-design-website-customer-visit-tracker)


The service has to answer two questions quickly: **"When did this customer last visit?"** and **"Who is the earliest customer that has visited only once?"**


The first question is easy. A hash map from customer id to a small `Customer` object answers it in `O(1)`.


For the second question, we keep the one-time visitors in a **min heap**, sorted by first visit time and then by customer id.


When a customer visits again, we do not dig them out of the heap right away. We throw them out later, only when they reach the top. After this cleanup, the top of the heap is always the answer.

---

## Solution 1: Brute Force


Keep one map: `Map<String, Customer> customers`.


`Customer` is a small class that holds everything about one customer: `customerId`, `visitCount`, `firstVisitTimestamp` and `lastVisitTimestamp`. Keeping these together means one map lookup gives us all the facts about a customer.


- `postCustomerVisit`: on the first visit, create a new `Customer`. On later visits, increase `visitCount` and update `lastVisitTimestamp`.
- `getLastLoginTimestamp`: look up the customer. Return `-1` if they are not in the map.
- `getFirstOneTimeVisitor`: check every customer and keep the best one-time visitor.


The scan looks like this:

```java
// Scan every customer and keep the best one-time visitor
public String getFirstOneTimeVisitor() {
    Customer best = null;
    for (Customer customer : customers.values()) {
        if (customer.visitCount > 1) {
            continue; // recurrent visitor, skip
        }
        boolean earlier = best == null
                || customer.firstVisitTimestamp < best.firstVisitTimestamp
                || (customer.firstVisitTimestamp == best.firstVisitTimestamp
                        && customer.customerId.compareTo(best.customerId) < 0);
        if (earlier) {
            best = customer;
        }
    }
    return best == null ? "" : best.customerId;
}
```


### Problem With This Approach


The first two methods are `O(1)`. But `getFirstOneTimeVisitor` checks every customer on every call, which is `O(N)`.


There can be up to 1,000,000 calls. If 500,000 different customers visit first and then we get 500,000 queries, that is 500,000 × 500,000 = 250 billion steps. Far too slow.


We need a way to find the best one-time visitor without looking at everyone.

---

## Solution 2: Min Heap with Lazy Removal


We keep the same `Customer` class and `customers` map. We add one more structure: a min heap of one-time visitors.


### Key Observation


A customer only moves forward:


**new customer → one-time visitor → recurrent visitor**


They become a one-time visitor exactly once, on their first visit. They stop being one exactly once, on their second visit. After that, they can never be a one-time visitor again.


### Why a Min Heap


We want the "smallest" one-time visitor, where smaller means:

1. an earlier first visit, and
2. on a tie, the smaller customer id.


A min heap always keeps the smallest item on top. Reading the top is `O(1)`. Adding an item or removing the top is `O(log N)`.


In Java, `PriorityQueue` is a min heap. We give it a comparator that compares `firstVisitTimestamp` first and `customerId` second.


### Why Not a Simple Queue?


Timestamps never go down, so customers become one-time visitors in time order. A plain queue looks like enough.


Ties break it. In Example 3, `cust2` arrives before `cust1`, both at time `100`. A queue would return `cust2`, but the answer is `cust1`. The heap sorts ties by id, so it gets this right.


### Lazy Removal


When a customer visits for the second time, they should leave the heap. But a heap can only remove its top quickly. Removing an item from the middle of a `PriorityQueue` is `O(N)`.


So we leave them in the heap and just increase their `visitCount`.


Later, `getFirstOneTimeVisitor` looks at the top of the heap:

- If the heap is empty, return `""`.
- If the top customer has exactly one visit, return their id.
- Otherwise, pop them and look again.


Popping is always safe because of the key observation: a recurrent customer never becomes a one-time visitor again.


Changing `visitCount` while a customer sits in the heap is safe too. The heap only sorts by `firstVisitTimestamp` and `customerId`, and these never change.


### Dry Run (Example 2)

| Call | What happens | Heap (smallest first) | Output |
|---|---|---|---|
| `postCustomerVisit("john", 20)` | john is new, add him to the heap | john(20) | |
| `postCustomerVisit("mike", 25)` | mike is new, add him to the heap | john(20), mike(25) | |
| `postCustomerVisit("john", 30)` | john now has 2 visits, heap is not touched | john(20), mike(25) | |
| `getFirstOneTimeVisitor()` | top john is recurrent, pop him. New top mike has 1 visit | mike(25) | `"mike"` |


### Complexity

`N` is the number of distinct customers.

| Method | Time |
|---|---|
| `postCustomerVisit` | `O(log N)` on a first visit, `O(1)` otherwise |
| `getLastLoginTimestamp` | `O(1)` |
| `getFirstOneTimeVisitor` | `O(log N)` amortized |


Amortized means "on average over all calls". A single call may pop many customers, but each customer enters the heap once and leaves it at most once. So all the pops together cost at most `O(N log N)`.


**Space:** `O(N)`. The map and the heap point to the same `Customer` objects.


### Code

```java
import java.util.*;

// Everything we know about one customer
class Customer {
    String customerId;
    int firstVisitTimestamp;
    int lastVisitTimestamp;
    int visitCount;

    Customer(String customerId, int timestamp) {
        this.customerId = customerId;
        this.firstVisitTimestamp = timestamp;
        this.lastVisitTimestamp = timestamp;
        this.visitCount = 1;
    }
}

public class CustomerVisitTracker {

    // customerId -> Customer, gives O(1) lookups
    Map<String, Customer> customers;

    // Min heap of customers who may still be one-time visitors.
    // Top = earliest first visit. On a tie, the smaller customerId.
    // Recurrent customers are removed lazily, only when they reach the top.
    PriorityQueue<Customer> oneTimeVisitors;

    public CustomerVisitTracker() {
        customers = new HashMap<>();
        oneTimeVisitors = new PriorityQueue<>((a, b) -> {
            if (a.firstVisitTimestamp != b.firstVisitTimestamp) {
                return Integer.compare(a.firstVisitTimestamp, b.firstVisitTimestamp);
            }
            return a.customerId.compareTo(b.customerId);
        });
    }

    public void postCustomerVisit(String customerId, int currentTimestamp) {
        Customer customer = customers.get(customerId);

        if (customer == null) {
            // First visit: a new one-time visitor
            customer = new Customer(customerId, currentTimestamp);
            customers.put(customerId, customer);
            oneTimeVisitors.add(customer);
            return;
        }

        // Second or later visit: the customer is now recurrent.
        // We leave it in the heap. getFirstOneTimeVisitor() removes it later.
        customer.visitCount++;

        // Timestamps never go down, so the newest visit is the latest login
        customer.lastVisitTimestamp = currentTimestamp;
    }

    public int getLastLoginTimestamp(String customerId) {
        Customer customer = customers.get(customerId);
        return customer == null ? -1 : customer.lastVisitTimestamp;
    }

    public String getFirstOneTimeVisitor() {
        while (!oneTimeVisitors.isEmpty()) {
            Customer top = oneTimeVisitors.peek();
            if (top.visitCount == 1) {
                return top.customerId;
            }
            // A recurrent customer never becomes a one-time visitor again,
            // so it is safe to throw it away for good
            oneTimeVisitors.poll();
        }
        return "";
    }
}
```