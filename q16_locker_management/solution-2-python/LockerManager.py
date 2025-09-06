from dataclasses import dataclass
import heapq
from typing import Dict, Set, List

class LockerManager:
    """
    Lexicographic Locker Manager
    - findSlot(size) returns the lexicographically smallest FREE lockerId for that size.
    - IDs are "<SIZE><number>", e.g., "M1", "M10" (note: "M10" < "M2" lexicographically).
    """

    @dataclass
    class Locker:
        id: str
        size: str
        occupied: bool

    def __init__(self):
        self.VALID_SIZES: Set[str] = {"S", "M", "L", "XL", "XXL"}
        self.nextIndex: Dict[str, int] = {s: 0 for s in self.VALID_SIZES}

        # Free structures per size: min-heap of IDs (strings) + set for membership
        self._free_heap: Dict[str, List[str]] = {s: [] for s in self.VALID_SIZES}
        self._free_set: Dict[str, Set[str]] = {s: set() for s in self.VALID_SIZES}

        self.byId: Dict[str, LockerManager.Locker] = {}

    def addSlot(self, size: str):
        if size not in self.VALID_SIZES:
            return
        idx = self.nextIndex[size] + 1
        self.nextIndex[size] = idx
        locker_id = f"{size}{idx}"
        lk = LockerManager.Locker(locker_id, size, False)
        self.byId[locker_id] = lk
        self._free_set[size].add(locker_id)
        heapq.heappush(self._free_heap[size], locker_id)  # lexicographic order on strings

    def _pop_stale(self, size: str):
        heap = self._free_heap[size]
        s = self._free_set[size]
        while heap and heap[0] not in s:
            heapq.heappop(heap)

    def findSlot(self, size: str) -> str:
        if size not in self.VALID_SIZES:
            return ""
        self._pop_stale(size)
        heap = self._free_heap[size]
        if not heap:
            return ""
        return heap[0]  # lexicographically smallest free ID

    def occupySlot(self, lockerId: str) -> bool:
        lk = self.byId.get(lockerId)
        if lk is None or lk.occupied:
            return False
        if lockerId not in self._free_set[lk.size]:
            return False
        self._free_set[lk.size].remove(lockerId)  # lazy-remove from heap
        lk.occupied = True
        return True

    def freeSlot(self, lockerId: str) -> bool:
        lk = self.byId.get(lockerId)
        if lk is None or not lk.occupied:
            return False
        lk.occupied = False
        if lockerId not in self._free_set[lk.size]:
            self._free_set[lk.size].add(lockerId)
            heapq.heappush(self._free_heap[lk.size], lockerId)
        return True
