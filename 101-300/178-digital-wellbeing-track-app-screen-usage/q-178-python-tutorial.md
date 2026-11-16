# Design a Digital Wellbeing System to Track Screen Usage of Apps in Python

#### Problem Statement
[https://codezym.com/question/178-digital-wellbeing-track-app-screen-usage](https://codezym.com/question/178-digital-wellbeing-track-app-screen-usage)


## Core Idea

We need to add up screen time for each app on each day, and keep only the last 7 days of data.

The most useful hint in the problem is that days in `addUsage` never go down. So the oldest stored day is always the first one to expire. This makes a simple **queue of days** the perfect tool for throwing away old data.

For each stored day, we also keep a **dictionary from app name to minutes** and the **total minutes of that day**. With these, `getAppUsage` and `getTotalUsage` become quick dictionary lookups.

We will start with a brute force solution, see why it is slow, and then improve it step by step.


## Approach 1: Brute Force (Store Every Record)

The simplest idea is to save every `addUsage` call as a separate record in a list. When a query comes, we scan the whole list and add up the records that match.

We also remember the latest day, so we can ignore records older than the last 7 days.

```python
# One raw record for every addUsage() call
class UsageRecord:
    def __init__(self, app_name: str, day: int, minutes: int):
        self.app_name = app_name
        self.day = day
        self.minutes = minutes


class DigitalWellbeingSystem:
    def __init__(self):
        self.records = []      # one UsageRecord per addUsage() call
        self.latest_day = 0

    def addUsage(self, appName: str, day: int, minutes: int) -> None:
        self.records.append(UsageRecord(appName, day, minutes))
        self.latest_day = day  # days never go down, so this is the latest day

    def getAppUsage(self, appName: str, day: int) -> int:
        # Days older than the last 7 days are treated as deleted
        if day < self.latest_day - 6:
            return 0
        total = 0
        for record in self.records:
            if record.day == day and record.app_name == appName:
                total += record.minutes
        return total
```

`getTotalUsage` works the same way, just without the app name check.

`getUsageStatistics` has to group the records by day and app, add up their minutes, and then sort all of them.


### Problems with this approach

1. **Every query scans the full list.** With up to `10^5` calls, this can grow to billions of steps.
2. **Old records are never deleted.** The list keeps growing, even though we only need the last 7 days.
3. **Repeated records are not merged.** If an app is used 1000 times on the same day, we keep 1000 records and add them up again on every query.
4. **Statistics are rebuilt from scratch.** Every call groups and sorts the whole list again.


## Approach 2: Day-wise Dictionaries with a Queue (Optimal)

Let's fix these problems one at a time.


### Step 1: Merge records with a dictionary of dictionaries

Instead of a list of raw records, we group usage first by day, and then by app.

```python
# day -> {appName -> total minutes used by that app on that day}
self.usage_by_day = {}
```

When the same app is used again on the same day, we simply add the new minutes to its existing value. Now `getAppUsage` is just two dictionary lookups.

**Why a dictionary of dictionaries?** The outer dictionary finds a day instantly, and it can delete a whole day in one step. The inner dictionary finds an app inside that day instantly.


### Step 2: Keep a running total for each day

```python
# day -> total minutes used by all apps on that day
self.total_by_day = {}
```

Whenever we add minutes to an app, we also add them to that day's total. So `getTotalUsage` is a single lookup, and we never loop over all the apps of a day.


### Step 3: Remove old days with a queue

This is the heart of the solution.

We must keep only the days from `latestDay - 6` to `latestDay`. Since days in `addUsage` never go down:

- A new day is always the newest one, so it joins the **back** (right end) of the queue.
- The oldest day is always at the **front** (left end), so it is the first one to expire.

```python
# Stored days in increasing order: oldest on the left, newest on the right
self.stored_days = deque()
```

After every `addUsage`, we look at the front of the queue. While the front day is older than `latestDay - 6`, we remove it from the queue and from both dictionaries.

**Why a queue?** It always shows the oldest day in `O(1)`, and it keeps the days sorted for free. We never have to search for expired days. After cleanup it holds at most 7 days, so this step is very fast.

**Why a `deque` and not a list?** `deque` from `collections` is the standard queue in Python. Removing from its left end with `popleft()` takes `O(1)`, while `list.pop(0)` takes `O(n)`.

Notice that the query methods no longer need to check the 7 day rule. A day outside the window was either removed already or never added, so the lookup simply returns `0`.


### Step 4: Build the statistics

The output must be sorted by day, and then by app name.

- Days are already in increasing order inside the queue.
- For each day, we sort its app names with `sorted()`.

So we never sort all the data together. We only sort the app names of one day at a time.

Python's default string sorting is case-sensitive. Uppercase letters come before lowercase letters, so `"Zoom"` comes before `"chat"`. This is exactly what the problem asks for.


### Walkthrough

Let's trace Example 2.

| Call | Latest day | Days kept | Queue after the call |
|---|---|---|---|
| `addUsage("Browser", 3, 25)` | 3 | up to 3 | `[3]` |
| `addUsage("Music", 4, 70)` | 4 | up to 4 | `[3, 4]` |
| `addUsage("Browser", 9, 35)` | 9 | 3 to 9 | `[3, 4, 9]` |
| `addUsage("Game", 10, 120)` | 10 | 4 to 10 | `[4, 9, 10]` |

In the last call, day 3 is at the front and is now too old, so it gets removed. That is why `getAppUsage("Browser", 3)` returns `0` after this call.

The final state looks like this:

```text
stored_days  :  front -> [4, 9, 10] <- back

usage_by_day :  4  -> { "Music": 70 }
                9  -> { "Browser": 35 }
                10 -> { "Game": 120 }

total_by_day :  { 4: 70, 9: 35, 10: 120 }
```

`getUsageStatistics()` walks the queue from front to back and returns `["4,Music,70", "9,Browser,35", "10,Game,120"]`.


### Complexity

Let `K` be the number of stored app-day pairs.

| Method | Time |
|---|---|
| `addUsage` | `O(1)` on average |
| `getAppUsage` | `O(1)` |
| `getTotalUsage` | `O(1)` |
| `getUsageStatistics` | `O(K log K)`, because we sort app names |

Space is `O(K)`, since we only keep data for the last 7 days.


### Python Code

```python
from collections import deque
from typing import List


class DigitalWellbeingSystem:

    def __init__(self):
        # We keep data only for the last 7 days
        self.window_size = 7

        # day -> {appName -> total minutes used by that app on that day}
        self.usage_by_day = {}

        # day -> total minutes used by all apps on that day
        self.total_by_day = {}

        # Stored days in increasing order: oldest on the left, newest on the right
        self.stored_days = deque()

    def addUsage(self, appName: str, day: int, minutes: int) -> None:
        # Days never go down, so a day we have not seen yet is the newest one
        if day not in self.usage_by_day:
            self.usage_by_day[day] = {}
            self.total_by_day[day] = 0
            self.stored_days.append(day)

        # Add minutes to this app and to the day's total
        app_minutes = self.usage_by_day[day]
        app_minutes[appName] = app_minutes.get(appName, 0) + minutes
        self.total_by_day[day] += minutes

        # The current day is the latest day seen so far
        self.removeOldDays(day)

    def removeOldDays(self, latestDay: int) -> None:
        """Removes every day older than (latestDay - 6).
        The oldest day is always at the left end of the deque."""
        oldest_allowed_day = latestDay - self.window_size + 1
        while self.stored_days and self.stored_days[0] < oldest_allowed_day:
            old_day = self.stored_days.popleft()
            del self.usage_by_day[old_day]
            del self.total_by_day[old_day]

    def getAppUsage(self, appName: str, day: int) -> int:
        # A missing day means no usage, or the day was already removed
        app_minutes = self.usage_by_day.get(day)
        if app_minutes is None:
            return 0
        return app_minutes.get(appName, 0)

    def getTotalUsage(self, day: int) -> int:
        return self.total_by_day.get(day, 0)

    def getUsageStatistics(self) -> List[str]:
        result = []

        # The deque already has days in increasing order
        for day in self.stored_days:
            app_minutes = self.usage_by_day[day]

            # Sort app names of this day (case-sensitive)
            for app_name in sorted(app_minutes):
                result.append(f"{day},{app_name},{app_minutes[app_name]}")

        return result
```