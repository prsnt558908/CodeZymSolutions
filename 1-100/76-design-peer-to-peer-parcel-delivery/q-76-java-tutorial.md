# Design Peer to Peer Parcel Delivery System in Java

#### Problem Statement

[https://codezym.com/question/76-design-peer-to-peer-parcel-delivery](https://codezym.com/question/76-design-peer-to-peer-parcel-delivery)

Keep two things in order: waiting parcels and free drivers. A queue gives us the oldest waiting order, and a min-heap gives us the smallest free driver ID. Maps make status lookups quick. A few state checks handle pickup, cancellation, and delivery, so a separate design pattern is unnecessary for these fixed rules.


## What the system needs to remember

An assigned driver becomes busy immediately, even before pickup. The driver becomes free after delivery or cancellation before pickup.

Orders follow these transitions.

```mermaid
stateDiagram-v2
    CREATED --> ASSIGNED: Driver available
    ASSIGNED --> PICKED_UP: Pickup
    PICKED_UP --> DELIVERED: Delivery
    CREATED --> CANCELED: Cancel
    ASSIGNED --> CANCELED: Cancel
```

`ACCEPTED` and `PENDING` are responses from `createOrder`, not stored order states.

| Creation result | Returned value | Stored order state |
| --- | --- | --- |
| A driver is assigned | `ACCEPTED` | `ASSIGNED` |
| All drivers are busy | `PENDING` | `CREATED` |
| The request is invalid | `INVALID` | No new order |

The statement leaves duplicate order IDs and unknown status lookups unspecified. This solution rejects reused order IDs or `null` with `INVALID` and returns `ERROR` for unknown order or driver status lookups. Reuse is rejected even after cancellation or delivery.


## 1. Start with a simple scan

Keep orders in creation order. Whenever assignment is needed, scan for the first `CREATED` order, then scan all drivers to find the smallest available ID.

For example, a map can store each driver's current order. `null` means available.

```java
String findAvailableDriver() {
    String best = null;
    for (String driverId : driverOrders.keySet()) {
        if (driverOrders.get(driverId) == null
                && (best == null || driverId.compareTo(best) < 0)) {
            best = driverId;
        }
    }
    return best;
}
```

This works, but one assignment can scan all orders and drivers, costing `O(N + D)` for `N` stored orders and `D` drivers. Repeating these scans does unnecessary work.


## 2. Use a queue and a min-heap

| Data | Structure | Why we need it |
| --- | --- | --- |
| `Order` | Small class | Groups the parcel details, status, and assigned driver |
| `customers`, `deliverableItems` | Sets | Check exact customer and item IDs quickly |
| `orders` | ID to `Order` map | Find an order without scanning |
| `driverOrders` | Driver ID to order ID map | Track busy drivers and recognize valid driver IDs |
| `pendingOrders` | `ArrayDeque` queue | Preserve creation order |
| `availableDrivers` | `PriorityQueue<String>` | Choose the smallest free driver ID |

`PriorityQueue` puts the smallest string at its head. `poll()` removes it and `offer()` adds a driver back. We never need to sort the full driver list again.

Compare complete driver IDs as strings. With twelve drivers, the order begins `dv-0`, `dv-1`, `dv-10`, `dv-11`, `dv-2`. Comparing only the number would select the wrong driver.

The State pattern could give every status its own class, but these transitions need only short checks. Strategy would help if driver selection rules were interchangeable. Here, the rule is fixed, so neither adds useful simplicity.


## 3. Keep assignment in one helper

`assignPendingOrders` repeatedly takes the oldest waiting order and the smallest free driver. It records the assignment in both the order and `driverOrders`.

Call it after creating an order and whenever a driver is released. This makes automatic assignment happen before the public method returns.

Canceling a waiting order only changes its status. Its queue entry stays until the helper reaches it, then the helper skips it. Each entry is removed once, so cancellation does not need a search through the queue.

Canceling an assigned order changes its status first, then releases its driver. Delivery follows the same release path. Pickup and delivery check both the current status and the assigned driver. Failed requests leave the system unchanged.

An order keeps its last assigned driver ID after cancellation or delivery. The status prevents any further action on that order. `driverOrders` records the driver's current job.


## Walk through a busy driver

Assume one driver, `dv-0`, and a valid customer and item.

| Action | Return | What changes |
| --- | --- | --- |
| Create `A` | `ACCEPTED` | `dv-0` is assigned to `A` |
| Create `B` | `PENDING` | `B` waits |
| Create `C` | `PENDING` | `C` waits after `B` |
| Cancel `B` | `CANCELED` | Its queue entry remains |
| Cancel `A` | `CANCELED` | The helper skips `B` and assigns `C` to `dv-0` |
| Pick up `C` with `dv-0` | `PICKED_UP` | `C` can no longer be canceled |
| Cancel `C` | `ERROR` | Nothing changes |
| Deliver `C` with `dv-0` | `DELIVERED` | `dv-0` becomes available |


## Why this works

The queue preserves creation order, and skipped canceled entries cannot change the order of the remaining parcels. The heap contains each free driver exactly once, so every assignment selects the correct driver and removes that driver from availability.

Only valid transitions release a driver. Repeated cancellation or delivery fails before another release. After assignment finishes, a free driver and a waiting non-canceled order cannot remain together.


## Complexity

Let `U` be the number of customers, `I` the number of configured items, `D` the number of drivers, and `N` the number of valid orders created. These costs assume bounded ID lengths and average constant-time hash lookups.

| Operation | Time |
| --- | --- |
| Initialization, including heap construction | `O(U + I + D)` |
| Pickup and status lookup | `O(1)` |
| Cancel a waiting order | `O(1)` |
| Create, deliver, or cancel an assigned order | `O(log(D + 1))` amortized |

One call can additionally skip `K` canceled queue entries, taking `O(K + log(D + 1))`. Each entry is skipped only once, so that extra work totals `O(N)` across the system's lifetime.

Space is `O(U + I + D + N)`. Old orders remain stored so their status can still be queried.


## Complete Java code

Save this as `PeerToPeerDeliverySystem.java`. Both classes are top-level classes.

```java
import java.util.*;

class Order {
    String orderId;
    String customerId;
    String itemId;
    String status = "CREATED";
    String driverId;

    Order(String orderId, String customerId, String itemId) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.itemId = itemId;
    }
}

public class PeerToPeerDeliverySystem {
    Set<String> customers = new HashSet<>();
    Set<String> deliverableItems;
    Map<String, Order> orders = new HashMap<>();

    // A null value means the driver is available.
    Map<String, String> driverOrders = new HashMap<>();
    Queue<String> pendingOrders = new ArrayDeque<>();
    PriorityQueue<String> availableDrivers;

    public PeerToPeerDeliverySystem(
            int totalUsers,
            int totalDrivers,
            List<String> deliverableItemIds) {
        deliverableItems = new HashSet<>(deliverableItemIds);

        for (int i = 0; i < totalUsers; i++) {
            customers.add("user-" + i);
        }

        for (int i = 0; i < totalDrivers; i++) {
            driverOrders.put("dv-" + i, null);
        }

        // String ordering puts dv-10 before dv-2.
        availableDrivers = new PriorityQueue<>(driverOrders.keySet());
    }

    public String createOrder(String orderId, String customerId, String itemId) {
        if (orderId == null || orders.containsKey(orderId)
                || !customers.contains(customerId)
                || !deliverableItems.contains(itemId)) {
            return "INVALID";
        }

        Order order = new Order(orderId, customerId, itemId);
        orders.put(orderId, order);
        pendingOrders.offer(orderId);
        assignPendingOrders();

        return order.status.equals("ASSIGNED") ? "ACCEPTED" : "PENDING";
    }

    public String cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null || (!order.status.equals("CREATED")
                && !order.status.equals("ASSIGNED"))) {
            return "ERROR";
        }

        // Mark it first so assignment can never reuse a canceled order.
        order.status = "CANCELED";
        if (order.driverId != null) {
            releaseDriver(order.driverId);
        }

        return "CANCELED";
    }

    public String pickUpOrder(String driverId, String orderId) {
        Order order = orders.get(orderId);
        if (order == null || !driverOrders.containsKey(driverId)
                || !order.status.equals("ASSIGNED")
                || !driverId.equals(order.driverId)) {
            return "ERROR";
        }

        order.status = "PICKED_UP";
        return "PICKED_UP";
    }

    public String deliverOrder(String driverId, String orderId) {
        Order order = orders.get(orderId);
        if (order == null || !driverOrders.containsKey(driverId)
                || !order.status.equals("PICKED_UP")
                || !driverId.equals(order.driverId)) {
            return "ERROR";
        }

        order.status = "DELIVERED";
        releaseDriver(driverId);
        return "DELIVERED";
    }

    public String getOrderStatus(String orderId) {
        Order order = orders.get(orderId);
        return order == null ? "ERROR" : order.status;
    }

    public String getDriverStatus(String driverId) {
        if (!driverOrders.containsKey(driverId)) {
            return "ERROR";
        }

        String orderId = driverOrders.get(driverId);
        return orderId == null ? "AVAILABLE" : "BUSY:" + orderId;
    }

    void assignPendingOrders() {
        while (!pendingOrders.isEmpty() && !availableDrivers.isEmpty()) {
            Order order = orders.get(pendingOrders.poll());

            // Canceled waiting orders stay in the queue until reached.
            if (!order.status.equals("CREATED")) {
                continue;
            }

            String driverId = availableDrivers.poll();
            order.driverId = driverId;
            order.status = "ASSIGNED";
            driverOrders.put(driverId, order.orderId);
        }
    }

    void releaseDriver(String driverId) {
        driverOrders.put(driverId, null);
        availableDrivers.offer(driverId);
        assignPendingOrders();
    }
}
```
