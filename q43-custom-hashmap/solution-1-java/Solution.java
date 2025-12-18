import java.util.*;

public class CustomHashMap {

    // Singly-linked list node for each bucket (separate chaining)
    private static class Entry {
        String key;
        String value;
        Entry next;

        Entry(String key, String value, Entry next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private Entry[] buckets;     // array of bucket heads
    private int size;

    // Store load factors rounded to 2 decimals as "hundredths" to avoid floating precision issues
    private final int minLF100;
    private final int maxLF100;

    public CustomHashMap(double minLoadFactor, double maxLoadFactor) {
        this.minLF100 = round2ToInt(minLoadFactor);
        this.maxLF100 = round2ToInt(maxLoadFactor);

        // initial bucketsCount = 2
        this.buckets = new Entry[2];
        this.size = 0;
    }

    public void put(String key, String value) {
        int idx = bucketIndex(key, buckets.length);

        // update if key exists
        for (Entry cur = buckets[idx]; cur != null; cur = cur.next) {
            if (cur.key.equals(key)) {
                cur.value = value;
                adjustBucketsIfNeeded(); // spec: check after each put
                return;
            }
        }

        // insert new at head
        buckets[idx] = new Entry(key, value, buckets[idx]);
        size++;

        adjustBucketsIfNeeded();
    }

    public String get(String key) {
        int idx = bucketIndex(key, buckets.length);
        for (Entry cur = buckets[idx]; cur != null; cur = cur.next) {
            if (cur.key.equals(key)) return cur.value;
        }
        return "";
    }

    public String remove(String key) {
        int idx = bucketIndex(key, buckets.length);

        Entry prev = null;
        Entry cur = buckets[idx];

        while (cur != null) {
            if (cur.key.equals(key)) {
                String removedValue = cur.value;

                if (prev == null) {
                    buckets[idx] = cur.next;
                } else {
                    prev.next = cur.next;
                }

                size--;
                adjustBucketsIfNeeded(); // spec: check after each remove
                return removedValue;
            }
            prev = cur;
            cur = cur.next;
        }

        adjustBucketsIfNeeded(); // even though no change, still "after each remove"
        return "";
    }

    public List<String> getBucketKeys(int bucketIndex) {
        if (bucketIndex < 0 || bucketIndex >= buckets.length) {
            return new ArrayList<>();
        }

        List<String> keys = new ArrayList<>();
        for (Entry cur = buckets[bucketIndex]; cur != null; cur = cur.next) {
            keys.add(cur.key);
        }

        Collections.sort(keys);
        return keys;
    }

    public int size() {
        return size;
    }

    public int bucketsCount() {
        return buckets.length;
    }

    // ----------------- Internal helpers -----------------

    private void adjustBucketsIfNeeded() {
        while (true) {
            int lf100 = loadFactor100(); // load factor rounded to 2 decimals (as hundredths)

            if (lf100 > maxLF100) {
                rehash(buckets.length * 2);
                continue;
            }

            if (lf100 < minLF100 && buckets.length > 2) {
                rehash(buckets.length / 2);
                continue;
            }

            break;
        }
    }

    private void rehash(int newBucketsCount) {
        if (newBucketsCount < 2) newBucketsCount = 2;

        Entry[] old = buckets;
        Entry[] neu = new Entry[newBucketsCount];

        for (Entry head : old) {
            Entry cur = head;
            while (cur != null) {
                Entry next = cur.next;

                int idx = bucketIndex(cur.key, newBucketsCount);
                // Insert at head in new bucket
                cur.next = neu[idx];
                neu[idx] = cur;

                cur = next;
            }
        }

        buckets = neu;
    }

    private int loadFactor100() {
        // LoadFactor = round2(size / bucketsCount)
        // store as integer "hundredths"
        return round2ToInt((double) size / (double) buckets.length);
    }

    private static int round2ToInt(double value) {
        // returns round2(value) * 100 as int, where round2(x)=Math.round(x*100.0)/100.0
        return (int) Math.round(value * 100.0);
    }

    private static int bucketIndex(String key, int bucketsCount) {
        int h = customHash(key);
        // h is non-negative for given constraints, but keep it safe:
        int idx = h % bucketsCount;
        if (idx < 0) idx += bucketsCount;
        return idx;
    }

    private static int customHash(String key) {
        int len = key.length();
        int sum = 0;
        for (int i = 0; i < len; i++) {
            char c = key.charAt(i); // key contains only a-z
            sum += (c - 'a' + 1);
        }
        return (len * len) + sum;
    }
}
