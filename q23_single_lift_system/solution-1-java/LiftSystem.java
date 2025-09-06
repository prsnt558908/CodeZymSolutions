import java.util.*;

/**
 * LiftSystem
 *
 * Single-lift feasibility planner that decides whether to ACCEPT or REJECT a pickup request.
 * - UP and DOWN requests are evaluated on independent passes (they do NOT clash).
 * - For each direction, we plan to stop ONLY at the union of all pickup and drop floors (minimal halts).
 * - K-stop rule (per rider): number of halts strictly between src and dst must be <= maxStops.
 *   (Exclude boarding and destination floors from the count.)
 * - Capacity rule: at any segment between consecutive floors, onboard <= liftCapacity.
 *
 * Data structures per direction:
 * - TreeSet<Integer> stops: unique planned halts for that direction (pickups/drops only).
 * - ArrayList<Rider> riders: accepted riders in that direction (intervals [min(src,dst), max(src,dst)]).
 *
 * Acceptance check for a new request (src, dst):
 * 1) Build proposedStops = stops ∪ {src, dst}  (coalesces duplicates).
 * 2) Verify K-stop rule for each existing rider and the new rider using proposedStops.
 *    For rider interval (low, high), counted halts = |proposedStops ∩ (low, high)|.
 * 3) Capacity feasibility via line sweep:
 *    - Build diff array: diff[low] += 1; diff[high] -= 1 for every rider (existing + new).
 *    - Running sum across floors gives active riders on each travel segment; must be <= capacity.
 *
 * If both checks pass → accept (add rider and stops). Otherwise reject.
 */
public class LiftSystem {

    private final int floorsCount;
    private final int capacity;
    private final int maxStops;

    private final Planner up;    // handles requests with source <= destination
    private final Planner down;  // handles requests with source >  destination

    public LiftSystem(int floorsCount, int liftCapacity, int maxStops) {
        if (floorsCount < 2 || floorsCount > 100) {
            throw new IllegalArgumentException("floorsCount out of range");
        }
        if (liftCapacity < 1 || liftCapacity > 20) {
            throw new IllegalArgumentException("liftCapacity out of range");
        }
        if (maxStops < 1 || maxStops > 10) {
            throw new IllegalArgumentException("maxStops out of range");
        }
        this.floorsCount = floorsCount;
        this.capacity = liftCapacity;
        this.maxStops = maxStops;
        this.up = new Planner();
        this.down = new Planner();
    }

    /**
     * Submit a new ride request. Returns true if accepted (feasible), else false.
     * Direction rule:
     * - if source <= destination → UP planner
     * - if source >  destination → DOWN planner
     */
    public boolean requestPickup(int source, int destination) {
        if (!inRange(source) || !inRange(destination)) return false;

        // Trivial case: same floor request (no travel); always feasible; no state change needed.
        if (source == destination) return true;

        Planner planner = (source <= destination) ? up : down;
        if (planner.canAccept(source, destination, floorsCount, capacity, maxStops)) {
            planner.accept(source, destination);
            return true;
        }
        return false;
    }

    // Optional: debugging helpers (not required by the spec)
    public Set<Integer> getUpStops()   { return Collections.unmodifiableSet(up.stops); }
    public Set<Integer> getDownStops() { return Collections.unmodifiableSet(down.stops); }
    public int getUpRiderCount()       { return up.riders.size(); }
    public int getDownRiderCount()     { return down.riders.size(); }

    // ---- internals ----

    private boolean inRange(int floor) {
        return 0 <= floor && floor < floorsCount;
    }

    private static final class Rider {
        final int src;
        final int dst;
        final int low;
        final int high;
        Rider(int s, int d) {
            this.src = s;
            this.dst = d;
            this.low = Math.min(s, d);
            this.high = Math.max(s, d);
        }
    }

    private static final class Planner {
        private final TreeSet<Integer> stops = new TreeSet<>();
        private final ArrayList<Rider> riders = new ArrayList<>();

        boolean canAccept(int src, int dst, int floorsCount, int capacity, int maxStops) {
            // 1) Build proposed stops (minimal plan = pickups/drops only)
            TreeSet<Integer> proposedStops = new TreeSet<>(stops);
            proposedStops.add(src);
            proposedStops.add(dst);

            // 2) K-stop check for existing riders
            for (Rider r : riders) {
                int inside = countInteriorStops(proposedStops, r.low, r.high);
                if (inside > maxStops) return false;
            }
            // 2b) K-stop check for the new rider
            int low = Math.min(src, dst), high = Math.max(src, dst);
            if (countInteriorStops(proposedStops, low, high) > maxStops) return false;

            // 3) Capacity check via line sweep
            // diff[f] accumulates net starts(+1) and ends(-1) at floor f
            int[] diff = new int[floorsCount + 1]; // +1 guard for ease; we won't index beyond floorsCount
            for (Rider r : riders) {
                diff[r.low] += 1;
                diff[r.high] -= 1;
            }
            diff[low] += 1;
            diff[high] -= 1;

            int active = 0;
            // Each iteration 'f' represents the segment (f, f+1)
            for (int f = 0; f < floorsCount - 1; f++) {
                active += diff[f];
                if (active > capacity) return false;
            }

            return true;
        }

        void accept(int src, int dst) {
            riders.add(new Rider(src, dst));
            stops.add(src);
            stops.add(dst);
        }

        private static int countInteriorStops(NavigableSet<Integer> stops, int low, int high) {
            if (low >= high) return 0;
            // strictly between low and high
            return stops.subSet(low, false, high, false).size();
        }
    }

    
}
