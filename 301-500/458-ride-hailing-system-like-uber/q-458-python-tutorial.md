
# Design Ride-Hailing System Like Uber, Ola in Python

#### Problem Statement

[https://codezym.com/question/458-ride-hailing-system-like-uber](https://codezym.com/question/458-ride-hailing-system-like-uber)

The core idea is to keep the system around three small dataclasses, `Rider`, `Driver`, and `Trip`. Dictionaries give direct access by identifier, an enum-backed state machine protects the trip lifecycle, and a list sorted by driver x-coordinate stores only drivers who can currently receive a request. Python's `bisect` module lets us search this list efficiently. A full State pattern with one class per state or Strategy classes for the single matching and pricing rules would add extra code without making this fixed problem easier to understand.

## 1. Start With a Simple Approach

The simplest correct matching method is to store every driver in a dictionary and scan all drivers whenever a rider requests a trip.

During the scan, we would ignore unavailable or assigned drivers, calculate the squared distance for every remaining driver, and keep the closest one. If two drivers have the same distance, we would keep the lexicographically smaller driver identifier.

This is easy to implement, but every trip request examines all available drivers. With many drivers and many requests, that repeated work can become expensive.

## 2. Improve Matching With a Sorted List

Python does not have a balanced sorted-set type in its standard library. We therefore keep every available and unassigned driver in a normal list of `DriverPosition` objects sorted by:

1. The driver's x-coordinate.
2. The driver's identifier.

The `bisect` module finds positions inside this list with binary search.

For a pickup point, we first inspect a driver immediately to the left or right on the x-axis. Suppose that driver's squared distance is `best_distance`. Any driver whose x-coordinate differs from the pickup by more than `isqrt(best_distance)` cannot be closer, even before considering its y-coordinate.

We use `bisect_left` and `bisect_right` to locate that x-coordinate range. For every candidate inside it, we still calculate the complete two-dimensional squared distance, so the result remains exact. Equal distances are resolved using the driver identifier.

This index improves normal trip requests. In the worst case, many drivers can share a narrow x-coordinate range, so one request can still examine every available driver.

Only matchable drivers belong in this list. A driver is removed when assigned or manually made unavailable. The driver is inserted again after completion, cancellation, or a later availability change.

Because this is a Python list, inserting or removing an item takes linear time due to element shifting. This is the main difference from the Java version's `TreeSet`.

## 3. Model the Important Data With Dataclasses

### Rider

A rider stores its identifier, name, and `active_trip_id`. A non-null active trip prevents the rider from creating another nonterminal trip.

### Driver

A driver stores its identifier, name, current coordinates, availability, and `active_trip_id`. The active trip also prevents availability from being changed while the driver is assigned.

### DriverPosition

`DriverPosition` is a frozen, ordered dataclass containing only the x-coordinate and driver identifier. The generated ordering lets `bisect` compare positions correctly.

Keeping position objects separate from mutable drivers is important. Changing a driver's location cannot silently damage the order of the list. The old position is removed before an available driver's coordinates are updated, and the new position is then inserted.

### Trip

A trip keeps all booking details and its changing state. It also stores the surge percentage that existed when the request was created. This snapshot is important because later surge updates must not change an existing trip's final fare.

The trip uses `field(default_factory=dict)` for payment results, giving every trip its own dictionary. That dictionary lets repeated payment requests return the exact original result.

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

`subtotal = base_fare_in_cents + distance_units × fare_per_distance_unit_in_cents`

Then apply the percentage with integer ceiling division:

`fare = (subtotal × surge_percentage + 99) // 100`

Python integers automatically grow when required, so the calculation does not overflow.

`estimateFare` reads the zone's current percentage. `completeTrip` uses the percentage stored inside the trip, so the final fare is unaffected by later pricing changes.

## 6. Make Payments Idempotent

Two pieces of data are needed:

- Each trip maps a payment request identifier to the result originally returned for it.
- A global dictionary records which trip owns each payment request identifier.

Payment processing follows this order:

1. Reject a missing or non-completed trip.
2. Reject an identifier already owned by another trip.
3. Return a cached result when the same identifier is repeated for the same trip.
4. If payment is already terminal, return and cache the terminal status without increasing the attempt count.
5. Otherwise, record one new attempt and update the payment status.

The cached result check happens before reading the current overall payment status. This is why retrying an earlier failed request still returns `RETRY_PENDING`, even if a later request has already succeeded.

## 7. Why the Main Invariants Hold

A rider and driver receive an active trip identifier at the same time the trip is created. Those identifiers are cleared only when the trip becomes terminal. Therefore, neither participant can enter a second nonterminal trip.

The matching list contains only available and unassigned drivers. Assigning a driver removes it, while completion and cancellation insert it again. Therefore, a reserved driver cannot be selected by another request.

Every state-changing method checks the one allowed source state. Therefore, calls cannot skip or reverse lifecycle steps.

Each trip stores its surge percentage at creation. Therefore, fare completion always uses the required pricing snapshot.

Each accepted payment request identifier is associated with one trip and one cached response. Therefore, retries do not create extra attempts and cross-trip reuse is rejected.

## 8. Python Solution

```python
from bisect import bisect_left, bisect_right, insort
from dataclasses import dataclass, field
from enum import Enum
from math import isqrt
from typing import Dict, List, Optional


class TripStatus(Enum):
    REQUESTED = "REQUESTED"
    ACCEPTED = "ACCEPTED"
    IN_PROGRESS = "IN_PROGRESS"
    COMPLETED = "COMPLETED"
    CANCELLED = "CANCELLED"


class PaymentStatus(Enum):
    NOT_STARTED = "NOT_STARTED"
    PENDING = "PENDING"
    RETRY_PENDING = "RETRY_PENDING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"


@dataclass
class Rider:
    id: str
    name: str
    active_trip_id: Optional[str] = None


@dataclass
class Driver:
    id: str
    name: str
    location_x: int
    location_y: int
    available: bool = True
    active_trip_id: Optional[str] = None


@dataclass(order=True, frozen=True)
class DriverPosition:
    location_x: int
    driver_id: str


@dataclass
class Trip:
    id: str
    rider_id: str
    driver_id: str
    pickup_x: int
    pickup_y: int
    destination_x: int
    destination_y: int
    zone_id: str
    payment_method_id: str
    surge_percentage: int
    payment_results: Dict[str, str] = field(default_factory=dict)
    status: TripStatus = TripStatus.REQUESTED
    payment_status: PaymentStatus = PaymentStatus.NOT_STARTED
    fare_in_cents: int = 0
    payment_attempts: int = 0


class RideHailingSystem:
    DEFAULT_SURGE_PERCENTAGE = 100
    LOWEST_ID = ""
    HIGHEST_ID = "\U0010ffff"

    def __init__(
        self,
        baseFareInCents,
        farePerDistanceUnitInCents,
        maxPaymentAttempts,
        drivers,
    ):
        self.base_fare_in_cents = baseFareInCents
        self.fare_per_distance_unit_in_cents = farePerDistanceUnitInCents
        self.max_payment_attempts = maxPaymentAttempts

        self.riders: Dict[str, Rider] = {}
        self.drivers: Dict[str, Driver] = {}
        self.trips: Dict[str, Trip] = {}
        self.surge_by_zone: Dict[str, int] = {}
        self.payment_request_owners: Dict[str, str] = {}

        # Only available and unassigned drivers are kept in this index.
        self.available_drivers_by_x: List[DriverPosition] = []

        for driver_data in drivers:
            driver_id, driver_name, location_x, location_y = driver_data.split(",")
            driver = Driver(
                id=driver_id,
                name=driver_name,
                location_x=int(location_x),
                location_y=int(location_y),
            )
            self.drivers[driver.id] = driver
            self.available_drivers_by_x.append(
                DriverPosition(driver.location_x, driver.id)
            )

        self.available_drivers_by_x.sort()

    def addRider(self, riderId, riderName):
        if riderId in self.riders:
            return False

        self.riders[riderId] = Rider(riderId, riderName)
        return True

    def updateDriverLocation(self, driverId, locationX, locationY):
        driver = self.drivers.get(driverId)
        if driver is None:
            return False

        indexed = self._is_matchable(driver)
        if indexed:
            self._remove_from_available_index(driver)

        driver.location_x = locationX
        driver.location_y = locationY

        if indexed:
            self._add_to_available_index(driver)
        return True

    def setDriverAvailability(self, driverId, available):
        driver = self.drivers.get(driverId)
        if driver is None or driver.active_trip_id is not None:
            return False

        if driver.available == available:
            return True

        if driver.available:
            self._remove_from_available_index(driver)
        driver.available = available
        if driver.available:
            self._add_to_available_index(driver)
        return True

    def updateSurgePricing(self, zoneId, activeRequests, availableDrivers):
        if activeRequests == 0:
            surge_percentage = 100
        elif availableDrivers == 0:
            surge_percentage = 300
        elif activeRequests <= availableDrivers:
            surge_percentage = 100
        elif activeRequests <= 2 * availableDrivers:
            surge_percentage = 150
        else:
            surge_percentage = 200

        self.surge_by_zone[zoneId] = surge_percentage
        return surge_percentage

    def estimateFare(self, zoneId, estimatedDistanceUnits):
        surge_percentage = self.surge_by_zone.get(
            zoneId,
            self.DEFAULT_SURGE_PERCENTAGE,
        )
        return self._calculate_fare(estimatedDistanceUnits, surge_percentage)

    def requestTrip(
        self,
        tripId,
        riderId,
        pickupX,
        pickupY,
        destinationX,
        destinationY,
        zoneId,
        paymentMethodId,
    ):
        rider = self.riders.get(riderId)
        if (
            tripId in self.trips
            or rider is None
            or rider.active_trip_id is not None
            or not self.available_drivers_by_x
        ):
            return ""

        driver = self._find_nearest_available_driver(pickupX, pickupY)
        if driver is None:
            return ""

        surge_percentage = self.surge_by_zone.get(
            zoneId,
            self.DEFAULT_SURGE_PERCENTAGE,
        )
        trip = Trip(
            id=tripId,
            rider_id=riderId,
            driver_id=driver.id,
            pickup_x=pickupX,
            pickup_y=pickupY,
            destination_x=destinationX,
            destination_y=destinationY,
            zone_id=zoneId,
            payment_method_id=paymentMethodId,
            surge_percentage=surge_percentage,
        )

        self.trips[tripId] = trip
        rider.active_trip_id = tripId
        driver.active_trip_id = tripId
        driver.available = False
        self._remove_from_available_index(driver)
        return driver.id

    def acceptTrip(self, tripId, driverId):
        trip = self.trips.get(tripId)
        if (
            trip is None
            or trip.status != TripStatus.REQUESTED
            or trip.driver_id != driverId
        ):
            return False

        trip.status = TripStatus.ACCEPTED
        return True

    def startTrip(self, tripId, driverId):
        trip = self.trips.get(tripId)
        if (
            trip is None
            or trip.status != TripStatus.ACCEPTED
            or trip.driver_id != driverId
        ):
            return False

        trip.status = TripStatus.IN_PROGRESS
        return True

    def completeTrip(self, tripId, driverId, actualDistanceUnits):
        trip = self.trips.get(tripId)
        if (
            trip is None
            or trip.status != TripStatus.IN_PROGRESS
            or trip.driver_id != driverId
        ):
            return -1

        trip.fare_in_cents = self._calculate_fare(
            actualDistanceUnits,
            trip.surge_percentage,
        )
        trip.status = TripStatus.COMPLETED
        trip.payment_status = PaymentStatus.PENDING

        rider = self.riders[trip.rider_id]
        rider.active_trip_id = None

        driver = self.drivers[trip.driver_id]
        driver.active_trip_id = None
        driver.location_x = trip.destination_x
        driver.location_y = trip.destination_y
        driver.available = True
        self._add_to_available_index(driver)
        return trip.fare_in_cents

    def cancelTrip(self, tripId, requesterId):
        trip = self.trips.get(tripId)
        if (
            trip is None
            or self._is_terminal(trip.status)
            or requesterId not in (trip.rider_id, trip.driver_id)
        ):
            return False

        trip.status = TripStatus.CANCELLED
        trip.fare_in_cents = 0

        rider = self.riders[trip.rider_id]
        rider.active_trip_id = None

        driver = self.drivers[trip.driver_id]
        driver.active_trip_id = None
        driver.available = True
        self._add_to_available_index(driver)
        return True

    def processPayment(self, tripId, paymentRequestId, gatewaySuccessful):
        trip = self.trips.get(tripId)
        if trip is None or trip.status != TripStatus.COMPLETED:
            return "INVALID_REQUEST"

        owner_trip_id = self.payment_request_owners.get(paymentRequestId)
        if owner_trip_id is not None and owner_trip_id != tripId:
            return "INVALID_REQUEST"

        previous_result = trip.payment_results.get(paymentRequestId)
        if previous_result is not None:
            # Idempotency returns the first result even if the gateway flag changed.
            return previous_result

        if trip.payment_status in (PaymentStatus.SUCCEEDED, PaymentStatus.FAILED):
            result = trip.payment_status.value
            self.payment_request_owners[paymentRequestId] = tripId
            trip.payment_results[paymentRequestId] = result
            return result

        self.payment_request_owners[paymentRequestId] = tripId
        trip.payment_attempts += 1

        if gatewaySuccessful:
            trip.payment_status = PaymentStatus.SUCCEEDED
            result = "SUCCEEDED"
        elif trip.payment_attempts >= self.max_payment_attempts:
            trip.payment_status = PaymentStatus.FAILED
            result = "FAILED"
        else:
            trip.payment_status = PaymentStatus.RETRY_PENDING
            result = "RETRY_PENDING"

        trip.payment_results[paymentRequestId] = result
        return result

    def getTripDetails(self, tripId):
        trip = self.trips.get(tripId)
        if trip is None:
            return ""

        return ",".join(
            [
                trip.id,
                trip.rider_id,
                trip.driver_id,
                trip.status.value,
                str(trip.pickup_x),
                str(trip.pickup_y),
                str(trip.destination_x),
                str(trip.destination_y),
                trip.zone_id,
                str(trip.surge_percentage),
                str(trip.fare_in_cents),
                trip.payment_status.value,
                str(trip.payment_attempts),
            ]
        )

    def _find_nearest_available_driver(self, pickup_x, pickup_y):
        # Start with a driver immediately to the left or right on the x-axis.
        pickup = DriverPosition(pickup_x, self.LOWEST_ID)
        right_index = bisect_left(self.available_drivers_by_x, pickup)

        best_driver = None
        best_distance = None

        if right_index < len(self.available_drivers_by_x):
            right = self.available_drivers_by_x[right_index]
            best_driver = self.drivers[right.driver_id]
            best_distance = self._squared_distance(best_driver, pickup_x, pickup_y)

        if right_index > 0:
            left = self.available_drivers_by_x[right_index - 1]
            left_driver = self.drivers[left.driver_id]
            left_distance = self._squared_distance(left_driver, pickup_x, pickup_y)
            if self._is_better(
                left_driver,
                left_distance,
                best_driver,
                best_distance,
            ):
                best_driver = left_driver
                best_distance = left_distance

        radius = isqrt(best_distance)
        minimum_x = pickup_x - radius
        maximum_x = pickup_x + radius

        # A driver outside this x-range cannot have a smaller 2-D distance.
        from_index = bisect_left(
            self.available_drivers_by_x,
            DriverPosition(minimum_x, self.LOWEST_ID),
        )
        to_index = bisect_right(
            self.available_drivers_by_x,
            DriverPosition(maximum_x, self.HIGHEST_ID),
        )

        for index in range(from_index, to_index):
            position = self.available_drivers_by_x[index]
            driver = self.drivers[position.driver_id]
            distance = self._squared_distance(driver, pickup_x, pickup_y)
            if self._is_better(driver, distance, best_driver, best_distance):
                best_driver = driver
                best_distance = distance

        return best_driver

    def _is_better(
        self,
        candidate,
        candidate_distance,
        current,
        current_distance,
    ):
        return (
            current is None
            or candidate_distance < current_distance
            or (
                candidate_distance == current_distance
                and candidate.id < current.id
            )
        )

    def _squared_distance(self, driver, pickup_x, pickup_y):
        delta_x = driver.location_x - pickup_x
        delta_y = driver.location_y - pickup_y
        return delta_x * delta_x + delta_y * delta_y

    def _calculate_fare(self, distance_units, surge_percentage):
        subtotal = (
            self.base_fare_in_cents
            + distance_units * self.fare_per_distance_unit_in_cents
        )
        return (subtotal * surge_percentage + 99) // 100

    def _is_terminal(self, status):
        return status in (TripStatus.COMPLETED, TripStatus.CANCELLED)

    def _is_matchable(self, driver):
        return driver.available and driver.active_trip_id is None

    def _add_to_available_index(self, driver):
        position = DriverPosition(driver.location_x, driver.id)
        insort(self.available_drivers_by_x, position)

    def _remove_from_available_index(self, driver):
        position = DriverPosition(driver.location_x, driver.id)
        index = bisect_left(self.available_drivers_by_x, position)
        if (
            index < len(self.available_drivers_by_x)
            and self.available_drivers_by_x[index] == position
        ):
            self.available_drivers_by_x.pop(index)
```

## 9. Complexity Analysis

Let `D` be the number of drivers, `A` the number of currently available drivers, and `K` the number of available drivers inside the x-coordinate range examined by a request.

- Construction takes `O(D log D)` time because all initial positions are sorted once.
- Adding a rider takes `O(1)` average time.
- Finding the candidate range for a request takes `O(log A)` time, and checking its drivers takes `O(K)` time.
- Removing the assigned driver from the sorted list takes `O(A)` time, so the complete `requestTrip` operation is `O(A + K)`, which is `O(A)` in the worst case.
- Updating the location or availability of a free driver takes `O(A)` time because list elements may need to shift.
- Completing or cancelling a trip takes `O(A)` time because the driver is inserted back into the sorted list.
- Surge updates, fare estimates, lifecycle checks, payment calls, and trip lookup take `O(1)` average time.
- Total space is `O(D + R + T + P + Z)` for drivers, riders, trips, payment request records, and zones.

