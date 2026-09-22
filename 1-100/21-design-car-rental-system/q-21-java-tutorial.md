

# Design a Car Rental System in Java

#### Problem Statement

[https://codezym.com/question/21-design-car-rental-system](https://codezym.com/question/21-design-car-rental-system)

The core idea is to keep cars and bookings as small objects, while `CarRentalService` coordinates all operations. Two hash maps give direct lookup by license plate and order ID. Each car keeps only its own booking list, so a new request checks that car instead of scanning every booking in the system. A small service layer with simple domain objects is the right design here. A full State pattern may sound useful for trip stages, but it would add several classes for rules that only need a start reading, an end reading, and one availability update.

## 1. Start With a Simple Approach

The simplest solution is to keep one list containing every booking.

Whenever someone requests a car, we could scan the complete list, ignore bookings for other cars, and check the remaining date ranges for overlap. This produces the correct result, but work grows with every booking in the entire service.

We can improve it without introducing a complex data structure. Every `Car` stores its own `ArrayList<Booking>`. A booking request now scans only the bookings of the requested car.

We also keep two maps.

- `cars` maps a license plate to its `Car`.
- `bookingsByOrderId` maps an order ID to its `Booking`.

These maps make `addCar`, `startTrip`, and the booking lookup inside `endTrip` direct operations.

## 2. The Main Classes

### Car

A `Car` stores its daily price, daily free kilometer allowance, extra kilometer price, and booking list. The license plate does not need to be repeated inside the object because it is already the key in the `cars` map.

### Booking

A `Booking` connects one car to one inclusive date range. It also stores the starting odometer reading.

The booking needs two different end dates.

- `bookedTillDate` never changes. It is used to calculate the minimum bill promised by the original booking.
- `blockedTillDate` represents how long the car is currently unavailable. It starts as the booked end date and becomes the actual return date when the trip ends.

This difference is important. Suppose a car is booked from August 6 to August 12 but is returned on August 9. The customer is still charged through August 12, but the car becomes available again from August 10.

## 3. Reject Overlapping Bookings

Both date ranges are inclusive. Ranges `[A..B]` and `[C..D]` overlap when both of these conditions are true.

```text
A <= D
C <= B
```

In Java, it is convenient to express the same rule by checking that neither range starts after the other range ends.

```java
!firstStart.isAfter(secondEnd) && !secondStart.isAfter(firstEnd)
```

This correctly rejects bookings that share even one boundary day.

## 4. Start and End a Trip

`startTrip` uses the order map and stores the starting odometer reading in the booking. A rejected booking is not present in the map, so an unknown order ID is safely ignored.

When the trip ends, an unknown order ID returns `-1`. For a valid order, billing uses the later of the booked end date and actual end date.

```text
chargeTillDate = max(bookedTillDate, actualEndDate)
days = daysBetween(fromDate, chargeTillDate) + 1
```

Adding one makes the range inclusive. A trip that starts and ends on the same day therefore costs one day.

The distance part of the bill is calculated as follows.

```text
tripKms = finalOdometer - startOdometer
freeKms = days * freeKmsPerDay
extraKms = max(0, tripKms - freeKms)
```

The final cost is then.

```text
totalCost = days * costPerDay + extraKms * costPerKm
```

After calculating the bill, `blockedTillDate` is changed to the actual return date. This handles both early and delayed returns. Because the end date is inclusive, another booking can begin only on the following day.

## 5. Why Simple Structures Are Enough

A sorted map or interval tree could make overlap queries faster for a very large production system. This problem keeps everything in memory and gives no large-scale requirement, so a hash map plus one list per car is easier to understand and maintain.

Let `B` be the number of bookings already stored for the requested car.

- `addCar` takes `O(1)` average time.
- `bookCar` takes `O(B)` time because it checks that car's booking list.
- `startTrip` takes `O(1)` average time.
- `endTrip` takes `O(1)` average time.
- Total space is `O(C + N)` for `C` cars and `N` successful bookings.

## 6. Java Solution

```java
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CarRentalService {

    private static class Car {
        private final int costPerDay;
        private final int freeKmsPerDay;
        private final int costPerKm;
        private final List<Booking> bookings = new ArrayList<>();

        private Car(int costPerDay, int freeKmsPerDay, int costPerKm) {
            this.costPerDay = costPerDay;
            this.freeKmsPerDay = freeKmsPerDay;
            this.costPerKm = costPerKm;
        }
    }

    private static class Booking {
        private final Car car;
        private final LocalDate fromDate;
        private final LocalDate bookedTillDate;
        private LocalDate blockedTillDate;
        private int startOdometer;

        private Booking(Car car, LocalDate fromDate, LocalDate tillDate) {
            this.car = car;
            this.fromDate = fromDate;
            this.bookedTillDate = tillDate;
            this.blockedTillDate = tillDate;
        }
    }

    private final Map<String, Car> cars;
    private final Map<String, Booking> bookingsByOrderId;

    public CarRentalService() {
        cars = new HashMap<>();
        bookingsByOrderId = new HashMap<>();
    }

    public void addCar(String licensePlate, int costPerDay, int freeKmsPerDay, int costPerKm) {
        if (licensePlate == null || licensePlate.isBlank()
                || costPerDay < 0 || freeKmsPerDay < 0 || costPerKm < 0) {
            return;
        }

        // putIfAbsent keeps the original car when the plate already exists.
        cars.putIfAbsent(licensePlate, new Car(costPerDay, freeKmsPerDay, costPerKm));
    }

    public boolean bookCar(String orderId, String carLicensePlate, String fromDate, String tillDate) {
        if (orderId == null || orderId.isBlank() || bookingsByOrderId.containsKey(orderId)) {
            return false;
        }

        Car car = cars.get(carLicensePlate);
        if (car == null) {
            return false;
        }

        LocalDate start = LocalDate.parse(fromDate);
        LocalDate end = LocalDate.parse(tillDate);
        if (start.isAfter(end)) {
            return false;
        }

        for (Booking existing : car.bookings) {
            if (overlaps(start, end, existing.fromDate, existing.blockedTillDate)) {
                return false;
            }
        }

        Booking booking = new Booking(car, start, end);
        car.bookings.add(booking);
        bookingsByOrderId.put(orderId, booking);
        return true;
    }

    public void startTrip(String orderId, int odometerReading) {
        Booking booking = bookingsByOrderId.get(orderId);
        if (booking == null) {
            return;
        }

        booking.startOdometer = odometerReading;
    }

    public int endTrip(String orderId, int finalOdometerReading, String endDate) {
        Booking booking = bookingsByOrderId.get(orderId);
        if (booking == null) {
            return -1;
        }

        LocalDate actualEndDate = LocalDate.parse(endDate);

        LocalDate chargeTillDate = actualEndDate.isAfter(booking.bookedTillDate)
                ? actualEndDate
                : booking.bookedTillDate;

        long days = ChronoUnit.DAYS.between(booking.fromDate, chargeTillDate) + 1;
        long tripKms = finalOdometerReading - booking.startOdometer;
        long freeKms = days * booking.car.freeKmsPerDay;
        long extraKms = Math.max(0L, tripKms - freeKms);

        long totalCost = days * booking.car.costPerDay
                + extraKms * booking.car.costPerKm;

        // Availability follows the actual return date, even for an early return.
        booking.blockedTillDate = actualEndDate;
        return (int) totalCost;
    }

    private boolean overlaps(LocalDate firstStart, LocalDate firstEnd,
                             LocalDate secondStart, LocalDate secondEnd) {
        return !firstStart.isAfter(secondEnd) && !secondStart.isAfter(firstEnd);
    }
}
```

