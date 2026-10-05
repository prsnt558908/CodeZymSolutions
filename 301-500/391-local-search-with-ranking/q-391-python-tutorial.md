# Design Local Search with Ranking in Python

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

- **Strategy** for the ranking rule. It helps when the caller can choose between several ranking rules. Here the rule is fixed, so an extra class would add code without adding any flexibility. One sort key is enough.
- **Observer** to keep the index in sync with the items. It shines when many listeners react to the same change. We have exactly one listener (the index) and one place where items change, so a direct `self.index.update(...)` call is simpler and keeps the order of steps obvious.

---

## Solution 1: Brute Force (Check Every Item)

### Idea

Keep all items in a `dict` (id -> item). For every search, walk through **all** items:

1. Skip the item if its type does not match the filter.
2. If the name starts with the query, put the item in group 1.
3. Else if the name contains the query, put it in group 2.
4. Else if the content contains the query, put it in group 3.

Then sort each group (newest first, then smaller id) and read the groups in order until we have `limit` ids.


We save `type`, `name` and `content` in lower case when an item is stored. After that, case-insensitive matching is just a normal `startswith` or `in` check with the lower-cased query.


Why three lists instead of one? Each list is one priority group, so the sort key never needs a "group number". The key `(-updated_at, id)` is enough: a bigger `updated_at` becomes a smaller negative number, so newer items come first, and equal times fall back to the id.

### Code

```python
class Item:
    """One stored item.

    Text fields are saved in lower case, so every comparison
    is case-insensitive.
    """

    def __init__(self, id, type, name, content, updated_at):
        self.id = id
        self.type = type.lower()
        self.name = name.lower()
        self.content = content.lower()
        self.updated_at = updated_at


class LocalSearch:
    def __init__(self):
        self.items = {}  # id -> Item

    def addOrUpdateItem(self, id, type, name, content, updatedAt):
        # assigning to an existing key simply replaces the old item
        self.items[id] = Item(id, type, name, content, updatedAt)

    def removeItem(self, id):
        return self.items.pop(id, None) is not None

    def search(self, query, typeFilter, limit):
        q = query.lower()
        item_type = typeFilter.lower()

        # the three priority groups, best group first
        name_starts, name_contains, content_only = [], [], []

        # check every single item
        for item in self.items.values():
            if item_type and item.type != item_type:
                continue
            if item.name.startswith(q):
                name_starts.append(item)
            elif q in item.name:
                name_contains.append(item)
            elif q in item.content:
                content_only.append(item)

        result = []
        for group in (name_starts, name_contains, content_only):
            self.add_newest_first(group, result, limit)
        return result

    def add_newest_first(self, group, result, limit):
        """Sorts one group (newest first, then smaller id) and copies
        its ids into result until result holds limit ids."""
        if len(result) >= limit:
            return
        group.sort(key=lambda item: (-item.updated_at, item.id))
        for item in group[:limit - len(result)]:
            result.append(item.id)
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

We keep a dictionary from each trigram to the set of item ids whose name or content contains it. Say we store these three items:

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


To intersect the sets cheaply, we pick the smallest set and keep only its ids that also appear in every other set. Python's `set.intersection` does this in one call.

### Why we still double-check

A candidate is not always a real match. `Plaza Lane` contains `pla` and `lan` but not `plan`, because the two trigrams come from different places.


So every candidate gets a real `startswith` or `in` check. The same check also tells us the item's group. Candidates are usually few, so this step is cheap.

### Queries with 1 or 2 characters

They have no trigram, so the index cannot help. For them we simply check every item, exactly like Solution 1.


That is a fair deal: such short queries tend to match a large part of the collection anyway, so even a perfect index would hand back most of the items.

### Updating only what changed

When an item changes, we compute the trigrams of its old version and of its new version:

- trigrams only in the old version (`old - new`): remove the id from their sets
- trigrams only in the new version (`new - old`): add the id to their sets
- trigrams in both versions: leave them alone


For example, if an item without content is renamed from `Plan` to `Plans`, the only new trigram is `ans`. So exactly one set changes. This is what the problem means by "update only the affected search-index entries".


Adding an item is the same as updating from an empty old version. Removing is the same as updating to an empty new version. So one method, `update(id, old_item, new_item)`, covers all three cases.

### Important classes and data structures

- **`self.items`** (`dict`, id -> `Item`): finds an item in O(1). On update and remove we need the old version of the item to know which trigrams to clean up.
- **`self.ids_by_trigram`** (`dict`, trigram -> `set` of ids): a `set` adds, removes and checks an id in O(1) and never stores the same id twice. Set difference (`-`) and `intersection` keep the update and search steps short. Empty sets are deleted, so unused trigrams do not pile up.
- **`Item`**: keeps lower-cased text, so every check is a plain `startswith` or `in`. An item is never changed after it is created, so its old version always gives back exactly the trigrams we indexed for it.
- **Three group lists**: one list per priority group. Sorting each group on its own keeps the sort key simple.

### Code

```python
class Item:
    """One stored item.

    Text fields are saved in lower case, so every comparison
    is case-insensitive.
    """

    def __init__(self, id, type, name, content, updated_at):
        self.id = id
        self.type = type.lower()
        self.name = name.lower()
        self.content = content.lower()
        self.updated_at = updated_at


