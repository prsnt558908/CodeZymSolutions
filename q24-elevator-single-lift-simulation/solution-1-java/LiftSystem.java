import java.util.*;

/**
 * Elevator Management System — Single Lift (Java 11, fast & simple).
 *
 * Rules implemented:
 * - tick(): moves exactly one floor; on arrival, riders alight first, then boarding happens instantly.
 * - addRequest(s, d):
 *     * Reject if same-direction "already passed" (spec).
 *     * If the rider would board RIGHT NOW (same floor & correct direction/idle), require a free seat.
 *     * Otherwise accept and queue; the rider will board when the car reaches their floor in the required direction.
 * - Direction consistency: the car completes its current pass before flipping.
 * - Initial state: 0-I-0 (floor 0, Idle, empty).
 *
 * Complexity:
 * - All operations are O(1)–O(F) with F ≤ 200 (small), using arrays and per-floor queues.
 */
public class LiftSystem {

    private enum Direction { UP, DOWN, IDLE }

    private final int floors;   // floors are 0..floors-1
    private final int capacity; // max people onboard

    private int currentFloor = 0;
    private Direction direction = Direction.IDLE;
    private int onboard = 0;

    // drop[f] = number of onboard riders who will get off at floor f
    private final int[] drop;

    // Per-floor waiting queues by direction (store destination floors)
    @SuppressWarnings("unchecked")
    private final ArrayDeque<Integer>[] waitUp;
    @SuppressWarnings("unchecked")
    private final ArrayDeque<Integer>[] waitDown;

    public LiftSystem(int floors, int liftsCapacity) {
        if (floors < 2 || floors > 200) throw new IllegalArgumentException("floors out of range");
        if (liftsCapacity < 1 || liftsCapacity > 20) throw new IllegalArgumentException("capacity out of range");
        this.floors = floors;
        this.capacity = liftsCapacity;
        this.drop = new int[floors];
        this.waitUp = (ArrayDeque<Integer>[]) new ArrayDeque[floors];
        this.waitDown = (ArrayDeque<Integer>[]) new ArrayDeque[floors];
        for (int i = 0; i < floors; i++) {
            waitUp[i] = new ArrayDeque<>();
            waitDown[i] = new ArrayDeque<>();
        }
    }

    // ---------------- Public API ----------------

    public boolean addRequest(int startFloor, int destinationFloor) {
        if (!inRange(startFloor) || !inRange(destinationFloor) || startFloor == destinationFloor) return false;

        Direction reqDir = (destinationFloor > startFloor) ? Direction.UP : Direction.DOWN;

        // "Already passed" rule for same-direction requests
        if (direction == Direction.UP && reqDir == Direction.UP && startFloor < currentFloor)  return false;
        if (direction == Direction.DOWN && reqDir == Direction.DOWN && startFloor > currentFloor) return false;

        // If IDLE
        if (direction == Direction.IDLE) {
            if (startFloor == currentFloor) {
                // Will start in rider's direction; must have a seat to accept now
                if (onboard >= capacity) return false;
                direction = reqDir;
                boardNow(destinationFloor);
            } else {
                // Start moving toward caller's floor
                direction = (startFloor > currentFloor) ? Direction.UP : Direction.DOWN;
                enqueue(startFloor, destinationFloor, reqDir);
            }
            return true;
        }

        // If moving and the rider would board RIGHT NOW (same floor & same direction)
        if (reqDir == direction && startFloor == currentFloor) {
            if (onboard >= capacity) return false; // cannot board now -> reject
            boardNow(destinationFloor);
            return true;
        }

        // Otherwise, accept and queue; service will happen when we pass startFloor in reqDir
        enqueue(startFloor, destinationFloor, reqDir);
        return true;
    }

    public String getLiftState() {
        char d;
        switch (direction) {
            case UP:   d = 'U'; break;
            case DOWN: d = 'D'; break;
            default:   d = 'I';
        }
        return currentFloor + "-" + d + "-" + onboard;
    }

