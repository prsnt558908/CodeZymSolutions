# Design Train Platform Management System in Python

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

```python
# one dict per platform: minute -> trainId
self.occupied = [{} for _ in range(platformCount)]

# trainId -> platform, used by getPlatformOfTrain()
self.platformOfTrain = {}
```

To assign a train, try delay `0`, then `1`, then `2` and so on. For each delay, check the platforms from 0 upwards. The first platform that is free for every minute of the stay wins.

```python
# try delay 0, 1, 2 ... and, for each delay, platform 0, 1, 2 ...
delay = 0
while True:
    start = arrivalTime + delay
    for p in range(len(self.occupied)):
        if self.isFree(p, start, start + waitTime - 1):
            # mark every minute of the stay
            for t in range(start, start + waitTime):
                self.occupied[p][t] = trainId
            self.platformOfTrain[trainId] = p
            return f"{p},{delay}"
    delay += 1
```

`isFree()` simply checks every minute from start to end.

The loop order handles the tie rule for us. The outer loop is the delay and the inner loop goes from platform 0 upwards, so the first match has the lowest delay and the lowest platform number.

Both queries are now dict lookups. `getTrainAtPlatform()` reads `self.occupied[p].get(timestamp, "")`. `getPlatformOfTrain()` finds the platform in `platformOfTrain`, then checks the minute dict to see if the train is there at that timestamp.

### Complexity

Let `D` be the delay, `P` the number of platforms and `W` the waitTime.

- `assignPlatform`: O(D × P × W)
- `getTrainAtPlatform` and `getPlatformOfTrain`: O(1)

## Solution 2: Optimized Approach (Bookings + Binary Search + Strategy)

### Why Solution 1 Is Slow

- **Too much memory:** a train that stays 30 minutes adds 30 dict entries.
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

A plain sorted list with binary search is enough here, so we don't need any extra library.

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

**Strategy (used).** The rule "lowest delay first, then lowest platform number" lives in its own class, `LowestDelayStrategy`, behind the `PlatformSelectionStrategy` base class.

A real station may want a different rule tomorrow. For example, it may keep platform 0 for express trains, or spread trains evenly across all platforms. With Strategy, we write one new class and swap it in where the manager creates its strategy. The platforms and bookings do not change at all.

If we wrote the rule directly inside `assignPlatform()`, every new rule would mean editing the manager and adding more if/else checks.

**State (looks useful, but not a fit).** It is tempting to give each platform a state like Free or Occupied. But queries can ask about any timestamp, past or future. In the example, platform 1 is busy at time 3, free at time 6 and busy again at time 9. One "current state" cannot answer all of these, so we store bookings instead.

**Observer (looks useful, but not needed).** We could notify display boards when a train arrives or leaves. But there is no ticking clock in this problem. Every query brings its own timestamp and we just look it up, so there is nothing to notify.

### Classes and Data Structures

- **`Booking`**: one train's stay. With the platform and both times in one object, a single lookup tells us where and when a train is.
- **`Platform`**: owns its sorted list of bookings. The sorted order is what makes the jumping walk and the binary search possible.
- **`PlatformSelectionStrategy` and `LowestDelayStrategy`**: the rule for choosing a platform. `PlatformSelectionStrategy` is an abstract base class, so every new rule must provide `selectPlatform()`. `LowestDelayStrategy` asks every platform for its earliest start and keeps the smallest. It uses a strict `<`, so on a tie the platform that came first, which has the lower number, is kept.
- **`TrainPlatformManager`**: the entry point. It holds the platforms, the strategy and a dict `bookingOfTrain` from trainId to booking. This dict lets `getPlatformOfTrain()` find any train in O(1). A train that is still waiting out its delay, or has already left, is not on any platform, so we return `-1`.

### Complexity

Let `P` be the number of platforms, `B` the number of bookings on one platform and `K` the number of bookings that block the new train.

- `assignPlatform`: O(P × (log B + K)) to pick the platform, plus the list insert. When trains come in time order, the new booking goes to the end of the list, so the insert is O(1).
- `getTrainAtPlatform`: O(log B)
- `getPlatformOfTrain`: O(1)
- Memory: one `Booking` per train.

`K` stays small unless many trains are waiting for the same platform at the same time.

### Code

