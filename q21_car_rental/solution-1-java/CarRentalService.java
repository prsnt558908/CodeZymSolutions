import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * In-memory Car Rental System with "early return frees remaining days" rule.
 *
 * Availability semantics:
 * - If a trip ends EARLY (actualEndDate < tillDate), the car is occupied through actualEndDate
 *   (inclusive) and becomes available from the NEXT day.
 * - If a trip ends LATE (actualEndDate > tillDate), the car remains occupied through actualEndDate
 *   (inclusive) — new bookings overlapping that extension must be rejected.
 *
 * Cost still uses: effectiveEndDate = max(tillDate, endDate).
 */
public class CarRentalService {

    // --- Entities ---

    private static final class Car {
        final String licensePlate;
        final int costPerDay;
        final int freeKmsPerDay;
        final int costPerKm;

        Car(String licensePlate, int costPerDay, int freeKmsPerDay, int costPerKm) {
            this.licensePlate = licensePlate;
            this.costPerDay = costPerDay;
            this.freeKmsPerDay = freeKmsPerDay;
            this.costPerKm = costPerKm;
        }
    }

    private static final class Booking {
        final String orderId;
        final String carPlate;
        final LocalDate fromDate;
        final LocalDate tillDate;

        Integer startOdometer;      // set at startTrip
        LocalDate actualEndDate;    // set at endTrip (input endDate)
        Integer finalOdometer;      // set at endTrip
        Integer totalCost;          // cached after first endTrip computation (idempotent)

        Booking(String orderId, String carPlate, LocalDate fromDate, LocalDate tillDate) {
            this.orderId = orderId;
            this.carPlate = carPlate;
            this.fromDate = fromDate;
            this.tillDate = tillDate;
        }
    }

    // --- Storage ---

    private final Map<String, Car> cars = new HashMap<>();
    private final Map<String, Booking> bookingsByOrder = new HashMap<>();
    private final Map<String, List<Booking>> bookingsByCar = new HashMap<>();

    public CarRentalService() { }

    // --- API Methods ---

    public void addCar(String licensePlate, int costPerDay, int freeKmsPerDay, int costPerKm) {
        if (licensePlate == null || licensePlate.isBlank()) return;
        if (costPerDay < 0 || freeKmsPerDay < 0 || costPerKm < 0) return;
        cars.putIfAbsent(licensePlate, new Car(licensePlate, costPerDay, freeKmsPerDay, costPerKm));
    }

    public boolean bookCar(String orderId, String carLicensePlate, String fromDate, String tillDate) {
        if (orderId == null || orderId.isBlank()) return false;
        if (carLicensePlate == null || carLicensePlate.isBlank()) return false;
        if (!cars.containsKey(carLicensePlate)) return false;
        if (bookingsByOrder.containsKey(orderId)) return false;

        LocalDate from = LocalDate.parse(fromDate);
        LocalDate till = LocalDate.parse(tillDate);
        if (till.isBefore(from)) return false; // fromDate ≤ tillDate

        // Overlap check (inclusive) with existing bookings for this car.
        // We consider the occupied range as [b.fromDate .. occupiedEndDate(b)].
        List<Booking> existing = bookingsByCar.getOrDefault(carLicensePlate, Collections.emptyList());
        for (Booking b : existing) {
            LocalDate occupiedEnd = occupiedEndDate(b);
            if (overlapsInclusive(from, till, b.fromDate, occupiedEnd)) {
                return false;
            }
        }

        Booking booking = new Booking(orderId, carLicensePlate, from, till);
        bookingsByOrder.put(orderId, booking);
        bookingsByCar.computeIfAbsent(carLicensePlate, k -> new ArrayList<>()).add(booking);
        return true;
    }

    public void startTrip(String orderId, int odometerReading) {
        Booking b = bookingsByOrder.get(orderId);
        if (b == null) return;
        b.startOdometer = odometerReading;
    }

    public int endTrip(String orderId, int finalOdometerReading, String endDate) {
        Booking b = bookingsByOrder.get(orderId);
        if (b == null) return -1;

        if (b.totalCost != null) {
            return b.totalCost;
        }

        Car car = cars.get(b.carPlate);
        LocalDate providedEnd = LocalDate.parse(endDate);
        LocalDate effectiveEnd = providedEnd.isAfter(b.tillDate) ? providedEnd : b.tillDate;

        int days = (int) (ChronoUnit.DAYS.between(b.fromDate, effectiveEnd) + 1); // inclusive
        int startOdo = (b.startOdometer != null) ? b.startOdometer : finalOdometerReading;
        int tripKms = Math.max(0, finalOdometerReading - startOdo);

        int freeAllowance = days * car.freeKmsPerDay;
        int extraKms = Math.max(0, tripKms - freeAllowance);

        int total = days * car.costPerDay + extraKms * car.costPerKm;

        // Persist trip closure details
        b.actualEndDate = providedEnd;   // availability derives from this (occupied through this date inclusive)
        b.finalOdometer = finalOdometerReading;
        b.totalCost = total;

        return total;
    }

    // --- Helpers ---

    /**
     * The car is considered occupied through the ACTUAL end date inclusive if the trip has ended,
     * otherwise through the originally booked tillDate.
     * - Early end (actualEndDate < tillDate): occupiedEnd = actualEndDate (free from the next day)
     * - Late end  (actualEndDate > tillDate): occupiedEnd = actualEndDate (block new bookings inside extension)
     * - Not ended: occupiedEnd = tillDate
     */
    private static LocalDate occupiedEndDate(Booking b) {
        return (b.actualEndDate != null) ? b.actualEndDate : b.tillDate;
    }

    // Inclusive overlap: [aStart..aEnd] overlaps [bStart..bEnd] iff aStart ≤ bEnd and bStart ≤ aEnd
    private static boolean overlapsInclusive(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        return !aEnd.isBefore(bStart) && !bEnd.isBefore(aStart);
    }
}
