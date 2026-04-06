import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class MessageStreamingService {

    private final ConcurrentHashMap<String, Topic> topics = new ConcurrentHashMap<>();
    // Stores "next offset to read" for each (topicName, consumerId, partitionId)
    private final ConcurrentHashMap<CursorKey, Long> cursors = new ConcurrentHashMap<>();

    public MessageStreamingService() {
    }

    public boolean createTopic(String topicName, int partitionCount) {
        if (topicName == null || topicName.isEmpty()) {
            throw new IllegalArgumentException("topicName must be non-empty");
        }
        if (partitionCount < 1) {
            throw new IllegalArgumentException("partitionCount must be >= 1");
        }

        Topic topic = new Topic(topicName, partitionCount);
        return topics.putIfAbsent(topicName, topic) == null;
    }

    public String publish(String topicName, int partitionId, String message) {
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("message must be non-empty");
        }

        Topic topic = topics.get(topicName);
        if (topic == null) {
            throw new IllegalArgumentException("Topic does not exist: " + topicName);
        }

        Partition partition = topic.getPartition(partitionId);
        long offset = partition.append(message); // monotonic, publish-order within partition

        return "p" + partitionId + ":" + offset;
    }

    public List<String> consume(String topicName, String consumerId, int partitionId, int maxMessages) {
        if (consumerId == null || consumerId.isEmpty()) {
            throw new IllegalArgumentException("consumerId must be non-empty");
        }
        if (maxMessages < 1) {
            throw new IllegalArgumentException("maxMessages must be >= 1");
        }

        Topic topic = topics.get(topicName);
        if (topic == null) {
            throw new IllegalArgumentException("Topic does not exist: " + topicName);
        }

        Partition partition = topic.getPartition(partitionId);
        CursorKey key = new CursorKey(topicName, consumerId, partitionId);

        // Ensure that concurrent consume() calls for the SAME cursor key are serialized.
        AtomicReference<List<String>> outRef = new AtomicReference<>(Collections.emptyList());

        cursors.compute(key, (k, existingNextOffset) -> {
            long nextOffset = (existingNextOffset == null) ? 0L : existingNextOffset;

            List<String> batch = partition.readFrom(nextOffset, maxMessages);
            outRef.set(batch);

            // Advance cursor only by the number of returned messages; if none, cursor stays the same.
            return nextOffset + batch.size();
        });

        return outRef.get();
    }

    // -------------------- Internal Model --------------------

    private static final class Topic {
        private final String name;
        private final int partitionCount;
        private final List<Partition> partitions;

        private Topic(String name, int partitionCount) {
            this.name = name;
            this.partitionCount = partitionCount;
            this.partitions = new ArrayList<>(partitionCount);
            for (int i = 0; i < partitionCount; i++) {
                this.partitions.add(new Partition());
            }
        }

        private Partition getPartition(int partitionId) {
            if (partitionId < 0 || partitionId >= partitionCount) {
                throw new IllegalArgumentException(
                        "Invalid partitionId " + partitionId + " for topic '" + name + "' (partitionCount=" + partitionCount + ")"
                );
            }
            return partitions.get(partitionId);
        }
    }

    private static final class Partition {
        // Append-only log. Access protected by 'lock' to preserve order and atomicity.
        private final List<String> log = new ArrayList<>();
        private final Object lock = new Object();

        // Returns the assigned offset (monotonically increasing, starting from 0).
        private long append(String message) {
            synchronized (lock) {
                long offset = log.size(); // publish order within the partition
                log.add(message);
                return offset;
            }
        }

        // Reads up to maxMessages starting at 'startOffset' in strictly increasing offset order.
        private List<String> readFrom(long startOffset, int maxMessages) {
            if (startOffset < 0) {
                throw new IllegalArgumentException("startOffset must be >= 0");
            }

            synchronized (lock) {
                int size = log.size();
                if (startOffset >= size) {
                    return Collections.emptyList();
                }
                if (startOffset > Integer.MAX_VALUE) {
                    throw new IllegalStateException("Offset too large for in-memory ArrayList indexing: " + startOffset);
                }

                int from = (int) startOffset;
                int to = Math.min(size, from + maxMessages);

                List<String> result = new ArrayList<>(to - from);
                for (int i = from; i < to; i++) {
                    result.add(log.get(i));
                }
                return result;
            }
        }
    }

    private static final class CursorKey {
        private final String topicName;
        private final String consumerId;
        private final int partitionId;

        private CursorKey(String topicName, String consumerId, int partitionId) {
            this.topicName = topicName;
            this.consumerId = consumerId;
            this.partitionId = partitionId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CursorKey)) return false;
            CursorKey that = (CursorKey) o;
            return partitionId == that.partitionId
                    && Objects.equals(topicName, that.topicName)
                    && Objects.equals(consumerId, that.consumerId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(topicName, consumerId, partitionId);
        }
    }
}
