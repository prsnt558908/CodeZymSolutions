# Design Configuration Management Service in Java

#### Problem Statement
[https://codezym.com/question/319-config-management-service](https://codezym.com/question/319-config-management-service)

## Core Idea

We need an in-memory service where updates for each configuration key wait in line, get committed strictly in order, and subscribers can read the latest committed value of the key they follow.

The main insight: subscribers never need old values, only the **latest committed value**, and every subscriber of a key sees the same value. So we store that value **once per key** and let subscribers read it when they ask. Pending updates wait in **one FIFO queue per key**, so "is this update first in line?" is just a peek at the front of that queue.

This looks like a textbook **Observer** problem, but a classic push-style Observer is not optimal here because it does work for every subscriber on every commit. A few hash maps plus one queue per key, **with no design pattern**, give us O(1) time for every operation.

## Brute Force: One Big List

The simplest idea is to keep every update in one list, in submission order, each with a flag saying pending or committed. A map from subscriber to key handles subscriptions.

- **Submit:** append the update to the list.
- **Commit:** scan the list to find the update, then scan everything before it to make sure no update for the same key is still pending.
- **Get:** scan the list backwards to find the last committed update for the subscriber's key.

**The problem:** the service can receive up to 1,000,000 updates, so a single commit or read may scan a million items. The list also keeps growing, even though old committed values are never needed again.

## Improving It Step by Step

### Step 1: One FIFO Queue per Key

A commit only asks one question: *is this update first in line for its key?* Updates of different keys never block each other, so each key gets its **own queue** of pending update IDs.

To find an update's key from its ID, we keep a map `updateId -> update`. Then the check is just `updateId.equals(queue.peek())` on that key's queue.

When an update is committed, we remove it from both. So "does not exist" and "already committed" become one simple case: the ID is not in the pending map.

### Step 2: Keep Only the Latest Committed Value

All subscribers of a key read the same value, and only the newest one matters. So one map `key -> latest committed value` is enough.

A commit overwrites one entry. A read is one lookup. Older values are simply forgotten, which the problem allows.

### Step 3: Subscriptions Are Just a Map

Each subscriber follows at most one key, so a map `subscriberId -> key` is all we need.

Because subscribers read the value only when they ask, the subscription rules work automatically:

- A new subscriber instantly sees the key's current committed value.
- A subscriber added before a commit sees the new value right after the commit.
- After unsubscribing, the subscriber has no key, so it gets `""`.

## Do We Need a Design Pattern?

### Observer (push style): sounds right, but wasteful here

The textbook approach: every key keeps a list of its subscribers, and each commit loops over that list and pushes the new value into every subscriber.

It works, but a key can have 10,000 subscribers and the service can receive 1,000,000 updates. That is up to **10 billion pushes**. And if two commits happen before a subscriber reads, the first push was wasted.

Our design keeps the subscribe idea but **flips push into pull**. Think of a notice board: instead of mailing a copy of every new notice to 10,000 people, we pin it on one board and people look at the board when they need it. One write per commit, one read per request.

### State: overkill for a two-step lifecycle

An update moves from pending to committed, which can tempt you to use the State pattern. But there are only two states and one move between them.

We don't even store the state. An update in the pending map is pending, and a committed update is simply removed. State classes would add code without adding value.

**Verdict:** plain maps and queues are both the simplest and the fastest choice here.

## Final Design

We need just two classes.

**`ConfigurationUpdate`** is a small data holder for one submitted update: `updateId`, `key` and `value`. A commit arrives with only an ID, and this object tells us the key and the value.

**`ConfigurationManagementService`** is the service itself. It owns four maps:

| Data structure | Stores | Why we need it |
|---|---|---|
| `pendingUpdates` | `updateId -> ConfigurationUpdate` | Finds a pending update in O(1). Committed updates are removed, so it also catches "already committed". |
| `pendingQueues` | `key -> Queue<String>` of update IDs | Keeps FIFO order per key. The "first in line" check is one `peek()`. |
| `committedValues` | `key -> latest committed value` | The one shared value that every subscriber of the key reads. |
| `subscriptions` | `subscriberId -> key` | One key per subscriber. Subscribe, unsubscribe and reads are all O(1). |

Notice there is no `Subscriber` class and no `key -> subscribers` list. We never push anything to subscribers, so we don't need them.

## How Each Method Works

- **`subscribe`**: return `false` if the subscriber already follows a key. Otherwise save `subscriberId -> key`.
- **`unsubscribe`**: remove the entry only if the subscriber follows exactly this key.
- **`submitConfigurationUpdate`**: reject empty input, save the update, and add its ID to the back of its key's queue.
- **`configurationUpdatedMethod`**: find the pending update. If its ID is not at the front of its key's queue, return `""`. Otherwise remove it from the queue and the map, and save its value as the key's latest committed value.
- **`getConfigurationUpdate`**: find the subscriber's key and return its committed value, or `""` if there is none.

## Dry Run of Example 1

Short names used below: `submit` is `submitConfigurationUpdate`, `commit` is `configurationUpdatedMethod` and `get` is `getConfigurationUpdate`. Every update is for the key `logging.level`, so the key is left out of the submit calls.

| Call | Queue of `logging.level` | Committed value | Output |
|---|---|---|---|
| `subscribe("api-node", "logging.level")` | empty | none | `true` |
| `submit("logging-info", "INFO")` | `[logging-info]` | none | `"logging-info"` |
| `submit("logging-debug", "DEBUG")` | `[logging-info, logging-debug]` | none | `"logging-debug"` |
| `get("api-node")` | no change | none | `""` |
| `commit("logging-debug")` | no change, it is not at the front | none | `""` |
| `commit("logging-info")` | `[logging-debug]` | `INFO` | `"logging-info"` |
| `get("api-node")` | no change | `INFO` | `"INFO"` |
| `get("api-node")` | no change | `INFO` | `"INFO"` |
| `commit("logging-debug")` | empty | `DEBUG` | `"logging-debug"` |
| `get("api-node")` | empty | `DEBUG` | `"DEBUG"` |

## Java Code

```java
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/**
 * One configuration update submitted by a user.
 * We keep it only while it is pending (waiting to be committed).
 */
class ConfigurationUpdate {
    String updateId;
    String key;
    String value;

    ConfigurationUpdate(String updateId, String key, String value) {
        this.updateId = updateId;
        this.key = key;
        this.value = value;
    }
}

/**
 * Single-threaded, in-memory configuration service.
 *
 * Pending updates wait in one FIFO queue per key.
 * A commit saves the value as the key's latest committed value.
 * Subscribers read (pull) that value only when they ask for it.
 */
public class ConfigurationManagementService {

    // updateId -> update, only for updates that are still pending
    Map<String, ConfigurationUpdate> pendingUpdates = new HashMap<>();

    // key -> ids of its pending updates, oldest first
    Map<String, Queue<String>> pendingQueues = new HashMap<>();

    // key -> latest committed value (older values are thrown away)
    Map<String, String> committedValues = new HashMap<>();

    // subscriberId -> the one key this subscriber follows
    Map<String, String> subscriptions = new HashMap<>();

    public ConfigurationManagementService() {
    }

    public boolean subscribe(String subscriberId, String key) {
        if (isEmpty(subscriberId) || isEmpty(key)) return false;
        // a subscriber can follow only one key at a time
        if (subscriptions.containsKey(subscriberId)) return false;
        subscriptions.put(subscriberId, key);
        return true;
    }

    public boolean unsubscribe(String subscriberId, String key) {
        if (isEmpty(subscriberId) || isEmpty(key)) return false;
        // remove only if the subscriber follows exactly this key
        if (!key.equals(subscriptions.get(subscriberId))) return false;
        subscriptions.remove(subscriberId);
        return true;
    }

    public String submitConfigurationUpdate(String updateId, String key, String value) {
        if (isEmpty(updateId) || isEmpty(key) || isEmpty(value)) return "";
        // ids are guaranteed to be unique, this check only protects the queues
        if (pendingUpdates.containsKey(updateId)) return "";

        pendingUpdates.put(updateId, new ConfigurationUpdate(updateId, key, value));
        // create the key's queue the first time we see the key
        pendingQueues.computeIfAbsent(key, k -> new ArrayDeque<>()).add(updateId);
        return updateId;
    }

    public String configurationUpdatedMethod(String updateId) {
        ConfigurationUpdate update = pendingUpdates.get(updateId);
        // the update does not exist or is already committed
        if (update == null) return "";

        Queue<String> queue = pendingQueues.get(update.key);
        // an older update for the same key is still waiting
        if (!updateId.equals(queue.peek())) return "";

        queue.poll();
        pendingUpdates.remove(updateId);
        // all subscribers of this key will now read the new value
        committedValues.put(update.key, update.value);
        return updateId;
    }

    public String getConfigurationUpdate(String subscriberId) {
        String key = subscriptions.get(subscriberId);
        // not subscribed to any key
        if (key == null) return "";
        // empty string if the key has no committed value yet
        return committedValues.getOrDefault(key, "");
    }

    // null and "" both count as missing input
    boolean isEmpty(String text) {
        return text == null || text.isEmpty();
    }
}
```

## Complexity

| Method | Time |
|---|---|
| `subscribe` | O(1) |
| `unsubscribe` | O(1) |
| `submitConfigurationUpdate` | O(1) |
| `configurationUpdatedMethod` | O(1) |
| `getConfigurationUpdate` | O(1) |

These are average times for hash map and queue operations.

**Space:** O(P + K + S), where P is the number of pending updates, K the number of keys and S the number of subscribers. Committed updates are dropped and only the latest value per key is kept.