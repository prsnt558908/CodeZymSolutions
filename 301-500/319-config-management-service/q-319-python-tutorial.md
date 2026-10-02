# Design Configuration Management Service in Python

#### Problem Statement
[https://codezym.com/question/319-config-management-service](https://codezym.com/question/319-config-management-service)

## Core Idea

We need an in-memory service where updates for each configuration key wait in line, get committed strictly in order, and subscribers can read the latest committed value of the key they follow.

The main insight: subscribers never need old values, only the **latest committed value**, and every subscriber of a key sees the same value. So we store that value **once per key** and let subscribers read it when they ask. Pending updates wait in **one FIFO queue per key**, so "is this update first in line?" is just a peek at the front of that queue.

This looks like a textbook **Observer** problem, but a classic push-style Observer is not optimal here because it does work for every subscriber on every commit. A few dictionaries plus one queue per key, **with no design pattern**, give us O(1) time for every operation.

## Brute Force: One Big List

The simplest idea is to keep every update in one list, in submission order, each with a flag saying pending or committed. A dictionary from subscriber to key handles subscriptions.

- **Submit:** append the update to the list.
- **Commit:** scan the list to find the update, then scan everything before it to make sure no update for the same key is still pending.
- **Get:** scan the list backwards to find the last committed update for the subscriber's key.

**The problem:** the service can receive up to 1,000,000 updates, so a single commit or read may scan a million items. The list also keeps growing, even though old committed values are never needed again.

## Improving It Step by Step

### Step 1: One FIFO Queue per Key

A commit only asks one question: *is this update first in line for its key?* Updates of different keys never block each other, so each key gets its **own queue** of pending update IDs.

To find an update's key from its ID, we keep a dictionary `updateId -> update`. Then the check is just `queue[0] == updateId` on that key's queue.

When an update is committed, we remove it from both. So "does not exist" and "already committed" become one simple case: the ID is not in the pending dictionary.

### Step 2: Keep Only the Latest Committed Value

All subscribers of a key read the same value, and only the newest one matters. So one dictionary `key -> latest committed value` is enough.

A commit overwrites one entry. A read is one lookup. Older values are simply forgotten, which the problem allows.

### Step 3: Subscriptions Are Just a Dictionary

Each subscriber follows at most one key, so a dictionary `subscriberId -> key` is all we need.

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

We don't even store the state. An update in the pending dictionary is pending, and a committed update is simply removed. State classes would add code without adding value.

**Verdict:** plain dictionaries and queues are both the simplest and the fastest choice here.

## Final Design

We need just two classes.

**`ConfigurationUpdate`** is a small data holder for one submitted update: `update_id`, `key` and `value`. A commit arrives with only an ID, and this object tells us the key and the value.

**`ConfigurationManagementService`** is the service itself. It owns four dictionaries:

| Data structure | Stores | Why we need it |
|---|---|---|
| `pending_updates` | `updateId -> ConfigurationUpdate` | Finds a pending update in O(1). Committed updates are removed, so it also catches "already committed". |
| `pending_queues` | `key -> deque` of update IDs | Keeps FIFO order per key. The "first in line" check is one look at `queue[0]`. |
| `committed_values` | `key -> latest committed value` | The one shared value that every subscriber of the key reads. |
| `subscriptions` | `subscriberId -> key` | One key per subscriber. Subscribe, unsubscribe and reads are all O(1). |

**Why `deque` and not a list?** Removing the first item of a Python list shifts every other item, which is O(n). `deque.popleft()` removes it in O(1).

Notice there is no `Subscriber` class and no `key -> subscribers` list. We never push anything to subscribers, so we don't need them.

## How Each Method Works

- **`subscribe`**: return `False` if the subscriber already follows a key. Otherwise save `subscriberId -> key`.
- **`unsubscribe`**: remove the entry only if the subscriber follows exactly this key.
- **`submitConfigurationUpdate`**: reject empty input, save the update, and add its ID to the back of its key's queue.
- **`configurationUpdatedMethod`**: find the pending update. If its ID is not at the front of its key's queue, return `""`. Otherwise remove it from the queue and the dictionary, and save its value as the key's latest committed value.
- **`getConfigurationUpdate`**: find the subscriber's key and return its committed value, or `""` if there is none.

## Dry Run of Example 1

Short names used below: `submit` is `submitConfigurationUpdate`, `commit` is `configurationUpdatedMethod` and `get` is `getConfigurationUpdate`. Every update is for the key `logging.level`, so the key is left out of the submit calls.

| Call | Queue of `logging.level` | Committed value | Output |
|---|---|---|---|
| `subscribe("api-node", "logging.level")` | empty | none | `True` |
| `submit("logging-info", "INFO")` | `[logging-info]` | none | `"logging-info"` |
| `submit("logging-debug", "DEBUG")` | `[logging-info, logging-debug]` | none | `"logging-debug"` |
| `get("api-node")` | no change | none | `""` |
| `commit("logging-debug")` | no change, it is not at the front | none | `""` |
| `commit("logging-info")` | `[logging-debug]` | `INFO` | `"logging-info"` |
| `get("api-node")` | no change | `INFO` | `"INFO"` |
| `get("api-node")` | no change | `INFO` | `"INFO"` |
| `commit("logging-debug")` | empty | `DEBUG` | `"logging-debug"` |
| `get("api-node")` | empty | `DEBUG` | `"DEBUG"` |

## Python Code

```python
from collections import deque


class ConfigurationUpdate:
    """One configuration update submitted by a user.
    We keep it only while it is pending (waiting to be committed).
    """

    def __init__(self, update_id, key, value):
        self.update_id = update_id
        self.key = key
        self.value = value


class ConfigurationManagementService:
    """Single-threaded, in-memory configuration service.

    Pending updates wait in one FIFO queue per key.
    A commit saves the value as the key's latest committed value.
    Subscribers read (pull) that value only when they ask for it.
    """

    def __init__(self):
        # updateId -> update, only for updates that are still pending
        self.pending_updates = {}
        # key -> deque of its pending update ids, oldest first
        self.pending_queues = {}
        # key -> latest committed value (older values are thrown away)
        self.committed_values = {}
        # subscriberId -> the one key this subscriber follows
        self.subscriptions = {}

    def subscribe(self, subscriberId, key):
        if not subscriberId or not key:
            return False
        # a subscriber can follow only one key at a time
        if subscriberId in self.subscriptions:
            return False
        self.subscriptions[subscriberId] = key
        return True

    def unsubscribe(self, subscriberId, key):
        if not subscriberId or not key:
            return False
        # remove only if the subscriber follows exactly this key
        if self.subscriptions.get(subscriberId) != key:
            return False
        del self.subscriptions[subscriberId]
        return True

    def submitConfigurationUpdate(self, updateId, key, value):
        if not updateId or not key or not value:
            return ""
        # ids are guaranteed to be unique, this check only protects the queues
        if updateId in self.pending_updates:
            return ""

        self.pending_updates[updateId] = ConfigurationUpdate(updateId, key, value)
        # create the key's queue the first time we see the key
        if key not in self.pending_queues:
            self.pending_queues[key] = deque()
        self.pending_queues[key].append(updateId)
        return updateId

    def configurationUpdatedMethod(self, updateId):
        update = self.pending_updates.get(updateId)
        # the update does not exist or is already committed
        if update is None:
            return ""

        queue = self.pending_queues[update.key]
        # an older update for the same key is still waiting
        if queue[0] != updateId:
            return ""

        queue.popleft()
        del self.pending_updates[updateId]
        # all subscribers of this key will now read the new value
        self.committed_values[update.key] = update.value
        return updateId

    def getConfigurationUpdate(self, subscriberId):
        key = self.subscriptions.get(subscriberId)
        # not subscribed to any key
        if key is None:
            return ""
        # empty string if the key has no committed value yet
        return self.committed_values.get(key, "")
```

## Complexity

| Method | Time |
|---|---|
| `subscribe` | O(1) |
| `unsubscribe` | O(1) |
| `submitConfigurationUpdate` | O(1) |
| `configurationUpdatedMethod` | O(1) |
| `getConfigurationUpdate` | O(1) |

These are average times for dictionary and deque operations.

**Space:** O(P + K + S), where P is the number of pending updates, K the number of keys and S the number of subscribers. Committed updates are dropped and only the latest value per key is kept.