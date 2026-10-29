# Design Gym Fitness Center Management and Slot Booking System in Java

#### Problem Statement
[https://codezym.com/question/75-design-gym-fitness-center-management](https://codezym.com/question/75-design-gym-fitness-center-management)


We are building **GetInShape**, the backend of a gym booking app. Each center is open in a few time windows. An admin adds workout slots inside those windows, and users book seats, cancel them, or wait for a full slot to free up.

The core idea is simple: **every slot is an object that owns its own data**. It knows its seats, who booked it and who is waiting for it. A map takes us straight to any slot, so booking, cancelling and notifying only touch that one object.

For design patterns, the "notify me" feature is a natural fit for the **Observer** pattern, and we use it in its simplest form. Everything else is plain classes and maps. Strategy and Factory also look tempting here, but they would only add classes without making the code clearer.


## Key Rules

A slot is identified by four values: `centerName`, `workoutType`, `startTime` and `endTime`.

`addWorkoutSlot()` returns `true` only when all of these hold:

- the center exists, `startTime < endTime` and `totalSeats > 0`
- the workout type is allowed in that center
- the slot fits fully inside **one** opening window of the center
- it does not overlap another slot of the same center (`6-7` and `7-8` only touch, so both are allowed)

Both views return one line per slot in the format `"centerName|workoutType|startTime|endTime|seatsAvailable"`. Full slots are listed too, with `0` seats.

- **By start time:** `""` or `"*"` as the center name means all centers.
- **By seats available:** a real center name is needed, otherwise the result is empty.
- **Ties:** in both views, ties are broken by comparing the full lines as text. Text is compared character by character, so with equal seats, a line with `18|19` comes before a line with `8|9`.

When two answers could apply, the order of checks decides:

- `bookSession()`: `SLOT_NOT_FOUND`, then `ALREADY_BOOKED`, then `NO_SEATS`. A user who already holds a seat in a full slot gets `ALREADY_BOOKED`.
- `addToInterestList()`: `SLOT_NOT_FOUND`, then `SEATS_AVAILABLE`, then `ALREADY_INTERESTED`.
- `notifyInterestedUsers()` returns one `NOTIFY` line per waiting user, in the order they joined. Then it empties the list, because everyone has been told.


## Solution 1: Simple Approach with One List

Let's start with the most direct idea: keep **all slots of all centers in one list**. Each slot stores a seat counter, a list of booked users and a list of waiting users.

To find a slot, we scan the whole list.

```java
WorkoutSlot findSlot(String centerName, String workoutType, int startTime, int endTime) {
    for (WorkoutSlot slot : allSlots) {
        if (slot.centerName.equals(centerName) && slot.workoutType.equals(workoutType)
                && slot.startTime == startTime && slot.endTime == endTime) {
            return slot;
        }
    }
    return null;
}
```

Booking is that scan plus a few checks.

```java
public String bookSession(String userId, String centerName, String workoutType, int startTime, int endTime) {
    WorkoutSlot slot = findSlot(centerName, workoutType, startTime, endTime);
    if (slot == null) {
        return "SLOT_NOT_FOUND";
    }
    if (slot.bookedUsers.contains(userId)) { // bookedUsers is a List here
        return "ALREADY_BOOKED";
    }
    if (slot.seatsLeft == 0) {
        return "NO_SEATS";
    }
    slot.bookedUsers.add(userId);
    slot.seatsLeft--;
    return "BOOKED";
}
```

The overlap check and both views also loop over `allSlots` and skip the slots of other centers or other workouts.

This works, but it has clear problems:

- **Every call scans every slot.** A booking at one center walks through the slots of all centers.
- **`List.contains()` is a scan too.** "Already booked?" and "already interested?" check users one by one.
- **Seats are stored twice.** `seatsLeft` and `bookedUsers` must always change together. Forgetting one of them is an easy bug.


## Solution 2: Maps and Small Classes

### Idea

We keep the slot object, but we remove the scans. Each frequent question gets a data structure that answers it directly.

- `Map<String, Center> centers` finds a center by its name.
- `Map<String, WorkoutSlot> slots` finds a slot in O(1). The key is `"centerName|workoutType|startTime|endTime"`.
- `Map<String, List<WorkoutSlot>> slotsByWorkout` keeps the slots of one workout type from every center. The "all centers" view reads this list instead of scanning everything.
- `bookedUsers`, a `HashSet` inside the slot, gives an O(1) "already booked?" check.
- `interestedUsers`, a `LinkedHashSet` inside the slot, gives an O(1) "already interested?" check, and it also remembers the order in which users joined.

We no longer store a seat counter. Seats available is always `totalSeats - bookedUsers.size()`, so it can never get out of sync.


### Which design pattern fits?

**Observer, in its simplest form.** "Tell me when a seat frees up" is exactly what Observer is about: users subscribe to an event, and all of them are told when it happens. Here the slot is the subject and `interestedUsers` is its list of subscribers.

A full Observer would add an interface like `SlotObserver` and a class for each kind of subscriber. We don't need that yet. Every subscriber is just a user id, and every notification is the same log line. If email or SMS alerts come later, that is the right time to add the interface.

**Strategy, not needed.** The two views differ only in how they sort, so a `SortStrategy` class for each order may look neat. But a `Comparator` lambda already is a tiny strategy, and two short lambdas are easier to read than two extra classes.

**Factory, not needed.** "New workouts may be added later" can suggest one class per workout and a factory to create them. But all workouts behave the same, only the name differs. Keeping the workout type as a plain string means a new workout needs no code change at all.


### Main classes

**`GetInShape`** is the entry point. It owns the three maps. Each method finds the right center or slot, then lets that object do the work.

**`Center`** keeps the opening windows, the allowed workouts and its own slots. It answers the two questions asked before a slot is added: "is the center open then?" and "is that time already taken?". A center has only a handful of slots in a day, so a simple loop over its own list is enough.

**`WorkoutSlot`** owns its bookings and its interest list. Booking, cancelling and notifying change only this object.


### How it works

**Adding a slot.** We run the checks from the Key Rules section. The only tricky one is overlap: two slots overlap when `newStart < oldEnd` and `oldStart < newEnd`. If all checks pass, we save the slot in the center's list and in both maps.

**Viewing.** We collect the matching slots and sort them with a comparator: start time (or seats) first, then the full line. Seat counts change on every booking, so we sort when asked. A sorted structure would need an update on every single booking.

**Booking and cancelling.** One map lookup finds the slot. `Set.add()` and `Set.remove()` do the rest. `remove()` returns `false` when the user had no booking, which gives us `BOOKING_NOT_FOUND` for free.

**Notify me.** Here is the flow from Example 5, for the `connaught_place` yoga slot `18-19` with 1 seat:

1. `arjun` books it, so 0 seats are left.
2. `rohit` tries to book and gets `NO_SEATS`.
3. `rohit` joins the interest list and gets `INTEREST_ADDED`.
4. `arjun` cancels, so 1 seat is free again.
5. `notifyInterestedUsers()` returns `["NOTIFY|rohit|connaught_place|yoga|18-19"]` and the list becomes empty.


### Code

```java
import java.util.*;

/**
 * Entry point of the gym booking platform.
 * It finds the right center or slot using maps,
 * then lets the Center and WorkoutSlot objects do the actual work.
 */
public class GetInShape {
    // centerName -> center
    Map<String, Center> centers;
    // "centerName|workoutType|startTime|endTime" -> slot
    // book, cancel, interest and notify all find their slot here in O(1)
    Map<String, WorkoutSlot> slots;
    // workoutType -> slots of that type from all centers, used when centerName is "*"
    Map<String, List<WorkoutSlot>> slotsByWorkout;

    public GetInShape() {
        centers = new HashMap<>();
        slots = new HashMap<>();
        slotsByWorkout = new HashMap<>();
    }

    public void onboardCenter(String centerName, List<String> centerTimings, List<String> workoutTypes) {
        // centerName is a unique id, so a repeated onboarding is ignored
        if (centers.containsKey(centerName)) {
            return;
        }
        centers.put(centerName, new Center(centerName, centerTimings, workoutTypes));
    }

    public boolean addWorkoutSlot(String centerName, String workoutType, int startTime, int endTime, int totalSeats) {
        Center center = centers.get(centerName);
        if (center == null || startTime >= endTime || totalSeats <= 0) {
            return false;
        }
        if (!center.workoutTypes.contains(workoutType)) {
            return false;
        }
        if (!center.isOpen(startTime, endTime) || center.hasOverlap(startTime, endTime)) {
            return false;
        }
        WorkoutSlot slot = new WorkoutSlot(centerName, workoutType, startTime, endTime, totalSeats);
        center.slots.add(slot);
        slots.put(slotKey(centerName, workoutType, startTime, endTime), slot);
        slotsByWorkout.computeIfAbsent(workoutType, k -> new ArrayList<>()).add(slot);
        return true;
    }

    public List<String> viewWorkoutAvailabilityByStartTime(String workoutType, String centerName) {
        List<WorkoutSlot> result = new ArrayList<>();
        if (isAllCenters(centerName)) {
            result.addAll(slotsByWorkout.getOrDefault(workoutType, new ArrayList<>()));
        } else if (centers.containsKey(centerName)) {
            result.addAll(centers.get(centerName).getSlots(workoutType));
        }
        // smaller start time first, ties are broken by the full slot text
        result.sort((a, b) -> a.startTime != b.startTime
                ? Integer.compare(a.startTime, b.startTime)
                : a.toString().compareTo(b.toString()));
        return toLines(result);
    }

    public List<String> viewWorkoutAvailabilityBySeatsAvailable(String workoutType, String centerName) {
        List<WorkoutSlot> result = new ArrayList<>();
        // this view needs one real center, "" and "*" are not allowed
        if (!isAllCenters(centerName) && centers.containsKey(centerName)) {
            result.addAll(centers.get(centerName).getSlots(workoutType));
        }
        // fewer free seats first, ties are broken by the full slot text
        result.sort((a, b) -> a.seatsAvailable() != b.seatsAvailable()
                ? Integer.compare(a.seatsAvailable(), b.seatsAvailable())
                : a.toString().compareTo(b.toString()));
        return toLines(result);
    }

    public String bookSession(String userId, String centerName, String workoutType, int startTime, int endTime) {
        WorkoutSlot slot = slots.get(slotKey(centerName, workoutType, startTime, endTime));
        if (slot == null) {
            return "SLOT_NOT_FOUND";
        }
        if (slot.bookedUsers.contains(userId)) {
            return "ALREADY_BOOKED";
        }
        if (slot.seatsAvailable() == 0) {
            return "NO_SEATS";
        }
        slot.bookedUsers.add(userId);
        return "BOOKED";
    }

    public String cancelSession(String userId, String centerName, String workoutType, int startTime, int endTime) {
        WorkoutSlot slot = slots.get(slotKey(centerName, workoutType, startTime, endTime));
        if (slot == null) {
            return "SLOT_NOT_FOUND";
        }
        // remove() returns false when the user had no booking
        if (!slot.bookedUsers.remove(userId)) {
            return "BOOKING_NOT_FOUND";
        }
        return "CANCELLED";
    }

    public String addToInterestList(String userId, String centerName, String workoutType, int startTime, int endTime) {
        WorkoutSlot slot = slots.get(slotKey(centerName, workoutType, startTime, endTime));
        if (slot == null) {
            return "SLOT_NOT_FOUND";
        }
        if (slot.seatsAvailable() > 0) {
            return "SEATS_AVAILABLE";
        }
        // add() returns false when the user is already in the set
        if (!slot.interestedUsers.add(userId)) {
            return "ALREADY_INTERESTED";
        }
        return "INTEREST_ADDED";
    }

    public List<String> notifyInterestedUsers(String centerName, String workoutType, int startTime, int endTime) {
        WorkoutSlot slot = slots.get(slotKey(centerName, workoutType, startTime, endTime));
        if (slot == null) {
            return new ArrayList<>();
        }
        return slot.notifyInterestedUsers();
    }

    String slotKey(String centerName, String workoutType, int startTime, int endTime) {
        return centerName + "|" + workoutType + "|" + startTime + "|" + endTime;
    }

    boolean isAllCenters(String centerName) {
        return centerName == null || centerName.isEmpty() || centerName.equals("*");
    }

    List<String> toLines(List<WorkoutSlot> slotList) {
        List<String> lines = new ArrayList<>();
        for (WorkoutSlot slot : slotList) {
            lines.add(slot.toString());
        }
        return lines;
    }
}

/**
 * A gym center: its opening hours, allowed workouts and its slots for the day.
 * It answers the two questions asked while adding a slot:
 * "is the center open then?" and "is that time already taken?"
 */
class Center {
    String name;
    // opening windows, "6-9" is stored as {6, 9}
    List<int[]> timings = new ArrayList<>();
    // a set gives an O(1) "is this workout allowed here?" check
    Set<String> workoutTypes = new HashSet<>();
    // all slots of this center for the day
    List<WorkoutSlot> slots = new ArrayList<>();

    Center(String name, List<String> centerTimings, List<String> workoutTypes) {
        this.name = name;
        for (String timing : centerTimings) {
            String[] parts = timing.split("-");
            timings.add(new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())});
        }
        this.workoutTypes.addAll(workoutTypes);
    }

    /** True if the time fits fully inside one opening window. */
    boolean isOpen(int startTime, int endTime) {
        for (int[] window : timings) {
            if (window[0] <= startTime && endTime <= window[1]) {
                return true;
            }
        }
        return false;
    }

    /** True if the time shares any part with an existing slot. Touching slots like 6-7 and 7-8 are fine. */
    boolean hasOverlap(int startTime, int endTime) {
        for (WorkoutSlot slot : slots) {
            if (startTime < slot.endTime && slot.startTime < endTime) {
                return true;
            }
        }
        return false;
    }

    List<WorkoutSlot> getSlots(String workoutType) {
        List<WorkoutSlot> result = new ArrayList<>();
        for (WorkoutSlot slot : slots) {
            if (slot.workoutType.equals(workoutType)) {
                result.add(slot);
            }
        }
        return result;
    }
}

/**
 * One workout slot of a center.
 * It owns its bookings and its interest list,
 * so a booking or a cancellation touches only this object.
 */
class WorkoutSlot {
    String centerName;
    String workoutType;
    int startTime;
    int endTime;
    int totalSeats;
    // users holding a seat, a set makes the "already booked?" check O(1)
    Set<String> bookedUsers = new HashSet<>();
    // users waiting for a free seat, kept in the order they joined
    Set<String> interestedUsers = new LinkedHashSet<>();

    WorkoutSlot(String centerName, String workoutType, int startTime, int endTime, int totalSeats) {
        this.centerName = centerName;
        this.workoutType = workoutType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalSeats = totalSeats;
    }

    int seatsAvailable() {
        return totalSeats - bookedUsers.size();
    }

    /** One log line for every interested user. The list is then cleared because everyone has been told. */
    List<String> notifyInterestedUsers() {
        List<String> logs = new ArrayList<>();
        for (String userId : interestedUsers) {
            logs.add("NOTIFY|" + userId + "|" + centerName + "|" + workoutType + "|" + startTime + "-" + endTime);
        }
        interestedUsers.clear();
        return logs;
    }

    /** The output format: "centerName|workoutType|startTime|endTime|seatsAvailable" */
    @Override
    public String toString() {
        return centerName + "|" + workoutType + "|" + startTime + "|" + endTime + "|" + seatsAvailable();
    }
}
```


### Complexity

Let `T` be the number of opening windows of a center, `S` the number of slots in one center, `K` the number of slots a view returns, and `I` the number of users in an interest list.

| Method | Time |
|---|---|
| `onboardCenter` | O(T + workout types) |
| `addWorkoutSlot` | O(T + S) |
| `bookSession`, `cancelSession`, `addToInterestList` | O(1) on average |
| `notifyInterestedUsers` | O(I) |
| `viewWorkoutAvailabilityByStartTime` | O(K log K), plus O(S) when one center is given |
| `viewWorkoutAvailabilityBySeatsAvailable` | O(S + K log K) |

Space is O(slots + bookings + interested users).


### Supporting More Days

Right now everything happens on a single day. To support more days, add a `date` field to `WorkoutSlot`, put it in the slot key, and compare only slots of the same date in the overlap check. The rest of the logic stays the same.