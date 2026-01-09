import java.util.*;

/**
 * In-memory Meeting Room Scheduler for recurring meetings.
 *
 * Time is integer-based.
 * Each booking creates EXACTLY 20 occurrences:
 *   start_i = startTime + i * repeatDuration, for i = 0..19
 *   interval_i = [start_i, start_i + duration - 1] (both inclusive)
 *
 * Rules:
 * - Same room cannot have overlapping occurrences across all recurring bookings (consider ONLY the 20 occurrences).
 * - Different rooms can overlap freely.
 */
public class MeetingRoomScheduler {

    private static final int MAX_OCCURRENCES = 20;

    private final int roomsCount;
    private final int employeesCount;

    private final Map<String, Booking> bookingsById = new HashMap<>();
    private final List<List<String>> bookingIdsByRoom;      // roomId -> list of bookingIds
    private final List<List<String>> bookingIdsByEmployee;  // employeeId -> list of bookingIds

    private static class Booking {
        final String bookingId;
        final int employeeId;
        final int roomId;
        final int startTime;
        final int duration;
        final int repeatDuration;

        Booking(String bookingId, int employeeId, int roomId, int startTime, int duration, int repeatDuration) {
            this.bookingId = bookingId;
            this.employeeId = employeeId;
            this.roomId = roomId;
            this.startTime = startTime;
            this.duration = duration;
            this.repeatDuration = repeatDuration;
        }

        long occStart(int i) {
            return (long) startTime + (long) i * (long) repeatDuration;
        }

        long occEnd(int i) {
            return occStart(i) + (long) duration - 1L;
        }
    }

    public MeetingRoomScheduler(int roomsCount, int employeesCount) {
        this.roomsCount = roomsCount;
        this.employeesCount = employeesCount;

        this.bookingIdsByRoom = new ArrayList<>();
        for (int i = 0; i < roomsCount; i++) bookingIdsByRoom.add(new ArrayList<>());

        this.bookingIdsByEmployee = new ArrayList<>();
        for (int i = 0; i < employeesCount; i++) bookingIdsByEmployee.add(new ArrayList<>());
    }

    public boolean bookRoom(String bookingId, int employeeId, int roomId, int startTime, int duration, int repeatDuration) {
        // Defensive checks
        if (bookingId == null || bookingId.isEmpty()) return false;
        if (bookingsById.containsKey(bookingId)) return false;

        if (startTime < 0) return false;
        if (duration <= 0) return false;
        if (repeatDuration <= 0) return false;
        if (duration >= repeatDuration) return false; // spec says duration < repeatDuration

        Booking nb = new Booking(bookingId, employeeId, roomId, startTime, duration, repeatDuration);

        // Check overlap with existing bookings in the same room, considering ONLY 20 occurrences each.
        for (String existingId : bookingIdsByRoom.get(roomId)) {
            Booking eb = bookingsById.get(existingId);
            if (eb == null) continue;
            if (bookingsOverlapWithin20(nb, eb)) {
                return false;
            }
        }

        // No overlaps: add booking.
        bookingsById.put(bookingId, nb);
        bookingIdsByRoom.get(roomId).add(bookingId);
        bookingIdsByEmployee.get(employeeId).add(bookingId);
        return true;
    }

    public List<Integer> getAvailableRooms(int startTime, int endTime) {
        if (startTime > endTime) return new ArrayList<>();

        List<Integer> res = new ArrayList<>();
        for (int roomId = 0; roomId < roomsCount; roomId++) {
            if (isRoomFreeForInterval(roomId, startTime, endTime)) {
                res.add(roomId);
            }
        }
        return res; // ascending order
    }

    public boolean cancelBooking(String bookingId) {
        Booking b = bookingsById.remove(bookingId);
        if (b == null) return false;

        bookingIdsByRoom.get(b.roomId).remove(bookingId);
        bookingIdsByEmployee.get(b.employeeId).remove(bookingId);
        return true;
    }

    public List<String> listBookingsForRoom(int roomId, int n) {
        if (n <= 0) return new ArrayList<>();
        return listFirstNOccurrences(bookingIdsByRoom.get(roomId), n);
    }

    public List<String> listBookingsForEmployee(int employeeId, int n) {
        if (n <= 0) return new ArrayList<>();
        return listFirstNOccurrences(bookingIdsByEmployee.get(employeeId), n);
    }

    // ------------------------- Core helpers -------------------------

    private boolean isRoomFreeForInterval(int roomId, int qs, int qe) {
        for (String bookingId : bookingIdsByRoom.get(roomId)) {
            Booking b = bookingsById.get(bookingId);
            if (b == null) continue;
            if (bookingOverlapsIntervalWithin20(b, qs, qe)) {
                return false;
            }
        }
        return true;
    }

    private static boolean intervalsOverlap(long aStart, long aEnd, long bStart, long bEnd) {
        // closed intervals
        return aStart <= bEnd && bStart <= aEnd;
    }

    private boolean bookingOverlapsIntervalWithin20(Booking b, long qs, long qe) {
        for (int i = 0; i < MAX_OCCURRENCES; i++) {
            long s = b.occStart(i);
            long e = b.occEnd(i);
            if (intervalsOverlap(s, e, qs, qe)) return true;
        }
        return false;
    }

    private boolean bookingsOverlapWithin20(Booking a, Booking b) {
        // naive but correct for fixed 20 occurrences: O(20*20) per pair
        for (int i = 0; i < MAX_OCCURRENCES; i++) {
            long aS = a.occStart(i);
            long aE = a.occEnd(i);
            for (int j = 0; j < MAX_OCCURRENCES; j++) {
                long bS = b.occStart(j);
                long bE = b.occEnd(j);
                if (intervalsOverlap(aS, aE, bS, bE)) return true;
            }
        }
        return false;
    }

    // ------------------------- Listing helpers -------------------------

    private static class Occ {
        final String bookingId;
        final long start;
        final long end;
        final int idx; // occurrence index 0..19

        Occ(String bookingId, long start, long end, int idx) {
            this.bookingId = bookingId;
            this.start = start;
            this.end = end;
            this.idx = idx;
        }
    }

    /**
     * Merge occurrences across multiple bookings and return first n occurrences,
     * sorted by start time (ascending). Each booking contributes at most 20 occurrences.
     */
    private List<String> listFirstNOccurrences(List<String> bookingIds, int n) {
        List<String> res = new ArrayList<>();
        if (bookingIds == null || bookingIds.isEmpty()) return res;

        PriorityQueue<Occ> pq = new PriorityQueue<>((o1, o2) -> {
            if (o1.start != o2.start) return Long.compare(o1.start, o2.start);
            int c = o1.bookingId.compareTo(o2.bookingId);
            if (c != 0) return c;
            return Integer.compare(o1.idx, o2.idx);
        });

        // seed with occurrence 0 for each booking
        for (String id : bookingIds) {
            Booking b = bookingsById.get(id);
            if (b == null) continue;
            long s = b.occStart(0);
            long e = b.occEnd(0);
            pq.add(new Occ(id, s, e, 0));
        }

        while (res.size() < n && !pq.isEmpty()) {
            Occ cur = pq.poll();
            res.add(cur.bookingId + "-" + cur.start + "-" + cur.end);

            Booking b = bookingsById.get(cur.bookingId);
            if (b == null) continue;

            int nextIdx = cur.idx + 1;
            if (nextIdx < MAX_OCCURRENCES) {
                long ns = b.occStart(nextIdx);
                long ne = b.occEnd(nextIdx);
                pq.add(new Occ(cur.bookingId, ns, ne, nextIdx));
            }
        }

        return res;
    }
}
