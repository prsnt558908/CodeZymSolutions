# Fixes vs. your failing test:
# - Align tick() logic with the provided Java:
#   * When IDLE: choose directionTowardNearestWork() (nearest drops or any waiting), then move.
#   * If no work strictly ahead in the current pass, retarget to directionTowardNearestWork()
#     (not just the opposite side). If that yields a new direction, allow same-floor instant boarding.
# - has_work_ahead() matches Java: looks only at current-direction drops and same-direction queues.
#   (Opposite-direction calls DO NOT count as "ahead" for the current pass.)
#
# Result: At floor 2 after dropping 5→2, direction stays D because the nearest pending work is below
# (the Up request at floor 1), so we keep descending to finish the pass before flipping.

from collections import deque
from enum import Enum
from typing import List


class Direction(Enum):
    UP = 1
    DOWN = -1
    IDLE = 0


class LiftSystem:
    """
    Elevator Management System — Single Lift (Python).

    - tick(): moves exactly one floor; on arrival, riders alight first, then boarding happens instantly.
    - addRequest(s, d):
        * Reject if same-direction "already passed".
        * If the rider would board RIGHT NOW (same floor & correct direction/idle), require a free seat.
        * Otherwise accept and queue; the rider will board when the car reaches their floor in the required direction.
    - Direction consistency: the car completes its current pass before flipping.
    - Initial state: 0-I-0 (floor 0, Idle, empty).
    """

    def __init__(self, floors: int, liftsCapacity: int):
        if floors < 2 or floors > 200:
            raise ValueError("floors out of range")
        if liftsCapacity < 1 or liftsCapacity > 20:
            raise ValueError("capacity out of range")

        # config
        self.floors: int = floors
        self.capacity: int = liftsCapacity

        # state
        self.currentFloor: int = 0
        self.direction: Direction = Direction.IDLE
        self.onboard: int = 0

        # drop[f] = number of onboard riders who will get off at floor f
        self.drop: List[int] = [0] * self.floors

        # per-floor waiting queues by direction (store destination floors)
        self.waitUp: List[deque[int]] = [deque() for _ in range(self.floors)]
        self.waitDown: List[deque[int]] = [deque() for _ in range(self.floors)]

    # ---------------- Public API ----------------

    def addRequest(self, startFloor: int, destinationFloor: int) -> bool:
        if not self._in_range(startFloor) or not self._in_range(destinationFloor):
            return False
        if startFloor == destinationFloor:
            return False

        reqDir = Direction.UP if destinationFloor > startFloor else Direction.DOWN

        # "Already passed" rule for same-direction requests
        if self.direction == Direction.UP and reqDir == Direction.UP and startFloor < self.currentFloor:
            return False
        if self.direction == Direction.DOWN and reqDir == Direction.DOWN and startFloor > self.currentFloor:
            return False

        # If IDLE
        if self.direction == Direction.IDLE:
            if startFloor == self.currentFloor:
                # Must have a seat to accept immediate boarding
                if self.onboard >= self.capacity:
                    return False
                self.direction = reqDir
                self._board_now(destinationFloor)
            else:
                # Start moving toward caller's floor
                self.direction = Direction.UP if startFloor > self.currentFloor else Direction.DOWN
                self._enqueue(startFloor, destinationFloor, reqDir)
            return True

        # If moving and the rider would board RIGHT NOW (same floor & same direction)
        if reqDir == self.direction and startFloor == self.currentFloor:
            if self.onboard >= self.capacity:
                return False
            self._board_now(destinationFloor)
            return True

        # Otherwise, accept and queue; service when we pass startFloor in reqDir
        self._enqueue(startFloor, destinationFloor, reqDir)
        return True

    def getLiftState(self) -> str:
        d = "I"
        if self.direction == Direction.UP:
            d = "U"
        elif self.direction == Direction.DOWN:
            d = "D"
        return f"{self.currentFloor}-{d}-{self.onboard}"

    def tick(self) -> None:
        # If idle, choose direction toward the nearest work (immediate floor first)
        if self.direction == Direction.IDLE:
            if not self._has_any_work():
                return
            nd = self._direction_toward_nearest_work()
            self.direction = nd
            if self.direction == Direction.IDLE:
                return

        # If nothing ahead in current pass, retarget toward the nearest work
        if not self._has_work_ahead(self.direction):
            nd = self._direction_toward_nearest_work()
            if nd == Direction.IDLE:
                self.direction = Direction.IDLE
                return
            self.direction = nd

        # Move exactly one floor
        if self.direction == Direction.UP and self.currentFloor < self.floors - 1:
            self.currentFloor += 1
        elif self.direction == Direction.DOWN and self.currentFloor > 0:
            self.currentFloor -= 1

        # Arrival: alight first, then board in current direction
        self._alight_here()
        self._board_here()

        # After servicing this floor, if nothing ahead, pick nearest work; allow same-floor pickup after flip
        if not self._has_work_ahead(self.direction):
            nd = self._direction_toward_nearest_work()
            if nd == Direction.IDLE:
                self.direction = Direction.IDLE
            else:
                self.direction = nd
                self._board_here()

    # ---------------- Internals ----------------

    def _in_range(self, f: int) -> bool:
        return 0 <= f < self.floors

    def _board_now(self, dest: int) -> None:
        self.onboard += 1
        self.drop[dest] = self.drop[dest] + 1

    def _enqueue(self, start: int, dest: int, dirn: Direction) -> None:
        if dirn == Direction.UP:
            self.waitUp[start].append(dest)
        else:
            self.waitDown[start].append(dest)

    def _alight_here(self) -> None:
        if self.drop[self.currentFloor] > 0:
            self.onboard -= self.drop[self.currentFloor]
            self.drop[self.currentFloor] = 0

    def _board_here(self) -> None:
        if self.onboard >= self.capacity:
            return
        q = self.waitUp[self.currentFloor] if self.direction == Direction.UP else self.waitDown[self.currentFloor]
        while q and self.onboard < self.capacity:
            dest = q.popleft()
            self._board_now(dest)

    def _has_any_work(self) -> bool:
        if self.onboard > 0:
            return True
        for f in range(self.floors):
            if self.waitUp[f] or self.waitDown[f]:
                return True
        return False

    # Is there work strictly "ahead" in the current pass? (match Java)
    def _has_work_ahead(self, dirn: Direction) -> bool:
        if dirn == Direction.UP:
            for f in range(self.currentFloor + 1, self.floors):
                if self.drop[f] > 0:
                    return True
            for f in range(self.currentFloor, self.floors):
                if self.waitUp[f]:
                    return True
            return False
        elif dirn == Direction.DOWN:
            for f in range(0, self.currentFloor):
                if self.drop[f] > 0:
                    return True
            for f in range(self.currentFloor, -1, -1):
                if self.waitDown[f]:
                    return True
            return False
        return False

    # Choose direction toward the NEAREST work (drops or any waiting). Prefer immediate same-floor pickups if seats.
    def _direction_toward_nearest_work(self) -> Direction:
        if self.onboard < self.capacity:
            if self.waitUp[self.currentFloor]:
                return Direction.UP
            if self.waitDown[self.currentFloor]:
                return Direction.DOWN

        upDist = float("inf")
        downDist = float("inf")

        # look above
        for f in range(self.currentFloor + 1, self.floors):
            if self.drop[f] > 0 or self.waitUp[f] or self.waitDown[f]:
                upDist = f - self.currentFloor
                break
        # look below
        for f in range(self.currentFloor - 1, -1, -1):
            if self.drop[f] > 0 or self.waitUp[f] or self.waitDown[f]:
                downDist = self.currentFloor - f
                break

        if upDist == float("inf") and downDist == float("inf"):
            return Direction.IDLE
        return Direction.UP if upDist <= downDist else Direction.DOWN
