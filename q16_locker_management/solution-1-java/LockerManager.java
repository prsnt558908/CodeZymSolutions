import java.util.*;

/**
 * Lexicographic Locker Manager
 * - findSlot(size) returns the lexicographically smallest FREE lockerId for that size.
 * - IDs are generated as "<SIZE><number>" e.g., "M1", "M10".
 */
public class LockerManager {

    private static final Set<String> VALID_SIZES = new HashSet<>(Arrays.asList("S","M","L","XL","XXL"));

    // Next numeric suffix per size (increment-before-use)
    private final Map<String, Integer> nextIndex = new HashMap<>();
    // Free IDs per size (TreeSet<String> gives lexicographic order)
    private final Map<String, TreeSet<String>> freeIds = new HashMap<>();
    // Registry
    private final Map<String, Locker> byId = new HashMap<>();

    public LockerManager() {
        for (String s : VALID_SIZES) {
            nextIndex.put(s, 0);
            freeIds.put(s, new TreeSet<>());
        }
    }

    public void addSlot(String size) {
        if (!VALID_SIZES.contains(size)) return;
        int idx = nextIndex.compute(size, (k, v) -> v == null ? 1 : v + 1);
        String id = size + idx;
        Locker lk = new Locker(id, size, false);
        byId.put(id, lk);
        freeIds.get(size).add(id); // keep IDs (strings) for lexicographic min
    }

    public String findSlot(String size) {
        if (!VALID_SIZES.contains(size)) return "";
        TreeSet<String> set = freeIds.get(size);
        if (set == null || set.isEmpty()) return "";
        return set.first(); // lexicographically smallest free ID
    }

    public boolean occupySlot(String lockerId) {
        Locker lk = byId.get(lockerId);
        if (lk == null || lk.occupied) return false;
        // Must be present in free set to occupy
        if (!freeIds.get(lk.size).remove(lockerId)) return false;
        lk.occupied = true;
        return true;
    }

    public boolean freeSlot(String lockerId) {
        Locker lk = byId.get(lockerId);
        if (lk == null || !lk.occupied) return false;
        lk.occupied = false;
        freeIds.get(lk.size).add(lockerId);
        return true;
    }

    private static final class Locker {
        final String id;
        final String size;
        boolean occupied;
        Locker(String id, String size, boolean occ) { this.id=id; this.size=size; this.occupied=occ; }
    }
}
