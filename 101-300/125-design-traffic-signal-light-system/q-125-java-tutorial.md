# Design a Traffic Signal Light System in Java

#### Problem Statement
[https://codezym.com/question/125-design-traffic-signal-light-system](https://codezym.com/question/125-design-traffic-signal-light-system)

## Core Idea

This problem is a small simulation that moves forward in time.

Every method call brings a `currentTime`. Before answering, the system first catches up to that time.

We do not run a clock that ticks every second. Once the first road is open, a new road is picked only when a green phase ends. So we jump straight from one phase end to the next.

For each road we store its waiting vehicles and the time it last turned red. Then `redTime = now - redSince`.

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

So we put the rule behind an interface, `RoadSelectionStrategy`. `HighestPriorityStrategy` implements our formula.

A new rule is just a new class. The time handling code stays untouched.

Could we write the formula directly inside `TrafficLightSystem`? Yes, it works.

But then one class mixes time handling with the selection rule, and every rule change touches the tricky timing code. One small interface is a cheap price for keeping them apart.

### Why not the State pattern?

Traffic lights are the textbook State pattern example. But here a road is only green or red, and it does not decide its own next state. The controller decides by comparing all roads.

Separate state classes would add code but remove no `if-else`. A simple `isGreen` flag is enough.

### Why not the Observer pattern?

Observer helps when listeners must react to an event right away. Here nothing is listening. Every answer is computed lazily when a method is called, so Observer adds wiring with no benefit.

---

## Solution 1: Jump From Phase to Phase

### The naive idea

A first idea is a loop that runs once per second. It adds `1` to every red road's `redTime` and checks if the green phase is over.

This does a lot of useless work, because no road is picked between two phase ends. And `currentTime` can jump by billions of seconds.

### The better idea

Store `redSince`, the time a road turned red, instead of a running timer. Then `redTime = time - redSince` at any moment, in O(1).

Now we only stop at phase ends. At each phase end we pick the next green road. Nothing else needs to happen.

### Classes and data structures

**`Road`** keeps one road's data together: `id`, `waitingVehicles`, `redSince` and `isGreen`. `getRedTime(time)` returns `0` for the green road, else `time - redSince`.

**`RoadSelectionStrategy`** is the strategy interface, with one method `pickRoad(roads, time)`.

**`HighestPriorityStrategy`** computes `waitingVehicles * 2 + redTime` for every road and returns the best one. On a tie it compares road ids.

**`TrafficLightSystem`** is the controller. Its key fields:

- `Map<String, Road> roads`: `vehicleArrived` finds a road by id in O(1). We also loop over its values to compare priorities.
- `greenRoad` and `greenEndTime`: the current green road and when its phase ends. `greenEndTime` is the only moment when anything can change.
- `started`: set on the first call, so all roads count as red from that time.
- `anyVehicleArrived`: the first road opens only after vehicles arrive.

We use `long` for vehicle counts and times. Many arrivals of `100000` vehicles can overflow an `int`, and `greenEndTime` can go past `2147483647`.

### How it works

**`advanceTo(time)`** catches up with time. On the very first call it sets `redSince = time` for every road. Then, while the current phase ends at or before `time`, it calls `switchGreen(greenEndTime)`.

**`switchGreen(time)`** asks the strategy for the winner at `time`.

- If the winner is a different road, the old green road turns red with `redSince = time`.
- The winner turns green and its waiting vehicles become `0`.
- The new phase ends at `time + minGreenTime`.

If the same road wins again, it simply stays green for one more phase.

**`openFirstRoadIfNeeded(time)`** is called only from the get methods. It opens the first road if vehicles have arrived and nothing is green yet.

**`getNextGreenRoad`** calls `pickRoad` at `greenEndTime` but changes nothing.

At that moment the green road's red time is `0`, and every red road has `greenEndTime - redSince`. That is exactly what the real switch will see.

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

```java
import java.util.*;

/**
 * One incoming road of the intersection.
 * We store the time it turned red, not a running red timer.
 * So redTime can be calculated in O(1) whenever we need it.
 */
class Road {
    String id;
    long waitingVehicles = 0;
    long redSince = 0;        // time when this road last turned red
    boolean isGreen = false;

    Road(String id) {
        this.id = id;
    }

    // Seconds this road has been continuously red at 'time'. A green road has 0.
    long getRedTime(long time) {
        return isGreen ? 0 : time - redSince;
    }
}

/**
 * Strategy pattern: the rule that decides which road gets the green light next.
 * The timing logic in TrafficLightSystem does not care how this rule works.
 */
interface RoadSelectionStrategy {
    Road pickRoad(Collection<Road> roads, long time);
}

/**
 * priority = waitingVehicles * 2 + redTime
 * On a tie, the lexicographically smaller road id wins.
 */
class HighestPriorityStrategy implements RoadSelectionStrategy {
    public Road pickRoad(Collection<Road> roads, long time) {
        Road best = null;
        long bestPriority = 0;
        for (Road road : roads) {
            long priority = road.waitingVehicles * 2 + road.getRedTime(time);
            if (best == null || priority > bestPriority
                    || (priority == bestPriority && road.id.compareTo(best.id) < 0)) {
                best = road;
                bestPriority = priority;
            }
        }
        return best;
    }
}

public class TrafficLightSystem {
    Map<String, Road> roads = new HashMap<>();   // road id -> road
    int minGreenTime;
    RoadSelectionStrategy strategy = new HighestPriorityStrategy();

    boolean started = false;            // true after the first timestamped call
    boolean anyVehicleArrived = false;  // the first road opens only after vehicles arrive
    Road greenRoad = null;              // null until the first road opens
    long greenEndTime = 0;              // time when the current green phase ends

    public TrafficLightSystem(List<String> roadIds, int minGreenTimeSeconds) {
        for (String id : roadIds) {
            roads.put(id, new Road(id));
        }
        this.minGreenTime = minGreenTimeSeconds;
    }

    public void vehicleArrived(String roadId, int vehiclesCount, int currentTime) {
        advanceTo(currentTime);
        roads.get(roadId).waitingVehicles += vehiclesCount;
        anyVehicleArrived = true;
    }

    public String getCurrentGreenRoad(int currentTime) {
        advanceTo(currentTime);
        openFirstRoadIfNeeded(currentTime);
        return greenRoad == null ? "" : greenRoad.id;
    }

    public String getNextGreenRoad(int currentTime) {
        advanceTo(currentTime);
        openFirstRoadIfNeeded(currentTime);
        if (greenRoad == null) return "";
        // Run the same rule at the next reevaluation time, without changing any state
        return strategy.pickRoad(roads.values(), greenEndTime).id;
    }

    public int getRemainingGreenTime(int currentTime) {
        advanceTo(currentTime);
        openFirstRoadIfNeeded(currentTime);
        if (greenRoad == null) return -1;
        return (int) (greenEndTime - currentTime);
    }

    // Catch up with time: do every reevaluation that happens at or before 'time'
    void advanceTo(long time) {
        if (!started) {
            // All roads are red from the first timestamped call
            started = true;
            for (Road road : roads.values()) {
                road.redSince = time;
            }
        }
        while (greenRoad != null && greenEndTime <= time) {
            switchGreen(greenEndTime);
        }
    }

    // Only the get methods open the first road, and only after some vehicles arrived
    void openFirstRoadIfNeeded(long time) {
        if (greenRoad == null && anyVehicleArrived) {
            switchGreen(time);
        }
    }

    // Reevaluate priorities at 'time' and give green to the winner for minGreenTime seconds
    void switchGreen(long time) {
        Road next = strategy.pickRoad(roads.values(), time);
        if (greenRoad != null && greenRoad != next) {
            greenRoad.isGreen = false;
            greenRoad.redSince = time;   // old green road starts its red time now
        }
        next.isGreen = true;
        next.waitingVehicles = 0;        // vehicles counted so far get to pass
        greenRoad = next;
        greenEndTime = time + minGreenTime;
    }
}
```

### Complexity

Let `R` be the number of roads (at most `6`).

- `pickRoad` and `switchGreen` take O(R).
- A call does one switch for every phase that ended since the previous call.

**The problem:** `currentTime` can jump from `0` to `2147483647` with `minGreenTimeSeconds = 1`. That is about 2 billion switches inside a single call. Far too slow.

---

## Solution 2: Skip Idle Rounds

### Key observation

Look at what happens when no road has waiting vehicles.

Priority becomes just `redTime`. So the road that has been red the longest always wins. The road that just finished green has the smallest red time, so it goes to the back.

That is a simple queue. With roads `A`, `B`, `C` the lights go, for example, `A, B, C, A, B, C, ...`

One full round takes `R * minGreenTimeSeconds` seconds.

### Why wait for one full round first?

When traffic has just stopped, red times can be uneven. One road may have been red for 500 seconds while busy roads kept winning.

After every road gets green once, red times fall into the same pattern in every round. From then on, each round is an exact copy of the previous one, only shifted in time.

We must not jump earlier. Otherwise a road could keep its old 500 seconds of red time, even though it really got green in between. The next arrival would then pick the wrong road.

### How we jump

`idleSwitches` counts switches in a row made while no vehicle was waiting anywhere. Any arrival resets it to `0`.

Once `idleSwitches >= R`, one full idle round is done. So `advanceTo` can jump over every full round that fits before `time`:

```
roundTime = R * minGreenTime
shift     = (time - greenEndTime) / roundTime * roundTime
```

We add `shift` to `greenEndTime` and to every road's `redSince`.

Why is this safe?

- Red times at a phase end are `greenEndTime - redSince`. Both values move by the same `shift`, so every red time stays the same.
- Waiting vehicles are all `0`.
- The green road is the same, because we skipped whole rounds.

So the system is in exactly the same situation, just later. After the jump, fewer than `R` normal switches are left.

Note: this shortcut relies on our priority rule. If you plug in a different strategy, check that the idle rotation still holds.

### Code

```java
import java.util.*;

/**
 * One incoming road of the intersection.
 * We store the time it turned red, not a running red timer.
 * So redTime can be calculated in O(1) whenever we need it.
 */
class Road {
    String id;
    long waitingVehicles = 0;
    long redSince = 0;        // time when this road last turned red
    boolean isGreen = false;

    Road(String id) {
        this.id = id;
    }

    // Seconds this road has been continuously red at 'time'. A green road has 0.
    long getRedTime(long time) {
        return isGreen ? 0 : time - redSince;
    }
}

/**
 * Strategy pattern: the rule that decides which road gets the green light next.
 * The timing logic in TrafficLightSystem does not care how this rule works.
 */
interface RoadSelectionStrategy {
    Road pickRoad(Collection<Road> roads, long time);
}

/**
 * priority = waitingVehicles * 2 + redTime
 * On a tie, the lexicographically smaller road id wins.
 */
class HighestPriorityStrategy implements RoadSelectionStrategy {
    public Road pickRoad(Collection<Road> roads, long time) {
        Road best = null;
        long bestPriority = 0;
        for (Road road : roads) {
            long priority = road.waitingVehicles * 2 + road.getRedTime(time);
            if (best == null || priority > bestPriority
                    || (priority == bestPriority && road.id.compareTo(best.id) < 0)) {
                best = road;
                bestPriority = priority;
            }
        }
        return best;
    }
}

public class TrafficLightSystem {
    Map<String, Road> roads = new HashMap<>();   // road id -> road
    int minGreenTime;
    RoadSelectionStrategy strategy = new HighestPriorityStrategy();

    boolean started = false;            // true after the first timestamped call
    boolean anyVehicleArrived = false;  // the first road opens only after vehicles arrive
    Road greenRoad = null;              // null until the first road opens
    long greenEndTime = 0;              // time when the current green phase ends
    int idleSwitches = 0;               // switches in a row made while no vehicle was waiting

    public TrafficLightSystem(List<String> roadIds, int minGreenTimeSeconds) {
        for (String id : roadIds) {
            roads.put(id, new Road(id));
        }
        this.minGreenTime = minGreenTimeSeconds;
    }

    public void vehicleArrived(String roadId, int vehiclesCount, int currentTime) {
        advanceTo(currentTime);
        roads.get(roadId).waitingVehicles += vehiclesCount;
        anyVehicleArrived = true;
        idleSwitches = 0;   // traffic is back, the fixed rotation is broken
    }

    public String getCurrentGreenRoad(int currentTime) {
        advanceTo(currentTime);
        openFirstRoadIfNeeded(currentTime);
        return greenRoad == null ? "" : greenRoad.id;
    }

    public String getNextGreenRoad(int currentTime) {
        advanceTo(currentTime);
        openFirstRoadIfNeeded(currentTime);
        if (greenRoad == null) return "";
        // Run the same rule at the next reevaluation time, without changing any state
        return strategy.pickRoad(roads.values(), greenEndTime).id;
    }

    public int getRemainingGreenTime(int currentTime) {
        advanceTo(currentTime);
        openFirstRoadIfNeeded(currentTime);
        if (greenRoad == null) return -1;
        return (int) (greenEndTime - currentTime);
    }

    // Catch up with time: do every reevaluation that happens at or before 'time'
    void advanceTo(long time) {
        if (!started) {
            // All roads are red from the first timestamped call
            started = true;
            for (Road road : roads.values()) {
                road.redSince = time;
            }
        }
        while (greenRoad != null && greenEndTime <= time) {
            if (idleSwitches >= roads.size()) {
                skipIdleRounds(time);
            }
            switchGreen(greenEndTime);
        }
    }

    /*
     * With no waiting vehicles, priority is just redTime.
     * So the road that has been red the longest always wins, and the lights rotate road by road.
     * After one full idle round, every next round is an exact copy, only shifted in time.
     * So we jump over all full rounds that end at or before 'time' in one step.
     */
    void skipIdleRounds(long time) {
        long roundTime = (long) roads.size() * minGreenTime;
        long shift = (time - greenEndTime) / roundTime * roundTime;
        greenEndTime += shift;
        for (Road road : roads.values()) {
            road.redSince += shift;
        }
    }

    // Only the get methods open the first road, and only after some vehicles arrived
    void openFirstRoadIfNeeded(long time) {
        if (greenRoad == null && anyVehicleArrived) {
            switchGreen(time);
        }
    }

    // Reevaluate priorities at 'time' and give green to the winner for minGreenTime seconds
    void switchGreen(long time) {
        if (noVehicleWaiting()) {
            idleSwitches++;
        } else {
            idleSwitches = 0;
        }
        Road next = strategy.pickRoad(roads.values(), time);
        if (greenRoad != null && greenRoad != next) {
            greenRoad.isGreen = false;
            greenRoad.redSince = time;   // old green road starts its red time now
        }
        next.isGreen = true;
        next.waitingVehicles = 0;        // vehicles counted so far get to pass
        greenRoad = next;
        greenEndTime = time + minGreenTime;
    }

    boolean noVehicleWaiting() {
        for (Road road : roads.values()) {
            if (road.waitingVehicles > 0) return false;
        }
        return true;
    }
}
```

### Complexity

- After the last arrival, a road with waiting vehicles gets green within about `R` switches. Every other road can beat it at most once: after getting green, that road has no waiting vehicles and a shorter red time.
- Then comes one idle round of `R` switches, one jump, and fewer than `R` more switches.
- So each call does O(R) switches of O(R) work each: **O(R²) per call**. With `R <= 6`, that is constant time, no matter how far time jumps.
- Space: **O(R)**.