from __future__ import annotations

from dataclasses import dataclass
import heapq
from typing import Dict, List


@dataclass(frozen=True)
class _Booking:
    booking_id: str
    employee_id: int
    room_id: int
    start_time: int
    duration: int
    repeat_duration: int

    def occ_start(self, i: int) -> int:
        return self.start_time + i * self.repeat_duration

    def occ_end(self, i: int) -> int:
        return self.occ_start(i) + self.duration - 1


@dataclass(order=True)
class _Occ:
    # heap order: (start, booking_id, idx)
    start: int
    booking_id: str
    idx: int
    end: int


class MeetingRoomScheduler:
    """
    In-memory Meeting Room Scheduler for recurring meetings.

    Each booking creates EXACTLY 20 occurrences:
      start_i = startTime + i * repeatDuration, for i = 0..19
      interval_i = [start_i, start_i + duration - 1] (both inclusive)

    Rules:
    - Same room cannot have overlapping occurrences across all bookings (consider ONLY the 20 occurrences).
    - Different rooms can overlap freely.
    """

    MAX_OCCURRENCES = 20

    def __init__(self, roomsCount: int, employeesCount: int):
        self.rooms_count = roomsCount
        self.employees_count = employeesCount

        self.bookings_by_id: Dict[str, _Booking] = {}
        self.booking_ids_by_room: List[List[str]] = [[] for _ in range(roomsCount)]
        self.booking_ids_by_employee: List[List[str]] = [[] for _ in range(employeesCount)]

    def bookRoom(
        self,
        bookingId: str,
        employeeId: int,
        roomId: int,
        startTime: int,
        duration: int,
        repeatDuration: int,
    ) -> bool:
        # bookingId is said to be unique and non-blank; employeeId/roomId are valid.
        # Still handle defensively.
        if bookingId is None or bookingId == "":
            return False
        if bookingId in self.bookings_by_id:
            return False

        if startTime < 0:
            return False
        if duration <= 0:
            return False
        if repeatDuration <= 0:
            return False
        if duration >= repeatDuration:  # spec says duration < repeatDuration
            return False

        nb = _Booking(
            booking_id=bookingId,
            employee_id=employeeId,
            room_id=roomId,
            start_time=startTime,
            duration=duration,
            repeat_duration=repeatDuration,
        )

        # Check overlap with existing bookings in the same room (only 20 occurrences).
        for existing_id in self.booking_ids_by_room[roomId]:
            eb = self.bookings_by_id.get(existing_id)
            if eb is None:
                continue
            if self._bookings_overlap_within_20(nb, eb):
                return False

        # No overlaps: add booking
        self.bookings_by_id[bookingId] = nb
        self.booking_ids_by_room[roomId].append(bookingId)
        self.booking_ids_by_employee[employeeId].append(bookingId)
        return True

    def getAvailableRooms(self, startTime: int, endTime: int) -> List[int]:
        if startTime > endTime:
            return []

        res: List[int] = []
        for room_id in range(self.rooms_count):
            if self._is_room_free_for_interval(room_id, startTime, endTime):
                res.append(room_id)
        return res  # already ascending

    def cancelBooking(self, bookingId: str) -> bool:
        b = self.bookings_by_id.pop(bookingId, None)
        if b is None:
            return False

        # Remove IDs from indices (linear scan; small N typical for LLD)
        self.booking_ids_by_room[b.room_id] = [x for x in self.booking_ids_by_room[b.room_id] if x != bookingId]
        self.booking_ids_by_employee[b.employee_id] = [
            x for x in self.booking_ids_by_employee[b.employee_id] if x != bookingId
        ]
        return True

    def listBookingsForRoom(self, roomId: int, n: int) -> List[str]:
        if n <= 0:
            return []
        return self._list_first_n_occurrences(self.booking_ids_by_room[roomId], n)

    def listBookingsForEmployee(self, employeeId: int, n: int) -> List[str]:
        if n <= 0:
            return []
        return self._list_first_n_occurrences(self.booking_ids_by_employee[employeeId], n)

    # ------------------------- Core helpers -------------------------

    @staticmethod
    def _intervals_overlap(a_start: int, a_end: int, b_start: int, b_end: int) -> bool:
        # Closed intervals
        return a_start <= b_end and b_start <= a_end

    def _is_room_free_for_interval(self, room_id: int, qs: int, qe: int) -> bool:
        for bid in self.booking_ids_by_room[room_id]:
            b = self.bookings_by_id.get(bid)
            if b is None:
                continue
            if self._booking_overlaps_interval_within_20(b, qs, qe):
                return False
        return True

    def _booking_overlaps_interval_within_20(self, b: _Booking, qs: int, qe: int) -> bool:
        for i in range(self.MAX_OCCURRENCES):
            s = b.occ_start(i)
            e = b.occ_end(i)
            if self._intervals_overlap(s, e, qs, qe):
                return True
        return False

    def _bookings_overlap_within_20(self, a: _Booking, b: _Booking) -> bool:
        # O(20*20) exact check
        for i in range(self.MAX_OCCURRENCES):
            a_s = a.occ_start(i)
            a_e = a.occ_end(i)
            for j in range(self.MAX_OCCURRENCES):
                b_s = b.occ_start(j)
                b_e = b.occ_end(j)
                if self._intervals_overlap(a_s, a_e, b_s, b_e):
                    return True
        return False

    # ------------------------- Listing helpers -------------------------

    def _list_first_n_occurrences(self, booking_ids: List[str], n: int) -> List[str]:
        res: List[str] = []
        if not booking_ids:
            return res

        heap: List[_Occ] = []

        # Seed each booking with occurrence 0
        for bid in booking_ids:
            b = self.bookings_by_id.get(bid)
            if b is None:
                continue
            s = b.occ_start(0)
            e = b.occ_end(0)
            heapq.heappush(heap, _Occ(start=s, booking_id=bid, idx=0, end=e))

        while len(res) < n and heap:
            cur = heapq.heappop(heap)
            res.append(f"{cur.booking_id}-{cur.start}-{cur.end}")

            b = self.bookings_by_id.get(cur.booking_id)
            if b is None:
                continue

            next_idx = cur.idx + 1
            if next_idx < self.MAX_OCCURRENCES:
                ns = b.occ_start(next_idx)
                ne = b.occ_end(next_idx)
                heapq.heappush(heap, _Occ(start=ns, booking_id=cur.booking_id, idx=next_idx, end=ne))

        return res