class TrigramIndex:
    """The search index.

    Maps every trigram (3 characters in a row) to the ids of the items
    whose name or content contains it.
    """

    def __init__(self):
        self.ids_by_trigram = {}  # trigram -> set of ids

    def trigrams_of(self, text):
        """All distinct trigrams of a piece of text."""
        return {text[i:i + 3] for i in range(len(text) - 2)}

    def item_trigrams(self, item):
        """All distinct trigrams found in the item's name and content."""
        return self.trigrams_of(item.name) | self.trigrams_of(item.content)

    def update(self, id, old_item, new_item):
        """Moves the index from the old version of an item to the new one.

        old_item is None for a brand new item. new_item is None for a
        removal. Only the trigrams that really changed are touched.
        """
        old_trigrams = set() if old_item is None else self.item_trigrams(old_item)
        new_trigrams = set() if new_item is None else self.item_trigrams(new_item)

        # trigrams that disappeared
        for trigram in old_trigrams - new_trigrams:
            ids = self.ids_by_trigram[trigram]
            ids.discard(id)
            if not ids:
                del self.ids_by_trigram[trigram]
        # trigrams that are new
        for trigram in new_trigrams - old_trigrams:
            self.ids_by_trigram.setdefault(trigram, set()).add(id)

    def candidates(self, query):
        """Ids of the items that contain every trigram of the query.

        The query must have 3 or more characters. A few of these items may
        still not contain the full query, so the caller double-checks them.
        """
        id_sets = []
        for trigram in self.trigrams_of(query):
            ids = self.ids_by_trigram.get(trigram)
            if ids is None:
                return set()  # no item has this trigram, so nothing can match
            id_sets.append(ids)

        # find the smallest set, then keep only its ids that appear in every set
        smallest = min(id_sets, key=len)
        return smallest.intersection(*id_sets)


class LocalSearch:
    def __init__(self):
        self.items = {}  # id -> Item
        self.index = TrigramIndex()

    def addOrUpdateItem(self, id, type, name, content, updatedAt):
        new_item = Item(id, type, name, content, updatedAt)
        old_item = self.items.get(id)  # None when the id is new
        self.items[id] = new_item
        self.index.update(id, old_item, new_item)

    def removeItem(self, id):
        old_item = self.items.pop(id, None)
        if old_item is None:
            return False
        self.index.update(id, old_item, None)
        return True

    def search(self, query, typeFilter, limit):
        q = query.lower()
        item_type = typeFilter.lower()

        # A query shorter than 3 characters has no trigram, so we check
        # every item. Otherwise we only check the items the index suggests.
        if len(q) < 3:
            to_check = self.items.values()
        else:
            to_check = [self.items[id] for id in self.index.candidates(q)]

        # the three priority groups, best group first
        name_starts, name_contains, content_only = [], [], []

        for item in to_check:
            if item_type and item.type != item_type:
                continue
            if item.name.startswith(q):
                name_starts.append(item)
            elif q in item.name:
                name_contains.append(item)
            elif q in item.content:
                content_only.append(item)
            # otherwise it is not a real match
            # (for example, its trigrams came from different places)

        result = []
        for group in (name_starts, name_contains, content_only):
            self.add_newest_first(group, result, limit)
        return result

    def add_newest_first(self, group, result, limit):
        """Sorts one group (newest first, then smaller id) and copies
        its ids into result until result holds limit ids."""
        if len(result) >= limit:
            return
        group.sort(key=lambda item: (-item.updated_at, item.id))
        for item in group[:limit - len(result)]:
            result.append(item.id)
```

### Complexity

Let `L` be the length of one item's name plus content, `T` the total length of all names and contents, `q` the query length, `S` the size of the smallest set among the query's trigrams, `C` the number of candidates and `M` the number of matching items. Each `in` check is counted as one pass over the text.

| Operation | Brute Force | Trigram Index |
|---|---|---|
| `addOrUpdateItem` | O(L) | O(L) |
| `removeItem` | O(1) | O(L) |
| `search` | O(T + M log M) | O(S · q + C · L + M log M) |


For queries with fewer than 3 characters, the trigram solution costs the same as brute force.


The trade-off: adding an item now costs more (one set update per trigram), and the index needs extra memory, roughly one entry per distinct trigram of every item. In return, a search reads only the few items that can actually match, instead of the whole collection.