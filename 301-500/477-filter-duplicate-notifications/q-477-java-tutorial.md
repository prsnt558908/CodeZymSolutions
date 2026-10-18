# Filter Duplicate Notifications in Java

#### Problem Statement

[https://codezym.com/question/477-filter-duplicate-notifications](https://codezym.com/question/477-filter-duplicate-notifications)

Keep one record per notification ID, and move it to the newest position after every arrival. A map finds a record quickly, while a doubly linked list keeps records ordered by their latest arrival time. This lets us remove expired records from the front without scanning every ID. The design needs only a filter class and a small record class.

## Start with a map

Store each ID and its last-seen time in a map. Reject an arrival when its previous arrival was less than 600 seconds ago. Always update the time, including when the notification is rejected.

This handles duplicates, but keeping every ID forever uses too much memory. Scanning the entire map to remove expired IDs on every call takes `O(N)` time per call.

## Keep records in last-seen order

Combine two familiar data structures:

- A `HashMap` maps each ID to its record, so we can find it in average `O(1)` time.
- A doubly linked list orders those records from oldest to newest. Each record has links to its previous and next records, so we can remove it in `O(1)` time.

`NotificationRecord` stores an ID, its last-seen time, and those two links. `NotificationFilter` owns the map and the list's two ends.

Arrival times never decrease. Moving an updated record to the back therefore keeps the list ordered, even when several calls have the same time.

For each `shouldSend` call:

1. Remove records from the front while their age is at least 600 seconds. Remove them from the map too.
2. Look up the current ID. If it remains in the map, reject the notification. Otherwise, allow it.
3. Update its time and move its record to the back. Create a record if the ID is absent.

A queue containing every arrival can grow very large when one ID repeats many times. Here, an existing record is moved instead, so each active ID has exactly one record.

## Walk through the window

Let `A` represent the UUID `748e219a-4be6-4c2c-972b-1a63c89d425f`. Start with a new filter.

- `shouldSend(A, 20)` returns `true`. Record time 20.
- `shouldSend(A, 300)` returns `false`. The gap is 280 seconds, and the record changes to 300.
- `shouldSend(A, 620)` returns `false`. Compare against 300, so the gap is only 320 seconds. Update the record to 620.
- `shouldSend(A, 1220)` returns `true`. The gap is exactly 600 seconds, so the previous record expires.

Two arrivals of the same ID at the same time have a gap of zero. The second is rejected. Different IDs are checked independently.

## Why this works

The map and list always contain the same records, with one record per retained ID. Every arrival updates its record, so the stored time includes rejected duplicates.

The list stays ordered by last-seen time. Once the oldest record is younger than 600 seconds, every later record is also unexpired. Cleanup can safely stop there.

After cleanup, an ID is present exactly when its most recent arrival was less than 600 seconds ago. Its presence therefore gives the correct return value.

## Complexity

Processing takes expected amortized `O(1)` time per call. Updating a record takes constant time, and each created record can be removed by cleanup only once. A single call after a long gap may remove `O(N)` records.

Space is `O(N)`, where `N` is the number of distinct IDs seen within the current 600-second window. Cleanup runs during each call. Use `long` for times because inputs can reach `1,000,000,000,000`.

## Java code

```java
import java.util.HashMap;
import java.util.Map;

class NotificationRecord {
    String notificationId;
    long lastSeen;
    NotificationRecord previous;
    NotificationRecord next;

    NotificationRecord(String notificationId, long lastSeen) {
        this.notificationId = notificationId;
        this.lastSeen = lastSeen;
    }
}

public class NotificationFilter {
    Map<String, NotificationRecord> records;
    NotificationRecord oldest;
    NotificationRecord newest;

    public NotificationFilter() {
        records = new HashMap<>();
    }

    public boolean shouldSend(String notificationId, long arrivalTime) {
        // A gap of exactly 600 seconds is no longer a duplicate.
        while (oldest != null && arrivalTime - oldest.lastSeen >= 600) {
            NotificationRecord expired = oldest;
            removeRecord(expired);
            records.remove(expired.notificationId);
        }

        NotificationRecord record = records.get(notificationId);
        boolean send = record == null;

        if (record == null) {
            record = new NotificationRecord(notificationId, arrivalTime);
            records.put(notificationId, record);
        } else {
            // Rejected duplicates also restart the window.
            removeRecord(record);
            record.lastSeen = arrivalTime;
        }

        appendRecord(record);
        return send;
    }

    // Detach a record and update both ends when needed.
    void removeRecord(NotificationRecord record) {
        if (record.previous == null) {
            oldest = record.next;
        } else {
            record.previous.next = record.next;
        }

        if (record.next == null) {
            newest = record.previous;
        } else {
            record.next.previous = record.previous;
        }

        record.previous = null;
        record.next = null;
    }

    // A new or updated record becomes the newest one.
    void appendRecord(NotificationRecord record) {
        record.previous = newest;

        if (newest == null) {
            oldest = record;
        } else {
            newest.next = record;
        }

        newest = record;
    }
}
```
