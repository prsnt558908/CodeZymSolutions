# Design Dasher Payout Service in Python

#### Problem Statement
[https://codezym.com/question/97-design-dasher-payout-service](https://codezym.com/question/97-design-dasher-payout-service)

## Core Idea

A dasher earns `basePayRate` cents for every minute they spend on an order. When orders overlap, each minute pays `ongoingDeliveries * basePayRate`. It sounds like we must find all the overlaps, but we don't. Paying `2 * basePayRate` for a minute where 2 orders overlap is the same as paying each of those 2 orders `basePayRate`. So every order simply pays for its own minutes, `end - start + 1`, and the overlap rule takes care of itself. We start with a simple brute force, improve it step by step, and discuss common follow-up questions at the end.


For each dasher we keep just two running numbers: total delivery minutes and completed deliveries. A small dictionary holds half-finished orders until their other record arrives, because START and END can come in any order and even in different calls. Payout then becomes one line of math.


No design pattern is needed here. Every dasher is paid with the same formula, only the numbers (rate and bonus) change. So a few plain classes and dictionaries give the simplest and fastest design.

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

- `dashers` dictionary (`dasherId -> Dasher`): finds a dasher by id in O(1). Order ids like `O1` can repeat for different dashers, so each dasher keeps its own orders.
- `Dasher`: keeps one dasher's pay settings and its `orders` dictionary (`orderId -> Order`) together, instead of spreading them over many dictionaries.
- `Order`: holds `start` and `end` as minutes of the day, so `09:00` becomes `540`. We use `None` for "not received yet". We can't use `0` for that, because `0` is a valid time (`00:00`).


START and END of an order may arrive in any order, even in different calls. So we just fill in whichever half arrives and calculate nothing until `payout()` is called.

### Calculating payout

1. Create `ongoing = [0] * 1440`, one slot for every minute of the day.
2. For every order that has both START and END, count it as a completed delivery and add 1 to every minute from `start` to `end`. If `start > end`, the range is empty, so the order adds 0 minutes.
3. Base pay is the sum of `ongoing[minute] * base_pay_rate` over all minutes.
4. Bonus is `(completed_deliveries // delivery_counts_to_get_bonus) * bonus_pay`. Integer division gives the number of full bonus groups.

```python
class Order:
    """One order of a dasher.
    Times are minutes of the day (09:00 -> 540).
    None means "not received yet". We can't use 0 for that, because 0 is a valid time (00:00).
    """

    def __init__(self):
        self.start = None
        self.end = None

    def is_complete(self):
        return self.start is not None and self.end is not None


class Dasher:
    """Pay settings and all orders of one dasher."""

    def __init__(self):
        self.base_pay_rate = 0                 # cents per minute for each ongoing delivery
        self.bonus_pay = 0                     # cents for every group of completed deliveries
        self.delivery_counts_to_get_bonus = 0  # deliveries needed for one bonus
        self.orders = {}                       # orderId -> Order

    def add_record(self, order_id, is_start, minute):
        if order_id not in self.orders:
            self.orders[order_id] = Order()
        order = self.orders[order_id]
        if is_start:
            order.start = minute
        else:
            order.end = minute

    def calculate_payout(self):
        ongoing = [0] * (24 * 60)  # ongoing[m] = deliveries running at minute m
        completed_deliveries = 0

        for order in self.orders.values():
            if not order.is_complete():
                continue
            completed_deliveries += 1
            # when start > end this range is empty, so the order adds 0 minutes
            for m in range(order.start, order.end + 1):
                ongoing[m] += 1

        # payment rule: every minute pays ongoingDeliveries * basePayRate
        base_pay = 0
        for count in ongoing:
            base_pay += count * self.base_pay_rate

        bonus = 0
        if self.delivery_counts_to_get_bonus > 0:  # avoids division by zero if metadata was never set
            bonus = (completed_deliveries // self.delivery_counts_to_get_bonus) * self.bonus_pay
        return base_pay + bonus


class DasherPayoutService:
    def __init__(self):
        self.dashers = {}  # dasherId -> Dasher

    def addOrUpdatePayoutMetadata(self, dasherId, basePayRate, bonusPay, deliveryCountsToGetBonus):
        dasher = self.get_or_create_dasher(dasherId)
        dasher.base_pay_rate = basePayRate
        dasher.bonus_pay = bonusPay
        dasher.delivery_counts_to_get_bonus = deliveryCountsToGetBonus

    def addDeliveryActivity(self, dasherId, deliveryActivities):
        dasher = self.get_or_create_dasher(dasherId)
        for activity in deliveryActivities:
            # "orderId=O1,action=START,time=09:00" -> ["orderId=O1", "action=START", "time=09:00"]
            parts = activity.split(",")
            order_id = self.read_value(parts[0])
            is_start = self.read_value(parts[1]) == "START"
            minute = self.to_minutes(self.read_value(parts[2]))
            dasher.add_record(order_id, is_start, minute)

    def payout(self, dasherId):
        dasher = self.dashers.get(dasherId)
        if dasher is None:
            return 0
        return dasher.calculate_payout()

    def get_or_create_dasher(self, dasher_id):
        if dasher_id not in self.dashers:
            self.dashers[dasher_id] = Dasher()
        return self.dashers[dasher_id]

    def read_value(self, key_value):
        """'time=09:00' -> '09:00'"""
        return key_value.split("=", 1)[1].strip()

    def to_minutes(self, hhmm):
        """'09:00' -> 540 (minutes since midnight)"""
        hours, minutes = hhmm.split(":")
        return int(hours) * 60 + int(minutes)
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


Think of every order as having its own meter. During an overlap two meters run together, and that is exactly why the pay doubles. So we never have to find overlaps. We don't need the minute list, or even a sweep line that sorts all START and END events. We just add up each order's own minutes.

## Solution 2: Optimal (Running Totals)

We improve Solution 1 in two parts.

1. **Better math:** base pay is `total_minutes * base_pay_rate`, where `total_minutes` is the sum of `end - start + 1` over all completed orders. No minute list.
2. **Better data structure:** once an order has both START and END, its minutes never change. So we add them to the running totals right away and delete the order. Payout no longer loops over anything.

### Why these data structures?

- `pending_orders` dictionary (`orderId -> Order`): START and END can arrive in any order and in different calls. The first half waits here until its partner arrives. Only unfinished orders stay in memory.
- `total_minutes` and `completed_deliveries`: two plain counters are all that payout needs.


We store minutes and counts, not cents. So if `addOrUpdatePayoutMetadata()` changes the rates later, payout still uses the latest rates.

### Walkthrough of Example 3

Records arrive unordered and in two calls. `basePayRate = 25`, `bonusPay = 150`, `deliveryCountsToGetBonus = 2`.

| Record | What happens | total_minutes | completed_deliveries |
|---|---|---|---|
| O20 END 10:05 | O20 waits in `pending_orders` | 0 | 0 |
| O21 START 10:08 | O21 waits in `pending_orders` | 0 | 0 |
| O20 START 10:00 | O20 complete: 10:00 to 10:05 = 6 minutes | 6 | 1 |
| O21 END 10:10 | O21 complete: 10:08 to 10:10 = 3 minutes | 9 | 2 |

Payout = `9 * 25 + (2 // 2) * 150 = 225 + 150 = 375` cents.

### Edge cases

- `start > end`: the order adds 0 minutes. It still has both records, which is how the problem defines a delivered order, so it counts toward the bonus.
- Only START or only END received: the order waits in `pending_orders` and adds nothing.
- `00:00` is minute `0`, which is why `None`, not `0`, marks a missing time.
- No records at all: payout is `0`.

```python
class Order:
    """An order that is still waiting for its START or END record.
    Times are minutes of the day (09:00 -> 540).
    None means "not received yet". We can't use 0 for that, because 0 is a valid time (00:00).
    """

    def __init__(self):
        self.start = None
        self.end = None

    def is_complete(self):
        return self.start is not None and self.end is not None

    def minutes(self):
        """[start, end] includes both minutes. If start > end the order pays nothing."""
        if self.start > self.end:
            return 0
        return self.end - self.start + 1


class Dasher:
    """Pay settings and running totals of one dasher."""

    def __init__(self):
        self.base_pay_rate = 0                 # cents per minute for each ongoing delivery
        self.bonus_pay = 0                     # cents for every group of completed deliveries
        self.delivery_counts_to_get_bonus = 0  # deliveries needed for one bonus

        self.pending_orders = {}               # orderId -> Order still missing START or END
        self.total_minutes = 0                 # minutes of all completed orders
        self.completed_deliveries = 0          # orders that have both START and END

    def add_record(self, order_id, is_start, minute):
        if order_id not in self.pending_orders:
            self.pending_orders[order_id] = Order()
        order = self.pending_orders[order_id]
        if is_start:
            order.start = minute
        else:
            order.end = minute

        # Both halves are here, so its minutes can't change anymore.
        # Add them to the totals and forget the order.
        if order.is_complete():
            self.total_minutes += order.minutes()
            self.completed_deliveries += 1
            del self.pending_orders[order_id]

    def calculate_payout(self):
        # overlapping orders each pay for their own minutes,
        # so this already includes the multi-order pay
        base_pay = self.total_minutes * self.base_pay_rate

        bonus = 0
        if self.delivery_counts_to_get_bonus > 0:  # avoids division by zero if metadata was never set
            bonus = (self.completed_deliveries // self.delivery_counts_to_get_bonus) * self.bonus_pay
        return base_pay + bonus


class DasherPayoutService:
    def __init__(self):
        self.dashers = {}  # dasherId -> Dasher

    def addOrUpdatePayoutMetadata(self, dasherId, basePayRate, bonusPay, deliveryCountsToGetBonus):
        dasher = self.get_or_create_dasher(dasherId)
        dasher.base_pay_rate = basePayRate
        dasher.bonus_pay = bonusPay
        dasher.delivery_counts_to_get_bonus = deliveryCountsToGetBonus

    def addDeliveryActivity(self, dasherId, deliveryActivities):
        dasher = self.get_or_create_dasher(dasherId)
        for activity in deliveryActivities:
            # "orderId=O1,action=START,time=09:00" -> ["orderId=O1", "action=START", "time=09:00"]
            parts = activity.split(",")
            order_id = self.read_value(parts[0])
            is_start = self.read_value(parts[1]) == "START"
            minute = self.to_minutes(self.read_value(parts[2]))
            dasher.add_record(order_id, is_start, minute)

    def payout(self, dasherId):
        dasher = self.dashers.get(dasherId)
        if dasher is None:
            return 0
        return dasher.calculate_payout()

    def get_or_create_dasher(self, dasher_id):
        if dasher_id not in self.dashers:
            self.dashers[dasher_id] = Dasher()
        return self.dashers[dasher_id]

    def read_value(self, key_value):
        """'time=09:00' -> '09:00'"""
        return key_value.split("=", 1)[1].strip()

    def to_minutes(self, hhmm):
        """'09:00' -> 540 (minutes since midnight)"""
        hours, minutes = hhmm.split(":")
        return int(hours) * 60 + int(minutes)
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

- Rates are just data. Keep a dictionary from country code to a `PayConfig` with that country's default base rate, bonus and currency, and store a `country_code` in each `Dasher`.
- At payout, use the dasher's own rate if one was set, else the country default. Keep amounts in the local currency's smallest unit (cents, paise), so currencies never mix.
- Only if a country needs a different formula, not just different numbers, add a Strategy per country.

### 2. Double pay during peak hours

- Keep the peak windows, for example 12:00 to 13:59, as a list of `(start, end)` minute pairs.
- When an order completes, count its minutes inside each window: `min(end, peak_end) - max(start, peak_start) + 1`, or 0 when that is negative. Add them to a second counter, `peak_minutes`.
- Base pay becomes `(total_minutes + peak_minutes) * base_pay_rate`, because every peak minute is paid one extra time. Overlapping orders still work, since each order counts its own peak minutes.

### 3. What if the upstream Delivery API fails?

In an actual interview, the records may come from an upstream Delivery API instead of `addDeliveryActivity()`.

- Wrap the call in a small `DeliveryClient` class with a timeout and a few retries with growing wait times (exponential backoff), so retry logic stays out of the payout code.
- If it still fails, never return a guessed or partial amount. Mark the payout as pending (or return an error) and retry it later from a queue, because paying late is better than paying wrong.
- Retries can send the same records twice. Keep a `set` of completed order ids and skip repeats, so no order is paid twice.