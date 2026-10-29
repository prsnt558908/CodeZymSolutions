# Design Doctor Appointment Booking App like Practo in Java

#### Problem Statement
[https://codezym.com/question/78-design-practo-doctor-appointment-booking-app](https://codezym.com/question/78-design-practo-doctor-appointment-booking-app)


Think of every doctor as having a small calendar for the day. Each hour the doctor offers is a slot. A slot is either free or held by one confirmed booking, and it can have a line of patients waiting behind it.


Once we see it like this, most methods become a quick lookup in the right map. Booking checks if the slot is free. Cancelling frees the slot and hands it to the first patient in the line. A simple queue per slot is that line.


For design patterns, we use the **Strategy pattern** in one place only: ranking the free slots. Today the rule is "earlier start time first, then doctor name". Tomorrow it could be "best rated doctor first". Strategy lets us swap that rule without touching the booking code.


Everything else is plain classes (`Doctor`, `Slot`, `Booking`) and a few maps. State and Observer look tempting for this problem, but here they would only add classes without solving a real problem. We will see why near the end.


## Rules That Shape the Design

- Every slot is 60 minutes long, so a start time like `09:00` is enough to identify a slot of a doctor.
- One slot of a doctor can have only one confirmed booking.
- A patient who already has a confirmed booking at some start time can't book another one at that time.
- If a slot is taken, the patient can join its waitlist. Each doctor + start time has its own waitlist, first come first served.
- Cancelling a confirmed booking gives the slot to the first booking in that waitlist.


## A Simple First Attempt

The most direct idea is to keep all doctors in one list and every booking ever made in another list. Each booking has a status: `CONFIRMED`, `WAITLISTED` or `CANCELLED`.

```java
List<Doctor> doctors = new ArrayList<>();
List<Booking> bookings = new ArrayList<>(); // every booking ever made, oldest first
```

To know if a slot is taken, we scan all bookings.

```java
boolean isSlotBooked(String doctorName, String startTime) {
    for (Booking b : bookings) {
        if (b.status.equals("CONFIRMED") && b.doctorName.equals(doctorName)
                && b.startTime.equals(startTime)) {
            return true;
        }
    }
    return false;
}
```

The waitlist order comes from the list order. The oldest `WAITLISTED` booking for a doctor and time appears first in the list. So when a confirmed booking is cancelled, we promote the first match we find.

```java
// the oldest waiting booking for the same doctor and time gets the slot
for (Booking b : bookings) {
    if (b.status.equals("WAITLISTED") && b.doctorName.equals(cancelled.doctorName)
            && b.startTime.equals(cancelled.startTime)) {
        b.status = "CONFIRMED";
        messages.add("Booking confirmed for Booking id: " + b.bookingId);
        break;
    }
}
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

Since a start time identifies a slot, a `Doctor` can keep three small maps, all keyed by start time.

```java
// start time -> slot offered today
Map<String, Slot> slots = new HashMap<>();
// start time -> confirmed booking holding that slot
Map<String, Booking> bookedSlots = new HashMap<>();
// start time -> bookings waiting for that slot, oldest first
Map<String, Queue<Booking>> waitlists = new HashMap<>();
```

- `slots` answers "does this doctor offer 09:00?" in O(1).
- `bookedSlots` answers "is 09:00 free?" in O(1). Freeing a slot is a single `remove`.
- `waitlists` keeps a real **queue** for each slot. `add` puts a booking at the back and `poll` gives the oldest one. That is exactly the first come first served rule.


A `Slot` stores its start time (the key), the full text like `09:00-10:00` and the doctor's name. Ranking and the output line both use them.


`bookedSlots` and `waitlists` are kept apart from `slots` on purpose. When a doctor registers again, only `slots` and the speciality are replaced. Bookings made earlier stay as they are.


### Step 2: Use maps for everything we look up by name or id

```java
// doctor name -> doctor
Map<String, Doctor> doctors = new HashMap<>();
// speciality -> names of doctors with that speciality
Map<String, Set<String>> doctorsBySpeciality = new HashMap<>();
// booking id -> booking, only for bookings that are confirmed or waitlisted
Map<String, Booking> bookings = new HashMap<>();
// patient id -> confirmed bookings of that patient
Map<String, List<Booking>> patientBookings = new HashMap<>();
```

- `bookings` is needed because `cancelBooking` only gives us an id. A cancelled booking is removed from this map, so cancelling it again returns `Invalid booking id`.
- `patientBookings` lets us check a patient's clash and list their appointments without looking at other patients. A patient has only a few bookings in a day, so a list is enough.
- `doctorsBySpeciality` lets availability look only at doctors of the asked speciality. When a doctor registers again with a new speciality, their name moves to the new group.


A `Booking` stores who booked which doctor at what time, and whether it is `CONFIRMED` or `WAITLISTED`. A small enum is enough for the status.


### Step 3: Rank free slots with the Strategy pattern

The problem asks us to *rank* free slots: earlier start time first, then doctor name. In a real app, ranking is the rule most likely to change (rating, fees, distance). So we put it behind a small interface.

```java
/** Decides the order in which free slots are shown to patients. */
interface SlotRankingStrategy {
    void rank(List<Slot> freeSlots);
}

/** Default ranking: earlier start time first, then doctor name in alphabetical order. */
class StartTimeRankingStrategy implements SlotRankingStrategy {
    public void rank(List<Slot> freeSlots) {
        freeSlots.sort((a, b) -> {
            if (!a.startTime.equals(b.startTime)) {
                return a.startTime.compareTo(b.startTime);
            }
            return a.doctorName.compareTo(b.doctorName);
        });
    }
}
```

`StartTimeRankingStrategy` sorts by start time and then by doctor name. Times are in `HH:MM` format, so comparing them as strings gives the right order: `"09:00"` comes before `"10:00"`.


A new ranking rule later is just a new class. `DoctorAppointmentSystem` only calls `rankingStrategy.rank(freeSlots)`, so its booking logic stays the same. We only swap the object stored in `rankingStrategy`.


### Putting It Together

- **bookAppointment**: if the doctor is unknown or does not offer that time, return `Slot not available`. If the patient already has a confirmed booking at that time, return `Slot already booked`. If the slot is free, confirm it. Otherwise join the queue when `addToWaitlistIfBooked` is true, else return `Slot already booked`.
- **cancelBooking**: a waitlisted booking just leaves its queue. A confirmed booking frees its slot, and the first booking in the queue gets it.
- **showAvailabilityBySpeciality**: collect the slots not in `bookedSlots` for doctors of that speciality, rank them and format them.
- **showAppointmentsBooked**: if the name belongs to a doctor, use their `bookedSlots`. Otherwise treat it as a patient id and use `patientBookings`. Return them earliest first.


The clash check runs before the free slot check. So a patient who is already busy at 10:00 can't even join a waitlist for 10:00.


Here is the waitlist flow from the problem's third example:

| Call | What happens | Output |
|---|---|---|
| `bookAppointment("b2", "PatientB", "Curious", "12:00", false)` | 12:00 is free, `b2` takes it | `BOOKED` |
| `bookAppointment("b3", "PatientC", "Curious", "12:00", true)` | 12:00 is taken, `b3` joins the queue | `Added to the waitlist` |
| `cancelBooking("b2")` | 12:00 is freed, `poll()` returns `b3` and it is confirmed | `Booking Cancelled`, `Booking confirmed for Booking id: b3` |


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

```java
import java.util.*;

/** A booking either holds a slot or waits in the queue for it. */
enum BookingStatus {
    CONFIRMED,
    WAITLISTED
}

/** One booking made by a patient for one slot of a doctor. */
class Booking {
    String bookingId;
    String patientId;
    String doctorName;
    String startTime;
    BookingStatus status;

    Booking(String bookingId, String patientId, String doctorName, String startTime) {
        this.bookingId = bookingId;
        this.patientId = patientId;
        this.doctorName = doctorName;
        this.startTime = startTime;
    }
}

/** A 60 minute slot offered by a doctor, like "09:00-10:00". */
class Slot {
    String doctorName;
    String startTime; // "09:00", used as the key of the slot
    String timing;    // "09:00-10:00", used in the output

    Slot(String doctorName, String timing) {
        this.doctorName = doctorName;
        this.timing = timing;
        this.startTime = timing.split("-")[0];
    }
}

/** A doctor with the slots they offer, the slots already booked and a waitlist for each slot. */
class Doctor {
    String name;
    String speciality;
    // start time -> slot offered today
    Map<String, Slot> slots = new HashMap<>();
    // start time -> confirmed booking holding that slot
    Map<String, Booking> bookedSlots = new HashMap<>();
    // start time -> bookings waiting for that slot, oldest first
    Map<String, Queue<Booking>> waitlists = new HashMap<>();

    Doctor(String name) {
        this.name = name;
    }

    /** Overwrites speciality and slots. Bookings and waitlists made earlier are kept. */
    void update(String speciality, List<String> timings) {
        this.speciality = speciality;
        slots = new HashMap<>();
        for (String timing : timings) {
            Slot slot = new Slot(name, timing);
            slots.put(slot.startTime, slot);
        }
    }

    boolean isBooked(String startTime) {
        return bookedSlots.containsKey(startTime);
    }

    Queue<Booking> getWaitlist(String startTime) {
        return waitlists.computeIfAbsent(startTime, time -> new LinkedList<>());
    }
}

/** Decides the order in which free slots are shown to patients. */
interface SlotRankingStrategy {
    void rank(List<Slot> freeSlots);
}

/** Default ranking: earlier start time first, then doctor name in alphabetical order. */
class StartTimeRankingStrategy implements SlotRankingStrategy {
    public void rank(List<Slot> freeSlots) {
        freeSlots.sort((a, b) -> {
            if (!a.startTime.equals(b.startTime)) {
                return a.startTime.compareTo(b.startTime);
            }
            return a.doctorName.compareTo(b.doctorName);
        });
    }
}

public class DoctorAppointmentSystem {
    // doctor name -> doctor
    Map<String, Doctor> doctors = new HashMap<>();
    // speciality -> names of doctors with that speciality
    Map<String, Set<String>> doctorsBySpeciality = new HashMap<>();
    // booking id -> booking, only for bookings that are confirmed or waitlisted
    Map<String, Booking> bookings = new HashMap<>();
    // patient id -> confirmed bookings of that patient
    Map<String, List<Booking>> patientBookings = new HashMap<>();
    // replace this object to rank free slots in a different way
    SlotRankingStrategy rankingStrategy = new StartTimeRankingStrategy();

    public void registerDoctor(String doctorName, String speciality, List<String> slots) {
        Doctor doctor = doctors.get(doctorName);
        if (doctor == null) {
            doctor = new Doctor(doctorName);
            doctors.put(doctorName, doctor);
        } else {
            // speciality may change, so take the doctor out of the old group first
            doctorsBySpeciality.get(doctor.speciality).remove(doctorName);
        }
        doctor.update(speciality, slots);
        doctorsBySpeciality.computeIfAbsent(speciality, key -> new HashSet<>()).add(doctorName);
    }

    public String bookAppointment(String bookingId, String patientId, String doctorName,
                                  String startTime, boolean addToWaitlistIfBooked) {
        Doctor doctor = doctors.get(doctorName);
        // unknown doctor, or a slot the doctor never offered
        if (doctor == null || !doctor.slots.containsKey(startTime)) {
            return "Slot not available";
        }
        // a patient can't see two doctors at the same time
        if (hasConfirmedBookingAt(patientId, startTime)) {
            return "Slot already booked";
        }

        Booking booking = new Booking(bookingId, patientId, doctorName, startTime);
        if (!doctor.isBooked(startTime)) {
            confirm(doctor, booking);
            bookings.put(bookingId, booking);
            return "BOOKED";
        }
        if (!addToWaitlistIfBooked) {
            return "Slot already booked";
        }
        booking.status = BookingStatus.WAITLISTED;
        doctor.getWaitlist(startTime).add(booking);
        bookings.put(bookingId, booking);
        return "Added to the waitlist";
    }

    public List<String> showAvailabilityBySpeciality(String speciality) {
        List<Slot> freeSlots = new ArrayList<>();
        for (String doctorName : doctorsBySpeciality.getOrDefault(speciality, new HashSet<>())) {
            Doctor doctor = doctors.get(doctorName);
            for (Slot slot : doctor.slots.values()) {
                if (!doctor.isBooked(slot.startTime)) {
                    freeSlots.add(slot);
                }
            }
        }
        rankingStrategy.rank(freeSlots);

        List<String> result = new ArrayList<>();
        for (Slot slot : freeSlots) {
            result.add("Dr." + slot.doctorName + ": (" + slot.timing + ")");
        }
        return result;
    }

    public List<String> cancelBooking(String bookingId) {
        List<String> messages = new ArrayList<>();
        Booking booking = bookings.remove(bookingId);
        if (booking == null) {
            messages.add("Invalid booking id");
            return messages;
        }
        messages.add("Booking Cancelled");
        Doctor doctor = doctors.get(booking.doctorName);
        Queue<Booking> waitlist = doctor.getWaitlist(booking.startTime);

        if (booking.status == BookingStatus.WAITLISTED) {
            // it never held the slot, so it only leaves the queue
            waitlist.remove(booking);
            return messages;
        }

        // free the slot
        doctor.bookedSlots.remove(booking.startTime);
        patientBookings.get(booking.patientId).remove(booking);

        // the first booking in the waitlist gets the free slot
        Booking next = waitlist.poll();
        if (next != null) {
            confirm(doctor, next);
            messages.add("Booking confirmed for Booking id: " + next.bookingId);
        }
        return messages;
    }

    public List<String> showAppointmentsBooked(String userName) {
        Doctor doctor = doctors.get(userName);
        List<Booking> confirmed = new ArrayList<>();
        if (doctor != null) {
            confirmed.addAll(doctor.bookedSlots.values());
        } else {
            confirmed.addAll(patientBookings.getOrDefault(userName, new ArrayList<>()));
        }
        // earliest appointment first
        confirmed.sort((a, b) -> a.startTime.compareTo(b.startTime));

        List<String> result = new ArrayList<>();
        for (Booking b : confirmed) {
            // a doctor sees the patient, a patient sees the doctor
            String other = (doctor != null) ? b.patientId : "Dr " + b.doctorName;
            result.add("Booking id: " + b.bookingId + ", " + other + " " + b.startTime);
        }
        return result;
    }

    /** Puts the booking in its slot and in the patient's list of appointments. */
    void confirm(Doctor doctor, Booking booking) {
        booking.status = BookingStatus.CONFIRMED;
        doctor.bookedSlots.put(booking.startTime, booking);
        patientBookings.computeIfAbsent(booking.patientId, key -> new ArrayList<>()).add(booking);
    }

    boolean hasConfirmedBookingAt(String patientId, String startTime) {
        for (Booking b : patientBookings.getOrDefault(patientId, new ArrayList<>())) {
            if (b.startTime.equals(startTime)) {
                return true;
            }
        }
        return false;
    }
}
```