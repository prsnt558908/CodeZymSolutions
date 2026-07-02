# Design Elevator System in Python Using State Pattern

**Problem Statement:** https://codezym.com/question/11-design-simple-elevator-system-multiple-lifts

Elevator system design is one of the common low level design problems asked in interviews. It looks simple in the beginning, but it becomes complex when we start handling multiple lifts, multiple floors, lift direction, capacity, incoming requests, outgoing requests, and time simulation.

The main idea of this solution is to keep the `Lift` class clean by moving direction-specific behavior into separate state classes. Since a lift behaves differently when it is moving up, moving down, idle, or going to pick its first passenger, this is a good use case for the **State Design Pattern**.

In this tutorial, we will build a simple elevator system in Python that can:

- Handle multiple lifts
- Assign the best lift for a user request
- Track lift direction and current floor
- Track number of people inside each lift
- Simulate lift movement using `tick()`

---

## Requirements

We have an elevator system managing multiple lifts in a building with multiple floors. Each lift has the same capacity.

### 1. Request a lift from outside

A user can press the **Up** or **Down** button outside the lift in the corridor.

```python
request_lift(floor, direction)
```

Here:

- `floor` is the floor from where the user requests the lift
- `direction` is `'U'` for up and `'D'` for down
- The method returns the assigned lift index
- If no lift is available, it returns `-1`

A user may request a lift but may not eventually enter it. It may also happen that one person requested the lift, but multiple people enter when the lift arrives.

---

### 2. Press floor button inside lift

When the assigned lift reaches the user's floor, the user enters and presses the destination floor button.

```python
press_floor_button_in_lift(lift_index, floor)
```

Every person entering the lift presses their destination floor exactly once. This helps us track the number of people inside the lift.

The platform assumes the parameters are valid:

- `lift_index` is valid
- `floor` is valid
- The lift is not already full
- The destination floor does not change the current movement direction of the lift

---

### 3. Get lift state

The system should be able to return the current state of any lift.

```python
get_lift_state(lift_index)
```

Example output:

```text
4-U-8
```

This means:

- Lift is currently at floor `4`
- Lift is moving up, represented by `U`
- There are `8` people inside the lift

---

### 4. Simulate time using tick

In real life, lift movement depends on actual time. But for this problem, we simplify it.

We assume:

- Lift takes exactly one second to move from one floor to the next
- Time taken for people to enter or exit is zero seconds
- Every call to `tick()` means one second has passed

```python
tick()
```

So every time `tick()` is called, each lift updates its state and moves to the next floor if required.

---

## Why We Need Multiple Classes

After understanding the requirements, the next step is class design.

The elevator system is basically a collection of lifts. So the most important class is:

```python
class Lift:
    pass
```

Each lift is responsible for:

- Tracking its current floor
- Tracking its current direction
- Tracking incoming requests
- Tracking outgoing destination requests
- Moving every second using `tick()`
- Deciding how much time it will take to reach a requested floor

But the behavior of these methods changes depending on the lift state.

For example:

- If lift is moving up, it can serve upward requests above its current floor
- If lift is moving down, it can serve downward requests below its current floor
- If lift is idle, it can be assigned to any valid request
- If lift is moving up only to pick the first passenger who wants to go down, then it should behave differently

This is why we use the **State Design Pattern**.

---

## State Design Pattern in This Solution

Instead of writing all logic inside one big `Lift` class, we create a base class called `LiftState`.

Each state class implements behavior for that particular state.

```python
class LiftState:
    def get_direction(self):
        pass

    def get_time_to_reach_floor(self, floor, direction):
        pass

    def count_people(self, floor, direction):
        pass

    def tick(self):
        pass
```

The `Lift` class keeps the current state object and delegates behavior to it.

```python
def get_time_to_reach_floor(self, floor, direction):
    return self.state.get_time_to_reach_floor(floor, direction)
```

This keeps the `Lift` class simple and makes the code easier to extend.

---

## Different Lift States

The lift can be in one of these states:

