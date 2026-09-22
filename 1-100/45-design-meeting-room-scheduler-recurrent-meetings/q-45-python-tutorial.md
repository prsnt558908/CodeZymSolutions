# Design a Meeting Room Scheduler for Recurrent Meetings

#### Problem Statement
[https://codezym.com/question/45-design-meeting-room-scheduler-recurrent-meetings](https://codezym.com/question/45-design-meeting-room-scheduler-recurrent-meetings)

The core idea is to turn each recurring booking into 20 ordinary meeting occurrences. Keep those occurrences in sorted lists for their room and employee, and use a dictionary to find a booking when it needs to be cancelled. Binary search helps us check room conflicts quickly. We check all 20 occurrences before saving any of them. No special design pattern is needed here: small `Booking` and `Occurrence` data classes, together with a scheduler that manages them, keep the solution simple and clear.

## 1. Understand What We Are Scheduling

A **booking** is the complete recurring series. An **occurrence** is one meeting within that series.

For example, a booking with `startTime = 10`, `duration = 3`, and `repeatDuration = 5` creates these occurrences:

```text
[10, 12], [15, 17], [20, 22], ..., [105, 107]
```

For occurrence number `i`, where `i` goes from `0` to `19`:

```text
start = startTime + i * repeatDuration
end   = start + duration - 1
```

There are exactly **20 occurrences, including the first meeting**. The meeting at `startTime + 20 * repeatDuration` is not part of this booking.

Keep these rules in mind:

- Both endpoints are included. `[10, 12]` overlaps `[12, 14]`, but it does not overlap `[13, 14]`.
- Reject a booking if `startTime < 0`, `duration <= 0`, or `duration >= repeatDuration`. These checks also reject a nonpositive repeat duration.
- If even one of the 20 occurrences conflicts with an existing meeting in the same room, reject the whole booking.
- Meetings in different rooms may overlap. The statement does not forbid an employee from having overlapping meetings in different rooms.
- Both listing methods return individual occurrences, formatted as `bookingId-startTime-endTime`.
- Cancellation removes the complete series from both the room and employee schedules.

Room IDs, employee IDs, and unique, nonblank booking IDs are guaranteed by the problem. The implementation relies on those guarantees.

## 2. Start with a Simple List

The simplest approach is to keep every occurrence in one list.

To book a room, generate the 20 proposed occurrences. For each one, scan the existing list and check meetings belonging to that room. Two closed intervals overlap when:

```text
existing_start <= new_end and new_start <= existing_end
```

If every proposed occurrence is free, add all 20 to the list. To list meetings for a room or employee, filter the list and sort the matches by start time. To cancel a booking, remove every occurrence with that booking ID.

This works, but each operation keeps searching through unrelated meetings. If there are `T` stored occurrences, checking a booking can take `O(20 * T)` time. Listing also repeats the sorting work.

We can improve those parts with ordinary dictionaries and lists. The final implementation below uses this improved approach.

## 3. Give Each Room and Employee a Sorted List

### `Booking`: information shared by the whole series

`Booking` stores the booking ID, employee ID, and room ID. These values are the same for all 20 occurrences, so they belong together.

We use Python's `@dataclass` because this class only needs to hold related data. We do not need to store the recurrence parameters after expanding the series because this problem has no operation that edits a recurrence rule.

### `Occurrence`: one actual meeting interval

`Occurrence` stores a reference to its `Booking`, a start time, and an end time. It is also a data class because it represents a small data record.

All 20 occurrences refer to the same booking object. This makes cancellation straightforward: remove the occurrences that refer to that object.

Python integers grow automatically when a calculation needs more space. A later start such as `startTime + 19 * repeatDuration` therefore remains correct even if it is larger than the usual 32-bit integer range.

### A dictionary for cancellation

`self._bookings_by_id` maps each booking ID to its `Booking` object. It finds the booking for cancellation in expected `O(1)` time. The object contains the room and employee IDs, so we know exactly which two schedules must be updated.

### Lists for room and employee schedules

`self._room_schedules` contains one sorted list per room. Because room IDs are consecutive integers starting at zero, `self._room_schedules[room_id]` directly finds a room's schedule.

`self._employee_schedules` works the same way for employees. Both kinds of schedules stay sorted by occurrence start time, so listing the first `n` meetings requires no new sorting.

The same occurrence object is referenced by both lists. We do not create a separate copy of the meeting for the employee schedule.

Python lists support fast access by index, which is what our binary search needs. We do not need a more complex sorted-map structure.

### Why no extra design pattern?

A Strategy pattern could make sense if the scheduler supported several interchangeable recurrence rules, such as daily meetings, weekdays only, and custom calendars. Here, the only rule is a fixed gap repeated exactly 20 times. Adding a strategy interface and several classes would not simplify the required behavior.

The scheduler therefore coordinates the operations directly, while the two data classes hold the related values.

## 4. Check Room Conflicts with Binary Search

A room's schedule has two useful properties:

1. Occurrences are sorted by start time.
2. Existing occurrences never overlap each other.

For a proposed interval `[start, end]`, binary search finds the first existing occurrence whose start time is **greater than or equal to** `start`. Call its position `index`.

Only two nearby occurrences need checking:

- The occurrence just before `index`, if one exists. It overlaps when its end time is `>= start`.
- The occurrence at `index`, if one exists. It overlaps when its start time is `<= end`.

Why can we ignore the others?

If the previous occurrence ends before the new meeting starts, all earlier occurrences must also end before it. Otherwise, those existing meetings would already overlap each other. If the next occurrence starts after the new meeting ends, all later occurrences also start too late to conflict.

For example, suppose a room contains:

```text
[10, 12], [15, 17], [20, 22]
```

For `[13, 14]`, binary search lands at `[15, 17]`. The previous meeting ends at `12`, and the next starts at `15`, so the interval is free.

For `[12, 14]`, the previous meeting ends at `12`, so there is a conflict. The equality matters because the endpoints are inclusive.

This check takes `O(log(M + 1))` time for a room containing `M` occurrences. It is used only for room schedules because employee schedules may contain overlapping meetings in different rooms.

## 5. Check Everything Before Changing Anything

Consider an existing booking with one-unit meetings starting at:

```text
20, 27, 34, 41, 48, 55, 62, 69, ...
```

A new request starts at:

```text
21, 29, 37, 45, 53, 61, 69, ...
```

The first occurrences do not conflict, but both bookings use the room at time `69`. The new booking must fail completely.

`bookRoom` therefore has two stages:

1. Generate each proposed occurrence into a temporary list and check it against the room's existing schedule. Return `False` immediately if any conflict is found.
2. After all 20 checks pass, merge the new occurrences into the room and employee schedules, and save the booking in the dictionary.

The proposed occurrences do not overlap each other because `duration < repeatDuration`. That is why we can check all of them against the unchanged room schedule.

### Merge instead of sorting again

The existing schedule is sorted. The 20 new occurrences are also sorted because we generate them in increasing time order.

Use two indexes, one for each list. Repeatedly take the occurrence with the earlier start time, then copy any remaining occurrences. This merges a schedule of size `M` with the new series in `O(M + 20)` time.

Employee occurrences can have equal start times when bookings use different rooms. The statement does not define a tie order. This implementation keeps the earlier accepted booking first when start times are equal and retains every occurrence.

## 6. Availability, Cancellation, and Listings

### Find available rooms

Return an empty list if `startTime > endTime`. Otherwise, visit room IDs from `0` upward and use the same conflict check for the entire requested interval. Add rooms with no conflict.

Visiting IDs in this order automatically produces ascending output. We do not reject a query merely because its start time is negative. The availability method only declares a reversed interval invalid.

### Cancel a booking

Remove the booking from `self._bookings_by_id`. If it was absent, return `False`.

Otherwise, rebuild its room and employee lists with list comprehensions that keep occurrences belonging to other bookings. List comprehensions preserve the order of the remaining entries.

### List the first `n` occurrences

The relevant schedule is already sorted. Read at most its first `n` entries and format each one as `bookingId-startTime-endTime`.

If the schedule has fewer than `n` entries, return all of them. For `n <= 0`, return an empty list.

## 7. Keep Each Operation Together

Two threads could otherwise both check an empty room before either one saves its booking.

The scheduler creates one `RLock`. Every public operation runs inside `with self._lock`, so a conflict check and its later updates form one complete operation. Availability and listing methods use the same lock and therefore cannot observe a half-finished change.

This addresses the concurrency expectation in the supplied background requirements. One scheduler-wide lock is simple and sufficient here. It does serialize operations for different rooms, which is a throughput tradeoff.

`RLock` is appropriate because it follows Python's context-manager style and releases the lock automatically when the method returns, including an early return.

## 8. Why the Solution Is Correct

**A successful booking cannot create a room conflict.** Every proposed occurrence is checked against the existing, sorted room schedule. The binary search checks the only two neighbors that could establish an overlap. The 20 proposed occurrences cannot overlap one another because the repeat gap is greater than their duration.

**A failed booking changes nothing.** All checks happen before the dictionary or either stored schedule is updated. Temporary occurrences from a rejected request never enter the scheduler.

**Both schedules remain sorted and complete.** Each successful booking merges its 20 occurrences into both sorted lists. The merge preserves all entries and their start-time order. Cancellation preserves the order of the entries that remain.

**Cancellation removes the whole series.** Every occurrence refers to its booking object. Removing all occurrences with that reference clears the series from both indexes, and removing the dictionary entry makes a second cancellation return `False`.

These properties hold initially for empty schedules and remain true after every operation.

## 9. Time and Space Complexity

Let `R` be the number of rooms, `U` the number of employees, and `B` the number of active recurring bookings. Let `K = 20`, `M` be the number of stored occurrences in the affected room, and `E` the number for the affected employee.

- **Constructor:** `O(R + U)` time to create the empty schedules.
- **Successful booking:** `O(K * log(M + 1) + M + E + K)` time. The searches check conflicts. The two merges build the updated lists. A rejected booking stops during validation and does not merge anything.
- **Availability:** `O(R * log(Mmax + 2))` time, where `Mmax` is the largest room schedule size. This includes visiting empty rooms.
- **Cancellation:** `O(M + E)` time for an existing booking. An unknown booking ID takes expected `O(1)` time.
- **Room listing:** `O(min(n, M))` time for positive `n`.
- **Employee listing:** `O(min(n, E))` time for positive `n`.

These bounds treat dictionary access as expected constant time and ignore the character cost of formatting output strings. Nonpositive listing limits return in constant time.

The stored data uses `O(R + U + K * B)` space. Each occurrence appears by reference in two lists, which changes only a constant factor. A successful booking temporarily needs `O(M + E + K)` additional space for its new occurrences and merged lists.

The sorted lists make conflict searches and listings efficient, while booking and cancellation still require linear list work. Binary search does not make list updates logarithmic.


## 11. Complete Python Code

The public class and method signatures match the supplied Python starter. The implementation uses data classes, dictionaries, lists, and one standard-library lock.

```python
from dataclasses import dataclass
from threading import RLock


@dataclass
class Booking:
    booking_id: str
    employee_id: int
    room_id: int


@dataclass
class Occurrence:
    booking: Booking
    start_time: int
    end_time: int


class MeetingRoomScheduler:
    OCCURRENCE_COUNT = 20

    def __init__(self, roomsCount: int, employeesCount: int):
        # One dictionary entry represents an entire recurring series.
        self._bookings_by_id: dict[str, Booking] = {}

        # The outer lists are indexed by room ID or employee ID.
        # Every inner list stays sorted by occurrence start time.
        self._room_schedules: list[list[Occurrence]] = [
            [] for _ in range(roomsCount)
        ]
        self._employee_schedules: list[list[Occurrence]] = [
            [] for _ in range(employeesCount)
        ]

        self._lock = RLock()

    def bookRoom(
        self,
        bookingId: str,
        employeeId: int,
        roomId: int,
        startTime: int,
        duration: int,
        repeatDuration: int,
    ) -> bool:
        with self._lock:
            if startTime < 0 or duration <= 0 or duration >= repeatDuration:
                return False

            booking = Booking(bookingId, employeeId, roomId)
            room_schedule = self._room_schedules[roomId]
            new_occurrences: list[Occurrence] = []

            # Validate all occurrences before changing stored state.
            for i in range(self.OCCURRENCE_COUNT):
                occurrence_start = startTime + i * repeatDuration
                occurrence_end = occurrence_start + duration - 1

                if self._has_conflict(
                    room_schedule, occurrence_start, occurrence_end
                ):
                    return False

                new_occurrences.append(
                    Occurrence(booking, occurrence_start, occurrence_end)
                )

            updated_room = self._merge_by_start(
                room_schedule, new_occurrences
            )
            updated_employee = self._merge_by_start(
                self._employee_schedules[employeeId], new_occurrences
            )

            self._room_schedules[roomId] = updated_room
            self._employee_schedules[employeeId] = updated_employee
            self._bookings_by_id[bookingId] = booking
            return True

    def getAvailableRooms(self, startTime: int, endTime: int) -> list[int]:
        with self._lock:
            if startTime > endTime:
                return []

            available: list[int] = []

            for room_id, schedule in enumerate(self._room_schedules):
                if not self._has_conflict(schedule, startTime, endTime):
                    available.append(room_id)

            return available

    def cancelBooking(self, bookingId: str) -> bool:
        with self._lock:
            booking = self._bookings_by_id.pop(bookingId, None)

            if booking is None:
                return False

            # Remove all 20 occurrences from both stored indexes.
            room_schedule = self._room_schedules[booking.room_id]
            self._room_schedules[booking.room_id] = [
                occurrence
                for occurrence in room_schedule
                if occurrence.booking is not booking
            ]

            employee_schedule = self._employee_schedules[booking.employee_id]
            self._employee_schedules[booking.employee_id] = [
                occurrence
                for occurrence in employee_schedule
                if occurrence.booking is not booking
            ]

            return True

    def listBookingsForRoom(self, roomId: int, n: int) -> list[str]:
        with self._lock:
            return self._first_n(self._room_schedules[roomId], n)

    def listBookingsForEmployee(self, employeeId: int, n: int) -> list[str]:
        with self._lock:
            return self._first_n(self._employee_schedules[employeeId], n)

    def _has_conflict(
        self,
        schedule: list[Occurrence],
        start: int,
        end: int,
    ) -> bool:
        """Check a sorted room schedule using inclusive endpoints."""
        low = 0
        high = len(schedule)

        # Find the first occurrence with start_time >= start.
        while low < high:
            mid = low + (high - low) // 2

            if schedule[mid].start_time < start:
                low = mid + 1
            else:
                high = mid

        index = low

        if index > 0 and schedule[index - 1].end_time >= start:
            return True

        return index < len(schedule) and schedule[index].start_time <= end

    def _merge_by_start(
        self,
        existing: list[Occurrence],
        added: list[Occurrence],
    ) -> list[Occurrence]:
        """Merge sorted lists, keeping existing entries first on ties."""
        merged: list[Occurrence] = []
        i = 0
        j = 0

        while i < len(existing) and j < len(added):
            if existing[i].start_time <= added[j].start_time:
                merged.append(existing[i])
                i += 1
            else:
                merged.append(added[j])
                j += 1

        merged.extend(existing[i:])
        merged.extend(added[j:])
        return merged

    def _first_n(
        self,
        schedule: list[Occurrence],
        n: int,
    ) -> list[str]:
        if n <= 0:
            return []

        result: list[str] = []

        for occurrence in schedule[:n]:
            result.append(
                f"{occurrence.booking.booking_id}-"
                f"{occurrence.start_time}-{occurrence.end_time}"
            )

        return result
```
