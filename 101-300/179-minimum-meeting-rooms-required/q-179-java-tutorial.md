# Minimum Meeting Rooms Required in Java

#### Problem Statement
[https://codezym.com/question/179-minimum-meeting-rooms-required](https://codezym.com/question/179-minimum-meeting-rooms-required)


## Core Idea

Imagine every meeting as a group of people who walk into a room and later walk out.

The number of rooms we need is simply the **largest number of meetings running at the same moment**.

So the whole problem becomes: **find the busiest moment of the day.**

We will start with a simple brute force check. Then we will make it faster by sorting start and end times. Finally, we will use the small time range to count everything with one walk over a timeline.


## Problem in Short

Each interval is a string like `"2,5"`. We split it to get `start = 2` and `end = 5`.

Intervals are half-open `[start, end)`. So a meeting that ends at `3` and a meeting that starts at `3` can use the same room.

Return the minimum number of rooms so that no two overlapping meetings share a room.


## Two Key Observations

**1. Rooms needed = most meetings running together.**

If 3 meetings run at the same moment, we clearly need 3 rooms.

And whenever a meeting ends, its room becomes free for the next meeting. So we never need more rooms than the busiest moment.


**2. The busiest moment always begins at some start time.**

The count of running meetings only goes up when a meeting starts. It never goes up when a meeting ends.

So if we check the count at every start time, we will surely find the maximum.


## Reading the Input

Every solution begins by turning the strings into numbers.

```java
String[] parts = intervals.get(i).split(",");
int start = Integer.parseInt(parts[0]);
int end = Integer.parseInt(parts[1]);
```


## Solution 1: Brute Force

### Idea

Go to each meeting's start time. At that moment, count how many meetings are running.

A meeting is running at time `t` if `start <= t < end`.

Notice the `<` on the end side. That is the half-open rule. A meeting ending at `t` is already over at `t`.

The biggest count we see is the answer.


### Code

```java
import java.util.*;

public class MinimumMeetingRoomsRequired {

    public MinimumMeetingRoomsRequired() {
    }

    public int minRooms(List<String> intervals) {
        int n = intervals.size();
        int[] starts = new int[n];
        int[] ends = new int[n];

        // turn every "start,end" string into two numbers
        for (int i = 0; i < n; i++) {
            String[] parts = intervals.get(i).split(",");
            starts[i] = Integer.parseInt(parts[0]);
            ends[i] = Integer.parseInt(parts[1]);
        }

        int maxRooms = 0;

        // the busiest moment always begins at some meeting's start time
        for (int i = 0; i < n; i++) {
            int time = starts[i];
            int running = 0;

            // count meetings that are in progress at this time
            for (int j = 0; j < n; j++) {
                // half-open rule: a meeting ending at 'time' is already over
                if (starts[j] <= time && time < ends[j]) {
                    running++;
                }
            }
            maxRooms = Math.max(maxRooms, running);
        }
        return maxRooms;
    }
}
```


### What is the problem?

For every meeting, we scan all the meetings again. That is `O(n²)` work.

With 12000 meetings, that is about 144 million checks. It works, but most of that work is repeated.

We need a way to look at time in order, so we do not have to rescan everything.


## Solution 2: Sort Start Times and End Times

### Idea

Here is the trick: **we do not care which start belongs to which end.**

We only care about two kinds of events: "a meeting started" and "a meeting ended". A free room is a free room, no matter which meeting left it.

So we put all start times in one array and all end times in another. Then we sort both.

Now we walk through time from left to right with two pointers:

- If the next start comes **before** the next end, a meeting begins. Running count goes up by 1.
- Otherwise, a meeting ends first. Running count goes down by 1.

We keep track of the highest running count.


### Handling the tie

What if a start and an end happen at the same time? For example, one meeting ends at `3` and another starts at `3`.

We process the **end first**. The room becomes free, and then the new meeting takes it. This is exactly the half-open rule.

In the code, this comes for free. We treat it as a start only when `starts[i] < ends[j]`. When they are equal, the end is processed first.


### Why two sorted arrays?

Sorted arrays let us replay the day in time order using just two pointers.

We keep them separate because a room does not care which meeting freed it. This removes the need to track pairs.


### When can we stop?

Once all start times are processed, the running count can only go down. So we stop as soon as `i` reaches `n`.


### Dry Run

Example 3: `"10,20", "1,5", "6,9", "4,8"`

Sorted starts: `1, 4, 6, 10`

Sorted ends: `5, 8, 9, 20`

| Next Start | Next End | Action | Running | Max |
|---|---|---|---|---|
| 1 | 5 | start | 1 | 1 |
| 4 | 5 | start | 2 | 2 |
| 6 | 5 | end | 1 | 2 |
| 6 | 8 | start | 2 | 2 |
| 10 | 8 | end | 1 | 2 |
| 10 | 9 | end | 0 | 2 |
| 10 | 20 | start | 1 | 2 |

All starts are done. Answer is `2`.


### Code

```java
import java.util.*;

public class MinimumMeetingRoomsRequired {

    public MinimumMeetingRoomsRequired() {
    }

    public int minRooms(List<String> intervals) {
        int n = intervals.size();
        int[] starts = new int[n];
        int[] ends = new int[n];

        // turn every "start,end" string into two numbers
        for (int i = 0; i < n; i++) {
            String[] parts = intervals.get(i).split(",");
            starts[i] = Integer.parseInt(parts[0]);
            ends[i] = Integer.parseInt(parts[1]);
        }

        // a free room does not care which meeting left it,
        // so start times and end times can be sorted separately
        Arrays.sort(starts);
        Arrays.sort(ends);

        int running = 0;   // meetings in progress right now
        int maxRooms = 0;
        int i = 0;         // next start time to look at
        int j = 0;         // next end time to look at

        while (i < n) {
            if (starts[i] < ends[j]) {
                // a new meeting starts before any running meeting ends
                running++;
                i++;
                maxRooms = Math.max(maxRooms, running);
            } else {
                // a meeting ends first (or at the same moment), its room is free
                running--;
                j++;
            }
        }
        return maxRooms;
    }
}
```


This is fast and works for **any** range of time values.

But the constraints tell us something more: time never goes above `100000`. We can use that to skip sorting.


## Solution 3: Timeline Counting (Best for These Constraints)

### Idea

Since time is small, we can make an array where the **index is the time**.

For every meeting:

- `change[start] += 1` means one more meeting is running from this time.
- `change[end] -= 1` means one less meeting is running from this time.

Then we walk from time `0` to `100000` and keep a running sum.

The running sum at time `t` is the number of meetings in progress at time `t`. The biggest running sum is the answer.


### Why does this follow the half-open rule?

If one meeting ends at `3` and another starts at `3`, then `change[3]` gets `-1` and `+1`. They cancel out.

So the two meetings are never counted together. That is exactly what we want.


### Why an array and not a map?

A map would also work, but we would need it sorted by time to walk it in order.

Since time values are small, a plain `int` array is simpler. Its indexes are already in sorted order, and each update is instant.


### Dry Run

Example 4: `"0,100", "10,20", "20,30", "30,40"`

| Time | Change | Running |
|---|---|---|
| 0 | +1 | 1 |
| 10 | +1 | 2 |
| 20 | -1 +1 = 0 | 2 |
| 30 | -1 +1 = 0 | 2 |
| 40 | -1 | 1 |
| 100 | -1 | 0 |

Highest running count is `2`. Answer is `2`.


### Code

```java
import java.util.*;

public class MinimumMeetingRoomsRequired {

    // largest possible end time, taken from the constraints
    int MAX_TIME = 100000;

    public MinimumMeetingRoomsRequired() {
    }

    public int minRooms(List<String> intervals) {
        // change[t] = (meetings starting at t) - (meetings ending at t)
        int[] change = new int[MAX_TIME + 1];

        for (String interval : intervals) {
            String[] parts = interval.split(",");
            int start = Integer.parseInt(parts[0]);
            int end = Integer.parseInt(parts[1]);

            change[start]++;   // one more meeting from 'start'
            change[end]--;     // one less meeting from 'end'
        }

        int running = 0;   // meetings in progress at time t
        int maxRooms = 0;

        // walk the timeline from left to right
        for (int t = 0; t <= MAX_TIME; t++) {
            running += change[t];
            maxRooms = Math.max(maxRooms, running);
        }
        return maxRooms;
    }
}
```


## Complexity Summary

`n` = number of meetings, `T` = largest time value (100000)

| Solution | Time | Space |
|---|---|---|
| Brute Force | O(n²) | O(n) |
| Sort Starts and Ends | O(n log n) | O(n) |
| Timeline Counting | O(n + T) | O(T) |


## Which One Should You Use?

**Solution 2** is the safe general choice. It works even if time values are huge, like `10^9`.

**Solution 3** is the simplest and fastest here, because time values are small.