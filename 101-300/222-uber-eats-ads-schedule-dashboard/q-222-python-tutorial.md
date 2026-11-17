# Design Uber Eats Ads Schedule Dashboard in Python

#### Problem Statement
[https://codezym.com/question/222-uber-eats-ads-schedule-dashboard](https://codezym.com/question/222-uber-eats-ads-schedule-dashboard)


The whole week is a small, fixed grid: 7 days and 6 time slots per day, so only 42 cells. Each cell is either selected or not.

So we store the full week as a 7 x 6 table of `True` / `False` values. Clicking a slot flips one cell. Reading a day means walking one row, and reading the week means walking all rows from Monday to Sunday.

Because we always walk the grid in its natural order, the output comes out already sorted. We never need to sort anything.

No design pattern is needed here. The problem is only about storing and reading a tiny fixed grid, so one simple class with a 2D list is the cleanest and most optimal design.

---

## Understanding the Problem

Picture the dashboard as a table. Rows are days and columns are slots.

Here is the starting state of Example 1, `["MONDAY,0", "MONDAY,3", "FRIDAY,5"]`:

```text
             slot 0   slot 1   slot 2   slot 3   slot 4   slot 5
             00-04    04-08    08-12    12-16    16-20    20-24
MONDAY        [x]      [ ]      [ ]      [x]      [ ]      [ ]
TUESDAY       [ ]      [ ]      [ ]      [ ]      [ ]      [ ]
WEDNESDAY     [ ]      [ ]      [ ]      [ ]      [ ]      [ ]
THURSDAY      [ ]      [ ]      [ ]      [ ]      [ ]      [ ]
FRIDAY        [ ]      [ ]      [ ]      [ ]      [ ]      [x]
SATURDAY      [ ]      [ ]      [ ]      [ ]      [ ]      [ ]
SUNDAY        [ ]      [ ]      [ ]      [ ]      [ ]      [ ]
```

We need three operations:

1. `clickSlot`: flip one cell, then return that day's selected slots.
2. `getDaySchedule`: return one day's selected slots.
3. `getWeeklySchedule`: return all selected slots, Monday first.

Each slot is 4 hours long, so its time range is simple math:

- `startHour = slotIndex * 4`
- `endHour = startHour + 4`

For example, slot `3` runs from `12:00` to `16:00`.

---

## Approach 1: Keep a List of Selected Slots (Brute Force)

The most direct idea is to store selected slots just like the input, as strings such as `"MONDAY,3"`.

To click a slot: if its string is in the list, remove it. Otherwise, add it.

```python
# In __init__: self.selected = []

def clickSlot(self, day: str, slotIndex: int) -> List[str]:
    key = f"{day},{slotIndex}"
    if key in self.selected:
        self.selected.remove(key)    # was selected, so unselect it
    else:
        self.selected.append(key)    # was not selected, so select it
    return self.getDaySchedule(day)
```

The constructor must check `if slot not in self.selected` before adding. Otherwise a duplicate like `"WEDNESDAY,1"` is stored twice, and one click removes only one copy. The slot would still look selected.

Reading is where it gets messy. The list is in click order, so every read must filter and sort it.

We also cannot sort day names alphabetically, because `FRIDAY` would come before `MONDAY`. We need a custom sort key: a small function that turns `"FRIDAY,2"` into `(4, 2)`, meaning day position first, slot number second:

```python
# self.days = ["MONDAY", "TUESDAY", ..., "SUNDAY"]
ordered = sorted(
    self.selected,
    key=lambda s: (self.days.index(s.split(",")[0]), int(s.split(",")[1])),
)
```

### Problems with this approach

- **Sorting on every read**: the order of days and slots never changes, yet we sort again and again.
- **String parsing everywhere**: we keep splitting `"MONDAY,3"` back into a day and a number.
- **Extra care for duplicates**: we must check `in` before every add.
- **Slow lookups**: `in` and `remove` scan the whole list on every click.

With at most 42 items, speed is not the real problem. The real problem is that the code fights the data.

The data has a fixed shape and a fixed order. So let us store it exactly that way.

---

## Approach 2: Store the Week as a 7 x 6 Grid (Optimal)

### Key idea

We store the exact table from the picture above:

```python
self.grid = [[False] * 6 for _ in range(7)]
```

- **Row** = day index. `MONDAY` is `0`, `TUESDAY` is `1`, and so on up to `SUNDAY` at `6`.
- **Column** = slot index, from `0` to `5`.

`self.grid[dayIndex][slotIndex]` is `True` when the ad runs in that slot.

> **Python tip**: build the grid with a list comprehension like above. Writing `[[False] * 6] * 7` looks the same, but it creates 7 references to **one** shared row. Clicking a Monday slot would then change every day.

### The data structures

**`self.days`** is a list of day names in week order. It does two jobs:

- `self.days.index("FRIDAY")` gives the row number `4`.
- `self.days[4]` gives back `"FRIDAY"` when we build the output.

It has only 7 items, so `index` is instant.

**`self.grid`** holds the full weekly schedule in one place, just as the problem asks. Switching between days needs no extra work.

A cell's position already tells us its day and slot. So after the constructor, we never store or parse strings again.

### How each method works

**Constructor**: split each `"day,slotIndex"` string and set that cell to `True`.

> We **set** the cell to `True`. We do **not toggle** it. If `"WEDNESDAY,1"` appears twice, toggling would select it and then unselect it. Setting it to `True` twice does no harm, so duplicates are handled for free.

**`clickSlot`**: flip the cell with `self.grid[dayIndex][slotIndex] = not self.grid[dayIndex][slotIndex]`, then return `getDaySchedule(day)`.

**`getDaySchedule`**: walk the day's row from slot `0` to slot `5` and collect the selected cells.

**`getWeeklySchedule`**: do the same for every row, from Monday (row `0`) to Sunday (row `6`).

Walking left to right gives slots in order. Walking top to bottom gives days in order. So both results come out sorted with no sorting code at all.

### Formatting a slot

`f"{hour:02d}:00"` turns `4` into `"04:00"` and `24` into `"24:00"`. The `:02d` part pads a single digit hour with a leading zero.

So slot `1` on Monday becomes `"MONDAY,1,04:00,08:00"`.

---

## Why No Design Pattern?

Two patterns may look like a fit at first, but both add code without adding value here.

**State pattern**: a slot switches between "selected" and "unselected", which sounds like states. But the State pattern is useful when an object *behaves differently* in each state. Our slot only remembers yes or no, and a `bool` already does that.

**Command pattern**: each click could become a command object, which makes undo and redo easy. But this problem has no undo or history, so those extra classes would never be used.

One class with a grid stays short, fast and easy to read.

---

## Complexity

| Operation | Work done |
|---|---|
| Constructor | one cell update per initial slot |
| `clickSlot` | one flip, then read 6 cells |
| `getDaySchedule` | read 6 cells |
| `getWeeklySchedule` | read 42 cells |

The grid is always 42 cells and never grows. So every click and every read runs in constant time, O(1). The constructor does one O(1) update per initial slot.

---

## Python Code

```python
from typing import List


class AdsScheduleDashboard:
    """
    Uber Eats ads schedule dashboard.

    The whole week is stored as a 7 x 6 grid:
    row = day (MONDAY = 0 ... SUNDAY = 6), column = slot index (0 to 5).
    grid[day][slot] is True when the ad runs in that slot.
    """

    def __init__(self, selectedSlots: List[str]):
        # Days in week order. A day's position in this list is its row in the grid.
        self.days = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
                     "FRIDAY", "SATURDAY", "SUNDAY"]
        self.slotsPerDay = 6
        self.hoursPerSlot = 4

        # Full weekly schedule. All slots start as not selected.
        # The list comprehension gives every day its own separate row.
        self.grid = [[False] * self.slotsPerDay for _ in range(len(self.days))]

        for slot in selectedSlots:
            parts = slot.split(",")
            dayIndex = self.days.index(parts[0].strip())
            slotIndex = int(parts[1])

            # Set to True (do not toggle), so duplicate slots are harmless
            self.grid[dayIndex][slotIndex] = True

    def clickSlot(self, day: str, slotIndex: int) -> List[str]:
        dayIndex = self.days.index(day)
        self.grid[dayIndex][slotIndex] = not self.grid[dayIndex][slotIndex]
        return self.getDaySchedule(day)

    def getDaySchedule(self, day: str) -> List[str]:
        result = []
        self.addSelectedSlots(self.days.index(day), result)
        return result

    def getWeeklySchedule(self) -> List[str]:
        result = []
        # Rows go from MONDAY to SUNDAY, so days come out in order
        for dayIndex in range(len(self.days)):
            self.addSelectedSlots(dayIndex, result)
        return result

    def addSelectedSlots(self, dayIndex: int, result: List[str]) -> None:
        """
        Adds the selected slots of one day to result.
        Slots are read from 0 to 5, so they come out sorted.
        """
        for slotIndex in range(self.slotsPerDay):
            if self.grid[dayIndex][slotIndex]:
                result.append(self.formatSlot(dayIndex, slotIndex))

    # Example: (0, 1) -> "MONDAY,1,04:00,08:00"
    def formatSlot(self, dayIndex: int, slotIndex: int) -> str:
        startHour = slotIndex * self.hoursPerSlot
        endHour = startHour + self.hoursPerSlot
        start = self.toTime(startHour)
        end = self.toTime(endHour)
        return f"{self.days[dayIndex]},{slotIndex},{start},{end}"

    # Example: 4 -> "04:00", 24 -> "24:00"
    def toTime(self, hour: int) -> str:
        return f"{hour:02d}:00"
```