# Design Browser History Manager in Python

#### Problem Statement
[https://codezym.com/question/390-browser-history-manager](https://codezym.com/question/390-browser-history-manager)

A browser history is just a pile of visits. The tricky part is that we need to look at that pile in three different ways: by **visit id** (delete one visit), by **URL** (delete every visit to a page) and by **time** (show recent visits, search a time range, clear a time range).

So the core idea is to keep three simple structures in sync: a dictionary from visit id to visit, a dictionary from URL to its visit ids, and a list that is always kept sorted in display order, newest first. Every read walks the sorted list from the right starting point, which we find with binary search, and one helper removes a visit from all three structures at once.

This is a data structure problem more than a design pattern problem. One `Visit` class and one `GlobalBrowsingHistory` class are enough. Patterns like Singleton or Strategy look tempting here, but they would only add code without making the solution faster or clearer, as explained below.

## Understanding the Problem

Each method needs a different view of the same visits:

| Method | What it really needs |
|---|---|
| `recordVisit` | add a visit to every view (skip private visits) |
| `getRecentHistory` | visits sorted by time, newest first |
| `searchHistory` | visits inside a time range, newest first, plus a text check |
| `deleteVisit` | find one visit by its id |
| `deleteHistoryByUrl` | find all visits to one exact URL |
| `clearHistory` | find all visits inside a time range |
| `clearAllHistory` | wipe everything |

Two rules are used everywhere:

- **Display order:** bigger `visitedAt` first. If two visits have the same time, the lexicographically smaller `visitId` comes first.
- **Output format:** `"visitId,url,title,visitedAt"`.

We don't need to model tabs or windows at all. All normal visits go into one shared history, so deleting history can never close a tab or change its page.

## Do We Need a Design Pattern?

**Singleton** looks like a natural fit because of the word "Global" in the class name. But "global" here means one history shared by all tabs and windows of **one browser profile**, not one object for the whole program. A browser can have many profiles, and every test case creates a fresh `GlobalBrowsingHistory`. A Singleton would carry old visits from one test into the next. A normal class, created once per profile, is the right choice.

**Strategy** could turn "text matches" and "time is in range" into separate filter classes. But there are only two fixed rules. Also, in the fast solution the time range is not a filter at all: we use it to jump straight to the right spot in the sorted list. Filter classes would add code and hide that speed up.

So we stick to plain, simple classes.

## Solution 1: Simple Approach (Sort on Every Read)

The most direct idea: keep every visit in a dictionary keyed by `visitId`. When someone asks for history, collect the visits we need, sort them and return the first `limit`.

### The `Visit` class

`Visit` holds one history entry. It also knows three useful things about itself:

- `sortKey()` returns `(-visitedAt, visitId)`. Python compares tuples item by item, so sorting these keys in normal ascending order gives exactly our display order: a bigger `visitedAt` becomes a smaller `-visitedAt` and comes first, and ties fall back to the smaller `visitId`.
- `__str__()` builds the required `"visitId,url,title,visitedAt"` string.
- `matches()` does the case insensitive text check. Lower case copies of the URL and title are made once, when the visit is saved, so searches don't have to lowercase them again and again.

### A small trick

`getRecentHistory(limit)` is the same as searching for an empty query across all of time, because an empty query matches every entry. So it simply calls `searchHistory("", 0, MAX_TIME, limit)`, where `MAX_TIME` is `10**18`, the largest visit time the constraints allow.

### Code

```python
from typing import List

MAX_TIME = 10**18  # largest visit time allowed by the constraints


class Visit:
    """One saved history entry."""

    def __init__(self, visitId: str, url: str, title: str, visitedAt: int):
        self.visitId = visitId
        self.url = url
        self.title = title
        self.visitedAt = visitedAt
        # lower case copies, made once, so searches can ignore letter case cheaply
        self.lowerUrl = url.lower()
        self.lowerTitle = title.lower()

    def matches(self, lowerQuery: str) -> bool:
        """True if the url or the title contains the (already lower case) query."""
        return lowerQuery in self.lowerUrl or lowerQuery in self.lowerTitle

    def sortKey(self):
        """Newer visits first (so the time is negated), then the smaller visitId."""
        return (-self.visitedAt, self.visitId)

    def __str__(self) -> str:
        return f"{self.visitId},{self.url},{self.title},{self.visitedAt}"


class GlobalBrowsingHistory:
    def __init__(self):
        self.visitsById = {}  # visitId -> Visit

    def recordVisit(self, visitId: str, url: str, title: str, visitedAt: int, privateVisit: bool):
        if privateVisit:
            return  # private visits are never saved
        self.visitsById[visitId] = Visit(visitId, url, title, visitedAt)

    def getRecentHistory(self, limit: int) -> List[str]:
        # recent history is just a search with an empty query over all time
        return self.searchHistory("", 0, MAX_TIME, limit)

    def searchHistory(self, query: str, startTime: int, endTime: int, limit: int) -> List[str]:
        lowerQuery = query.lower()
        matching = [visit for visit in self.visitsById.values()
                    if startTime <= visit.visitedAt <= endTime and visit.matches(lowerQuery)]

        # the slow part: all matches are sorted again on every single call
        matching.sort(key=lambda visit: visit.sortKey())
        return [str(visit) for visit in matching[:limit]]

    def deleteVisit(self, visitId: str) -> bool:
        return self.visitsById.pop(visitId, None) is not None

    def deleteHistoryByUrl(self, url: str) -> int:
        idsToDelete = [visit.visitId for visit in self.visitsById.values() if visit.url == url]
        for visitId in idsToDelete:
            del self.visitsById[visitId]
        return len(idsToDelete)

    def clearHistory(self, startTime: int, endTime: int) -> int:
        idsToDelete = [visit.visitId for visit in self.visitsById.values()
                       if startTime <= visit.visitedAt <= endTime]
        for visitId in idsToDelete:
            del self.visitsById[visitId]
        return len(idsToDelete)

    def clearAllHistory(self) -> int:
        count = len(self.visitsById)
        self.visitsById.clear()
        return count
```

### Complexity

`n` is the number of stored visits.

| Method | Time |
|---|---|
| `recordVisit`, `deleteVisit` | O(1) |
| `getRecentHistory`, `searchHistory` | O(n log n) |
| `deleteHistoryByUrl`, `clearHistory`, `clearAllHistory` | O(n) |

### What is slow here?

1. **Every read sorts again.** Even if nothing changed, each call sorts the matching visits from scratch. With up to 100,000 calls, that is a lot of repeated work.
2. **Every read looks at all visits.** We return at most 500 entries, but we still check every visit, even those far outside the time range.
3. **Bulk deletes check every visit.** Deleting one URL or one time range scans the whole history, even if only a few visits match.

## Solution 2: Better Approach (Keep Visits Sorted All the Time)

Let's fix these problems one by one.

### Improvement 1: Keep the visits sorted

Instead of sorting at read time, we keep the visits sorted all the time. Python has no built-in sorted set, so we build one from two simple pieces: a plain list that is always kept sorted, and binary search from the `bisect` module to find positions in it. (The third party `sortedcontainers` library has a `SortedList`, but it is not part of the standard library.)

The list, `sortedKeys`, stores each visit's sort key `(-visitedAt, visitId)`. The full `Visit` objects stay in the dictionary. `insort()` uses binary search to find where a new key belongs and inserts it there, so the list stays sorted.

Recent history is now just "take the first `limit` keys", with no sorting at all.

### Improvement 2: Jump straight to the time range

Because the list is sorted by time, all visits inside `[startTime, endTime]` sit next to each other. So we can:

1. Jump to the first visit with `visitedAt <= endTime`.
2. Walk forward, towards older visits.
3. Stop as soon as a visit is older than `startTime`, or we already have `limit` matches.

To jump, we use a **marker** key `(-endTime, "")`. `bisect_left(sortedKeys, marker)` returns the index where the marker would be inserted. An empty string is smaller than every real id (a real id has at least 1 character), so that index is exactly where the visits with `visitedAt <= endTime` begin.

Here is a search with `endTime = 500` and `startTime = 400`:

```
sortedKeys:        (-600,"v9") (-500,"v1") (-500,"v7") (-400,"v2") (-300,"v5")
marker (-500,""):              ^ index 1, because "" < "v1"
walk to 400:                   (-500,"v1") (-500,"v7") (-400,"v2")  stop at 300
```

`clearHistory` finds both ends of its block with two binary searches. The block starts at the index for `endTime` and stops at the index for `startTime - 1`, which is where the visits older than `startTime` begin. Then `del sortedKeys[start:end]` cuts the whole block out in one step.

### Improvement 3: Group visits by URL

A dictionary maps each URL to a set with the ids of its visits. `deleteHistoryByUrl` now touches only that URL's visits instead of scanning everything. The key is the exact URL, so `https://A.com` and `https://a.com` stay separate, just as the problem asks.

### Keeping the three structures in sync

Every visit now lives in three places, so a delete must update all three. The helper `removeVisit()` does this for one visit: it finds the visit's key with binary search, deletes it from the list, then calls `removeFromDicts()` to clean up both dictionaries. `deleteVisit` and `deleteHistoryByUrl` use `removeVisit()`.

`clearHistory` has already cut its whole block out of the list, so it only calls `removeFromDicts()` for each visit in that block. `clearAllHistory` simply empties all three.

One Python detail: a set must not change size while a for loop is walking over it (Python raises `RuntimeError`). That's why `deleteHistoryByUrl` copies the id set into a list first.

### Why these data structures

- **`visitsById` (dict)**: `deleteVisit` must find a visit by its id in O(1). It also holds the full `Visit` objects, so the sorted list and the URL dictionary only need small keys and ids.
- **`visitIdsByUrl` (dict of sets)**: groups visits by exact URL. A set of ids lets us remove one id in O(1).
- **`sortedKeys` (list + `bisect`)**: our home-made sorted set. Binary search finds any position in O(log n), so every read is a walk that starts at the right place and stops early.

### Code

```python
from bisect import bisect_left, insort
from typing import List

MAX_TIME = 10**18  # largest visit time allowed by the constraints


class Visit:
    """One saved history entry."""

    def __init__(self, visitId: str, url: str, title: str, visitedAt: int):
        self.visitId = visitId
        self.url = url
        self.title = title
        self.visitedAt = visitedAt
        # lower case copies, made once, so searches can ignore letter case cheaply
        self.lowerUrl = url.lower()
        self.lowerTitle = title.lower()

    def matches(self, lowerQuery: str) -> bool:
        """True if the url or the title contains the (already lower case) query."""
        return lowerQuery in self.lowerUrl or lowerQuery in self.lowerTitle

    def sortKey(self):
        """Newer visits first (so the time is negated), then the smaller visitId."""
        return (-self.visitedAt, self.visitId)

    def __str__(self) -> str:
        return f"{self.visitId},{self.url},{self.title},{self.visitedAt}"


class GlobalBrowsingHistory:
    def __init__(self):
        # visitId -> Visit, finds any visit in O(1)
        self.visitsById = {}
        # url -> set of visitIds, so delete by url only touches those visits
        self.visitIdsByUrl = {}
        # sort key (-visitedAt, visitId) of every visit, always kept sorted (display order)
        self.sortedKeys = []

    def recordVisit(self, visitId: str, url: str, title: str, visitedAt: int, privateVisit: bool):
        if privateVisit:
            return  # private visits are never saved
        visit = Visit(visitId, url, title, visitedAt)
        self.visitsById[visitId] = visit
        self.visitIdsByUrl.setdefault(url, set()).add(visitId)
        insort(self.sortedKeys, visit.sortKey())  # binary search for the spot, then insert

    def getRecentHistory(self, limit: int) -> List[str]:
        # recent history is just a search with an empty query over all time
        return self.searchHistory("", 0, MAX_TIME, limit)

    def searchHistory(self, query: str, startTime: int, endTime: int, limit: int) -> List[str]:
        lowerQuery = query.lower()
        result = []

        # start at the newest visit that is not after endTime and walk towards older visits
        index = self.firstIndexAtOrBefore(endTime)
        while index < len(self.sortedKeys) and len(result) < limit:
            _, visitId = self.sortedKeys[index]
            visit = self.visitsById[visitId]
            if visit.visitedAt < startTime:
                break  # this visit and all visits after it are too old
            if visit.matches(lowerQuery):
                result.append(str(visit))
            index += 1
        return result

    def deleteVisit(self, visitId: str) -> bool:
        visit = self.visitsById.get(visitId)
        if visit is None:
            return False
        self.removeVisit(visit)
        return True

    def deleteHistoryByUrl(self, url: str) -> int:
        # copy the ids first, because removeVisit() changes this same set
        idsToDelete = list(self.visitIdsByUrl.get(url, []))
        for visitId in idsToDelete:
            self.removeVisit(self.visitsById[visitId])
        return len(idsToDelete)

    def clearHistory(self, startTime: int, endTime: int) -> int:
        # all visits inside [startTime, endTime] sit next to each other in sortedKeys
        start = self.firstIndexAtOrBefore(endTime)
        end = self.firstIndexAtOrBefore(startTime - 1)  # first visit older than startTime
        for _, visitId in self.sortedKeys[start:end]:
            self.removeFromDicts(self.visitsById[visitId])
        del self.sortedKeys[start:end]  # cut the whole block out in one step
        return end - start

    def clearAllHistory(self) -> int:
        count = len(self.visitsById)
        self.visitsById.clear()
        self.visitIdsByUrl.clear()
        self.sortedKeys.clear()
        return count

    def removeVisit(self, visit: Visit):
        """Removes one visit from all three structures, so they always agree with each other."""
        del self.sortedKeys[bisect_left(self.sortedKeys, visit.sortKey())]
        self.removeFromDicts(visit)

    def removeFromDicts(self, visit: Visit):
        """Removes one visit from visitsById and visitIdsByUrl."""
        del self.visitsById[visit.visitId]
        ids = self.visitIdsByUrl[visit.url]
        ids.remove(visit.visitId)
        if not ids:
            del self.visitIdsByUrl[visit.url]  # no visits left for this url

    def firstIndexAtOrBefore(self, time: int) -> int:
        """Index of the first visit with visitedAt <= time.

        The marker (-time, "") has an empty id, which is smaller than every real id,
        so it sorts exactly where the visits at or before this time begin.
        """
        return bisect_left(self.sortedKeys, (-time, ""))
```

### Complexity

`n` is the number of stored visits, `k` is the number of deleted visits and `m` is the number of visits we look at inside the time range (we stop early once we have `limit` matches).

| Method | Time |
|---|---|
| `recordVisit` | O(log n) to find the spot + one list shift |
| `getRecentHistory` | O(log n + limit) |
| `searchHistory` | O(log n + m) |
| `deleteVisit` | O(log n) to find the spot + one list shift |
| `deleteHistoryByUrl` | k × (O(log n) + one list shift) |
| `clearHistory` | O(log n + k) + one list shift |
| `clearAllHistory` | O(n) |

A list shift moves the items that come after the changed position. It is O(n) in theory, but Python does it as one fast block copy in C, so in practice it costs far less than looping over the visits in Python code.

Space is O(n). Each visit object is stored once, the dictionaries and the list only keep references, ids and small keys.