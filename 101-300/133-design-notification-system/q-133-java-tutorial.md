# Design Notification System in Java

#### Problem Statement
[https://codezym.com/question/133-design-notification-system](https://codezym.com/question/133-design-notification-system)

---

## Core Idea

A notification system is a classic **publish and subscribe** setup. Users sign up for an event type (like `ORDER_PLACED`) on one or more channels. When a notification is sent for that event, every subscriber gets one copy in their inbox for each channel they picked.


This is exactly what the **Observer pattern** is made for. Each event type is a **subject** that keeps track of its listeners. Each `(user, channel)` pair is an **observer** that receives a delivery when the event fires. Observer alone is the best fit here. We don't need to combine it with other patterns because every channel behaves the same way.


The two tricky rules are "no duplicate deliveries" and "results sorted by `userId`, then by `channel`". A **sorted set** of subscribers for each event type handles both at once. A set ignores duplicates, and a sorted set keeps everyone in order, so sending a notification is just a simple loop.


We will start with a simple brute force version, see where it is slow, and then improve it step by step.

---

## Solution 1: Brute Force (One Big List)

Store every subscription as a `{userId, eventType, channel}` entry in one list. Store a map of `userId -> inbox`. If a user has an inbox, the user is registered.

- **subscribe**: ignore unknown users and unsupported channels. Scan the list and add the entry only if it is not there yet.
- **unsubscribe**: scan the list and remove the entry if it is found.
- **sendNotification**: scan the whole list and pick the entries of this `eventType`. Sort them by `userId`, then by `channel`. Build each delivery string and add it to that user's inbox.
- **getUserInbox**: return a copy of the inbox, or an empty list for an unknown user.

> **Watch out:** sort by the `(userId, channel)` pair, not by the final delivery string. The `|` character has a bigger code than letters and digits, so `"U10|EMAIL|..."` would wrongly come before `"U1|EMAIL|..."`, even though `"U1"` comes before `"U10"`.

### Code

```java
import java.util.*;

public class NotificationSystem {

    Set<String> supportedChannels = new HashSet<>();

    // userId -> inbox. A user is registered only if it has an inbox.
    Map<String, List<String>> inboxes = new HashMap<>();

    // every subscription, stored as {userId, eventType, channel}
    List<String[]> subscriptions = new ArrayList<>();

    public NotificationSystem(List<String> supportedChannels) {
        this.supportedChannels.addAll(supportedChannels);
    }

    public void registerUser(String userId) {
        inboxes.putIfAbsent(userId, new ArrayList<>());
    }

    public void subscribe(String userId, String eventType, String channel) {
        if (!inboxes.containsKey(userId) || !supportedChannels.contains(channel)) {
            return;
        }
        if (indexOf(userId, eventType, channel) == -1) {
            subscriptions.add(new String[]{userId, eventType, channel});
        }
    }

    public void unsubscribe(String userId, String eventType, String channel) {
        int index = indexOf(userId, eventType, channel);
        if (index != -1) {
            subscriptions.remove(index);
        }
    }

    public List<String> sendNotification(String notificationId, String eventType, String content) {
        // 1. pick the subscriptions of this event
        List<String[]> matching = new ArrayList<>();
        for (String[] sub : subscriptions) {
            if (sub[1].equals(eventType)) {
                matching.add(sub);
            }
        }

        // 2. sort by userId (index 0), then by channel (index 2)
        matching.sort((a, b) -> {
            int byUser = a[0].compareTo(b[0]);
            return byUser != 0 ? byUser : a[2].compareTo(b[2]);
        });

        // 3. build each delivery and drop it in the user's inbox
        List<String> deliveries = new ArrayList<>();
        for (String[] sub : matching) {
            String delivery = sub[0] + "|" + sub[2] + "|" + notificationId + "|" + eventType + "|" + content;
            inboxes.get(sub[0]).add(delivery);
            deliveries.add(delivery);
        }
        return deliveries;
    }

    public List<String> getUserInbox(String userId) {
        return new ArrayList<>(inboxes.getOrDefault(userId, new ArrayList<>()));
    }

    /** Linear search over all subscriptions. Returns -1 when not found. */
    int indexOf(String userId, String eventType, String channel) {
        for (int i = 0; i < subscriptions.size(); i++) {
            String[] sub = subscriptions.get(i);
            if (sub[0].equals(userId) && sub[1].equals(eventType) && sub[2].equals(channel)) {
                return i;
            }
        }
        return -1;
    }
}
```

### Problems with this approach

- `subscribe` and `unsubscribe` scan **all** subscriptions, even the ones for other events.
- `sendNotification` scans everything too, and then **sorts again on every call**, even when nothing changed since the last send.
- One class does everything, so it is hard to see who is listening to which event.

It works for small inputs, but we can do better.

---

## Solution 2: Observer Pattern with Sorted Subscribers

### Fixing the problems one by one

| Problem in Solution 1 | Fix |
|---|---|
| Scanning the subscriptions of other events | Group subscribers by event type in a `Map<String, EventTopic>` |
| Scanning to find a duplicate or to unsubscribe | Keep each event's subscribers in a **set** |
| Sorting again on every send | Use a **sorted set** (`TreeSet`), so the order is always ready |

After these fixes, every event type becomes a small object that owns its subscribers. That object is the subject of the Observer pattern.

### Why the Observer pattern?

The problem describes Observer almost word for word: listeners attach to an event, detach from it, and every current listener is notified when the event fires.

- `EventTopic` is the **subject**. It has `attach`, `detach` and `publish` (notify everyone).
- `Subscriber` is the **observer**. Its `deliver` method builds the delivery string and puts it in the user's inbox.
- `NotificationSystem` is the front door. It checks the input and hands the call to the right topic.


In Solution 1 one class did all of this. Now each class has one small job, so the code is easier to read and change.


We skip a separate `Observer` interface because there is only one kind of subscriber. If another kind shows up later, `Subscriber` can easily become an interface.

### A pattern that looks useful but doesn't fit

**Strategy (with a Factory).** It is tempting to write `EmailChannel`, `SmsChannel` and `PushChannel` classes and a factory to create them. But here every channel does exactly the same thing: build a string and save it in an inbox.


Channel names are also any strings given at runtime, so one class per channel is not even possible. A `Set<String>` of channel names is all we need.

### Important classes and data structures

- **`User`**: `userId` plus an `ArrayList` inbox. A list keeps deliveries in the exact order they arrived.
- **`Subscriber`**: one `(user, channel)` pair. It holds a reference to its `User`, so it can drop a delivery straight into the inbox. Its `compareTo` orders by `userId`, then by `channel`.
- **`EventTopic`**: one event type plus a `TreeSet<Subscriber>`. `TreeSet` uses `compareTo` for two jobs at once:
  - it keeps subscribers sorted by `(userId, channel)`.
  - it treats two subscribers with the same user and channel as one item, so a duplicate subscription is simply ignored.
- **`NotificationSystem`**:
  - `Set<String> supportedChannels`: a fast "is this channel allowed?" check. Duplicate names in the input collapse into one.
  - `Map<String, User> users`: who is registered.
  - `Map<String, EventTopic> topics`: jump straight to the subscribers of one event without touching other events.

### How each method works

- **registerUser**: add a new `User` only if the id is new.
- **subscribe**: do nothing for an unknown user or an unsupported channel. Otherwise create the event's topic if needed and attach a new `Subscriber`. The `TreeSet` ignores a duplicate.
- **unsubscribe**: do nothing for an unknown user or an event nobody subscribed to. Otherwise detach the subscriber. Removing something that is not there does nothing.
- **sendNotification**: no topic means no subscribers, so return an empty list. Otherwise walk the already sorted subscribers and let each one deliver.
- **getUserInbox**: return a copy of the user's inbox, or an empty list for an unknown user.

### Code

```java
import java.util.*;

/**
 * A registered user.
 * The inbox keeps every delivery in the exact order it arrived.
 */
class User {
    String userId;
    List<String> inbox = new ArrayList<>();

    User(String userId) {
        this.userId = userId;
    }
}

/**
 * Observer: one (user, channel) pair listening to an event type.
 */
class Subscriber implements Comparable<Subscriber> {
    User user;
    String channel;

    Subscriber(User user, String channel) {
        this.user = user;
        this.channel = channel;
    }

    /** Called when the event fires. Builds the delivery and drops it in the user's inbox. */
    String deliver(String notificationId, String eventType, String content) {
        String delivery = user.userId + "|" + channel + "|" + notificationId + "|" + eventType + "|" + content;
        user.inbox.add(delivery);
        return delivery;
    }

    /**
     * Order by userId, then by channel.
     * TreeSet also uses this to spot duplicates: same user and same channel means same subscriber.
     */
    @Override
    public int compareTo(Subscriber other) {
        int byUser = user.userId.compareTo(other.user.userId);
        if (byUser != 0) {
            return byUser;
        }
        return channel.compareTo(other.channel);
    }
}

/**
 * Subject: one event type and everyone subscribed to it.
 * The TreeSet keeps subscribers unique and always sorted by (userId, channel).
 */
class EventTopic {
    String eventType;
    TreeSet<Subscriber> subscribers = new TreeSet<>();

    EventTopic(String eventType) {
        this.eventType = eventType;
    }

    void attach(Subscriber subscriber) {
        subscribers.add(subscriber);      // a duplicate is ignored
    }

    void detach(Subscriber subscriber) {
        subscribers.remove(subscriber);   // a missing subscriber is ignored
    }

    /** Notifies every subscriber in sorted order and returns their deliveries. */
    List<String> publish(String notificationId, String content) {
        List<String> deliveries = new ArrayList<>();
        for (Subscriber subscriber : subscribers) {
            deliveries.add(subscriber.deliver(notificationId, eventType, content));
        }
        return deliveries;
    }
}

/**
 * Entry point. Checks every call and passes it to the right EventTopic.
 */
public class NotificationSystem {

    Set<String> supportedChannels = new HashSet<>();   // duplicate names collapse into one
    Map<String, User> users = new HashMap<>();          // userId -> User
    Map<String, EventTopic> topics = new HashMap<>();   // eventType -> EventTopic

    public NotificationSystem(List<String> supportedChannels) {
        this.supportedChannels.addAll(supportedChannels);
    }

    public void registerUser(String userId) {
        users.putIfAbsent(userId, new User(userId));
    }

    public void subscribe(String userId, String eventType, String channel) {
        User user = users.get(userId);
        if (user == null || !supportedChannels.contains(channel)) {
            return;
        }
        // create the topic the first time anyone subscribes to this event
        EventTopic topic = topics.computeIfAbsent(eventType, key -> new EventTopic(key));
        topic.attach(new Subscriber(user, channel));
    }

    public void unsubscribe(String userId, String eventType, String channel) {
        User user = users.get(userId);
        EventTopic topic = topics.get(eventType);
        if (user == null || topic == null) {
            return;
        }
        topic.detach(new Subscriber(user, channel));
    }

    public List<String> sendNotification(String notificationId, String eventType, String content) {
        EventTopic topic = topics.get(eventType);
        if (topic == null) {
            return new ArrayList<>();
        }
        return topic.publish(notificationId, content);
    }

    public List<String> getUserInbox(String userId) {
        User user = users.get(userId);
        if (user == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(user.inbox);   // a copy, so callers can't change the real inbox
    }
}
```

### Complexity

`S` = total subscriptions, `k` = subscribers of the given event, `m` = number of deliveries in the user's inbox.

| Method | Solution 1 | Solution 2 |
|---|---|---|
| registerUser | O(1) | O(1) |
| subscribe | O(S) | O(log k) |
| unsubscribe | O(S) | O(log k) |
| sendNotification | O(S + k log k) | O(k) |
| getUserInbox | O(m) | O(m) |

Building the delivery strings takes extra time based on their length, and that cost is the same in both solutions.