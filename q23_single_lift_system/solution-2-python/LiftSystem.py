# Design an Elevator System - Request Feasibility (Single Lift)
# Python reference implementation that enforces:
# - Per-direction independence (UP and DOWN do not clash)
# - Capacity on every floor-to-floor segment within a direction
# - "maxStops" rule for every rider already accepted AND the new rider:
#   Count distinct halt floors strictly between (source, destination).
#   Halts are all accepted sources/destinations in that direction.
# - Inputs are validated against floorsCount and source != destination.
#
# Assumption from spec: the car remains IDLE during submissions; we only
# build/validate a feasible plan of halts. No timing/simulation is needed.

from typing import List, Tuple

class LiftSystem:
    def __init__(self, floorsCount: int, liftCapacity: int, maxStops: int):
        # basic argument checks
        if not (2 <= floorsCount <= 100):
            raise ValueError("floorsCount must be in [2, 100]")
        if not (1 <= liftCapacity <= 20):
            raise ValueError("liftCapacity must be in [1, 20]")
        if not (1 <= maxStops <= 10):
            raise ValueError("maxStops must be in [1, 10]")

        self.floorsCount = floorsCount
        self.liftCapacity = liftCapacity
        self.maxStops = maxStops

        # Maintain accepted requests per direction.
        # UP rides store (src,dst) with src < dst
        # DOWN rides store (src,dst) with src > dst
        self.up_rides: List[Tuple[int, int]] = []
        self.down_rides: List[Tuple[int, int]] = []

    def requestPickup(self, source: int, destination: int) -> bool:
        # Validate inputs
        if not (0 <= source < self.floorsCount and 0 <= destination < self.floorsCount):
            return False
        if source == destination:
            # Spec: "source and destination will never be same"
            return False

        is_up = source < destination
        rides = self.up_rides if is_up else self.down_rides

        # Normalize tuple direction (src < dst for UP, src > dst for DOWN) for internal consistency
        s, d = (source, destination) if is_up else (source, destination)

        # Try adding and check feasibility
        candidate = rides + [(s, d)]
        if not self._feasible(candidate):
            return False

        # If feasible, persist
        rides.append((s, d))
        return True

    # ---------- Internal helpers ----------

    def _feasible(self, rides: List[Tuple[int, int]]) -> bool:
        """
        Check both constraints for ONE direction:
        - Capacity on every segment
        - maxStops for every rider (including the newest)
        """
        # Build the set of distinct halt floors in THIS direction:
        # every source/destination of every accepted request
        halts = set()
        for s, d in rides:
            halts.add(s)
            halts.add(d)

        # maxStops check for EACH rider
        for s, d in rides:
            lo, hi = (s, d) if s < d else (d, s)  # strict bounds
            # Distinct halts strictly between lo and hi
            stops_between = sum(1 for h in halts if lo < h < hi)
            if stops_between > self.maxStops:
                return False

        # Capacity check per floor-to-floor segment
        # segments are [f, f+1) for integer floors f
        load = [0] * (self.floorsCount - 1)  # load[f] is riders crossing segment f→f+1
        for s, d in rides:
            lo, hi = (s, d) if s < d else (d, s)
            for f in range(lo, hi):
                load[f - 0] += 1
                if load[f - 0] > self.liftCapacity:
                    return False

        return True

