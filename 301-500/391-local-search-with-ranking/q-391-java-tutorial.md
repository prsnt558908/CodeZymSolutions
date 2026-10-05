# Design Local Search with Ranking in Java

#### Problem Statement
[https://codezym.com/question/391-local-search-with-ranking](https://codezym.com/question/391-local-search-with-ranking)

Think of the index at the back of a book. To find a word, you don't read the whole book. You look the word up and jump straight to the right pages. Our search works the same way.


We keep an index that maps every **trigram** (3 characters in a row, like `pla`) to the ids of the items that contain it. A search looks up the trigrams of the query and checks only the items that contain all of them (a query of 1 or 2 characters has no trigram, so it checks every item). Each real match goes into one of the three ranking groups, and each group is sorted newest first.


Adding, updating or removing an item touches only the trigrams of that one item, so the index is never rebuilt.


This problem is mostly about picking the right data structure, so no design pattern is needed. Three small classes (`Item`, `TrigramIndex` and `LocalSearch`, where `LocalSearch` simply owns the other two) keep the code short and easy to follow. Patterns like Strategy or Observer would only add extra layers here.


We will first build a simple brute force solution, see why it is slow, and then improve it with the trigram index.

---

## What We Need to Build

- `addOrUpdateItem` stores a new item, or replaces every field of an existing one.
- `removeItem` deletes an item and returns whether it existed.
- `search` returns at most `limit` ids, ranked in 3 groups:
  1. the name **starts with** the query
  2. the name **contains** the query somewhere else
  3. only the **content** contains the query
- Inside a group, the most recent `updatedAt` comes first. On a tie, the smaller id comes first. Ids are compared in dictionary order, so `a10` comes before `a9`.
- Name, content and type are compared case-insensitively. An empty `typeFilter` means "any type".

---

## Do We Need a Design Pattern?

Not really. All the hard work is about storing text so that search is fast. We simply split the code by job:

- `Item` holds the data of one item.
- `TrigramIndex` finds candidate items fast.
- `LocalSearch` is the public entry point. It keeps the items and the index in sync and ranks the results.


Two patterns look tempting at first, but they don't fit this problem:

- **Strategy** for the ranking rule. It helps when the caller can choose between several ranking rules. Here the rule is fixed, so an extra interface and class would add code without adding any flexibility. One comparator is enough.
- **Observer** to keep the index in sync with the items. It shines when many listeners react to the same change. We have exactly one listener (the index) and one place where items change, so a direct `index.update(...)` call is simpler and keeps the order of steps obvious.

---

## Solution 1: Brute Force (Check Every Item)

### Idea

Keep all items in a `HashMap` (id -> item). For every search, walk through **all** items:

1. Skip the item if its type does not match the filter.
2. If the name starts with the query, put the item in group 1.
3. Else if the name contains the query, put it in group 2.
4. Else if the content contains the query, put it in group 3.

Then sort each group (newest first, then smaller id) and read the groups in order until we have `limit` ids.


We save `type`, `name` and `content` in lower case when an item is stored. After that, case-insensitive matching is just a normal `startsWith` or `contains` with the lower-cased query.


Why three lists instead of one? Each list is one priority group, so the comparator never needs a "group number". It only has to handle "newest first, then smaller id".

### Code

```java
import java.util.*;

/**
 * One stored item.
 * Text fields are saved in lower case, so every comparison is case-insensitive.
 */
class Item {
    String id;
    String type;
    String name;
    String content;
    long updatedAt;

    Item(String id, String type, String name, String content, long updatedAt) {
        this.id = id;
        this.type = type.toLowerCase();
        this.name = name.toLowerCase();
        this.content = content.toLowerCase();
        this.updatedAt = updatedAt;
    }
}

public class LocalSearch {
    // id -> item
    Map<String, Item> items = new HashMap<>();

    public LocalSearch() {
    }

    public void addOrUpdateItem(String id, String type, String name, String content, long updatedAt) {
        // put() simply replaces the old item when the id already exists
        items.put(id, new Item(id, type, name, content, updatedAt));
    }

    public boolean removeItem(String id) {
        return items.remove(id) != null;
    }

    public List<String> search(String query, String typeFilter, int limit) {
        String q = query.toLowerCase();
        String type = typeFilter.toLowerCase();

        // The three priority groups, best group first
        List<Item> nameStarts = new ArrayList<>();
        List<Item> nameContains = new ArrayList<>();
        List<Item> contentOnly = new ArrayList<>();

        // Check every single item
        for (Item item : items.values()) {
            if (!type.isEmpty() && !item.type.equals(type)) {
                continue;
            }
            if (item.name.startsWith(q)) {
                nameStarts.add(item);
            } else if (item.name.contains(q)) {
                nameContains.add(item);
            } else if (item.content.contains(q)) {
                contentOnly.add(item);
            }
        }

        List<String> result = new ArrayList<>();
        addNewestFirst(nameStarts, result, limit);
        addNewestFirst(nameContains, result, limit);
        addNewestFirst(contentOnly, result, limit);
        return result;
    }

    /**
     * Sorts one group (newest first, then smaller id)
     * and copies its ids into result until result holds limit ids.
     */
    void addNewestFirst(List<Item> group, List<String> result, int limit) {
        if (result.size() >= limit) {
            return;
        }
        group.sort((a, b) -> a.updatedAt != b.updatedAt
                ? Long.compare(b.updatedAt, a.updatedAt)
                : a.id.compareTo(b.id));
        for (Item item : group) {
            if (result.size() >= limit) {
                return;
            }
            result.add(item.id);
        }
    }
}
```

### What is wrong with it?

- Every search reads the full name and content of **every** item, even when only two items match. The cost of a search grows with the size of the whole collection, not with the number of results.
- With up to 1,000,000 items and up to 10,000 characters of content each, a single search may have to read gigabytes of text.


We need a way to look only at the items that **can** match. That is exactly what an index gives us.

---

## Solution 2: Trigram Index (Optimized)

### What is a trigram?

A trigram is just 3 characters in a row. `plan` has 2 trigrams: `pla` and `lan`. `planet` has 4: `pla`, `lan`, `ane` and `net`.

### The index

We keep a map from each trigram to the set of item ids whose name or content contains it. Say we store these three items:

| Id | Name |
|---|---|
| `a1` | Plan Meeting |
| `a2` | Plaza Lane |
| `a3` | Budget Review |

A small part of the index then looks like this:

| Trigram | Item ids |
|---|---|
| `pla` | a1, a2 |
| `lan` | a1, a2 |
| `bud` | a3 |

### Finding candidates

If an item contains `plan`, it must contain **every** trigram of `plan`, so both `pla` and `lan`. That means only the items found in **all** of these sets can match.


For the query `plan`, both sets give `{a1, a2}`. Item `a3` is never even looked at.


To intersect the sets cheaply, we start from the smallest set and keep only the ids that also appear in every other set.

### Why we still double-check

A candidate is not always a real match. `Plaza Lane` contains `pla` and `lan` but not `plan`, because the two trigrams come from different places.


So every candidate gets a real `startsWith` or `contains` check. The same check also tells us the item's group. Candidates are usually few, so this step is cheap.

### Queries with 1 or 2 characters

They have no trigram, so the index cannot help. For them we simply check every item, exactly like Solution 1.


That is a fair deal: such short queries tend to match a large part of the collection anyway, so even a perfect index would hand back most of the items.

### Updating only what changed

When an item changes, we compute the trigrams of its old version and of its new version:

- trigrams only in the old version: remove the id from their sets
- trigrams only in the new version: add the id to their sets
- trigrams in both versions: leave them alone


For example, if an item without content is renamed from `Plan` to `Plans`, the only new trigram is `ans`. So exactly one set changes. This is what the problem means by "update only the affected search-index entries".


Adding an item is the same as updating from an empty old version. Removing is the same as updating to an empty new version. So one method, `update(id, oldItem, newItem)`, covers all three cases.

### Important classes and data structures

- **`Map<String, Item> items`** (id -> item): finds an item in O(1). On update and remove we need the old version of the item to know which trigrams to clean up.
- **`Map<String, Set<String>> idsByTrigram`** (the index): a `Set` adds, removes and checks an id in O(1) and never stores the same id twice. Empty sets are deleted, so unused trigrams do not pile up.
- **`Item`**: keeps lower-cased text, so every check is a plain `startsWith` or `contains`. An item is never changed after it is created, so its old version always gives back exactly the trigrams we indexed for it.
- **Three group lists**: one list per priority group. Sorting each group on its own keeps the comparator simple.

### Code

```java
import java.util.*;

/**
 * One stored item.
 * Text fields are saved in lower case, so every comparison is case-insensitive.
 */
class Item {
    String id;
    String type;
    String name;
    String content;
    long updatedAt;

    Item(String id, String type, String name, String content, long updatedAt) {
        this.id = id;
        this.type = type.toLowerCase();
        this.name = name.toLowerCase();
        this.content = content.toLowerCase();
        this.updatedAt = updatedAt;
    }
}

/**
 * The search index.
 * Maps every trigram (3 characters in a row) to the ids of the items
 * whose name or content contains it.
 */
class TrigramIndex {
    Map<String, Set<String>> idsByTrigram = new HashMap<>();

    /** All distinct trigrams found in the item's name and content. */
    Set<String> trigramsOf(Item item) {
        Set<String> trigrams = new HashSet<>();
        addTrigrams(item.name, trigrams);
        addTrigrams(item.content, trigrams);
        return trigrams;
    }

    void addTrigrams(String text, Set<String> trigrams) {
        for (int i = 0; i + 3 <= text.length(); i++) {
            trigrams.add(text.substring(i, i + 3));
        }
    }

    /**
     * Moves the index from the old version of an item to the new version.
     * oldItem is null for a brand new item. newItem is null for a removal.
     * Only the trigrams that really changed are touched.
     */
    void update(String id, Item oldItem, Item newItem) {
        Set<String> oldTrigrams = oldItem == null ? new HashSet<>() : trigramsOf(oldItem);
        Set<String> newTrigrams = newItem == null ? new HashSet<>() : trigramsOf(newItem);

        // trigrams that disappeared
        for (String trigram : oldTrigrams) {
            if (!newTrigrams.contains(trigram)) {
                Set<String> ids = idsByTrigram.get(trigram);
                ids.remove(id);
                if (ids.isEmpty()) {
                    idsByTrigram.remove(trigram);
                }
            }
        }
        // trigrams that are new
        for (String trigram : newTrigrams) {
            if (!oldTrigrams.contains(trigram)) {
                idsByTrigram.computeIfAbsent(trigram, t -> new HashSet<>()).add(id);
            }
        }
    }

    /**
     * Ids of the items that contain every trigram of the query (query has 3+ characters).
     * A few of them may still not contain the full query, so the caller double-checks.
     */
    Set<String> candidates(String query) {
        Set<String> queryTrigrams = new HashSet<>();
        addTrigrams(query, queryTrigrams);

        List<Set<String>> idSets = new ArrayList<>();
        for (String trigram : queryTrigrams) {
            Set<String> ids = idsByTrigram.get(trigram);
            if (ids == null) {
                return new HashSet<>(); // no item has this trigram, so nothing can match
            }
            idSets.add(ids);
        }

        // Find the smallest set, then keep only its ids that appear in every set
        Set<String> smallest = idSets.get(0);
        for (Set<String> ids : idSets) {
            if (ids.size() < smallest.size()) {
                smallest = ids;
            }
        }
        Set<String> result = new HashSet<>(smallest);
        for (Set<String> ids : idSets) {
            result.retainAll(ids);
        }
        return result;
    }
}

public class LocalSearch {
    // id -> item
    Map<String, Item> items = new HashMap<>();
    TrigramIndex index = new TrigramIndex();

    public LocalSearch() {
    }

    public void addOrUpdateItem(String id, String type, String name, String content, long updatedAt) {
        Item newItem = new Item(id, type, name, content, updatedAt);
        Item oldItem = items.put(id, newItem); // null when the id is new
        index.update(id, oldItem, newItem);
    }

    public boolean removeItem(String id) {
        Item oldItem = items.remove(id);
        if (oldItem == null) {
            return false;
        }
        index.update(id, oldItem, null);
        return true;
    }

    public List<String> search(String query, String typeFilter, int limit) {
        String q = query.toLowerCase();
        String type = typeFilter.toLowerCase();

        // A query shorter than 3 characters has no trigram, so we check every item.
        // Otherwise we only check the few items suggested by the index.
        Collection<Item> toCheck = q.length() < 3 ? items.values() : candidateItems(q);

        // The three priority groups, best group first
        List<Item> nameStarts = new ArrayList<>();
        List<Item> nameContains = new ArrayList<>();
        List<Item> contentOnly = new ArrayList<>();

        for (Item item : toCheck) {
            if (!type.isEmpty() && !item.type.equals(type)) {
                continue;
            }
            if (item.name.startsWith(q)) {
                nameStarts.add(item);
            } else if (item.name.contains(q)) {
                nameContains.add(item);
            } else if (item.content.contains(q)) {
                contentOnly.add(item);
            }
            // otherwise it is not a real match (for example, its trigrams came from different places)
        }

        List<String> result = new ArrayList<>();
        addNewestFirst(nameStarts, result, limit);
        addNewestFirst(nameContains, result, limit);
        addNewestFirst(contentOnly, result, limit);
        return result;
    }

    /** Turns the candidate ids from the index into items. */
    List<Item> candidateItems(String q) {
        List<Item> candidates = new ArrayList<>();
        for (String id : index.candidates(q)) {
            candidates.add(items.get(id));
        }
        return candidates;
    }

    /**
     * Sorts one group (newest first, then smaller id)
     * and copies its ids into result until result holds limit ids.
     */
    void addNewestFirst(List<Item> group, List<String> result, int limit) {
        if (result.size() >= limit) {
            return;
        }
        group.sort((a, b) -> a.updatedAt != b.updatedAt
                ? Long.compare(b.updatedAt, a.updatedAt)
                : a.id.compareTo(b.id));
        for (Item item : group) {
            if (result.size() >= limit) {
                return;
            }
            result.add(item.id);
        }
    }
}
```

### Complexity

Let `L` be the length of one item's name plus content, `T` the total length of all names and contents, `q` the query length, `S` the size of the smallest set among the query's trigrams, `C` the number of candidates and `M` the number of matching items. Each `contains` check is counted as one pass over the text.

| Operation | Brute Force | Trigram Index |
|---|---|---|
| `addOrUpdateItem` | O(L) | O(L) |
| `removeItem` | O(1) | O(L) |
| `search` | O(T + M log M) | O(S · q + C · L + M log M) |


For queries with fewer than 3 characters, the trigram solution costs the same as brute force.


The trade-off: adding an item now costs more (one set update per trigram), and the index needs extra memory, roughly one entry per distinct trigram of every item. In return, a search reads only the few items that can actually match, instead of the whole collection.