# Dasher Payout Logic - Simple in Java

#### Problem Statement
[https://codezym.com/question/103-dasher-payout-logic](https://codezym.com/question/103-dasher-payout-logic)

A dasher is paid for the time their fulfilled deliveries were in progress. When deliveries overlap, the rate goes up: with 2 ongoing fulfilled deliveries, the dasher earns `2 × 0.3` dollars per minute.

The core idea is simple. This "multi-order rate" is the same as paying **each** ongoing fulfilled delivery `0.3` dollars per minute on its own. So a dasher's payout for the day is just `0.3 × (total minutes of all their fulfilled deliveries)`.

We will first solve it by walking through the timeline exactly as the problem describes. Then we will use the idea above to remove sorting completely and answer every `getPayout()` call in `O(1)`.

No design pattern is needed here. There is a single pay rule and a single calculation, so one small class with a couple of hash maps is the cleanest design.

## Quick Recap of the Rules

- Each record looks like `dasherId,deliveryId,timestamp,status`.
- A delivery is ongoing from its `ACCEPTED` record until its `FULFILLED` or `CANCELLED` record.
- Between two consecutive records of a dasher, pay = `minutes × 0.3 × ongoing deliveries that end as FULFILLED`.
- A cancelled delivery never adds pay, not even while it is ongoing.
- Seconds are ignored: `10:04:16` to `10:06:12` counts as `6 - 4 = 2` minutes.
- The input list may not be in time order, so records must be processed by timestamp.

## Do We Need a Design Pattern?

No. Plain code is the better fit here.

**Strategy** is the most tempting option, because the problem calls this the *first version* of the payment model. We could hide the pay rule behind a `PayRule` interface.

But today there is only one rule: 30 cents per minute. An interface and an extra class around a single multiplication only add code to read. If new pay models arrive later (like a peak hour bonus), moving the rule into a Strategy at that point is a small change.

**State** also looks like a fit, since a delivery moves from `ACCEPTED` to `FULFILLED` or `CANCELLED`. But State helps when an object behaves differently in each state across many actions. Here we only check the final status of a delivery once, so a simple `if` is enough.

## Two Details Used by Both Solutions

### Turning a timestamp into minutes

We turn every timestamp into one number: minutes passed since `1970-01-01`.

`minutesSince1970 = daysSince1970 × 24 × 60 + HH × 60 + mm`

The seconds are never read, so they are ignored for free. Since the date is part of the number, the math stays right even if a delivery crosses midnight.

### Keeping money exact

`0.3` cannot be stored exactly in a `double`. Even `48 * 0.3` gives `14.399999999999999` instead of `14.4`.

So we count whole minutes in a `long` and turn them into money only once, at the very end, using cents. `0.3` dollars per minute is `30` cents per minute:

`payout = paidMinutes * 30 / 100.0`, for example `48 * 30 / 100.0 = 14.4`

## Solution 1: Walk Through the Timeline

Let us first do exactly what the problem says: go through a dasher's records in time order, and for every gap between two records pay `gap minutes × ongoing fulfilled deliveries`.

### Steps

1. **Group records by dasher (in the constructor).** A `Map<Integer, List<String[]>>` maps each dasherId to its records. `getPayout()` needs only one dasher, so this avoids scanning every record on each call.
2. **Sort by time.** The format `yyyy-MM-dd HH:mm:ss` sorts correctly as plain text, so we simply sort by the timestamp string.
3. **Look ahead for fulfilled deliveries.** When a delivery starts, we must already know if it will be fulfilled, because a cancelled delivery must not raise the multiplier. So we first put the ids of all `FULFILLED` deliveries into a `Set`.
4. **Walk and count.** Keep a counter `ongoing`. At each record, first pay for the gap since the previous record: `(minute - previousMinute) × ongoing`. Then update the counter: `+1` when a fulfilled delivery is accepted and `-1` when it is fulfilled.

We add up "paid minutes" (gap minutes × ongoing deliveries) and convert them to dollars once at the end.

### Dry Run (Example 2)

Delivery 2 is cancelled, so it is never counted in `ongoing`.

| Record | Paid minutes for the gap before it | `ongoing` after it |
|---|---|---|
| 18:15 delivery 1 ACCEPTED | 0 (nothing ongoing yet) | 1 |
| 18:18 delivery 2 ACCEPTED | 3 × 1 = 3 | 1 |
| 18:36 delivery 1 FULFILLED | 18 × 1 = 18 | 0 |
| 18:45 delivery 2 CANCELLED | 9 × 0 = 0 | 0 |

Total = `21` paid minutes, so payout = `21 * 30 / 100.0 = 6.3` dollars.

### Code

```java
import java.time.LocalDate;
import java.util.*;

public class DasherPayoutService {

    // dasherId -> all activity records of that dasher.
    // Each record is stored as its 4 parts: [dasherId, deliveryId, timestamp, status]
    Map<Integer, List<String[]>> activitiesByDasher = new HashMap<>();

    public DasherPayoutService(List<String> deliveryActivities) {
        if (deliveryActivities == null) return;
        for (String activity : deliveryActivities) {
            String[] parts = activity.split(",");
            for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();

            int dasherId = Integer.parseInt(parts[0]);
            activitiesByDasher.computeIfAbsent(dasherId, id -> new ArrayList<>()).add(parts);
        }
    }

    public double getPayout(int dasherId) {
        List<String[]> activities = activitiesByDasher.getOrDefault(dasherId, new ArrayList<>());

        // Step 1: put the records in time order.
        // "yyyy-MM-dd HH:mm:ss" text sorts in the same order as the time itself.
        activities.sort((a, b) -> a[2].compareTo(b[2]));

        // Step 2: look ahead and find deliveries that end as FULFILLED. Only these are paid.
        Set<String> fulfilledDeliveries = new HashSet<>();
        for (String[] activity : activities) {
            if (activity[3].equals("FULFILLED")) fulfilledDeliveries.add(activity[1]);
        }

        // Step 3: walk the timeline. Between two consecutive records,
        // every ongoing fulfilled delivery earns pay for those minutes.
        long paidMinutes = 0;      // sum of (minutes in interval * ongoing fulfilled deliveries)
        int ongoing = 0;           // fulfilled deliveries that are in progress right now
        long previousMinute = 0;
        for (String[] activity : activities) {
            long minute = toMinutes(activity[2]);
            paidMinutes += (minute - previousMinute) * ongoing;
            previousMinute = minute;

            if (fulfilledDeliveries.contains(activity[1])) {
                if (activity[3].equals("ACCEPTED")) ongoing++;   // delivery starts
                else ongoing--;                                  // delivery is fulfilled, it ends
            }
        }

        // 0.3 dollars per minute is 30 cents per minute.
        // Counting whole cents and dividing once keeps the amount exact.
        return paidMinutes * 30 / 100.0;
    }

    // "2023-03-31 18:15:42" -> minutes counted from 1970-01-01.
    // Seconds are never read, so they are ignored as the problem asks.
    long toMinutes(String timestamp) {
        String[] dateAndTime = timestamp.split(" ");
        String[] time = dateAndTime[1].split(":");
        long days = LocalDate.parse(dateAndTime[0]).toEpochDay();
        return days * 24 * 60 + Integer.parseInt(time[0]) * 60 + Integer.parseInt(time[1]);
    }
}
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

- **Pass 1:** store the accepted minute of every delivery in `acceptedAt`, a `Map<String, Long>`. The key is `"dasherId,deliveryId"`. The deliveryId alone is already unique as per the constraints. Adding the dasherId just makes the key extra safe.
- **Pass 2:** for every `FULFILLED` record, add `fulfilledMinute - acceptedMinute` to `fulfilledMinutes`, a `Map<Integer, Long>` from dasherId to total minutes. `CANCELLED` records are skipped, so they add nothing.
- **getPayout():** one map lookup, then convert minutes to dollars.

Why two passes? The list is not promised to be in time order, so a `FULFILLED` record can appear before its `ACCEPTED` record. The first pass makes sure every start time is known before we use it.

`fulfilledMinutes` is the only map we keep after the constructor. `acceptedAt` is a temporary local map that is dropped once the totals are ready.

### Code

```java
import java.time.LocalDate;
import java.util.*;

public class DasherPayoutService {

    // dasherId -> total minutes of all fulfilled deliveries of that dasher
    Map<Integer, Long> fulfilledMinutes = new HashMap<>();

    public DasherPayoutService(List<String> deliveryActivities) {
        if (deliveryActivities == null) return;

        // Pass 1: remember the minute at which each delivery was accepted.
        // Key is "dasherId,deliveryId", the first two fields of a record.
        Map<String, Long> acceptedAt = new HashMap<>();
        for (String activity : deliveryActivities) {
            String[] parts = parseActivity(activity);
            if (parts[3].equals("ACCEPTED")) {
                acceptedAt.put(parts[0] + "," + parts[1], toMinutes(parts[2]));
            }
        }

        // Pass 2: a fulfilled delivery is paid for every minute from ACCEPTED to FULFILLED.
        // Cancelled deliveries are skipped, so they add nothing.
        for (String activity : deliveryActivities) {
            String[] parts = parseActivity(activity);
            if (!parts[3].equals("FULFILLED")) continue;

            Long acceptedMinute = acceptedAt.get(parts[0] + "," + parts[1]);
            if (acceptedMinute == null) continue;   // safety check for a missing ACCEPTED record

            int dasherId = Integer.parseInt(parts[0]);
            long minutes = toMinutes(parts[2]) - acceptedMinute;
            fulfilledMinutes.put(dasherId, fulfilledMinutes.getOrDefault(dasherId, 0L) + minutes);
        }
    }

    public double getPayout(int dasherId) {
        long minutes = fulfilledMinutes.getOrDefault(dasherId, 0L);
        // 0.3 dollars per minute is 30 cents per minute.
        // Counting whole cents and dividing once keeps the amount exact.
        return minutes * 30 / 100.0;
    }

    // "1,10,2023-03-31 18:15:00,ACCEPTED" -> ["1", "10", "2023-03-31 18:15:00", "ACCEPTED"]
    String[] parseActivity(String activity) {
        String[] parts = activity.split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    // "2023-03-31 18:15:42" -> minutes counted from 1970-01-01.
    // Seconds are never read, so they are ignored as the problem asks.
    long toMinutes(String timestamp) {
        String[] dateAndTime = timestamp.split(" ");
        String[] time = dateAndTime[1].split(":");
        long days = LocalDate.parse(dateAndTime[0]).toEpochDay();
        return days * 24 * 60 + Integer.parseInt(time[0]) * 60 + Integer.parseInt(time[1]);
    }
}
```

### Complexity

- Constructor: `O(n)`, where `n` is the number of records.
- `getPayout()`: `O(1)`, a single map lookup.
- Space: `O(n)` while building, after that only `O(number of dashers)` is kept.