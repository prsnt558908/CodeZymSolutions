# Design an In-Memory Cache with Custom Eviction Policy in Java

#### Problem Statement
[https://codezym.com/question/48-design-in-memory-cache-custom-eviction-policy](https://codezym.com/question/48-design-in-memory-cache-custom-eviction-policy)


A cache has two separate jobs. It stores key-value pairs, and it decides which key to throw out when it gets too full. The core idea of this solution is to keep these two jobs apart.


The cache stores its values in a `HashMap`. The "which key leaves next" rule lives in its own policy class, and every policy follows one common interface. This is the **Strategy pattern**. A tiny **Simple Factory** turns a policy name like `"REMOVE-LARGEST-POLICY"` into the right policy object.


Strategy plus Simple Factory is the best combination here, because the eviction rule is the only thing that changes from one policy to another.


For speed, each policy keeps its keys in a sorted set, ordered from "evict first" to "evict last". So the next key to evict is always the first key in the set, and we never scan the whole cache.


We will start with a simple brute force solution, see where it falls short, and then fix it step by step.


## Understanding the Rules

Each policy gives every key a **score**:

- `REMOVE-LARGEST-POLICY`: the score is the length of the key.
- `REMOVE-HEAVIEST-POLICY`: the score is the weight of the key, where `a = 1`, `b = 2`, ..., `z = 26`. For example, `"cab"` weighs `3 + 1 + 2 = 6`.


The key with the **highest score** is evicted first. If several keys share the highest score, the **lexicographically smallest** one is evicted.


Two more rules shape the code:

- **Insert first, then evict.** A new key is always added first. Only then, if the cache holds more than `capacity` keys, exactly one key is evicted. So the new key itself can be evicted right away.
- **Updating a key never evicts.** If the key already exists, we only change its value.


For example, take capacity `2` with `REMOVE-LARGEST-POLICY`. After adding `"a"` and `"bb"`, adding `"ccc"` makes the cache `{a, bb, ccc}` for a moment. `"ccc"` is the longest key, so it is evicted at once, and the cache is back to `{a, bb}`.


## Solution 1: Brute Force (Scan Every Key)

Store all entries in a `HashMap<String, String>`. Whenever we need the next eviction key, look at every key and keep the one that should leave first.

```java
public void put(String key, String value) {
    boolean isNewKey = !values.containsKey(key);
    values.put(key, value);
    if (isNewKey && values.size() > capacity) {
        values.remove(nextEvictionKey());
    }
}

// scan every key and keep the one that should leave first
public String nextEvictionKey() {
    String answer = null;
    for (String key : values.keySet()) {
        if (answer == null || leavesBefore(key, answer)) {
            answer = key;
        }
    }
    return answer == null ? "" : answer;
}

// true if key a should be evicted before key b
boolean leavesBefore(String a, String b) {
    int scoreA = score(a), scoreB = score(b);
    if (scoreA != scoreB) {
        return scoreA > scoreB;
    }
    return a.compareTo(b) < 0;
}

// every policy is one more branch here
int score(String key) {
    if (policyName.equals("REMOVE-LARGEST-POLICY")) {
        return key.length();
    }
    int weight = 0; // REMOVE-HEAVIEST-POLICY
    for (char c : key.toCharArray()) {
        weight += c - 'a' + 1;
    }
    return weight;
}
```

Here `values` is the map, `capacity` and `policyName` come from the constructor, and `get` and `remove` are plain `HashMap` calls.


### Problems with this solution

1. **It is slow.** Every eviction and every `nextEvictionKey()` call looks at all `n` keys. That is O(n) work each time, and a full cache pays it on almost every `put` of a new key.
2. **It is hard to extend.** The policy rules sit inside the cache as `if-else` branches. Every new policy means editing the cache class again, and one small mistake there can break the policies that already work.


Next, we fix both problems.


## Solution 2: Strategy Pattern + Sorted Set

### Step 1: Move the eviction rule out of the cache (Strategy pattern)

The cache does not need to know *how* a policy picks a key. It only needs to ask one question: "which key leaves next?"


So every policy implements one small interface:

```java
interface EvictionPolicy {
    void keyAdded(String key);

    void keyRemoved(String key);

    String nextEvictionKey();
}
```

- **`EvictionPolicy`** is the strategy. The cache holds one policy object and never cares which policy it is.
- **`RemoveLargestPolicy`** and **`RemoveHeaviestPolicy`** are the concrete strategies, one class per rule.


**Why does a policy need `keyAdded` and `keyRemoved`?** The cache calls them whenever a key comes in or goes out. This keeps each policy's own data up to date, so it can answer `nextEvictionKey()` right away instead of scanning.


It also lets each policy pick its own data structure. Both policies here use a sorted set, but a future "evict the oldest key" policy could simply keep keys in the order they arrived. The cache would not change at all.


### Step 2: Keep the keys sorted, so the answer is always ready

Each policy keeps its keys in a sorted set (`TreeSet`) in eviction order:

1. Higher score comes first.
2. Same score: the lexicographically smaller key comes first.


Now the next key to evict is simply `sortedKeys.first()`. Adding or removing a key costs O(log n), and there is no full scan anymore.

```java
// keys sorted in eviction order, so first() is always the next key to evict
TreeSet<String> sortedKeys = new TreeSet<>((a, b) -> {
    if (a.length() != b.length()) {
        return b.length() - a.length(); // longer key comes first
    }
    return a.compareTo(b); // same length: smaller key comes first
});
```

`RemoveHeaviestPolicy` uses the same idea, but compares weights instead of lengths.


Two details keep this correct:

- **The tie-break is part of the comparator.** A `TreeSet` treats two keys as equal when the comparator returns `0`. If we compared only lengths, `"aa"` and `"bb"` would look equal, and the second one would never be stored. Comparing the keys themselves on a tie keeps every key distinct.
- **A key's place in the set never changes.** The order depends only on the key, never on its value. So updating a value only touches the `HashMap`, and the sorted set never needs fixing.


### Step 3: Create the right policy from its name (Simple Factory)

The constructor gets the policy as a plain string. `EvictionPolicyFactory` turns that name into a policy object, so the mapping from names to classes lives in one place.


The cache never mentions `RemoveLargestPolicy` or `RemoveHeaviestPolicy` by name. Adding a new policy now takes two small steps: write a class that implements `EvictionPolicy`, and add one `case` to the factory. The cache class stays untouched.


### Why not other design patterns?

**Template Method:** We could write an abstract `Cache` class with an abstract `pickKeyToEvict()` method, plus one subclass per policy, like `LargestKeyCache` and `HeaviestKeyCache`. It works, but it welds each policy into a cache class.


Then every new kind of cache needs one subclass per policy. For example, a cache with time-based expiry would need `ExpiringLargestKeyCache`, `ExpiringHeaviestKeyCache`, and so on. With Strategy, the cache *has* a policy instead of *being* one, so any cache can use any policy.


**Builder:** The class is named `CacheBuilder`, so the Builder pattern may come to mind. Builder helps when an object has many optional settings. Our cache has only two settings, and both are required, so a normal constructor is simpler.


### Walkthrough with Example 2

Capacity is `3` and the policy is `REMOVE-LARGEST-POLICY`.

```
put bb, aa, cc       sortedKeys = [aa, bb, cc]       all have length 2, so alphabetical order
nextEvictionKey()    -> "aa"
put dd               sortedKeys = [aa, bb, cc, dd]   size 4 > capacity 3
                     evict sortedKeys.first() = "aa"
                     sortedKeys = [bb, cc, dd]
get("aa")            -> ""
```


### Complexity

Here `n` is the number of keys in the cache.

| Operation | Brute Force | Strategy + Sorted Set |
|---|---|---|
| `get` | O(1) | O(1) |
| `put` existing key | O(1) | O(1) |
| `put` new key | O(n) when it evicts | O(log n) |
| `remove` | O(1) | O(log n) |
| `nextEvictionKey` | O(n) | O(log n) |

Each comparison also reads the characters of the two keys, which is quick for short keys. Memory is O(n) in both solutions.


### Code

```java
import java.util.*;

/**
 * In-memory cache with a fixed capacity.
 * It stores key -> value and asks its eviction policy which key to remove
 * whenever it holds more keys than its capacity.
 */
public class CacheBuilder {
    int capacity;

    // key -> value, gives O(1) get, put and remove
    Map<String, String> values = new HashMap<>();

    // decides which key leaves first (Strategy pattern)
    EvictionPolicy policy;

    public CacheBuilder(int capacity, String evictionPolicy) {
        this.capacity = capacity;
        this.policy = new EvictionPolicyFactory().create(evictionPolicy);
    }

    public String get(String key) {
        return values.getOrDefault(key, "");
    }

    public void put(String key, String value) {
        // existing key: only update the value, never evict
        if (values.containsKey(key)) {
            values.put(key, value);
            return;
        }

        // new key: insert first
        values.put(key, value);
        policy.keyAdded(key);

        // then evict exactly one key if we are over capacity,
        // this can be the key we just inserted
        if (values.size() > capacity) {
            remove(policy.nextEvictionKey());
        }
    }

    public String nextEvictionKey() {
        return policy.nextEvictionKey();
    }

    public boolean remove(String key) {
        if (!values.containsKey(key)) {
            return false;
        }
        values.remove(key);
        policy.keyRemoved(key);
        return true;
    }
}

/**
 * Strategy: an eviction policy decides which key should leave the cache first.
 * The cache tells the policy whenever a key is added or removed,
 * so each policy can keep its own data ready for the next question.
 */
interface EvictionPolicy {
    void keyAdded(String key);

    void keyRemoved(String key);

    /** Returns the key that would be evicted next, or "" if there are no keys. */
    String nextEvictionKey();
}

/**
 * REMOVE-LARGEST-POLICY: the longest key leaves first.
 * Keys of the same length: the lexicographically smallest key leaves first.
 */
class RemoveLargestPolicy implements EvictionPolicy {
    // keys sorted in eviction order, so first() is always the next key to evict
    TreeSet<String> sortedKeys = new TreeSet<>((a, b) -> {
        if (a.length() != b.length()) {
            return b.length() - a.length(); // longer key comes first
        }
        return a.compareTo(b); // same length: smaller key comes first
    });

    public void keyAdded(String key) {
        sortedKeys.add(key);
    }

    public void keyRemoved(String key) {
        sortedKeys.remove(key);
    }

    public String nextEvictionKey() {
        return sortedKeys.isEmpty() ? "" : sortedKeys.first();
    }
}

/**
 * REMOVE-HEAVIEST-POLICY: the key with the highest weight leaves first.
 * Keys of the same weight: the lexicographically smallest key leaves first.
 */
class RemoveHeaviestPolicy implements EvictionPolicy {
    // keys sorted in eviction order, so first() is always the next key to evict
    TreeSet<String> sortedKeys = new TreeSet<>((a, b) -> {
        int weightA = weight(a), weightB = weight(b);
        if (weightA != weightB) {
            return weightB - weightA; // heavier key comes first
        }
        return a.compareTo(b); // same weight: smaller key comes first
    });

    /** Sum of character values, a = 1, b = 2, ..., z = 26. */
    int weight(String key) {
        int sum = 0;
        for (char c : key.toCharArray()) {
            sum += c - 'a' + 1;
        }
        return sum;
    }

    public void keyAdded(String key) {
        sortedKeys.add(key);
    }

    public void keyRemoved(String key) {
        sortedKeys.remove(key);
    }

    public String nextEvictionKey() {
        return sortedKeys.isEmpty() ? "" : sortedKeys.first();
    }
}

/** Simple Factory: turns a policy name into the matching policy object. */
class EvictionPolicyFactory {
    EvictionPolicy create(String policyName) {
        switch (policyName) {
            case "REMOVE-LARGEST-POLICY":
                return new RemoveLargestPolicy();
            case "REMOVE-HEAVIEST-POLICY":
                return new RemoveHeaviestPolicy();
            default:
                throw new IllegalArgumentException("Unknown eviction policy: " + policyName);
        }
    }
}
```