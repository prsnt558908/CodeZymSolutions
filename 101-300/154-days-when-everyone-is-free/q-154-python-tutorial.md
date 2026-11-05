# Days When Everyone Is Free in Python

#### Problem Statement

[https://codezym.com/question/154-days-when-everyone-is-free](https://codezym.com/question/154-days-when-everyone-is-free)

Record when each blocked interval starts and when it ends, then visit the days in order. For the main question, a running count tells us whether any block is active. For the follow-up, we also track each person's active records, so overlapping records do not count the same person twice. Both methods take O(n + d) time, where n is the number of records.


## 1. Start with a simple solution

For each day, check every record and collect the blocked person IDs in a set. Here is the work for one day:

```python
blockedPeople = set()

for record in records:
    parts = record.split(",")
    personId = int(parts[0])
    start = int(parts[1])
    end = int(parts[2])

    if start <= day <= end:
        blockedPeople.add(personId)
```

An empty set means everyone is free. For the follow-up, subtract the set's size from the total number of distinct people and compare the result with k.

This takes O(n × d) time. With up to 100,000 records and 100,000 days, repeatedly scanning the records is too slow.


## 2. Everyone is free: use a difference array

For a blocked interval [start, end], add 1 at start and subtract 1 at end + 1 in a `changes` array. Then add these changes to a running count as we visit each day.

The subtraction happens on end + 1 because the end day is still blocked. An array of size d + 2 also safely handles an interval ending on day d.

A day belongs in the answer when the running count is zero. Repeated records cause no problem here. Whether one record or five records block a day, that day cannot be a day when everyone is free.


## 3. At least k people are free: count people once

The running count above counts records. We cannot subtract it from the number of people. One person might have several overlapping records.

Instead, use a list of events for each day. An event stores a person ID and a change of +1 or -1. Each record adds a +1 event on start and a -1 event on end + 1.

The small `DayEvent` class keeps these two values together. A map named `activeRecords` stores how many blocks are currently active for each person. The map also gives us the total number of distinct people. Large or sparse IDs do not require a large ID-indexed array.

Maintain one more count, `blockedPeople`:

- When a person's active count changes from zero to positive, add one blocked person.
- When it changes from positive to zero, remove one blocked person.
- Other changes leave the number of blocked people unchanged.

After processing every event for a day, include it if `totalPeople - blockedPeople >= k`. Checking after all events handles intervals that stop and start for the same person on the same day.

### A small example

Take records `["1,2,4", "1,3,5", "2,4,4"]`, with d = 6 and k = 1.

Person 1 is blocked from day 2 through day 5. Their overlapping records still represent only one blocked person. Person 2 is blocked only on day 4.

Everyone is free on `[1, 6]`. At least one person is free on `[1, 2, 3, 5, 6]`. On day 5, person 1 still has one active record, while person 2 is free.


## Time and space

The main method takes O(n + d) time and O(d) extra space.

The follow-up creates two events per record and scans the day lists once. It takes O(n + d) expected time, using average O(1) map operations, and O(n + d) extra space. Visiting days in order keeps the answers sorted. Either output list can contain up to d days.

This time bound is optimal in the worst case because all n records must be read and the output can contain a number of days proportional to d.


## Complete code

```python
from dataclasses import dataclass


@dataclass
class DayEvent:
    personId: int
    change: int


class FreeDaysFinder:
    def __init__(self):
        pass

    def getDaysWhenEveryoneIsFree(self, records, d):
        changes = [0] * (d + 2)

        for record in records:
            parts = record.split(",")
            start = int(parts[1])
            end = int(parts[2])

            changes[start] += 1
            # The end day is blocked, so remove the block on end + 1.
            changes[end + 1] -= 1

        result = []
        activeRecords = 0

        for day in range(1, d + 1):
            activeRecords += changes[day]
            if activeRecords == 0:
                result.append(day)

        return result

    def getDaysWhenAtLeastKPeopleAreFree(self, records, d, k):
        events = [[] for _ in range(d + 2)]
        activeRecords = {}

        for record in records:
            parts = record.split(",")
            personId = int(parts[0])
            start = int(parts[1])
            end = int(parts[2])

            events[start].append(DayEvent(personId, 1))
            events[end + 1].append(DayEvent(personId, -1))
            activeRecords[personId] = 0

        totalPeople = len(activeRecords)
        blockedPeople = 0
        result = []

        for day in range(1, d + 1):
            for event in events[day]:
                before = activeRecords[event.personId]
                after = before + event.change

                # Count a person only when their active count crosses zero.
                if before == 0 and after > 0:
                    blockedPeople += 1
                elif before > 0 and after == 0:
                    blockedPeople -= 1

                activeRecords[event.personId] = after

            # Apply every event for this day before checking availability.
            if totalPeople - blockedPeople >= k:
                result.append(day)

        return result
```
