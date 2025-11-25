import java.util.*;

/**
 * Single-queue in-memory Pub/Sub (no threads, no locks).
 *
 * - Exactly one global FIFO queue of (eventType, message).
 * - Only subscribers active at send-time are notified.
 * - No retroactive delivery (re-subscribed users start from "now").
 * - Filtering by eventType.
 * - Processed counts accumulate across unsubscribe/resubscribe.
 */
public class SingleQueuePubSubSystem {

    // === Global FIFO event model ===
    private static final class Event {
        final String eventType;
        final String payload;
        Event(String eventType, String payload) {
            this.eventType = eventType;
            this.payload = payload;
        }
    }

    // === Subscriber snapshot ===
    private static final class Subscriber {
        final String id;
        final Set<String> filters;
        final int joinedAtIndex; // index in globalQueue at (re)subscribe time (for clarity; not used for scanning)
        Subscriber(String id, Collection<String> filters, int joinedAtIndex) {
            this.id = id;
            this.filters = new HashSet<>(filters == null ? Collections.emptyList() : filters);
            this.joinedAtIndex = joinedAtIndex;
        }
        boolean accepts(String eventType) { return filters.contains(eventType); }
    }

    // Single global FIFO queue
    private final List<Event> globalQueue = new ArrayList<>();

    // Active subscribers by id
    private final Map<String, Subscriber> active = new HashMap<>();

    // Cumulative processed counts across sessions
    private final Map<String, Integer> processedCounts = new HashMap<>();

    public SingleQueuePubSubSystem() {
        // nothing else to init
    }

    /**
     * Registers a subscriber (replaces filters if already existed before).
     * Starts from the current tail — no retroactive delivery.
     */
    public void addSubscriber(String subscriberId, List<String> eventTypesToProcess) {
        if (subscriberId == null || subscriberId.trim().isEmpty()) return;

        // If already active, remove (filters replaced per spec)
        removeSubscriber(subscriberId);

        int tail = globalQueue.size(); // "now"
        Subscriber sub = new Subscriber(subscriberId, eventTypesToProcess, tail);
        active.put(subscriberId, sub);
        // Note: processedCounts is NOT reset; it accumulates across sessions by design.
    }

    /**
     * Unsubscribes the subscriber if active; stops further processing.
     */
    public void removeSubscriber(String subscriberId) {
        if (subscriberId == null) return;
        active.remove(subscriberId);
    }

    /**
     * Appends a message to the single global FIFO queue and synchronously
     * "notifies" current subscribers. Only those whose filter contains the eventType
     * are counted as having processed the message.
     */
    public void sendMessage(String eventType, String message) {
        if (eventType == null || message == null) return;

        // Append to FIFO
        globalQueue.add(new Event(eventType, message));

        // Notify current subscribers (no retroactive delivery; only those currently active)
        for (Subscriber sub : active.values()) {
            if (sub.accepts(eventType)) {
                processedCounts.merge(sub.id, 1, Integer::sum);
            }
        }
    }

    /**
     * Returns total number of messages this subscriber has actually processed so far,
     * including across unsubscribe/resubscribe sessions.
     */
    public int countProcessedMessages(String subscriberId) {
        if (subscriberId == null) return 0;
        return processedCounts.getOrDefault(subscriberId, 0);
        // If subscriber never processed anything (or never existed), returns 0.
    }
}
