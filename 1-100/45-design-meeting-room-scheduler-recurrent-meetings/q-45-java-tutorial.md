# Design a Meeting Room Scheduler for Recurrent Meetings

#### Problem Statement
[https://codezym.com/question/45-design-meeting-room-scheduler-recurrent-meetings](https://codezym.com/question/45-design-meeting-room-scheduler-recurrent-meetings)

The core idea is to turn each recurring booking into 20 ordinary meeting occurrences. Keep those occurrences in sorted lists for their room and employee, and use a map to find a booking when it needs to be cancelled. Binary search helps us check room conflicts quickly. We check all 20 occurrences before saving any of them. No special design pattern is needed here: a small `Booking` class, an `Occurrence` class, and a scheduler with clear responsibilities are enough.

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
existingStart <= newEnd && newStart <= existingEnd
```

If every proposed occurrence is free, add all 20 to the list. To list meetings for a room or employee, filter the list and sort the matches by start time. To cancel a booking, remove every occurrence with that booking ID.

This works, but each operation keeps searching through unrelated meetings. If there are `T` stored occurrences, checking a booking can take `O(20 * T)` time. Listing also repeats the sorting work.

We can improve those parts with ordinary maps and lists. The final implementation below uses this improved approach.

## 3. Give Each Room and Employee a Sorted List

### `Booking`: information shared by the whole series

`Booking` stores the booking ID, employee ID, and room ID. These values are the same for all 20 occurrences, so they belong together.

We do not need to store the recurrence parameters after expanding the series because this problem has no operation that edits a recurrence rule.

### `Occurrence`: one actual meeting interval

`Occurrence` stores a reference to its `Booking`, a start time, and an end time.

All 20 occurrences refer to the same booking object. This makes cancellation straightforward: remove the occurrences belonging to that object.

Occurrence times use `long`, even though the public methods accept `int`. A later start time such as `startTime + 19 * repeatDuration` can exceed the `int` range. We convert to `long` **before** multiplication and addition.

### A map for cancellation

`Map<String, Booking> bookingsById` finds the booking associated with an ID in expected `O(1)` time. Its room and employee IDs tell us exactly which two schedules need updating.

### Lists for room and employee schedules

`List<List<Occurrence>> roomSchedules` stores one sorted list per room. Because room IDs are consecutive integers starting at zero, `roomSchedules.get(roomId)` directly finds a room's schedule.

`employeeSchedules` works the same way for employees. Both schedules stay sorted by occurrence start time, so listing the first `n` meetings requires no sorting.

The same occurrence object is referenced by both lists. We do not create a separate copy of the meeting for the employee schedule.

We use `ArrayList` for these lists because binary search needs fast access by index. No `TreeMap` or `NavigableMap` is required.

### Why no extra design pattern?

A Strategy pattern could make sense if the scheduler supported several interchangeable recurrence rules, such as daily meetings, weekdays only, and custom calendars. Here, the only rule is a fixed gap repeated exactly 20 times. A strategy interface would add classes without simplifying the required behavior.

The scheduler therefore coordinates the operations directly, while `Booking` and `Occurrence` hold the data.

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

This check takes `O(log(M + 1))` time for a room containing `M` occurrences. It is used only for room schedules: employee schedules may contain overlapping meetings.

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

1. Generate each proposed occurrence into a temporary list and check it against the room's existing schedule. Return `false` immediately if any conflict is found.
2. After all 20 checks pass, merge the new occurrences into the room and employee schedules, and save the booking in the map.

The proposed occurrences do not overlap each other because `duration < repeatDuration`. That is why we can check them all against the unchanged room schedule.

### Merge instead of sorting again

The existing schedule is sorted. The 20 new occurrences are also sorted because we generate them in increasing time order.

Use two indexes, one for each list. Repeatedly take the occurrence with the earlier start time, then copy any remaining occurrences. This merges a schedule of size `M` with the new series in `O(M + 20)` time.

Employee occurrences can have equal start times when bookings use different rooms. The statement does not define a tie order. This implementation keeps the earlier accepted booking first when start times are equal. It retains every occurrence.

## 6. Availability, Cancellation, and Listings

### Find available rooms

Return an empty list if `startTime > endTime`. Otherwise, visit room IDs from `0` upward and use the same conflict check for the entire requested interval. Add the rooms with no conflict.

Visiting IDs in this order automatically produces ascending output. We do not reject a query merely because its start time is negative. The availability method only declares a reversed interval invalid.

### Cancel a booking

Remove the booking from `bookingsById`. If it was absent, return `false`.

Otherwise, remove its occurrences from its room's list and its employee's list. `ArrayList.removeIf` does this with a linear scan and preserves the order of the remaining entries.

### List the first `n` occurrences

The relevant schedule is already sorted. Read at most its first `n` entries and format each one as `bookingId-startTime-endTime`.

If the schedule has fewer than `n` entries, return all of them. For `n <= 0`, return an empty list.

## 7. Keep Each Operation Together

Two threads could otherwise both check an empty room before either saves its booking.

All public operations use Java's `synchronized` keyword, so only one operation can access a particular scheduler instance at a time. The lock covers both the conflict checks and the updates. Listing and availability methods use the same lock so they see complete changes.

This also addresses the concurrency expectation in the supplied background requirements. A scheduler-wide lock is simple and sufficient for this implementation. It does serialize operations across different rooms, which is a throughput tradeoff.

## 8. Why the Solution Is Correct

**A successful booking cannot create a room conflict.** Every proposed occurrence is checked against the existing, sorted room schedule. The binary search checks the only two neighbors that could establish an overlap. The 20 proposed occurrences cannot overlap one another because the repeat gap is greater than their duration.

**A failed booking changes nothing.** All checks happen before the map or either stored schedule is updated. Temporary occurrences from a rejected request never enter the scheduler.

**Both schedules remain sorted and complete.** Each successful booking merges its 20 occurrences into both sorted lists. The merge preserves all entries and their start-time order. Removal during cancellation preserves the order of the entries that remain.

**Cancellation removes the whole series.** Every occurrence refers to its booking object. Removing all occurrences with that reference clears the series from both indexes, and removing the map entry makes a second cancellation return `false`.

These properties hold initially for empty schedules and remain true after every operation.

## 9. Time and Space Complexity

Let `R` be the number of rooms, `U` the number of employees, and `B` the number of active recurring bookings. Let `K = 20`, `M` be the number of stored occurrences in the affected room, and `E` the number for the affected employee.

- **Constructor:** `O(R + U)` time to create the empty schedules.
- **Successful booking:** `O(K * log(M + 1) + M + E + K)` time. The searches check conflicts. The two merges build the updated lists. A rejected booking stops during validation and does not merge anything.
- **Availability:** `O(R * log(Mmax + 2))` time, where `Mmax` is the largest room schedule size. This includes visiting empty rooms.
- **Cancellation:** `O(M + E)` time for an existing booking. An unknown booking ID takes expected `O(1)` time.
- **Room listing:** `O(min(n, M))` time for positive `n`.
- **Employee listing:** `O(min(n, E))` time for positive `n`.

These bounds treat map access as expected constant time and ignore the character cost of formatting output strings. Nonpositive listing limits return in constant time.

The stored data uses `O(R + U + K * B)` space. Each occurrence appears by reference in two lists, which changes only a constant factor. A successful booking temporarily needs `O(M + E + K)` additional space for its new occurrences and merged lists.

The tradeoff is deliberate: sorted `ArrayList` schedules make conflict searches and listings efficient, while booking and cancellation still require linear list work. Binary search does not make list updates logarithmic.


## 11. Complete Java Code

The public class and method signatures match the supplied Java starter. The implementation uses Java 8-compatible syntax and standard collections.

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MeetingRoomScheduler {

    private static final int OCCURRENCE_COUNT = 20;

    // One map entry represents an entire recurring series.
    private final Map<String, Booking> bookingsById = new HashMap<>();

    // The outer list is indexed by room ID or employee ID.
    // Each inner list stays sorted by occurrence start time.
    private final List<List<Occurrence>> roomSchedules = new ArrayList<>();
    private final List<List<Occurrence>> employeeSchedules = new ArrayList<>();

    private static final class Booking {
        final String id;
        final int employeeId;
        final int roomId;

        Booking(String id, int employeeId, int roomId) {
            this.id = id;
            this.employeeId = employeeId;
            this.roomId = roomId;
        }
    }

    private static final class Occurrence {
        final Booking booking;
        final long startTime;
        final long endTime;

        Occurrence(Booking booking, long startTime, long endTime) {
            this.booking = booking;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    public MeetingRoomScheduler(int roomsCount, int employeesCount) {
        for (int roomId = 0; roomId < roomsCount; roomId++) {
            roomSchedules.add(new ArrayList<>());
        }

        for (int employeeId = 0; employeeId < employeesCount; employeeId++) {
            employeeSchedules.add(new ArrayList<>());
        }
    }

    public synchronized boolean bookRoom(
            String bookingId,
            int employeeId,
            int roomId,
            int startTime,
            int duration,
            int repeatDuration) {

        if (startTime < 0 || duration <= 0 || duration >= repeatDuration) {
            return false;
        }

        Booking booking = new Booking(bookingId, employeeId, roomId);
        List<Occurrence> roomSchedule = roomSchedules.get(roomId);
        List<Occurrence> newOccurrences = new ArrayList<>(OCCURRENCE_COUNT);

        // Validate all occurrences before changing any stored collection.
        for (int i = 0; i < OCCURRENCE_COUNT; i++) {
            long occurrenceStart = (long) startTime + (long) i * repeatDuration;
            long occurrenceEnd = occurrenceStart + duration - 1L;

            if (hasConflict(roomSchedule, occurrenceStart, occurrenceEnd)) {
                return false;
            }

            newOccurrences.add(
                    new Occurrence(booking, occurrenceStart, occurrenceEnd));
        }

        List<Occurrence> updatedRoom = mergeByStart(roomSchedule, newOccurrences);
        List<Occurrence> updatedEmployee = mergeByStart(
                employeeSchedules.get(employeeId), newOccurrences);

        roomSchedules.set(roomId, updatedRoom);
        employeeSchedules.set(employeeId, updatedEmployee);
        bookingsById.put(bookingId, booking);
        return true;
    }

    public synchronized List<Integer> getAvailableRooms(int startTime, int endTime) {
        List<Integer> available = new ArrayList<>();

        if (startTime > endTime) {
            return available;
        }

        for (int roomId = 0; roomId < roomSchedules.size(); roomId++) {
            if (!hasConflict(roomSchedules.get(roomId), startTime, endTime)) {
                available.add(roomId);
            }
        }

        return available;
    }

    public synchronized boolean cancelBooking(String bookingId) {
        Booking booking = bookingsById.remove(bookingId);

        if (booking == null) {
            return false;
        }

        // Both indexes reference occurrences belonging to this exact booking.
        roomSchedules.get(booking.roomId)
                .removeIf(occurrence -> occurrence.booking == booking);
        employeeSchedules.get(booking.employeeId)
                .removeIf(occurrence -> occurrence.booking == booking);

        return true;
    }

    public synchronized List<String> listBookingsForRoom(int roomId, int n) {
        return firstN(roomSchedules.get(roomId), n);
    }

    public synchronized List<String> listBookingsForEmployee(int employeeId, int n) {
        return firstN(employeeSchedules.get(employeeId), n);
    }

    /**
     * Checks a sorted room schedule whose existing intervals never overlap.
     * Both endpoints of the requested interval are inclusive.
     */
    private boolean hasConflict(List<Occurrence> schedule, long start, long end) {
        int low = 0;
        int high = schedule.size();

        // Find the first occurrence with startTime >= start.
        while (low < high) {
            int mid = low + (high - low) / 2;

            if (schedule.get(mid).startTime < start) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }

        int index = low;

        if (index > 0 && schedule.get(index - 1).endTime >= start) {
            return true;
        }

        return index < schedule.size() && schedule.get(index).startTime <= end;
    }

    /** Merges two sorted lists, keeping existing entries first on equal starts. */
    private List<Occurrence> mergeByStart(
            List<Occurrence> existing, List<Occurrence> added) {

        List<Occurrence> merged = new ArrayList<>(existing.size() + added.size());
        int i = 0;
        int j = 0;

        while (i < existing.size() && j < added.size()) {
            if (existing.get(i).startTime <= added.get(j).startTime) {
                merged.add(existing.get(i++));
            } else {
                merged.add(added.get(j++));
            }
        }

        while (i < existing.size()) {
            merged.add(existing.get(i++));
        }

        while (j < added.size()) {
            merged.add(added.get(j++));
        }

        return merged;
    }

    private List<String> firstN(List<Occurrence> schedule, int n) {
        List<String> result = new ArrayList<>();

        if (n <= 0) {
            return result;
        }

        int count = Math.min(n, schedule.size());

        for (int i = 0; i < count; i++) {
            Occurrence occurrence = schedule.get(i);
            result.add(occurrence.booking.id + "-"
                    + occurrence.startTime + "-" + occurrence.endTime);
        }

        return result;
    }
}
```
