# import java.util.*;  -> Python equivalents below
from dataclasses import dataclass
from bisect import bisect_left, bisect_right
from typing import Dict, List, Tuple

"""
RoomBooking

- Rooms are fixed at construction and kept sorted lexicographically.
- Each room stores its meetings in a TreeMap<start, end>.
  Overlap check for inclusive [s, e]:
    prev = floorEntry(s)  -> conflict if prev.end >= s
    next = ceilingEntry(s)-> conflict if next.start <= e
- bookMeeting returns the lexicographically smallest room that can host [s, e], else "".
- cancelMeeting removes by meetingId if active.

Complexity:
  - book: O(R * log K) in worst case (R rooms, K meetings in a room), typically fast in practice.
  - cancel: O(log K)
"""
class RoomBooking:

    # Per-room schedule: start -> end (times are inclusive)
    # In Python, we emulate a TreeMap per room with a list of (start, end) sorted by start.
    schedules: Dict[str, List[Tuple[int, int]]]

    # Active meetings: meetingId -> Booking(roomId, start, end)
    active: Dict[str, "RoomBooking.Booking"]

    # Rooms kept lexicographically sorted to honor the “smallest room id” rule
    roomsSorted: List[str]

    @dataclass(frozen=True)
    class Booking:
        roomId: str
        start: int
        end: int

    def __init__(self, roomIds: List[str]):
        # Copy & sort room ids lexicographically
        self.roomsSorted = list(roomIds)
        self.roomsSorted.sort()
        # Initialize an empty schedule per room
        self.schedules = {r: [] for r in self.roomsSorted}
        self.active = {}

    """
    Attempts to book an inclusive interval [startTime, endTime] for meetingId.
    Returns the lexicographically smallest room id that can host it, or "" if none.
    If meetingId is already active, booking is rejected and "" is returned.
    """
    def bookMeeting(self, meetingId: str, startTime: int, endTime: int) -> str:
        # meetingId cannot refer to more than one active meeting at a time
        if meetingId in self.active:
            return ""

        for room in self.roomsSorted:
            tm = self.schedules[room]  # sorted list of (start, end) by start

            # Prepare a list of starts to emulate TreeMap floor/ceiling by key
            starts = [s for s, _ in tm]

            # Check overlap with the previous interval (by start) relative to startTime
            i = bisect_left(starts, startTime)
            prev_idx = i - 1
            if prev_idx >= 0:
                prev_end = tm[prev_idx][1]
                if prev_end >= startTime:
                    # Inclusive conflict (prev ends at or after startTime)
                    continue

            # Check overlap with the next interval (by start) relative to startTime
            next_idx = i  # ceilingEntry(s) -> first start >= s
            if next_idx < len(tm):
                next_start = tm[next_idx][0]
                if next_start <= endTime:
                    # Inclusive conflict (next starts at or before endTime)
                    continue

            # No conflict: insert and record active booking
            tm.insert(i, (startTime, endTime))
            self.active[meetingId] = RoomBooking.Booking(room, startTime, endTime)
            return room

        # No room available
        return ""

    """
    Cancels the meeting with meetingId if active.
    Returns true if it existed and was canceled; false otherwise.
    """
    def cancelMeeting(self, meetingId: str) -> bool:
        b = self.active.pop(meetingId, None)
        if b is None:
            return False

        tm = self.schedules.get(b.roomId)
        if tm is not None:
            # Stored by start time; remove exact interval by start
            # Since starts are unique under non-overlap, bisect to locate b.start
            starts = [s for s, _ in tm]
            idx = bisect_left(starts, b.start)
            if idx < len(tm) and tm[idx][0] == b.start:
                tm.pop(idx)
            # Optional sanity: if removed interval existed but end mismatched (shouldn't happen),
            # we could restore map; per construction we only ever store unique [start,end].

        return True
