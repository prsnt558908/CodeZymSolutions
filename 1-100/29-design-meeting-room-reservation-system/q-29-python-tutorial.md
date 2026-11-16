# Design Meeting Room Reservation System in Python

#### Problem Statement
[https://codezym.com/question/29-design-meeting-room-reservation-system](https://codezym.com/question/29-design-meeting-room-reservation-system)

## Core Idea

This problem is about choosing the right data structures. It does not need a design pattern, because there is one fixed rule for picking a room and no behavior that changes. Three small classes are enough: `RoomBooking`, `Room` and `Meeting`.

Keep the rooms sorted by id and check them in that order. The first free room we find is always the lexicographically smallest one.

Inside each room, keep the meetings sorted by start time. Then checking a room needs just one quick lookup, not a scan of all its meetings.

Finally, a dict from meeting id to meeting makes cancelling fast.

## Why Not a Design Pattern?

Two patterns look useful at first. Both would only add extra code here.

**Strategy pattern:** it sounds good for "how do we pick a room". But the problem has only one rule: the smallest room id. Sorted rooms already give us that rule. A separate strategy class would be extra code with nothing to switch.

**State pattern:** it sounds good because a room looks "free" or "booked". But a room is not simply free or booked. It depends on the time range we ask about.

A room can be busy at 10 and free at 30. So there is no single state to store.

## When Do Two Meetings Overlap?

Time ranges are inclusive. Two meetings `[a, b]` and `[c, d]` overlap when each one starts at or before the end of the other:

```
a <= d  and  c <= b
```

So `[10, 20]` and `[20, 30]` overlap, because both use time 20. But `[5, 5]` and `[6, 10]` do not overlap.

## Solution 1: Brute Force

Each room keeps a plain list of its meetings. To check a room, we compare the new meeting with every meeting in it.

```python
# Room: compare with every meeting in this room
def isFree(self, startTime: int, endTime: int) -> bool:
    for m in self.meetings:
        if m.startTime <= endTime and startTime <= m.endTime:
            return False
    return True
```

To book, walk the rooms in sorted order and take the first free one.

```python
# RoomBooking: rooms are sorted by id, so the first free room is the answer
for room in self.rooms:
    if room.isFree(startTime, endTime):
        meeting = Meeting(meetingId, room, startTime, endTime)
        room.meetings.append(meeting)
        self.activeMeetings[meetingId] = meeting
        return room.id
return ""
```

This is correct, but slow. Checking one room costs `O(M)`, where `M` is the number of meetings in that room. Cancelling also has to search the room's list.

If a room has thousands of meetings, every booking pays for all of them. Let's make the room check faster.

## Solution 2: Keep Each Room's Meetings Sorted

### The key observation

Meetings in a room never overlap. So when we sort them by start time, they sit one after another on the timeline.

To place a new meeting `[start, end]`, we only need to look at one meeting: **the meeting that starts last, at or before `end`**.

- Meetings that start after `end` are fully to the right. They cannot overlap.
- All earlier meetings end before this one starts. So if this one does not overlap, none of them do.
- If no meeting starts at or before `end`, the room is free.

Here is an example:

```
Room A:  [2,4]    [8,12]        [20,25]

New meeting [10,16]
Last meeting that starts at or before 16: [8,12]
12 >= 10, so they overlap. Room A is busy.

New meeting [13,16]
Last meeting that starts at or before 16: [8,12]
12 < 13, so no overlap. Room A is free.

New meeting [14,22]
Last meeting that starts at or before 22: [20,25]
25 >= 14, so they overlap. Room A is busy.
```

The last case shows that a meeting starting inside our range is also caught.

### Classes and data structures

**`Room`** stores the meetings of one room using two simple structures. Together, they work like a sorted map from start time to end time.

- `startTimes`: a list of start times that is always kept sorted. `bisect_right(startTimes, endTime) - 1` gives the index of the last start time at or before `endTime` in `O(log M)`.
- `endTimeByStart`: a dict that gives the end time for each start time.

`insort()` puts a new start time at the right place, so the list stays sorted.

Start times inside one room are always unique, because two meetings with the same start would overlap. That is why a start time can safely be a dict key.

**`Meeting`** remembers its room and its start time. So cancel knows exactly what to remove and from where.

**`RoomBooking`** holds:

- `rooms`: a list of `Room` objects, sorted by id once, in the constructor.
- `activeMeetings`: a dict from meeting id to meeting, for a quick cancel.

### How bookMeeting works

1. If the meeting id is already active, return `""`. One id cannot point to two active meetings.
2. Walk the rooms in sorted order. At the first free room, add the meeting and return the room id.
3. If no room is free, return `""`.

### How cancelMeeting works

1. Remove the meeting from `activeMeetings`. If it was not there, return `False`.
2. Remove its start time from its room and return `True`.

### Example 1 walkthrough

| Call | roomA | roomB | Returns |
|---|---|---|---|
| `bookMeeting("m1", 10, 20)` | [10,20] | | `"roomA"` |
| `bookMeeting("m2", 15, 25)` | [10,20] | [15,25] | `"roomB"` |
| `bookMeeting("m3", 20, 30)` | [10,20] | [15,25] | `""` |
| `cancelMeeting("m1")` | | [15,25] | `True` |
| `bookMeeting("m4", 20, 30)` | [20,30] | [15,25] | `"roomA"` |

For `m3`, the meeting in roomA ends at 20 and the meeting in roomB ends at 25. Both are `>= 20`, so both rooms are busy.

### Complexity

Let `R` be the number of rooms, `M` the number of meetings in one room and `N` the number of active meetings.

- Constructor: `O(R log R)` to sort the rooms.
- `bookMeeting`: `O(R log M)` to find a free room in the worst case, plus `O(M)` for `insort()` to shift list items. It stops at the first free room.
- `cancelMeeting`: `O(M)`, because removing from a list shifts the items after it. This shifting is very fast in practice.
- Space: `O(R + N)`.

### Code

```python
from bisect import bisect_left, bisect_right, insort
from typing import List


class RoomBooking:
    """
    Books meetings into a fixed list of rooms.
    Always gives the lexicographically smallest room that is free.
    """

    def __init__(self, roomIds: List[str]):
        # all rooms, sorted by id. The first free room we find is the answer.
        self.rooms = [Room(roomId) for roomId in sorted(roomIds)]

        # meetingId -> active meeting. Lets cancelMeeting() find it quickly.
        self.activeMeetings = {}

    def bookMeeting(self, meetingId: str, startTime: int, endTime: int) -> str:
        # one id cannot point to two active meetings
        if meetingId in self.activeMeetings:
            return ""

        for room in self.rooms:
            if room.isFree(startTime, endTime):
                room.addMeeting(startTime, endTime)
                meeting = Meeting(meetingId, room, startTime, endTime)
                self.activeMeetings[meetingId] = meeting
                return room.id
        return ""

    def cancelMeeting(self, meetingId: str) -> bool:
        meeting = self.activeMeetings.pop(meetingId, None)
        if meeting is None:
            return False

        meeting.room.removeMeeting(meeting.startTime)
        return True


class Room:
    """
    A conference room and the meetings booked in it.
    Meetings in a room never overlap.
    """

    def __init__(self, roomId: str):
        self.id = roomId

        # start times of all meetings in this room, in sorted order
        self.startTimes = []

        # start time -> end time of the same meeting
        self.endTimeByStart = {}

    def isFree(self, startTime: int, endTime: int) -> bool:
        """
        Checks only one meeting: the one that starts last, at or
        before endTime. If it ends before startTime, every earlier
        meeting ends even earlier, so no meeting overlaps.
        """
        index = bisect_right(self.startTimes, endTime) - 1
        if index < 0:
            return True
        lastStart = self.startTimes[index]
        return self.endTimeByStart[lastStart] < startTime

    def addMeeting(self, startTime: int, endTime: int) -> None:
        insort(self.startTimes, startTime)
        self.endTimeByStart[startTime] = endTime

    def removeMeeting(self, startTime: int) -> None:
        index = bisect_left(self.startTimes, startTime)
        self.startTimes.pop(index)
        del self.endTimeByStart[startTime]


class Meeting:
    """
    A booked meeting. It remembers its room, so cancelling is quick.
    """

    def __init__(self, meetingId: str, room: Room,
                 startTime: int, endTime: int):
        self.id = meetingId
        self.room = room
        self.startTime = startTime
        self.endTime = endTime
```