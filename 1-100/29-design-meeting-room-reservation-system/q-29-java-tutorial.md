# Design Meeting Room Reservation System in Java

#### Problem Statement
[https://codezym.com/question/29-design-meeting-room-reservation-system](https://codezym.com/question/29-design-meeting-room-reservation-system)

## Core Idea

This problem is about choosing the right data structures. It does not need a design pattern, because there is one fixed rule for picking a room and no behavior that changes. Three small classes are enough: `RoomBooking`, `Room` and `Meeting`.

Keep the rooms sorted by id and check them in that order. The first free room we find is always the lexicographically smallest one.

Inside each room, keep the meetings sorted by start time. Then checking a room needs just one quick lookup, not a scan of all its meetings.

Finally, a map from meeting id to meeting makes cancelling fast.

## Why Not a Design Pattern?

Two patterns look useful at first. Both would only add extra code here.

**Strategy pattern:** it sounds good for "how do we pick a room". But the problem has only one rule: the smallest room id. Sorted rooms already give us that rule. A strategy interface would be an extra class with nothing to switch.

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

```java
// Room: compare with every meeting in this room
boolean isFree(int startTime, int endTime) {
    for (Meeting m : meetings) {
        if (m.startTime <= endTime && startTime <= m.endTime) return false;
    }
    return true;
}
```

To book, walk the rooms in sorted order and take the first free one.

```java
// RoomBooking: rooms are sorted by id, so the first free room is the answer
for (Room room : rooms) {
    if (room.isFree(startTime, endTime)) {
        Meeting meeting = new Meeting(meetingId, room, startTime, endTime);
        room.meetings.add(meeting);
        activeMeetings.put(meetingId, meeting);
        return room.id;
    }
}
return "";
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

- `TreeSet<Integer> startTimes`: a sorted set of start times. `startTimes.floor(endTime)` returns the last start time at or before `endTime` in `O(log M)`.
- `Map<Integer, Integer> endTimeByStart`: gives the end time for each start time.

A set works because start times inside one room are always unique. Two meetings with the same start would overlap.

**`Meeting`** remembers its room and its start time. So cancel knows exactly what to remove and from where.

**`RoomBooking`** holds:

- `List<Room> rooms`: sorted by id once, in the constructor.
- `Map<String, Meeting> activeMeetings`: meeting id to meeting, for a quick cancel.

### How bookMeeting works

1. If the meeting id is already active, return `""`. One id cannot point to two active meetings.
2. Walk the rooms in sorted order. At the first free room, add the meeting and return the room id.
3. If no room is free, return `""`.

### How cancelMeeting works

1. Remove the meeting from `activeMeetings`. If it was not there, return `false`.
2. Remove its start time from its room and return `true`.

### Example 1 walkthrough

| Call | roomA | roomB | Returns |
|---|---|---|---|
| `bookMeeting("m1", 10, 20)` | [10,20] | | `"roomA"` |
| `bookMeeting("m2", 15, 25)` | [10,20] | [15,25] | `"roomB"` |
| `bookMeeting("m3", 20, 30)` | [10,20] | [15,25] | `""` |
| `cancelMeeting("m1")` | | [15,25] | `true` |
| `bookMeeting("m4", 20, 30)` | [20,30] | [15,25] | `"roomA"` |

For `m3`, the meeting in roomA ends at 20 and the meeting in roomB ends at 25. Both are `>= 20`, so both rooms are busy.

### Complexity

Let `R` be the number of rooms, `M` the number of meetings in one room and `N` the number of active meetings.

- Constructor: `O(R log R)` to sort the rooms.
- `bookMeeting`: `O(R log M)` in the worst case. It stops at the first free room.
- `cancelMeeting`: `O(log M)`.
- Space: `O(R + N)`.

### Code

```java
import java.util.*;

/**
 * Books meetings into a fixed list of rooms.
 * Always gives the lexicographically smallest room that is free.
 */
public class RoomBooking {

    // all rooms, sorted by id. The first free room we find is the answer.
    List<Room> rooms = new ArrayList<>();

    // meetingId -> active meeting. Lets cancelMeeting() find a meeting quickly.
    Map<String, Meeting> activeMeetings = new HashMap<>();

    public RoomBooking(List<String> roomIds) {
        // copy before sorting, so the caller's list is not changed
        List<String> sortedIds = new ArrayList<>(roomIds);
        Collections.sort(sortedIds);
        for (String roomId : sortedIds) {
            rooms.add(new Room(roomId));
        }
    }

    public String bookMeeting(String meetingId, int startTime, int endTime) {
        // one id cannot point to two active meetings
        if (activeMeetings.containsKey(meetingId)) return "";

        for (Room room : rooms) {
            if (room.isFree(startTime, endTime)) {
                room.addMeeting(startTime, endTime);
                activeMeetings.put(meetingId, new Meeting(meetingId, room, startTime, endTime));
                return room.id;
            }
        }
        return "";
    }

    public boolean cancelMeeting(String meetingId) {
        Meeting meeting = activeMeetings.remove(meetingId);
        if (meeting == null) return false;

        meeting.room.removeMeeting(meeting.startTime);
        return true;
    }
}

/**
 * A conference room and the meetings booked in it.
 * Meetings in a room never overlap.
 */
class Room {
    String id;

    // start times of all meetings in this room, in sorted order
    TreeSet<Integer> startTimes = new TreeSet<>();

    // start time -> end time of the same meeting
    Map<Integer, Integer> endTimeByStart = new HashMap<>();

    Room(String roomId) {
        this.id = roomId;
    }

    /**
     * Checks only one meeting: the one that starts last, at or before endTime.
     * If it ends before startTime, every earlier meeting ends even earlier,
     * so no meeting overlaps.
     */
    boolean isFree(int startTime, int endTime) {
        Integer lastStart = startTimes.floor(endTime);
        if (lastStart == null) return true;
        return endTimeByStart.get(lastStart) < startTime;
    }

    void addMeeting(int startTime, int endTime) {
        startTimes.add(startTime);
        endTimeByStart.put(startTime, endTime);
    }

    void removeMeeting(int startTime) {
        startTimes.remove(startTime);
        endTimeByStart.remove(startTime);
    }
}

/**
 * A booked meeting. It remembers its room, so cancelling is quick.
 */
class Meeting {
    String id;
    Room room;
    int startTime;
    int endTime;

    Meeting(String meetingId, Room room, int startTime, int endTime) {
        this.id = meetingId;
        this.room = room;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
```