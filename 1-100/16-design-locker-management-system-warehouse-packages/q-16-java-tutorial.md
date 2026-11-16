# Design Locker Management System for Warehouse Packages in Java

#### Problem Statement
[https://codezym.com/question/16-design-locker-management-system-warehouse-packages](https://codezym.com/question/16-design-locker-management-system-warehouse-packages)


## Core Idea

This problem comes down to one question: how do we quickly find the smallest free locker of a given size?

The answer is to keep the ids of all free lockers of each size in a **sorted set**. The smallest free id is then always at the front. Occupying a locker takes its id out of the set, and freeing it puts the id back.

We do not need a design pattern here. A few small classes with one job each, plus the right data structures, keep the code short and fast. Later we will see why patterns like State or Strategy only add extra code for this problem.


## A Small Trap: Lexicographic Order

Locker ids are strings. So "smallest" means dictionary order, not number order.

Strings are compared one character at a time, from the left. Compare `M10` and `M2`: both start with `M`. Next, `1` is smaller than `2`. So `M10` comes before `M2`.

Example: lockers `M1` to `M12` exist and only `M1` is occupied. Now `findSlot("M")` returns `M10`, not `M2`.

Java's `TreeSet<String>` sorts strings in exactly this order, so we need no extra code for it.


## Basic Solution: Check Every Locker

Let's start simple. Keep every locker in one list. Each `Locker` stores its `id`, its `size` and an `occupied` flag.

To find a free locker, walk the whole list and remember the smallest free id of the asked size:

```java
public String findSlot(String size) {
    String best = "";
    for (Locker locker : allLockers) {
        if (locker.size.equals(size) && !locker.occupied) {
            if (best.isEmpty() || locker.id.compareTo(best) < 0) {
                best = locker.id;
            }
        }
    }
    return best;
}
```

`occupySlot` and `freeSlot` also walk the list to find the locker with the given id.


### What is wrong with it?

Every call looks at every locker. There can be 200,000 lockers. With 200,000 calls, that is 40 billion checks, which is far too slow.

There are two slow parts:

1. Finding a locker by its id.
2. Finding the smallest free locker of a size.

Let's fix both.


## Better Solution: Sorted Set of Free Lockers

**Fix 1: a map from id to locker.** With `Map<String, Locker>`, a call like `occupySlot("M3")` gets locker `M3` in one step.

**Fix 2: a sorted set of free ids for each size.** A `TreeSet<String>` keeps its items sorted all the time. `first()` gives the smallest free id right away, and adding or removing an id is fast too.

We keep one sorted set per size, because `findSlot("M")` should only look at `M` lockers.

Why not a min-heap (`PriorityQueue`)? `occupySlot` can be called on any locker, not only the smallest one. A heap cannot remove an item from the middle quickly. A sorted set can.


### The Classes

**`Locker`**: one locker. It stores `id`, `size` and `occupied`. The `occupied` flag tells us in one step if a locker is in use.

**`LockerGroup`**: all lockers of one size. Every size needs two things: `nextNumber` to make new ids (`M1`, `M2`, ...) and `freeIds`, the sorted set of free ids. This class keeps both together, so each size is handled in one place.

**`LockerManager`**: the entry point. It holds `groups` (size to `LockerGroup`) and `lockers` (id to `Locker`). It checks the input, picks the right group or locker, and passes the work on.

`groups` has only the 5 valid sizes as keys. So checking a size is just a map lookup. If the size is not found, it is invalid.


### How Each Call Works

- **`addSlot(size)`**: get the group for `size`. If there is none, the size is invalid, so do nothing. Otherwise create the next locker, for example `M3`, save it in `lockers` and add its id to the free set.
- **`findSlot(size)`**: get the group. If there is none, or its free set is empty, return `""`. Otherwise return the first id in the free set.
- **`occupySlot(lockerId)`**: get the locker. If it does not exist or is already occupied, return `false`. Otherwise mark it occupied, remove its id from the free set and return `true`.
- **`freeSlot(lockerId)`**: get the locker. If it does not exist or is already free, return `false`. Otherwise mark it free, put its id back in the free set and return `true`.

Note that `findSlot` only looks. It does not take the locker. A locker is taken only when `occupySlot` is called.


### Dry Run (Example 2)

| Call | Result | Free `S` lockers after the call |
|---|---|---|
| `addSlot("S")` | creates `S1` | `{S1}` |
| `addSlot("S")` | creates `S2` | `{S1, S2}` |
| `findSlot("S")` | `"S1"` | `{S1, S2}` |
| `occupySlot("S1")` | `true` | `{S2}` |
| `occupySlot("S1")` | `false`, already occupied | `{S2}` |
| `findSlot("S")` | `"S2"` | `{S2}` |


## Do We Need a Design Pattern?

Two patterns look like a good fit at first. Neither helps here.

**State pattern.** A locker is either free or occupied, so it looks like a small state machine. But there are only two states and two simple rules: you cannot occupy a locker that is already occupied, and you cannot free a locker that is already free. One `boolean` and one `if` handle this. The State pattern would need an interface and two state classes for the same result.

**Strategy pattern.** It is useful when there are many ways to pick a locker, like nearest first or least used first. Here there is only one rule: smallest id. A strategy interface with a single implementation is extra code with no benefit.

If a future version asks for different ways to pick a locker, Strategy would be the first thing to add.


## Complexity

n is the number of lockers.

| Operation | Time |
|---|---|
| `addSlot` | O(log n) |
| `findSlot` | O(log n) |
| `occupySlot` | O(log n) |
| `freeSlot` | O(log n) |

Space: O(n) for the lockers and the free sets.


## Java Code

```java
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * One locker. It knows its id, its size and if a package is inside.
 */
class Locker {
    String id;
    String size;
    boolean occupied = false;

    Locker(String id, String size) {
        this.id = id;
        this.size = size;
    }
}

/**
 * All lockers of one size.
 * Free locker ids are kept in a sorted set, so the
 * lexicographically smallest free id is always the first one.
 */
class LockerGroup {
    String size;
    int nextNumber = 1;
    TreeSet<String> freeIds = new TreeSet<>();

    LockerGroup(String size) {
        this.size = size;
    }

    /** Creates the next locker of this size: M1, M2, M3 ... */
    Locker createLocker() {
        Locker locker = new Locker(size + nextNumber, size);
        nextNumber++;
        freeIds.add(locker.id);
        return locker;
    }

    /** Returns the smallest free id, or "" if no locker is free. */
    String findFreeLocker() {
        if (freeIds.isEmpty()) return "";
        return freeIds.first();
    }

    void occupy(Locker locker) {
        locker.occupied = true;
        freeIds.remove(locker.id);
    }

    void free(Locker locker) {
        locker.occupied = false;
        freeIds.add(locker.id);
    }
}

public class LockerManager {
    // size -> all lockers of that size. Only the 5 valid sizes are keys.
    Map<String, LockerGroup> groups = new HashMap<>();
    // locker id -> locker, so we can reach any locker in one step
    Map<String, Locker> lockers = new HashMap<>();

    public LockerManager() {
        String[] sizes = {"S", "M", "L", "XL", "XXL"};
        for (String size : sizes) {
            groups.put(size, new LockerGroup(size));
        }
    }

    public void addSlot(String size) {
        LockerGroup group = groups.get(size);
        if (group == null) return; // invalid size, do nothing
        Locker locker = group.createLocker();
        lockers.put(locker.id, locker);
    }

    public String findSlot(String size) {
        LockerGroup group = groups.get(size);
        if (group == null) return "";
        return group.findFreeLocker();
    }

    public boolean occupySlot(String lockerId) {
        Locker locker = lockers.get(lockerId);
        if (locker == null || locker.occupied) return false;
        groups.get(locker.size).occupy(locker);
        return true;
    }

    public boolean freeSlot(String lockerId) {
        Locker locker = lockers.get(lockerId);
        if (locker == null || !locker.occupied) return false;
        groups.get(locker.size).free(locker);
        return true;
    }
}
```