# Design Train Platform Management System in Java

#### Problem Statement
[https://codezym.com/question/32-design-train-platform-management-system](https://codezym.com/question/32-design-train-platform-management-system)

A train sits on one platform for one unbroken block of minutes. So we don't need to track every single minute. We only need to track these blocks of time.

The core idea: every platform keeps a list of its **bookings** (which train, from when, till when), sorted by time. When a new train comes, each platform tells us the earliest time it can take this train. We pick the platform with the smallest delay, and the lowest platform number if there is a tie.

The rule for picking a platform is the part most likely to change later, so we keep it behind the **Strategy** design pattern. Strategy alone is the best fit here. The rest of the problem is about storing time blocks and searching them, and a sorted list does that well. More patterns would only add code.

We will start with a simple minute by minute solution, see why it is slow, and then improve it in three small steps.

## Quick Recap

- A train stays on its platform from its start time to its departure time, both inclusive.
- Start time is `arrivalTime + delay`, so `departureTime = arrivalTime + waitTime - 1 + delay`.
- If a train leaves platform `p` at time `t`, the next train can use `p` from time `t + 1`.
- Choose the platform with the lowest delay. On a tie, choose the lowest platform number.
- Platform numbers are 0-based. Queries return `""` or `-1` when nothing is found.

## Solution 1: Simple Approach (Check Every Minute)

### Idea

For each platform, remember which train is there at every single minute.

```java
// one map per platform: minute -> trainId
List<Map<Integer, String>> occupied = new ArrayList<>();

// trainId -> platform, used by getPlatformOfTrain()
Map<String, Integer> platformOfTrain = new HashMap<>();
```

To assign a train, try delay `0`, then `1`, then `2` and so on. For each delay, check the platforms from 0 upwards. The first platform that is free for every minute of the stay wins.

```java
// try delay 0, 1, 2 ... and, for each delay, platform 0, 1, 2 ...
for (int delay = 0; ; delay++) {
    int start = arrivalTime + delay;
    for (int p = 0; p < occupied.size(); p++) {
        if (isFree(p, start, start + waitTime - 1)) {
            // mark every minute of the stay
            for (int t = start; t < start + waitTime; t++) {
                occupied.get(p).put(t, trainId);
            }
            platformOfTrain.put(trainId, p);
            return p + "," + delay;
        }
    }
}
```

`isFree()` simply checks every minute from start to end.

The loop order handles the tie rule for us. The outer loop is the delay and the inner loop goes from platform 0 upwards, so the first match has the lowest delay and the lowest platform number.

Both queries are now map lookups. `getTrainAtPlatform()` reads `occupied.get(p).get(timestamp)`. `getPlatformOfTrain()` finds the platform in `platformOfTrain`, then checks the minute map to see if the train is there at that timestamp.

### Complexity

Let `D` be the delay, `P` the number of platforms and `W` the waitTime.

- `assignPlatform`: O(D × P × W)
- `getTrainAtPlatform` and `getPlatformOfTrain`: O(1)

## Solution 2: Optimized Approach (Bookings + Binary Search + Strategy)

### Why Solution 1 Is Slow

- **Too much memory:** a train that stays 30 minutes adds 30 map entries.
- **Too much repeated work:** every extra minute of delay checks every platform and every minute again. If 100 trains of 30 minutes each arrive together at a station with 1 platform, the last train tries almost 3000 delays before it finds a slot.

We fix this in three small steps.

### Fix 1: Store One Booking per Train

A train never leaves in the middle of its stay. So one small object can describe the whole stay: train, platform, start time and end time. We call it a `Booking`.

Each platform keeps a list of its bookings, sorted by time.

Two bookings on the same platform never overlap. So when the list is sorted by start time, it is also sorted by end time. Everything below depends on this fact.

Memory is now one object per train, no matter how long the train stays.

### Fix 2: Jump Over Bookings Instead of Trying Every Minute

To find the earliest start on one platform, set `start = arrivalTime` and walk through its bookings in time order:

- If our stay ends before this booking starts, we fit in the gap. Stop.
- If not, this booking is in our way. Jump to `start = booking.endTime + 1` and check the next booking.

Example from the problem: T7 arrives at 9 and needs 3 minutes. Platform 0 has T4 from 5 to 9.

- `start = 9`: our stay would be 9 to 11, which hits T4. Jump to 10.
- `start = 10`: no more bookings, so T7 can start at 10. The delay is 1.

The work now depends on how many bookings are in the way, not on how long the delay is.

This walk also finds free gaps between two bookings. So it gives the right answer even when trains are not added in time order.

### Fix 3: Skip Old Bookings with Binary Search

Bookings that ended before the train arrived can never block it. The list is sorted by end time, so a binary search finds the first booking that ends at or after a given time.

This one helper, `firstBookingEndingAtOrAfter(time)`, is used in three places:

- `getEarliestStart()`: the walk from Fix 2 starts here, not at the beginning of the list.
- `book()`: the new booking is inserted at this index, so the list stays sorted.
- `getTrainAt()`: the booking at this index is the only one that can cover the timestamp. If it has already started, that is our train.

A sorted list with binary search is enough here, so we don't need a tree based map.

### Example Walkthrough

Here are the assignments from the problem. For each call, every platform reports its earliest start, and the smallest one wins.

| Call | Earliest start on P0, P1, P2 | Result |
|---|---|---|
| `assignPlatform("T1", 0, 5)` | 0, 0, 0 | `"0,0"` |
| `assignPlatform("T2", 2, 3)` | 5, 2, 2 | `"1,0"` |
| `assignPlatform("T3", 4, 4)` | 5, 5, 4 | `"2,0"` |
| `assignPlatform("T4", 5, 5)` | 5, 5, 8 | `"0,0"` |
| `assignPlatform("T5", 9, 1)` | 10, 9, 9 | `"1,0"` |
| `assignPlatform("T6", 9, 2)` | 10, 10, 9 | `"2,0"` |
| `assignPlatform("T7", 9, 3)` | 10, 10, 11 | `"0,1"` |

Look at T4 and T7: two platforms tie on the earliest start, so the lower platform number wins.

### Which Design Pattern Fits?

**Strategy (used).** The rule "lowest delay first, then lowest platform number" lives in its own class, `LowestDelayStrategy`, behind the `PlatformSelectionStrategy` interface.

A real station may want a different rule tomorrow. For example, it may keep platform 0 for express trains, or spread trains evenly across all platforms. With Strategy, we write one new class and swap it in where the manager creates its strategy. The platforms and bookings do not change at all.

If we wrote the rule directly inside `assignPlatform()`, every new rule would mean editing the manager and adding more if/else checks.

**State (looks useful, but not a fit).** It is tempting to give each platform a state like Free or Occupied. But queries can ask about any timestamp, past or future. In the example, platform 1 is busy at time 3, free at time 6 and busy again at time 9. One "current state" cannot answer all of these, so we store bookings instead.

**Observer (looks useful, but not needed).** We could notify display boards when a train arrives or leaves. But there is no ticking clock in this problem. Every query brings its own timestamp and we just look it up, so there is nothing to notify.

### Classes and Data Structures

- **`Booking`**: one train's stay. With the platform and both times in one object, a single lookup tells us where and when a train is.
- **`Platform`**: owns its sorted `List<Booking>`. The sorted order is what makes the jumping walk and the binary search possible.
- **`PlatformSelectionStrategy` and `LowestDelayStrategy`**: the rule for choosing a platform. The strategy asks every platform for its earliest start and keeps the smallest. It uses a strict `<`, so on a tie the platform that came first, which has the lower number, is kept.
- **`TrainPlatformManager`**: the entry point. It holds the platforms, the strategy and a `Map<String, Booking>` from trainId to booking. This map lets `getPlatformOfTrain()` find any train in O(1). A train that is still waiting out its delay, or has already left, is not on any platform, so we return `-1`.

### Complexity

Let `P` be the number of platforms, `B` the number of bookings on one platform and `K` the number of bookings that block the new train.

- `assignPlatform`: O(P × (log B + K)) to pick the platform, plus the list insert. When trains come in time order, the new booking goes to the end of the list, so the insert is O(1).
- `getTrainAtPlatform`: O(log B)
- `getPlatformOfTrain`: O(1)
- Memory: one `Booking` per train.

`K` stays small unless many trains are waiting for the same platform at the same time.

### Code

```java
import java.util.*;

/**
 * One train's stay on one platform.
 * Both startTime and endTime are inclusive.
 */
class Booking {
    String trainId;
    int platformNumber;
    int startTime;
    int endTime;

    Booking(String trainId, int platformNumber, int startTime, int endTime) {
        this.trainId = trainId;
        this.platformNumber = platformNumber;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    boolean isActiveAt(int timestamp) {
        return startTime <= timestamp && timestamp <= endTime;
    }
}

/**
 * A platform and all its bookings, sorted by time.
 * Bookings on one platform never overlap, so this list is sorted
 * by startTime and by endTime at the same time.
 */
class Platform {
    int platformNumber;
    List<Booking> bookings = new ArrayList<>();

    Platform(int platformNumber) {
        this.platformNumber = platformNumber;
    }

    /**
     * Binary search. Returns the index of the first booking that ends
     * at or after 'time', or bookings.size() if there is none.
     */
    int firstBookingEndingAtOrAfter(int time) {
        int low = 0;
        int high = bookings.size();
        while (low < high) {
            int mid = (low + high) / 2;
            if (bookings.get(mid).endTime < time) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /**
     * Earliest time, at or after arrivalTime, when this platform
     * stays free for waitTime minutes in a row.
     */
    int getEarliestStart(int arrivalTime, int waitTime) {
        int start = arrivalTime;
        // bookings before this index ended before arrivalTime, so they can't block us
        for (int i = firstBookingEndingAtOrAfter(arrivalTime); i < bookings.size(); i++) {
            Booking booking = bookings.get(i);
            int end = start + waitTime - 1;
            if (end < booking.startTime) {
                break; // we fit in the gap before this booking
            }
            start = booking.endTime + 1; // blocked, so try right after this booking
        }
        return start;
    }

    /** Books the earliest free slot for the train and returns the booking. */
    Booking book(String trainId, int arrivalTime, int waitTime) {
        int start = getEarliestStart(arrivalTime, waitTime);
        Booking booking = new Booking(trainId, platformNumber, start, start + waitTime - 1);
        // insert at the right index so the list stays sorted
        bookings.add(firstBookingEndingAtOrAfter(start), booking);
        return booking;
    }

    /** Returns the train on this platform at 'timestamp', or "" if it is empty. */
    String getTrainAt(int timestamp) {
        // only the first booking ending at or after timestamp can cover it
        int i = firstBookingEndingAtOrAfter(timestamp);
        if (i < bookings.size() && bookings.get(i).startTime <= timestamp) {
            return bookings.get(i).trainId;
        }
        return "";
    }
}

/**
 * Strategy pattern: decides which platform a new train gets.
 */
interface PlatformSelectionStrategy {
    Platform selectPlatform(List<Platform> platforms, int arrivalTime, int waitTime);
}

/**
 * The rule from the problem: lowest delay first,
 * then the lowest platform number.
 */
class LowestDelayStrategy implements PlatformSelectionStrategy {
    public Platform selectPlatform(List<Platform> platforms, int arrivalTime, int waitTime) {
        Platform bestPlatform = null;
        int bestStart = Integer.MAX_VALUE;
        for (Platform platform : platforms) {
            int start = platform.getEarliestStart(arrivalTime, waitTime);
            // strict '<' keeps the lower platform number on a tie
            if (start < bestStart) {
                bestStart = start;
                bestPlatform = platform;
            }
        }
        return bestPlatform;
    }
}

public class TrainPlatformManager {
    List<Platform> platforms = new ArrayList<>();
    // trainId -> its booking, so we can find any train in O(1)
    Map<String, Booking> bookingOfTrain = new HashMap<>();
    PlatformSelectionStrategy selectionStrategy = new LowestDelayStrategy();

    public TrainPlatformManager(int platformCount) {
        for (int i = 0; i < platformCount; i++) {
            platforms.add(new Platform(i));
        }
    }

    public String assignPlatform(String trainId, int arrivalTime, int waitTime) {
        Platform platform = selectionStrategy.selectPlatform(platforms, arrivalTime, waitTime);
        Booking booking = platform.book(trainId, arrivalTime, waitTime);
        bookingOfTrain.put(trainId, booking);
        int delay = booking.startTime - arrivalTime;
        return platform.platformNumber + "," + delay;
    }

    public String getTrainAtPlatform(int platformNumber, int timestamp) {
        if (platformNumber < 0 || platformNumber >= platforms.size()) {
            return "";
        }
        return platforms.get(platformNumber).getTrainAt(timestamp);
    }

    public int getPlatformOfTrain(String trainId, int timestamp) {
        Booking booking = bookingOfTrain.get(trainId);
        // -1 if the train is unknown, still waiting (delay), or already left
        if (booking == null || !booking.isActiveAt(timestamp)) {
            return -1;
        }
        return booking.platformNumber;
    }
}
```