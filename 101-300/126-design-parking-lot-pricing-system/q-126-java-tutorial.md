# Design Parking Lot Pricing System in Java

#### Problem Statement
[https://codezym.com/question/126-design-parking-lot-pricing-system](https://codezym.com/question/126-design-parking-lot-pricing-system)

---

## Core Idea

There is one parking stay and 3 different rules to price it. The stay does not change. Only the rule does.

This is a perfect fit for the **Strategy pattern**. Each pricing rule becomes its own small class. All rules follow one common interface. The main class keeps them in a map and picks one by its number. No other pattern is needed on top of this.

For speed, we use one simple trick. Minutes never change the price. Only hours and dates do. So we cut the stay at every midnight into small **day parts**. A day part is just a range of hours on one date, like `22 to 23` on a Friday.

Every strategy prices these day parts with simple counting. So we never walk the stay minute by minute.

---

## Quick Look at the Rules

- An **hour bucket** is `HH:00` to `HH:59`. Touching even one minute of it charges the full hour.
- Entry and exit are both **inclusive**. The entry minute and the exit minute are part of the stay.
- The `day` field decides weekday or weekend. `sat` and `sun` are the weekend.
- Only the entry and exit carry a day name. For the dates in between, we just move forward through the week: `fri`, `sat`, `sun`, `mon` and so on.
- For strategy 2, blocks are checked **separately for each date**.

---

## Solution 1: Brute Force (Walk Every Minute)

### Idea

The most direct way is to follow the stay one minute at a time.

We number every minute starting from `00:00` of the entry date. Minute `0` is `00:00` of the entry date. Minute `1440` is `00:00` of the next date.

For any minute we can find:

- `dateIndex = minute / 1440` : `0` for the entry date, `1` for the next date, and so on.
- `hour = (minute % 1440) / 60`
- weekend or not: `(entryDay + dateIndex) % 7 >= 5`

Here `mon` is `0` and `sun` is `6`. Each new date adds `1`. The `% 7` wraps `sun` back to `mon`. The values `5` and `6` mean `sat` and `sun`.

To find the last minute, we need the number of dates between entry and exit. `LocalDate.toEpochDay()` turns a date into a plain day number. Subtracting two day numbers gives the gap. Month lengths and leap years are handled for us.

### Why do we need a Set?

Many minutes fall inside the same hour bucket. But each bucket must be charged only once.

A `Set<Integer>` remembers what is already charged. `Set.add()` returns `true` only the first time a value is added. So we charge only when `add()` returns `true`.

- An hour bucket is saved as `dateIndex * 24 + hour`.
- A block is saved as `dateIndex * 4 + blockNumber`.

For strategy 3, we count the hour buckets in the set at the end and apply the slabs.

### Code

```java
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ParkingLotPricingSystem {

    List<String> dayNames = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");
    int[] weekdayBlockPrice = {60, 70, 110, 140};
    int[] weekendBlockPrice = {70, 80, 120, 150};

    public ParkingLotPricingSystem() {
    }

    public int calculatePrice(String entryDateTime, String exitDateTime, int strategy) {
        String[] entry = entryDateTime.split("-");
        String[] exit = exitDateTime.split("-");

        // every minute is counted from 00:00 of the entry date
        int dateGap = (int) (dateOf(exit).toEpochDay() - dateOf(entry).toEpochDay());
        int firstMinute = minuteOfDay(entry);
        int lastMinute = dateGap * 1440 + minuteOfDay(exit);
        int entryDay = dayNames.indexOf(entry[5]);

        // hour buckets (or blocks) that are already charged
        Set<Integer> charged = new HashSet<>();
        int total = 0;

        for (int minute = firstMinute; minute <= lastMinute; minute++) {
            int dateIndex = minute / 1440;                     // 0 = entry date, 1 = next date ...
            int hour = (minute % 1440) / 60;
            boolean weekend = (entryDay + dateIndex) % 7 >= 5; // 5 = sat, 6 = sun

            if (strategy == 1) {
                // add() returns true only the first time, so a bucket is charged once
                if (charged.add(dateIndex * 24 + hour)) {
                    total += hourlyRate(weekend, hour);
                }
            } else if (strategy == 2) {
                int block = blockOf(hour);
                if (charged.add(dateIndex * 4 + block)) {
                    total += weekend ? weekendBlockPrice[block] : weekdayBlockPrice[block];
                }
            } else {
                charged.add(dateIndex * 24 + hour);
            }
        }

        if (strategy == 3) {
            total = slabPrice(charged.size());
        }
        return total;
    }

    public String findMinimumPrice(String entryDateTime, String exitDateTime) {
        int bestStrategy = 0;
        int bestPrice = Integer.MAX_VALUE;
        for (int strategy = 1; strategy <= 3; strategy++) {
            int price = calculatePrice(entryDateTime, exitDateTime, strategy);
            // strict '<' keeps the smaller strategy number on a tie
            if (price < bestPrice) {
                bestPrice = price;
                bestStrategy = strategy;
            }
        }
        return bestStrategy + "-" + bestPrice;
    }

    LocalDate dateOf(String[] parts) {
        return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
    }

    int minuteOfDay(String[] parts) {
        return Integer.parseInt(parts[3]) * 60 + Integer.parseInt(parts[4]);
    }

    int hourlyRate(boolean weekend, int hour) {
        boolean dayHour = hour >= 9 && hour <= 16;
        if (weekend) {
            return dayHour ? 15 : 12;
        }
        return dayHour ? 20 : 10;
    }

    // block 0: 00-08, block 1: 09-12, block 2: 13-17, block 3: 18-23
    int blockOf(int hour) {
        if (hour <= 8) return 0;
        if (hour <= 12) return 1;
        if (hour <= 17) return 2;
        return 3;
    }

    int slabPrice(int hours) {
        int firstSlab = Math.min(hours, 2);
        int secondSlab = Math.min(Math.max(hours - 2, 0), 3);
        int thirdSlab = Math.max(hours - 5, 0);
        return firstSlab * 30 + secondSlab * 20 + thirdSlab * 10;
    }
}
```

### Problems with this approach

- **Slow.** A stay can be 1,000,000 minutes long. We visit every single minute. `findMinimumPrice` does this 3 times.
- **Extra memory.** The set can grow to thousands of entries.
- **Messy design.** All 3 rules are mixed inside one method with if-else. Changing one rule can break another. Adding a 4th rule means editing this method again.

---

## Solution 2: Strategy Pattern + Day Parts (Optimized)

We fix the speed with two simple observations. Then we fix the design with the Strategy pattern.

### Observation 1: Minutes never matter

All prices work on whole hours. Even the blocks of strategy 2 are made of whole hours.

Entry and exit are inclusive. So the entry hour is always touched, and the exit hour is always touched. The exact minute changes nothing.

So `08:50` behaves exactly like `08:00`. We only keep the hour.

### Observation 2: Each date is one range of hours

A stay has no gaps. So on any date, the touched hours form one unbroken range.

- Entry date: from the entry hour to `23`.
- Every middle date: the full day, `0` to `23`.
- Exit date: from `0` to the exit hour.
- Entry and exit on the same date: from the entry hour to the exit hour.

So we cut the stay at every midnight. Each piece is a `DayPart`. It needs only 3 things: is it a weekend, the first hour and the last hour.

**Example:** from `2026-04-17-22-30-fri` to `2026-04-20-01-15-mon`.

| Date | Day | Weekend | Hours touched |
|---|---|---|---|
| 2026-04-17 | fri | no | 22 to 23 |
| 2026-04-18 | sat | yes | 0 to 23 |
| 2026-04-19 | sun | yes | 0 to 23 |
| 2026-04-20 | mon | no | 0 to 1 |

The longest stay is 1,000,000 minutes. That is less than 700 dates. So we handle at most about 700 day parts instead of 1,000,000 minutes.

### One small helper: hours inside a range

Every strategy asks the same question:

> How many hours of this day part fall inside the range `start` to `end`?

Take the later start and the earlier end. If they cross, the answer is `0`.

```text
low    = max(firstHour, start)
high   = min(lastHour, end)
answer = max(0, high - low + 1)
```

For example, the day part `8 to 14` and the day hours `9 to 16` give `low = 9` and `high = 14`. So `6` hours fall inside.

### How each strategy prices the day parts

**Strategy 1, Hourly:** day hours are the hours inside `9 to 16`. Off hours are all the other hours of the part. Multiply each by its weekday or weekend rate.

**Strategy 2, Block:** check all 4 blocks of the date. If at least one hour of the part falls inside a block, add that block's price once.

**Strategy 3, Slab:** first add up the hours of all parts. Then apply the slabs to that total.

For the example above:

- Strategy 1: `20 + 312 + 312 + 20 = 664`
- Strategy 2: `140 + 420 + 420 + 60 = 1040`
- Strategy 3: `52` hours, so `2*30 + 3*20 + 47*10 = 590`

So `findMinimumPrice` returns `"3-590"`.

### Choosing the design pattern

**Why Strategy?**

All 3 rules take the same input and return a price. Only the math inside is different. That is exactly what the Strategy pattern is for.

- Each rule lives in its own small class. Changing one cannot break another.
- A new rule needs one new class and one line in the constructor. Nothing else changes.
- `findMinimumPrice` simply loops over all strategies. It does not care how each one works.

**Why not plain if-else?** Solution 1 already shows it. It works, but all the rules are packed into one method. It gets harder to read and change with every new rule.

**Why not Template Method?** It sounds close, because every rule goes through the day parts and adds up a price. But slab pricing cannot price one date on its own. It needs the total hours first. One shared skeleton would need awkward hooks for that. Small independent strategies are simpler.

**Why not a Factory class?** The strategies hold no state. We create each one once in the constructor and keep it in a map. A map lookup already returns the right strategy. A separate factory would only add code.

### The main classes and why we need them

- **`ParkingTime`** reads a string like `2026-04-14-08-10-tue` once. It keeps only the date, the hour and the day index.
- **`DayPart`** is the heart of the solution. It turns a long stay into a few small hour ranges. All 3 strategies work on the same list of day parts, so the time logic is written only once.
- **`PricingStrategy`** is the common interface. `HourlyPricingStrategy`, `BlockPricingStrategy` and `SlabPricingStrategy` are its 3 versions.
- **`Map<Integer, PricingStrategy>`** maps a strategy number to its object. A simple lookup replaces the if-else chain.
- **`ParkingLotPricingSystem`** builds the list of day parts and hands it to the chosen strategy. In `findMinimumPrice` the list is built once and shared by all 3 strategies.

### Code

```java
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One parsed timestamp in the format yyyy-MM-dd-HH-mm-day.
 * Minutes are not stored because they never change the price.
 */
class ParkingTime {
    LocalDate date;
    int hour;
    int dayIndex; // 0 = mon, 1 = tue ... 5 = sat, 6 = sun

    ParkingTime(String dateTime) {
        String[] parts = dateTime.split("-");
        date = LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
        hour = Integer.parseInt(parts[3]);
        dayIndex = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun").indexOf(parts[5]);
    }
}

/**
 * The part of a parking stay that falls on one date.
 * On a single date, the touched hours are always one unbroken range.
 */
class DayPart {
    boolean weekend;
    int firstHour; // first touched hour bucket on this date
    int lastHour;  // last touched hour bucket on this date

    DayPart(boolean weekend, int firstHour, int lastHour) {
        this.weekend = weekend;
        this.firstHour = firstHour;
        this.lastHour = lastHour;
    }

    int totalHours() {
        return lastHour - firstHour + 1;
    }

    /** Counts how many touched hours of this part fall inside [start, end]. */
    int hoursInRange(int start, int end) {
        int low = Math.max(firstHour, start);
        int high = Math.min(lastHour, end);
        return Math.max(0, high - low + 1);
    }
}

/** Common contract for every pricing rule. */
interface PricingStrategy {
    int calculatePrice(List<DayPart> parts);
}

/** Strategy 1: every touched hour is charged by its own hourly rate. */
class HourlyPricingStrategy implements PricingStrategy {
    int dayStart = 9; // day hours are 09:00 to 16:59
    int dayEnd = 16;

    public int calculatePrice(List<DayPart> parts) {
        int total = 0;
        for (DayPart part : parts) {
            int dayHours = part.hoursInRange(dayStart, dayEnd);
            int offHours = part.totalHours() - dayHours;
            if (part.weekend) {
                total += dayHours * 15 + offHours * 12;
            } else {
                total += dayHours * 20 + offHours * 10;
            }
        }
        return total;
    }
}

/** Strategy 2: every touched block is charged once for each date. */
class BlockPricingStrategy implements PricingStrategy {
    int[] blockStart = {0, 9, 13, 18};
    int[] blockEnd = {8, 12, 17, 23};
    int[] weekdayPrice = {60, 70, 110, 140};
    int[] weekendPrice = {70, 80, 120, 150};

    public int calculatePrice(List<DayPart> parts) {
        int total = 0;
        for (DayPart part : parts) {
            for (int i = 0; i < blockStart.length; i++) {
                // one touched hour inside the block is enough to charge it
                if (part.hoursInRange(blockStart[i], blockEnd[i]) > 0) {
                    total += part.weekend ? weekendPrice[i] : weekdayPrice[i];
                }
            }
        }
        return total;
    }
}

/** Strategy 3: only the total number of touched hours matters. */
class SlabPricingStrategy implements PricingStrategy {

    public int calculatePrice(List<DayPart> parts) {
        int hours = 0;
        for (DayPart part : parts) {
            hours += part.totalHours();
        }
        int firstSlab = Math.min(hours, 2);                   // first 2 hours at 30
        int secondSlab = Math.min(Math.max(hours - 2, 0), 3); // next 3 hours at 20
        int thirdSlab = Math.max(hours - 5, 0);               // every hour after that at 10
        return firstSlab * 30 + secondSlab * 20 + thirdSlab * 10;
    }
}

public class ParkingLotPricingSystem {

    // strategy number -> pricing rule
    Map<Integer, PricingStrategy> strategies = new HashMap<>();

    public ParkingLotPricingSystem() {
        strategies.put(1, new HourlyPricingStrategy());
        strategies.put(2, new BlockPricingStrategy());
        strategies.put(3, new SlabPricingStrategy());
    }

    public int calculatePrice(String entryDateTime, String exitDateTime, int strategy) {
        List<DayPart> parts = splitByDate(entryDateTime, exitDateTime);
        return strategies.get(strategy).calculatePrice(parts);
    }

    public String findMinimumPrice(String entryDateTime, String exitDateTime) {
        // split once, then reuse the same parts for every strategy
        List<DayPart> parts = splitByDate(entryDateTime, exitDateTime);
        int bestStrategy = 0;
        int bestPrice = Integer.MAX_VALUE;
        for (int id = 1; id <= strategies.size(); id++) {
            int price = strategies.get(id).calculatePrice(parts);
            // strict '<' keeps the smaller strategy number on a tie
            if (price < bestPrice) {
                bestPrice = price;
                bestStrategy = id;
            }
        }
        return bestStrategy + "-" + bestPrice;
    }

    /**
     * Cuts the stay at every midnight.
     * Returns one DayPart for each date, from the entry date to the exit date.
     */
    List<DayPart> splitByDate(String entryDateTime, String exitDateTime) {
        ParkingTime entry = new ParkingTime(entryDateTime);
        ParkingTime exit = new ParkingTime(exitDateTime);
        // how many dates after the entry date the exit date comes
        int dateGap = (int) (exit.date.toEpochDay() - entry.date.toEpochDay());

        List<DayPart> parts = new ArrayList<>();
        for (int i = 0; i <= dateGap; i++) {
            // move forward from the entry day: fri, sat, sun, mon ...
            boolean weekend = (entry.dayIndex + i) % 7 >= 5;
            int firstHour = (i == 0) ? entry.hour : 0;
            int lastHour = (i == dateGap) ? exit.hour : 23;
            parts.add(new DayPart(weekend, firstHour, lastHour));
        }
        return parts;
    }
}
```

### Complexity

Let `D` be the number of dates the stay touches. `D` is at most about 700.

- Building the day parts: `O(D)`.
- Each strategy: `O(D)`. Strategy 2 checks only 4 blocks per date, so it is still `O(D)`.
- Memory: `O(D)` for the list of day parts.
