# Design Doctor Appointment Booking App like Practo in Python

#### Problem Statement
[https://codezym.com/question/78-design-practo-doctor-appointment-booking-app](https://codezym.com/question/78-design-practo-doctor-appointment-booking-app)


Think of every doctor as having a small calendar for the day. Each hour the doctor offers is a slot. A slot is either free or held by one confirmed booking, and it can have a line of patients waiting behind it.


Once we see it like this, most methods become a quick lookup in the right dictionary. Booking checks if the slot is free. Cancelling frees the slot and hands it to the first patient in the line. A simple queue per slot is that line.


For design patterns, we use the **Strategy pattern** in one place only: ranking the free slots. Today the rule is "earlier start time first, then doctor name". Tomorrow it could be "best rated doctor first". Strategy lets us swap that rule without touching the booking code.


Everything else is plain classes (`Doctor`, `Slot`, `Booking`) and a few dictionaries. State and Observer look tempting for this problem, but here they would only add classes without solving a real problem. We will see why near the end.


## Rules That Shape the Design

- Every slot is 60 minutes long, so a start time like `09:00` is enough to identify a slot of a doctor.
- One slot of a doctor can have only one confirmed booking.
- A patient who already has a confirmed booking at some start time can't book another one at that time.
- If a slot is taken, the patient can join its waitlist. Each doctor + start time has its own waitlist, first come first served.
- Cancelling a confirmed booking gives the slot to the first booking in that waitlist.


## A Simple First Attempt

The most direct idea is to keep all doctors in one list and every booking ever made in another list. Each booking has a status: `CONFIRMED`, `WAITLISTED` or `CANCELLED`.

```python
self.doctors = []
self.bookings = []  # every booking ever made, oldest first
```

To know if a slot is taken, we scan all bookings.

```python
def is_slot_booked(self, doctor_name, start_time):
    for b in self.bookings:
        if (b.status == "CONFIRMED" and b.doctor_name == doctor_name
                and b.start_time == start_time):
            return True
    return False
```

The waitlist order comes from the list order. The oldest `WAITLISTED` booking for a doctor and time appears first in the list. So when a confirmed booking is cancelled, we promote the first match we find.

```python
# the oldest waiting booking for the same doctor and time gets the slot
for b in self.bookings:
    if (b.status == "WAITLISTED" and b.doctor_name == cancelled.doctor_name
            and b.start_time == cancelled.start_time):
        b.status = "CONFIRMED"
        messages.append("Booking confirmed for Booking id: " + b.booking_id)
        break
```

The other methods work the same way: loop over everything and filter.


### What is wrong with it?

It gives correct answers, but:

- **Everything is a full scan.** Finding a doctor, finding a booking by id and checking a patient's clash all walk through whole lists.
- **Availability is the slowest.** For each doctor and each slot, we scan all bookings again. That is doctors × slots × bookings work for a single call.
- **The list only grows.** Cancelled bookings stay in it forever, and every later scan pays for them.
- **There is no real waitlist.** It only exists as "WAITLISTED rows in creation order", which is hard to see and easy to break.


## Better Solution

### Step 1: Let each doctor own their slots

Since a start time identifies a slot, a `Doctor` can keep three small dictionaries, all keyed by start time.

```python
self.slots = {}         # start time -> Slot offered today
self.booked_slots = {}  # start time -> confirmed Booking holding that slot
self.waitlists = {}     # start time -> deque of Bookings waiting, oldest first
```

- `slots` answers "does this doctor offer 09:00?" in O(1).
- `booked_slots` answers "is 09:00 free?" in O(1). Freeing a slot is a single `del`.
- `waitlists` keeps a real **queue** (a `deque`) for each slot. `append` puts a booking at the back and `popleft` gives the oldest one in O(1). That is exactly the first come first served rule.


A `Slot` stores its start time (the key), the full text like `09:00-10:00` and the doctor's name. Ranking and the output line both use them.


`booked_slots` and `waitlists` are kept apart from `slots` on purpose. When a doctor registers again, only `slots` and the speciality are replaced. Bookings made earlier stay as they are.


### Step 2: Use dictionaries for everything we look up by name or id

```python
self.doctors = {}                # doctor name -> Doctor
self.doctors_by_speciality = {}  # speciality -> set of doctor names
self.bookings = {}               # booking id -> Booking, only confirmed or waitlisted ones
self.patient_bookings = {}       # patient id -> list of confirmed Bookings
```

- `bookings` is needed because `cancelBooking` only gives us an id. A cancelled booking is removed from this dictionary, so cancelling it again returns `Invalid booking id`.
- `patient_bookings` lets us check a patient's clash and list their appointments without looking at other patients. A patient has only a few bookings in a day, so a list is enough.
- `doctors_by_speciality` lets availability look only at doctors of the asked speciality. When a doctor registers again with a new speciality, their name moves to the new group.


A `Booking` stores who booked which doctor at what time, and whether it is `CONFIRMED` or `WAITLISTED`. A small `Enum` is enough for the status.


### Step 3: Rank free slots with the Strategy pattern

The problem asks us to *rank* free slots: earlier start time first, then doctor name. In a real app, ranking is the rule most likely to change (rating, fees, distance). So we put it behind a small base class.

```python
class SlotRankingStrategy:
    """Decides the order in which free slots are shown to patients."""

    def rank(self, free_slots):
        raise NotImplementedError


class StartTimeRankingStrategy(SlotRankingStrategy):
    """Default ranking: earlier start time first, then doctor name in alphabetical order."""

    def rank(self, free_slots):
        free_slots.sort(key=lambda slot: (slot.start_time, slot.doctor_name))
```

`StartTimeRankingStrategy` sorts by the pair (start time, doctor name). Times are in `HH:MM` format, so comparing them as strings gives the right order: `"09:00"` comes before `"10:00"`.


A new ranking rule later is just a new class. `DoctorAppointmentSystem` only calls `self.ranking_strategy.rank(free_slots)`, so its booking logic stays the same. We only swap the object stored in `ranking_strategy`.


### Putting It Together

- **bookAppointment**: if the doctor is unknown or does not offer that time, return `Slot not available`. If the patient already has a confirmed booking at that time, return `Slot already booked`. If the slot is free, confirm it. Otherwise join the queue when `addToWaitlistIfBooked` is `True`, else return `Slot already booked`.
- **cancelBooking**: a waitlisted booking just leaves its queue. A confirmed booking frees its slot, and the first booking in the queue gets it.
- **showAvailabilityBySpeciality**: collect the slots not in `booked_slots` for doctors of that speciality, rank them and format them.
- **showAppointmentsBooked**: if the name belongs to a doctor, use their `booked_slots`. Otherwise treat it as a patient id and use `patient_bookings`. Return them earliest first.


The clash check runs before the free slot check. So a patient who is already busy at 10:00 can't even join a waitlist for 10:00.


Here is the waitlist flow from the problem's third example:

| Call | What happens | Output |
|---|---|---|
| `bookAppointment("b2", "PatientB", "Curious", "12:00", False)` | 12:00 is free, `b2` takes it | `BOOKED` |
| `bookAppointment("b3", "PatientC", "Curious", "12:00", True)` | 12:00 is taken, `b3` joins the queue | `Added to the waitlist` |
| `cancelBooking("b2")` | 12:00 is freed, `popleft()` returns `b3` and it is confirmed | `Booking Cancelled`, `Booking confirmed for Booking id: b3` |


## Why Not State or Observer?

**State pattern:** a booking moves between confirmed and waitlisted, so State looks like a fit. But only `cancelBooking` behaves differently for the two, and that difference is one `if`. A class per state would add more code than it removes.


**Observer pattern:** when a waitlisted patient gets the slot, it sounds like we should "notify" them. But the problem only wants a message in the returned list. Nobody subscribes to anything, so an observer list would be unused code.


## Complexity

D = doctors, S = slots of one doctor, B = all bookings ever made, P = confirmed bookings of one patient, W = size of one waitlist, N = slots offered by doctors of one speciality, A = appointments returned.

| Method | First attempt | Final solution |
|---|---|---|
| `registerDoctor` | O(D) | O(S) |
| `bookAppointment` | O(D + S + B) | O(P) |
| `showAvailabilityBySpeciality` | O(D × S × B) | O(N log N) |
| `cancelBooking` | O(B) | O(W + P) |
| `showAppointmentsBooked` | O(D + B + A log A) | O(A log A) |


## Final Code

```python
from collections import deque
from enum import Enum
from typing import List


class BookingStatus(Enum):
    """A booking either holds a slot or waits in the queue for it."""
    CONFIRMED = 1
    WAITLISTED = 2


class Booking:
    """One booking made by a patient for one slot of a doctor."""

    def __init__(self, booking_id, patient_id, doctor_name, start_time):
        self.booking_id = booking_id
        self.patient_id = patient_id
        self.doctor_name = doctor_name
        self.start_time = start_time
        self.status = None


class Slot:
    """A 60 minute slot offered by a doctor, like "09:00-10:00"."""

    def __init__(self, doctor_name, timing):
        self.doctor_name = doctor_name
        self.timing = timing                    # "09:00-10:00", used in the output
        self.start_time = timing.split("-")[0]  # "09:00", used as the key of the slot


class Doctor:
    """A doctor with the slots they offer, the slots already booked and a waitlist for each slot."""

    def __init__(self, name):
        self.name = name
        self.speciality = None
        self.slots = {}         # start time -> Slot offered today
        self.booked_slots = {}  # start time -> confirmed Booking holding that slot
        self.waitlists = {}     # start time -> deque of Bookings waiting, oldest first

    def update(self, speciality, timings):
        """Overwrites speciality and slots. Bookings and waitlists made earlier are kept."""
        self.speciality = speciality
        self.slots = {}
        for timing in timings:
            slot = Slot(self.name, timing)
            self.slots[slot.start_time] = slot

    def is_booked(self, start_time):
        return start_time in self.booked_slots

    def get_waitlist(self, start_time):
        if start_time not in self.waitlists:
            self.waitlists[start_time] = deque()
        return self.waitlists[start_time]


class SlotRankingStrategy:
    """Decides the order in which free slots are shown to patients."""

    def rank(self, free_slots):
        raise NotImplementedError


class StartTimeRankingStrategy(SlotRankingStrategy):
    """Default ranking: earlier start time first, then doctor name in alphabetical order."""

    def rank(self, free_slots):
        free_slots.sort(key=lambda slot: (slot.start_time, slot.doctor_name))


class DoctorAppointmentSystem:
    def __init__(self):
        self.doctors = {}                # doctor name -> Doctor
        self.doctors_by_speciality = {}  # speciality -> set of doctor names
        self.bookings = {}               # booking id -> Booking, only confirmed or waitlisted ones
        self.patient_bookings = {}       # patient id -> list of confirmed Bookings
        # replace this object to rank free slots in a different way
        self.ranking_strategy = StartTimeRankingStrategy()

    def registerDoctor(self, doctorName: str, speciality: str, slots: List[str]) -> None:
        doctor = self.doctors.get(doctorName)
        if doctor is None:
            doctor = Doctor(doctorName)
            self.doctors[doctorName] = doctor
        else:
            # speciality may change, so take the doctor out of the old group first
            self.doctors_by_speciality[doctor.speciality].discard(doctorName)
        doctor.update(speciality, slots)
        self.doctors_by_speciality.setdefault(speciality, set()).add(doctorName)

    def bookAppointment(self, bookingId: str, patientId: str, doctorName: str,
                        startTime: str, addToWaitlistIfBooked: bool) -> str:
        doctor = self.doctors.get(doctorName)
        # unknown doctor, or a slot the doctor never offered
        if doctor is None or startTime not in doctor.slots:
            return "Slot not available"
        # a patient can't see two doctors at the same time
        if self.has_confirmed_booking_at(patientId, startTime):
            return "Slot already booked"

        booking = Booking(bookingId, patientId, doctorName, startTime)
        if not doctor.is_booked(startTime):
            self.confirm(doctor, booking)
            self.bookings[bookingId] = booking
            return "BOOKED"
        if not addToWaitlistIfBooked:
            return "Slot already booked"
        booking.status = BookingStatus.WAITLISTED
        doctor.get_waitlist(startTime).append(booking)
        self.bookings[bookingId] = booking
        return "Added to the waitlist"

    def showAvailabilityBySpeciality(self, speciality: str) -> List[str]:
        free_slots = []
        for doctor_name in self.doctors_by_speciality.get(speciality, set()):
            doctor = self.doctors[doctor_name]
            for slot in doctor.slots.values():
                if not doctor.is_booked(slot.start_time):
                    free_slots.append(slot)
        self.ranking_strategy.rank(free_slots)
        return [f"Dr.{slot.doctor_name}: ({slot.timing})" for slot in free_slots]

    def cancelBooking(self, bookingId: str) -> List[str]:
        booking = self.bookings.pop(bookingId, None)
        if booking is None:
            return ["Invalid booking id"]
        messages = ["Booking Cancelled"]
        doctor = self.doctors[booking.doctor_name]
        waitlist = doctor.get_waitlist(booking.start_time)

        if booking.status == BookingStatus.WAITLISTED:
            # it never held the slot, so it only leaves the queue
            waitlist.remove(booking)
            return messages

        # free the slot
        del doctor.booked_slots[booking.start_time]
        self.patient_bookings[booking.patient_id].remove(booking)

        # the first booking in the waitlist gets the free slot
        if waitlist:
            next_booking = waitlist.popleft()
            self.confirm(doctor, next_booking)
            messages.append(f"Booking confirmed for Booking id: {next_booking.booking_id}")
        return messages

    def showAppointmentsBooked(self, userName: str) -> List[str]:
        doctor = self.doctors.get(userName)
        if doctor is not None:
            confirmed = list(doctor.booked_slots.values())
        else:
            confirmed = list(self.patient_bookings.get(userName, []))
        # earliest appointment first
        confirmed.sort(key=lambda b: b.start_time)

        result = []
        for b in confirmed:
            # a doctor sees the patient, a patient sees the doctor
            other = b.patient_id if doctor is not None else "Dr " + b.doctor_name
            result.append(f"Booking id: {b.booking_id}, {other} {b.start_time}")
        return result

    def confirm(self, doctor, booking):
        """Puts the booking in its slot and in the patient's list of appointments."""
        booking.status = BookingStatus.CONFIRMED
        doctor.booked_slots[booking.start_time] = booking
        self.patient_bookings.setdefault(booking.patient_id, []).append(booking)

    def has_confirmed_booking_at(self, patient_id, start_time):
        for b in self.patient_bookings.get(patient_id, []):
            if b.start_time == start_time:
                return True
        return False
```