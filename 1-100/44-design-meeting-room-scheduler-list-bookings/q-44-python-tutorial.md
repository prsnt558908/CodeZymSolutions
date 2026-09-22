# Design a Meeting Room Scheduler in Python

#### Problem Statement

[https://codezym.com/question/44-design-meeting-room-scheduler-list-bookings](https://codezym.com/question/44-design-meeting-room-scheduler-list-bookings)

The core idea is to keep each room's bookings in a sorted list, find bookings by ID through a map, and group booking IDs by employee through sets. Binary search helps us check just two neighboring bookings when testing a room's availability. No special design pattern is needed: a small frozen `Booking` dataclass holds the data, and `MeetingRoomScheduler` handles the rules. We also use one shared lock so that two overlapping requests cannot book the same room at the same time.

## Understand the Rules First

The scheduler manages a fixed number of rooms and employees. Room IDs run from `0` to `roomsCount - 1`, and employee IDs run from `0` to `employeesCount - 1`.

A booking reserves one room for one employee. We must support booking, cancellation, availability checks, and listing bookings by room or employee.

Some details are easy to miss:

- **Both endpoints are included.** `[10, 20]` and `[20, 30]` overlap because both include time `20`.
- **A single time point is valid.** `[60, 60]` reserves the room at time `60`.
- **Conflicts are checked only within the same room.** One employee may have overlapping bookings in different rooms. The problem does not forbid that.
- **Booking validation:** `bookRoom` returns `False` when `startTime < 0` or `startTime > endTime`.
- **Availability validation:** `getAvailableRooms` returns an empty list when `startTime > endTime`. Its stated rules do not reject a negative start, so we apply only this check to availability queries.
- **Ordering matters.** Available room IDs must be ascending. Booking IDs must be ordered by start time, then by booking ID in ascending string order.
- **Failed operations must leave the records unchanged.** A rejected booking must not appear in either listing.

Booking IDs are guaranteed to be unique and non-blank, and the supplied room and employee IDs are valid. We use these guarantees directly.

## Start with One List of All Bookings

A straightforward solution stores every active booking in one list.

To book a room, scan the list and check whether any booking in that room overlaps the requested interval. To cancel, scan for the booking ID. To list bookings, filter the list and sort the matching entries.

This works, but a request for one room examines bookings belonging to every other room. Cancellation also searches through unrelated bookings. A straightforward availability implementation that repeats this scan for every room takes `O(R × B)` time, where `R` is the number of rooms and `B` is the number of active bookings. Even a single-pass version still examines all active bookings.

We can improve these operations by organizing the same booking records in a few simple collections.

## Organize Bookings for the Questions We Ask

### 1. A map to find a booking by ID

`dict[str, Booking] bookings_by_id` stores each active booking under its ID.

Cancellation starts with this map. Once we find the booking, we immediately know its room, employee, and start time. Looking it up takes expected `O(1)` time. Removing it from all collections has additional work explained below.

### 2. A sorted list for each room

`list[list[Booking]] bookings_by_room` contains one Python list per room. Each inner list stays sorted by start time.

Room IDs are consecutive integers, so `bookings_by_room[room_id]` directly selects the correct list.

Because accepted bookings in a room never overlap, two of them cannot have the same start time. Therefore, sorting a room's bookings by start time already satisfies the required order by start time and then booking ID.

Keeping this list sorted also lets us use binary search for availability checks. We do not need a `TreeMap` or a custom tree.

### 3. A set of booking IDs for each employee

`list[set[str]] booking_ids_by_employee` contains one Python set per employee. A set lets us add or remove an employee's booking ID in expected `O(1)` time. We still sort the final listing explicitly.

When the employee's bookings are requested, we fetch their records from `bookings_by_id`, sort them by start time and booking ID, and return the IDs.

For example, if an employee has `b2` and `b10` starting at the same time in different rooms, the listing returns `b10` before `b2`. Booking IDs use string order, not the numeric value inside the ID.

The room lists and the dictionary refer to the same `Booking` objects. The employee sets store their IDs. All three collections describe the same active bookings.

## Why Do We Need a Booking Class?

A `Booking` groups the five values that belong together: booking ID, employee ID, room ID, start time, and end time.

The dataclass is frozen, so an accepted booking cannot later change its start time and silently break the room list's ordering.

The scheduler owns the collections and coordinates their updates. Rooms and employees only need IDs in this problem, so separate classes with no additional behavior would not help the implementation.

No named design pattern is necessary. For example, Strategy would be useful if the system had several interchangeable booking rules. Here there is one fixed overlap rule, so introducing a strategy interface and extra implementations would add work without improving the required behavior.

## Detect an Overlap Using Two Neighbors

For two closed intervals `[a, b]` and `[c, d]`, the general overlap condition is:

`a <= d and c <= b`

Our room list is sorted and already contains only non-overlapping bookings. We can use that fact to avoid scanning the whole list.

For a new interval `[startTime, endTime]`, binary search finds the first booking whose start time is **greater than or equal to** `startTime`. Call its position `index`.

Only two existing bookings need to be checked:

1. **The booking just before `index`:** it conflicts if its `endTime >= startTime`.
2. **The booking at `index`:** it conflicts if its `startTime <= endTime`.

Suppose a room contains `[10, 20]` and `[30, 40]`.

For `[21, 29]`, binary search gives the position between them. The previous booking ends before `21`, and the next booking starts after `29`, so the request succeeds.

For `[20, 29]`, the previous booking ends exactly at the requested start. That is an overlap, so the request fails.

For `[21, 30]`, the next booking starts exactly at the requested end. This also fails.

### Why are these two checks enough?

Every booking before the previous one must end before that previous booking starts, because existing bookings do not overlap. If the previous booking ends before the requested start, all earlier bookings are also safe.

Every booking after the next one starts even later. If the next booking starts after the requested end, all later bookings are also safe.

This argument also covers a request that contains several existing bookings: the first booking starting inside the requested interval already reveals the conflict.

Finding the position takes `O(log(K + 1))` comparisons for a room with `K` bookings. Inserting into a Python list can still take `O(K)` time because later elements may need to move. Binary search speeds up the search. It does not make insertion logarithmic.

## How Each Operation Works

### bookRoom

First reject an invalid booking interval. Then select the room's list, find the insertion position, and check the two neighbors.

If a conflict exists, return `False` immediately. Otherwise, create one `Booking`, insert it at the correct room-list position, put it in the ID map, and add its ID to the employee's set.

All checks happen before any collection is changed.

### getAvailableRooms

Return an empty list if the interval is reversed. Otherwise, visit rooms from ID `0` upward and run the same two-neighbor check on each room.

Add a room ID only if there is no overlap. Visiting rooms in ascending order means the result is already sorted.

The result describes availability at the time of this call. A later `bookRoom` call checks again because another request may have booked the room in between.

### cancelBooking

Remove the booking from `bookings_by_id`. If it was absent, return `False`.

Otherwise, use its start time to find its position in the room's list and remove it. This position is unambiguous because active bookings in one room have distinct start times. Finally, remove its ID from the employee's set and return `True`.

Cancellation updates every place that refers to the booking, so both listings and availability immediately reflect the removal.

### listBookingsForRoom

The room's list is already in the required order. Copy its booking IDs into a new list and return it.

### listBookingsForEmployee

Collect that employee's booking records, sort them by start time and then booking ID, and copy their IDs into a new list.

All returned lists are fresh lists. Changing a returned list cannot change the scheduler's internal records.


## Why the Solution Stays Correct

Initially, every collection is empty, so all room lists are sorted and contain no overlaps.

A successful booking is inserted at its sorted position only after both possible neighboring conflicts have been ruled out. The room therefore remains sorted and free of overlaps. The booking is then recorded in the map and the employee's set under the same lock.

A rejected booking changes nothing. Cancellation removes the same booking from all three collections and cannot introduce an overlap among the remaining bookings.

These properties make the availability checks valid after every operation. Room listings follow the maintained order, and employee listings explicitly apply the required two-part ordering.

## Time and Space Complexity

Let `R` be the number of rooms, `E` the number of employees, `B` the total number of active bookings, `K` the number of bookings in the affected room, and `M` the number belonging to the affected employee. Dictionary and set operations below use expected time. List growth uses amortized time. Lock waiting time is excluded.

- **Construction:** `O(R + E)` time to create the room lists and employee sets.
- **bookRoom:** an invalid interval takes `O(1)`. A request rejected for overlap takes `O(log(K + 2))`. A successful request takes `O(K + 1)` because list elements may need to shift.
- **getAvailableRooms:** `O(R + sum(log(Kr + 1)))`, where `Kr` is the number of bookings in room `r`. Equivalently, it is bounded by `O(R log(Kmax + 2))`, where `Kmax` is the largest room-list size. The result needs up to `O(R)` space.
- **cancelBooking:** expected `O(1)` if the ID is absent. `O(K + 1)` if present because removing an entry may shift the remaining room-list elements.
- **listBookingsForRoom:** `O(K + 1)` time and `O(K)` output space.
- **listBookingsForEmployee:** `O(M log(M + 2) + 1)` time and `O(M)` temporary/output space.

The collections contain `O(R + E + B)` live entries. Each booking is represented in a constant number of places. Python lists, dictionaries, and sets can keep allocated capacity after deletions, so actual memory can depend on earlier peak sizes rather than only the current number of active bookings.

The main tradeoff is deliberate: sorted lists make availability checks fast with familiar collections, while successful booking and cancellation may still take linear time within one room.


## Complete Python Solution

```python
from dataclasses import dataclass
import threading
from typing import Dict, List, Set


@dataclass(frozen=True)
class Booking:
    """Immutable information about one accepted room booking."""

    booking_id: str
    employee_id: int
    room_id: int
    start_time: int
    end_time: int


class MeetingRoomScheduler:
    def __init__(self, roomsCount, employeesCount):
        self._bookings_by_id: Dict[str, Booking] = {}
        self._bookings_by_room: List[List[Booking]] = [
            [] for _ in range(roomsCount)
        ]
        self._booking_ids_by_employee: List[Set[str]] = [
            set() for _ in range(employeesCount)
        ]
        self._lock = threading.RLock()

    def bookRoom(self, bookingId, employeeId, roomId, startTime, endTime):
        with self._lock:
            if startTime < 0 or startTime > endTime:
                return False

            room_bookings = self._bookings_by_room[roomId]
            index = self._find_insertion_index(room_bookings, startTime)

            if self._has_overlap(room_bookings, index, startTime, endTime):
                return False

            # Update every collection only after the request is accepted.
            booking = Booking(
                booking_id=bookingId,
                employee_id=employeeId,
                room_id=roomId,
                start_time=startTime,
                end_time=endTime,
            )
            room_bookings.insert(index, booking)
            self._bookings_by_id[bookingId] = booking
            self._booking_ids_by_employee[employeeId].add(bookingId)
            return True

    def getAvailableRooms(self, startTime, endTime):
        with self._lock:
            available_rooms = []

            # The query contract rejects reversed ranges, not negative starts.
            if startTime > endTime:
                return available_rooms

            for room_id, room_bookings in enumerate(self._bookings_by_room):
                index = self._find_insertion_index(room_bookings, startTime)
                if not self._has_overlap(
                    room_bookings, index, startTime, endTime
                ):
                    available_rooms.append(room_id)

            return available_rooms

    def cancelBooking(self, bookingId):
        with self._lock:
            booking = self._bookings_by_id.pop(bookingId, None)
            if booking is None:
                return False

            room_bookings = self._bookings_by_room[booking.room_id]
            # Accepted bookings in one room have distinct start times.
            index = self._find_insertion_index(
                room_bookings, booking.start_time
            )
            del room_bookings[index]
            self._booking_ids_by_employee[booking.employee_id].remove(bookingId)
            return True

    def listBookingsForRoom(self, roomId):
        with self._lock:
            return [
                booking.booking_id
                for booking in self._bookings_by_room[roomId]
            ]

    def listBookingsForEmployee(self, employeeId):
        with self._lock:
            employee_bookings = [
                self._bookings_by_id[booking_id]
                for booking_id in self._booking_ids_by_employee[employeeId]
            ]
            employee_bookings.sort(
                key=lambda booking: (booking.start_time, booking.booking_id)
            )
            return [booking.booking_id for booking in employee_bookings]

    def _find_insertion_index(self, room_bookings, start_time):
        """Return the first position whose start time is at least start_time."""
        left = 0
        right = len(room_bookings)

        while left < right:
            middle = left + (right - left) // 2
            if room_bookings[middle].start_time < start_time:
                left = middle + 1
            else:
                right = middle

        return left

    def _has_overlap(self, room_bookings, index, start_time, end_time):
        """Existing room bookings are sorted and never overlap one another."""
        # Closed intervals conflict even when their endpoints are equal.
        if index > 0 and room_bookings[index - 1].end_time >= start_time:
            return True

        return (
            index < len(room_bookings)
            and room_bookings[index].start_time <= end_time
        )
```