```python
from abc import ABC, abstractmethod
from typing import Dict, List


class Booking:
    """One train's stay on one platform.
    Both startTime and endTime are inclusive."""

    def __init__(self, trainId: str, platformNumber: int, startTime: int, endTime: int):
        self.trainId = trainId
        self.platformNumber = platformNumber
        self.startTime = startTime
        self.endTime = endTime

    def isActiveAt(self, timestamp: int) -> bool:
        return self.startTime <= timestamp <= self.endTime


class Platform:
    """A platform and all its bookings, sorted by time.
    Bookings on one platform never overlap, so this list is sorted
    by startTime and by endTime at the same time."""

    def __init__(self, platformNumber: int):
        self.platformNumber = platformNumber
        self.bookings: List[Booking] = []

    def firstBookingEndingAtOrAfter(self, time: int) -> int:
        """Binary search. Returns the index of the first booking that ends
        at or after 'time', or len(self.bookings) if there is none."""
        low = 0
        high = len(self.bookings)
        while low < high:
            mid = (low + high) // 2
            if self.bookings[mid].endTime < time:
                low = mid + 1
            else:
                high = mid
        return low

    def getEarliestStart(self, arrivalTime: int, waitTime: int) -> int:
        """Earliest time, at or after arrivalTime, when this platform
        stays free for waitTime minutes in a row."""
        start = arrivalTime
        # bookings before this index ended before arrivalTime, so they can't block us
        for i in range(self.firstBookingEndingAtOrAfter(arrivalTime), len(self.bookings)):
            booking = self.bookings[i]
            end = start + waitTime - 1
            if end < booking.startTime:
                break  # we fit in the gap before this booking
            start = booking.endTime + 1  # blocked, so try right after this booking
        return start

    def book(self, trainId: str, arrivalTime: int, waitTime: int) -> Booking:
        """Books the earliest free slot for the train and returns the booking."""
        start = self.getEarliestStart(arrivalTime, waitTime)
        booking = Booking(trainId, self.platformNumber, start, start + waitTime - 1)
        # insert at the right index so the list stays sorted
        self.bookings.insert(self.firstBookingEndingAtOrAfter(start), booking)
        return booking

    def getTrainAt(self, timestamp: int) -> str:
        """Returns the train on this platform at 'timestamp', or "" if it is empty."""
        # only the first booking ending at or after timestamp can cover it
        i = self.firstBookingEndingAtOrAfter(timestamp)
        if i < len(self.bookings) and self.bookings[i].startTime <= timestamp:
            return self.bookings[i].trainId
        return ""


class PlatformSelectionStrategy(ABC):
    """Strategy pattern: decides which platform a new train gets."""

    @abstractmethod
    def selectPlatform(self, platforms: List[Platform], arrivalTime: int, waitTime: int) -> Platform:
        pass


class LowestDelayStrategy(PlatformSelectionStrategy):
    """The rule from the problem: lowest delay first,
    then the lowest platform number."""

    def selectPlatform(self, platforms: List[Platform], arrivalTime: int, waitTime: int) -> Platform:
        bestPlatform = None
        bestStart = float("inf")
        for platform in platforms:
            start = platform.getEarliestStart(arrivalTime, waitTime)
            # strict '<' keeps the lower platform number on a tie
            if start < bestStart:
                bestStart = start
                bestPlatform = platform
        return bestPlatform


class TrainPlatformManager:
    def __init__(self, platformCount: int):
        self.platforms: List[Platform] = [Platform(i) for i in range(platformCount)]
        # trainId -> its booking, so we can find any train in O(1)
        self.bookingOfTrain: Dict[str, Booking] = {}
        self.selectionStrategy: PlatformSelectionStrategy = LowestDelayStrategy()

    def assignPlatform(self, trainId: str, arrivalTime: int, waitTime: int) -> str:
        platform = self.selectionStrategy.selectPlatform(self.platforms, arrivalTime, waitTime)
        booking = platform.book(trainId, arrivalTime, waitTime)
        self.bookingOfTrain[trainId] = booking
        delay = booking.startTime - arrivalTime
        return f"{platform.platformNumber},{delay}"

    def getTrainAtPlatform(self, platformNumber: int, timestamp: int) -> str:
        if platformNumber < 0 or platformNumber >= len(self.platforms):
            return ""
        return self.platforms[platformNumber].getTrainAt(timestamp)

    def getPlatformOfTrain(self, trainId: str, timestamp: int) -> int:
        booking = self.bookingOfTrain.get(trainId)
        # -1 if the train is unknown, still waiting (delay), or already left
        if booking is None or not booking.isActiveAt(timestamp):
            return -1
        return booking.platformNumber
```