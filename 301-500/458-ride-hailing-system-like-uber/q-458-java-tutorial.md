# Design Ride-Hailing System Like Uber, Ola in Java

#### Problem Statement

[https://codezym.com/question/458-ride-hailing-system-like-uber](https://codezym.com/question/458-ride-hailing-system-like-uber)

The core idea is to keep the system around three small entities, `Rider`, `Driver`, and `Trip`. Hash maps give direct access by identifier, an enum-backed state machine protects the trip lifecycle, and a sorted set stores only drivers who can currently receive a request. This combination is a good fit for the fixed rules in this problem. A full State pattern with one class per state or Strategy interfaces for the single matching and pricing rules would add extra classes without making the solution clearer.

## 1. Start With a Simple Approach

The simplest correct matching method is to store every driver in a map and scan all drivers whenever a rider requests a trip.

During the scan, we would ignore unavailable or assigned drivers, calculate the squared distance for every remaining driver, and keep the closest one. If two drivers have the same distance, we would keep the lexicographically smaller driver identifier.

This is easy to implement, but every trip request examines all available drivers. With many drivers and many requests, that repeated work can become expensive.

## 2. Improve Matching With a Sorted Set

We keep every available and unassigned driver in a `TreeSet` ordered by:

1. The driver's x-coordinate.
2. The driver's identifier.

This acts as a lightweight spatial index.

For a pickup point, we first inspect a driver immediately to the left or right on the x-axis. Suppose that driver's squared distance is `bestDistance`. Any driver whose x-coordinate differs from the pickup by more than `floor(sqrt(bestDistance))` cannot be closer, even before considering its y-coordinate.

We therefore inspect only the drivers inside that x-coordinate range. For every candidate, we still calculate the complete two-dimensional squared distance, so the answer remains exact. Equal distances are resolved using the driver identifier.

The index improves normal requests while staying simple. In the worst case, many drivers can share a narrow x-coordinate range, so one request can still inspect all available drivers.

Only matchable drivers belong in this set. A driver is removed when assigned or manually made unavailable. The driver is inserted again after completion, cancellation, or a later availability change.

## 3. Model the Important Data

### Rider

A rider stores its identifier, name, and `activeTripId`. A non-null active trip prevents the rider from creating another nonterminal trip.

### Driver

A driver stores its identifier, name, current coordinates, availability, and `activeTripId`. The active trip also prevents availability from being changed while the driver is assigned.

### DriverPosition

The sorted set needs only the x-coordinate and driver identifier. We use a separate immutable `DriverPosition` object so changing a driver's location cannot silently damage the ordering inside the set.

When an available driver's location changes, the old position is removed before the coordinates are updated, and the new position is then inserted.

### Trip

A trip keeps all booking details and its changing state. It also stores the surge percentage that existed when the request was created. This snapshot is important because later surge updates must not change an existing trip's final fare.

The trip also keeps payment attempt results by `paymentRequestId`. That lets repeated payment requests return the exact original result.

## 4. Protect the Trip Lifecycle

The successful state sequence is:

`REQUESTED → ACCEPTED → IN_PROGRESS → COMPLETED`

A trip may move from any nonterminal state to `CANCELLED`.

Each public method checks both the current state and the assigned driver before changing anything. Invalid calls return immediately and leave the stored data unchanged.

An enum-backed state machine is simpler here than the classic State pattern. There are only a few fixed transitions, and each transition needs just one small validation block.

When a trip completes or is cancelled, both active-trip links are cleared. The driver is made available and returned to the matching index. On completion, the driver's location is first moved to the destination.

## 5. Calculate and Freeze Surge Pricing

Each zone stores one percentage such as `100`, `150`, `200`, or `300`. A zone that has never been updated uses `100`.

For a distance, first calculate:

`subtotal = baseFareInCents + distanceUnits × farePerDistanceUnitInCents`

Then apply the percentage with integer ceiling division:

`fare = (subtotal × surgePercentage + 99) / 100`

All fare values use `long`.

`estimateFare` reads the zone's current percentage. `completeTrip` uses the percentage stored inside the trip, so the final fare is unaffected by later pricing changes.

## 6. Make Payments Idempotent

Two pieces of data are needed:

- Each trip maps a payment request identifier to the result originally returned for it.
- A global map records which trip owns each payment request identifier.

Payment processing follows this order:

1. Reject a missing or non-completed trip.
2. Reject an identifier already owned by another trip.
3. Return a cached result when the same identifier is repeated for the same trip.
4. If payment is already terminal, return and cache the terminal status without increasing the attempt count.
5. Otherwise, record one new attempt and update the payment status.

The cached result check happens before reading the current overall payment status. This is why retrying an earlier failed request still returns `RETRY_PENDING`, even if a later request has already succeeded.

## 7. Why the Main Invariants Hold

A rider and driver receive an active trip identifier at the same time the trip is created. Those identifiers are cleared only when the trip becomes terminal. Therefore, neither participant can enter a second nonterminal trip.

The matching set contains only available and unassigned drivers. Assigning a driver removes it, while completion and cancellation insert it again. Therefore, a reserved driver cannot be selected by another request.

Every state-changing method checks the one allowed source state. Therefore, calls cannot skip or reverse lifecycle steps.

Each trip stores its surge percentage at creation. Therefore, fare completion always uses the required pricing snapshot.

Each accepted payment request identifier is associated with one trip and one cached response. Therefore, retries do not create extra attempts and cross-trip reuse is rejected.

## 8. Java Solution

```java
import java.util.*;

public class RideHailingSystem {
    private static final int DEFAULT_SURGE_PERCENTAGE = 100;
    private static final String LOWEST_ID = "";
    private static final String HIGHEST_ID = "\uffff";

    private enum TripStatus {
        REQUESTED,
        ACCEPTED,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }

    private enum PaymentStatus {
        NOT_STARTED,
        PENDING,
        RETRY_PENDING,
        SUCCEEDED,
        FAILED
    }

    private static class Rider {
        private final String id;
        private final String name;
        private String activeTripId;

        private Rider(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class Driver {
        private final String id;
        private final String name;
        private int locationX;
        private int locationY;
        private boolean available = true;
        private String activeTripId;

        private Driver(String id, String name, int locationX, int locationY) {
            this.id = id;
            this.name = name;
            this.locationX = locationX;
            this.locationY = locationY;
        }
    }

    private static class DriverPosition {
        private final int locationX;
        private final String driverId;

        private DriverPosition(int locationX, String driverId) {
            this.locationX = locationX;
            this.driverId = driverId;
        }
    }

    private static class Trip {
        private final String id;
        private final String riderId;
        private final String driverId;
        private final int pickupX;
        private final int pickupY;
        private final int destinationX;
        private final int destinationY;
        private final String zoneId;
        private final String paymentMethodId;
        private final int surgePercentage;
        private final Map<String, String> paymentResults = new HashMap<>();

        private TripStatus status = TripStatus.REQUESTED;
        private PaymentStatus paymentStatus = PaymentStatus.NOT_STARTED;
        private long fareInCents;
        private int paymentAttempts;

        private Trip(
                String id,
                String riderId,
                String driverId,
                int pickupX,
                int pickupY,
                int destinationX,
                int destinationY,
                String zoneId,
                String paymentMethodId,
                int surgePercentage) {
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
    }

    private final long baseFareInCents;
    private final long farePerDistanceUnitInCents;
    private final int maxPaymentAttempts;

    private final Map<String, Rider> riders = new HashMap<>();
    private final Map<String, Driver> drivers = new HashMap<>();
    private final Map<String, Trip> trips = new HashMap<>();
    private final Map<String, Integer> surgeByZone = new HashMap<>();
    private final Map<String, String> paymentRequestOwners = new HashMap<>();

    // Only available and unassigned drivers are kept in this index.
    private final TreeSet<DriverPosition> availableDriversByX = new TreeSet<>(
            Comparator.comparingInt((DriverPosition position) -> position.locationX)
                    .thenComparing(position -> position.driverId));

    public RideHailingSystem(
            long baseFareInCents,
            long farePerDistanceUnitInCents,
            int maxPaymentAttempts,
            List<String> drivers) {
        this.baseFareInCents = baseFareInCents;
        this.farePerDistanceUnitInCents = farePerDistanceUnitInCents;
        this.maxPaymentAttempts = maxPaymentAttempts;

        for (String driverData : drivers) {
            String[] parts = driverData.split(",", -1);
            Driver driver = new Driver(
                    parts[0],
                    parts[1],
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
            this.drivers.put(driver.id, driver);
            addToAvailableIndex(driver);
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

        boolean indexed = isMatchable(driver);
        if (indexed) {
            removeFromAvailableIndex(driver);
        }

        driver.locationX = locationX;
        driver.locationY = locationY;

        if (indexed) {
            addToAvailableIndex(driver);
        }
        return true;
    }

    public boolean setDriverAvailability(String driverId, boolean available) {
        Driver driver = drivers.get(driverId);
        if (driver == null || driver.activeTripId != null) {
            return false;
        }

        if (driver.available == available) {
            return true;
        }

        if (driver.available) {
            removeFromAvailableIndex(driver);
        }
        driver.available = available;
        if (driver.available) {
            addToAvailableIndex(driver);
        }
        return true;
    }

    public int updateSurgePricing(String zoneId, int activeRequests, int availableDrivers) {
        int surgePercentage;
        if (activeRequests == 0) {
            surgePercentage = 100;
        } else if (availableDrivers == 0) {
            surgePercentage = 300;
        } else if (activeRequests <= availableDrivers) {
            surgePercentage = 100;
        } else if (activeRequests <= 2 * availableDrivers) {
            surgePercentage = 150;
        } else {
            surgePercentage = 200;
        }

        surgeByZone.put(zoneId, surgePercentage);
        return surgePercentage;
    }

    public long estimateFare(String zoneId, int estimatedDistanceUnits) {
        int surgePercentage = surgeByZone.getOrDefault(zoneId, DEFAULT_SURGE_PERCENTAGE);
        return calculateFare(estimatedDistanceUnits, surgePercentage);
    }

    public String requestTrip(
            String tripId,
            String riderId,
            int pickupX,
            int pickupY,
            int destinationX,
            int destinationY,
            String zoneId,
            String paymentMethodId) {
        Rider rider = riders.get(riderId);
        if (trips.containsKey(tripId)
                || rider == null
                || rider.activeTripId != null
                || availableDriversByX.isEmpty()) {
            return "";
        }

        Driver driver = findNearestAvailableDriver(pickupX, pickupY);
        if (driver == null) {
            return "";
        }

        int surgePercentage = surgeByZone.getOrDefault(zoneId, DEFAULT_SURGE_PERCENTAGE);
        Trip trip = new Trip(
                tripId,
                riderId,
                driver.id,
                pickupX,
                pickupY,
                destinationX,
                destinationY,
                zoneId,
                paymentMethodId,
                surgePercentage);

        trips.put(tripId, trip);
        rider.activeTripId = tripId;
        driver.activeTripId = tripId;
        driver.available = false;
        removeFromAvailableIndex(driver);
        return driver.id;
    }

    public boolean acceptTrip(String tripId, String driverId) {
        Trip trip = trips.get(tripId);
        if (trip == null
                || trip.status != TripStatus.REQUESTED
                || !trip.driverId.equals(driverId)) {
            return false;
        }

        trip.status = TripStatus.ACCEPTED;
        return true;
    }

    public boolean startTrip(String tripId, String driverId) {
        Trip trip = trips.get(tripId);
        if (trip == null
                || trip.status != TripStatus.ACCEPTED
                || !trip.driverId.equals(driverId)) {
            return false;
        }

        trip.status = TripStatus.IN_PROGRESS;
        return true;
    }

    public long completeTrip(String tripId, String driverId, int actualDistanceUnits) {
        Trip trip = trips.get(tripId);
        if (trip == null
                || trip.status != TripStatus.IN_PROGRESS
                || !trip.driverId.equals(driverId)) {
            return -1;
        }

        trip.fareInCents = calculateFare(actualDistanceUnits, trip.surgePercentage);
        trip.status = TripStatus.COMPLETED;
        trip.paymentStatus = PaymentStatus.PENDING;

        Rider rider = riders.get(trip.riderId);
        rider.activeTripId = null;

        Driver driver = drivers.get(trip.driverId);
        driver.activeTripId = null;
        driver.locationX = trip.destinationX;
        driver.locationY = trip.destinationY;
        driver.available = true;
        addToAvailableIndex(driver);
        return trip.fareInCents;
    }

    public boolean cancelTrip(String tripId, String requesterId) {
        Trip trip = trips.get(tripId);
        if (trip == null
                || isTerminal(trip.status)
                || (!trip.riderId.equals(requesterId) && !trip.driverId.equals(requesterId))) {
            return false;
        }

        trip.status = TripStatus.CANCELLED;
        trip.fareInCents = 0;

        Rider rider = riders.get(trip.riderId);
        rider.activeTripId = null;

        Driver driver = drivers.get(trip.driverId);
        driver.activeTripId = null;
        driver.available = true;
        addToAvailableIndex(driver);
        return true;
    }

    public String processPayment(
            String tripId,
            String paymentRequestId,
            boolean gatewaySuccessful) {
        Trip trip = trips.get(tripId);
        if (trip == null || trip.status != TripStatus.COMPLETED) {
            return "INVALID_REQUEST";
        }

        String ownerTripId = paymentRequestOwners.get(paymentRequestId);
        if (ownerTripId != null && !ownerTripId.equals(tripId)) {
            return "INVALID_REQUEST";
        }

        String previousResult = trip.paymentResults.get(paymentRequestId);
        if (previousResult != null) {
            // Idempotency returns the first result even if the gateway flag changed.
            return previousResult;
        }

        if (trip.paymentStatus == PaymentStatus.SUCCEEDED
                || trip.paymentStatus == PaymentStatus.FAILED) {
            String result = trip.paymentStatus.name();
            paymentRequestOwners.put(paymentRequestId, tripId);
            trip.paymentResults.put(paymentRequestId, result);
            return result;
        }

        paymentRequestOwners.put(paymentRequestId, tripId);
        trip.paymentAttempts++;

        String result;
        if (gatewaySuccessful) {
            trip.paymentStatus = PaymentStatus.SUCCEEDED;
            result = "SUCCEEDED";
        } else if (trip.paymentAttempts >= maxPaymentAttempts) {
            trip.paymentStatus = PaymentStatus.FAILED;
            result = "FAILED";
        } else {
            trip.paymentStatus = PaymentStatus.RETRY_PENDING;
            result = "RETRY_PENDING";
        }

        trip.paymentResults.put(paymentRequestId, result);
        return result;
    }

    public String getTripDetails(String tripId) {
        Trip trip = trips.get(tripId);
        if (trip == null) {
            return "";
        }

        return String.join(",",
                trip.id,
                trip.riderId,
                trip.driverId,
                trip.status.name(),
                String.valueOf(trip.pickupX),
                String.valueOf(trip.pickupY),
                String.valueOf(trip.destinationX),
                String.valueOf(trip.destinationY),
                trip.zoneId,
                String.valueOf(trip.surgePercentage),
                String.valueOf(trip.fareInCents),
                trip.paymentStatus.name(),
                String.valueOf(trip.paymentAttempts));
    }

    private Driver findNearestAvailableDriver(int pickupX, int pickupY) {
        // Start with a driver immediately to the left or right on the x-axis.
        DriverPosition pickup = new DriverPosition(pickupX, LOWEST_ID);
        DriverPosition right = availableDriversByX.ceiling(pickup);
        DriverPosition left = availableDriversByX.lower(pickup);

        Driver bestDriver = null;
        long bestDistance = Long.MAX_VALUE;

        if (right != null) {
            bestDriver = drivers.get(right.driverId);
            bestDistance = squaredDistance(bestDriver, pickupX, pickupY);
        }
        if (left != null) {
            Driver leftDriver = drivers.get(left.driverId);
            long leftDistance = squaredDistance(leftDriver, pickupX, pickupY);
            if (isBetter(leftDriver, leftDistance, bestDriver, bestDistance)) {
                bestDriver = leftDriver;
                bestDistance = leftDistance;
            }
        }

        long radius = floorSquareRoot(bestDistance);
        int minimumX = clampToInt((long) pickupX - radius);
        int maximumX = clampToInt((long) pickupX + radius);

        // A driver outside this x-range cannot have a smaller 2-D distance.
        DriverPosition from = new DriverPosition(minimumX, LOWEST_ID);
        DriverPosition to = new DriverPosition(maximumX, HIGHEST_ID);
        for (DriverPosition position : availableDriversByX.subSet(from, true, to, true)) {
            Driver driver = drivers.get(position.driverId);
            long distance = squaredDistance(driver, pickupX, pickupY);
            if (isBetter(driver, distance, bestDriver, bestDistance)) {
                bestDriver = driver;
                bestDistance = distance;
            }
        }
        return bestDriver;
    }

    private boolean isBetter(
            Driver candidate,
            long candidateDistance,
            Driver current,
            long currentDistance) {
        return current == null
                || candidateDistance < currentDistance
                || (candidateDistance == currentDistance
                        && candidate.id.compareTo(current.id) < 0);
    }

    private long squaredDistance(Driver driver, int pickupX, int pickupY) {
        long deltaX = (long) driver.locationX - pickupX;
        long deltaY = (long) driver.locationY - pickupY;
        return deltaX * deltaX + deltaY * deltaY;
    }

    private long floorSquareRoot(long value) {
        long root = (long) Math.sqrt(value);
        while ((root + 1) * (root + 1) <= value) {
            root++;
        }
        while (root * root > value) {
            root--;
        }
        return root;
    }

    private int clampToInt(long value) {
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private long calculateFare(int distanceUnits, int surgePercentage) {
        long subtotal = baseFareInCents + (long) distanceUnits * farePerDistanceUnitInCents;
        return (subtotal * surgePercentage + 99) / 100;
    }

    private boolean isTerminal(TripStatus status) {
        return status == TripStatus.COMPLETED || status == TripStatus.CANCELLED;
    }

    private boolean isMatchable(Driver driver) {
        return driver.available && driver.activeTripId == null;
    }

    private void addToAvailableIndex(Driver driver) {
        availableDriversByX.add(new DriverPosition(driver.locationX, driver.id));
    }

    private void removeFromAvailableIndex(Driver driver) {
        availableDriversByX.remove(new DriverPosition(driver.locationX, driver.id));
    }
}
```

## 9. Complexity Analysis

Let `D` be the number of drivers, `A` the number of currently available drivers, and `K` the number of available drivers inside the x-coordinate range examined by a request.

- Construction takes `O(D log D)` time because each driver is inserted into the sorted set.
- Adding a rider takes `O(1)` average time.
- Updating the location or availability of a free driver takes `O(log A)` time.
- Requesting a trip takes `O(log A + K)` time. Its worst case is `O(A)`.
- Completing or cancelling a trip takes `O(log A)` time because the driver returns to the index.
- Surge updates, fare estimates, lifecycle transitions, payment calls, and trip lookup take `O(1)` average time.
- Total space is `O(D + R + T + P + Z)` for drivers, riders, trips, payment request records, and zones.

