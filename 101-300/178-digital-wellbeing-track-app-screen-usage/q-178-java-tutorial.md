# Design a Digital Wellbeing System to Track Screen Usage of Apps in Java

#### Problem Statement
[https://codezym.com/question/178-digital-wellbeing-track-app-screen-usage](https://codezym.com/question/178-digital-wellbeing-track-app-screen-usage)


## Core Idea

We need to add up screen time for each app on each day, and keep only the last 7 days of data.

The most useful hint in the problem is that days in `addUsage` never go down. So the oldest stored day is always the first one to expire. This makes a simple **queue of days** the perfect tool for throwing away old data.

For each stored day, we also keep a **map from app name to minutes** and the **total minutes of that day**. With these, `getAppUsage` and `getTotalUsage` become quick map lookups.

We will start with a brute force solution, see why it is slow, and then improve it step by step.


## Approach 1: Brute Force (Store Every Record)

The simplest idea is to save every `addUsage` call as a separate record in a list. When a query comes, we scan the whole list and add up the records that match.

We also remember the latest day, so we can ignore records older than the last 7 days.

```java
// One raw record for every addUsage() call
class UsageRecord {
    String appName;
    int day;
    int minutes;

    UsageRecord(String appName, int day, int minutes) {
        this.appName = appName;
        this.day = day;
        this.minutes = minutes;
    }
}

// Inside DigitalWellbeingSystem
List<UsageRecord> records = new ArrayList<>();
int latestDay = 0;

public void addUsage(String appName, int day, int minutes) {
    records.add(new UsageRecord(appName, day, minutes));
    latestDay = day; // days never go down, so this is the latest day
}

public int getAppUsage(String appName, int day) {
    // Days older than the last 7 days are treated as deleted
    if (day < latestDay - 6) {
        return 0;
    }
    int total = 0;
    for (UsageRecord record : records) {
        if (record.day == day && record.appName.equals(appName)) {
            total += record.minutes;
        }
    }
    return total;
}
```

`getTotalUsage` works the same way, just without the app name check.

`getUsageStatistics` has to group the records by day and app, add up their minutes, and then sort all of them.


### Problems with this approach

1. **Every query scans the full list.** With up to `10^5` calls, this can grow to billions of steps.
2. **Old records are never deleted.** The list keeps growing, even though we only need the last 7 days.
3. **Repeated records are not merged.** If an app is used 1000 times on the same day, we keep 1000 records and add them up again on every query.
4. **Statistics are rebuilt from scratch.** Every call groups and sorts the whole list again.


## Approach 2: Day-wise Maps with a Queue (Optimal)

Let's fix these problems one at a time.


### Step 1: Merge records with a map of maps

Instead of a list of raw records, we group usage first by day, and then by app.

```java
// day -> (appName -> total minutes used by that app on that day)
Map<Integer, Map<String, Integer>> usageByDay;
```

When the same app is used again on the same day, we simply add the new minutes to its existing value. Now `getAppUsage` is just two map lookups.

**Why a map of maps?** The outer map finds a day instantly, and it can delete a whole day in one step. The inner map finds an app inside that day instantly.


### Step 2: Keep a running total for each day

```java
// day -> total minutes used by all apps on that day
Map<Integer, Integer> totalByDay;
```

Whenever we add minutes to an app, we also add them to that day's total. So `getTotalUsage` is a single lookup, and we never loop over all the apps of a day.


### Step 3: Remove old days with a queue

This is the heart of the solution.

We must keep only the days from `latestDay - 6` to `latestDay`. Since days in `addUsage` never go down:

- A new day is always the newest one, so it joins the **back** of the queue.
- The oldest day is always at the **front**, so it is the first one to expire.

```java
// Stored days in increasing order: oldest at the front, newest at the back
Queue<Integer> storedDays;
```

After every `addUsage`, we look at the front of the queue. While the front day is older than `latestDay - 6`, we remove it from the queue and from both maps.

**Why a queue?** It always shows the oldest day in `O(1)`, and it keeps the days sorted for free. We never have to search for expired days. After cleanup it holds at most 7 days, so this step is very fast.

Notice that the query methods no longer need to check the 7 day rule. A day outside the window was either removed already or never added, so the lookup simply returns `0`.


### Step 4: Build the statistics

The output must be sorted by day, and then by app name.

- Days are already in increasing order inside the queue.
- For each day, we copy its app names into a list and sort them.

So we never sort all the data together. We only sort the app names of one day at a time.

Java's default string sorting is case-sensitive. Uppercase letters come before lowercase letters, so `"Zoom"` comes before `"chat"`. This is exactly what the problem asks for.


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
storedDays :  front -> [4, 9, 10] <- back

usageByDay :  4  -> { Music: 70 }
              9  -> { Browser: 35 }
              10 -> { Game: 120 }

totalByDay :  { 4: 70, 9: 35, 10: 120 }
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


### Java Code

```java
import java.util.*;

public class DigitalWellbeingSystem {

    // We keep data only for the last 7 days
    int windowSize = 7;

    // day -> (appName -> total minutes used by that app on that day)
    Map<Integer, Map<String, Integer>> usageByDay;

    // day -> total minutes used by all apps on that day
    Map<Integer, Integer> totalByDay;

    // Stored days in increasing order: oldest at the front, newest at the back
    Queue<Integer> storedDays;

    public DigitalWellbeingSystem() {
        usageByDay = new HashMap<>();
        totalByDay = new HashMap<>();
        storedDays = new LinkedList<>();
    }

    public void addUsage(String appName, int day, int minutes) {
        // Days never go down, so a day we have not seen yet is the newest one
        if (!usageByDay.containsKey(day)) {
            usageByDay.put(day, new HashMap<>());
            totalByDay.put(day, 0);
            storedDays.add(day);
        }

        // Add minutes to this app and to the day's total
        Map<String, Integer> appMinutes = usageByDay.get(day);
        appMinutes.put(appName, appMinutes.getOrDefault(appName, 0) + minutes);
        totalByDay.put(day, totalByDay.get(day) + minutes);

        // The current day is the latest day seen so far
        removeOldDays(day);
    }

    /**
     * Removes every day older than (latestDay - 6).
     * The oldest day is always at the front of the queue.
     */
    void removeOldDays(int latestDay) {
        int oldestAllowedDay = latestDay - windowSize + 1;
        while (!storedDays.isEmpty() && storedDays.peek() < oldestAllowedDay) {
            int oldDay = storedDays.poll();
            usageByDay.remove(oldDay);
            totalByDay.remove(oldDay);
        }
    }

    public int getAppUsage(String appName, int day) {
        // A missing day means no usage, or the day was already removed
        Map<String, Integer> appMinutes = usageByDay.get(day);
        if (appMinutes == null) {
            return 0;
        }
        return appMinutes.getOrDefault(appName, 0);
    }

    public int getTotalUsage(int day) {
        return totalByDay.getOrDefault(day, 0);
    }

    public List<String> getUsageStatistics() {
        List<String> result = new ArrayList<>();

        // The queue already has days in increasing order
        for (int day : storedDays) {
            Map<String, Integer> appMinutes = usageByDay.get(day);

            // Sort app names of this day (case-sensitive)
            List<String> appNames = new ArrayList<>(appMinutes.keySet());
            Collections.sort(appNames);

            for (String appName : appNames) {
                result.add(day + "," + appName + "," + appMinutes.get(appName));
            }
        }
        return result;
    }
}
```