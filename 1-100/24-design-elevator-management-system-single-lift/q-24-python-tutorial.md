# Design Elevator Management System - Single Lift in Python


#### Problem Statement

[https://codezym.com/question/24-design-elevator-management-system-single-lift](https://codezym.com/question/24-design-elevator-management-system-single-lift)


The core idea is to model the lift as a small state machine with three states, `U`, `D`, and `I`. We do not need a full State design pattern with separate classes because there is only one lift and each state has very little behavior. A small dataclass groups the lift's changing state, while separate waiting queues store upward and downward riders on every floor. A new same-direction request at the current floor needs a free seat immediately. An eligible future pickup can be queued. Already accepted riders who find the car full stay queued for a later pass.

## 1. Start With a Simple but Incomplete Approach

A first idea is to store only the number of pickups and drop-offs at every floor.

That works while every waiting rider can board immediately. It breaks when the lift is full because different waiting riders may have different destinations. If we keep only a pickup count, we no longer know which drop-off should be scheduled when one of those riders eventually boards.

Another tempting idea is to reserve a seat across every floor segment before accepting a request. That is too strict for the supplied test expectations. A request from a floor ahead can be accepted even when the car will be full on its next visit there. The rider remains waiting and boards during a later pass when space is available. This does not remove the separate capacity check for a new rider who would board immediately at the current floor.

We therefore need to keep each waiting rider's destination until that rider actually enters the lift.

## 2. Separate Lift Direction From Rider Direction

The lift's movement direction and a rider's requested direction are not always the same.

For example, suppose the lift is idle at floor 0 and receives a request from floor 6 to floor 2. The rider wants to go down, but the empty lift must first move up to floor 6. At floor 6, it turns downward and the rider boards immediately.

We keep one direction value for the lift and determine each request's direction separately.

- A request is upward when `destinationFloor > startFloor`.
- A request is downward when `destinationFloor < startFloor`.
- The lift starts at floor 0 in the idle state.

If the lift is already moving in the same direction as a new request, the request is rejected when its start floor has already been passed. A request in the opposite direction can be stored for a future sweep.

## 3. Keep the Mutable State Together

The `LiftState` dataclass stores the values that change while the lift operates:

- `current_floor`.
- `people_inside`.
- `active_requests`.
- `direction`.

This is a small organizational helper rather than a separate design pattern. The public `LiftSystem` class still owns all elevator behavior.

## 4. Store Waiting Riders by Floor and Direction

For every floor, we keep two FIFO queues:

- `up_waiting` contains the destinations of riders who want to go up.
- `down_waiting` contains the destinations of riders who want to go down.

Python's `deque` supports efficient insertion at the back with `append()` and removal from the front with `popleft()`. Riders waiting at the same floor and in the same direction are therefore considered in request order.

We also use two integer lists:

- `up_dropoffs[floor]` counts upward riders currently inside who will leave there.
- `down_dropoffs[floor]` counts downward riders currently inside who will leave there.

A drop-off is added only when its rider boards. This is important because a rider left behind by a full car is still waiting and must not be counted as a passenger.

## 5. Distinguish Immediate Boarding From Future Pickups

The two reported test cases distinguish requests made at the current floor from requests made for a future pickup. The capacity sentence in the written statement does not spell out this distinction, so we use both test expectations when implementing it.

### A full car rejects a new immediate boarding request

Suppose the lift starts at floor 0 with capacity 2.

- `addRequest(0, 5)` returns `True` and boards the first rider.
- `addRequest(0, 6)` returns `True` and boards the second rider.
- `addRequest(0, 7)` returns `False` because the lift is full at the rider's floor and is traveling in the rider's direction.

The rejected request must not be added to a queue. After the two accepted trips finish, the lift is idle at floor 6.

The immediate capacity check applies only when all three conditions hold:

- The start floor equals the current floor.
- The rider's direction matches the lift's direction.
- The number of people inside has reached the capacity.

An opposite-direction rider at the current floor is not boarding immediately. That request remains subject to the normal rules for future pickups.

### An eligible request from a floor ahead can wait

Now start a fresh lift at floor 0 with capacity 2 and make these requests:

- `addRequest(0, 10)`.
- `addRequest(0, 12)`.
- `addRequest(1, 14)`.

The first two riders board at floor 0, so the car is full. The third request is still accepted because its pickup is ahead at floor 1, not an immediate boarding at floor 0.

When the lift reaches floor 1, the third rider cannot board. Their destination remains in the upward waiting queue at floor 1. After the current passengers leave, the lift returns, reaches floor 1 during a later upward pass, and boards the waiting rider.

The boarding loop stops as soon as `people_inside == lifts_capacity`. It never removes accepted riders who could not board, so no accepted request is lost. Do not run the new-request rejection check again when the lift reaches an already accepted rider.

## 6. Processing `addRequest()`

The method follows these steps.

1. Find the rider's requested direction.
2. Reject the request if the lift is moving in that same direction and has already passed the start floor.
3. Reject a same-direction request at the current floor if the car is full.
4. Add the destination to the correct waiting queue at the start floor.
5. If the lift is idle, start moving toward the pickup floor.
6. If the rider is at the current floor and the directions match, board immediately.

Both rejection checks run before changing a queue or the request count. A full car does not reject an otherwise eligible future pickup just because it is full now.

## 7. Processing a Floor

When the lift processes a floor, it handles events in this order:

1. Riders whose destination is the current floor leave.
2. Free seats are now available.
3. Waiting riders whose requested direction matches the lift board in FIFO order.
4. Boarding stops when either the queue is empty or the lift reaches capacity.

Handling drop-offs before pickups allows a seat to be reused immediately at the same floor.

## 8. Processing One Tick

One call to `tick()` performs these actions:

1. An idle lift stays where it is.
2. A moving lift advances exactly one floor.
3. Drop-offs and pickups are processed at the arrival floor.
4. The lift checks whether any waiting pickup or active drop-off remains ahead.
5. If no event remains ahead, the lift either becomes idle or reverses direction.

When the lift reverses at a floor, riders waiting there in the new direction board immediately. No extra tick is needed.

The lift continues in its current direction while any event exists ahead. This includes an opposite-direction pickup. For example, an empty lift moving up toward a downward request at floor 6 must still reach floor 6 before it can reverse.

## 9. Why a Full State Pattern Is Not Needed

The State pattern could create separate `UpState`, `DownState`, and `IdleState` classes. That would be useful in a larger elevator system where every state has many rules.

Here, those classes would mostly wrap a few conditions and queue operations. A single direction value with small helper methods gives the same clear behavior with less code. The result is still a state machine without unnecessary class structure.

## 10. Correctness Argument

### The lift never exceeds its capacity

Riders board only while `people_inside < lifts_capacity`. Every successful boarding increases `people_inside` by one, and the loop stops when the capacity is reached. Therefore, the number of passengers can never exceed the lift's capacity.

### A rejected request does not change the system

The passed-floor and immediate-capacity checks both run before enqueuing the request or increasing `active_requests`. Therefore, a rejected rider cannot affect later stops, passenger counts, or the decision to become idle.

### An accepted rider left behind is not lost

A waiting destination is removed from its queue only when a seat is available. If the car is full, the destination stays in the queue. The waiting pickup remains an active event, so it can be served during a later sweep. Once new requests stop arriving, the finite queues drain over successive sweeps.

### Riders leave at the correct destination

When a rider boards, one drop-off is added at that rider's destination. The rider count is reduced only when the lift reaches that destination while traveling in the matching direction. Therefore, every boarded rider leaves at the requested floor.

### The lift does not reverse too early

Before reversing, the lift scans all floors ahead for a waiting pickup or active drop-off. It keeps moving whenever such an event exists. Therefore, it cannot turn around while an accepted request still requires it to visit a floor ahead.

### The reported state is correct

`current_floor` changes by exactly one during each active tick. `people_inside` is decreased for drop-offs and increased only for successful boardings. The direction changes only after all work ahead is finished. Therefore, `getLiftState()` reports the correct floor, direction, and passenger count.

## 11. Complexity Analysis

Let `F` be the number of floors, `C` be the lift capacity, and `R` be the number of waiting requests.

- `addRequest()` takes `O(C)` time in the worst case when it runs the boarding loop. Otherwise, it takes `O(1)` amortized time.
- `tick()` takes `O(F + C)` time in the worst case. It may scan the floors ahead and board at most `C` riders.
- `getLiftState()` takes `O(1)` time.
- The total extra space is `O(F + R)`.

Since the building has at most 200 floors, scanning the floors keeps the code simple and fast enough.

## 12. Python Solution

```python
from collections import deque
from dataclasses import dataclass
from typing import Deque, List


@dataclass
class LiftState:
    current_floor: int = 0
    people_inside: int = 0
    active_requests: int = 0
    direction: str = "I"


class LiftSystem:
    UP = "U"
    DOWN = "D"
    IDLE = "I"

    def __init__(self, floors, liftsCapacity):
        self.floors = floors
        self.lifts_capacity = liftsCapacity

        self.up_waiting = self._create_waiting_queues(floors)
        self.down_waiting = self._create_waiting_queues(floors)
        self.up_dropoffs = [0] * floors
        self.down_dropoffs = [0] * floors

        self.state = LiftState()

    def addRequest(self, startFloor, destinationFloor):
        request_direction = (
            self.UP if destinationFloor > startFloor else self.DOWN
        )

        if self._has_already_passed(startFloor, request_direction):
            return False

        # A same-direction rider at this floor needs a seat immediately.
        # Reject before changing any queue or request count.
        if (
            startFloor == self.state.current_floor
            and self.state.direction == request_direction
            and self.state.people_inside >= self.lifts_capacity
        ):
            return False

        if request_direction == self.UP:
            self.up_waiting[startFloor].append(destinationFloor)
        else:
            self.down_waiting[startFloor].append(destinationFloor)
        self.state.active_requests += 1

        if self.state.direction == self.IDLE:
            if startFloor > self.state.current_floor:
                self.state.direction = self.UP
            elif startFloor < self.state.current_floor:
                self.state.direction = self.DOWN
            else:
                self.state.direction = request_direction

        # A rider at the current floor boards immediately only when the lift
        # is traveling in the rider's requested direction.
        if (
            startFloor == self.state.current_floor
            and self.state.direction == request_direction
        ):
            self._process_current_floor()

        return True

    def getLiftState(self):
        return (
            f"{self.state.current_floor}-"
            f"{self.state.direction}-"
            f"{self.state.people_inside}"
        )

    def tick(self):
        if self.state.direction == self.IDLE:
            return

        if self.state.direction == self.UP:
            self.state.current_floor += 1
        else:
            self.state.current_floor -= 1

        self._process_current_floor()
        self._update_direction()

    def _has_already_passed(self, start_floor, request_direction):
        if self.state.direction != request_direction:
            return False
        if self.state.direction == self.UP:
            return start_floor < self.state.current_floor
        return start_floor > self.state.current_floor

    def _process_current_floor(self):
        current_floor = self.state.current_floor

        if self.state.direction == self.UP:
            leaving = self.up_dropoffs[current_floor]
            self.state.people_inside -= leaving
            self.state.active_requests -= leaving
            self.up_dropoffs[current_floor] = 0
            self._board_waiting_riders(
                self.up_waiting[current_floor],
                self.up_dropoffs,
            )
        elif self.state.direction == self.DOWN:
            leaving = self.down_dropoffs[current_floor]
            self.state.people_inside -= leaving
            self.state.active_requests -= leaving
            self.down_dropoffs[current_floor] = 0
            self._board_waiting_riders(
                self.down_waiting[current_floor],
                self.down_dropoffs,
            )

    def _board_waiting_riders(
        self,
        waiting_riders: Deque[int],
        dropoffs: List[int],
    ):
        while (
            self.state.people_inside < self.lifts_capacity
            and waiting_riders
        ):
            destination_floor = waiting_riders.popleft()
            self.state.people_inside += 1
            dropoffs[destination_floor] += 1

    def _update_direction(self):
        if self._has_work_ahead():
            return

        if self.state.active_requests == 0:
            self.state.direction = self.IDLE
            return

        if self.state.direction == self.UP:
            self.state.direction = self.DOWN
        else:
            self.state.direction = self.UP

        # A rider waiting at the turning floor boards without another tick.
        self._process_current_floor()

    def _has_work_ahead(self):
        step = 1 if self.state.direction == self.UP else -1
        floor = self.state.current_floor + step

        while 0 <= floor < self.floors:
            if self._has_event_at(floor):
                return True
            floor += step

        return False

    def _has_event_at(self, floor):
        return (
            bool(self.up_waiting[floor])
            or self.up_dropoffs[floor] > 0
            or bool(self.down_waiting[floor])
            or self.down_dropoffs[floor] > 0
        )

    @staticmethod
    def _create_waiting_queues(floors):
        return [deque() for _ in range(floors)]
```

## 13. Short Walkthrough

Consider a lift with capacity 2.

- `addRequest(0, 10)` boards the first rider.
- `addRequest(0, 12)` boards the second rider, making the lift full.
- `addRequest(1, 14)` returns `True` and adds the third rider to the upward queue at floor 1.
- At floor 1, the car is full, so the third rider remains queued.
- At floors 10 and 12, the two passengers leave.
- With no event above floor 12, the lift reverses and returns toward floor 1.
- At floor 1, it changes to the upward direction and boards the waiting rider.
- The lift then travels to floor 14 and completes the final request.

This behavior preserves an accepted future pickup when the car is full, while the separate admission check rejects a new immediate boarding request if no seat is available.
