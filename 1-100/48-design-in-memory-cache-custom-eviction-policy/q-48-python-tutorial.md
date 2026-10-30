# Design an In-Memory Cache with Custom Eviction Policy in Python

#### Problem Statement
[https://codezym.com/question/48-design-in-memory-cache-custom-eviction-policy](https://codezym.com/question/48-design-in-memory-cache-custom-eviction-policy)


A cache has two separate jobs. It stores key-value pairs, and it decides which key to throw out when it gets too full. The core idea of this solution is to keep these two jobs apart.


The cache stores its values in a dictionary. The "which key leaves next" rule lives in its own policy class, and every policy follows one common interface. This is the **Strategy pattern**. A tiny **Simple Factory** turns a policy name like `"REMOVE-LARGEST-POLICY"` into the right policy object.


Strategy plus Simple Factory is the best combination here, because the eviction rule is the only thing that changes from one policy to another.


For speed, each policy keeps its keys in a heap, arranged so that the next key to evict is always on top. So we never scan the whole cache.


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

Store all entries in a dictionary. Whenever we need the next eviction key, look at every key and keep the one that should leave first.

```python
def put(self, key, value):
    is_new_key = key not in self.values
    self.values[key] = value
    if is_new_key and len(self.values) > self.capacity:
        del self.values[self.nextEvictionKey()]

def nextEvictionKey(self):
    if not self.values:
        return ""
    # scan every key: highest score first, then the smallest key
    return min(self.values, key=lambda k: (-self.score(k), k))

def score(self, key):
    # every policy is one more branch here
    if self.policy_name == "REMOVE-LARGEST-POLICY":
        return len(key)
    return sum(ord(c) - ord("a") + 1 for c in key)  # REMOVE-HEAVIEST-POLICY
```

`min()` compares the pairs `(-score, key)`. Python compares tuples item by item, so the highest score wins first (its negative is the smallest), and on a tie the smaller key wins.


Here `values` is the dictionary, `capacity` and `policy_name` come from the constructor, and `get` and `remove` are plain dictionary calls.


### Problems with this solution

1. **It is slow.** Every eviction and every `nextEvictionKey()` call looks at all `n` keys. That is O(n) work each time, and a full cache pays it on almost every `put` of a new key.
2. **It is hard to extend.** The policy rules sit inside the cache as `if-else` branches. Every new policy means editing the cache class again, and one small mistake there can break the policies that already work.


Next, we fix both problems.


## Solution 2: Strategy Pattern + Heap

### Step 1: Move the eviction rule out of the cache (Strategy pattern)

The cache does not need to know *how* a policy picks a key. It only needs to ask one question: "which key leaves next?"


So every policy follows one small abstract class:

```python
class EvictionPolicy(ABC):
    @abstractmethod
    def key_added(self, key):
        pass

    @abstractmethod
    def key_removed(self, key):
        pass

    @abstractmethod
    def next_eviction_key(self):
        pass
```

Python has no `interface` keyword, so we use `ABC` and `@abstractmethod`. A policy class that forgets one of these methods cannot even be created.

- **`EvictionPolicy`** is the strategy. The cache holds one policy object and never cares which policy it is.
- **`RemoveLargestPolicy`** and **`RemoveHeaviestPolicy`** are the concrete strategies, one class per rule.


**Why does a policy need `key_added` and `key_removed`?** The cache calls them whenever a key comes in or goes out. This keeps each policy's own data up to date, so it can answer `next_eviction_key()` right away instead of scanning.


It also lets each policy pick its own data structure. Both policies here use a heap, but a future "evict the oldest key" policy could simply keep keys in the order they arrived. The cache would not change at all.


### Step 2: Keep the keys in a heap, so the answer is always ready

Each policy keeps a heap of `(-score, key)` pairs, the same pairs the brute force passed to `min()`. Python's `heapq` always keeps the smallest pair at `heap[0]`, and the smallest pair is exactly the next key to evict. Adding a key costs O(log n).


There is one catch: a heap cannot remove an item from the middle quickly. So we use a simple trick called **lazy deletion**, which means "delete later, not now":

- `key_removed` only deletes the key from a set called `keys`, which holds the keys that are in the cache right now.
- The old heap entry stays where it is.
- `next_eviction_key` pops entries from the top until the top key is still in `keys`.

```python
def next_eviction_key(self):
    # throw away entries of keys that are no longer in the cache
    while self.heap and self.heap[0][1] not in self.keys:
        heapq.heappop(self.heap)
    return self.heap[0][1] if self.heap else ""
```

Each old entry is popped only once, so this cleanup stays cheap overall.


Two details keep this correct:

- **A key's score never changes.** The score depends only on the key, never on its value. So updating a value only touches the dictionary, and the heap never needs fixing.
- **Duplicate entries are harmless.** If a key is removed and then added again, the heap may hold two identical entries for it. Both say the same thing, so the answer is still right, and both are thrown away once the key leaves.


### Step 3: Create the right policy from its name (Simple Factory)

The constructor gets the policy as a plain string. `EvictionPolicyFactory` turns that name into a policy object, so the mapping from names to classes lives in one place.


The cache never mentions `RemoveLargestPolicy` or `RemoveHeaviestPolicy` by name. Adding a new policy now takes two small steps: write a class that inherits from `EvictionPolicy`, and add one `if` to the factory. The cache class stays untouched.


### Why not other design patterns?

**Template Method:** We could write an abstract `Cache` class with an abstract `pick_key_to_evict()` method, plus one subclass per policy, like `LargestKeyCache` and `HeaviestKeyCache`. It works, but it welds each policy into a cache class.


Then every new kind of cache needs one subclass per policy. For example, a cache with time-based expiry would need `ExpiringLargestKeyCache`, `ExpiringHeaviestKeyCache`, and so on. With Strategy, the cache *has* a policy instead of *being* one, so any cache can use any policy.


**Builder:** The class is named `CacheBuilder`, so the Builder pattern may come to mind. Builder helps when an object has many optional settings. Our cache has only two settings, and both are required, so a normal constructor is simpler.


### Walkthrough with Example 2

Capacity is `3` and the policy is `REMOVE-LARGEST-POLICY`. One extra `nextEvictionKey()` call at the end shows the cleanup.

```
put bb, aa, cc       heap top = (-2, "aa")    all have length 2, so the smallest key wins
nextEvictionKey()    -> "aa"
put dd               size 4 > capacity 3, so evict the top key "aa"
                     "aa" leaves keys, but its old entry stays in the heap
get("aa")            -> ""
nextEvictionKey()    pop the old (-2, "aa") entry, the new top is (-2, "bb")  -> "bb"
```


### Complexity

Here `n` is the number of keys in the cache.

| Operation | Brute Force | Strategy + Heap |
|---|---|---|
| `get` | O(1) | O(1) |
| `put` existing key | O(1) | O(1) |
| `put` new key | O(n) when it evicts | O(log n) |
| `remove` | O(1) | O(1) |
| `nextEvictionKey` | O(n) | O(log n) |

Computing a score also reads the characters of the key, which is quick for short keys. Memory is O(n), plus any old heap entries that are still waiting to be popped.


### Code

```python
import heapq
from abc import ABC, abstractmethod


class CacheBuilder:
    """
    In-memory cache with a fixed capacity.
    It stores key -> value and asks its eviction policy which key to remove
    whenever it holds more keys than its capacity.
    """

    def __init__(self, capacity, evictionPolicy):
        self.capacity = capacity

        # key -> value, gives O(1) get, put and remove
        self.values = {}

        # decides which key leaves first (Strategy pattern)
        self.policy = EvictionPolicyFactory().create(evictionPolicy)

    def get(self, key):
        return self.values.get(key, "")

    def put(self, key, value):
        # existing key: only update the value, never evict
        if key in self.values:
            self.values[key] = value
            return

        # new key: insert first
        self.values[key] = value
        self.policy.key_added(key)

        # then evict exactly one key if we are over capacity,
        # this can be the key we just inserted
        if len(self.values) > self.capacity:
            self.remove(self.policy.next_eviction_key())

    def nextEvictionKey(self):
        return self.policy.next_eviction_key()

    def remove(self, key):
        if key not in self.values:
            return False
        del self.values[key]
        self.policy.key_removed(key)
        return True


class EvictionPolicy(ABC):
    """
    Strategy: an eviction policy decides which key should leave the cache first.
    The cache tells the policy whenever a key is added or removed,
    so each policy can keep its own data ready for the next question.
    """

    @abstractmethod
    def key_added(self, key):
        pass

    @abstractmethod
    def key_removed(self, key):
        pass

    @abstractmethod
    def next_eviction_key(self):
        """Returns the key that would be evicted next, or "" if there are no keys."""
        pass


class RemoveLargestPolicy(EvictionPolicy):
    """
    REMOVE-LARGEST-POLICY: the longest key leaves first.
    Keys of the same length: the lexicographically smallest key leaves first.
    """

    def __init__(self):
        self.keys = set()  # keys that are in the cache right now
        self.heap = []  # (-length, key) pairs, the smallest pair is evicted first

    def key_added(self, key):
        self.keys.add(key)
        heapq.heappush(self.heap, (-len(key), key))

    def key_removed(self, key):
        # a heap cannot delete from the middle quickly,
        # so we only forget the key here and skip its old entry later
        self.keys.discard(key)

    def next_eviction_key(self):
        # throw away entries of keys that are no longer in the cache
        while self.heap and self.heap[0][1] not in self.keys:
            heapq.heappop(self.heap)
        return self.heap[0][1] if self.heap else ""


class RemoveHeaviestPolicy(EvictionPolicy):
    """
    REMOVE-HEAVIEST-POLICY: the key with the highest weight leaves first.
    Keys of the same weight: the lexicographically smallest key leaves first.
    """

    def __init__(self):
        self.keys = set()  # keys that are in the cache right now
        self.heap = []  # (-weight, key) pairs, the smallest pair is evicted first

    def weight(self, key):
        """Sum of character values, a = 1, b = 2, ..., z = 26."""
        return sum(ord(c) - ord("a") + 1 for c in key)

    def key_added(self, key):
        self.keys.add(key)
        heapq.heappush(self.heap, (-self.weight(key), key))

    def key_removed(self, key):
        # a heap cannot delete from the middle quickly,
        # so we only forget the key here and skip its old entry later
        self.keys.discard(key)

    def next_eviction_key(self):
        # throw away entries of keys that are no longer in the cache
        while self.heap and self.heap[0][1] not in self.keys:
            heapq.heappop(self.heap)
        return self.heap[0][1] if self.heap else ""


class EvictionPolicyFactory:
    """Simple Factory: turns a policy name into the matching policy object."""

    def create(self, policyName):
        if policyName == "REMOVE-LARGEST-POLICY":
            return RemoveLargestPolicy()
        if policyName == "REMOVE-HEAVIEST-POLICY":
            return RemoveHeaviestPolicy()
        raise ValueError("Unknown eviction policy: " + policyName)
```