# Design a Car Rental System in Python

#### Problem Statement

[https://codezym.com/question/21-design-car-rental-system](https://codezym.com/question/21-design-car-rental-system)

The core idea is to keep cars and bookings as small data classes, while `CarRentalService` coordinates all operations. Two dictionaries give direct lookup by license plate and order ID. Each car keeps only its own booking list, so a new request checks that car instead of scanning every booking in the system. A small service layer with simple data classes is the right design here. A full State pattern may sound useful for trip stages, but it would add several classes for rules that only need a start reading, an end reading, and one availability update.

## 1. Start With a Simple Approach

The simplest solution is to keep one list containing every booking.

Whenever someone requests a car, we could scan the complete list, ignore bookings for other cars, and check the remaining date ranges for overlap. This produces the correct result, but work grows with every booking in the entire service.

We can improve it without introducing a complex data structure. Every `Car` stores its own `List[Booking]`. A booking request now scans only the bookings of the requested car.

We also keep two dictionaries.

- `cars` maps a license plate to its `Car`.
- `bookings_by_order_id` maps an order ID to its `Booking`.

These dictionaries make `addCar`, `startTrip`, and the booking lookup inside `endTrip` direct operations.

## 2. The Main Data Classes

### Car

A `Car` stores its daily price, daily free kilometer allowance, extra kilometer price, and booking list. The license plate does not need to be repeated inside the object because it is already the key in the `cars` dictionary.

The `default_factory=list` creates a separate empty booking list for every car. Without it, different car objects could accidentally share the same list.

### Booking

A `Booking` connects one car to one inclusive date range. It also stores the starting odometer reading.

The booking needs two different end dates.

- `booked_till_date` never changes. It is used to calculate the minimum bill promised by the original booking.
- `blocked_till_date` represents how long the car is currently unavailable. It starts as the booked end date and becomes the actual return date when the trip ends.

This difference is important. Suppose a car is booked from August 6 to August 12 but is returned on August 9. The customer is still charged through August 12, but the car becomes available again from August 10.

## 3. Reject Overlapping Bookings

Both date ranges are inclusive. Ranges `[A..B]` and `[C..D]` overlap when both of these conditions are true.

```text
A <= D
C <= B
```

Python date objects can be compared directly, so the overlap check becomes.

```text
first_start <= second_end and second_start <= first_end
```

This correctly rejects bookings that share even one boundary day.

## 4. Start and End a Trip

`startTrip` uses the order dictionary and stores the starting odometer reading in the booking. A rejected booking is not present in the dictionary, so an unknown order ID is safely ignored.

When the trip ends, an unknown order ID returns `-1`. For a valid order, billing uses the later of the booked end date and actual end date.

```text
charge_till_date = max(booked_till_date, actual_end_date)
days = (charge_till_date - from_date).days + 1
```

Adding one makes the range inclusive. A trip that starts and ends on the same day therefore costs one day.

The distance part of the bill is calculated as follows.

```text
trip_kms = final_odometer - start_odometer
free_kms = days * free_kms_per_day
extra_kms = max(0, trip_kms - free_kms)
```

The final cost is then.

```text
total_cost = days * cost_per_day + extra_kms * cost_per_km
```

After calculating the bill, `blocked_till_date` is changed to the actual return date. This handles both early and delayed returns. Because the end date is inclusive, another booking can begin only on the following day.

## 5. Why Simple Structures Are Enough

A sorted map or interval tree could make overlap queries faster for a very large production system. This problem keeps everything in memory and gives no large-scale requirement, so dictionaries plus one list per car are easier to understand and maintain.

Let `B` be the number of bookings already stored for the requested car.

- `addCar` takes `O(1)` average time.
- `bookCar` takes `O(B)` time because it checks that car's booking list.
- `startTrip` takes `O(1)` average time.
- `endTrip` takes `O(1)` average time.
- Total space is `O(C + N)` for `C` cars and `N` successful bookings.

## 6. Python Solution

```python
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from typing import Dict, List


@dataclass
class Car:
    cost_per_day: int
    free_kms_per_day: int
    cost_per_km: int
    bookings: List[Booking] = field(default_factory=list)


@dataclass
class Booking:
    car: Car
    from_date: date
    booked_till_date: date
    blocked_till_date: date
    start_odometer: int = 0


class CarRentalService:
    def __init__(self):
        self.cars: Dict[str, Car] = {}
        self.bookings_by_order_id: Dict[str, Booking] = {}

    def addCar(self, licensePlate, costPerDay, freeKmsPerDay, costPerKm):
        if (not licensePlate or costPerDay < 0
                or freeKmsPerDay < 0 or costPerKm < 0):
            return

        # setdefault keeps the original car when the plate already exists.
        self.cars.setdefault(
            licensePlate,
            Car(costPerDay, freeKmsPerDay, costPerKm)
        )

    def bookCar(self, orderId, carLicensePlate, fromDate, tillDate):
        if not orderId or orderId in self.bookings_by_order_id:
            return False

        car = self.cars.get(carLicensePlate)
        if car is None:
            return False

        start = date.fromisoformat(fromDate)
        end = date.fromisoformat(tillDate)
        if start > end:
            return False

        for existing in car.bookings:
            if self._overlaps(
                    start,
                    end,
                    existing.from_date,
                    existing.blocked_till_date):
                return False

        booking = Booking(car, start, end, end)
        car.bookings.append(booking)
        self.bookings_by_order_id[orderId] = booking
        return True

    def startTrip(self, orderId, odometerReading):
        booking = self.bookings_by_order_id.get(orderId)
        if booking is None:
            return

        booking.start_odometer = odometerReading

    def endTrip(self, orderId, finalOdometerReading, endDate):
        booking = self.bookings_by_order_id.get(orderId)
        if booking is None:
            return -1

        actual_end_date = date.fromisoformat(endDate)
        charge_till_date = max(actual_end_date, booking.booked_till_date)

        days = (charge_till_date - booking.from_date).days + 1
        trip_kms = finalOdometerReading - booking.start_odometer
        free_kms = days * booking.car.free_kms_per_day
        extra_kms = max(0, trip_kms - free_kms)

        total_cost = (
            days * booking.car.cost_per_day
            + extra_kms * booking.car.cost_per_km
        )

        # Availability follows the actual return date, even for an early return.
        booking.blocked_till_date = actual_end_date
        return total_cost

    @staticmethod
    def _overlaps(first_start, first_end, second_start, second_end):
        return first_start <= second_end and second_start <= first_end
```
