# Design Browser History Manager in Java

#### Problem Statement
[https://codezym.com/question/390-browser-history-manager](https://codezym.com/question/390-browser-history-manager)

A browser history is just a pile of visits. The tricky part is that we need to look at that pile in three different ways: by **visit id** (delete one visit), by **URL** (delete every visit to a page) and by **time** (show recent visits, search a time range, clear a time range).

So the core idea is to keep three simple structures in sync: a map from visit id to visit, a map from URL to its visit ids, and a sorted set that always holds the visits in display order, newest first. Every read walks the sorted set from the right starting point, and one helper removes a visit from all three structures at once.

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

**Strategy** could turn "text matches" and "time is in range" into separate filter classes. But there are only two fixed rules. Also, in the fast solution the time range is not a filter at all: we use it to jump straight to the right spot in the sorted set. Filter classes would add code and hide that speed up.

So we stick to plain, simple classes.

## Solution 1: Simple Approach (Sort on Every Read)

The most direct idea: keep every visit in a `HashMap` keyed by `visitId`. When someone asks for history, collect the visits we need, sort them and return the first `limit`.

### The `Visit` class

`Visit` holds one history entry. It also knows three useful things about itself:

- `compareTo()` defines the display order in one place. We can sort visits with `Collections.sort()` (and later keep them in a sorted set) without repeating the rule.
- `toString()` builds the required `"visitId,url,title,visitedAt"` string.
- `matches()` does the case insensitive text check. Lower case copies of the URL and title are made once, when the visit is saved, so searches don't have to lowercase them again and again.

### A small trick

`getRecentHistory(limit)` is the same as searching for an empty query across all of time, because an empty query matches every entry. So it simply calls `searchHistory("", 0, Long.MAX_VALUE, limit)`.

### Code

```java
import java.util.*;

/**
 * One saved history entry.
 */
class Visit implements Comparable<Visit> {
    String visitId;
    String url;
    String title;
    long visitedAt;

    // lower case copies, made once, so searches can ignore letter case cheaply
    String lowerUrl;
    String lowerTitle;

    Visit(String visitId, String url, String title, long visitedAt) {
        this.visitId = visitId;
        this.url = url;
        this.title = title;
        this.visitedAt = visitedAt;
        this.lowerUrl = url.toLowerCase();
        this.lowerTitle = title.toLowerCase();
    }

    // true if the url or the title contains the (already lower case) query
    boolean matches(String lowerQuery) {
        return lowerUrl.contains(lowerQuery) || lowerTitle.contains(lowerQuery);
    }

    // newer visits come first, for the same time the smaller visitId comes first
    @Override
    public int compareTo(Visit other) {
        if (visitedAt != other.visitedAt) {
            return Long.compare(other.visitedAt, visitedAt);
        }
        return visitId.compareTo(other.visitId);
    }

    // "visitId,url,title,visitedAt"
    @Override
    public String toString() {
        return visitId + "," + url + "," + title + "," + visitedAt;
    }
}

public class GlobalBrowsingHistory {
    // visitId -> visit
    Map<String, Visit> visitsById = new HashMap<>();

    public GlobalBrowsingHistory() {
    }

    public void recordVisit(String visitId, String url, String title, long visitedAt, boolean privateVisit) {
        if (privateVisit) {
            return; // private visits are never saved
        }
        visitsById.put(visitId, new Visit(visitId, url, title, visitedAt));
    }

    // recent history is just a search with an empty query over all time
    public List<String> getRecentHistory(int limit) {
        return searchHistory("", 0, Long.MAX_VALUE, limit);
    }

    public List<String> searchHistory(String query, long startTime, long endTime, int limit) {
        String lowerQuery = query.toLowerCase();
        List<Visit> matching = new ArrayList<>();
        for (Visit visit : visitsById.values()) {
            boolean inRange = visit.visitedAt >= startTime && visit.visitedAt <= endTime;
            if (inRange && visit.matches(lowerQuery)) {
                matching.add(visit);
            }
        }

        // the slow part: all matches are sorted again on every single call
        Collections.sort(matching);

        List<String> result = new ArrayList<>();
        for (int i = 0; i < matching.size() && i < limit; i++) {
            result.add(matching.get(i).toString());
        }
        return result;
    }

    public boolean deleteVisit(String visitId) {
        return visitsById.remove(visitId) != null;
    }

    public int deleteHistoryByUrl(String url) {
        int sizeBefore = visitsById.size();
        // removing from values() also removes the entry from the map
        visitsById.values().removeIf(visit -> visit.url.equals(url));
        return sizeBefore - visitsById.size();
    }

    public int clearHistory(long startTime, long endTime) {
        int sizeBefore = visitsById.size();
        visitsById.values().removeIf(visit -> visit.visitedAt >= startTime && visit.visitedAt <= endTime);
        return sizeBefore - visitsById.size();
    }

    public int clearAllHistory() {
        int count = visitsById.size();
        visitsById.clear();
        return count;
    }
}
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

Instead of sorting at read time, we keep the visits sorted all the time in a `TreeSet<Visit>`, Java's sorted set. It uses the same `compareTo()` from `Visit`, so the newest visit is always first.

Adding or removing a visit costs O(log n). Recent history is now just "take the first `limit` visits", with no sorting at all.

### Improvement 2: Jump straight to the time range

Because the set is sorted by time, all visits inside `[startTime, endTime]` sit next to each other. So we can:

1. Jump to the first visit with `visitedAt <= endTime`.
2. Walk forward, towards older visits.
3. Stop as soon as a visit is older than `startTime`, or we already have `limit` matches.

To jump, we use a **marker**: a fake visit with `visitedAt = endTime` and an empty `visitId`. An empty string is smaller than every real id (a real id has at least 1 character), so in sorted order the marker sits exactly where the visits with `visitedAt <= endTime` begin. Then `sortedVisits.tailSet(marker)` gives us every visit from that spot onwards, without walking over the newer visits.

Here is a search with `endTime = 500` and `startTime = 400`:

```
sorted set:      [600,v9] [500,v1] [500,v7] [400,v2] [300,v5]
marker (500,""):          ^ sits here, because "" < "v1"
tailSet(marker):          [500,v1] [500,v7] [400,v2] [300,v5]
walk to 400:              [500,v1] [500,v7] [400,v2]  stop at 300
```

`clearHistory` uses the same walk to find the visits it has to delete.

### Improvement 3: Group visits by URL

A `HashMap<String, Set<String>>` maps each URL to the ids of its visits. `deleteHistoryByUrl` now touches only that URL's visits instead of scanning everything. The map key is the exact URL, so `https://A.com` and `https://a.com` stay separate, just as the problem asks.

### Keeping the three structures in sync

Every visit now lives in three places, so a delete must update all three. We put that work in one helper, `removeVisit()`, and every method that deletes specific visits calls it. This way the structures can never disagree. `clearAllHistory` simply empties all three.

One Java detail: a collection must not be changed while a for-each loop is walking over it (Java throws `ConcurrentModificationException`). That's why `deleteHistoryByUrl` copies the id set first, and `clearHistory` first collects the visits and only then deletes them.

### Why these data structures

- **`HashMap<String, Visit> visitsById`**: `deleteVisit` must find a visit by its id in O(1). It also lets the URL map store just ids.
- **`HashMap<String, Set<String>> visitIdsByUrl`**: groups visits by exact URL. A `HashSet` of ids lets us remove one id in O(1).
- **`TreeSet<Visit> sortedVisits`**: always sorted in display order, so every read is a walk that starts at the right place and stops early.

### Code

```java
import java.util.*;

/**
 * One saved history entry.
 */
class Visit implements Comparable<Visit> {
    String visitId;
    String url;
    String title;
    long visitedAt;

    // lower case copies, made once, so searches can ignore letter case cheaply
    String lowerUrl;
    String lowerTitle;

    Visit(String visitId, String url, String title, long visitedAt) {
        this.visitId = visitId;
        this.url = url;
        this.title = title;
        this.visitedAt = visitedAt;
        this.lowerUrl = url.toLowerCase();
        this.lowerTitle = title.toLowerCase();
    }

    // true if the url or the title contains the (already lower case) query
    boolean matches(String lowerQuery) {
        return lowerUrl.contains(lowerQuery) || lowerTitle.contains(lowerQuery);
    }

    // newer visits come first, for the same time the smaller visitId comes first
    @Override
    public int compareTo(Visit other) {
        if (visitedAt != other.visitedAt) {
            return Long.compare(other.visitedAt, visitedAt);
        }
        return visitId.compareTo(other.visitId);
    }

    // "visitId,url,title,visitedAt"
    @Override
    public String toString() {
        return visitId + "," + url + "," + title + "," + visitedAt;
    }
}

public class GlobalBrowsingHistory {
    // visitId -> visit, finds any visit in O(1)
    Map<String, Visit> visitsById = new HashMap<>();

    // url -> ids of all visits to that url, so delete by url only touches those visits
    Map<String, Set<String>> visitIdsByUrl = new HashMap<>();

    // every visit, always kept in display order: newest first, then smaller visitId
    TreeSet<Visit> sortedVisits = new TreeSet<>();

    public GlobalBrowsingHistory() {
    }

    public void recordVisit(String visitId, String url, String title, long visitedAt, boolean privateVisit) {
        if (privateVisit) {
            return; // private visits are never saved
        }
        Visit visit = new Visit(visitId, url, title, visitedAt);
        visitsById.put(visitId, visit);
        // get (or create) the id set of this url, then add the new id
        visitIdsByUrl.computeIfAbsent(url, key -> new HashSet<>()).add(visitId);
        sortedVisits.add(visit);
    }

    // recent history is just a search with an empty query over all time
    public List<String> getRecentHistory(int limit) {
        return searchHistory("", 0, Long.MAX_VALUE, limit);
    }

    public List<String> searchHistory(String query, long startTime, long endTime, int limit) {
        String lowerQuery = query.toLowerCase();
        List<String> result = new ArrayList<>();

        // start at the newest visit that is not after endTime and walk towards older visits
        for (Visit visit : visitsAtOrBefore(endTime)) {
            if (visit.visitedAt < startTime) {
                break; // this visit and all visits after it are too old
            }
            if (visit.matches(lowerQuery)) {
                result.add(visit.toString());
                if (result.size() == limit) {
                    break; // we have enough entries
                }
            }
        }
        return result;
    }

    public boolean deleteVisit(String visitId) {
        Visit visit = visitsById.get(visitId);
        if (visit == null) {
            return false;
        }
        removeVisit(visit);
        return true;
    }

    public int deleteHistoryByUrl(String url) {
        Set<String> ids = visitIdsByUrl.get(url);
        if (ids == null) {
            return 0;
        }
        // copy the ids first, because removeVisit() changes this same set
        List<String> idsToDelete = new ArrayList<>(ids);
        for (String id : idsToDelete) {
            removeVisit(visitsById.get(id));
        }
        return idsToDelete.size();
    }

    public int clearHistory(long startTime, long endTime) {
        // collect first, because a set must not be changed while we walk over it
        List<Visit> visitsToDelete = new ArrayList<>();
        for (Visit visit : visitsAtOrBefore(endTime)) {
            if (visit.visitedAt < startTime) {
                break;
            }
            visitsToDelete.add(visit);
        }
        for (Visit visit : visitsToDelete) {
            removeVisit(visit);
        }
        return visitsToDelete.size();
    }

    public int clearAllHistory() {
        int count = visitsById.size();
        visitsById.clear();
        visitIdsByUrl.clear();
        sortedVisits.clear();
        return count;
    }

    // Removes one visit from all three structures, so they always agree with each other.
    void removeVisit(Visit visit) {
        visitsById.remove(visit.visitId);
        sortedVisits.remove(visit);
        Set<String> ids = visitIdsByUrl.get(visit.url);
        ids.remove(visit.visitId);
        if (ids.isEmpty()) {
            visitIdsByUrl.remove(visit.url); // no visits left for this url
        }
    }

    // All visits with visitedAt <= time, newest first.
    // The marker has an empty id, which is smaller than every real id,
    // so in sorted order it sits exactly where the visits at or before this time begin.
    SortedSet<Visit> visitsAtOrBefore(long time) {
        Visit marker = new Visit("", "", "", time);
        return sortedVisits.tailSet(marker);
    }
}
```

### Complexity

`n` is the number of stored visits, `k` is the number of deleted visits and `m` is the number of visits we look at inside the time range (we stop early once we have `limit` matches).

| Method | Time |
|---|---|
| `recordVisit` | O(log n) |
| `getRecentHistory` | O(log n + limit) |
| `searchHistory` | O(log n + m) |
| `deleteVisit` | O(log n) |
| `deleteHistoryByUrl` | O(k log n) |
| `clearHistory` | O(log n + k log n) |
| `clearAllHistory` | O(n) |

Space is O(n). Each visit object is stored once, the maps and the set only keep references and ids.