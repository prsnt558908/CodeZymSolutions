# Assign Tennis Court Bookings With Maintenance in Java


#### Problem Statement
[https://codezym.com/question/463-tennis-court-book-maintain](https://codezym.com/question/463-tennis-court-book-maintain)


Sort bookings by start time and booking id. For each booking, release every court that is ready, then choose the smallest available court number. Two priority queues keep these steps fast, while a counter on each court tells us when to add maintenance time.


## Start with a simple solution


Parse each booking into numeric values and sort by `(startTime, bookingId)`. The id comparison must be numeric, so booking 2 comes before booking 10 when they start together.


Keep courts in a list. For each booking, scan from court 0 upward and pick the first court whose next available time is at most the booking start time. If none is ready, add a new court.


This follows the assignment rule, but the scans can take `O(n²)` time. When all bookings overlap, every new booking checks every existing court. That is too much work for 100,000 bookings.


## Replace the scans with two priority queues


A min-heap keeps its smallest value at the front. We need two heaps because the earliest available time and the smallest court number answer different questions.


- `busyCourts` keeps occupied courts ordered by their next available time. That time includes maintenance when required.
- `freeCourts` keeps available court numbers ordered from smallest to largest.
- `courts` stores courts in number order. Its index is the court number, and each court stores its usage counter and assigned booking ids.


`Booking` groups the parsed id and times. `Court` groups the court number, next available time, counter, and output. Java uses `PriorityQueue` for both heaps and `StringBuilder` to collect comma-separated ids without repeatedly copying the accumulated string.


For each sorted booking:


1. Move **every** court with `freeAt <= startTime` from `busyCourts` into `freeCourts`.
2. Take the smallest number from `freeCourts`. If it is empty, create court `courts.size()`.
3. Record the booking, update the court's next available time, and put it into `busyCourts`.


Releasing every ready court matters. Suppose court 1 became free at time 5 and court 0 became free at time 7. A booking starting at time 8 must use court 0, even though court 1 became free earlier.


## Add maintenance to the available time


After assigning a booking, increase `bookingsSinceMaintenance` and set `freeAt` to its finish time. When the counter reaches `durability`, add `maintenanceTime` to that finish time and reset the counter to zero.


Maintenance starts when the triggering booking finishes. A long idle gap does not reset the counter, and maintenance is added only after a complete batch of `durability` bookings.


Both public methods use `assignBookings`. The method without maintenance passes a maintenance time of 0 and a durability of 1, so every court becomes available exactly when its booking finishes.


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


## Java Code

```java
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class AssignTennisCourts {

    public AssignTennisCourts() {
    }

    public List<String> assignCourts(List<String> bookings) {
        return assignBookings(bookings, 0, 1);
    }

    public List<String> assignCourtsWithMaintenance(
            List<String> bookings, int maintenanceTime, int durability) {
        return assignBookings(bookings, maintenanceTime, durability);
    }

    /** Shared scheduler. All court data belongs to this call. */
    List<String> assignBookings(
            List<String> bookings, int maintenanceTime, int durability) {
        List<Booking> sortedBookings = new ArrayList<>();
        for (String value : bookings) {
            String[] parts = value.split(",");
            sortedBookings.add(new Booking(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])));
        }

        // Compare numeric ids when start times are equal.
        sortedBookings.sort((first, second) -> {
            int comparison = Integer.compare(first.startTime, second.startTime);
            if (comparison != 0) {
                return comparison;
            }
            return Integer.compare(first.id, second.id);
        });

        List<Court> courts = new ArrayList<>();
        PriorityQueue<Court> busyCourts = new PriorityQueue<>(
                (first, second) -> Long.compare(first.freeAt, second.freeAt));
        PriorityQueue<Integer> freeCourts = new PriorityQueue<>();

        for (Booking booking : sortedBookings) {
            // Release every ready court before choosing the smallest number.
            while (!busyCourts.isEmpty()
                    && busyCourts.peek().freeAt <= booking.startTime) {
                freeCourts.add(busyCourts.poll().number);
            }

            Court court;
            if (freeCourts.isEmpty()) {
                court = new Court(courts.size());
                courts.add(court);
            } else {
                court = courts.get(freeCourts.poll());
            }

            if (court.bookingIds.length() > 0) {
                court.bookingIds.append(",");
            }
            court.bookingIds.append(booking.id);

            // Maintenance starts when the booking finishes.
            court.freeAt = booking.finishTime;
            court.bookingsSinceMaintenance++;
            if (court.bookingsSinceMaintenance == durability) {
                court.freeAt += maintenanceTime;
                court.bookingsSinceMaintenance = 0;
            }

            busyCourts.add(court);
        }

        List<String> result = new ArrayList<>();
        for (Court court : courts) {
            result.add(court.bookingIds.toString());
        }
        return result;
    }
}

class Booking {
    int id;
    int startTime;
    int finishTime;

    Booking(int id, int startTime, int finishTime) {
        this.id = id;
        this.startTime = startTime;
        this.finishTime = finishTime;
    }
}

class Court {
    int number;
    long freeAt;
    int bookingsSinceMaintenance;
    StringBuilder bookingIds = new StringBuilder();

    Court(int number) {
        this.number = number;
    }
}
```
