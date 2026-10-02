# Design Kafka Like Message Streaming Service in Python

#### Problem Statement
[https://codezym.com/question/72-design-kafka-like-message-streaming-service](https://codezym.com/question/72-design-kafka-like-message-streaming-service)

## Core Idea

Think of every partition as a notebook where we only write on the next empty line and never erase anything. A message's offset is simply its line number (counting from 0).


Every consumer only needs a bookmark: the line number it should read next. Different consumers keep their own bookmarks in the same notebook, so they never disturb each other.


So the whole design is three small classes: `MessageStreamingService` (the entry point that holds all topics), `Topic` (holds its partitions) and `Partition` (holds the list of messages and one bookmark per consumer).


No design pattern is needed here. Observer looks like a perfect fit for publish/subscribe, but in this problem consumers pull messages at their own pace and a new consumer must also read old messages. Plain classes with a list and a dict give the simplest and fastest solution.

## Do We Need a Design Pattern?

**Observer** sounds right on paper. A partition could keep a list of subscribers and push every new message to them. But it does not fit this problem.

- Consumers pull. They call `consume()` whenever they want, with their own batch size (`maxMessages`).
- There is no subscribe step. A consumer shows up for the first time inside `consume()`, and it must still get every old message from offset `0`. Observer only delivers messages published after subscribing.
- To make Observer work, every consumer would need its own inbox to hold the pushed messages. That is exactly Solution 1 below, and we will see why it wastes memory and time.


**Iterator** also comes to mind, because a consumer walks through the messages one by one. But a list iterator that has reached the end stays finished, even if new items are appended to the list later. A consumer that has caught up would never see new messages. A plain `int` index does the same job and never gets stuck.


So we skip patterns and use three small classes that hold each other: the service holds topics, a topic holds partitions. Each class does one small job.

## Class Design

Both solutions below use the same three classes. Only the inside of `Partition` changes.

```
MessageStreamingService
  topics : dict (topicName -> Topic)
    Topic
      partitions : list of Partition     (list index = partition id)
        Partition
          messages : list of str         (list index = offset)
```

- **`MessageStreamingService`**: the only class the caller talks to. A `dict` finds a topic by name in O(1), and `topicName in self.topics` tells us if a name is already taken. Dict keys are case-sensitive, so `"Orders"` and `"orders"` are two different topics, as required.

- **`Topic`**: partition ids are always `0` to `partitionCount - 1`, so a simple `list` is enough. The list index is the partition id.

- **`Partition`**: an append-only `list` of messages. A new message always goes to the end, so after adding it, its offset is just `len(messages) - 1`. This also means offsets follow the publish order.


There is no `Consumer` class. A consumer here is only an id plus a reading position, and that position belongs to a partition.

## Solution 1: One Inbox Per Consumer (Brute Force)

The very first idea is to treat each partition like a queue and remove a message once it is read. That breaks Example 2: after `c1` reads `o1` and `o2` they are gone, so `c2` gets nothing.


The fix is to give **every consumer its own inbox** (a queue) in each partition.

- `publish()` adds the message to the partition's full list and to every existing inbox.
- When a consumer reads a partition for the first time, its inbox starts as a copy of the full list, because it must read from offset `0`.
- `consume()` takes up to `maxMessages` messages out of that consumer's inbox.


Each inbox is a `deque`, because taking a message from the front with `popleft()` is O(1). Doing the same on a normal list with `pop(0)` is O(n).

### Code

```python
from collections import deque


class MessageStreamingService:
    """Solution 1 (brute force): every consumer gets its own inbox (queue) per partition.
    Correct, but the same message is added to many inboxes."""

    def __init__(self):
        # topic name -> Topic
        self.topics = {}

    def createTopic(self, topicName, partitionCount):
        if topicName in self.topics:
            return False
        self.topics[topicName] = Topic(partitionCount)
        return True

    def publish(self, topicName, partitionId, message):
        partition = self.get_partition(topicName, partitionId)
        if partition is None:
            return ""
        offset = partition.append(message)
        return f"p{partitionId}:{offset}"

    def consume(self, topicName, consumerId, partitionId, maxMessages):
        partition = self.get_partition(topicName, partitionId)
        if partition is None or maxMessages < 1:  # invalid input, nothing to read
            return []
        return partition.read(consumerId, maxMessages)

    def get_partition(self, topic_name, partition_id):
        """Returns the partition, or None if the topic or the partition id does not exist."""
        topic = self.topics.get(topic_name)
        if topic is None:
            return None
        return topic.get_partition(partition_id)


class Topic:
    """A topic is a fixed list of partitions. List index = partition id."""

    def __init__(self, partition_count):
        self.partitions = [Partition() for _ in range(partition_count)]

    def get_partition(self, partition_id):
        """Returns None for an invalid partition id."""
        if partition_id < 0 or partition_id >= len(self.partitions):
            return None
        return self.partitions[partition_id]


class Partition:
    def __init__(self):
        # full history, needed because a new consumer must start from offset 0
        self.messages = []
        # consumer_id -> deque of messages this consumer has not read yet
        self.inboxes = {}

    def append(self, message):
        """Adds the message to the log and to every inbox. Returns its offset."""
        self.messages.append(message)
        for inbox in self.inboxes.values():
            inbox.append(message)
        return len(self.messages) - 1

    def read(self, consumer_id, max_messages):
        """Takes up to max_messages messages out of this consumer's inbox."""
        # first read: copy the whole history into a new inbox
        if consumer_id not in self.inboxes:
            self.inboxes[consumer_id] = deque(self.messages)
        inbox = self.inboxes[consumer_id]
        batch = []
        while inbox and len(batch) < max_messages:
            batch.append(inbox.popleft())
        return batch
```

### What Is Wrong With It?

It gives correct answers, but it does a lot of extra work.

- **Wasted memory:** a message waits in every consumer's inbox until that consumer reads it. With many slow consumers, the same message is kept in many places.
- **Slow publish:** every `publish()` loops over all inboxes, so it costs O(C) instead of O(1), where `C` is the number of consumers.
- **Slow first read:** a new consumer copies the whole history into its inbox, which costs O(N) for `N` messages.

## Solution 2: Shared Log + Cursor (Optimal)

### The Key Insight

Look at any inbox from Solution 1. It always holds the messages from some position up to the end of the list. If `c1` has read 2 out of 5 messages, its inbox holds exactly the messages at offsets `2`, `3` and `4`.


So an inbox is always just the "tail" of the message list. We do not need to copy that tail. We only need to remember **where it starts**, which is one integer.


That integer is the consumer's **cursor**: the offset of the next message it should read.

### Inside the Partition

- **`messages` (`list`)**: the append-only log, shared by all consumers. The slice `messages[start:start + max_messages]` gives the whole batch in one step, and a slice simply stops at the end of the list, so we never go out of range.

- **`cursors` (`dict`)**: `consumer_id -> next offset to read`. Consumers can show up at any time, and `cursors.get(consumer_id, 0)` makes a brand new consumer start from offset `0` without any sign up step.


Reading never deletes anything. It only moves that reader's cursor forward, so other consumers are not affected.

### How `consume()` Works

1. Get the consumer's cursor (`0` if we have never seen this consumer).
2. Take the slice of up to `maxMessages` messages starting at the cursor.
3. Move the cursor forward by the number of messages we took.


The slice keeps the list order, so messages always come out in increasing offset order.

### Dry Run (Example 2)

| Call | Messages | c1 cursor | c2 cursor | Returns |
|---|---|---|---|---|
| `publish("orders", 0, "o1")` | `["o1"]` | | | `"p0:0"` |
| `publish("orders", 0, "o2")` | `["o1", "o2"]` | | | `"p0:1"` |
| `consume("orders", "c1", 0, 1)` | `["o1", "o2"]` | 0 → 1 | | `["o1"]` |
| `consume("orders", "c1", 0, 5)` | `["o1", "o2"]` | 1 → 2 | | `["o2"]` |
| `consume("orders", "c1", 0, 5)` | `["o1", "o2"]` | 2 | | `[]` |
| `consume("orders", "c2", 0, 10)` | `["o1", "o2"]` | 2 | 0 → 2 | `["o1", "o2"]` |
| `consume("orders", "c2", 0, 10)` | `["o1", "o2"]` | 2 | 2 | `[]` |

### Code

```python
class MessageStreamingService:
    """Solution 2 (optimal): each partition is an append-only list of messages,
    and every consumer only stores the offset of the next message it should read."""

    def __init__(self):
        # topic name -> Topic (dict keys are case-sensitive, as required)
        self.topics = {}

    def createTopic(self, topicName, partitionCount):
        if topicName in self.topics:
            return False
        self.topics[topicName] = Topic(partitionCount)
        return True

    def publish(self, topicName, partitionId, message):
        partition = self.get_partition(topicName, partitionId)
        if partition is None:
            return ""
        offset = partition.append(message)
        return f"p{partitionId}:{offset}"

    def consume(self, topicName, consumerId, partitionId, maxMessages):
        partition = self.get_partition(topicName, partitionId)
        if partition is None or maxMessages < 1:  # invalid input, nothing to read
            return []
        return partition.read(consumerId, maxMessages)

    def get_partition(self, topic_name, partition_id):
        """Returns the partition, or None if the topic or the partition id does not exist."""
        topic = self.topics.get(topic_name)
        if topic is None:
            return None
        return topic.get_partition(partition_id)


class Topic:
    """A topic is a fixed list of partitions. List index = partition id."""

    def __init__(self, partition_count):
        self.partitions = [Partition() for _ in range(partition_count)]

    def get_partition(self, partition_id):
        """Returns None for an invalid partition id."""
        if partition_id < 0 or partition_id >= len(self.partitions):
            return None
        return self.partitions[partition_id]


class Partition:
    """Append-only log of messages + one cursor per consumer. Offset = index in the list."""

    def __init__(self):
        self.messages = []
        # consumer_id -> offset of the next message this consumer should read
        self.cursors = {}

    def append(self, message):
        """Adds the message at the end and returns its offset."""
        self.messages.append(message)
        return len(self.messages) - 1

    def read(self, consumer_id, max_messages):
        """Returns up to max_messages messages from this consumer's cursor and moves the cursor forward."""
        start = self.cursors.get(consumer_id, 0)  # a new consumer starts at 0
        batch = self.messages[start:start + max_messages]  # slicing stops at the end of the list
        self.cursors[consumer_id] = start + len(batch)
        return batch
```

## Complexity

`P` = partition count, `N` = messages in a partition, `C` = consumers reading that partition, `k` = messages returned by one `consume()` call.

| Operation | Solution 1 (Inbox) | Solution 2 (Cursor) |
|---|---|---|
| `createTopic` | O(P) | O(P) |
| `publish` | O(C) | O(1) |
| `consume` | O(k), but O(N) on a consumer's first read | O(k) |
| Memory per partition | O(N × C) in the worst case | O(N + C) |