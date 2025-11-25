import java.util.*;

/**
 * RoomBooking
 *
 * - Rooms are fixed at construction and kept sorted lexicographically.
 * - Each room stores its meetings in a TreeMap<start, end>.
 *   Overlap check for inclusive [s, e]:
 *     prev = floorEntry(s)  -> conflict if prev.end >= s
 *     next = ceilingEntry(s)-> conflict if next.start <= e
 * - bookMeeting returns the lexicographically smallest room that can host [s, e], else "".
 * - cancelMeeting removes by meetingId if active.
 *
 * Complexity:
 *   - book: O(R * log K) in worst case (R rooms, K meetings in a room), typically fast in practice.
 *   - cancel: O(log K)
 */
public class RoomBooking {

    /** Per-room schedule: start -> end (times are inclusive) */
    private final Map<String, TreeMap<Integer, Integer>> schedules = new HashMap<>();

    /** Active meetings: meetingId -> Booking(roomId, start, end) */
    private final Map<String, Booking> active = new HashMap<>();

    /** Rooms kept lexicographically sorted to honor the “smallest room id” rule */
    private final List<String> roomsSorted;

    private static final class Booking {
        final String roomId;
        final int start;
        final int end;
        Booking(String roomId, int start, int end) {
            this.roomId = roomId;
            this.start = start;
            this.end = end;
        }
    }

    public RoomBooking(List<String> roomIds) {
        // Copy & sort room ids lexicographically
        this.roomsSorted = new ArrayList<>(roomIds);
        Collections.sort(this.roomsSorted);
        // Initialize an empty schedule per room
        for (String r : this.roomsSorted) {
            schedules.put(r, new TreeMap<>());
        }
    }

    /**
     * Attempts to book an inclusive interval [startTime, endTime] for meetingId.
     * Returns the lexicographically smallest room id that can host it, or "" if none.
     * If meetingId is already active, booking is rejected and "" is returned.
     */
    public String bookMeeting(String meetingId, int startTime, int endTime) {
        // meetingId cannot refer to more than one active meeting at a time
        if (active.containsKey(meetingId)) {
            return "";
        }

        for (String room : roomsSorted) {
            TreeMap<Integer, Integer> tm = schedules.get(room);

            // Check overlap with the previous interval (by start) relative to startTime
            Map.Entry<Integer, Integer> prev = tm.floorEntry(startTime);
            if (prev != null && prev.getValue() >= startTime) {
                // Inclusive conflict (prev ends at or after startTime)
                continue;
            }

            // Check overlap with the next interval (by start) relative to startTime
            Map.Entry<Integer, Integer> next = tm.ceilingEntry(startTime);
            if (next != null && next.getKey() <= endTime) {
                // Inclusive conflict (next starts at or before endTime)
                continue;
            }

            // No conflict: insert and record active booking
            tm.put(startTime, endTime);
            active.put(meetingId, new Booking(room, startTime, endTime));
            return room;
        }

        // No room available
        return "";
    }

    /**
     * Cancels the meeting with meetingId if active.
     * Returns true if it existed and was canceled; false otherwise.
     */
    public boolean cancelMeeting(String meetingId) {
        Booking b = active.remove(meetingId);
        if (b == null) return false;

        TreeMap<Integer, Integer> tm = schedules.get(b.roomId);
        if (tm != null) {
            // Stored by start time; remove exact interval
            Integer removed = tm.remove(b.start);
            // Optional sanity: if removed interval existed but end mismatched (shouldn't happen), we could restore map
            // but per construction we only ever store unique [start,end] non-overlapping intervals in a room.
        }
        return true;
    }
}
