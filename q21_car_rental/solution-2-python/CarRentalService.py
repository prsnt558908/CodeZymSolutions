from dataclasses import dataclass
from datetime import date
from typing import Dict, List, Optional


@dataclass(frozen=True)
class Car:
    license_plate: str
    cost_per_day: int
    free_kms_per_day: int
    cost_per_km: int


@dataclass
class Booking:
    order_id: str
    car_plate: str
    from_date: date
    till_date: date

    # Filled later
    start_odometer: Optional[int] = None
    actual_end_date: Optional[date] = None
    final_odometer: Optional[int] = None
    total_cost: Optional[int] = None


class CarRentalService:
    """
    Availability semantics for booking overlap checks:
    - Before endTrip: occupied [from_date .. till_date] (inclusive).
    - Early end (actual_end_date < till_date): occupied through actual_end_date only
      (car becomes available starting the NEXT day).
    - Late end  (actual_end_date > till_date): occupied through actual_end_date.
    Trip cost uses: effective_end_date = max(till_date, provided end_date at endTrip).
    """

    def __init__(self):
        self._cars: Dict[str, Car] = {}
        self._bookings_by_order: Dict[str, Booking] = {}
        self._bookings_by_car: Dict[str, List[Booking]] = {}

    # --- Utilities ---

    @staticmethod
    def _to_int(x) -> Optional[int]:
        try:
            return int(x)
        except (TypeError, ValueError):
            return None

    # --- API ---

    def addCar(self, licensePlate: str, costPerDay, freeKmsPerDay, costPerKm) -> None:
        if not licensePlate or licensePlate.strip() == "":
            return
        cpd = self._to_int(costPerDay)
        fkp = self._to_int(freeKmsPerDay)
        cpk = self._to_int(costPerKm)
        if cpd is None or fkp is None or cpk is None:
            return
        if cpd < 0 or fkp < 0 or cpk < 0:
            return
        if licensePlate in self._cars:
            return
        self._cars[licensePlate] = Car(licensePlate, cpd, fkp, cpk)

    def bookCar(self, orderId: str, carLicensePlate: str, fromDate: str, tillDate: str) -> bool:
        if not orderId or orderId.strip() == "":
            return False
        if not carLicensePlate or carLicensePlate.strip() == "":
            return False
        if carLicensePlate not in self._cars:
            return False
        if orderId in self._bookings_by_order:
            return False

        try:
            from_d = date.fromisoformat(fromDate)
            till_d = date.fromisoformat(tillDate)
        except ValueError:
            return False
        if till_d < from_d:
            return False  # fromDate ≤ tillDate

        # Overlap check (inclusive) against existing bookings for this car
        for b in self._bookings_by_car.get(carLicensePlate, []):
            occ_end = self._occupied_end_date(b)
            if self._overlaps_inclusive(from_d, till_d, b.from_date, occ_end):
                return False

        booking = Booking(orderId, carLicensePlate, from_d, till_d)
        self._bookings_by_order[orderId] = booking
        self._bookings_by_car.setdefault(carLicensePlate, []).append(booking)
        return True

    def startTrip(self, orderId: str, odometerReading) -> None:
        b = self._bookings_by_order.get(orderId)
        if b is None:
            return
        odo = self._to_int(odometerReading)
        if odo is None:
            return
        b.start_odometer = odo

    def endTrip(self, orderId: str, finalOdometerReading, endDate: str) -> int:
        b = self._bookings_by_order.get(orderId)
        if b is None:
            return -1
        if b.total_cost is not None:
            return b.total_cost  # idempotent

        odo_final = self._to_int(finalOdometerReading)
        if odo_final is None:
            return -1
        try:
            provided_end = date.fromisoformat(endDate)
        except ValueError:
            return -1

        car = self._cars[b.car_plate]

        # Charge up to the later of the two dates
        effective_end = provided_end if provided_end > b.till_date else b.till_date

        # Inclusive day count
        days = (effective_end - b.from_date).days + 1

        start_odo = b.start_odometer if b.start_odometer is not None else odo_final
        trip_kms = max(0, odo_final - start_odo)

        free_allowance = days * car.free_kms_per_day
        extra_kms = max(0, trip_kms - free_allowance)

        total = days * car.cost_per_day + extra_kms * car.cost_per_km

        # Persist trip closure details for future availability checks
        b.actual_end_date = provided_end
        b.final_odometer = odo_final
        b.total_cost = total

        return total

    # --- Helpers ---

    def _occupied_end_date(self, b: Booking) -> date:
        """
        Occupied end date is:
        - actual_end_date if the trip has ended (early or late),
        - otherwise till_date.
        This enables early-release from the day AFTER actual_end_date,
        and blocks late returns through the extended date.
        """
        return b.actual_end_date if b.actual_end_date is not None else b.till_date

    @staticmethod
    def _overlaps_inclusive(a_start: date, a_end: date, b_start: date, b_end: date) -> bool:
        # [a_start..a_end] overlaps [b_start..b_end] iff a_start ≤ b_end and b_start ≤ a_end
        return not (a_end < b_start or b_end < a_start)
