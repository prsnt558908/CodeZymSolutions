from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Tuple
import bisect


@dataclass(frozen=True, order=True)
class Booking:
    # Order by (startTime, bookingId) to match required listing sort
    startTime: int
    bookingId: str
    employeeId: int
    roomId: int
    endTime: int


class MeetingRoomScheduler:
    def __init__(self, roomsCount: int, employeesCount: int):
        self.roomsCount = roomsCount
        self.employeesCount = employeesCount

        # bookingId -> Booking
        self._by_id: Dict[str, Booking] = {}

        # roomId -> sorted list[Booking] by (startTime, bookingId)
        self._room_bookings: List[List[Booking]] = [[] for _ in range(roomsCount)]

        # employeeId -> sorted list[Booking] by (startTime, bookingId)
        self._emp_bookings: List[List[Booking]] = [[] for _ in range(employeesCount)]

    # CLOSED intervals overlap if: a.start <= b.end && b.start <= a.end
    def _overlaps(self, b: Booking, start: int, end: int) -> bool:
        return start <= b.endTime and b.startTime <= end

    def bookRoom(self, bookingId: str, employeeId: int, roomId: int, startTime: int, endTime: int) -> bool:
        # extra safety (spec says bookingId is unique + non-blank, ids valid)
        if bookingId is None or str(bookingId).strip() == "":
            return False
        if startTime > endTime or startTime < 0:
            return False
        if bookingId in self._by_id:
            return False

        room_list = self._room_bookings[roomId]

        # Find insertion point by (startTime, bookingId)
        key = Booking(startTime=startTime, bookingId=bookingId, employeeId=employeeId, roomId=roomId, endTime=endTime)
        idx = bisect.bisect_left(room_list, key)

        # Only neighbors can overlap if list is sorted by startTime
        if idx - 1 >= 0 and self._overlaps(room_list[idx - 1], startTime, endTime):
            return False
        if idx < len(room_list) and self._overlaps(room_list[idx], startTime, endTime):
            return False

        # Insert everywhere
        bisect.insort(room_list, key)
        bisect.insort(self._emp_bookings[employeeId], key)
        self._by_id[bookingId] = key
        return True

    def getAvailableRooms(self, startTime: int, endTime: int) -> List[int]:
        if startTime > endTime:
            return []

        ans: List[int] = []
        for roomId in range(self.roomsCount):
            room_list = self._room_bookings[roomId]
            if not room_list:
                ans.append(roomId)
                continue

            # Find first booking with startTime >= query startTime
            dummy = Booking(startTime=startTime, bookingId="", employeeId=0, roomId=roomId, endTime=startTime)
            idx = bisect.bisect_left(room_list, dummy)

            busy = False
            if idx - 1 >= 0 and self._overlaps(room_list[idx - 1], startTime, endTime):
                busy = True
            elif idx < len(room_list) and self._overlaps(room_list[idx], startTime, endTime):
                busy = True

            if not busy:
                ans.append(roomId)

        # already ascending due to loop order
        return ans

    def cancelBooking(self, bookingId: str) -> bool:
        b = self._by_id.pop(bookingId, None)
        if b is None:
            return False

        # remove from room list
        room_list = self._room_bookings[b.roomId]
        idx = bisect.bisect_left(room_list, b)
        # due to uniqueness, it should match at idx
        if idx < len(room_list) and room_list[idx] == b:
            room_list.pop(idx)
        else:
            # fallback linear search (shouldn't happen, but keeps structure consistent)
            for i, x in enumerate(room_list):
                if x.bookingId == bookingId:
                    room_list.pop(i)
                    break

        # remove from employee list
        emp_list = self._emp_bookings[b.employeeId]
        idx = bisect.bisect_left(emp_list, b)
        if idx < len(emp_list) and emp_list[idx] == b:
            emp_list.pop(idx)
        else:
            for i, x in enumerate(emp_list):
                if x.bookingId == bookingId:
                    emp_list.pop(i)
                    break

        return True

    def listBookingsForRoom(self, roomId: int) -> List[str]:
        return [b.bookingId for b in self._room_bookings[roomId]]

    def listBookingsForEmployee(self, employeeId: int) -> List[str]:
        return [b.bookingId for b in self._emp_bookings[employeeId]]