| State | Meaning |
|---|---|
| `IdleState` | Lift is not moving |
| `MovingUpState` | Lift is moving up with normal upward requests |
| `MovingDownState` | Lift is moving down with normal downward requests |
| `MovingUpToPickFirstState` | Lift is going up to pick the first passenger who wants to go down |
| `MovingDownToPickFirstState` | Lift is going down to pick the first passenger who wants to go up |

---

## Important Data Structures

### `incoming_requests_count`

```python
self.incoming_requests_count = set()
```

This stores floors where people are waiting outside the lift.

A `set` is enough because we only need to know whether the lift has to stop at a floor for incoming passengers.

---

### `outgoing_requests_count`

```python
self.outgoing_requests_count = defaultdict(int)
```

This stores destination floors of people already inside the lift.

Example:

```python
{
    5: 2,
    8: 1
}
```

This means:

- 2 people want to get down at floor 5
- 1 person wants to get down at floor 8

We use `defaultdict(int)` because it makes counting easier.

---

## Class Explanation

## `Solution` Class

The `Solution` class acts as the lift manager.

It stores all lifts and assigns the best lift for a request.

Important responsibilities:

- Initialize all lifts
- Handle outside lift requests
- Handle floor button presses inside lift
- Return lift state
- Call `tick()` for all lifts

When a user requests a lift, the solution checks every lift and chooses the one that can reach the user in minimum time.

```python
for i, lift in enumerate(self.lifts):
    time = lift.get_time_to_reach_floor(floor, direction)
```

If the lift cannot serve that request, it returns `-1` for time and is ignored.

---

## `Lift` Class

The `Lift` class stores the actual data of a lift.

It has:

- Current floor
- Total floors
- Capacity
- Incoming requests
- Outgoing requests
- Current state

The important point is that `Lift` does not directly contain all movement logic. Instead, it delegates state-specific behavior to the current state object.

```python
def tick(self):
    self.state.tick()
```

After every tick, if there are no incoming or outgoing requests, the lift becomes idle.

```python
if not self.outgoing_requests_count and not self.incoming_requests_count:
    self.set_state('I')
```

---

## `LiftState` Class

`LiftState` is the base class for all lift states.

It provides default behavior.

```python
class LiftState:
    def __init__(self, lift):
        self.lift = lift
```

Every state has access to the lift object, so it can read or update lift data.

---

## `MovingUpState`

This state is used when the lift is moving up normally.

It can serve only those requests where:

- Requested direction is up
- Requested floor is above or equal to current floor

```python
if direction != 'U' or floor < self.lift.get_current_floor():
    return -1
```

In `tick()`, the lift first removes the incoming request from the current floor, then moves one floor up.

```python
self.lift.set_current_floor(self.lift.get_current_floor() + 1)
```

After reaching the new floor, people whose destination is this floor are removed from the lift.

---

## `MovingDownState`

This state is used when the lift is moving down normally.

It can serve only those requests where:

- Requested direction is down
- Requested floor is below or equal to current floor

```python
if direction != 'D' or floor > self.lift.get_current_floor():
    return -1
```

In `tick()`, the lift moves one floor down.

```python
self.lift.set_current_floor(self.lift.get_current_floor() - 1)
```

---

## `IdleState`

This state is used when the lift is not moving.

An idle lift can be assigned to any request. The time taken is simply the absolute distance between current floor and requested floor.

```python
return abs(floor - self.lift.get_current_floor())
```

There is no `tick()` logic because the lift remains where it is.

---

## `MovingUpToPickFirstState`

This state is used when the lift is going up to pick its first passenger, but that passenger wants to go down.

Example:

- Lift is currently at floor 2
- A user requests lift from floor 8 to go down
- Lift must first go up to floor 8
- Then it will start moving down

Even though the lift is currently moving up, it should accept only downward requests that are not above its topmost pickup floor.

```python
if direction != 'D' or floor > next_stop:
    return -1
```

When the lift reaches the topmost pickup floor, its state changes to `MovingDownState`.

```python
if self.lift.get_current_floor() == self.next_stop():
    self.lift.set_state('D')
```

---

## `MovingDownToPickFirstState`

This is the opposite of `MovingUpToPickFirstState`.

