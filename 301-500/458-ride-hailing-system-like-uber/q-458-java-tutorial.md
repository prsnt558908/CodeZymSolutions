# Design Ride-Hailing System Like Uber, Ola in Java

#### Problem Statement
[https://codezym.com/question/458-ride-hailing-system-like-uber](https://codezym.com/question/458-ride-hailing-system-like-uber)


## Core Idea

We model the system with three small classes, `Rider`, `Driver` and `Trip`, each kept in a `HashMap` by id so every lookup is instant. A simple enum guards the fixed trip states. The only hard part is finding the nearest free driver quickly, so we put that job behind a small `DriverMatcher` interface. This is the **Strategy pattern**, and it is the right fit here: we start with a simple matcher that checks every driver, then swap in a fast quadtree matcher by changing just one line. State and Observer also sound useful, but for these fixed rules they would only add extra classes.

For the fast matcher, we compare two common spatial indexes: a grid and a quadtree. We code the quadtree, because it adapts on its own to crowded city centers and empty outskirts, which is exactly how real cities look.

Payments are made safe to retry by remembering the answer we gave for every payment request id.


## Design Patterns

### Strategy Pattern for Driver Matching

Matching is the part of a ride app that keeps changing. Today the rule is "nearest driver wins". Tomorrow it might be "nearest driver with a good rating".

So `RideHailingSystem` only talks to this interface:

```java
interface DriverMatcher {
    void addDriver(Driver driver);            // driver can now get requests
    void removeDriver(Driver driver);         // driver can no longer get requests
    Driver findNearestDriver(int x, int y);   // null when no driver is available
}
```

We write two strategies in this article. `LinearDriverMatcher` is simple but slow. `QuadTreeDriverMatcher` is fast. A grid-based matcher would plug in the same way. Going from Solution 1 to Solution 2 changes one line in the main class.

We use Strategy only where we really have more than one way to do a job. Surge pricing has one fixed rule, so a plain method is clearer there.


### Why Not the State Pattern?

The State pattern would create one class per trip state, and each class would decide which moves are allowed.

Here every move is allowed from exactly one state, and cancel is allowed from any active state. Each rule is a single `if`. Five state classes would spread these tiny rules over many places. An enum keeps all of them in plain sight.


### Why Not the Observer Pattern?

We could publish events like "driver moved" or "trip ended" and let listeners update the matcher. But there is just one listener, and it lives inside the same system. Calling the matcher directly is shorter and easier to follow.


## Building Blocks

### Driver

Keeps the location, an `available` flag and `activeTripId`.

- `available` says whether the driver can get a new request.
- `activeTripId` says whether the driver is on a trip. `setDriverAvailability` must fail while it is set.

It also has `isBetterThan()`, the matching rule in one place: the closer driver wins, and on a tie the smaller id wins.

Distances are `long`. A squared distance can reach 8 × 10¹², which does not fit in an `int`.


### Rider

Keeps `activeTripId`. A rider who already has an active trip cannot request another one.


### Trip

Keeps the booking details, the state, the fare and the payment details.

It also copies the zone's surge percentage at request time. Later surge updates must not change this trip's fare, so we never look the zone up again for this trip.

`paymentResults` remembers the first answer given for each payment request id.


### Maps in RideHailingSystem

| Map | Why we need it |
| --- | --- |
| `drivers`, `riders`, `trips` | O(1) lookup by id |
| `surgeByZone` | Current surge of each zone. A missing zone means 100 |
| `paymentRequestOwner` | Which trip used each payment request id, so no other trip can reuse it |


## How the Main Operations Work

### Trip lifecycle

`REQUESTED → ACCEPTED → IN_PROGRESS → COMPLETED`, and any of the first three can move to `CANCELLED`.

`acceptTrip`, `startTrip` and `completeTrip` each check two things: the trip is in the one allowed state, and the caller is the assigned driver. If a check fails, nothing changes.

`requestTrip` reserves the chosen driver. It removes the driver from the matcher, marks it unavailable and links the rider and the driver to the trip.

When a trip ends, `releasePeople()` does the reverse. It clears both links, marks the driver available and gives it back to the matcher. On completion, we first move the driver to the destination.


### Fares

`subtotal = baseFare + distance × farePerUnit`

`fare = ceil(subtotal × surge / 100)`

Adding 99 before dividing by 100 rounds up, using plain integer math with no floating point. For example, `(601 + 99) / 100 = 7`, while `(600 + 99) / 100 = 6`.

`estimateFare` uses the zone's current surge. `completeTrip` uses the surge saved inside the trip.


### Payments that are safe to retry

Think of a payment request id as a receipt number. If the app sends the same request twice, for example after a network glitch, we must not create a second attempt.

`processPayment` checks things in this order:

1. The trip is missing or not `COMPLETED`: return `INVALID_REQUEST`.
2. The id was already used by another trip: return `INVALID_REQUEST`.
3. The id was already used by this trip: return the old answer. The new `gatewaySuccessful` value is ignored.
4. The payment is already `SUCCEEDED` or `FAILED`: return that status, with no new attempt.
5. Otherwise, count one attempt. Success gives `SUCCEEDED`. A failure gives `RETRY_PENDING` while attempts remain, and `FAILED` on the last allowed attempt.

After steps 4 and 5, we save the id together with its answer.

Step 3 runs before step 4 on purpose. If an old failed request id is sent again, it still returns `RETRY_PENDING`, even when a later request has already succeeded.


## Solution 1: Check Every Available Driver

`LinearDriverMatcher` keeps every available driver in a `HashSet`. To find the nearest one, it checks them all.

Busy and offline drivers are never in the set. A driver leaves the set when it gets a trip or goes offline, and comes back when the trip ends or it comes online again.

When an available driver moves, we remove it, update its location and add it back. For a set this changes nothing, but in Solution 2 the driver's place in the quadtree depends on its location, so this order matters there.

```java
import java.util.*;

/** States of a trip. COMPLETED and CANCELLED are terminal. */
enum TripStatus {
    REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED
}

/** Payment states of a trip. */
enum PaymentStatus {
    NOT_STARTED, PENDING, RETRY_PENDING, SUCCEEDED, FAILED
}

class Rider {
    String id;
    String name;
    String activeTripId;   // null when the rider has no running trip

    Rider(String id, String name) {
        this.id = id;
        this.name = name;
    }
}

class Driver {
    String id;
    String name;
    int x;
    int y;
    boolean available = true;   // can this driver get a new trip request?
    String activeTripId;        // null when the driver is not on a trip

    Driver(String id, String name, int x, int y) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
    }

    /** Squared distance to a point. We use long so the result never overflows. */
    long distanceTo(int pointX, int pointY) {
        long dx = (long) x - pointX;
        long dy = (long) y - pointY;
        return dx * dx + dy * dy;
    }

    /** True if this driver is a better pick than other: closer, or same distance and smaller id. */
    boolean isBetterThan(Driver other, int pickupX, int pickupY) {
        if (other == null) {
            return true;
        }
        long myDistance = distanceTo(pickupX, pickupY);
        long otherDistance = other.distanceTo(pickupX, pickupY);
        if (myDistance != otherDistance) {
            return myDistance < otherDistance;
        }
        return id.compareTo(other.id) < 0;
    }
}

class Trip {
    String id;
    String riderId;
    String driverId;
    int pickupX;
    int pickupY;
    int destinationX;
    int destinationY;
    String zoneId;
    String paymentMethodId;
    int surgePercentage;   // copied from the zone when the trip is created

    TripStatus status = TripStatus.REQUESTED;
    long fareInCents = 0;
    PaymentStatus paymentStatus = PaymentStatus.NOT_STARTED;
    int paymentAttempts = 0;

    // paymentRequestId -> the answer we gave the first time we saw it
    Map<String, String> paymentResults = new HashMap<>();

    Trip(String id, String riderId, String driverId, int pickupX, int pickupY,
         int destinationX, int destinationY, String zoneId, String paymentMethodId, int surgePercentage) {
        this.id = id;
        this.riderId = riderId;
        this.driverId = driverId;
        this.pickupX = pickupX;
        this.pickupY = pickupY;
        this.destinationX = destinationX;
        this.destinationY = destinationY;
        this.zoneId = zoneId;
        this.paymentMethodId = paymentMethodId;
        this.surgePercentage = surgePercentage;
    }

    /** A trip is active until it is COMPLETED or CANCELLED. */
    boolean isActive() {
        return status == TripStatus.REQUESTED
                || status == TripStatus.ACCEPTED
                || status == TripStatus.IN_PROGRESS;
    }

    String details() {
        return id + "," + riderId + "," + driverId + "," + status + ","
                + pickupX + "," + pickupY + "," + destinationX + "," + destinationY + ","
                + zoneId + "," + surgePercentage + "," + fareInCents + ","
                + paymentStatus + "," + paymentAttempts;
    }
}

/**
 * Strategy for finding the nearest available driver.
 * The system tells it when a driver becomes free or busy,
 * and asks it for the best driver for a pickup point.
 */
interface DriverMatcher {
    void addDriver(Driver driver);            // driver can now get requests

    void removeDriver(Driver driver);         // driver can no longer get requests

    Driver findNearestDriver(int x, int y);   // null when no driver is available
}

/** Simple strategy: keep all available drivers in a set and check every one of them. */
class LinearDriverMatcher implements DriverMatcher {
    Set<Driver> availableDrivers = new HashSet<>();

    public void addDriver(Driver driver) {
        availableDrivers.add(driver);
    }

    public void removeDriver(Driver driver) {
        availableDrivers.remove(driver);
    }

    public Driver findNearestDriver(int x, int y) {
        Driver best = null;
        for (Driver driver : availableDrivers) {
            if (driver.isBetterThan(best, x, y)) {
                best = driver;
            }
        }
        return best;
    }
}

public class RideHailingSystem {
    long baseFareInCents;
    long farePerDistanceUnitInCents;
    int maxPaymentAttempts;

    Map<String, Driver> drivers = new HashMap<>();
    Map<String, Rider> riders = new HashMap<>();
    Map<String, Trip> trips = new HashMap<>();
    Map<String, Integer> surgeByZone = new HashMap<>();          // zoneId -> surge percentage
    Map<String, String> paymentRequestOwner = new HashMap<>();   // paymentRequestId -> tripId

    DriverMatcher matcher = new LinearDriverMatcher();

    public RideHailingSystem(long baseFareInCents, long farePerDistanceUnitInCents, int maxPaymentAttempts, List<String> drivers) {
        this.baseFareInCents = baseFareInCents;
        this.farePerDistanceUnitInCents = farePerDistanceUnitInCents;
        this.maxPaymentAttempts = maxPaymentAttempts;

        // Each entry looks like "driverId,driverName,locationX,locationY"
        for (String entry : drivers) {
            String[] parts = entry.split(",");
            Driver driver = new Driver(parts[0], parts[1],
                    Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
            this.drivers.put(driver.id, driver);
            matcher.addDriver(driver);   // every driver starts available
        }
    }

    public boolean addRider(String riderId, String riderName) {
        if (riders.containsKey(riderId)) {
            return false;
        }
        riders.put(riderId, new Rider(riderId, riderName));
        return true;
    }

    public boolean updateDriverLocation(String driverId, int locationX, int locationY) {
        Driver driver = drivers.get(driverId);
        if (driver == null) {
            return false;
        }
        // An available driver is stored in the matcher at the old spot.
        // Take it out, move it, then put it back at the new spot.
        if (driver.available) {
            matcher.removeDriver(driver);
        }
        driver.x = locationX;
        driver.y = locationY;
        if (driver.available) {
            matcher.addDriver(driver);
        }
        return true;
    }

    public boolean setDriverAvailability(String driverId, boolean available) {
        Driver driver = drivers.get(driverId);
        if (driver == null || driver.activeTripId != null) {
            return false;
        }
        if (driver.available != available) {
            driver.available = available;
            if (available) {
                matcher.addDriver(driver);
            } else {
                matcher.removeDriver(driver);
            }
        }
        return true;
    }

    public int updateSurgePricing(String zoneId, int activeRequests, int availableDrivers) {
        int surge;
        if (activeRequests == 0) {
            surge = 100;
        } else if (availableDrivers == 0) {
            surge = 300;
        } else if (activeRequests <= availableDrivers) {
            surge = 100;
        } else if (activeRequests <= 2 * availableDrivers) {
            surge = 150;
        } else {
            surge = 200;
        }
        surgeByZone.put(zoneId, surge);
        return surge;
    }

    public long estimateFare(String zoneId, int estimatedDistanceUnits) {
        return calculateFare(estimatedDistanceUnits, currentSurge(zoneId));
    }

    public String requestTrip(String tripId, String riderId, int pickupX, int pickupY, int destinationX, int destinationY, String zoneId, String paymentMethodId) {
        Rider rider = riders.get(riderId);
        if (trips.containsKey(tripId) || rider == null || rider.activeTripId != null) {
            return "";
        }
        Driver driver = matcher.findNearestDriver(pickupX, pickupY);
        if (driver == null) {
            return "";   // no driver is available
        }

        // The trip keeps the zone's surge from this moment. Later changes do not affect it.
        Trip trip = new Trip(tripId, riderId, driver.id, pickupX, pickupY,
                destinationX, destinationY, zoneId, paymentMethodId, currentSurge(zoneId));
        trips.put(tripId, trip);

        // Reserve the driver and link both people to this trip
        matcher.removeDriver(driver);
        driver.available = false;
        driver.activeTripId = tripId;
        rider.activeTripId = tripId;
        return driver.id;
    }

    public boolean acceptTrip(String tripId, String driverId) {
        Trip trip = trips.get(tripId);
        if (trip == null || trip.status != TripStatus.REQUESTED || !trip.driverId.equals(driverId)) {
            return false;
        }
        trip.status = TripStatus.ACCEPTED;
        return true;
    }

    public boolean startTrip(String tripId, String driverId) {
        Trip trip = trips.get(tripId);
        if (trip == null || trip.status != TripStatus.ACCEPTED || !trip.driverId.equals(driverId)) {
            return false;
        }
        trip.status = TripStatus.IN_PROGRESS;
        return true;
    }

    public long completeTrip(String tripId, String driverId, int actualDistanceUnits) {
        Trip trip = trips.get(tripId);
        if (trip == null || trip.status != TripStatus.IN_PROGRESS || !trip.driverId.equals(driverId)) {
            return -1;
        }
        trip.status = TripStatus.COMPLETED;
        trip.fareInCents = calculateFare(actualDistanceUnits, trip.surgePercentage);
        trip.paymentStatus = PaymentStatus.PENDING;

        // The driver ends up at the drop point
        Driver driver = drivers.get(driverId);
        driver.x = trip.destinationX;
        driver.y = trip.destinationY;
        releasePeople(trip);
        return trip.fareInCents;
    }

    public boolean cancelTrip(String tripId, String requesterId) {
        Trip trip = trips.get(tripId);
        if (trip == null || !trip.isActive()) {
            return false;
        }
        if (!requesterId.equals(trip.riderId) && !requesterId.equals(trip.driverId)) {
            return false;
        }
        trip.status = TripStatus.CANCELLED;   // fare stays 0 and payment stays NOT_STARTED
        releasePeople(trip);
        return true;
    }

    public String processPayment(String tripId, String paymentRequestId, boolean gatewaySuccessful) {
        Trip trip = trips.get(tripId);
        if (trip == null || trip.status != TripStatus.COMPLETED) {
            return "INVALID_REQUEST";
        }

        String ownerTripId = paymentRequestOwner.get(paymentRequestId);
        if (ownerTripId != null) {
            // Seen before: same trip gets the old answer, any other trip is rejected
            return ownerTripId.equals(tripId) ? trip.paymentResults.get(paymentRequestId) : "INVALID_REQUEST";
        }

        String result;
        if (trip.paymentStatus == PaymentStatus.SUCCEEDED || trip.paymentStatus == PaymentStatus.FAILED) {
            result = trip.paymentStatus.name();   // payment is already settled, no new attempt
        } else {
            trip.paymentAttempts++;
            if (gatewaySuccessful) {
                trip.paymentStatus = PaymentStatus.SUCCEEDED;
            } else if (trip.paymentAttempts < maxPaymentAttempts) {
                trip.paymentStatus = PaymentStatus.RETRY_PENDING;
            } else {
                trip.paymentStatus = PaymentStatus.FAILED;
            }
            result = trip.paymentStatus.name();
        }

        // Remember this request id so a retry gets exactly the same answer
        paymentRequestOwner.put(paymentRequestId, tripId);
        trip.paymentResults.put(paymentRequestId, result);
        return result;
    }

    public String getTripDetails(String tripId) {
        Trip trip = trips.get(tripId);
        return trip == null ? "" : trip.details();
    }

    /** Zones that were never updated use 100 percent. */
    int currentSurge(String zoneId) {
        return surgeByZone.getOrDefault(zoneId, 100);
    }

    /** ceil(subtotal * surge / 100). Adding 99 before dividing rounds up. */
    long calculateFare(int distanceUnits, int surgePercentage) {
        long subtotal = baseFareInCents + distanceUnits * farePerDistanceUnitInCents;
        return (subtotal * surgePercentage + 99) / 100;
    }

    /** Called when a trip ends. Frees the rider, and the driver can take new requests again. */
    void releasePeople(Trip trip) {
        riders.get(trip.riderId).activeTripId = null;
        Driver driver = drivers.get(trip.driverId);
        driver.activeTripId = null;
        driver.available = true;
        matcher.addDriver(driver);
    }
}
```


### The Problem With Solution 1

Every `requestTrip` looks at every available driver. That is O(D) work for each request.

With 100,000 drivers and tens of thousands of requests, that is billions of distance checks. Yet a rider never needs a driver from the far side of the map. We only need to look nearby.


## Solution 2: Spatial Index With a Quadtree

A spatial index groups drivers by where they are, so a request only looks near the pickup. Two common choices are a grid and a quadtree.


### Option 1: Grid

Cut the map into equal square cells, like a chessboard. A `HashMap` maps each cell to the drivers inside it, and a driver's cell is found with one division.

To search, check the pickup's cell, then the ring of 8 cells around it, then the next ring of 16, and so on, until no unseen cell can hold a closer driver.

```
2 2 2 2 2
2 1 1 1 2
2 1 0 1 2
2 1 1 1 2
2 2 2 2 2
```

**Pros**

- Very simple: one map from cell to drivers.
- Adding, removing or moving a driver is O(1).
- Very fast when drivers are spread evenly and the cell size fits.

**Cons**

- One cell size for the whole map. A busy downtown packs hundreds of drivers into one cell, while empty outskirts make the search walk through ring after ring of empty cells.
- The right cell size is a guess. Drivers keep moving, so a size that fits in the morning can be wrong by evening.


### Option 2: Quadtree

Start with one big square that covers the whole map. When a square holds more than 10 drivers, split it into 4 equal smaller squares. Busy areas end up with many small squares, and quiet areas stay as one big square.

```
+-------+-------+
|       | . |. .|
|   .   +---+---+
|       |. .|. .|
+-------+---+---+
|       |       |
|   .   |   .   |
|       |       |
+-------+-------+
```

Here each dot stands for a few drivers. The busy top-right quarter has split into 4 smaller squares, while the quiet quarters stay whole.

**Pros**

- Adapts by itself: small squares where drivers are crowded, big squares where they are few.
- Nothing to tune except the leaf size, and any small number like 10 works.
- One check can skip a whole square, however big: when it has no drivers, or when it is too far away.

**Cons**

- A little more code: a node class, splitting, and a search that calls itself (recursion).
- Adding, removing or moving a driver walks down the tree, so it costs O(depth) instead of O(1). Here the depth is at most 21, so it stays cheap.


### Why We Pick the Quadtree

Real cities are uneven. The center is crowded, the outskirts are thin, and drivers move between them all day. A grid has to pick one cell size for all of that. A quadtree reshapes itself as drivers move, so it stays fast without any tuning.

| | Grid | Quadtree |
| --- | --- | --- |
| Shape | One layer of equal cells | Squares that split when crowded |
| Add, remove, move a driver | O(1) | O(depth), at most 21 steps |
| Drivers spread evenly | Fast | Fast |
| Crowded center, empty outskirts | Slow | Fast |
| Tuning | Needs a good cell size | Just a leaf size, like 10 |


### How the Quadtree Works

**Nodes.** Each node is a square. A leaf keeps up to 10 drivers in a plain list. Every node also keeps `count`, the number of drivers anywhere inside its square.

**The root square.** Coordinates go from -1,000,000 to 1,000,000. The root square starts at -1,048,576 and has side 2,097,152, which is 2²¹. A power of two can be halved evenly all the way down to a 1 × 1 square.

**Adding a driver.** Walk down from the root to the leaf whose square holds the driver, adding 1 to `count` on the way. Put the driver in the leaf's list. If the list now has more than 10 drivers, split the leaf into 4 squares and move each driver into the square it stands in. A new square that is still too crowded splits again.

**Removing a driver.** Do the same walk, subtracting 1 from `count`, and take the driver out of the leaf's list.

We never merge squares back together. An empty square just stays, and the search skips it because its `count` is 0. This keeps removing as simple as adding.

**Moving a driver** is a remove at the old spot plus an add at the new spot.


### Finding the Nearest Driver

The search starts at the root and carries the best driver found so far:

1. Skip a square whose `count` is 0.
2. Skip a square if even its nearest point is farther than our best driver. Nothing inside it can win or tie.
3. In a leaf, check every driver one by one.
4. In a split square, search the child on the pickup's side first, then the other three.

The child on the pickup's side is the closest one. A good driver found there early lets step 2 skip more squares later.

To measure the distance from the pickup to a square, we use the square's nearest point. On each axis, that is how far the pickup sits outside the square, or 0 when it is inside: `dx = max(0, left - x, x - right)`, and the same for `dy`. The squared distance is then `dx² + dy²`, as usual.

Ties are safe. Step 2 skips a square only when it is strictly farther than the best driver. A square at exactly the same distance is still searched, so a driver at the same distance with a smaller id can still win.


### Corner Case: Drivers on the Same Point

Real cars have length and breadth, so two cars never stand on the exact same point. The tests, though, use plain integer points, so many drivers can share one. A 1 × 1 square cannot split any further, so it simply keeps all of them. That is the `node.size > 1` check in `insert`.


### Code

Only `QuadNode`, `QuadTreeDriverMatcher` and one line in `RideHailingSystem` are new. Everything else is the same as Solution 1.

```java
import java.util.*;

/** States of a trip. COMPLETED and CANCELLED are terminal. */
enum TripStatus {
    REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED
}

/** Payment states of a trip. */
enum PaymentStatus {
    NOT_STARTED, PENDING, RETRY_PENDING, SUCCEEDED, FAILED
}

class Rider {
    String id;
    String name;
    String activeTripId;   // null when the rider has no running trip

    Rider(String id, String name) {
        this.id = id;
        this.name = name;
    }
}

class Driver {
    String id;
    String name;
    int x;
    int y;
    boolean available = true;   // can this driver get a new trip request?
    String activeTripId;        // null when the driver is not on a trip

    Driver(String id, String name, int x, int y) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
    }

    /** Squared distance to a point. We use long so the result never overflows. */
    long distanceTo(int pointX, int pointY) {
        long dx = (long) x - pointX;
        long dy = (long) y - pointY;
        return dx * dx + dy * dy;
    }

    /** True if this driver is a better pick than other: closer, or same distance and smaller id. */
    boolean isBetterThan(Driver other, int pickupX, int pickupY) {
        if (other == null) {
            return true;
        }
        long myDistance = distanceTo(pickupX, pickupY);
        long otherDistance = other.distanceTo(pickupX, pickupY);
        if (myDistance != otherDistance) {
            return myDistance < otherDistance;
        }
        return id.compareTo(other.id) < 0;
    }
}

class Trip {
    String id;
    String riderId;
    String driverId;
    int pickupX;
    int pickupY;
    int destinationX;
    int destinationY;
    String zoneId;
    String paymentMethodId;
    int surgePercentage;   // copied from the zone when the trip is created

    TripStatus status = TripStatus.REQUESTED;
    long fareInCents = 0;
    PaymentStatus paymentStatus = PaymentStatus.NOT_STARTED;
    int paymentAttempts = 0;

    // paymentRequestId -> the answer we gave the first time we saw it
    Map<String, String> paymentResults = new HashMap<>();

    Trip(String id, String riderId, String driverId, int pickupX, int pickupY,
         int destinationX, int destinationY, String zoneId, String paymentMethodId, int surgePercentage) {
        this.id = id;
        this.riderId = riderId;
        this.driverId = driverId;
        this.pickupX = pickupX;
        this.pickupY = pickupY;
        this.destinationX = destinationX;
        this.destinationY = destinationY;
        this.zoneId = zoneId;
        this.paymentMethodId = paymentMethodId;
        this.surgePercentage = surgePercentage;
    }

    /** A trip is active until it is COMPLETED or CANCELLED. */
    boolean isActive() {
        return status == TripStatus.REQUESTED
                || status == TripStatus.ACCEPTED
                || status == TripStatus.IN_PROGRESS;
    }

    String details() {
        return id + "," + riderId + "," + driverId + "," + status + ","
                + pickupX + "," + pickupY + "," + destinationX + "," + destinationY + ","
                + zoneId + "," + surgePercentage + "," + fareInCents + ","
                + paymentStatus + "," + paymentAttempts;
    }
}

/**
 * Strategy for finding the nearest available driver.
 * The system tells it when a driver becomes free or busy,
 * and asks it for the best driver for a pickup point.
 */
interface DriverMatcher {
    void addDriver(Driver driver);            // driver can now get requests

    void removeDriver(Driver driver);         // driver can no longer get requests

    Driver findNearestDriver(int x, int y);   // null when no driver is available
}

/**
 * One square of the quadtree.
 * A leaf keeps a small list of drivers. A crowded leaf splits into 4 smaller squares.
 */
class QuadNode {
    long minX;
    long minY;
    long size;                                  // side length, always a power of two
    int count = 0;                              // drivers anywhere inside this square
    List<Driver> drivers = new ArrayList<>();   // used only while this node is a leaf
    QuadNode[] children = null;                 // 4 smaller squares after a split

    QuadNode(long minX, long minY, long size) {
        this.minX = minX;
        this.minY = minY;
        this.size = size;
    }

    boolean isLeaf() {
        return children == null;
    }

    /** Child square on the point's side: 0 bottom-left, 1 bottom-right, 2 top-left, 3 top-right. */
    int childIndex(int x, int y) {
        long half = size / 2;
        int index = 0;
        if (x >= minX + half) {
            index += 1;
        }
        if (y >= minY + half) {
            index += 2;
        }
        return index;
    }

    /** Squared distance from a point to the nearest point of this square. 0 if the point is inside. */
    long minDistanceTo(int x, int y) {
        long dx = Math.max(0, Math.max(minX - x, x - (minX + size - 1)));
        long dy = Math.max(0, Math.max(minY - y, y - (minY + size - 1)));
        return dx * dx + dy * dy;
    }
}

/** Faster strategy: a quadtree. A square with too many drivers splits into 4 smaller squares. */
class QuadTreeDriverMatcher implements DriverMatcher {
    int capacity = 10;   // a leaf splits once it holds more drivers than this
    // Side 2,097,152 = 2^21 covers every coordinate from -1,000,000 to 1,000,000
    QuadNode root = new QuadNode(-1_048_576, -1_048_576, 2_097_152);

    public void addDriver(Driver driver) {
        insert(root, driver);
    }

    public void removeDriver(Driver driver) {
        remove(root, driver);
    }

    public Driver findNearestDriver(int x, int y) {
        return search(root, x, y, null);
    }

    void insert(QuadNode node, Driver driver) {
        node.count++;
        if (!node.isLeaf()) {
            insert(node.children[node.childIndex(driver.x, driver.y)], driver);
            return;
        }
        node.drivers.add(driver);
        // A 1 x 1 square cannot split, so drivers on the exact same point just share it
        if (node.drivers.size() > capacity && node.size > 1) {
            split(node);
        }
    }

    void split(QuadNode node) {
        long half = node.size / 2;
        node.children = new QuadNode[] {
            new QuadNode(node.minX, node.minY, half),
            new QuadNode(node.minX + half, node.minY, half),
            new QuadNode(node.minX, node.minY + half, half),
            new QuadNode(node.minX + half, node.minY + half, half)
        };
        for (Driver driver : node.drivers) {
            insert(node.children[node.childIndex(driver.x, driver.y)], driver);
        }
        node.drivers = new ArrayList<>();
    }

    /** Squares are never merged back. Empty squares stay, and the search skips them using count. */
    void remove(QuadNode node, Driver driver) {
        node.count--;
        if (node.isLeaf()) {
            node.drivers.remove(driver);
        } else {
            remove(node.children[node.childIndex(driver.x, driver.y)], driver);
        }
    }

    /** Returns the better of best and every driver inside this square, skipping squares that cannot win. */
    Driver search(QuadNode node, int x, int y, Driver best) {
        if (node.count == 0) {
            return best;   // no drivers in this square
        }
        // Even the nearest point of this square is farther than our best driver: nothing inside can win or tie
        if (best != null && node.minDistanceTo(x, y) > best.distanceTo(x, y)) {
            return best;
        }
        if (node.isLeaf()) {
            for (Driver driver : node.drivers) {
                if (driver.isBetterThan(best, x, y)) {
                    best = driver;
                }
            }
            return best;
        }
        // The child on the pickup's side is the closest one, so look there first.
        // A good driver found early lets us skip more squares later.
        int first = node.childIndex(x, y);
        best = search(node.children[first], x, y, best);
        for (int i = 0; i < 4; i++) {
            if (i != first) {
                best = search(node.children[i], x, y, best);
            }
        }
        return best;
    }
}

public class RideHailingSystem {
    long baseFareInCents;
    long farePerDistanceUnitInCents;
    int maxPaymentAttempts;

    Map<String, Driver> drivers = new HashMap<>();
    Map<String, Rider> riders = new HashMap<>();
    Map<String, Trip> trips = new HashMap<>();
    Map<String, Integer> surgeByZone = new HashMap<>();          // zoneId -> surge percentage
    Map<String, String> paymentRequestOwner = new HashMap<>();   // paymentRequestId -> tripId

    DriverMatcher matcher = new QuadTreeDriverMatcher();

    public RideHailingSystem(long baseFareInCents, long farePerDistanceUnitInCents, int maxPaymentAttempts, List<String> drivers) {
        this.baseFareInCents = baseFareInCents;
        this.farePerDistanceUnitInCents = farePerDistanceUnitInCents;
        this.maxPaymentAttempts = maxPaymentAttempts;

        // Each entry looks like "driverId,driverName,locationX,locationY"
        for (String entry : drivers) {
            String[] parts = entry.split(",");
            Driver driver = new Driver(parts[0], parts[1],
                    Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
            this.drivers.put(driver.id, driver);
            matcher.addDriver(driver);   // every driver starts available
        }
    }

    public boolean addRider(String riderId, String riderName) {
        if (riders.containsKey(riderId)) {
            return false;
        }
        riders.put(riderId, new Rider(riderId, riderName));
        return true;
    }

    public boolean updateDriverLocation(String driverId, int locationX, int locationY) {
        Driver driver = drivers.get(driverId);
        if (driver == null) {
            return false;
        }
        // An available driver is stored in the matcher at the old spot.
        // Take it out, move it, then put it back at the new spot.
        if (driver.available) {
            matcher.removeDriver(driver);
        }
        driver.x = locationX;
        driver.y = locationY;
        if (driver.available) {
            matcher.addDriver(driver);
        }
        return true;
    }

    public boolean setDriverAvailability(String driverId, boolean available) {
        Driver driver = drivers.get(driverId);
        if (driver == null || driver.activeTripId != null) {
            return false;
        }
        if (driver.available != available) {
            driver.available = available;
            if (available) {
                matcher.addDriver(driver);
            } else {
                matcher.removeDriver(driver);
            }
        }
        return true;
    }

    public int updateSurgePricing(String zoneId, int activeRequests, int availableDrivers) {
        int surge;
        if (activeRequests == 0) {
            surge = 100;
        } else if (availableDrivers == 0) {
            surge = 300;
        } else if (activeRequests <= availableDrivers) {
            surge = 100;
        } else if (activeRequests <= 2 * availableDrivers) {
            surge = 150;
        } else {
            surge = 200;
        }
        surgeByZone.put(zoneId, surge);
        return surge;
    }

    public long estimateFare(String zoneId, int estimatedDistanceUnits) {
        return calculateFare(estimatedDistanceUnits, currentSurge(zoneId));
    }

    public String requestTrip(String tripId, String riderId, int pickupX, int pickupY, int destinationX, int destinationY, String zoneId, String paymentMethodId) {
        Rider rider = riders.get(riderId);
        if (trips.containsKey(tripId) || rider == null || rider.activeTripId != null) {
            return "";
        }
        Driver driver = matcher.findNearestDriver(pickupX, pickupY);
        if (driver == null) {
            return "";   // no driver is available
        }

        // The trip keeps the zone's surge from this moment. Later changes do not affect it.
        Trip trip = new Trip(tripId, riderId, driver.id, pickupX, pickupY,
                destinationX, destinationY, zoneId, paymentMethodId, currentSurge(zoneId));
        trips.put(tripId, trip);

        // Reserve the driver and link both people to this trip
        matcher.removeDriver(driver);
        driver.available = false;
        driver.activeTripId = tripId;
        rider.activeTripId = tripId;
        return driver.id;
    }

    public boolean acceptTrip(String tripId, String driverId) {
        Trip trip = trips.get(tripId);
        if (trip == null || trip.status != TripStatus.REQUESTED || !trip.driverId.equals(driverId)) {
            return false;
        }
        trip.status = TripStatus.ACCEPTED;
        return true;
    }

    public boolean startTrip(String tripId, String driverId) {
        Trip trip = trips.get(tripId);
        if (trip == null || trip.status != TripStatus.ACCEPTED || !trip.driverId.equals(driverId)) {
            return false;
        }
        trip.status = TripStatus.IN_PROGRESS;
        return true;
    }

    public long completeTrip(String tripId, String driverId, int actualDistanceUnits) {
        Trip trip = trips.get(tripId);
        if (trip == null || trip.status != TripStatus.IN_PROGRESS || !trip.driverId.equals(driverId)) {
            return -1;
        }
        trip.status = TripStatus.COMPLETED;
        trip.fareInCents = calculateFare(actualDistanceUnits, trip.surgePercentage);
        trip.paymentStatus = PaymentStatus.PENDING;

        // The driver ends up at the drop point
        Driver driver = drivers.get(driverId);
        driver.x = trip.destinationX;
        driver.y = trip.destinationY;
        releasePeople(trip);
        return trip.fareInCents;
    }

    public boolean cancelTrip(String tripId, String requesterId) {
        Trip trip = trips.get(tripId);
        if (trip == null || !trip.isActive()) {
            return false;
        }
        if (!requesterId.equals(trip.riderId) && !requesterId.equals(trip.driverId)) {
            return false;
        }
        trip.status = TripStatus.CANCELLED;   // fare stays 0 and payment stays NOT_STARTED
        releasePeople(trip);
        return true;
    }

    public String processPayment(String tripId, String paymentRequestId, boolean gatewaySuccessful) {
        Trip trip = trips.get(tripId);
        if (trip == null || trip.status != TripStatus.COMPLETED) {
            return "INVALID_REQUEST";
        }

        String ownerTripId = paymentRequestOwner.get(paymentRequestId);
        if (ownerTripId != null) {
            // Seen before: same trip gets the old answer, any other trip is rejected
            return ownerTripId.equals(tripId) ? trip.paymentResults.get(paymentRequestId) : "INVALID_REQUEST";
        }

        String result;
        if (trip.paymentStatus == PaymentStatus.SUCCEEDED || trip.paymentStatus == PaymentStatus.FAILED) {
            result = trip.paymentStatus.name();   // payment is already settled, no new attempt
        } else {
            trip.paymentAttempts++;
            if (gatewaySuccessful) {
                trip.paymentStatus = PaymentStatus.SUCCEEDED;
            } else if (trip.paymentAttempts < maxPaymentAttempts) {
                trip.paymentStatus = PaymentStatus.RETRY_PENDING;
            } else {
                trip.paymentStatus = PaymentStatus.FAILED;
            }
            result = trip.paymentStatus.name();
        }

        // Remember this request id so a retry gets exactly the same answer
        paymentRequestOwner.put(paymentRequestId, tripId);
        trip.paymentResults.put(paymentRequestId, result);
        return result;
    }

    public String getTripDetails(String tripId) {
        Trip trip = trips.get(tripId);
        return trip == null ? "" : trip.details();
    }

    /** Zones that were never updated use 100 percent. */
    int currentSurge(String zoneId) {
        return surgeByZone.getOrDefault(zoneId, 100);
    }

    /** ceil(subtotal * surge / 100). Adding 99 before dividing rounds up. */
    long calculateFare(int distanceUnits, int surgePercentage) {
        long subtotal = baseFareInCents + distanceUnits * farePerDistanceUnitInCents;
        return (subtotal * surgePercentage + 99) / 100;
    }

    /** Called when a trip ends. Frees the rider, and the driver can take new requests again. */
    void releasePeople(Trip trip) {
        riders.get(trip.riderId).activeTripId = null;
        Driver driver = drivers.get(trip.driverId);
        driver.activeTripId = null;
        driver.available = true;
        matcher.addDriver(driver);
    }
}
```


## Complexity

`D` is the number of available drivers. The quadtree's depth is at most 21, because each level halves the square and 2²¹ covers the whole map.

| Operation | Solution 1 | Solution 2 (quadtree) |
| --- | --- | --- |
| Constructor | O(drivers) | O(drivers × depth) |
| `requestTrip` | O(D) | About O(log D) usually, O(D) in the worst case |
| `updateDriverLocation`, `setDriverAvailability` | O(1) | O(depth) |
| `completeTrip`, `cancelTrip` | O(1) | O(depth) |
| All other methods | O(1) | O(1) |

A request usually opens only the few squares near the pickup, which is why it is about O(log D).

Space is O(drivers + riders + trips + payment request ids + zones), plus the quadtree's squares. Squares are never merged, so a square stays even after its drivers leave. That is fine for this problem's size.