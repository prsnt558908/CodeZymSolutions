import java.util.*;

public class MeetingRoomScheduler {

    private static class Booking {
        final String bookingId;
        final int employeeId;
        final int roomId;
        final int startTime;
        final int endTime;

        Booking(String bookingId, int employeeId, int roomId, int startTime, int endTime) {
            this.bookingId = bookingId;
            this.employeeId = employeeId;
            this.roomId = roomId;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    private static final Comparator<Booking> BOOKING_ORDER =
            (a, b) -> {
                if (a.startTime != b.startTime) return Integer.compare(a.startTime, b.startTime);
                return a.bookingId.compareTo(b.bookingId);
            };

    private static final String HIGH_ID = "\uFFFF";

    private final int roomsCount;
    private final int employeesCount;

    // bookingId -> Booking
    private final Map<String, Booking> byId = new HashMap<>();

    // roomId -> bookings sorted by (startTime, bookingId)
    private final List<TreeSet<Booking>> roomToBookings;

    // employeeId -> bookings sorted by (startTime, bookingId)
    private final List<TreeSet<Booking>> employeeToBookings;

    public MeetingRoomScheduler(int roomsCount, int employeesCount) {
        this.roomsCount = roomsCount;
        this.employeesCount = employeesCount;

        this.roomToBookings = new ArrayList<>(roomsCount);
        for (int i = 0; i < roomsCount; i++) {
            roomToBookings.add(new TreeSet<>(BOOKING_ORDER));
        }

        this.employeeToBookings = new ArrayList<>(employeesCount);
        for (int i = 0; i < employeesCount; i++) {
            employeeToBookings.add(new TreeSet<>(BOOKING_ORDER));
        }
    }

    // CLOSED intervals overlap if: a.start <= b.end && b.start <= a.end
    private boolean overlaps(Booking b, int start, int end) {
        return start <= b.endTime && b.startTime <= end;
    }

    private Booking floorByStart(TreeSet<Booking> set, int startTime) {
        // choose highest bookingId for same startTime to get true floor
        Booking key = new Booking(HIGH_ID, -1, -1, startTime, startTime);
        return set.floor(key);
    }

    private Booking ceilingByStart(TreeSet<Booking> set, int startTime) {
        // choose smallest bookingId for same startTime to get true ceiling
        Booking key = new Booking("", -1, -1, startTime, startTime);
        return set.ceiling(key);
    }

    public boolean bookRoom(String bookingId, int employeeId, int roomId, int startTime, int endTime) {
        if (bookingId == null || bookingId.isBlank()) return false; // extra safety
        if (startTime > endTime || startTime < 0) return false;
        if (byId.containsKey(bookingId)) return false; // extra safety (spec says unique)

        TreeSet<Booking> roomSet = roomToBookings.get(roomId);

        Booking prev = floorByStart(roomSet, startTime);
        if (prev != null && overlaps(prev, startTime, endTime)) return false;

        Booking next = ceilingByStart(roomSet, startTime);
        if (next != null && overlaps(next, startTime, endTime)) return false;

        Booking b = new Booking(bookingId, employeeId, roomId, startTime, endTime);
        roomSet.add(b);
        employeeToBookings.get(employeeId).add(b);
        byId.put(bookingId, b);
        return true;
    }

    public List<Integer> getAvailableRooms(int startTime, int endTime) {
        if (startTime > endTime) return new ArrayList<>();

        List<Integer> ans = new ArrayList<>();
        for (int roomId = 0; roomId < roomsCount; roomId++) {
            TreeSet<Booking> roomSet = roomToBookings.get(roomId);
            if (roomSet.isEmpty()) {
                ans.add(roomId);
                continue;
            }

            boolean busy = false;

            Booking prev = floorByStart(roomSet, startTime);
            if (prev != null && overlaps(prev, startTime, endTime)) {
                busy = true;
            } else {
                Booking next = ceilingByStart(roomSet, startTime);
                if (next != null && overlaps(next, startTime, endTime)) busy = true;
            }

            if (!busy) ans.add(roomId);
        }
        return ans; // already ascending due to loop order
    }

    public boolean cancelBooking(String bookingId) {
        Booking b = byId.remove(bookingId);
        if (b == null) return false;

        roomToBookings.get(b.roomId).remove(b);
        employeeToBookings.get(b.employeeId).remove(b);
        return true;
    }

    public List<String> listBookingsForRoom(int roomId) {
        List<String> res = new ArrayList<>();
        for (Booking b : roomToBookings.get(roomId)) {
            res.add(b.bookingId);
        }
        return res;
    }

    public List<String> listBookingsForEmployee(int employeeId) {
        List<String> res = new ArrayList<>();
        for (Booking b : employeeToBookings.get(employeeId)) {
            res.add(b.bookingId);
        }
        return res;
    }
}
