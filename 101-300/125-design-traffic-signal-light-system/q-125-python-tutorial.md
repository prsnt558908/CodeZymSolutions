# Design a Traffic Signal Light System in Python

#### Problem Statement
[https://codezym.com/question/125-design-traffic-signal-light-system](https://codezym.com/question/125-design-traffic-signal-light-system)

## Core Idea

This problem is a small simulation that moves forward in time.

Every method call brings a `currentTime`. Before answering, the system first catches up to that time.

We do not run a clock that ticks every second. Once the first road is open, a new road is picked only when a green phase ends. So we jump straight from one phase end to the next.

For each road we store its waiting vehicles and the time it last turned red. Then `red_time = now - red_since`.

The rule that picks the next green road lives in its own class, using the **Strategy** pattern. It is the one pattern that really helps here: the timing code never changes, even if the priority rule does.

State and Observer sound natural for traffic lights, but here they only add extra classes.

We build the solution in two steps:

1. **Solution 1:** jump from one phase end to the next. Simple and correct, but slow when time jumps very far.
2. **Solution 2:** when no vehicles are waiting, the lights rotate in a fixed loop. So we skip whole loops at once.

---

## Understanding the Rules

- `priority = waitingVehicles * 2 + redTime`. On a tie, the lexicographically smaller road id wins.

- A green road has `redTime = 0`. When a road turns green, its `waitingVehicles` also becomes `0`.

- Vehicles can arrive on the green road too. They count at the next reevaluation.

- Before handling a call, we finish every green phase that ends at or before `currentTime`. So a phase that ends exactly at `currentTime` switches first.

- `vehicleArrived` never opens a road. In Example 1, vehicles arrive at `100`, `105` and `108`, but the first road opens at `110`, inside `getCurrentGreenRoad`. So the first get call opens the first road, once some vehicles have arrived.

- If a get method is called before any vehicle has arrived, there is no green road yet. We return `""` for a road id and `-1` for the remaining time.

- `getNextGreenRoad` runs the same selection rule at the end of the current phase, without changing anything.

---

## Choosing the Design Pattern

The system does two separate jobs:

1. **When** to switch: tracking time and phase ends.
2. **Whom** to pick: comparing priorities.

### Strategy Pattern (used)

The "whom to pick" rule is the part most likely to change. Real systems use many rules: plain round robin, emergency vehicles first, rush hour weights and so on.

So we put the rule behind a base class, `RoadSelectionStrategy`. `HighestPriorityStrategy` implements our formula.

A new rule is just a new class. The time handling code stays untouched.

Could we write the formula directly inside `TrafficLightSystem`? Yes, it works.

But then one class mixes time handling with the selection rule, and every rule change touches the tricky timing code. One small base class is a cheap price for keeping them apart.

### Why not the State pattern?

Traffic lights are the textbook State pattern example. But here a road is only green or red, and it does not decide its own next state. The controller decides by comparing all roads.

Separate state classes would add code but remove no `if-else`. A simple `is_green` flag is enough.

### Why not the Observer pattern?

Observer helps when listeners must react to an event right away. Here nothing is listening. Every answer is computed lazily when a method is called, so Observer adds wiring with no benefit.

---

## Solution 1: Jump From Phase to Phase

### The naive idea

A first idea is a loop that runs once per second. It adds `1` to the red time of every red road and checks if the green phase is over.

This does a lot of useless work, because no road is picked between two phase ends. And `currentTime` can jump by billions of seconds.

### The better idea

Store `red_since`, the time a road turned red, instead of a running timer. Then `red_time = time - red_since` at any moment, in O(1).

Now we only stop at phase ends. At each phase end we pick the next green road. Nothing else needs to happen.

### Classes and data structures

**`Road`** keeps one road's data together: `id`, `waiting_vehicles`, `red_since` and `is_green`. `get_red_time(time)` returns `0` for the green road, else `time - red_since`.

**`RoadSelectionStrategy`** is the strategy base class, with one method `pick_road(roads, time)`.

**`HighestPriorityStrategy`** computes `waiting_vehicles * 2 + red_time` for every road and returns the best one. On a tie it compares road ids.

**`TrafficLightSystem`** is the controller. Its key fields:

- `roads`, a dict from road id to `Road`: `vehicleArrived` finds a road by id in O(1). We also loop over its values to compare priorities.
- `green_road` and `green_end_time`: the current green road and when its phase ends. `green_end_time` is the only moment when anything can change.
- `started`: set on the first call, so all roads count as red from that time.
- `any_vehicle_arrived`: the first road opens only after vehicles arrive.

### How it works

**`advance_to(time)`** catches up with time. On the very first call it sets `red_since = time` for every road. Then, while the current phase ends at or before `time`, it calls `switch_green(green_end_time)`.

**`switch_green(time)`** asks the strategy for the winner at `time`.

- If the winner is a different road, the old green road turns red with `red_since = time`.
- The winner turns green and its waiting vehicles become `0`.
- The new phase ends at `time + min_green_time`.

If the same road wins again, it simply stays green for one more phase.

**`open_first_road_if_needed(time)`** is called only from the get methods. It opens the first road if vehicles have arrived and nothing is green yet.

**`getNextGreenRoad`** calls `pick_road` at `green_end_time` but changes nothing.

At that moment the green road's red time is `0`, and every red road has `green_end_time - red_since`. That is exactly what the real switch will see.

### Dry run (Example 3)

Roads `A`, `B`, `C`, and `minGreenTimeSeconds = 10`.

| Call | What happens | Output |
|---|---|---|
| `vehicleArrived(A, 20, 200)` | First call. All roads are red from `200`. A has 20 waiting. | - |
| `vehicleArrived(B, 1, 200)` | B has 1 waiting. | - |
| `getCurrentGreenRoad(205)` | First road opens at `205`. A = 45, B = 7, C = 5. A turns green and its waiting becomes 0. Phase ends at `215`. | `"A"` |
| `vehicleArrived(A, 30, 210)` | A is green, but these 30 vehicles count at the next switch. | - |
| `getNextGreenRoad(212)` | Priorities at `215`: A = 60, B = 17, C = 15. | `"A"` |
| `getRemainingGreenTime(220)` | Catch up: switch at `215`, A wins again. Phase now ends at `225`. | `5` |

### Code

```python
class Road:
    """
    One incoming road of the intersection.
    We store the time it turned red, not a running red timer.
    So red time can be calculated in O(1) whenever we need it.
    """

    def __init__(self, road_id):
        self.id = road_id
        self.waiting_vehicles = 0
        self.red_since = 0        # time when this road last turned red
        self.is_green = False

    def get_red_time(self, time):
        """Seconds this road has been continuously red at 'time'. A green road has 0."""
        return 0 if self.is_green else time - self.red_since


class RoadSelectionStrategy:
    """
    Strategy pattern: the rule that decides which road gets the green light next.
    The timing logic in TrafficLightSystem does not care how this rule works.
    """

    def pick_road(self, roads, time):
        raise NotImplementedError


class HighestPriorityStrategy(RoadSelectionStrategy):
    """
    priority = waitingVehicles * 2 + redTime
    On a tie, the lexicographically smaller road id wins.
    """

    def pick_road(self, roads, time):
        best = None
        best_priority = 0
        for road in roads:
            priority = road.waiting_vehicles * 2 + road.get_red_time(time)
            if (best is None or priority > best_priority
                    or (priority == best_priority and road.id < best.id)):
                best = road
                best_priority = priority
        return best


class TrafficLightSystem:
    def __init__(self, roadIds, minGreenTimeSeconds):
        self.roads = {}                       # road id -> Road
        for road_id in roadIds:
            self.roads[road_id] = Road(road_id)
        self.min_green_time = minGreenTimeSeconds
        self.strategy = HighestPriorityStrategy()

        self.started = False                  # True after the first timestamped call
        self.any_vehicle_arrived = False      # the first road opens only after vehicles arrive
        self.green_road = None                # None until the first road opens
        self.green_end_time = 0               # time when the current green phase ends

    def vehicleArrived(self, roadId, vehiclesCount, currentTime):
        self.advance_to(currentTime)
        self.roads[roadId].waiting_vehicles += vehiclesCount
        self.any_vehicle_arrived = True

    def getCurrentGreenRoad(self, currentTime):
        self.advance_to(currentTime)
        self.open_first_road_if_needed(currentTime)
        return "" if self.green_road is None else self.green_road.id

    def getNextGreenRoad(self, currentTime):
        self.advance_to(currentTime)
        self.open_first_road_if_needed(currentTime)
        if self.green_road is None:
            return ""
        # Run the same rule at the next reevaluation time, without changing any state
        return self.strategy.pick_road(self.roads.values(), self.green_end_time).id

    def getRemainingGreenTime(self, currentTime):
        self.advance_to(currentTime)
        self.open_first_road_if_needed(currentTime)
        if self.green_road is None:
            return -1
        return self.green_end_time - currentTime

    def advance_to(self, time):
        """Catch up with time: do every reevaluation that happens at or before 'time'."""
        if not self.started:
            # All roads are red from the first timestamped call
            self.started = True
            for road in self.roads.values():
                road.red_since = time
        while self.green_road is not None and self.green_end_time <= time:
            self.switch_green(self.green_end_time)

    def open_first_road_if_needed(self, time):
        """Only the get methods open the first road, and only after some vehicles arrived."""
        if self.green_road is None and self.any_vehicle_arrived:
            self.switch_green(time)

    def switch_green(self, time):
        """Reevaluate priorities at 'time' and give green to the winner for min_green_time seconds."""
        next_road = self.strategy.pick_road(self.roads.values(), time)
        if self.green_road is not None and self.green_road is not next_road:
            self.green_road.is_green = False
            self.green_road.red_since = time   # old green road starts its red time now
        next_road.is_green = True
        next_road.waiting_vehicles = 0         # vehicles counted so far get to pass
        self.green_road = next_road
        self.green_end_time = time + self.min_green_time
```

### Complexity

Let `R` be the number of roads (at most `6`).

- `pick_road` and `switch_green` take O(R).
- A call does one switch for every phase that ended since the previous call.

**The problem:** `currentTime` can jump from `0` to `2147483647` with `minGreenTimeSeconds = 1`. That is about 2 billion switches inside a single call. Far too slow.

---

## Solution 2: Skip Idle Rounds

### Key observation

Look at what happens when no road has waiting vehicles.

Priority becomes just the red time. So the road that has been red the longest always wins. The road that just finished green has the smallest red time, so it goes to the back.

That is a simple queue. With roads `A`, `B`, `C` the lights go, for example, `A, B, C, A, B, C, ...`

One full round takes `R * minGreenTimeSeconds` seconds.

### Why wait for one full round first?

When traffic has just stopped, red times can be uneven. One road may have been red for 500 seconds while busy roads kept winning.

After every road gets green once, red times fall into the same pattern in every round. From then on, each round is an exact copy of the previous one, only shifted in time.

We must not jump earlier. Otherwise a road could keep its old 500 seconds of red time, even though it really got green in between. The next arrival would then pick the wrong road.

### How we jump

`idle_switches` counts switches in a row made while no vehicle was waiting anywhere. Any arrival resets it to `0`.

Once `idle_switches >= R`, one full idle round is done. So `advance_to` can jump over every full round that fits before `time`:

```
round_time = R * min_green_time
shift      = (time - green_end_time) // round_time * round_time
```

We add `shift` to `green_end_time` and to every road's `red_since`.

Why is this safe?

- Red times at a phase end are `green_end_time - red_since`. Both values move by the same `shift`, so every red time stays the same.
- Waiting vehicles are all `0`.
- The green road is the same, because we skipped whole rounds.

So the system is in exactly the same situation, just later. After the jump, fewer than `R` normal switches are left.

Note: this shortcut relies on our priority rule. If you plug in a different strategy, check that the idle rotation still holds.

### Code

```python
class Road:
    """
    One incoming road of the intersection.
    We store the time it turned red, not a running red timer.
    So red time can be calculated in O(1) whenever we need it.
    """

    def __init__(self, road_id):
        self.id = road_id
        self.waiting_vehicles = 0
        self.red_since = 0        # time when this road last turned red
        self.is_green = False

    def get_red_time(self, time):
        """Seconds this road has been continuously red at 'time'. A green road has 0."""
        return 0 if self.is_green else time - self.red_since


class RoadSelectionStrategy:
    """
    Strategy pattern: the rule that decides which road gets the green light next.
    The timing logic in TrafficLightSystem does not care how this rule works.
    """

    def pick_road(self, roads, time):
        raise NotImplementedError


class HighestPriorityStrategy(RoadSelectionStrategy):
    """
    priority = waitingVehicles * 2 + redTime
    On a tie, the lexicographically smaller road id wins.
    """

    def pick_road(self, roads, time):
        best = None
        best_priority = 0
        for road in roads:
            priority = road.waiting_vehicles * 2 + road.get_red_time(time)
            if (best is None or priority > best_priority
                    or (priority == best_priority and road.id < best.id)):
                best = road
                best_priority = priority
        return best


class TrafficLightSystem:
    def __init__(self, roadIds, minGreenTimeSeconds):
        self.roads = {}                       # road id -> Road
        for road_id in roadIds:
            self.roads[road_id] = Road(road_id)
        self.min_green_time = minGreenTimeSeconds
        self.strategy = HighestPriorityStrategy()

        self.started = False                  # True after the first timestamped call
        self.any_vehicle_arrived = False      # the first road opens only after vehicles arrive
        self.green_road = None                # None until the first road opens
        self.green_end_time = 0               # time when the current green phase ends
        self.idle_switches = 0                # switches in a row made while no vehicle was waiting

    def vehicleArrived(self, roadId, vehiclesCount, currentTime):
        self.advance_to(currentTime)
        self.roads[roadId].waiting_vehicles += vehiclesCount
        self.any_vehicle_arrived = True
        self.idle_switches = 0                # traffic is back, the fixed rotation is broken

    def getCurrentGreenRoad(self, currentTime):
        self.advance_to(currentTime)
        self.open_first_road_if_needed(currentTime)
        return "" if self.green_road is None else self.green_road.id

    def getNextGreenRoad(self, currentTime):
        self.advance_to(currentTime)
        self.open_first_road_if_needed(currentTime)
        if self.green_road is None:
            return ""
        # Run the same rule at the next reevaluation time, without changing any state
        return self.strategy.pick_road(self.roads.values(), self.green_end_time).id

    def getRemainingGreenTime(self, currentTime):
        self.advance_to(currentTime)
        self.open_first_road_if_needed(currentTime)
        if self.green_road is None:
            return -1
        return self.green_end_time - currentTime

    def advance_to(self, time):
        """Catch up with time: do every reevaluation that happens at or before 'time'."""
        if not self.started:
            # All roads are red from the first timestamped call
            self.started = True
            for road in self.roads.values():
                road.red_since = time
        while self.green_road is not None and self.green_end_time <= time:
            if self.idle_switches >= len(self.roads):
                self.skip_idle_rounds(time)
            self.switch_green(self.green_end_time)

    def skip_idle_rounds(self, time):
        """
        With no waiting vehicles, priority is just red time.
        So the road that has been red the longest always wins, and the lights rotate road by road.
        After one full idle round, every next round is an exact copy, only shifted in time.
        So we jump over all full rounds that end at or before 'time' in one step.
        """
        round_time = len(self.roads) * self.min_green_time
        shift = (time - self.green_end_time) // round_time * round_time
        self.green_end_time += shift
        for road in self.roads.values():
            road.red_since += shift

    def open_first_road_if_needed(self, time):
        """Only the get methods open the first road, and only after some vehicles arrived."""
        if self.green_road is None and self.any_vehicle_arrived:
            self.switch_green(time)

    def switch_green(self, time):
        """Reevaluate priorities at 'time' and give green to the winner for min_green_time seconds."""
        if self.no_vehicle_waiting():
            self.idle_switches += 1
        else:
            self.idle_switches = 0
        next_road = self.strategy.pick_road(self.roads.values(), time)
        if self.green_road is not None and self.green_road is not next_road:
            self.green_road.is_green = False
            self.green_road.red_since = time   # old green road starts its red time now
        next_road.is_green = True
        next_road.waiting_vehicles = 0         # vehicles counted so far get to pass
        self.green_road = next_road
        self.green_end_time = time + self.min_green_time

    def no_vehicle_waiting(self):
        return all(road.waiting_vehicles == 0 for road in self.roads.values())
```

### Complexity

- After the last arrival, a road with waiting vehicles gets green within about `R` switches. Every other road can beat it at most once: after getting green, that road has no waiting vehicles and a shorter red time.
- Then comes one idle round of `R` switches, one jump, and fewer than `R` more switches.
- So each call does O(R) switches of O(R) work each: **O(R²) per call**. With `R <= 6`, that is constant time, no matter how far time jumps.
- Space: **O(R)**.