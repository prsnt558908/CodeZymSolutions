# Design Dasher Payout Service in Java

#### Problem Statement
[https://codezym.com/question/97-design-dasher-payout-service](https://codezym.com/question/97-design-dasher-payout-service)

## Core Idea

A dasher earns `basePayRate` cents for every minute they spend on an order. When orders overlap, each minute pays `ongoingDeliveries * basePayRate`. It sounds like we must find all the overlaps, but we don't. Paying `2 * basePayRate` for a minute where 2 orders overlap is the same as paying each of those 2 orders `basePayRate`. So every order simply pays for its own minutes, `end - start + 1`, and the overlap rule takes care of itself. We start with a simple brute force, improve it step by step, and discuss common follow-up questions at the end.


For each dasher we keep just two running numbers: total delivery minutes and completed deliveries. A small map holds half-finished orders until their other record arrives, because START and END can come in any order and even in different calls. Payout then becomes one line of math.


No design pattern is needed here. Every dasher is paid with the same formula, only the numbers (rate and bonus) change. So a few plain classes and hash maps give the simplest and fastest design.

## Interview Focus

The emphasis in this question is on **API design and clean code**. The interviewer is looking more for brainstorming and requirement gathering than for an end-to-end working solution.


So spend the first few minutes asking questions. Here are good ones, with the answers this problem gives:

- Are both the start minute and the end minute paid? Yes, an order pays for `end - start + 1` minutes.
- Can START and END arrive out of order, or in different calls? Yes.
- Which orders count toward the bonus? Only the ones with both START and END.
- What if `start > end`? The order pays for 0 minutes.
- Is payout for a single day? Yes. In a real service, `payout()` would also take a `date`.


For the API, money stays an `int` in cents, so there are no decimal rounding errors. For clean code, each class has one job: `DasherPayoutService` reads the input, `Dasher` keeps one dasher's pay data, and `Order` holds one order's times.


The most common follow-up questions, and how to answer them, are discussed at the end.

## Do We Need a Design Pattern?

Two patterns look useful at first, but here they only add extra classes.


**Strategy pattern for pay rules:** Strategy helps when the *way* we calculate pay changes, for example one dasher paid per minute and another paid per order. Here every dasher uses the same formula. Only the numbers differ (rate, bonus amount, bonus target), and numbers are just data that we store in a `Dasher` object. Even the follow-ups at the end, country rates and peak hour pay, stay as plain data.


**State pattern for an order:** An order moves from "waiting" to "complete". But START and END can arrive in any order, so the only question is: do we have both times yet? Two fields, `start` and `end`, answer that. Separate state classes would be a lot of code for a two-field check.

## Solution 1: Brute Force (Count Every Minute)

The most direct way is to follow the payment rule word by word. For every minute of the day, count the ongoing deliveries and pay `ongoingDeliveries * basePayRate` for that minute.

### Storing the data

- `Map<String, Dasher> dashers`: finds a dasher by id in O(1). Order ids like `O1` can repeat for different dashers, so each dasher keeps its own orders.
- `Dasher`: keeps one dasher's pay settings and its `orders` map (`orderId -> Order`) together, instead of spreading them over many maps.
- `Order`: holds `start` and `end` as minutes of the day, so `09:00` becomes `540`. We use `-1` for "not received yet", because `0` is a valid time (`00:00`).


START and END of an order may arrive in any order, even in different calls. So we just fill in whichever half arrives and calculate nothing until `payout()` is called.

### Calculating payout

1. Create `ongoing[1440]`, one slot for every minute of the day.
2. For every order that has both START and END, count it as a completed delivery and add 1 to every minute from `start` to `end`. If `start > end`, the loop never runs, so the order adds 0 minutes.
3. Base pay is the sum of `ongoing[minute] * basePayRate` over all minutes.
4. Bonus is `(completedDeliveries / deliveryCountsToGetBonus) * bonusPay`. Integer division gives the number of full bonus groups.

```java
import java.util.*;

/**
 * One order of a dasher.
 * Times are minutes of the day (09:00 -> 540).
 * -1 means "not received yet", because 0 is a valid time (00:00).
 */
class Order {
    int start = -1;
    int end = -1;

    boolean isComplete() {
        return start != -1 && end != -1;
    }
}

/**
 * Pay settings and all orders of one dasher.
 */
class Dasher {
    int basePayRate;              // cents per minute for each ongoing delivery
    int bonusPay;                 // cents for every group of completed deliveries
    int deliveryCountsToGetBonus; // deliveries needed for one bonus
    Map<String, Order> orders = new HashMap<>(); // orderId -> order

    void addRecord(String orderId, boolean isStart, int time) {
        Order order = orders.get(orderId);
        if (order == null) {
            order = new Order();
            orders.put(orderId, order);
        }
        if (isStart) {
            order.start = time;
        } else {
            order.end = time;
        }
    }

    int calculatePayout() {
        int[] ongoing = new int[24 * 60]; // ongoing[m] = deliveries running at minute m
        int completedDeliveries = 0;

        for (Order order : orders.values()) {
            if (!order.isComplete()) continue;
            completedDeliveries++;
            // when start > end this loop never runs, so the order adds 0 minutes
            for (int minute = order.start; minute <= order.end; minute++) {
                ongoing[minute]++;
            }
        }

        // payment rule: every minute pays ongoingDeliveries * basePayRate
        int basePay = 0;
        for (int minute = 0; minute < ongoing.length; minute++) {
            basePay += ongoing[minute] * basePayRate;
        }

        int bonus = 0;
        if (deliveryCountsToGetBonus > 0) { // avoids division by zero if metadata was never set
            bonus = (completedDeliveries / deliveryCountsToGetBonus) * bonusPay;
        }
        return basePay + bonus;
    }
}

public class DasherPayoutService {
    Map<String, Dasher> dashers = new HashMap<>(); // dasherId -> dasher

    public DasherPayoutService() {
    }

    public void addOrUpdatePayoutMetadata(String dasherId, int basePayRate, int bonusPay, int deliveryCountsToGetBonus) {
        Dasher dasher = getOrCreateDasher(dasherId);
        dasher.basePayRate = basePayRate;
        dasher.bonusPay = bonusPay;
        dasher.deliveryCountsToGetBonus = deliveryCountsToGetBonus;
    }

    public void addDeliveryActivity(String dasherId, List<String> deliveryActivities) {
        Dasher dasher = getOrCreateDasher(dasherId);
        for (String activity : deliveryActivities) {
            // "orderId=O1,action=START,time=09:00" -> ["orderId=O1", "action=START", "time=09:00"]
            String[] parts = activity.split(",");
            String orderId = readValue(parts[0]);
            boolean isStart = readValue(parts[1]).equals("START");
            int time = toMinutes(readValue(parts[2]));
            dasher.addRecord(orderId, isStart, time);
        }
    }

    public int payout(String dasherId) {
        Dasher dasher = dashers.get(dasherId);
        if (dasher == null) return 0;
        return dasher.calculatePayout();
    }

    Dasher getOrCreateDasher(String dasherId) {
        Dasher dasher = dashers.get(dasherId);
        if (dasher == null) {
            dasher = new Dasher();
            dashers.put(dasherId, dasher);
        }
        return dasher;
    }

    // "time=09:00" -> "09:00"
    String readValue(String keyValue) {
        return keyValue.substring(keyValue.indexOf('=') + 1).trim();
    }

    // "09:00" -> 540 (minutes since midnight)
    int toMinutes(String hhmm) {
        String[] hourAndMinute = hhmm.split(":");
        return Integer.parseInt(hourAndMinute[0]) * 60 + Integer.parseInt(hourAndMinute[1]);
    }
}
```

### What's wrong with it?

- Every payout call walks through every minute of every order. One order can cover up to 1440 minutes, so many long orders make payout slow.
- Every payout call starts again from scratch, even if nothing changed.
- All orders stay in memory forever.

## Key Observation: Every Order Pays for Its Own Minutes

Let's draw Example 2. Each `=` or digit is one minute. The last row shows how many orders are ongoing in that minute.

```
time      09:00     09:10     09:20     09:30
O10       =====================
O11                 =====================
ongoing   1111111111222222222221111111111
```

- **Minute by minute:** the last row adds up to `10*1 + 11*2 + 10*1 = 42`.
- **Order by order:** O10 covers 21 minutes (09:00 to 09:20) and O11 covers 21 minutes (09:10 to 09:30), so `21 + 21 = 42`.


Same number, so pay is `42 * 30 = 1260` either way.


Think of every order as having its own meter. During an overlap two meters run together, and that is exactly why the pay doubles. So we never have to find overlaps. We don't need the minute array, or even a sweep line that sorts all START and END events. We just add up each order's own minutes.

## Solution 2: Optimal (Running Totals)

We improve Solution 1 in two parts.

1. **Better math:** base pay is `totalMinutes * basePayRate`, where `totalMinutes` is the sum of `end - start + 1` over all completed orders. No minute array.
2. **Better data structure:** once an order has both START and END, its minutes never change. So we add them to the running totals right away and delete the order. Payout no longer loops over anything.

### Why these data structures?

- `Map<String, Order> pendingOrders`: START and END can arrive in any order and in different calls. The first half waits here until its partner arrives. Only unfinished orders stay in memory.
- `totalMinutes` and `completedDeliveries`: two plain counters are all that payout needs.


We store minutes and counts, not cents. So if `addOrUpdatePayoutMetadata()` changes the rates later, payout still uses the latest rates.

### Walkthrough of Example 3

Records arrive unordered and in two calls. `basePayRate = 25`, `bonusPay = 150`, `deliveryCountsToGetBonus = 2`.

| Record | What happens | totalMinutes | completedDeliveries |
|---|---|---|---|
| O20 END 10:05 | O20 waits in `pendingOrders` | 0 | 0 |
| O21 START 10:08 | O21 waits in `pendingOrders` | 0 | 0 |
| O20 START 10:00 | O20 complete: 10:00 to 10:05 = 6 minutes | 6 | 1 |
| O21 END 10:10 | O21 complete: 10:08 to 10:10 = 3 minutes | 9 | 2 |

Payout = `9 * 25 + (2 / 2) * 150 = 225 + 150 = 375` cents.

### Edge cases

- `start > end`: the order adds 0 minutes. It still has both records, which is how the problem defines a delivered order, so it counts toward the bonus.
- Only START or only END received: the order waits in `pendingOrders` and adds nothing.
- `00:00` is minute `0`, which is why `-1` marks a missing time.
- No records at all: payout is `0`.

```java
import java.util.*;

/**
 * An order that is still waiting for its START or END record.
 * Times are minutes of the day (09:00 -> 540).
 * -1 means "not received yet", because 0 is a valid time (00:00).
 */
class Order {
    int start = -1;
    int end = -1;

    boolean isComplete() {
        return start != -1 && end != -1;
    }

    // [start, end] includes both minutes. If start > end the order pays nothing.
    int minutes() {
        if (start > end) return 0;
        return end - start + 1;
    }
}

/**
 * Pay settings and running totals of one dasher.
 */
class Dasher {
    int basePayRate;              // cents per minute for each ongoing delivery
    int bonusPay;                 // cents for every group of completed deliveries
    int deliveryCountsToGetBonus; // deliveries needed for one bonus

    Map<String, Order> pendingOrders = new HashMap<>(); // orderId -> order still missing START or END
    int totalMinutes = 0;         // minutes of all completed orders
    int completedDeliveries = 0;  // orders that have both START and END

    void addRecord(String orderId, boolean isStart, int time) {
        Order order = pendingOrders.get(orderId);
        if (order == null) {
            order = new Order();
            pendingOrders.put(orderId, order);
        }
        if (isStart) {
            order.start = time;
        } else {
            order.end = time;
        }

        // Both halves are here, so its minutes can't change anymore.
        // Add them to the totals and forget the order.
        if (order.isComplete()) {
            totalMinutes += order.minutes();
            completedDeliveries++;
            pendingOrders.remove(orderId);
        }
    }

    int calculatePayout() {
        // overlapping orders each pay for their own minutes,
        // so this already includes the multi-order pay
        int basePay = totalMinutes * basePayRate;

        int bonus = 0;
        if (deliveryCountsToGetBonus > 0) { // avoids division by zero if metadata was never set
            bonus = (completedDeliveries / deliveryCountsToGetBonus) * bonusPay;
        }
        return basePay + bonus;
    }
}

public class DasherPayoutService {
    Map<String, Dasher> dashers = new HashMap<>(); // dasherId -> dasher

    public DasherPayoutService() {
    }

    public void addOrUpdatePayoutMetadata(String dasherId, int basePayRate, int bonusPay, int deliveryCountsToGetBonus) {
        Dasher dasher = getOrCreateDasher(dasherId);
        dasher.basePayRate = basePayRate;
        dasher.bonusPay = bonusPay;
        dasher.deliveryCountsToGetBonus = deliveryCountsToGetBonus;
    }

    public void addDeliveryActivity(String dasherId, List<String> deliveryActivities) {
        Dasher dasher = getOrCreateDasher(dasherId);
        for (String activity : deliveryActivities) {
            // "orderId=O1,action=START,time=09:00" -> ["orderId=O1", "action=START", "time=09:00"]
            String[] parts = activity.split(",");
            String orderId = readValue(parts[0]);
            boolean isStart = readValue(parts[1]).equals("START");
            int time = toMinutes(readValue(parts[2]));
            dasher.addRecord(orderId, isStart, time);
        }
    }

    public int payout(String dasherId) {
        Dasher dasher = dashers.get(dasherId);
        if (dasher == null) return 0;
        return dasher.calculatePayout();
    }

    Dasher getOrCreateDasher(String dasherId) {
        Dasher dasher = dashers.get(dasherId);
        if (dasher == null) {
            dasher = new Dasher();
            dashers.put(dasherId, dasher);
        }
        return dasher;
    }

    // "time=09:00" -> "09:00"
    String readValue(String keyValue) {
        return keyValue.substring(keyValue.indexOf('=') + 1).trim();
    }

    // "09:00" -> 540 (minutes since midnight)
    int toMinutes(String hhmm) {
        String[] hourAndMinute = hhmm.split(":");
        return Integer.parseInt(hourAndMinute[0]) * 60 + Integer.parseInt(hourAndMinute[1]);
    }
}
```

## Complexity

| | addDeliveryActivity | payout | Memory |
|---|---|---|---|
| Solution 1 | O(k) | O(n × 1440) worst case | all orders |
| Solution 2 | O(k) | O(1) | only unfinished orders |

`k` is the number of records in one call and `n` is the number of orders of the dasher.

## Follow-up Questions

Each one needs only a small change to Solution 2.

### 1. Different base pay rates across countries

- Rates are just data. Keep a `Map<String, PayConfig>` from country code to that country's default base rate, bonus and currency, and store a `countryCode` in each `Dasher`.
- At payout, use the dasher's own rate if one was set, else the country default. Keep amounts in the local currency's smallest unit (cents, paise), so currencies never mix.
- Only if a country needs a different formula, not just different numbers, add a Strategy per country.

### 2. Double pay during peak hours

- Keep the peak windows, for example 12:00 to 13:59, as a list of `[start, end]` minute pairs.
- When an order completes, count its minutes inside each window: `min(end, peakEnd) - max(start, peakStart) + 1`, or 0 when that is negative. Add them to a second counter, `peakMinutes`.
- Base pay becomes `(totalMinutes + peakMinutes) * basePayRate`, because every peak minute is paid one extra time. Overlapping orders still work, since each order counts its own peak minutes.

### 3. What if the upstream Delivery API fails?

In an actual interview, the records may come from an upstream Delivery API instead of `addDeliveryActivity()`.

- Wrap the call in a small `DeliveryClient` class with a timeout and a few retries with growing wait times (exponential backoff), so retry logic stays out of the payout code.
- If it still fails, never return a guessed or partial amount. Mark the payout as pending (or return an error) and retry it later from a queue, because paying late is better than paying wrong.
- Retries can send the same records twice. Keep a `Set` of completed order ids and skip repeats, so no order is paid twice.