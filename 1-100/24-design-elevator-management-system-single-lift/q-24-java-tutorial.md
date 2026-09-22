
# Design Elevator Management System - Single Lift in Java

#### Problem Statement

[https://codezym.com/question/24-design-elevator-management-system-single-lift](https://codezym.com/question/24-design-elevator-management-system-single-lift)


The core idea is to model the lift as a small state machine with three states, `U`, `D`, and `I`. We do not need a full State design pattern with separate classes because there is only one lift and each state has very little behavior. We keep separate waiting queues for upward and downward riders on every floor. A new same-direction request at the current floor needs a free seat immediately. An eligible future pickup can be queued. When the lift reaches a floor, it first lets riders leave and then boards as many waiting riders as its capacity allows. Already accepted riders who cannot board stay queued for a later pass.

## 1. Start With a Simple but Incomplete Approach

A first idea is to store only the number of pickups and drop-offs at every floor.

That works while every waiting rider can board immediately. It breaks when the lift is full because different waiting riders may have different destinations. If we keep only a pickup count, we no longer know which drop-off should be scheduled when one of those riders eventually boards.

Another tempting idea is to reserve a seat across every floor segment before accepting a request. That is too strict for the supplied test expectations. A request from a floor ahead can be accepted even when the car will be full on its next visit there. The rider remains waiting and boards during a later pass when space is available. This does not remove the separate capacity check for a new rider who would board immediately at the current floor.

We therefore need to keep each waiting rider's destination until that rider actually enters the lift.

## 2. Separate Lift Direction From Rider Direction

The lift's movement direction and a rider's requested direction are not always the same.

For example, suppose the lift is idle at floor 0 and receives a request from floor 6 to floor 2. The rider wants to go down, but the empty lift must first move up to floor 6. At floor 6, it turns downward and the rider boards immediately.

We keep one `direction` field for the lift and determine each request's direction separately.

- A request is upward when `destinationFloor > startFloor`.
- A request is downward when `destinationFloor < startFloor`.
- The lift starts at floor 0 in the idle state.

If the lift is already moving in the same direction as a new request, the request is rejected when its start floor has already been passed. A request in the opposite direction can be stored for a future sweep.

## 3. Store Waiting Riders by Floor and Direction

For every floor, we keep two FIFO queues:

- `upWaiting` contains the destinations of riders who want to go up.
- `downWaiting` contains the destinations of riders who want to go down.

FIFO means riders waiting at the same floor and in the same direction are considered in request order.

We also use two integer arrays:

- `upDropoffs[floor]` counts upward riders currently inside who will leave there.
- `downDropoffs[floor]` counts downward riders currently inside who will leave there.

A drop-off is added only when its rider boards. This is important because a rider left behind by a full car is still waiting and must not be counted as a passenger.

## 4. Distinguish Immediate Boarding From Future Pickups

The two reported test cases distinguish requests made at the current floor from requests made for a future pickup. The capacity sentence in the written statement does not spell out this distinction, so we use both test expectations when implementing it.

### A full car rejects a new immediate boarding request

Suppose the lift starts at floor 0 with capacity 2.

- `addRequest(0, 5)` returns `true` and boards the first rider.
- `addRequest(0, 6)` returns `true` and boards the second rider.
- `addRequest(0, 7)` returns `false` because the lift is full at the rider's floor and is traveling in the rider's direction.

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

The boarding loop stops as soon as `peopleInside == liftsCapacity`. It never removes accepted riders who could not board, so no accepted request is lost. Do not run the new-request rejection check again when the lift reaches an already accepted rider.

## 5. Processing `addRequest()`

The method follows these steps.

1. Find the rider's requested direction.
2. Reject the request if the lift is moving in that same direction and has already passed the start floor.
3. Reject a same-direction request at the current floor if the car is full.
4. Add the destination to the correct waiting queue at the start floor.
5. If the lift is idle, start moving toward the pickup floor.
6. If the rider is at the current floor and the directions match, board immediately.

Both rejection checks run before changing a queue or the request count. A full car does not reject an otherwise eligible future pickup just because it is full now.

## 6. Processing a Floor

When the lift processes a floor, it handles events in this order:

1. Riders whose destination is the current floor leave.
2. Free seats are now available.
3. Waiting riders whose requested direction matches the lift board in FIFO order.
4. Boarding stops when either the queue is empty or the lift reaches capacity.

Handling drop-offs before pickups allows a seat to be reused immediately at the same floor.

## 7. Processing One Tick

One call to `tick()` performs these actions:

1. An idle lift stays where it is.
2. A moving lift advances exactly one floor.
3. Drop-offs and pickups are processed at the arrival floor.
4. The lift checks whether any waiting pickup or active drop-off remains ahead.
5. If no event remains ahead, the lift either becomes idle or reverses direction.

When the lift reverses at a floor, riders waiting there in the new direction board immediately. No extra tick is needed.

The lift continues in its current direction while any event exists ahead. This includes an opposite-direction pickup. For example, an empty lift moving up toward a downward request at floor 6 must still reach floor 6 before it can reverse.

## 8. Why a Full State Pattern Is Not Needed

The State pattern could create separate `UpState`, `DownState`, and `IdleState` classes. That would be useful in a larger elevator system where every state has many rules.

Here, those classes would mostly wrap a few conditions and queue operations. A single direction field with small helper methods gives the same clear behavior with less code. The result is still a state machine without unnecessary class structure.

## 9. Correctness Argument

### The lift never exceeds its capacity

Riders board only while `peopleInside < liftsCapacity`. Every successful boarding increases `peopleInside` by one, and the loop stops when the capacity is reached. Therefore, the number of passengers can never exceed the lift's capacity.

### A rejected request does not change the system

The passed-floor and immediate-capacity checks both run before enqueuing the request or increasing `activeRequests`. Therefore, a rejected rider cannot affect later stops, passenger counts, or the decision to become idle.

### An accepted rider left behind is not lost

A waiting destination is removed from its queue only when a seat is available. If the car is full, the destination stays in the queue. The waiting pickup remains an active event, so it can be served during a later sweep. Once new requests stop arriving, the finite queues drain over successive sweeps.

### Riders leave at the correct destination

When a rider boards, one drop-off is added at that rider's destination. The rider count is reduced only when the lift reaches that destination while traveling in the matching direction. Therefore, every boarded rider leaves at the requested floor.

### The lift does not reverse too early

Before reversing, the lift scans all floors ahead for a waiting pickup or active drop-off. It keeps moving whenever such an event exists. Therefore, it cannot turn around while an accepted request still requires it to visit a floor ahead.

### The reported state is correct

`currentFloor` changes by exactly one during each active tick. `peopleInside` is decreased for drop-offs and increased only for successful boardings. The direction changes only after all work ahead is finished. Therefore, `getLiftState()` reports the correct floor, direction, and passenger count.

## 10. Complexity Analysis

Let `F` be the number of floors, `C` be the lift capacity, and `R` be the number of waiting requests.

- `addRequest()` has a conservative amortized bound of `O(C)` when it runs the boarding loop. Otherwise, it takes `O(1)` amortized time. Queue insertion has an amortized bound because `ArrayDeque` occasionally grows its backing array.
- `tick()` takes `O(F + C)` time in the worst case. It may scan the floors ahead and board at most `C` riders.
- `getLiftState()` takes `O(1)` time.
- The total extra space is `O(F + R)`.

Since the building has at most 200 floors, scanning the floors keeps the code simple and fast enough.

## 11. Java Solution

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class LiftSystem {
    private static final char UP = 'U';
    private static final char DOWN = 'D';
    private static final char IDLE = 'I';

    private final int floors;
    private final int liftsCapacity;

    private final List<Queue<Integer>> upWaiting;
    private final List<Queue<Integer>> downWaiting;
    private final int[] upDropoffs;
    private final int[] downDropoffs;

    private int currentFloor;
    private int peopleInside;
    private int activeRequests;
    private char direction;

    public LiftSystem(int floors, int liftsCapacity) {
        this.floors = floors;
        this.liftsCapacity = liftsCapacity;

        this.upWaiting = createWaitingQueues(floors);
        this.downWaiting = createWaitingQueues(floors);
        this.upDropoffs = new int[floors];
        this.downDropoffs = new int[floors];

        this.currentFloor = 0;
        this.peopleInside = 0;
        this.activeRequests = 0;
        this.direction = IDLE;
    }

    public boolean addRequest(int startFloor, int destinationFloor) {
        char requestDirection = destinationFloor > startFloor ? UP : DOWN;

        if (hasAlreadyPassed(startFloor, requestDirection)) {
            return false;
        }

        // A same-direction rider at this floor needs a seat immediately.
        // Reject before changing any queue or request count.
        if (startFloor == currentFloor
                && direction == requestDirection
                && peopleInside >= liftsCapacity) {
            return false;
        }

        if (requestDirection == UP) {
            upWaiting.get(startFloor).offer(destinationFloor);
        } else {
            downWaiting.get(startFloor).offer(destinationFloor);
        }
        activeRequests++;

        if (direction == IDLE) {
            if (startFloor > currentFloor) {
                direction = UP;
            } else if (startFloor < currentFloor) {
                direction = DOWN;
            } else {
                direction = requestDirection;
            }
        }

        // A rider at the current floor boards immediately only when the lift
        // is traveling in the rider's requested direction.
        if (startFloor == currentFloor && direction == requestDirection) {
            processCurrentFloor();
        }

        return true;
    }

    public String getLiftState() {
        return currentFloor + "-" + direction + "-" + peopleInside;
    }

    public void tick() {
        if (direction == IDLE) {
            return;
        }

        currentFloor += direction == UP ? 1 : -1;
        processCurrentFloor();
        updateDirection();
    }

    private boolean hasAlreadyPassed(int startFloor, char requestDirection) {
        if (direction != requestDirection) {
            return false;
        }
        if (direction == UP) {
            return startFloor < currentFloor;
        }
        return startFloor > currentFloor;
    }

    private void processCurrentFloor() {
        if (direction == UP) {
            peopleInside -= upDropoffs[currentFloor];
            activeRequests -= upDropoffs[currentFloor];
            upDropoffs[currentFloor] = 0;
            boardWaitingRiders(upWaiting.get(currentFloor), upDropoffs);
        } else if (direction == DOWN) {
            peopleInside -= downDropoffs[currentFloor];
            activeRequests -= downDropoffs[currentFloor];
            downDropoffs[currentFloor] = 0;
            boardWaitingRiders(downWaiting.get(currentFloor), downDropoffs);
        }
    }

    private void boardWaitingRiders(
            Queue<Integer> waitingRiders,
            int[] dropoffs) {
        while (peopleInside < liftsCapacity && !waitingRiders.isEmpty()) {
            int destinationFloor = waitingRiders.remove();
            peopleInside++;
            dropoffs[destinationFloor]++;
        }
    }

    private void updateDirection() {
        if (hasWorkAhead()) {
            return;
        }

        if (activeRequests == 0) {
            direction = IDLE;
            return;
        }

        direction = direction == UP ? DOWN : UP;

        // A rider waiting at the turning floor boards without another tick.
        processCurrentFloor();
    }

    private boolean hasWorkAhead() {
        int step = direction == UP ? 1 : -1;
        for (int floor = currentFloor + step;
                floor >= 0 && floor < floors;
                floor += step) {
            if (hasEventAt(floor)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEventAt(int floor) {
        return !upWaiting.get(floor).isEmpty()
                || upDropoffs[floor] > 0
                || !downWaiting.get(floor).isEmpty()
                || downDropoffs[floor] > 0;
    }

    private List<Queue<Integer>> createWaitingQueues(int floors) {
        List<Queue<Integer>> waiting = new ArrayList<>(floors);
        for (int floor = 0; floor < floors; floor++) {
            waiting.add(new ArrayDeque<>());
        }
        return waiting;
    }
}
```

## 12. Short Walkthrough

Consider a lift with capacity 2.

- `addRequest(0, 10)` boards the first rider.
- `addRequest(0, 12)` boards the second rider, making the lift full.
- `addRequest(1, 14)` returns `true` and adds the third rider to the upward queue at floor 1.
- At floor 1, the car is full, so the third rider remains queued.
- At floors 10 and 12, the two passengers leave.
- With no event above floor 12, the lift reverses and returns toward floor 1.
- At floor 1, it changes to the upward direction and boards the waiting rider.
- The lift then travels to floor 14 and completes the final request.

This behavior preserves an accepted future pickup when the car is full, while the separate admission check rejects a new immediate boarding request if no seat is available.
