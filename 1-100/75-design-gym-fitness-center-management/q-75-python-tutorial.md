# Design Gym Fitness Center Management and Slot Booking System in Python

#### Problem Statement
[https://codezym.com/question/75-design-gym-fitness-center-management](https://codezym.com/question/75-design-gym-fitness-center-management)


We are building **GetInShape**, the backend of a gym booking app. Each center is open in a few time windows. An admin adds workout slots inside those windows, and users book seats, cancel them, or wait for a full slot to free up.

The core idea is simple: **every slot is an object that owns its own data**. It knows its seats, who booked it and who is waiting for it. A dict takes us straight to any slot, so booking, cancelling and notifying only touch that one object.

For design patterns, the "notify me" feature is a natural fit for the **Observer** pattern, and we use it in its simplest form. Everything else is plain classes and dicts. Strategy and Factory also look tempting here, but they would only add classes without making the code clearer.


## Key Rules

A slot is identified by four values: `centerName`, `workoutType`, `startTime` and `endTime`.

`addWorkoutSlot()` returns `True` only when all of these hold:

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

```python
def find_slot(self, center_name, workout_type, start_time, end_time):
    for slot in self.all_slots:
        if (slot.center_name == center_name and slot.workout_type == workout_type
                and slot.start_time == start_time and slot.end_time == end_time):
            return slot
    return None
```

Booking is that scan plus a few checks.

```python
def bookSession(self, userId, centerName, workoutType, startTime, endTime):
    slot = self.find_slot(centerName, workoutType, startTime, endTime)
    if slot is None:
        return "SLOT_NOT_FOUND"
    if userId in slot.booked_users:  # booked_users is a list here
        return "ALREADY_BOOKED"
    if slot.seats_left == 0:
        return "NO_SEATS"
    slot.booked_users.append(userId)
    slot.seats_left -= 1
    return "BOOKED"
```

The overlap check and both views also loop over `all_slots` and skip the slots of other centers or other workouts.

This works, but it has clear problems:

- **Every call scans every slot.** A booking at one center walks through the slots of all centers.
- **`in` on a list is a scan too.** "Already booked?" and "already interested?" check users one by one.
- **Seats are stored twice.** `seats_left` and `booked_users` must always change together. Forgetting one of them is an easy bug.


## Solution 2: Dicts and Small Classes

### Idea

We keep the slot object, but we remove the scans. Each frequent question gets a data structure that answers it directly.

- `centers`, a dict from center name to `Center`, finds a center by its name.
- `slots`, a dict, finds a slot in O(1). The key is `"centerName|workoutType|startTime|endTime"`.
- `slots_by_workout`, a dict from workout type to a list of slots, keeps the slots of one workout type from every center. The "all centers" view reads this list instead of scanning everything.
- `booked_users`, a `set` inside the slot, gives an O(1) "already booked?" check.
- `interested_users`, a `dict` inside the slot, gives an O(1) "already interested?" check. A dict also keeps its keys in the order they were added, so it remembers the order in which users joined. We use it as an ordered set.

We no longer store a seat counter. Seats available is always `total_seats - len(booked_users)`, so it can never get out of sync.


### Which design pattern fits?

**Observer, in its simplest form.** "Tell me when a seat frees up" is exactly what Observer is about: users subscribe to an event, and all of them are told when it happens. Here the slot is the subject and `interested_users` is its list of subscribers.

A full Observer would add a base class like `SlotObserver` and a class for each kind of subscriber. We don't need that yet. Every subscriber is just a user id, and every notification is the same log line. If email or SMS alerts come later, that is the right time to add it.

**Strategy, not needed.** The two views differ only in how they sort, so a `SortStrategy` class for each order may look neat. But the `key` function we pass to `sort()` already is a tiny strategy, and two short lambdas are easier to read than two extra classes.

**Factory, not needed.** "New workouts may be added later" can suggest one class per workout and a factory to create them. But all workouts behave the same, only the name differs. Keeping the workout type as a plain string means a new workout needs no code change at all.


### Main classes

**`GetInShape`** is the entry point. It owns the three dicts. Each method finds the right center or slot, then lets that object do the work.

**`Center`** keeps the opening windows, the allowed workouts and its own slots. It answers the two questions asked before a slot is added: "is the center open then?" and "is that time already taken?". A center has only a handful of slots in a day, so a simple loop over its own list is enough.

**`WorkoutSlot`** owns its bookings and its interest list. Booking, cancelling and notifying change only this object.


### How it works

**Adding a slot.** We run the checks from the Key Rules section. The only tricky one is overlap: two slots overlap when `new_start < old_end` and `old_start < new_end`. If all checks pass, we save the slot in the center's list and in both dicts.

**Viewing.** We collect the matching slots and sort them with a tuple key: `(start_time, line)` or `(seats_available, line)`. Python compares tuples item by item, so the line is used only when the first values tie. Seat counts change on every booking, so we sort when asked. A sorted structure would need an update on every single booking.

**Booking and cancelling.** One dict lookup finds the slot. Then `userId in slot.booked_users` tells us in O(1) whether the user holds a seat, which decides both `ALREADY_BOOKED` and `BOOKING_NOT_FOUND`.

**Notify me.** Here is the flow from Example 5, for the `connaught_place` yoga slot `18-19` with 1 seat:

1. `arjun` books it, so 0 seats are left.
2. `rohit` tries to book and gets `NO_SEATS`.
3. `rohit` joins the interest list and gets `INTEREST_ADDED`.
4. `arjun` cancels, so 1 seat is free again.
5. `notifyInterestedUsers()` returns `["NOTIFY|rohit|connaught_place|yoga|18-19"]` and the list becomes empty.


### Code

```python
from typing import List


class GetInShape:
    """Entry point of the gym booking platform.

    It finds the right center or slot using dicts,
    then lets the Center and WorkoutSlot objects do the actual work.
    """

    def __init__(self):
        # center name -> Center
        self.centers = {}
        # "centerName|workoutType|startTime|endTime" -> WorkoutSlot
        # book, cancel, interest and notify all find their slot here in O(1)
        self.slots = {}
        # workout type -> slots of that type from all centers, used when centerName is "*"
        self.slots_by_workout = {}

    def onboardCenter(self, centerName: str, centerTimings: List[str], workoutTypes: List[str]) -> None:
        # centerName is a unique id, so a repeated onboarding is ignored
        if centerName in self.centers:
            return
        self.centers[centerName] = Center(centerName, centerTimings, workoutTypes)

    def addWorkoutSlot(self, centerName: str, workoutType: str, startTime: int, endTime: int, totalSeats: int) -> bool:
        center = self.centers.get(centerName)
        if center is None or startTime >= endTime or totalSeats <= 0:
            return False
        if workoutType not in center.workout_types:
            return False
        if not center.is_open(startTime, endTime) or center.has_overlap(startTime, endTime):
            return False
        slot = WorkoutSlot(centerName, workoutType, startTime, endTime, totalSeats)
        center.slots.append(slot)
        self.slots[self.slot_key(centerName, workoutType, startTime, endTime)] = slot
        self.slots_by_workout.setdefault(workoutType, []).append(slot)
        return True

    def viewWorkoutAvailabilityByStartTime(self, workoutType: str, centerName: str) -> List[str]:
        if self.is_all_centers(centerName):
            result = list(self.slots_by_workout.get(workoutType, []))
        elif centerName in self.centers:
            result = self.centers[centerName].get_slots(workoutType)
        else:
            result = []
        # smaller start time first, ties are broken by the full slot text
        result.sort(key=lambda slot: (slot.start_time, slot.to_line()))
        return [slot.to_line() for slot in result]

    def viewWorkoutAvailabilityBySeatsAvailable(self, workoutType: str, centerName: str) -> List[str]:
        result = []
        # this view needs one real center, "" and "*" are not allowed
        if not self.is_all_centers(centerName) and centerName in self.centers:
            result = self.centers[centerName].get_slots(workoutType)
        # fewer free seats first, ties are broken by the full slot text
        result.sort(key=lambda slot: (slot.seats_available(), slot.to_line()))
        return [slot.to_line() for slot in result]

    def bookSession(self, userId: str, centerName: str, workoutType: str, startTime: int, endTime: int) -> str:
        slot = self.slots.get(self.slot_key(centerName, workoutType, startTime, endTime))
        if slot is None:
            return "SLOT_NOT_FOUND"
        if userId in slot.booked_users:
            return "ALREADY_BOOKED"
        if slot.seats_available() == 0:
            return "NO_SEATS"
        slot.booked_users.add(userId)
        return "BOOKED"

    def cancelSession(self, userId: str, centerName: str, workoutType: str, startTime: int, endTime: int) -> str:
        slot = self.slots.get(self.slot_key(centerName, workoutType, startTime, endTime))
        if slot is None:
            return "SLOT_NOT_FOUND"
        if userId not in slot.booked_users:
            return "BOOKING_NOT_FOUND"
        slot.booked_users.remove(userId)
        return "CANCELLED"

    def addToInterestList(self, userId: str, centerName: str, workoutType: str, startTime: int, endTime: int) -> str:
        slot = self.slots.get(self.slot_key(centerName, workoutType, startTime, endTime))
        if slot is None:
            return "SLOT_NOT_FOUND"
        if slot.seats_available() > 0:
            return "SEATS_AVAILABLE"
        if userId in slot.interested_users:
            return "ALREADY_INTERESTED"
        slot.interested_users[userId] = True
        return "INTEREST_ADDED"

    def notifyInterestedUsers(self, centerName: str, workoutType: str, startTime: int, endTime: int) -> List[str]:
        slot = self.slots.get(self.slot_key(centerName, workoutType, startTime, endTime))
        if slot is None:
            return []
        return slot.notify_interested_users()

    def slot_key(self, center_name, workout_type, start_time, end_time):
        return f"{center_name}|{workout_type}|{start_time}|{end_time}"

    def is_all_centers(self, center_name):
        return center_name is None or center_name == "" or center_name == "*"


class Center:
    """A gym center: its opening hours, allowed workouts and its slots for the day.

    It answers the two questions asked while adding a slot:
    "is the center open then?" and "is that time already taken?"
    """

    def __init__(self, name, center_timings, workout_types):
        self.name = name
        # opening windows, "6-9" is stored as (6, 9)
        self.timings = []
        for timing in center_timings:
            start, end = timing.split("-")
            self.timings.append((int(start), int(end)))
        # a set gives an O(1) "is this workout allowed here?" check
        self.workout_types = set(workout_types)
        # all slots of this center for the day
        self.slots = []

    def is_open(self, start_time, end_time):
        """True if the time fits fully inside one opening window."""
        for window_start, window_end in self.timings:
            if window_start <= start_time and end_time <= window_end:
                return True
        return False

    def has_overlap(self, start_time, end_time):
        """True if the time shares any part with an existing slot.

        Touching slots like 6-7 and 7-8 are fine.
        """
        for slot in self.slots:
            if start_time < slot.end_time and slot.start_time < end_time:
                return True
        return False

    def get_slots(self, workout_type):
        return [slot for slot in self.slots if slot.workout_type == workout_type]


class WorkoutSlot:
    """One workout slot of a center.

    It owns its bookings and its interest list,
    so a booking or a cancellation touches only this object.
    """

    def __init__(self, center_name, workout_type, start_time, end_time, total_seats):
        self.center_name = center_name
        self.workout_type = workout_type
        self.start_time = start_time
        self.end_time = end_time
        self.total_seats = total_seats
        # users holding a seat, a set makes the "already booked?" check O(1)
        self.booked_users = set()
        # users waiting for a free seat. A dict keeps its keys in the order
        # they were added, so it works as an ordered set with O(1) lookups.
        self.interested_users = {}

    def seats_available(self):
        return self.total_seats - len(self.booked_users)

    def notify_interested_users(self):
        """One log line for every interested user.

        The list is then cleared because everyone has been told.
        """
        logs = []
        for user_id in self.interested_users:
            logs.append(f"NOTIFY|{user_id}|{self.center_name}|{self.workout_type}"
                        f"|{self.start_time}-{self.end_time}")
        self.interested_users.clear()
        return logs

    def to_line(self):
        """The output format: "centerName|workoutType|startTime|endTime|seatsAvailable"."""
        return (f"{self.center_name}|{self.workout_type}|{self.start_time}"
                f"|{self.end_time}|{self.seats_available()}")
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