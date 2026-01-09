import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CacheBuilder {

    private final int capacity;
    private final Map<String, String> cache;
    private final EvictionPolicy evictionPolicy;

    public CacheBuilder(int capacity, String evictionPolicy) {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.evictionPolicy = EvictionPolicies.from(evictionPolicy);
    }

    public String get(String key) {
        String v = cache.get(key);
        return v == null ? "" : v;
    }

    public void put(String key, String value) {
        boolean existed = cache.containsKey(key);

        // Always insert/update first
        cache.put(key, value);

        // Eviction only for NEW keys, and only if size > capacity after insertion
        if (!existed && cache.size() > capacity) {
            String toEvict = evictionPolicy.selectKeyToEvict(cache.keySet());
            if (toEvict != null) {
                cache.remove(toEvict);
            }
        }
    }

    public String nextEvictionKey() {
        if (cache.isEmpty()) return "";
        String k = evictionPolicy.selectKeyToEvict(cache.keySet());
        return k == null ? "" : k;
    }

    public boolean remove(String key) {
        return cache.remove(key) != null;
    }

    // -------------------- Strategy Pattern --------------------

    private interface EvictionPolicy {
        /**
         * Selects a key to evict based purely on the current keys.
         * Must not modify the cache.
         */
        String selectKeyToEvict(Set<String> keys);
    }

    private static final class EvictionPolicies {
        static EvictionPolicy from(String policyName) {
            if ("REMOVE-LARGEST-POLICY".equals(policyName)) {
                return new RemoveLargestPolicy();
            }
            if ("REMOVE-HEAVIEST-POLICY".equals(policyName)) {
                return new RemoveHeaviestPolicy();
            }
            throw new IllegalArgumentException("Unknown evictionPolicy: " + policyName);
        }
    }

    /**
     * Evict the entry with the largest key length.
     * Tie-break: lexicographically smallest key.
     */
    private static final class RemoveLargestPolicy implements EvictionPolicy {
        @Override
        public String selectKeyToEvict(Set<String> keys) {
            if (keys == null || keys.isEmpty()) return null;

            String best = null;
            int bestLen = -1;

            for (String k : keys) {
                int len = (k == null) ? 0 : k.length();
                if (best == null) {
                    best = k;
                    bestLen = len;
                    continue;
                }

                if (len > bestLen) {
                    best = k;
                    bestLen = len;
                } else if (len == bestLen) {
                    // lexicographically smallest wins
                    if (k != null && (best == null || k.compareTo(best) < 0)) {
                        best = k;
                    }
                }
            }
            return best;
        }
    }

    /**
     * Evict the entry with the maximum key weight.
     * Weight is sum of a=1..z=26 (assumes keys are a-z).
     * Tie-break: lexicographically smallest key.
     */
    private static final class RemoveHeaviestPolicy implements EvictionPolicy {
        @Override
        public String selectKeyToEvict(Set<String> keys) {
            if (keys == null || keys.isEmpty()) return null;

            String best = null;
            int bestWeight = Integer.MIN_VALUE;

            for (String k : keys) {
                int w = weight(k);

                if (best == null) {
                    best = k;
                    bestWeight = w;
                    continue;
                }

                if (w > bestWeight) {
                    best = k;
                    bestWeight = w;
                } else if (w == bestWeight) {
                    // lexicographically smallest wins
                    if (k != null && (best == null || k.compareTo(best) < 0)) {
                        best = k;
                    }
                }
            }
            return best;
        }

        private int weight(String key) {
            if (key == null) return 0;
            int sum = 0;
            for (int i = 0; i < key.length(); i++) {
                char c = key.charAt(i);
                if (c >= 'a' && c <= 'z') {
                    sum += (c - 'a' + 1);
                } else if (c >= 'A' && c <= 'Z') {
                    sum += (c - 'A' + 1);
                } else {
                    // If unexpected chars appear, treat them as 0 contribution
                    // (spec says only a-z, so this is just defensive).
                }
            }
            return sum;
        }
    }
}
