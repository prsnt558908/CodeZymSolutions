# Modified LRU Cache for Dasher Delivery Assignments in Java

#### Problem Statement
[https://codezym.com/question/276-modified-lru-cache-dasher-delivery](https://codezym.com/question/276-modified-lru-cache-dasher-delivery)


## Core Idea

This problem is a small twist on the classic **LRU (least recently used) cache**.


We store the latest delivery of every dasher. When there are more dashers than `capacity`, we remove the dasher whose entry was **updated** the longest time ago.


The twist is that only `put` changes the order. `get` just reads a value and leaves the order alone.


So we need two things:

- a fast way to find a dasher by id: a **hash map**
- a way to keep dashers sorted by their last update: a **list**, oldest first and newest last


We will first build this with a map and a normal list, and see why the list makes `put` slow. Then we will replace it with a **doubly linked list**, so that both `put` and `get` run in O(1) time.


## How the Order Changes

Let's walk through Example 1 with `capacity = 2`. The order column lists dashers from the oldest update to the newest.

| Call | Order after the call | What happened |
|---|---|---|
| `put(11, 701)` | `11` | new dasher |
| `put(22, 702)` | `11, 22` | new dasher |
| `get(11)` returns `701` | `11, 22` | only a read, nothing moves |
| `put(33, 703)` | `22, 33` | now 3 dashers, so the oldest, `11`, is removed |
| `get(11)` returns `-1` | `22, 33` | `11` is gone |


In a normal LRU cache, `get(11)` would have made `11` the newest, and `22` would have been removed instead.


## Simple Version: Map + List

We keep two things:

- `deliveries`: a map from `dasherId` to `deliveryId`. It makes `get` a single lookup.
- `order`: a list of dasher ids, oldest update first and newest update last. It tells us which dasher to remove.

```java
public void put(int dasherId, int deliveryId) {
    if (deliveries.containsKey(dasherId)) {
        // remove by value, not by index. O(n): search, then shift
        order.remove(Integer.valueOf(dasherId));
    }
    deliveries.put(dasherId, deliveryId);
    order.add(dasherId); // the newest dasher goes to the end

    if (order.size() > capacity) {
        int oldest = order.remove(0); // O(n): every element shifts left
        deliveries.remove(oldest);
    }
}

public int get(int dasherId) {
    return deliveries.getOrDefault(dasherId, -1); // order is not touched
}
```

This gives the right answers, but `put` is slow:

- To update an existing dasher, we must **search** the list for it and pull it out. The search and the shifting of the elements after it are both O(n).
- To remove the oldest dasher, `order.remove(0)` shifts every other element one step left. That is O(n) too.


With `capacity` up to 100,000, one `put` can touch 100,000 elements. So 100,000 calls can take about 10 billion steps.


`get` is already O(1). To fix `put`, we need a list where we can **jump straight to a dasher** and **remove it in O(1)**.


## Final Solution: Map + Doubly Linked List

In a **doubly linked list**, every node points to the node before it (`prev`) and the node after it (`next`). If we already have a node, we can remove it by joining its two neighbors. No searching and no shifting.


The map gives us that node. Instead of `dasherId -> deliveryId`, it now stores `dasherId -> Node`.

```
head <-> [11 : 701] <-> [22 : 702] <-> tail
          oldest          newest
```

### The Important Pieces

- **`Node`**: one dasher entry with `dasherId`, `deliveryId`, `prev` and `next`. It keeps `dasherId` because when we remove the oldest node from the list, we need its id to remove it from the map too.
- **`nodes`**: the map from `dasherId` to its `Node`. It finds any dasher in O(1).
- **`head` and `tail`**: two dummy nodes that hold no data. Real nodes always sit between them, so we never need `null` checks while adding or removing. `head.next` is the oldest dasher and `tail.prev` is the newest.


### How Each Method Works

**`put` for a new dasher**

1. Create its node, add it to the map, and link it just before `tail`.
2. If the map now has more than `capacity` dashers, remove `head.next`, the oldest dasher, from both the list and the map.


**`put` for an existing dasher**

1. Save the new `deliveryId` in its node.
2. Unlink the node and link it again just before `tail`, so it becomes the newest.


The number of dashers stays the same, so nothing is removed.


Moving the node is what makes Example 2 work. After `put(40, 830)`, dasher `40` is newer than `50`, so `50` is removed when `60` arrives.


**`get`**

Look up the node and return its `deliveryId`, or `-1` if the dasher is not in the cache. The node is **not** moved. This is the only change from a standard LRU cache.


The two helpers in the code, `addToEnd` and `removeNode`, only change a few links, so they take O(1) time.


### Complexity

| | Simple Version | Final Solution |
|---|---|---|
| `put` | O(capacity) | O(1) |
| `get` | O(1) | O(1) |
| Space | O(capacity) | O(capacity) |

Hash map lookups are O(1) on average.


### Java Code

```java
import java.util.HashMap;
import java.util.Map;

public class DasherDeliveryCache {

    int capacity;

    // dasherId -> its node in the linked list, for O(1) lookup
    Map<Integer, Node> nodes;

    // Dummy nodes at both ends of the list.
    // head.next is the least recently updated dasher.
    // tail.prev is the most recently updated dasher.
    Node head;
    Node tail;

    public DasherDeliveryCache() {
    }

    public void init(int capacity) {
        this.capacity = capacity;
        nodes = new HashMap<>();
        head = new Node(0, 0);
        tail = new Node(0, 0);
        head.next = tail;
        tail.prev = head;
    }

    public void put(int dasherId, int deliveryId) {
        Node node = nodes.get(dasherId);

        if (node != null) {
            // Existing dasher: update the delivery, then make it the newest
            node.deliveryId = deliveryId;
            removeNode(node);
            addToEnd(node);
            return;
        }

        // New dasher: add it at the newest end
        node = new Node(dasherId, deliveryId);
        nodes.put(dasherId, node);
        addToEnd(node);

        // Too many dashers: remove the least recently updated one
        if (nodes.size() > capacity) {
            Node oldest = head.next;
            removeNode(oldest);
            nodes.remove(oldest.dasherId);
        }
    }

    public int get(int dasherId) {
        Node node = nodes.get(dasherId);
        if (node == null) {
            return -1;
        }
        // Only read the value. The update order does not change.
        return node.deliveryId;
    }

    // Links a node just before the dummy tail, the newest position
    void addToEnd(Node node) {
        node.prev = tail.prev;
        node.next = tail;
        tail.prev.next = node;
        tail.prev = node;
    }

    // Unlinks a node from its current position in the list
    void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
}

// One dasher entry in the doubly linked list
class Node {
    int dasherId;
    int deliveryId;
    Node prev;
    Node next;

    Node(int dasherId, int deliveryId) {
        this.dasherId = dasherId;
        this.deliveryId = deliveryId;
    }
}
```