This state is used when the lift is going down to pick its first passenger, but that passenger wants to go up.

Example:

- Lift is currently at floor 8
- A user requests lift from floor 2 to go up
- Lift first goes down to floor 2
- Then it starts moving up

When the lift reaches the lowest pickup floor, its state changes to `MovingUpState`.

```python
if self.lift.get_current_floor() == self.next_stop():
    self.lift.set_state('U')
```

---

## Complete Python Code

```python
from collections import defaultdict, deque

class Solution:
    def __init__(self):
        self.floors_count = 0
        self.lifts_count = 0
        self.lifts_capacity = 0
        self.helper = None
        self.lifts = []

    def init(self, floors, lifts, lifts_capacity, helper):
        """
        Initializes the elevator system.
        Args:
            floors (int): The number of floors in the building.
            lifts (int): The number of lifts in the system.
            lifts_capacity (int): The maximum number of people per lift.
            helper (Helper11): Helper class for printing and other actions.
        """
        self.floors_count = floors
        self.lifts_count = lifts
        self.lifts_capacity = lifts_capacity
        self.helper = helper
        self.lifts = [Lift(floors, lifts_capacity) for _ in range(lifts)]
        # self.helper.println("Lift system initialized ...")

    def request_lift(self, floor, direction):
        """
        User presses the outside UP or DOWN button outside the lift.
        Args:
            floor (int): The current floor.
            direction (str): The direction ('U' for up or 'D' for down).
        
        Returns:
            int: Index of the selected lift or -1 if no lift is available.
        """
        lift_index = -1
        time_taken = -1
        for i, lift in enumerate(self.lifts):
            time = lift.get_time_to_reach_floor(floor, direction)
            if time < 0 or lift.count_people(floor, direction) >= self.lifts_capacity:
                continue
            if time_taken < 0 or time < time_taken:
                time_taken = time
                lift_index = i
        if lift_index >= 0:
            self.lifts[lift_index].add_incoming_request(floor, direction)
        return lift_index

    def press_floor_button_in_lift(self, lift_index, floor):
        """
        User presses the floor button inside the lift.
        Args:
            lift_index (int): Index of the lift.
            floor (int): The floor button pressed.
        """
        lift = self.lifts[lift_index]
        lift.add_outgoing_request(floor, lift.get_move_direction())

    def get_lift_state(self, lift_index):
        """
        Returns the current state of the lift.
        Args:
            lift_index (int): Index of the lift.
        
        Returns:
            str: String representation of the lift's state.
        """
        if lift_index < 0 or lift_index >= len(self.lifts):
            return ""
        lift = self.lifts[lift_index]
        return f"{lift.get_current_floor()}-{lift.get_move_direction()}-{lift.get_current_people_count()}"

    def tick(self):
        """
        This method is called every second to update the lift states.
        """
        for lift in self.lifts:
            lift.tick()

class Lift:
    def __init__(self, floors, capacity):
        self.current_floor = 0
        self.floors = floors
        self.capacity = capacity
        self.incoming_requests_count = set()
        self.outgoing_requests_count = defaultdict(int)
        self.moving_up_state = MovingUpState(self)
        self.moving_down_state = MovingDownState(self)
        self.idle_state = IdleState(self)
        self.moving_up_to_pick_first = MovingUpToPickFirstState(self)
        self.moving_down_to_pick_first = MovingDownToPickFirstState(self)
        self.state = self.idle_state

    def get_current_people_count(self):
        return sum(self.outgoing_requests_count.values())

    def get_time_to_reach_floor(self, floor, direction):
        return self.state.get_time_to_reach_floor(floor, direction)

    def add_incoming_request(self, floor, direction):
        if self.state.get_direction() == 'I':
            if floor == self.current_floor:
                self.set_state(direction)
            else:
                if floor > self.current_floor:
                    self.state = self.moving_up_state if direction == 'U' else self.moving_up_to_pick_first
                else:
                    self.state = self.moving_down_state if direction == 'D' else self.moving_down_to_pick_first
        self.incoming_requests_count.add(floor)

    def add_outgoing_request(self, floor, direction):
        self.outgoing_requests_count[floor] += 1

    def count_people(self, floor, direction):
        return self.state.count_people(floor, direction)

    def get_move_direction(self):
        return self.state.get_direction()

    def get_current_floor(self):
        return self.current_floor

    def tick(self):
        self.state.tick()
        if not self.outgoing_requests_count and not self.incoming_requests_count:
            self.set_state('I')

    def set_state(self, direction):
        if direction == 'U':
            self.state = self.moving_up_state
        elif direction == 'D':
            self.state = self.moving_down_state
        else:
            self.state = self.idle_state

    def set_current_floor(self, current_floor):
        self.current_floor = current_floor


class LiftState:
    def __init__(self, lift):
        self.lift = lift

    def get_direction(self):
        return 'I'

    def get_time_to_reach_floor(self, floor, direction):
        return 0

    def count_people(self, floor, direction):
        return 0

    def tick(self):
        pass


class MovingUpState(LiftState):
    def get_direction(self):
        return 'U'

    def get_time_to_reach_floor(self, floor, direction):
        if direction != 'U' or floor < self.lift.get_current_floor():
            return -1
        return floor - self.lift.get_current_floor()

    def count_people(self, floor, direction):
        if direction != 'U':
            return 0
        return sum(v for f, v in self.lift.outgoing_requests_count.items() if f > floor)

    def tick(self):
        self.lift.incoming_requests_count.discard(self.lift.get_current_floor())
        # check if idle state has been achieved i.e. if there were requests on previous floor but no one entered
        if not self.lift.incoming_requests_count and not self.lift.outgoing_requests_count:
            return
        self.lift.set_current_floor(self.lift.get_current_floor() + 1)
        self.lift.outgoing_requests_count.pop(self.lift.get_current_floor(), None)


class MovingDownState(LiftState):
    def get_direction(self):
        return 'D'

    def get_time_to_reach_floor(self, floor, direction):
        if direction != 'D' or floor > self.lift.get_current_floor():
            return -1
        return self.lift.get_current_floor() - floor

    def count_people(self, floor, direction):
        if direction != 'D':
            return 0
        return sum(v for f, v in self.lift.outgoing_requests_count.items() if f < floor)

    def tick(self):
        self.lift.incoming_requests_count.discard(self.lift.get_current_floor())
        # check if idle state has been achieved i.e. if there were requests on previous floor but no one entered
        if not self.lift.incoming_requests_count and not self.lift.outgoing_requests_count:
            return
        self.lift.set_current_floor(self.lift.get_current_floor() - 1)
        self.lift.outgoing_requests_count.pop(self.lift.get_current_floor(), None)


class IdleState(LiftState):
    def get_direction(self):
        return 'I'

    def get_time_to_reach_floor(self, floor, direction):
        return abs(floor - self.lift.get_current_floor())


class MovingUpToPickFirstState(LiftState):
    def get_direction(self):
        return 'U'

    def get_time_to_reach_floor(self, floor, direction):
        next_stop = self.next_stop()
        if direction != 'D' or floor > next_stop:
            return -1
        return next_stop - self.lift.get_current_floor() + next_stop - floor

    def next_stop(self):
        return max(self.lift.incoming_requests_count, default=-1)

    def tick(self):
        self.lift.set_current_floor(self.lift.get_current_floor() + 1)
        if self.lift.get_current_floor() == self.next_stop():
            self.lift.set_state('D')


class MovingDownToPickFirstState(LiftState):
    def get_direction(self):
        return 'D'

    def get_time_to_reach_floor(self, floor, direction):
        next_stop = self.next_stop()
        if direction != 'U' or floor < next_stop:
            return -1
        if next_stop < 0:
            next_stop = floor
        return self.lift.get_current_floor() - next_stop + floor - next_stop

    def next_stop(self):
        return min(self.lift.incoming_requests_count, default=-1)

    def tick(self):
        self.lift.set_current_floor(self.lift.get_current_floor() - 1)
        if self.lift.get_current_floor() == self.next_stop():
            self.lift.set_state('U')
```
