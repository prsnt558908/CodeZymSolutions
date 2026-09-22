

# Design a Custom HashMap in Java

#### Problem Statement

[https://codezym.com/question/43-design-custom-hashmap](https://codezym.com/question/43-design-custom-hashmap)


The core idea is to split key-value pairs into **buckets** and search only the bucket chosen by the required hash formula. Each bucket is a list, so different keys can share it safely. This is called **separate chaining**. We resize the bucket collection when the rounded load factor crosses a limit. No design pattern is needed here. A small entry class and lists are enough.


## 1. Start With One List

We could store every key-value pair in one list. To insert, read, or remove a key, we would scan that list. An existing key must be updated instead of added again.


This works for basic storage, but each operation can inspect all `N` entries. It also does not implement the required buckets. Splitting the entries into buckets reduces how much we usually need to search.


## 2. Give Each Key a Bucket

The required hash is `length * length + sum of letter values`, where `a = 1` through `z = 26`. The bucket index is `hash % bucketsCount`.


For `"abcd"`, the hash is `4 * 4 + 1 + 2 + 3 + 4 = 26`. With four buckets, its index is `26 % 4 = 2`.


The square in the statement means multiplication. Java's `^` operator performs bitwise XOR, so we must use `length * length`.


### Why These Classes and Lists?

- **`Entry`** keeps one key and its value together. The key stays fixed, while an update can replace the value.
- **`List<List<Entry>> buckets`** holds the buckets. Each inner list stores entries that share a bucket index.
- **`entryCount`** tracks the number of distinct keys, making `size()` constant time.


The solution uses `ArrayList` and does not use a built-in map or set.


A Strategy pattern could separate several interchangeable hash functions, but this problem requires exactly one formula. An extra interface would add code without helping this solution.


### Handle Collisions by Comparing Keys

With four buckets, `"a"` and `"abcd"` both belong to bucket `2`. They still represent different entries. We scan that bucket and compare keys using `equals()`.


Even after resizing, two keys may remain together. For example, `"ab"` and `"ba"` have the same hash. Resizing never removes the need to compare complete keys.


## 3. Implement the Operations

- **`put`** searches the chosen bucket. It updates an existing entry or adds a new entry and increases the size.
- **`get`** searches the chosen bucket and returns the value, or `""` when absent.
- **`remove`** removes a matching entry, decreases the size, and returns its previous value. A missing key returns `""`.
- **`getBucketKeys`** copies the keys from a valid bucket and sorts that copy. Invalid indices return an empty list.


We sort only when bucket keys are requested. Keeping every bucket sorted would add work to insertions without helping our linear searches. Returning a copy also prevents callers from changing the stored bucket through the result.


Both `put` and `remove` finish with a resize check, including updates and unsuccessful removals, as required by the statement. Read-only methods do not resize the map.


## 4. Resize Using the Rounded Load Factor

Round both constructor thresholds once using `Math.round(value * 100.0) / 100.0`. Apply the same rounding to `(double) entryCount / bucketCount` before every threshold comparison.


- If the rounded load factor is strictly above the maximum, double the bucket count until it is no longer above the maximum.
- If it is strictly below the minimum, halve the bucket count until it is no longer below the minimum, or only two buckets remain.
- Equality with a limit does not trigger resizing.


For example, `5 / 8` becomes `0.63`, while `3 / 8` becomes `0.38`. Comparing unrounded values can give a different bucket count.


First calculate the target bucket count. Then create the new buckets and move each existing entry to `hash(key) % targetCount`. Intermediate bucket layouts are not observable, so we can redistribute entries once after choosing the final count.


Move entries directly instead of calling public `put`. Calling `put` would change the size and trigger more resize checks. Moving an entry must preserve both its key-value pair and the total size.


### A Detail About Narrow Threshold Ranges

The statement does not specify how to resolve ranges that no allowed bucket count can satisfy. With seven entries and limits `0.50` and `0.75`, eight buckets give `0.88`, while sixteen give `0.44`.


This solution chooses the resize direction once per operation. A growth check stops when the maximum is satisfied. A shrink check stops when the minimum is satisfied or two buckets remain. It does not reverse direction inside the same check, which would risk an endless grow-shrink loop. The supplied tests use compatible limits and do not distinguish between policies for this ambiguous case.


## 5. Follow a Small Example

Start with limits `0.25` and `0.75`, zero entries, and two buckets.


1. `put("a", "one")` gives size `1` and load factor `0.50`. The map keeps two buckets.
2. `put("bb", "two")` gives load factor `1.00`. Grow to four buckets. `"a"` moves to bucket `2`, and `"bb"` goes to bucket `0`.
3. `put("abcd", "three")` gives load factor `0.75`. No resize occurs. Bucket `2` contains `"a"` and `"abcd"`.
4. `put("m", "four")` gives load factor `1.00`. Grow to eight buckets. `"m"` goes to bucket `6`.
5. `put("a", "ONE")` updates the value. The size stays `4`.
6. Remove `"m"` and `"abcd"`. Two entries remain, so the load factor is `0.25`. Keep eight buckets.
7. Remove `"bb"`. One entry remains, giving `0.13`. Shrink to four buckets, where the load factor is `0.25`.
8. Remove `"a"`. The map becomes empty and shrinks to its minimum of two buckets.


Always inspect buckets after any resizing. In the statement's two-key example using `"a"` and `"c"` with these limits, the second insertion grows the map to four buckets. The correct resulting buckets are `0: ["c"]` and `2: ["a"]`.


## 6. Why the Solution Works

Every stored entry belongs to the bucket given by the required hash and the current bucket count. An insertion places it there, and resizing restores the same rule for the new count.


Key lookups search that exact bucket. Comparing the full key distinguishes collisions. Updating before adding prevents duplicate keys, so the size changes only when a distinct key is inserted or an existing key is removed.


Rehashing moves every existing entry exactly once without changing the size. Bucket inspection copies exactly the requested bucket's keys and sorts them, producing the required output.


## 7. Complexity

Let `N` be the number of entries, `B` the bucket count, and `K` the number of entries in the bucket being searched. Keys have at most 20 characters, so hashing and key comparison take bounded time.


- `get`, `put`, and `remove` take `O(K)` without resizing, or `O(N)` in the worst case.
- `getBucketKeys` takes `O(K log K)` time for sorting and `O(K)` space for the returned list. An invalid index takes `O(1)` time.
- `size` and `bucketsCount` take `O(1)` time.
- A resize takes `O(N + old bucket count + new bucket count)` time, including scanning old buckets and creating new ones.
- Stored entries and bucket lists use `O(N + B)` space. During resizing, both bucket collections temporarily exist.


Small buckets make ordinary operations fast, but the required hash does not guarantee a good distribution. We therefore cannot guarantee constant-time searches. An operation that resizes also pays the resize cost.


## 8. Complete Java Code



```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomHashMap {
    private static final int MIN_BUCKETS = 2;

    private static class Entry {
        final String key;
        String value;

        Entry(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private final double minLoadFactor;
    private final double maxLoadFactor;
    private List<List<Entry>> buckets;
    private int entryCount;

    public CustomHashMap(double minLoadFactor, double maxLoadFactor) {
        this.minLoadFactor = round2(minLoadFactor);
        this.maxLoadFactor = round2(maxLoadFactor);
        this.buckets = createBuckets(MIN_BUCKETS);
        this.entryCount = 0;
    }

    public void put(String key, String value) {
        List<Entry> bucket = buckets.get(hash(key) % buckets.size());

        for (Entry entry : bucket) {
            if (entry.key.equals(key)) {
                entry.value = value;
                checkRehash();
                return;
            }
        }

        bucket.add(new Entry(key, value));
        entryCount++;
        checkRehash();
    }

    public String get(String key) {
        List<Entry> bucket = buckets.get(hash(key) % buckets.size());

        for (Entry entry : bucket) {
            if (entry.key.equals(key)) {
                return entry.value;
            }
        }

        return "";
    }

    public String remove(String key) {
        List<Entry> bucket = buckets.get(hash(key) % buckets.size());

        for (int i = 0; i < bucket.size(); i++) {
            Entry entry = bucket.get(i);
            if (entry.key.equals(key)) {
                bucket.remove(i);
                entryCount--;
                checkRehash();
                return entry.value;
            }
        }

        checkRehash();
        return "";
    }

    public List<String> getBucketKeys(int bucketIndex) {
        List<String> keys = new ArrayList<>();

        if (bucketIndex < 0 || bucketIndex >= buckets.size()) {
            return keys;
        }

        for (Entry entry : buckets.get(bucketIndex)) {
            keys.add(entry.key);
        }

        Collections.sort(keys);
        return keys;
    }

    public int size() {
        return entryCount;
    }

    public int bucketsCount() {
        return buckets.size();
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static int hash(String key) {
        int length = key.length();
        int result = length * length;

        for (int i = 0; i < length; i++) {
            result += key.charAt(i) - 'a' + 1;
        }

        return result;
    }

    private double loadFactor(int bucketCount) {
        // Cast before division, then round before comparing thresholds.
        return round2((double) entryCount / bucketCount);
    }

    private static List<List<Entry>> createBuckets(int count) {
        List<List<Entry>> result = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            result.add(new ArrayList<>());
        }

        return result;
    }

    private void checkRehash() {
        int targetCount = buckets.size();

        // Choose one direction for this check to avoid resize oscillation.
        if (loadFactor(targetCount) > maxLoadFactor) {
            while (loadFactor(targetCount) > maxLoadFactor) {
                targetCount *= 2;
            }
        } else if (loadFactor(targetCount) < minLoadFactor) {
            while (targetCount > MIN_BUCKETS
                    && loadFactor(targetCount) < minLoadFactor) {
                targetCount /= 2;
            }
        }

        if (targetCount != buckets.size()) {
            rehash(targetCount);
        }
    }

    private void rehash(int newBucketCount) {
        List<List<Entry>> newBuckets = createBuckets(newBucketCount);

        for (List<Entry> bucket : buckets) {
            for (Entry entry : bucket) {
                int newIndex = hash(entry.key) % newBucketCount;
                // Reuse the entry without changing size or calling put.
                newBuckets.get(newIndex).add(entry);
            }
        }

        buckets = newBuckets;
    }
}
```
