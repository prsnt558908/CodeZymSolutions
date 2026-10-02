# Dasher Payout Logic - Simple in Python

#### Problem Statement
[https://codezym.com/question/103-dasher-payout-logic](https://codezym.com/question/103-dasher-payout-logic)

A dasher is paid for the time their fulfilled deliveries were in progress. When deliveries overlap, the rate goes up: with 2 ongoing fulfilled deliveries, the dasher earns `2 × 0.3` dollars per minute.

The core idea is simple. This "multi-order rate" is the same as paying **each** ongoing fulfilled delivery `0.3` dollars per minute on its own. So a dasher's payout for the day is just `0.3 × (total minutes of all their fulfilled deliveries)`.

We will first solve it by walking through the timeline exactly as the problem describes. Then we will use the idea above to remove sorting completely and answer every `getPayout()` call in `O(1)`.

No design pattern is needed here. There is a single pay rule and a single calculation, so one small class with a couple of dictionaries is the cleanest design.

## Quick Recap of the Rules

- Each record looks like `dasherId,deliveryId,timestamp,status`.
- A delivery is ongoing from its `ACCEPTED` record until its `FULFILLED` or `CANCELLED` record.
- Between two consecutive records of a dasher, pay = `minutes × 0.3 × ongoing deliveries that end as FULFILLED`.
- A cancelled delivery never adds pay, not even while it is ongoing.
- Seconds are ignored: `10:04:16` to `10:06:12` counts as `6 - 4 = 2` minutes.
- The input list may not be in time order, so records must be processed by timestamp.

## Do We Need a Design Pattern?

No. Plain code is the better fit here.

**Strategy** is the most tempting option, because the problem calls this the *first version* of the payment model. We could hide the pay rule behind a `PayRule` class.

But today there is only one rule: 30 cents per minute. An extra class around a single multiplication only adds code to read. If new pay models arrive later (like a peak hour bonus), moving the rule into a Strategy at that point is a small change.

**State** also looks like a fit, since a delivery moves from `ACCEPTED` to `FULFILLED` or `CANCELLED`. But State helps when an object behaves differently in each state across many actions. Here we only check the final status of a delivery once, so a simple `if` is enough.

## Two Details Used by Both Solutions

### Turning a timestamp into minutes

We turn every timestamp into one number: minutes passed since a fixed starting day. `date.toordinal()` gives the day number counted from 1 January of year 1.

`minutes = dayNumber × 24 × 60 + HH × 60 + mm`

The seconds are never read, so they are ignored for free. Since the date is part of the number, the math stays right even if a delivery crosses midnight.

### Keeping money exact

`0.3` cannot be stored exactly in a `float`. Even `48 * 0.3` gives `14.399999999999999` instead of `14.4`.

So we count whole minutes as an integer and turn them into money only once, at the very end, using cents. `0.3` dollars per minute is `30` cents per minute:

`payout = paid_minutes * 30 / 100`, for example `48 * 30 / 100 = 14.4`

In Python `/` always returns a `float`, so the result is already in the right type.

## Solution 1: Walk Through the Timeline

Let us first do exactly what the problem says: go through a dasher's records in time order, and for every gap between two records pay `gap minutes × ongoing fulfilled deliveries`.

### Steps

1. **Group records by dasher (in the constructor).** A dictionary `activities_by_dasher` maps each dasherId to a list of its records. `getPayout()` needs only one dasher, so this avoids scanning every record on each call.
2. **Sort by time.** The format `yyyy-MM-dd HH:mm:ss` sorts correctly as plain text, so we simply sort by the timestamp string.
3. **Look ahead for fulfilled deliveries.** When a delivery starts, we must already know if it will be fulfilled, because a cancelled delivery must not raise the multiplier. So we first put the ids of all `FULFILLED` deliveries into a set.
4. **Walk and count.** Keep a counter `ongoing`. At each record, first pay for the gap since the previous record: `(minute - previous_minute) × ongoing`. Then update the counter: `+1` when a fulfilled delivery is accepted and `-1` when it is fulfilled.

We add up "paid minutes" (gap minutes × ongoing deliveries) and convert them to dollars once at the end.

### Dry Run (Example 2)

Delivery 2 is cancelled, so it is never counted in `ongoing`.

| Record | Paid minutes for the gap before it | `ongoing` after it |
|---|---|---|
| 18:15 delivery 1 ACCEPTED | 0 (nothing ongoing yet) | 1 |
| 18:18 delivery 2 ACCEPTED | 3 × 1 = 3 | 1 |
| 18:36 delivery 1 FULFILLED | 18 × 1 = 18 | 0 |
| 18:45 delivery 2 CANCELLED | 9 × 0 = 0 | 0 |

Total = `21` paid minutes, so payout = `21 * 30 / 100 = 6.3` dollars.

### Code

```python
from datetime import date


class DasherPayoutService:
    def __init__(self, deliveryActivities):
        # dasherId -> all activity records of that dasher.
        # Each record is stored as its 4 parts: [dasherId, deliveryId, timestamp, status]
        self.activities_by_dasher = {}
        for activity in deliveryActivities or []:
            parts = [part.strip() for part in activity.split(",")]
            dasher_id = int(parts[0])
            self.activities_by_dasher.setdefault(dasher_id, []).append(parts)

    def getPayout(self, dasherId):
        activities = self.activities_by_dasher.get(dasherId, [])

        # Step 1: put the records in time order.
        # "yyyy-MM-dd HH:mm:ss" text sorts in the same order as the time itself.
        activities.sort(key=lambda parts: parts[2])

        # Step 2: look ahead and find deliveries that end as FULFILLED. Only these are paid.
        fulfilled_deliveries = {parts[1] for parts in activities if parts[3] == "FULFILLED"}

        # Step 3: walk the timeline. Between two consecutive records,
        # every ongoing fulfilled delivery earns pay for those minutes.
        paid_minutes = 0      # sum of (minutes in interval * ongoing fulfilled deliveries)
        ongoing = 0           # fulfilled deliveries that are in progress right now
        previous_minute = 0
        for parts in activities:
            minute = self.to_minutes(parts[2])
            paid_minutes += (minute - previous_minute) * ongoing
            previous_minute = minute

            if parts[1] in fulfilled_deliveries:
                if parts[3] == "ACCEPTED":
                    ongoing += 1      # delivery starts
                else:
                    ongoing -= 1      # delivery is fulfilled, it ends

        # 0.3 dollars per minute is 30 cents per minute.
        # Counting whole cents and dividing once keeps the amount exact.
        return paid_minutes * 30 / 100

    def to_minutes(self, timestamp):
        # "2023-03-31 18:15:42" -> minutes counted from a fixed starting day.
        # Seconds are never read, so they are ignored as the problem asks.
        day_part, time_part = timestamp.split(" ")
        hours, minutes, _ = time_part.split(":")
        days = date.fromisoformat(day_part).toordinal()
        return days * 24 * 60 + int(hours) * 60 + int(minutes)
```

### Complexity

- Constructor: `O(n)`, where `n` is the number of records.
- `getPayout()`: `O(k log k)` for sorting, where `k` is the number of records of that dasher.
- Space: `O(n)`.

### What Can Be Better?

It works, but every `getPayout()` call sorts and walks the records again. It also needs an extra look-ahead pass, because a delivery's final status is only known at its end.

Can we skip the timeline completely? Yes.

## Solution 2: Pay Each Delivery on Its Own (Optimal)

### The Key Observation

For any gap, the pay is `minutes × 0.3 × ongoing fulfilled deliveries`. That is exactly the same as giving `minutes × 0.3` to **each** ongoing fulfilled delivery separately.

Think of a team's working hours. You can count, hour by hour, how many people were working. Or you can add up each person's own hours. Both give the same total.

So over the whole day, every fulfilled delivery simply earns `0.3` dollars for each minute between its `ACCEPTED` and `FULFILLED` records:

```
payout = 0.3 × sum of (fulfilledMinute - acceptedMinute) for every fulfilled delivery
```

### Checking It With Example 1

Counting by time gaps (what the problem describes):

| Gap | Minutes | Ongoing fulfilled | Paid minutes |
|---|---|---|---|
| 18:15 to 18:18 | 3 | 1 | 3 |
| 18:18 to 18:36 | 18 | 2 | 36 |
| 18:36 to 18:45 | 9 | 1 | 9 |
| **Total** | | | **48** |

Counting by delivery:

| Delivery | Accepted | Fulfilled | Paid minutes |
|---|---|---|---|
| 1 | 18:15 | 18:36 | 21 |
| 2 | 18:18 | 18:45 | 27 |
| **Total** | | | **48** |

Both ways give 48 paid minutes, so the payout is 14.4 dollars.

This removes the sorting, the timeline walk and the look-ahead pass. Record order stops mattering, and all the work can be done once in the constructor.

### How We Build It

- **Pass 1:** store the accepted minute of every delivery in the dictionary `accepted_at`. The key is the tuple `(dasherId, deliveryId)`, which is the simplest way to use two ids together as one key. The deliveryId alone is already unique as per the constraints. Adding the dasherId just makes the key extra safe.
- **Pass 2:** for every `FULFILLED` record, add `fulfilledMinute - acceptedMinute` to `fulfilled_minutes`, a dictionary from dasherId to total minutes. `CANCELLED` records are skipped, so they add nothing.
- **getPayout():** one dictionary lookup, then convert minutes to dollars.

Why two passes? The list is not promised to be in time order, so a `FULFILLED` record can appear before its `ACCEPTED` record. The first pass makes sure every start time is known before we use it.

`fulfilled_minutes` is the only dictionary we keep after the constructor. `accepted_at` is a temporary local dictionary that is dropped once the totals are ready.

### Code

```python
from datetime import date


class DasherPayoutService:
    def __init__(self, deliveryActivities):
        # dasherId -> total minutes of all fulfilled deliveries of that dasher
        self.fulfilled_minutes = {}
        activities = deliveryActivities or []

        # Pass 1: remember the minute at which each delivery was accepted.
        # Key is (dasherId, deliveryId), the first two fields of a record.
        accepted_at = {}
        for activity in activities:
            dasher_id, delivery_id, timestamp, status = self.parse_activity(activity)
            if status == "ACCEPTED":
                accepted_at[(dasher_id, delivery_id)] = self.to_minutes(timestamp)

        # Pass 2: a fulfilled delivery is paid for every minute from ACCEPTED to FULFILLED.
        # Cancelled deliveries are skipped, so they add nothing.
        for activity in activities:
            dasher_id, delivery_id, timestamp, status = self.parse_activity(activity)
            if status != "FULFILLED":
                continue

            accepted_minute = accepted_at.get((dasher_id, delivery_id))
            if accepted_minute is None:   # safety check for a missing ACCEPTED record
                continue

            minutes = self.to_minutes(timestamp) - accepted_minute
            self.fulfilled_minutes[dasher_id] = self.fulfilled_minutes.get(dasher_id, 0) + minutes

    def getPayout(self, dasherId):
        minutes = self.fulfilled_minutes.get(dasherId, 0)
        # 0.3 dollars per minute is 30 cents per minute.
        # Counting whole cents and dividing once keeps the amount exact.
        return minutes * 30 / 100

    def parse_activity(self, activity):
        # "1,10,2023-03-31 18:15:00,ACCEPTED" -> (1, 10, "2023-03-31 18:15:00", "ACCEPTED")
        dasher_id, delivery_id, timestamp, status = [part.strip() for part in activity.split(",")]
        return int(dasher_id), int(delivery_id), timestamp, status

    def to_minutes(self, timestamp):
        # "2023-03-31 18:15:42" -> minutes counted from a fixed starting day.
        # Seconds are never read, so they are ignored as the problem asks.
        day_part, time_part = timestamp.split(" ")
        hours, minutes, _ = time_part.split(":")
        days = date.fromisoformat(day_part).toordinal()
        return days * 24 * 60 + int(hours) * 60 + int(minutes)
```

### Complexity

- Constructor: `O(n)`, where `n` is the number of records.
- `getPayout()`: `O(1)`, a single dictionary lookup.
- Space: `O(n)` while building, after that only `O(number of dashers)` is kept.