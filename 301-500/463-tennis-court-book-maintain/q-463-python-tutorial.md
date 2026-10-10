# Assign Tennis Court Bookings With Maintenance in Python


#### Problem Statement
[https://codezym.com/question/463-tennis-court-book-maintain](https://codezym.com/question/463-tennis-court-book-maintain)


Sort bookings by start time and booking id. For each booking, release every court that is ready, then choose the smallest available court number. Two priority queues keep these steps fast, while a counter on each court tells us when to add maintenance time.


## Start with a simple solution


Parse each booking into numeric values and sort by `(startTime, bookingId)`. The id comparison must be numeric, so booking 2 comes before booking 10 when they start together.


Keep courts in a list. For each booking, scan from court 0 upward and pick the first court whose next available time is at most the booking start time. If none is ready, add a new court.


This follows the assignment rule, but the scans can take `O(n²)` time. When all bookings overlap, every new booking checks every existing court. That is too much work for 100,000 bookings.


## Replace the scans with two priority queues


A min-heap keeps its smallest value at the front. We need two heaps because the earliest available time and the smallest court number answer different questions.


- `busy_courts` keeps occupied courts ordered by their next available time. That time includes maintenance when required.
- `free_courts` keeps available court numbers ordered from smallest to largest.
- `courts` stores courts in number order. Its index is the court number, and each court stores its usage counter and assigned booking ids.


`Booking` and `Court` are dataclasses that group the values belonging to a booking or a court. Python uses ordinary lists with `heapq` for the heaps. Busy entries are `(free_at, court_number)` pairs, while available entries are court numbers. Each court collects booking ids in a list, then joins them once. `default_factory=list` gives every court its own list.


For each sorted booking:


1. Move **every** court with `free_at <= startTime` from `busy_courts` into `free_courts`.
2. Take the smallest number from `free_courts`. If it is empty, create court `len(courts)`.
3. Record the booking, update the court's next available time, and put it into `busy_courts`.


Releasing every ready court matters. Suppose court 1 became free at time 5 and court 0 became free at time 7. A booking starting at time 8 must use court 0, even though court 1 became free earlier.


## Add maintenance to the available time


After assigning a booking, increase `bookings_since_maintenance` and set `free_at` to its finish time. When the counter reaches `durability`, add `maintenanceTime` to that finish time and reset the counter to zero.


Maintenance starts when the triggering booking finishes. A long idle gap does not reset the counter, and maintenance is added only after a complete batch of `durability` bookings.


Both public methods use `assign_bookings`. The method without maintenance passes a maintenance time of 0 and a durability of 1, so every court becomes available exactly when its booking finishes.


## Walk through an example


Use bookings `["21,0,10", "22,10,20", "23,20,30", "24,30,40", "25,35,45"]`, with maintenance time 15 and durability 2.


- Court 0 hosts bookings 21 and 22. Its second booking finishes at 20, so maintenance keeps it closed until 35.
- Booking 23 starts at 20 and opens court 1. Booking 24 starts at 30 and reuses that court. Court 1 then stays closed until 55.
- Booking 25 starts at 35, exactly when court 0 becomes available again. It goes to court 0.


The result is `["21,22,25", "23,24"]`.


## Why the solution works


Before each assignment, releasing all ready courts makes the available heap contain exactly the existing courts that can host the booking. Its smallest number is therefore the court required by the rule. If the heap is empty, every existing court is occupied or under maintenance, so the rule requires a new court.


Updating the next available time after every assignment keeps this reasoning true for the next booking. Using `<=` also allows a booking to start exactly when a previous booking or maintenance ends. Sorting first gives the required processing order, and the court list gives the required output order.


Without maintenance, opening a new court means the current booking overlaps an active booking on every existing court. That many courts are unavoidable, so the assignment uses the minimum possible number. With maintenance, the solution follows the specified assignment rule, which is not a guarantee of the minimum possible court count.


## Complexity


For `n` bookings, sorting takes `O(n log n)` time. Each booking creates one busy-heap entry, and each entry is removed at most once. The available-heap operations have the same bound, so total time is `O(n log n)`.


Space is `O(n)` for the parsed bookings, courts, heaps, and output. Every method call creates fresh scheduling data, so repeated calls on the same object are independent.


## Python Code

```python
from dataclasses import dataclass, field
from heapq import heappop, heappush
from typing import List


@dataclass
class Booking:
    id: int
    start_time: int
    finish_time: int


@dataclass
class Court:
    number: int
    free_at: int = 0
    bookings_since_maintenance: int = 0
    booking_ids: List[str] = field(default_factory=list)


class AssignTennisCourts:
    def __init__(self):
        pass

    def assignCourts(self, bookings: List[str]) -> List[str]:
        return self.assign_bookings(bookings, 0, 1)

    def assignCourtsWithMaintenance(
        self, bookings: List[str], maintenanceTime: int, durability: int
    ) -> List[str]:
        return self.assign_bookings(bookings, maintenanceTime, durability)

    def assign_bookings(
        self, bookings: List[str], maintenance_time: int, durability: int
    ) -> List[str]:
        """Shared scheduler. All court data belongs to this call."""
        sorted_bookings = []
        for value in bookings:
            parts = value.split(",")
            sorted_bookings.append(
                Booking(int(parts[0]), int(parts[1]), int(parts[2]))
            )

        # Compare numeric ids when start times are equal.
        sorted_bookings.sort(key=lambda booking: (booking.start_time, booking.id))

        courts = []
        busy_courts = []
        free_courts = []

        for booking in sorted_bookings:
            # Release every ready court before choosing the smallest number.
            while busy_courts and busy_courts[0][0] <= booking.start_time:
                _, court_number = heappop(busy_courts)
                heappush(free_courts, court_number)

            if not free_courts:
                court = Court(len(courts))
                courts.append(court)
            else:
                court = courts[heappop(free_courts)]

            court.booking_ids.append(str(booking.id))

            # Maintenance starts when the booking finishes.
            court.free_at = booking.finish_time
            court.bookings_since_maintenance += 1
            if court.bookings_since_maintenance == durability:
                court.free_at += maintenance_time
                court.bookings_since_maintenance = 0

            heappush(busy_courts, (court.free_at, court.number))

        return [",".join(court.booking_ids) for court in courts]
```
