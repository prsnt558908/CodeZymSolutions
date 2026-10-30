# Design a Single-Queue Publish Subscribe System in Python

#### Problem Statement
[https://codezym.com/question/33-design-single-queue-publish-subscribe-system](https://codezym.com/question/33-design-single-queue-publish-subscribe-system)


The core idea is simple. Every message is stored **only once**, in one shared list that works as the global FIFO queue. Each subscriber remembers its own **read position** in that list, which is the index of the next message it will read. This is what lets every subscriber consume at its own pace.


This fits the **Observer pattern** perfectly. `SingleQueuePubSubSystem` is the Subject (the queue manager): it adds each new message to the queue and notifies the active subscribers. `Subscriber` is the Observer: it reads new messages from its read position, processes only the event types it cares about, and keeps a running count.


Observer alone is the optimal choice here. No combination of patterns is needed. Singleton and Iterator look useful on paper, but they do not help this problem, as we will see below.

## Key Rules to Remember

- A subscriber processes only messages sent **while it is subscribed**. Nothing older, and nothing from the time it was removed.
- Only messages whose `eventType` is in its list are processed. Others are ignored.
- Adding a subscriber again **replaces** its old event type list.
- The processed count is **never reset**. It adds up across remove and add.
- A blank subscriber id is ignored. An unknown subscriber has a count of `0`.

## A Simple First Approach: Count Only When Asked

The simplest idea is to notify nobody. We just append messages to the queue and work out the count later, when someone asks for it.


For that, we remember every subscribe session as a window of the queue. `addSubscriber` opens a session at the current queue size, and `removeSubscriber` closes it at the current queue size.

```python
class Session:
    """One subscribe session: the window [startIndex, endIndex) of the queue."""

    def __init__(self, eventTypes, startIndex):
        self.eventTypes = set(eventTypes or [])
        self.startIndex = startIndex   # queue size when the subscriber joined
        self.endIndex = -1             # queue size when it left, -1 while still subscribed


# inside SingleQueuePubSubSystem
def sendMessage(self, eventType, message):
    self.queue.append(Message(eventType, message))   # just append, nobody is told


def countProcessedMessages(self, subscriberId):
    count = 0
    for session in self.sessionsById.get(subscriberId, []):
        end = len(self.queue) if session.endIndex == -1 else session.endIndex
        for i in range(session.startIndex, end):
            if self.queue[i].eventType in session.eventTypes:
                count += 1
    return count
```

The counts are correct, but this approach has clear problems.

- **Nobody is notified:** subscribers never really process anything. We only pretend at count time. A real subscriber has to act on a message when it arrives, not later.
- **Repeated work:** every count call scans all messages of all sessions again, even the ones it counted last time.
- **Growing history:** every `addSubscriber` call adds one more session to keep forever.

## Better Approach: Notify Subscribers and Keep a Running Count

Let us do the work **once**, when a message arrives.


`sendMessage` appends the message to the queue and notifies every active subscriber. Each subscriber reads the new message, checks its event type, and adds 1 to its count if it matches. Now `countProcessedMessages` just returns a stored number.


**Why a read position?** A subscriber does not get its own copy of the message. It reads straight from the shared queue, starting at its read position, and then moves the position forward. So each message is stored once, no matter how many subscribers there are.


In this single-threaded version, a subscriber reads new messages as soon as it is notified. In a real system it could fall behind and catch up later from the same position. That is what "consume at its own pace" means.

```
queue:  pre-1  o-1  p-1  s-1  p-2  o-2  r-1
index:    0     1    2    3    4    5    6
```

In the example, `S3` joins when the queue has 2 messages, so its read position starts at `2`. It never sees `pre-1` or `o-1`.


Two small steps handle the remaining rules.

- **Leaving:** `removeSubscriber` only takes the subscriber out of the active dictionary. Its object is kept, so its count is never lost.
- **Coming back:** `addSubscriber` reuses the same object, sets the new event types, and moves the read position to the end of the queue. `S1` comes back when the queue has 6 messages, so it starts at `6` and skips `o-2`.

## Design Patterns: What We Use and What We Skip

### Observer (our choice)

One thing changes (the queue) and many others must hear about it (the subscribers). This one-to-many update is exactly what the Observer pattern is for.

- `SingleQueuePubSubSystem` is the Subject. `addSubscriber` attaches, `removeSubscriber` detaches and `sendMessage` notifies.
- `Subscriber` is the Observer. `onNewMessage` reacts to the notification.


Why not plain code? `SingleQueuePubSubSystem` could keep a dictionary of filters and a dictionary of counts and do all the work itself. It gives the same answers, but then this one class must know how every subscriber processes messages. With Observer, it only appends and notifies. Each subscriber owns its own filter, read position and count.


There is only one kind of subscriber, so we skip a separate Observer base class. If you later need different kinds, make `Subscriber` an abstract base class with one method, `onNewMessage`.

### Why not Singleton?

"Exactly one global queue" sounds like a Singleton. But that rule is already true: each `SingleQueuePubSubSystem` object owns exactly one list, and the caller works with just one object. A Singleton also keeps one shared instance at class level, so old messages and subscribers would leak from one test case into the next.

### Why not Iterator?

A subscriber walks through the queue one message at a time, so a Python iterator looks natural. But a list iterator that has reached the end stays finished, even if new items are added to the list later. A subscriber that has caught up would never see new messages. A plain `int` read position does the same job and never gets stuck.

## Class Design

- **`Message`:** one entry in the queue, its `eventType` and its content.

- **`Subscriber`:** keeps its event types in a `set`, so checking a message type is O(1). It also keeps `readIndex` (its read position) and `processedCount`.

- **`SingleQueuePubSubSystem`:** the queue manager. It keeps three things.
  - `queue`: a `list`. New messages always go at the end, and reading by index is O(1).
  - `activeSubscribers`: a `dict` of who is subscribed right now. Only these are notified, so removed subscribers cost nothing in `sendMessage`.
  - `allSubscribers`: a `dict` of everyone ever added. It keeps counts alive after removal and makes `countProcessedMessages` a single lookup.

## Complexity

`K` = event types in a list, `S` = active subscribers, `M` = messages in the queue.

| Method | Brute Force | Final |
|---|---|---|
| `addSubscriber` | O(K) | O(K) |
| `removeSubscriber` | O(1) | O(1) |
| `sendMessage` | O(1) | O(S) |
| `countProcessedMessages` | O(M) | O(1) |

The final version does a little work on every send, so that every count is instant. Each message is checked only once per active subscriber, and it is stored only once in memory.

## Final Code

```python
class Message:
    """One entry in the global FIFO queue."""

    def __init__(self, eventType, content):
        self.eventType = eventType
        self.content = content


class Subscriber:
    """Observer.
    Reads the shared queue from its own read position and
    processes only the event types it is interested in.
    """

    def __init__(self, subscriberId):
        self.subscriberId = subscriberId
        self.eventTypesToProcess = set()
        self.readIndex = 0        # index of the next message this subscriber will read
        self.processedCount = 0   # never reset, so it adds up across subscribe sessions

    def subscribe(self, eventTypes, startIndex):
        """Starts a new session: a new filter, and only messages sent from now on."""
        self.eventTypesToProcess = set(eventTypes or [])
        self.readIndex = startIndex

    def onNewMessage(self, queue):
        """Called by SingleQueuePubSubSystem whenever a new message is added."""
        # read every message we have not read yet
        while self.readIndex < len(queue):
            message = queue[self.readIndex]
            self.readIndex += 1
            if message.eventType in self.eventTypesToProcess:
                self.processedCount += 1   # processing a message here means counting it


class SingleQueuePubSubSystem:
    """Subject (the queue manager).
    Owns the single global FIFO queue and notifies the active subscribers.
    """

    def __init__(self):
        self.queue = []               # the one global FIFO queue
        self.activeSubscribers = {}   # subscriberId -> Subscriber, only these get notified
        self.allSubscribers = {}      # subscriberId -> Subscriber, everyone ever added

    def addSubscriber(self, subscriberId, eventTypesToProcess):
        if not subscriberId or not subscriberId.strip():
            return   # ids must be non-blank
        subscriber = self.allSubscribers.get(subscriberId)
        if subscriber is None:
            subscriber = Subscriber(subscriberId)
            self.allSubscribers[subscriberId] = subscriber
        # start reading at the current end of the queue, so older messages are skipped
        subscriber.subscribe(eventTypesToProcess, len(self.queue))
        self.activeSubscribers[subscriberId] = subscriber

    def removeSubscriber(self, subscriberId):
        # the Subscriber object stays in allSubscribers, so its count is never lost
        self.activeSubscribers.pop(subscriberId, None)

    def sendMessage(self, eventType, message):
        self.queue.append(Message(eventType, message))
        for subscriber in self.activeSubscribers.values():
            subscriber.onNewMessage(self.queue)

    def countProcessedMessages(self, subscriberId):
        subscriber = self.allSubscribers.get(subscriberId)
        if subscriber is None:
            return 0
        return subscriber.processedCount
```