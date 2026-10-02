# Design Notification System in Python

#### Problem Statement
[https://codezym.com/question/133-design-notification-system](https://codezym.com/question/133-design-notification-system)

---

## Core Idea

A notification system is a classic **publish and subscribe** setup. Users sign up for an event type (like `ORDER_PLACED`) on one or more channels. When a notification is sent for that event, every subscriber gets one copy in their inbox for each channel they picked.


This is exactly what the **Observer pattern** is made for. Each event type is a **subject** that keeps track of its listeners. Each `(user, channel)` pair is an **observer** that receives a delivery when the event fires. Observer alone is the best fit here. We don't need to combine it with other patterns because every channel behaves the same way.


The two tricky rules are "no duplicate deliveries" and "results sorted by `userId`, then by `channel`". For each event type we keep a **dictionary of subscribers keyed by the `(userId, channel)` tuple**. A key can't repeat, so duplicates are blocked. Tuples sort by `userId` first and then by `channel`, so one `sorted()` call gives the exact order we need.


We will start with a simple brute force version, see where it is slow, and then improve it step by step.

---

## Solution 1: Brute Force (One Big List)

Store every subscription as a `(userId, eventType, channel)` tuple in one list. Store a dictionary of `userId -> inbox`. If a user has an inbox, the user is registered.

- **subscribe**: ignore unknown users and unsupported channels. Add the tuple only if it is not in the list yet.
- **unsubscribe**: remove the tuple if it is in the list.
- **sendNotification**: go over the whole list and pick the `(userId, channel)` pairs of this `eventType`. Sort them. Build each delivery string and add it to that user's inbox.
- **getUserInbox**: return a copy of the inbox, or an empty list for an unknown user.

> **Watch out:** sort the `(userId, channel)` pairs, not the final delivery strings. The `|` character has a bigger code than letters and digits, so `"U10|EMAIL|..."` would wrongly come before `"U1|EMAIL|..."`, even though `"U1"` comes before `"U10"`.

### Code

```python
from typing import List


class NotificationSystem:
    def __init__(self, supportedChannels: List[str]):
        self.supportedChannels = set(supportedChannels)
        # userId -> inbox. A user is registered only if it has an inbox.
        self.inboxes = {}
        # every subscription, stored as a (userId, eventType, channel) tuple
        self.subscriptions = []

    def registerUser(self, userId: str):
        if userId not in self.inboxes:
            self.inboxes[userId] = []

    def subscribe(self, userId: str, eventType: str, channel: str):
        if userId not in self.inboxes or channel not in self.supportedChannels:
            return
        subscription = (userId, eventType, channel)
        if subscription not in self.subscriptions:   # linear scan
            self.subscriptions.append(subscription)

    def unsubscribe(self, userId: str, eventType: str, channel: str):
        subscription = (userId, eventType, channel)
        if subscription in self.subscriptions:       # linear scan
            self.subscriptions.remove(subscription)

    def sendNotification(self, notificationId: str, eventType: str, content: str) -> List[str]:
        # 1. pick the (userId, channel) pairs subscribed to this event
        matching = []
        for user, event, channel in self.subscriptions:
            if event == eventType:
                matching.append((user, channel))

        # 2. tuples sort by userId first, then by channel
        matching.sort()

        # 3. build each delivery and drop it in the user's inbox
        deliveries = []
        for user, channel in matching:
            delivery = f"{user}|{channel}|{notificationId}|{eventType}|{content}"
            self.inboxes[user].append(delivery)
            deliveries.append(delivery)
        return deliveries

    def getUserInbox(self, userId: str) -> List[str]:
        return list(self.inboxes.get(userId, []))
```

### Problems with this approach

- `x in list` and `list.remove(x)` check items one by one, so `subscribe` and `unsubscribe` scan **all** subscriptions, even the ones for other events.
- `sendNotification` also scans every subscription of every event, just to find the few it needs.
- One class does everything, so it is hard to see who is listening to which event.

It works for small inputs, but we can do better.

---

## Solution 2: Observer Pattern with Subscribers Grouped by Event

### Fixing the problems one by one

| Problem in Solution 1 | Fix |
|---|---|
| Scanning the subscriptions of other events | Group subscribers by event type in a dictionary `eventType -> EventTopic` |
| Scanning to find a duplicate or to unsubscribe | Keep each event's subscribers in a dictionary keyed by `(userId, channel)` |

Python has no built-in sorted set, so `publish` still sorts on every send, same as before. That is fine, because it only sorts the subscribers of one event, and the tuple keys need no custom compare code.


After these fixes, every event type becomes a small object that owns its subscribers. That object is the subject of the Observer pattern.

### Why the Observer pattern?

The problem describes Observer almost word for word: listeners attach to an event, detach from it, and every current listener is notified when the event fires.

- `EventTopic` is the **subject**. It has `attach`, `detach` and `publish` (notify everyone).
- `Subscriber` is the **observer**. Its `deliver` method builds the delivery string and puts it in the user's inbox.
- `NotificationSystem` is the front door. It checks the input and hands the call to the right topic.


In Solution 1 one class did all of this. Now each class has one small job, so the code is easier to read and change.


We skip a separate `Observer` base class because there is only one kind of subscriber. If another kind shows up later, adding a base class is a small change.

### A pattern that looks useful but doesn't fit

**Strategy (with a Factory).** It is tempting to write `EmailChannel`, `SmsChannel` and `PushChannel` classes and a factory to create them. But here every channel does exactly the same thing: build a string and save it in an inbox.


Channel names are also any strings given at runtime, so one class per channel is not even possible. A `set` of channel names is all we need.

### Important classes and data structures

- **`User`**: `userId` plus a list inbox. A list keeps deliveries in the exact order they arrived.
- **`Subscriber`**: one `(user, channel)` pair. It holds a reference to its `User`, so it can drop a delivery straight into the inbox.
- **`EventTopic`**: one event type plus a dictionary `(userId, channel) -> Subscriber`. The tuple key does two jobs:
  - a key can't appear twice, so a duplicate subscription is simply ignored, and unsubscribing is a quick `pop`.
  - sorting the keys orders subscribers by `userId`, then by `channel`.
- **`NotificationSystem`**:
  - `supportedChannels` (a `set`): a fast "is this channel allowed?" check. Duplicate names in the input collapse into one.
  - `users` (`userId -> User`): who is registered.
  - `topics` (`eventType -> EventTopic`): jump straight to the subscribers of one event without touching other events.

### How each method works

- **registerUser**: add a new `User` only if the id is new.
- **subscribe**: do nothing for an unknown user or an unsupported channel. Otherwise create the event's topic if needed and attach a new `Subscriber`. A duplicate key is ignored.
- **unsubscribe**: if the event has a topic, remove the `(userId, channel)` key from it. A missing key, an unknown user or an unknown event simply does nothing.
- **sendNotification**: no topic means no subscribers, so return an empty list. Otherwise go over the sorted keys and let each subscriber deliver.
- **getUserInbox**: return a copy of the user's inbox, or an empty list for an unknown user.

### Code

```python
from typing import List


class User:
    """A registered user. The inbox keeps every delivery in the exact order it arrived."""

    def __init__(self, userId: str):
        self.userId = userId
        self.inbox = []


class Subscriber:
    """Observer: one (user, channel) pair listening to an event type."""

    def __init__(self, user: User, channel: str):
        self.user = user
        self.channel = channel

    def deliver(self, notificationId: str, eventType: str, content: str) -> str:
        """Called when the event fires. Builds the delivery and drops it in the user's inbox."""
        delivery = f"{self.user.userId}|{self.channel}|{notificationId}|{eventType}|{content}"
        self.user.inbox.append(delivery)
        return delivery


class EventTopic:
    """Subject: one event type and everyone subscribed to it."""

    def __init__(self, eventType: str):
        self.eventType = eventType
        # (userId, channel) -> Subscriber. The tuple key makes duplicates impossible.
        self.subscribers = {}

    def attach(self, subscriber: Subscriber):
        key = (subscriber.user.userId, subscriber.channel)
        if key not in self.subscribers:   # a duplicate is ignored
            self.subscribers[key] = subscriber

    def detach(self, userId: str, channel: str):
        self.subscribers.pop((userId, channel), None)   # a missing key is ignored

    def publish(self, notificationId: str, content: str) -> List[str]:
        """Notifies every subscriber, sorted by userId then channel, and returns their deliveries."""
        deliveries = []
        for key in sorted(self.subscribers):   # tuple keys sort by userId first, then by channel
            subscriber = self.subscribers[key]
            deliveries.append(subscriber.deliver(notificationId, self.eventType, content))
        return deliveries


class NotificationSystem:
    """Entry point. Checks every call and passes it to the right EventTopic."""

    def __init__(self, supportedChannels: List[str]):
        self.supportedChannels = set(supportedChannels)   # duplicate names collapse into one
        self.users = {}    # userId -> User
        self.topics = {}   # eventType -> EventTopic

    def registerUser(self, userId: str):
        if userId not in self.users:
            self.users[userId] = User(userId)

    def subscribe(self, userId: str, eventType: str, channel: str):
        user = self.users.get(userId)
        if user is None or channel not in self.supportedChannels:
            return
        # create the topic the first time anyone subscribes to this event
        if eventType not in self.topics:
            self.topics[eventType] = EventTopic(eventType)
        self.topics[eventType].attach(Subscriber(user, channel))

    def unsubscribe(self, userId: str, eventType: str, channel: str):
        topic = self.topics.get(eventType)
        if topic is not None:
            topic.detach(userId, channel)

    def sendNotification(self, notificationId: str, eventType: str, content: str) -> List[str]:
        topic = self.topics.get(eventType)
        if topic is None:
            return []
        return topic.publish(notificationId, content)

    def getUserInbox(self, userId: str) -> List[str]:
        user = self.users.get(userId)
        if user is None:
            return []
        return list(user.inbox)   # a copy, so callers can't change the real inbox
```

### Complexity

`S` = total subscriptions, `k` = subscribers of the given event, `m` = number of deliveries in the user's inbox.

| Method | Solution 1 | Solution 2 |
|---|---|---|
| registerUser | O(1) | O(1) |
| subscribe | O(S) | O(1) |
| unsubscribe | O(S) | O(1) |
| sendNotification | O(S + k log k) | O(k log k) |
| getUserInbox | O(m) | O(m) |

Building the delivery strings takes extra time based on their length, and that cost is the same in both solutions.