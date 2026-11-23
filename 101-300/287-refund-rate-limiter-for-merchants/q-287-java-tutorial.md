# Refund Rate Limiter for Merchants in Java

#### Problem Statement
[https://codezym.com/question/287-refund-rate-limiter-for-merchants](https://codezym.com/question/287-refund-rate-limiter-for-merchants)


## Core Idea

For every merchant we remember the times of their recent allowed refunds. When a new request comes in, we first throw away the times that are 5 or more minutes old.

If fewer than `maxRefunds` times are left, the refund is allowed and its time is saved. Otherwise it is rejected and nothing is saved.

The whole solution rests on one simple fact: **for one merchant, time never goes backward**. So once a refund falls out of the window, it can never come back into it. We can delete it for good.

New times join at the back and old times leave from the front. That is exactly what a **queue** does. So the solution is a `HashMap` from merchant id to a queue of timestamps.

We will start with a simple brute force, see where it wastes time and memory, and then fix it.


## Brute Force: Keep Every Refund Time

The most direct idea is to keep a list of all allowed refund times for each merchant. For every request, count how many of them are inside the window.

```java
// merchantId -> every allowed refund time, nothing is ever removed
Map<String, List<Long>> merchantRefunds = new HashMap<>();

public boolean allowRefund(String merchantId, long timestamp) {
    List<Long> refundTimes = merchantRefunds.computeIfAbsent(merchantId, k -> new ArrayList<>());

    // count the refunds that are still inside the window
    int count = 0;
    for (long t : refundTimes) {
        if (t > timestamp - 300_000) {
            count++;
        }
    }

    if (count >= maxRefunds) {
        return false;   // limit reached, do not record this request
    }
    refundTimes.add(timestamp);
    return true;
}
```

This gives the right answers. But every call walks the whole list, so it costs O(k), where k is the number of refunds the merchant has ever been allowed.

**Where it wastes time and memory:** the list never shrinks. Say a merchant gets 100,000 refunds allowed over several days. The last call still walks past 99,999 old times, and almost all of them expired long ago.

Over all calls that is about 100,000 x 100,000 / 2 = 5 billion checks. And all those old times stay in memory forever, even though they can never count again.


## Solution: HashMap + Queue (Sliding Window)

The fix comes straight from the core idea. An expired refund can never count again, so instead of skipping it on every call, **delete it**.

Expired times are always the oldest ones, so they sit at the front of the list. But removing from the front of an `ArrayList` is slow, because every other item has to shift one place left.

A queue fixes that. New times join at the back, expired times leave from the front, and both steps are O(1).

### Why these data structures?

- **`Map<String, Queue<Long>> merchantRefunds`**: each merchant has an independent limit, so each one gets its own queue. The map finds that queue in O(1). Also, timestamps are only in order for the same merchant, so mixing merchants in one queue would break the "oldest at the front" rule.
- **`Queue<Long>` (an `ArrayDeque`)**: holds a merchant's allowed refund times, oldest at the front. Adding at the back and removing from the front are both O(1).

### How it works

**`allowRefund(merchantId, timestamp)`**

1. Get the merchant's queue. A new merchant starts with an empty one.
2. While the oldest time in the queue is `<= timestamp - 300,000`, remove it. It is outside the window.
3. If the queue still holds `maxRefunds` times, the limit is reached. Return `false` and record nothing.
4. Otherwise add `timestamp` to the back and return `true`.

Note the `<=` in step 2. A refund that is exactly 5 minutes old is already outside the window.

### Dry Run (Example 3)

`maxRefunds = 1`

| Call | Remove times `<=` | Queue after removal | Returns | Queue at the end |
|---|---|---|---|---|
| `allowRefund("shop-9", 50000)` | -250000 | empty | **true** | 50000 |
| `allowRefund("shop-9", 349999)` | 49999 | 50000 | **false** | 50000 |
| `allowRefund("shop-9", 350000)` | 50000 | empty | **true** | 350000 |

At 349,999 the old refund is still 1 ms short of 5 minutes, so it counts and the request is rejected. At 350,000 it is exactly 5 minutes old, so it is removed and the new refund fits.

The rejected call at 349,999 was not added to the queue. If it had been, it would still be inside the window at 350,000 and would wrongly block that refund.

### Complexity

Each timestamp is added to a queue once and removed at most once. So even if one call removes many timestamps, the total work over all calls is O(n), where n is the number of calls.

- `allowRefund`: O(1) amortized, which means O(1) on average across all calls
- Space: O(m x maxRefunds) in the worst case, where m is the number of merchants. We only add a time when the queue has room, so a queue never holds more than `maxRefunds` timestamps.

### Java Code

```java
import java.util.*;

/**
 * Limits each merchant to at most maxRefunds refunds in any rolling 5-minute window.
 * For every merchant we keep a queue with the timestamps of its allowed refunds,
 * oldest at the front. Expired timestamps are removed before each decision.
 */
public class RefundRateLimiter {

    // length of the rolling window: 5 minutes in milliseconds
    long windowMillis = 300_000;

    // most refunds one merchant can make inside one window
    int maxRefunds;

    // merchantId -> timestamps of that merchant's allowed refunds, oldest at the front
    Map<String, Queue<Long>> merchantRefunds;

    public RefundRateLimiter(int maxRefunds) {
        this.maxRefunds = maxRefunds;
        merchantRefunds = new HashMap<>();
    }

    public boolean allowRefund(String merchantId, long timestamp) {
        // a new merchant starts with an empty queue
        Queue<Long> refundTimes = merchantRefunds.computeIfAbsent(merchantId, k -> new ArrayDeque<>());

        // remove expired refunds: a time t is outside the window when t <= timestamp - windowMillis
        while (!refundTimes.isEmpty() && refundTimes.peek() <= timestamp - windowMillis) {
            refundTimes.poll();
        }

        // limit reached: reject, and do not record this request
        if (refundTimes.size() >= maxRefunds) {
            return false;
        }

        refundTimes.add(timestamp);
        return true;
    }
}
```