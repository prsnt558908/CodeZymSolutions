# Design Ride-Hailing System Like Uber, Ola in Python

#### Problem Statement
[https://codezym.com/question/458-ride-hailing-system-like-uber](https://codezym.com/question/458-ride-hailing-system-like-uber)


## Core Idea

We model the system with three small classes, `Rider`, `Driver` and `Trip`, each kept in a dictionary by id so every lookup is instant. A simple enum guards the fixed trip states. The only hard part is finding the nearest free driver quickly, so we give that job its own small class. We start with a version that checks every driver, then replace it with a fast quadtree. No design pattern is needed here: Strategy and State sound useful, but with one fixed matching rule and a few fixed trip states, they would only add extra classes.

For the fast matcher, we compare two common spatial indexes: a grid and a quadtree. We code the quadtree, because it adapts on its own to crowded city centers and empty outskirts, which is exactly how real cities look.

Payments are made safe to retry by remembering the answer we gave for every payment request id.


## Do We Need Design Patterns?

No. Two patterns look tempting here, but both would add classes without making the code any clearer.


### Why Not the Strategy Pattern?

Strategy helps when a program must choose between several ways of doing a job, for example matching by distance in one city and by driver rating in another. Here there is only one rule: the nearest driver wins, and the smaller id breaks ties. Each solution uses exactly one way to find that driver, so a shared base class would have just one class behind it. We still keep the search in its own small class, which keeps `RideHailingSystem` short. If more matching rules ever show up, adding a shared base class then is a small change.


### Why Not the State Pattern?

The State pattern would create one class per trip state, and each class would decide which moves are allowed.

Here every move is allowed from exactly one state, and cancel is allowed from any active state. Each rule is a single `if`. Five state classes would spread these tiny rules over many places. An enum keeps all of them in plain sight.


## Building Blocks

The public method names like `requestTrip` follow the problem statement. Our own helpers use normal Python `snake_case`.


### Driver

Keeps the location, an `available` flag and `active_trip_id`.

- `available` says whether the driver can get a new request.
- `active_trip_id` says whether the driver is on a trip. `setDriverAvailability` must fail while it is set.

It also has `is_better_than()`, the matching rule in one place: the closer driver wins, and on a tie the smaller id wins. Comparing the tuples `(distance, id)` does both checks at once, because tuples are compared item by item.


### Rider

Keeps `active_trip_id`. A rider who already has an active trip cannot request another one.


### Trip

Keeps the booking details, the state, the fare and the payment details.

It also copies the zone's surge percentage at request time. Later surge updates must not change this trip's fare, so we never look the zone up again for this trip.

`payment_results` remembers the first answer given for each payment request id.


### Dictionaries in RideHailingSystem

| Dictionary | Why we need it |
| --- | --- |
| `drivers`, `riders`, `trips` | O(1) lookup by id |
| `surge_by_zone` | Current surge of each zone. A missing zone means 100 |
| `payment_request_owner` | Which trip used each payment request id, so no other trip can reuse it |


## How the Main Operations Work

### Trip lifecycle

`REQUESTED → ACCEPTED → IN_PROGRESS → COMPLETED`, and any of the first three can move to `CANCELLED`.

`acceptTrip`, `startTrip` and `completeTrip` each check two things: the trip is in the one allowed state, and the caller is the assigned driver. If a check fails, nothing changes.

`requestTrip` reserves the chosen driver. It removes the driver from the matcher, marks it unavailable and links the rider and the driver to the trip.

When a trip ends, `release_people()` does the reverse. It clears both links, marks the driver available and gives it back to the matcher. On completion, we first move the driver to the destination.


### Fares

`subtotal = baseFare + distance × farePerUnit`

`fare = ceil(subtotal × surge / 100)`

Adding 99 before dividing by 100 rounds up, using plain integer math with no floating point. For example, `(601 + 99) // 100 = 7`, while `(600 + 99) // 100 = 6`.

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

`LinearDriverMatcher` keeps every available driver in a `set`. To find the nearest one, it checks them all.

It has three methods: `add_driver`, `remove_driver` and `find_nearest_driver`. The quadtree in Solution 2 has the same three, so switching only changes one line in `RideHailingSystem`.

Busy and offline drivers are never in the set. A driver leaves the set when it gets a trip or goes offline, and comes back when the trip ends or it comes online again.

When an available driver moves, we remove it, update its location and add it back. For a set this changes nothing, but in Solution 2 the driver's place in the quadtree depends on its location, so this order matters there.

```python
from enum import Enum


class TripStatus(Enum):
    """States of a trip. COMPLETED and CANCELLED are terminal."""
    REQUESTED = "REQUESTED"
    ACCEPTED = "ACCEPTED"
    IN_PROGRESS = "IN_PROGRESS"
    COMPLETED = "COMPLETED"
    CANCELLED = "CANCELLED"


class PaymentStatus(Enum):
    """Payment states of a trip."""
    NOT_STARTED = "NOT_STARTED"
    PENDING = "PENDING"
    RETRY_PENDING = "RETRY_PENDING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"


class Rider:
    def __init__(self, rider_id, name):
        self.id = rider_id
        self.name = name
        self.active_trip_id = None   # None when the rider has no running trip


class Driver:
    def __init__(self, driver_id, name, x, y):
        self.id = driver_id
        self.name = name
        self.x = x
        self.y = y
        self.available = True        # can this driver get a new trip request?
        self.active_trip_id = None   # None when the driver is not on a trip

    def distance_to(self, point_x, point_y):
        """Squared distance to a point."""
        dx = self.x - point_x
        dy = self.y - point_y
        return dx * dx + dy * dy

    def is_better_than(self, other, pickup_x, pickup_y):
        """True if this driver is a better pick than other: closer, or same distance and smaller id."""
        if other is None:
            return True
        # Tuples compare item by item: distance first, then id
        return (self.distance_to(pickup_x, pickup_y), self.id) < (other.distance_to(pickup_x, pickup_y), other.id)


class Trip:
    def __init__(self, trip_id, rider_id, driver_id, pickup_x, pickup_y,
                 destination_x, destination_y, zone_id, payment_method_id, surge_percentage):
        self.id = trip_id
        self.rider_id = rider_id
        self.driver_id = driver_id
        self.pickup_x = pickup_x
        self.pickup_y = pickup_y
        self.destination_x = destination_x
        self.destination_y = destination_y
        self.zone_id = zone_id
        self.payment_method_id = payment_method_id
        self.surge_percentage = surge_percentage   # copied from the zone when the trip is created

        self.status = TripStatus.REQUESTED
        self.fare_in_cents = 0
        self.payment_status = PaymentStatus.NOT_STARTED
        self.payment_attempts = 0
        self.payment_results = {}   # paymentRequestId -> the answer we gave the first time we saw it

    def is_active(self):
        """A trip is active until it is COMPLETED or CANCELLED."""
        return self.status in (TripStatus.REQUESTED, TripStatus.ACCEPTED, TripStatus.IN_PROGRESS)

    def details(self):
        parts = [self.id, self.rider_id, self.driver_id, self.status.value,
                 self.pickup_x, self.pickup_y, self.destination_x, self.destination_y,
                 self.zone_id, self.surge_percentage, self.fare_in_cents,
                 self.payment_status.value, self.payment_attempts]
        return ",".join(str(part) for part in parts)


class LinearDriverMatcher:
    """
    Finds the nearest available driver by checking every one of them.
    The system calls add_driver when a driver becomes free,
    and remove_driver when the driver gets a trip or goes offline.
    """

    def __init__(self):
        self.available_drivers = set()

    def add_driver(self, driver):
        self.available_drivers.add(driver)

    def remove_driver(self, driver):
        self.available_drivers.discard(driver)

    def find_nearest_driver(self, x, y):
        """Returns None when no driver is available."""
        best = None
        for driver in self.available_drivers:
            if driver.is_better_than(best, x, y):
                best = driver
        return best


class RideHailingSystem:
    def __init__(self, baseFareInCents, farePerDistanceUnitInCents, maxPaymentAttempts, drivers):
        self.base_fare = baseFareInCents
        self.fare_per_unit = farePerDistanceUnitInCents
        self.max_payment_attempts = maxPaymentAttempts

        self.drivers = {}                 # driverId -> Driver
        self.riders = {}                  # riderId -> Rider
        self.trips = {}                   # tripId -> Trip
        self.surge_by_zone = {}           # zoneId -> surge percentage
        self.payment_request_owner = {}   # paymentRequestId -> tripId

        self.matcher = LinearDriverMatcher()

        # Each entry looks like "driverId,driverName,locationX,locationY"
        for entry in drivers:
            driver_id, name, x, y = entry.split(",")
            driver = Driver(driver_id, name, int(x), int(y))
            self.drivers[driver_id] = driver
            self.matcher.add_driver(driver)   # every driver starts available

    def addRider(self, riderId, riderName):
        if riderId in self.riders:
            return False
        self.riders[riderId] = Rider(riderId, riderName)
        return True

    def updateDriverLocation(self, driverId, locationX, locationY):
        driver = self.drivers.get(driverId)
        if driver is None:
            return False
        # An available driver is stored in the matcher at the old spot.
        # Take it out, move it, then put it back at the new spot.
        if driver.available:
            self.matcher.remove_driver(driver)
        driver.x = locationX
        driver.y = locationY
        if driver.available:
            self.matcher.add_driver(driver)
        return True

    def setDriverAvailability(self, driverId, available):
        driver = self.drivers.get(driverId)
        if driver is None or driver.active_trip_id is not None:
            return False
        if driver.available != available:
            driver.available = available
            if available:
                self.matcher.add_driver(driver)
            else:
                self.matcher.remove_driver(driver)
        return True

    def updateSurgePricing(self, zoneId, activeRequests, availableDrivers):
        if activeRequests == 0:
            surge = 100
        elif availableDrivers == 0:
            surge = 300
        elif activeRequests <= availableDrivers:
            surge = 100
        elif activeRequests <= 2 * availableDrivers:
            surge = 150
        else:
            surge = 200
        self.surge_by_zone[zoneId] = surge
        return surge

    def estimateFare(self, zoneId, estimatedDistanceUnits):
        return self.calculate_fare(estimatedDistanceUnits, self.current_surge(zoneId))

    def requestTrip(self, tripId, riderId, pickupX, pickupY, destinationX, destinationY, zoneId, paymentMethodId):
        rider = self.riders.get(riderId)
        if tripId in self.trips or rider is None or rider.active_trip_id is not None:
            return ""
        driver = self.matcher.find_nearest_driver(pickupX, pickupY)
        if driver is None:
            return ""   # no driver is available

        # The trip keeps the zone's surge from this moment. Later changes do not affect it.
        trip = Trip(tripId, riderId, driver.id, pickupX, pickupY,
                    destinationX, destinationY, zoneId, paymentMethodId, self.current_surge(zoneId))
        self.trips[tripId] = trip

        # Reserve the driver and link both people to this trip
        self.matcher.remove_driver(driver)
        driver.available = False
        driver.active_trip_id = tripId
        rider.active_trip_id = tripId
        return driver.id

    def acceptTrip(self, tripId, driverId):
        trip = self.trips.get(tripId)
        if trip is None or trip.status != TripStatus.REQUESTED or trip.driver_id != driverId:
            return False
        trip.status = TripStatus.ACCEPTED
        return True

    def startTrip(self, tripId, driverId):
        trip = self.trips.get(tripId)
        if trip is None or trip.status != TripStatus.ACCEPTED or trip.driver_id != driverId:
            return False
        trip.status = TripStatus.IN_PROGRESS
        return True

    def completeTrip(self, tripId, driverId, actualDistanceUnits):
        trip = self.trips.get(tripId)
        if trip is None or trip.status != TripStatus.IN_PROGRESS or trip.driver_id != driverId:
            return -1
        trip.status = TripStatus.COMPLETED
        trip.fare_in_cents = self.calculate_fare(actualDistanceUnits, trip.surge_percentage)
        trip.payment_status = PaymentStatus.PENDING

        # The driver ends up at the drop point
        driver = self.drivers[driverId]
        driver.x = trip.destination_x
        driver.y = trip.destination_y
        self.release_people(trip)
        return trip.fare_in_cents

    def cancelTrip(self, tripId, requesterId):
        trip = self.trips.get(tripId)
        if trip is None or not trip.is_active():
            return False
        if requesterId != trip.rider_id and requesterId != trip.driver_id:
            return False
        trip.status = TripStatus.CANCELLED   # fare stays 0 and payment stays NOT_STARTED
        self.release_people(trip)
        return True

    def processPayment(self, tripId, paymentRequestId, gatewaySuccessful):
        trip = self.trips.get(tripId)
        if trip is None or trip.status != TripStatus.COMPLETED:
            return "INVALID_REQUEST"

        owner_trip_id = self.payment_request_owner.get(paymentRequestId)
        if owner_trip_id is not None:
            # Seen before: same trip gets the old answer, any other trip is rejected
            return trip.payment_results[paymentRequestId] if owner_trip_id == tripId else "INVALID_REQUEST"

        if trip.payment_status in (PaymentStatus.SUCCEEDED, PaymentStatus.FAILED):
            result = trip.payment_status.value   # payment is already settled, no new attempt
        else:
            trip.payment_attempts += 1
            if gatewaySuccessful:
                trip.payment_status = PaymentStatus.SUCCEEDED
            elif trip.payment_attempts < self.max_payment_attempts:
                trip.payment_status = PaymentStatus.RETRY_PENDING
            else:
                trip.payment_status = PaymentStatus.FAILED
            result = trip.payment_status.value

        # Remember this request id so a retry gets exactly the same answer
        self.payment_request_owner[paymentRequestId] = tripId
        trip.payment_results[paymentRequestId] = result
        return result

    def getTripDetails(self, tripId):
        trip = self.trips.get(tripId)
        return "" if trip is None else trip.details()

    def current_surge(self, zone_id):
        """Zones that were never updated use 100 percent."""
        return self.surge_by_zone.get(zone_id, 100)

    def calculate_fare(self, distance_units, surge_percentage):
        """ceil(subtotal * surge / 100). Adding 99 before dividing rounds up."""
        subtotal = self.base_fare + distance_units * self.fare_per_unit
        return (subtotal * surge_percentage + 99) // 100

    def release_people(self, trip):
        """Called when a trip ends. Frees the rider, and the driver can take new requests again."""
        self.riders[trip.rider_id].active_trip_id = None
        driver = self.drivers[trip.driver_id]
        driver.active_trip_id = None
        driver.available = True
        self.matcher.add_driver(driver)
```


### The Problem With Solution 1

Every `requestTrip` looks at every available driver. That is O(D) work for each request.

With 100,000 drivers and tens of thousands of requests, that is billions of distance checks. Yet a rider never needs a driver from the far side of the map. We only need to look nearby.


## Solution 2: Spatial Index With a Quadtree

A spatial index groups drivers by where they are, so a request only looks near the pickup. Two common choices are a grid and a quadtree.


### Option 1: Grid

Cut the map into equal square cells, like a chessboard. A dictionary maps each cell to the drivers inside it, and a driver's cell is found with one division.

To search, check the pickup's cell, then the ring of 8 cells around it, then the next ring of 16, and so on, until no unseen cell can hold a closer driver.

```
2 2 2 2 2
2 1 1 1 2
2 1 0 1 2
2 1 1 1 2
2 2 2 2 2
```

**Pros**

- Very simple: one dictionary from cell to drivers.
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

The tree is at most 21 levels deep, so the recursive calls stay far below Python's recursion limit.


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

`QuadNode` and `QuadTreeDriverMatcher` replace `LinearDriverMatcher`, and one line in `RideHailingSystem` changes. Everything else is the same as Solution 1.

```python
from enum import Enum


class TripStatus(Enum):
    """States of a trip. COMPLETED and CANCELLED are terminal."""
    REQUESTED = "REQUESTED"
    ACCEPTED = "ACCEPTED"
    IN_PROGRESS = "IN_PROGRESS"
    COMPLETED = "COMPLETED"
    CANCELLED = "CANCELLED"


class PaymentStatus(Enum):
    """Payment states of a trip."""
    NOT_STARTED = "NOT_STARTED"
    PENDING = "PENDING"
    RETRY_PENDING = "RETRY_PENDING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"


class Rider:
    def __init__(self, rider_id, name):
        self.id = rider_id
        self.name = name
        self.active_trip_id = None   # None when the rider has no running trip


class Driver:
    def __init__(self, driver_id, name, x, y):
        self.id = driver_id
        self.name = name
        self.x = x
        self.y = y
        self.available = True        # can this driver get a new trip request?
        self.active_trip_id = None   # None when the driver is not on a trip

    def distance_to(self, point_x, point_y):
        """Squared distance to a point."""
        dx = self.x - point_x
        dy = self.y - point_y
        return dx * dx + dy * dy

    def is_better_than(self, other, pickup_x, pickup_y):
        """True if this driver is a better pick than other: closer, or same distance and smaller id."""
        if other is None:
            return True
        # Tuples compare item by item: distance first, then id
        return (self.distance_to(pickup_x, pickup_y), self.id) < (other.distance_to(pickup_x, pickup_y), other.id)


class Trip:
    def __init__(self, trip_id, rider_id, driver_id, pickup_x, pickup_y,
                 destination_x, destination_y, zone_id, payment_method_id, surge_percentage):
        self.id = trip_id
        self.rider_id = rider_id
        self.driver_id = driver_id
        self.pickup_x = pickup_x
        self.pickup_y = pickup_y
        self.destination_x = destination_x
        self.destination_y = destination_y
        self.zone_id = zone_id
        self.payment_method_id = payment_method_id
        self.surge_percentage = surge_percentage   # copied from the zone when the trip is created

        self.status = TripStatus.REQUESTED
        self.fare_in_cents = 0
        self.payment_status = PaymentStatus.NOT_STARTED
        self.payment_attempts = 0
        self.payment_results = {}   # paymentRequestId -> the answer we gave the first time we saw it

    def is_active(self):
        """A trip is active until it is COMPLETED or CANCELLED."""
        return self.status in (TripStatus.REQUESTED, TripStatus.ACCEPTED, TripStatus.IN_PROGRESS)

    def details(self):
        parts = [self.id, self.rider_id, self.driver_id, self.status.value,
                 self.pickup_x, self.pickup_y, self.destination_x, self.destination_y,
                 self.zone_id, self.surge_percentage, self.fare_in_cents,
                 self.payment_status.value, self.payment_attempts]
        return ",".join(str(part) for part in parts)


class QuadNode:
    """
    One square of the quadtree.
    A leaf keeps a small list of drivers. A crowded leaf splits into 4 smaller squares.
    """

    def __init__(self, min_x, min_y, size):
        self.min_x = min_x
        self.min_y = min_y
        self.size = size        # side length, always a power of two
        self.count = 0          # drivers anywhere inside this square
        self.drivers = []       # used only while this node is a leaf
        self.children = None    # 4 smaller squares after a split

    def is_leaf(self):
        return self.children is None

    def child_index(self, x, y):
        """Child square on the point's side: 0 bottom-left, 1 bottom-right, 2 top-left, 3 top-right."""
        half = self.size // 2
        index = 0
        if x >= self.min_x + half:
            index += 1
        if y >= self.min_y + half:
            index += 2
        return index

    def min_distance_to(self, x, y):
        """Squared distance from a point to the nearest point of this square. 0 if the point is inside."""
        dx = max(0, self.min_x - x, x - (self.min_x + self.size - 1))
        dy = max(0, self.min_y - y, y - (self.min_y + self.size - 1))
        return dx * dx + dy * dy


class QuadTreeDriverMatcher:
    """
    Finds the nearest available driver with a quadtree.
    A square with too many drivers splits into 4 smaller squares.
    """

    def __init__(self):
        self.capacity = 10   # a leaf splits once it holds more drivers than this
        # Side 2,097,152 = 2^21 covers every coordinate from -1,000,000 to 1,000,000
        self.root = QuadNode(-1_048_576, -1_048_576, 2_097_152)

    def add_driver(self, driver):
        self.insert(self.root, driver)

    def remove_driver(self, driver):
        self.remove(self.root, driver)

    def find_nearest_driver(self, x, y):
        """Returns None when no driver is available."""
        return self.search(self.root, x, y, None)

    def insert(self, node, driver):
        node.count += 1
        if not node.is_leaf():
            self.insert(node.children[node.child_index(driver.x, driver.y)], driver)
            return
        node.drivers.append(driver)
        # A 1 x 1 square cannot split, so drivers on the exact same point just share it
        if len(node.drivers) > self.capacity and node.size > 1:
            self.split(node)

    def split(self, node):
        half = node.size // 2
        node.children = [
            QuadNode(node.min_x, node.min_y, half),
            QuadNode(node.min_x + half, node.min_y, half),
            QuadNode(node.min_x, node.min_y + half, half),
            QuadNode(node.min_x + half, node.min_y + half, half),
        ]
        for driver in node.drivers:
            self.insert(node.children[node.child_index(driver.x, driver.y)], driver)
        node.drivers = []

    def remove(self, node, driver):
        """Squares are never merged back. Empty squares stay, and the search skips them using count."""
        node.count -= 1
        if node.is_leaf():
            node.drivers.remove(driver)
        else:
            self.remove(node.children[node.child_index(driver.x, driver.y)], driver)

    def search(self, node, x, y, best):
        """Returns the better of best and every driver inside this square, skipping squares that cannot win."""
        if node.count == 0:
            return best   # no drivers in this square
        # Even the nearest point of this square is farther than our best driver: nothing inside can win or tie
        if best is not None and node.min_distance_to(x, y) > best.distance_to(x, y):
            return best
        if node.is_leaf():
            for driver in node.drivers:
                if driver.is_better_than(best, x, y):
                    best = driver
            return best
        # The child on the pickup's side is the closest one, so look there first.
        # A good driver found early lets us skip more squares later.
        first = node.child_index(x, y)
        best = self.search(node.children[first], x, y, best)
        for i in range(4):
            if i != first:
                best = self.search(node.children[i], x, y, best)
        return best


class RideHailingSystem:
    def __init__(self, baseFareInCents, farePerDistanceUnitInCents, maxPaymentAttempts, drivers):
        self.base_fare = baseFareInCents
        self.fare_per_unit = farePerDistanceUnitInCents
        self.max_payment_attempts = maxPaymentAttempts

        self.drivers = {}                 # driverId -> Driver
        self.riders = {}                  # riderId -> Rider
        self.trips = {}                   # tripId -> Trip
        self.surge_by_zone = {}           # zoneId -> surge percentage
        self.payment_request_owner = {}   # paymentRequestId -> tripId

        self.matcher = QuadTreeDriverMatcher()

        # Each entry looks like "driverId,driverName,locationX,locationY"
        for entry in drivers:
            driver_id, name, x, y = entry.split(",")
            driver = Driver(driver_id, name, int(x), int(y))
            self.drivers[driver_id] = driver
            self.matcher.add_driver(driver)   # every driver starts available

    def addRider(self, riderId, riderName):
        if riderId in self.riders:
            return False
        self.riders[riderId] = Rider(riderId, riderName)
        return True

    def updateDriverLocation(self, driverId, locationX, locationY):
        driver = self.drivers.get(driverId)
        if driver is None:
            return False
        # An available driver is stored in the matcher at the old spot.
        # Take it out, move it, then put it back at the new spot.
        if driver.available:
            self.matcher.remove_driver(driver)
        driver.x = locationX
        driver.y = locationY
        if driver.available:
            self.matcher.add_driver(driver)
        return True

    def setDriverAvailability(self, driverId, available):
        driver = self.drivers.get(driverId)
        if driver is None or driver.active_trip_id is not None:
            return False
        if driver.available != available:
            driver.available = available
            if available:
                self.matcher.add_driver(driver)
            else:
                self.matcher.remove_driver(driver)
        return True

    def updateSurgePricing(self, zoneId, activeRequests, availableDrivers):
        if activeRequests == 0:
            surge = 100
        elif availableDrivers == 0:
            surge = 300
        elif activeRequests <= availableDrivers:
            surge = 100
        elif activeRequests <= 2 * availableDrivers:
            surge = 150
        else:
            surge = 200
        self.surge_by_zone[zoneId] = surge
        return surge

    def estimateFare(self, zoneId, estimatedDistanceUnits):
        return self.calculate_fare(estimatedDistanceUnits, self.current_surge(zoneId))

    def requestTrip(self, tripId, riderId, pickupX, pickupY, destinationX, destinationY, zoneId, paymentMethodId):
        rider = self.riders.get(riderId)
        if tripId in self.trips or rider is None or rider.active_trip_id is not None:
            return ""
        driver = self.matcher.find_nearest_driver(pickupX, pickupY)
        if driver is None:
            return ""   # no driver is available

        # The trip keeps the zone's surge from this moment. Later changes do not affect it.
        trip = Trip(tripId, riderId, driver.id, pickupX, pickupY,
                    destinationX, destinationY, zoneId, paymentMethodId, self.current_surge(zoneId))
        self.trips[tripId] = trip

        # Reserve the driver and link both people to this trip
        self.matcher.remove_driver(driver)
        driver.available = False
        driver.active_trip_id = tripId
        rider.active_trip_id = tripId
        return driver.id

    def acceptTrip(self, tripId, driverId):
        trip = self.trips.get(tripId)
        if trip is None or trip.status != TripStatus.REQUESTED or trip.driver_id != driverId:
            return False
        trip.status = TripStatus.ACCEPTED
        return True

    def startTrip(self, tripId, driverId):
        trip = self.trips.get(tripId)
        if trip is None or trip.status != TripStatus.ACCEPTED or trip.driver_id != driverId:
            return False
        trip.status = TripStatus.IN_PROGRESS
        return True

    def completeTrip(self, tripId, driverId, actualDistanceUnits):
        trip = self.trips.get(tripId)
        if trip is None or trip.status != TripStatus.IN_PROGRESS or trip.driver_id != driverId:
            return -1
        trip.status = TripStatus.COMPLETED
        trip.fare_in_cents = self.calculate_fare(actualDistanceUnits, trip.surge_percentage)
        trip.payment_status = PaymentStatus.PENDING

        # The driver ends up at the drop point
        driver = self.drivers[driverId]
        driver.x = trip.destination_x
        driver.y = trip.destination_y
        self.release_people(trip)
        return trip.fare_in_cents

    def cancelTrip(self, tripId, requesterId):
        trip = self.trips.get(tripId)
        if trip is None or not trip.is_active():
            return False
        if requesterId != trip.rider_id and requesterId != trip.driver_id:
            return False
        trip.status = TripStatus.CANCELLED   # fare stays 0 and payment stays NOT_STARTED
        self.release_people(trip)
        return True

    def processPayment(self, tripId, paymentRequestId, gatewaySuccessful):
        trip = self.trips.get(tripId)
        if trip is None or trip.status != TripStatus.COMPLETED:
            return "INVALID_REQUEST"

        owner_trip_id = self.payment_request_owner.get(paymentRequestId)
        if owner_trip_id is not None:
            # Seen before: same trip gets the old answer, any other trip is rejected
            return trip.payment_results[paymentRequestId] if owner_trip_id == tripId else "INVALID_REQUEST"

        if trip.payment_status in (PaymentStatus.SUCCEEDED, PaymentStatus.FAILED):
            result = trip.payment_status.value   # payment is already settled, no new attempt
        else:
            trip.payment_attempts += 1
            if gatewaySuccessful:
                trip.payment_status = PaymentStatus.SUCCEEDED
            elif trip.payment_attempts < self.max_payment_attempts:
                trip.payment_status = PaymentStatus.RETRY_PENDING
            else:
                trip.payment_status = PaymentStatus.FAILED
            result = trip.payment_status.value

        # Remember this request id so a retry gets exactly the same answer
        self.payment_request_owner[paymentRequestId] = tripId
        trip.payment_results[paymentRequestId] = result
        return result

    def getTripDetails(self, tripId):
        trip = self.trips.get(tripId)
        return "" if trip is None else trip.details()

    def current_surge(self, zone_id):
        """Zones that were never updated use 100 percent."""
        return self.surge_by_zone.get(zone_id, 100)

    def calculate_fare(self, distance_units, surge_percentage):
        """ceil(subtotal * surge / 100). Adding 99 before dividing rounds up."""
        subtotal = self.base_fare + distance_units * self.fare_per_unit
        return (subtotal * surge_percentage + 99) // 100

    def release_people(self, trip):
        """Called when a trip ends. Frees the rider, and the driver can take new requests again."""
        self.riders[trip.rider_id].active_trip_id = None
        driver = self.drivers[trip.driver_id]
        driver.active_trip_id = None
        driver.available = True
        self.matcher.add_driver(driver)
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