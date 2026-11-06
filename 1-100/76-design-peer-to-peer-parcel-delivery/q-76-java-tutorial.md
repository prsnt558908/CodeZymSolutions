# Design Peer to Peer Parcel Delivery System in Java

#### Problem Statement
[https://codezym.com/question/76-design-peer-to-peer-parcel-delivery](https://codezym.com/question/76-design-peer-to-peer-parcel-delivery)

---

## Core Idea

Every order follows a fixed path: it is created, gets a driver, gets picked up and is finally delivered. It can also be canceled, but only before pickup.

So an order is just a small **state machine**. An enum plus one rule method handles it cleanly. Patterns like State or Strategy would only add extra classes here, so this solution does not need any design pattern.

The real work is matching orders with drivers. Free drivers sit in a **min-heap**, so the lexicographically smallest driver id is always on top.

Waiting orders sit in a **queue**, so the oldest order is always served first. Whenever a driver becomes free, it takes the next waiting order.

---

## Order Life Cycle

```mermaid
stateDiagram-v2
    [*] --> CREATED : createOrder
    CREATED --> ASSIGNED : a driver is free
    ASSIGNED --> PICKED_UP : pickUpOrder
    PICKED_UP --> DELIVERED : deliverOrder
    CREATED --> CANCELED : cancelOrder
    ASSIGNED --> CANCELED : cancelOrder
```

- `CREATED`: the order is waiting for a driver.
- `ASSIGNED`: a driver is reserved for the order but has not picked it up yet.
- `PICKED_UP`: the driver has the parcel. Canceling is not allowed anymore.
- `DELIVERED` and `CANCELED` are final states.

A driver is either `AVAILABLE` or `BUSY:<orderId>`. It stays busy from assignment until delivery, or until its assigned order is canceled.

---

## Approach 1: Brute Force (Scan Everything)

Whenever a new order arrives or a driver becomes free, we need two answers:

1. Which free driver has the smallest id?
2. Which waiting order is the oldest?

The simplest way is to scan everything. Here `allOrders` is a list of all orders, in creation order.

```java
// scan every driver and keep the smallest free id
String findFreeDriver() {
    String best = null;
    for (Driver driver : drivers.values()) {
        if (driver.isAvailable() && (best == null || driver.driverId.compareTo(best) < 0)) {
            best = driver.driverId;
        }
    }
    return best;
}

// scan all orders in creation order and return the first one still waiting
Order findOldestWaitingOrder() {
    for (Order order : allOrders) {
        if (order.status == OrderStatus.CREATED) {
            return order;
        }
    }
    return null;
}
```

Notice that we compare driver ids as **strings**. In lexicographic order `dv-10` comes before `dv-2`. A loop that goes `dv-0, dv-1, dv-2 ...` by number would pick the wrong driver.

**Problems with this approach**

- `findFreeDriver()` checks all `D` drivers every single time.
- `findOldestWaitingOrder()` checks all `N` orders ever created, even delivered and canceled ones.

Both become slow as the system grows. Let's fix them one at a time.

---

## Approach 2: Min-Heap + Queue (Optimal)

### Fix 1: Min-heap for free drivers

A min-heap (`PriorityQueue` in Java) always keeps the smallest element on top. Strings are compared lexicographically by default, which is exactly our rule.

- Take the smallest free driver with `poll()` in `O(log D)`.
- Put a driver back with `add()` in `O(log D)` when it becomes free.

We never need to remove a driver from the middle of the heap. A driver leaves the heap only when it gets an order, and orders always go to the driver on top.

### Fix 2: Queue for waiting orders

An order that can't get a driver right away goes to the back of a queue. Orders leave from the front, so the oldest one is always served first.

What if a waiting order is canceled? Searching the queue to remove it would be slow. So we only mark it `CANCELED` and leave it there.

When it reaches the front later, we see it is canceled and simply drop it. This trick is called **lazy removal**.

### When does matching happen?

Only at two moments:

1. **A new order arrives.** If a driver is free, assign it now and return `ACCEPTED`. Otherwise add the order to the queue and return `PENDING`.
2. **A driver becomes free.** This happens after a delivery, or when an `ASSIGNED` order is canceled. The driver goes back into the heap and takes the next waiting order.

Because we match at both moments, a free driver and a waiting order never exist together. So when a new order finds a free driver, the queue is already empty and oldest first is still respected.

### Other rules worth noticing

- `createOrder` returns `INVALID` for a duplicate order id, an unknown customer or an unknown item.
- `pickUpOrder` and `deliverOrder` only work for the order's own driver. A wrong or unknown driver id never matches, so one check covers both cases.
- The two status methods return `ERROR` for an unknown id.

---

## Do We Need Design Patterns?

**State pattern** looks like a natural fit because an order has states. But it needs one class per state. With 5 states and 3 actions (cancel, pick up, deliver) that is 15 small methods, and 11 of them just return `ERROR`.

Freeing a driver also needs the heap and the queue, which live in the main class. So the state classes would need a link back to the main system. One `canMoveTo()` method that lists the valid moves is much simpler.

**Strategy pattern** could make the driver choosing rule pluggable, like nearest driver or best rated driver. But this problem has exactly one fixed rule: the smallest driver id. An interface with a single implementation only adds code.

So we keep it simple: a few small classes, an enum for order states and the right data structures.

---

## Classes and Data Structures

| Class | Why we need it |
|---|---|
| `OrderStatus` | Enum of the 5 order states. Safer than typing raw strings. |
| `Order` | Holds order details, status and the assigned driver. `canMoveTo()` keeps every state rule in one place. |
| `Driver` | Remembers the one order the driver is carrying and builds the `AVAILABLE` or `BUSY:<orderId>` text. |
| `PeerToPeerDeliverySystem` | Main class. Validates requests and matches orders with drivers. |

| Data structure | Why we need it |
|---|---|
| `Set<String> customerIds`, `Set<String> deliverableItemIds` | Validate a customer or item in `O(1)` without parsing ids. |
| `Map<String, Driver> drivers`, `Map<String, Order> orders` | Find any driver or order by id in `O(1)`. |
| `PriorityQueue<String> availableDrivers` | Gives the smallest free driver id in `O(log D)`. |
| `Queue<Order> pendingOrders` | Keeps waiting orders, oldest first. |

---

## Final Code

```java
import java.util.*;

/** All the states an order can be in. */
enum OrderStatus {
    CREATED, ASSIGNED, PICKED_UP, DELIVERED, CANCELED
}

/** One parcel delivery request placed by a customer. */
class Order {
    String orderId;
    String customerId;
    String itemId;
    OrderStatus status;
    String driverId; // null until a driver is assigned

    Order(String orderId, String customerId, String itemId) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.itemId = itemId;
        this.status = OrderStatus.CREATED;
        this.driverId = null;
    }

    /**
     * The order state machine. Valid moves are:
     * CREATED -> ASSIGNED -> PICKED_UP -> DELIVERED
     * CREATED or ASSIGNED -> CANCELED
     */
    boolean canMoveTo(OrderStatus nextStatus) {
        if (status == OrderStatus.CREATED) {
            return nextStatus == OrderStatus.ASSIGNED || nextStatus == OrderStatus.CANCELED;
        }
        if (status == OrderStatus.ASSIGNED) {
            return nextStatus == OrderStatus.PICKED_UP || nextStatus == OrderStatus.CANCELED;
        }
        if (status == OrderStatus.PICKED_UP) {
            return nextStatus == OrderStatus.DELIVERED;
        }
        return false; // no moves out of DELIVERED or CANCELED
    }
}

/** A driver carries at most one order at a time. */
class Driver {
    String driverId;
    String currentOrderId; // null means the driver is free

    Driver(String driverId) {
        this.driverId = driverId;
        this.currentOrderId = null;
    }

    boolean isAvailable() {
        return currentOrderId == null;
    }

    String getStatus() {
        return isAvailable() ? "AVAILABLE" : "BUSY:" + currentOrderId;
    }
}

public class PeerToPeerDeliverySystem {
    Set<String> customerIds = new HashSet<>();
    Set<String> deliverableItemIds = new HashSet<>();
    Map<String, Driver> drivers = new HashMap<>();
    Map<String, Order> orders = new HashMap<>();

    // free drivers, the lexicographically smallest id is always on top
    PriorityQueue<String> availableDrivers = new PriorityQueue<>();

    // orders waiting for a driver, oldest first
    // a canceled order may still sit here, it is dropped when it reaches the front
    Queue<Order> pendingOrders = new ArrayDeque<>();

    public PeerToPeerDeliverySystem(int totalUsers, int totalDrivers, List<String> deliverableItemIds) {
        for (int i = 0; i < totalUsers; i++) {
            customerIds.add("user-" + i);
        }
        for (int i = 0; i < totalDrivers; i++) {
            String driverId = "dv-" + i;
            drivers.put(driverId, new Driver(driverId));
            availableDrivers.add(driverId);
        }
        this.deliverableItemIds.addAll(deliverableItemIds);
    }

    public String createOrder(String orderId, String customerId, String itemId) {
        if (orders.containsKey(orderId) || !customerIds.contains(customerId)
                || !deliverableItemIds.contains(itemId)) {
            return "INVALID";
        }
        Order order = new Order(orderId, customerId, itemId);
        orders.put(orderId, order);

        if (availableDrivers.isEmpty()) {
            pendingOrders.add(order);
            return "PENDING";
        }
        assignOrder(order, availableDrivers.poll());
        return "ACCEPTED";
    }

    public String cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null || !order.canMoveTo(OrderStatus.CANCELED)) {
            return "ERROR";
        }
        order.status = OrderStatus.CANCELED;

        // ASSIGNED order: its driver was waiting, so set the driver free.
        // CREATED order: it stays in pendingOrders and is dropped later.
        if (order.driverId != null) {
            freeDriver(order.driverId);
        }
        return "CANCELED";
    }

    public String pickUpOrder(String driverId, String orderId) {
        Order order = orders.get(orderId);
        // a wrong or unknown driverId never matches order.driverId
        if (order == null || !order.canMoveTo(OrderStatus.PICKED_UP)
                || !order.driverId.equals(driverId)) {
            return "ERROR";
        }
        order.status = OrderStatus.PICKED_UP;
        return "PICKED_UP";
    }

    public String deliverOrder(String driverId, String orderId) {
        Order order = orders.get(orderId);
        if (order == null || !order.canMoveTo(OrderStatus.DELIVERED)
                || !order.driverId.equals(driverId)) {
            return "ERROR";
        }
        order.status = OrderStatus.DELIVERED;
        freeDriver(driverId);
        return "DELIVERED";
    }

    public String getOrderStatus(String orderId) {
        Order order = orders.get(orderId);
        return order == null ? "ERROR" : order.status.name();
    }

    public String getDriverStatus(String driverId) {
        Driver driver = drivers.get(driverId);
        return driver == null ? "ERROR" : driver.getStatus();
    }

    /** Links an order with a driver. */
    void assignOrder(Order order, String driverId) {
        order.status = OrderStatus.ASSIGNED;
        order.driverId = driverId;
        drivers.get(driverId).currentOrderId = order.orderId;
    }

    /** Marks a driver as free and hands out waiting orders. */
    void freeDriver(String driverId) {
        drivers.get(driverId).currentOrderId = null;
        availableDrivers.add(driverId);
        assignPendingOrders();
    }

    /** Gives waiting orders to free drivers, oldest order first. */
    void assignPendingOrders() {
        while (!availableDrivers.isEmpty() && !pendingOrders.isEmpty()) {
            Order order = pendingOrders.poll();
            // lazy removal: a canceled order is simply dropped
            if (order.status == OrderStatus.CREATED) {
                assignOrder(order, availableDrivers.poll());
            }
        }
    }
}
```

---

## Complexity

| Method | Time |
|---|---|
| `createOrder` | `O(log D)` |
| `cancelOrder` | `O(log D)` |
| `pickUpOrder` | `O(1)` |
| `deliverOrder` | `O(log D)` |
| `getOrderStatus`, `getDriverStatus` | `O(1)` |

`D` is the number of drivers. Dropping canceled orders from the queue adds a little extra work, but each order is dropped at most once in total. So on average `cancelOrder` and `deliverOrder` stay `O(log D)`.

Space is `O(U + D + I + N)` for users, drivers, items and orders.