    public void tick() {
        // If idle, choose a direction toward the nearest work (immediate floor first)
        if (direction == Direction.IDLE) {
            if (!hasAnyWork()) return;
            direction = directionTowardNearestWork();
            if (direction == Direction.IDLE) return;
        }

        // If nothing ahead in current pass, retarget toward the nearest work
        if (!hasWorkAhead(direction)) {
            Direction nd = directionTowardNearestWork();
            if (nd == Direction.IDLE) { direction = Direction.IDLE; return; }
            direction = nd;
        }

        // Move one floor
        if (direction == Direction.UP && currentFloor < floors - 1) currentFloor++;
        else if (direction == Direction.DOWN && currentFloor > 0)   currentFloor--;

        // Arrival: alight first, then board (zero time)
        alightHere();
        boardHere(); // board in current direction at this floor

        // After servicing this floor, if nothing ahead, pick the nearest work (may be same-floor opposite direction)
        if (!hasWorkAhead(direction)) {
            Direction nd = directionTowardNearestWork();
            if (nd == Direction.IDLE) {
                direction = Direction.IDLE;
            } else {
                // Flip/retarget and allow instant boarding at the SAME floor for the new direction
                direction = nd;
                boardHere(); // opportunistic same-floor pickup after direction change
            }
        }
    }

    // ---------------- Internals ----------------

    private boolean inRange(int f) { return 0 <= f && f < floors; }

    private void boardNow(int dest) {
        onboard++;
        drop[dest] = drop[dest] + 1;
    }

    private void enqueue(int start, int dest, Direction dir) {
        if (dir == Direction.UP) waitUp[start].addLast(dest);
        else                     waitDown[start].addLast(dest);
    }

    private void alightHere() {
        if (drop[currentFloor] > 0) {
            onboard -= drop[currentFloor];
            drop[currentFloor] = 0;
        }
    }

    private void boardHere() {
        if (onboard >= capacity) return;
        ArrayDeque<Integer> q = (direction == Direction.UP) ? waitUp[currentFloor] : waitDown[currentFloor];
        while (!q.isEmpty() && onboard < capacity) {
            int dest = q.removeFirst();
            boardNow(dest);
        }
    }

    private boolean hasAnyWork() {
        if (onboard > 0) return true;
        for (int f = 0; f < floors; f++) {
            if (!waitUp[f].isEmpty() || !waitDown[f].isEmpty()) return true;
        }
        return false;
    }

    // Is there work strictly "ahead" in the current pass?
    private boolean hasWorkAhead(Direction dir) {
        if (dir == Direction.UP) {
            for (int f = currentFloor + 1; f < floors; f++) if (drop[f] > 0) return true;
            for (int f = currentFloor; f < floors; f++) if (!waitUp[f].isEmpty()) return true;
            return false;
        } else if (dir == Direction.DOWN) {
            for (int f = 0; f < currentFloor; f++) if (drop[f] > 0) return true;
            for (int f = currentFloor; f >= 0; f--) if (!waitDown[f].isEmpty()) return true;
            return false;
        }
        return false;
    }

    // Choose direction toward the NEAREST work item (drops or any waiting start floor).
    // Prioritize immediate pickups at current floor (if seat available).
    private Direction directionTowardNearestWork() {
        if (onboard < capacity) {
            if (!waitUp[currentFloor].isEmpty())   return Direction.UP;
            if (!waitDown[currentFloor].isEmpty()) return Direction.DOWN;
        }
        int upDist = Integer.MAX_VALUE, downDist = Integer.MAX_VALUE;

        // look above
        for (int f = currentFloor + 1; f < floors; f++) {
            if (drop[f] > 0 || !waitUp[f].isEmpty() || !waitDown[f].isEmpty()) { upDist = f - currentFloor; break; }
        }
        // look below
        for (int f = currentFloor - 1; f >= 0; f--) {
            if (drop[f] > 0 || !waitUp[f].isEmpty() || !waitDown[f].isEmpty()) { downDist = currentFloor - f; break; }
        }

        if (upDist == Integer.MAX_VALUE && downDist == Integer.MAX_VALUE) return Direction.IDLE;
        if (upDist <= downDist) return Direction.UP;
        return Direction.DOWN;
    }
}
