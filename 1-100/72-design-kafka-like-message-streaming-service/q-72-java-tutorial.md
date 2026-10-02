# Design Kafka Like Message Streaming Service in Java

#### Problem Statement
[https://codezym.com/question/72-design-kafka-like-message-streaming-service](https://codezym.com/question/72-design-kafka-like-message-streaming-service)

## Core Idea

Think of every partition as a notebook where we only write on the next empty line and never erase anything. A message's offset is simply its line number (counting from 0).


Every consumer only needs a bookmark: the line number it should read next. Different consumers keep their own bookmarks in the same notebook, so they never disturb each other.


So the whole design is three small classes: `MessageStreamingService` (the entry point that holds all topics), `Topic` (holds its partitions) and `Partition` (holds the list of messages and one bookmark per consumer).


No design pattern is needed here. Observer looks like a perfect fit for publish/subscribe, but in this problem consumers pull messages at their own pace and a new consumer must also read old messages. Plain classes with a list and a map give the simplest and fastest solution.

## Do We Need a Design Pattern?

**Observer** sounds right on paper. A partition could keep a list of subscribers and push every new message to them. But it does not fit this problem.

- Consumers pull. They call `consume()` whenever they want, with their own batch size (`maxMessages`).
- There is no subscribe step. A consumer shows up for the first time inside `consume()`, and it must still get every old message from offset `0`. Observer only delivers messages published after subscribing.
- To make Observer work, every consumer would need its own inbox to hold the pushed messages. That is exactly Solution 1 below, and we will see why it wastes memory and time.


**Iterator** also comes to mind, because a consumer walks through the messages one by one. But a Java list iterator throws a `ConcurrentModificationException` if the list grows after the iterator was created, and here new messages keep arriving. A plain `int` index does the same job and never breaks.


So we skip patterns and use three small classes that hold each other: the service holds topics, a topic holds partitions. Each class does one small job.

## Class Design

Both solutions below use the same three classes. Only the inside of `Partition` changes.

```
MessageStreamingService
  topics : Map<topicName, Topic>
    Topic
      partitions : List<Partition>     (list index = partition id)
        Partition
          messages : List<String>      (list index = offset)
```

- **`MessageStreamingService`**: the only class the caller talks to. A `HashMap` finds a topic by name in O(1), and `containsKey()` tells us if a name is already taken. String keys are case-sensitive, so `"Orders"` and `"orders"` are two different topics, as required.

- **`Topic`**: partition ids are always `0` to `partitionCount - 1`, so a simple `ArrayList` is enough. The list index is the partition id.

- **`Partition`**: an append-only `ArrayList` of messages. A new message always goes to the end, so after adding it, its offset is just `size() - 1`. This also means offsets follow the publish order.


There is no `Consumer` class. A consumer here is only an id plus a reading position, and that position belongs to a partition.

## Solution 1: One Inbox Per Consumer (Brute Force)

The very first idea is to treat each partition like a queue and remove a message once it is read. That breaks Example 2: after `c1` reads `o1` and `o2` they are gone, so `c2` gets nothing.


The fix is to give **every consumer its own inbox** (a queue) in each partition.

- `publish()` adds the message to the partition's full list and to every existing inbox.
- When a consumer reads a partition for the first time, its inbox starts as a copy of the full list, because it must read from offset `0`.
- `consume()` takes up to `maxMessages` messages out of that consumer's inbox.

### Code

```java
import java.util.*;

/**
 * Solution 1 (brute force): every consumer gets its own inbox (queue) per partition.
 * Correct, but the same message is added to many inboxes.
 */
public class MessageStreamingService {

    // topic name -> topic
    Map<String, Topic> topics = new HashMap<>();

    public MessageStreamingService() {
    }

    public boolean createTopic(String topicName, int partitionCount) {
        if (topics.containsKey(topicName)) return false;
        topics.put(topicName, new Topic(partitionCount));
        return true;
    }

    public String publish(String topicName, int partitionId, String message) {
        Partition partition = getPartition(topicName, partitionId);
        if (partition == null) return "";
        int offset = partition.append(message);
        return "p" + partitionId + ":" + offset;
    }

    public List<String> consume(String topicName, String consumerId, int partitionId, int maxMessages) {
        Partition partition = getPartition(topicName, partitionId);
        if (partition == null) return new ArrayList<>();
        return partition.read(consumerId, maxMessages);
    }

    /** Returns the partition, or null if the topic or the partition id does not exist. */
    Partition getPartition(String topicName, int partitionId) {
        Topic topic = topics.get(topicName);
        if (topic == null) return null;
        return topic.getPartition(partitionId);
    }
}

/** A topic is a fixed list of partitions. List index = partition id. */
class Topic {
    List<Partition> partitions = new ArrayList<>();

    Topic(int partitionCount) {
        for (int i = 0; i < partitionCount; i++) {
            partitions.add(new Partition());
        }
    }

    /** Returns null for an invalid partition id. */
    Partition getPartition(int partitionId) {
        if (partitionId < 0 || partitionId >= partitions.size()) return null;
        return partitions.get(partitionId);
    }
}

class Partition {
    // full history, needed because a new consumer must start from offset 0
    List<String> messages = new ArrayList<>();
    // consumerId -> messages this consumer has not read yet
    Map<String, Queue<String>> inboxes = new HashMap<>();

    /** Adds the message to the log and to every inbox. Returns its offset. */
    int append(String message) {
        messages.add(message);
        for (Queue<String> inbox : inboxes.values()) {
            inbox.add(message);
        }
        return messages.size() - 1;
    }

    /** Takes up to maxMessages messages out of this consumer's inbox. */
    List<String> read(String consumerId, int maxMessages) {
        // first read: copy the whole history into a new inbox
        if (!inboxes.containsKey(consumerId)) {
            inboxes.put(consumerId, new LinkedList<>(messages));
        }
        Queue<String> inbox = inboxes.get(consumerId);
        List<String> batch = new ArrayList<>();
        while (!inbox.isEmpty() && batch.size() < maxMessages) {
            batch.add(inbox.poll());
        }
        return batch;
    }
}
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

- **`messages` (`ArrayList<String>`)**: the append-only log, shared by all consumers. We read by offset, and `get(index)` on an `ArrayList` is O(1).

- **`cursors` (`HashMap<String, Integer>`)**: `consumerId -> next offset to read`. Consumers can show up at any time, and `getOrDefault(consumerId, 0)` makes a brand new consumer start from offset `0` without any sign up step.


Reading never deletes anything. It only moves that reader's cursor forward, so other consumers are not affected.

### How `consume()` Works

1. Get the consumer's cursor (`0` if we have never seen this consumer).
2. Collect messages from the cursor until we reach the end of the list or have `maxMessages` of them.
3. Save the new cursor.


We walk the list from left to right, so messages always come out in increasing offset order.

### Dry Run (Example 2)

| Call | Messages | c1 cursor | c2 cursor | Returns |
|---|---|---|---|---|
| `publish("orders", 0, "o1")` | `[o1]` | | | `"p0:0"` |
| `publish("orders", 0, "o2")` | `[o1, o2]` | | | `"p0:1"` |
| `consume("orders", "c1", 0, 1)` | `[o1, o2]` | 0 → 1 | | `[o1]` |
| `consume("orders", "c1", 0, 5)` | `[o1, o2]` | 1 → 2 | | `[o2]` |
| `consume("orders", "c1", 0, 5)` | `[o1, o2]` | 2 | | `[]` |
| `consume("orders", "c2", 0, 10)` | `[o1, o2]` | 2 | 0 → 2 | `[o1, o2]` |
| `consume("orders", "c2", 0, 10)` | `[o1, o2]` | 2 | 2 | `[]` |

### Code

```java
import java.util.*;

/**
 * Solution 2 (optimal): each partition is an append-only list of messages,
 * and every consumer only stores the offset of the next message it should read.
 */
public class MessageStreamingService {

    // topic name -> topic (String keys are case-sensitive, as required)
    Map<String, Topic> topics = new HashMap<>();

    public MessageStreamingService() {
    }

    public boolean createTopic(String topicName, int partitionCount) {
        if (topics.containsKey(topicName)) return false;
        topics.put(topicName, new Topic(partitionCount));
        return true;
    }

    public String publish(String topicName, int partitionId, String message) {
        Partition partition = getPartition(topicName, partitionId);
        if (partition == null) return "";
        int offset = partition.append(message);
        return "p" + partitionId + ":" + offset;
    }

    public List<String> consume(String topicName, String consumerId, int partitionId, int maxMessages) {
        Partition partition = getPartition(topicName, partitionId);
        if (partition == null) return new ArrayList<>();
        return partition.read(consumerId, maxMessages);
    }

    /** Returns the partition, or null if the topic or the partition id does not exist. */
    Partition getPartition(String topicName, int partitionId) {
        Topic topic = topics.get(topicName);
        if (topic == null) return null;
        return topic.getPartition(partitionId);
    }
}

/** A topic is a fixed list of partitions. List index = partition id. */
class Topic {
    List<Partition> partitions = new ArrayList<>();

    Topic(int partitionCount) {
        for (int i = 0; i < partitionCount; i++) {
            partitions.add(new Partition());
        }
    }

    /** Returns null for an invalid partition id. */
    Partition getPartition(int partitionId) {
        if (partitionId < 0 || partitionId >= partitions.size()) return null;
        return partitions.get(partitionId);
    }
}

/** Append-only log of messages + one cursor per consumer. Offset = index in the list. */
class Partition {
    List<String> messages = new ArrayList<>();
    // consumerId -> offset of the next message this consumer should read
    Map<String, Integer> cursors = new HashMap<>();

    /** Adds the message at the end and returns its offset. */
    int append(String message) {
        messages.add(message);
        return messages.size() - 1;
    }

    /** Returns up to maxMessages messages from this consumer's cursor and moves the cursor forward. */
    List<String> read(String consumerId, int maxMessages) {
        int offset = cursors.getOrDefault(consumerId, 0); // a new consumer starts at 0
        List<String> batch = new ArrayList<>();
        while (offset < messages.size() && batch.size() < maxMessages) {
            batch.add(messages.get(offset));
            offset++;
        }
        cursors.put(consumerId, offset);
        return batch;
    }
}
```

## Complexity

`P` = partition count, `N` = messages in a partition, `C` = consumers reading that partition, `k` = messages returned by one `consume()` call.

| Operation | Solution 1 (Inbox) | Solution 2 (Cursor) |
|---|---|---|
| `createTopic` | O(P) | O(P) |
| `publish` | O(C) | O(1) |
| `consume` | O(k), but O(N) on a consumer's first read | O(k) |
| Memory per partition | O(N × C) in the worst case | O(N + C) |