from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP, getcontext
import heapq
from typing import List, Tuple

getcontext().prec = 40


def _round2_java(x: Decimal) -> Decimal:
    """
    Java behavior: Math.round(value * 100.0) / 100.0 for positive values.
    This is HALF_UP rounding to 2 decimals.
    """
    return (x * Decimal(100)).quantize(Decimal("1"), rounding=ROUND_HALF_UP) / Decimal(100)


@dataclass(frozen=True, order=True)
class _Lap:
    # Ordered exactly as required: timeTaken, carId, lapId
    time: int
    car: int
    lap: int

    def row(self) -> str:
        return f"{self.car}-{self.lap}-{self.time}"


class LastLapHero:
    """
    In-memory F1 tracker.
    - Top 3 fastest laps by (timeTaken, carId, lapId)
    - Top 3 drivers by (roundedAvg, carId), where roundedAvg uses Java Math.round(x*100)/100.
    """

    def __init__(self, carsCount: int, lapsCount: int):
        self.carsCount = carsCount
        self.lapsCount = lapsCount

        # For top laps: store ALL laps, but keep a size-3 max-heap of the best 3.
        # Heap items are "worst-first" among the kept best 3:
        #   (-time, -car, -lap, LapObj)
        # so heap[0] is the worst among currently kept best 3.
        self._top_heap: List[Tuple[int, int, int, _Lap]] = []

        # For driver averages: totals and counts
        self._total_time: List[int] = [0] * carsCount
        self._lap_count: List[int] = [0] * carsCount

    def recordLapTiming(self, carId: int, lapId: int, timeTaken: int) -> None:
        lap = _Lap(timeTaken, carId, lapId)

        # --- update top 3 fastest laps ---
        item = (-lap.time, -lap.car, -lap.lap, lap)
        if len(self._top_heap) < 3:
            heapq.heappush(self._top_heap, item)
        else:
            # If new lap is better than current "worst of best3", replace it.
            # Better means smaller (time, car, lap).
            worst = self._top_heap[0][3]  # Lap object
            if (lap.time, lap.car, lap.lap) < (worst.time, worst.car, worst.lap):
                heapq.heapreplace(self._top_heap, item)

        # --- update driver totals ---
        self._total_time[carId] += timeTaken
        self._lap_count[carId] += 1

    def getTop3FastestLaps(self) -> List[str]:
        # Heap holds up to 3 best laps, but not in sorted order -> sort them ascending.
        laps = [t[3] for t in self._top_heap]
        laps.sort(key=lambda x: (x.time, x.car, x.lap))
        return [l.row() for l in laps]

    def getTop3Drivers(self) -> List[int]:
        drivers: List[Tuple[Decimal, int]] = []
        for car in range(self.carsCount):
            cnt = self._lap_count[car]
            if cnt == 0:
                continue
            avg = Decimal(self._total_time[car]) / Decimal(cnt)
            ravg = _round2_java(avg)
            drivers.append((ravg, car))

        drivers.sort(key=lambda x: (x[0], x[1]))
        return [car for _, car in drivers[:3]]
