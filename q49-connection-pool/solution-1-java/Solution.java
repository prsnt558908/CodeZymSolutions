import java.util.*;

/**
 * In-memory ConnectionPool with:
 * - Fixed capacity of connections [0..capacity-1]
 * - Lowest-index allocation when free connections exist
 * - FIFO wait-queue when pool is full
 * - expireRequest removes a queued request (no-op otherwise)
 * - getRequestsWithConnection returns "requestId-connectionId" sorted lexicographically by requestId
 *
 * Assumption (per statement): acquireConnection is never called with a duplicate requestId.
 */
public class ConnectionPool {

    private final int capacity;

    // Min-heap of free connection ids so we always pick the lowest index
    private final PriorityQueue<Integer> freeConnections;

    // FIFO queue of waiting requestIds (some may later be expired; handled via waitingSet check)
    private final ArrayDeque<String> waitQueue;

    // Fast membership check for queued requests (also supports expire)
    private final HashSet<String> waitingSet;

    // requestId -> connectionId for currently held connections
    private final HashMap<String, Integer> holding;

    public ConnectionPool(int capacity) {
        this.capacity = capacity;

        this.freeConnections = new PriorityQueue<>();
        for (int i = 0; i < capacity; i++) {
            freeConnections.add(i);
        }

        this.waitQueue = new ArrayDeque<>();
        this.waitingSet = new HashSet<>();
        this.holding = new HashMap<>();
    }

    public int acquireConnection(String requestId) {
        // Per statement, requestId is unique and non-empty; no duplicate calls for acquire.
        if (!freeConnections.isEmpty()) {
            int connId = freeConnections.poll();
            holding.put(requestId, connId);
            return connId;
        }

        // Pool full -> enqueue and return -1
        waitQueue.addLast(requestId);
        waitingSet.add(requestId);
        return -1;
    }

    public boolean releaseConnection(String requestId) {
        Integer connId = holding.remove(requestId);
        if (connId == null) {
            return false; // invalid or not holding
        }

        // If there is a queued request, assign immediately to the oldest non-expired request.
        String nextRequest = pollNextValidWaitingRequest();
        if (nextRequest != null) {
            holding.put(nextRequest, connId);
            return true;
        }

        // Otherwise, connection becomes free again.
        freeConnections.add(connId);
        return true;
    }

    public void expireRequest(String requestId) {
        // Only affects queued requests; no-op if not queued or if holding.
        if (waitingSet.remove(requestId)) {
            // We do lazy removal from the FIFO queue to keep this O(1).
            // The actual removal is handled when we poll from the queue.
        }
    }

    public List<String> getRequestsWithConnection() {
        ArrayList<String> res = new ArrayList<>(holding.size());

        // Sort requestIds lexicographically
        ArrayList<String> keys = new ArrayList<>(holding.keySet());
        Collections.sort(keys);

        for (String req : keys) {
            res.add(req + "-" + holding.get(req));
        }
        return res;
    }

    // Returns the oldest requestId still waiting (not expired), else null.
    private String pollNextValidWaitingRequest() {
        while (!waitQueue.isEmpty()) {
            String req = waitQueue.pollFirst();
            if (waitingSet.remove(req)) {
                return req;
            }
            // else it was expired (or already removed), skip
        }
        return null;
    }
}
