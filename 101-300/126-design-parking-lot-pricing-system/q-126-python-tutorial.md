# Design Parking Lot Pricing System in Python

#### Problem Statement
[https://codezym.com/question/126-design-parking-lot-pricing-system](https://codezym.com/question/126-design-parking-lot-pricing-system)

---

## Core Idea

There is one parking stay and 3 different rules to price it. The stay does not change. Only the rule does.

This is a perfect fit for the **Strategy pattern**. Each pricing rule becomes its own small class. All rules follow one common interface. The main class keeps them in a dictionary and picks one by its number. No other pattern is needed on top of this.

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

- `date_index = minute // 1440` : `0` for the entry date, `1` for the next date, and so on.
- `hour = (minute % 1440) // 60`
- weekend or not: `(entry_day + date_index) % 7 >= 5`

Here `mon` is `0` and `sun` is `6`. Each new date adds `1`. The `% 7` wraps `sun` back to `mon`. The values `5` and `6` mean `sat` and `sun`.

To find the last minute, we need the number of dates between entry and exit. Subtracting two `date` objects gives a `timedelta`. Its `.days` value is the gap. Month lengths and leap years are handled for us.

### Why do we need a set?

Many minutes fall inside the same hour bucket. But each bucket must be charged only once.

A `set` remembers what is already charged. Before charging, we check `key not in charged`. Only then we add the key and charge it.

- An hour bucket is saved as `date_index * 24 + hour`.
- A block is saved as `date_index * 4 + block_number`.

For strategy 3, we count the hour buckets in the set at the end and apply the slabs.

### Code

```python
from datetime import date
from typing import List


class ParkingLotPricingSystem:
    def __init__(self):
        self.day_names = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
        self.weekday_block_price = [60, 70, 110, 140]
        self.weekend_block_price = [70, 80, 120, 150]

    def calculatePrice(self, entryDateTime: str, exitDateTime: str, strategy: int) -> int:
        entry_parts = entryDateTime.split("-")
        exit_parts = exitDateTime.split("-")

        # every minute is counted from 00:00 of the entry date
        date_gap = (self.date_of(exit_parts) - self.date_of(entry_parts)).days
        first_minute = self.minute_of_day(entry_parts)
        last_minute = date_gap * 1440 + self.minute_of_day(exit_parts)
        entry_day = self.day_names.index(entry_parts[5])

        # hour buckets (or blocks) that are already charged
        charged = set()
        total = 0

        for minute in range(first_minute, last_minute + 1):
            date_index = minute // 1440                  # 0 = entry date, 1 = next date ...
            hour = (minute % 1440) // 60
            weekend = (entry_day + date_index) % 7 >= 5  # 5 = sat, 6 = sun

            if strategy == 1:
                key = date_index * 24 + hour
                if key not in charged:  # charge each hour bucket only once
                    charged.add(key)
                    total += self.hourly_rate(weekend, hour)
            elif strategy == 2:
                block = self.block_of(hour)
                key = date_index * 4 + block
                if key not in charged:  # charge each block only once
                    charged.add(key)
                    if weekend:
                        total += self.weekend_block_price[block]
                    else:
                        total += self.weekday_block_price[block]
            else:
                charged.add(date_index * 24 + hour)

        if strategy == 3:
            total = self.slab_price(len(charged))
        return total

    def findMinimumPrice(self, entryDateTime: str, exitDateTime: str) -> str:
        best_strategy = 0
        best_price = float("inf")
        for strategy in range(1, 4):
            price = self.calculatePrice(entryDateTime, exitDateTime, strategy)
            # strict '<' keeps the smaller strategy number on a tie
            if price < best_price:
                best_price = price
                best_strategy = strategy
        return f"{best_strategy}-{best_price}"

    def date_of(self, parts: List[str]) -> date:
        return date(int(parts[0]), int(parts[1]), int(parts[2]))

    def minute_of_day(self, parts: List[str]) -> int:
        return int(parts[3]) * 60 + int(parts[4])

    def hourly_rate(self, weekend: bool, hour: int) -> int:
        day_hour = 9 <= hour <= 16
        if weekend:
            return 15 if day_hour else 12
        return 20 if day_hour else 10

    def block_of(self, hour: int) -> int:
        # block 0: 00-08, block 1: 09-12, block 2: 13-17, block 3: 18-23
        if hour <= 8:
            return 0
        if hour <= 12:
            return 1
        if hour <= 17:
            return 2
        return 3

    def slab_price(self, hours: int) -> int:
        first_slab = min(hours, 2)
        second_slab = min(max(hours - 2, 0), 3)
        third_slab = max(hours - 5, 0)
        return first_slab * 30 + second_slab * 20 + third_slab * 10
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
low    = max(first_hour, start)
high   = min(last_hour, end)
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
- A new rule needs one new class and one new entry in the dictionary. Nothing else changes.
- `findMinimumPrice` simply loops over all strategies. It does not care how each one works.

**Why not plain if-else?** Solution 1 already shows it. It works, but all the rules are packed into one method. It gets harder to read and change with every new rule.

**Why not Template Method?** It sounds close, because every rule goes through the day parts and adds up a price. But slab pricing cannot price one date on its own. It needs the total hours first. One shared skeleton would need awkward hooks for that. Small independent strategies are simpler.

**Why not a Factory class?** The strategies hold no state. We create each one once in the constructor and keep it in a dictionary. A dictionary lookup already returns the right strategy. A separate factory would only add code.

### The main classes and why we need them

- **`ParkingTime`** reads a string like `2026-04-14-08-10-tue` once. It keeps only the date, the hour and the day index.
- **`DayPart`** is the heart of the solution. It turns a long stay into a few small hour ranges. All 3 strategies work on the same list of day parts, so the time logic is written only once.
- **`PricingStrategy`** is the common interface, written as an abstract base class. `HourlyPricingStrategy`, `BlockPricingStrategy` and `SlabPricingStrategy` are its 3 versions.
- **`Dict[int, PricingStrategy]`** maps a strategy number to its object. A simple lookup replaces the if-else chain.
- **`ParkingLotPricingSystem`** builds the list of day parts and hands it to the chosen strategy. In `findMinimumPrice` the list is built once and shared by all 3 strategies.

### Code

```python
from abc import ABC, abstractmethod
from datetime import date
from typing import Dict, List


class ParkingTime:
    """One parsed timestamp in the format yyyy-MM-dd-HH-mm-day.
    Minutes are not stored because they never change the price.
    """

    def __init__(self, date_time: str):
        parts = date_time.split("-")
        self.date = date(int(parts[0]), int(parts[1]), int(parts[2]))
        self.hour = int(parts[3])
        # 0 = mon, 1 = tue ... 5 = sat, 6 = sun
        self.day_index = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"].index(parts[5])


class DayPart:
    """The part of a parking stay that falls on one date.
    On a single date, the touched hours are always one unbroken range.
    """

    def __init__(self, weekend: bool, first_hour: int, last_hour: int):
        self.weekend = weekend
        self.first_hour = first_hour  # first touched hour bucket on this date
        self.last_hour = last_hour    # last touched hour bucket on this date

    def total_hours(self) -> int:
        return self.last_hour - self.first_hour + 1

    def hours_in_range(self, start: int, end: int) -> int:
        """Counts how many touched hours of this part fall inside [start, end]."""
        low = max(self.first_hour, start)
        high = min(self.last_hour, end)
        return max(0, high - low + 1)


class PricingStrategy(ABC):
    """Common contract for every pricing rule."""

    @abstractmethod
    def calculate_price(self, parts: List[DayPart]) -> int:
        pass


class HourlyPricingStrategy(PricingStrategy):
    """Strategy 1: every touched hour is charged by its own hourly rate."""

    def __init__(self):
        self.day_start = 9  # day hours are 09:00 to 16:59
        self.day_end = 16

    def calculate_price(self, parts: List[DayPart]) -> int:
        total = 0
        for part in parts:
            day_hours = part.hours_in_range(self.day_start, self.day_end)
            off_hours = part.total_hours() - day_hours
            if part.weekend:
                total += day_hours * 15 + off_hours * 12
            else:
                total += day_hours * 20 + off_hours * 10
        return total


class BlockPricingStrategy(PricingStrategy):
    """Strategy 2: every touched block is charged once for each date."""

    def __init__(self):
        self.block_start = [0, 9, 13, 18]
        self.block_end = [8, 12, 17, 23]
        self.weekday_price = [60, 70, 110, 140]
        self.weekend_price = [70, 80, 120, 150]

    def calculate_price(self, parts: List[DayPart]) -> int:
        total = 0
        for part in parts:
            for i in range(len(self.block_start)):
                # one touched hour inside the block is enough to charge it
                if part.hours_in_range(self.block_start[i], self.block_end[i]) > 0:
                    if part.weekend:
                        total += self.weekend_price[i]
                    else:
                        total += self.weekday_price[i]
        return total


class SlabPricingStrategy(PricingStrategy):
    """Strategy 3: only the total number of touched hours matters."""

    def calculate_price(self, parts: List[DayPart]) -> int:
        hours = sum(part.total_hours() for part in parts)
        first_slab = min(hours, 2)               # first 2 hours at 30
        second_slab = min(max(hours - 2, 0), 3)  # next 3 hours at 20
        third_slab = max(hours - 5, 0)           # every hour after that at 10
        return first_slab * 30 + second_slab * 20 + third_slab * 10


class ParkingLotPricingSystem:
    def __init__(self):
        # strategy number -> pricing rule
        self.strategies: Dict[int, PricingStrategy] = {
            1: HourlyPricingStrategy(),
            2: BlockPricingStrategy(),
            3: SlabPricingStrategy(),
        }

    def calculatePrice(self, entryDateTime: str, exitDateTime: str, strategy: int) -> int:
        parts = self.split_by_date(entryDateTime, exitDateTime)
        return self.strategies[strategy].calculate_price(parts)

    def findMinimumPrice(self, entryDateTime: str, exitDateTime: str) -> str:
        # split once, then reuse the same parts for every strategy
        parts = self.split_by_date(entryDateTime, exitDateTime)
        best_strategy = 0
        best_price = float("inf")
        for strategy_id in range(1, len(self.strategies) + 1):
            price = self.strategies[strategy_id].calculate_price(parts)
            # strict '<' keeps the smaller strategy number on a tie
            if price < best_price:
                best_price = price
                best_strategy = strategy_id
        return f"{best_strategy}-{best_price}"

    def split_by_date(self, entryDateTime: str, exitDateTime: str) -> List[DayPart]:
        """Cuts the stay at every midnight.
        Returns one DayPart for each date, from the entry date to the exit date.
        """
        entry_time = ParkingTime(entryDateTime)
        exit_time = ParkingTime(exitDateTime)
        # how many dates after the entry date the exit date comes
        date_gap = (exit_time.date - entry_time.date).days

        parts = []
        for i in range(date_gap + 1):
            # move forward from the entry day: fri, sat, sun, mon ...
            weekend = (entry_time.day_index + i) % 7 >= 5
            first_hour = entry_time.hour if i == 0 else 0
            last_hour = exit_time.hour if i == date_gap else 23
            parts.append(DayPart(weekend, first_hour, last_hour))
        return parts
```

### Complexity

Let `D` be the number of dates the stay touches. `D` is at most about 700.

- Building the day parts: `O(D)`.
- Each strategy: `O(D)`. Strategy 2 checks only 4 blocks per date, so it is still `O(D)`.
- Memory: `O(D)` for the list of day parts.
