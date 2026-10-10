# Cinema Hall Movie Schedule in Python

#### Problem Statement
[https://codezym.com/question/461-cinema-hall-movie-schedule](https://codezym.com/question/461-cinema-hall-movie-schedule)

The cinema has only one screen, so every existing screening blocks a fixed piece of the day. The new movie can only go into the free time that is left, and it must fit fully inside **one** free gap, because two separate gaps cannot be joined together. So the whole problem comes down to one question: is any single free gap long enough?

To answer it, we look up each screening's duration in the movie catalog using a dictionary, turn every screening into a busy slot `(start, end)`, sort the slots by start time, and then walk through them once while measuring every gap. We will first write a simple brute force solution, see where it wastes work, and then improve it to this gap-based solution.

## Quick Recap of the Rules

- The hall is open from minute `600` (10 AM) to minute `1380` (11 PM). That gives `780` usable minutes.
- A screening that starts at `start` ends at `start + duration`. The duration comes from the movie catalog.
- A screening may start exactly when another one ends, so a gap can be `0` minutes long.
- The new movie fits if at least one gap is `durationInMinutes` long or longer. Free minutes from different gaps cannot be added together.

## Seeing the Gaps with Example 1

The catalog has Ocean Trail (180 minutes), City Lights (300 minutes) and Moon Ride (140 minutes).

The screenings arrive in random order: City Lights at 1080, Ocean Trail at 600 and Moon Ride at 900. After adding each movie's duration and sorting by start time, the day looks like this:

```text
600              780        900           1040      1080               1380
 |--Ocean Trail---|   FREE   |--Moon Ride---|  FREE   |---City Lights----|
                    120 min                   40 min
```

There are two free gaps: 120 minutes (780 to 900) and 40 minutes (1040 to 1080).

- A 120-minute movie fits in the first gap, so the answer is `True`.
- A 121-minute movie fits nowhere, so the answer is `False`, even though 160 minutes are free in total.

## Solution 1: Brute Force (Try Every Start Minute)

### Idea

The most direct approach is to try every possible start time: 600, 601, 602 and so on, up to the last minute where the movie would still end by closing time.

For each start time, we check whether the new movie clashes with any existing screening. The first start time with no clash means the answer is `True`.

### When Do Two Time Ranges Clash?

Say the new movie runs from `start` to `end`, and a screening runs from `busy_start` to `busy_end`. They clash only when **both** of these are true:

- the new movie starts before the screening ends: `start < busy_end`
- the screening starts before the new movie ends: `busy_start < end`

If one of them ends exactly when the other starts, one of these checks becomes false. So two movies that just touch each other do not clash, which matches the rules.

### Building the Busy Slots

A screening record only has a title and a start time. The duration lives in the catalog, so we need a quick way to find it.

- **`dict` (title to duration):** gives us any movie's duration in O(1) on average. Titles are used exactly as given, so `"Moon Ride"` and `"moon ride"` stay two different movies, just like the problem asks.
- **`list` of `(start, end)` tuples:** each screening becomes a tiny tuple. That is all we need to know about a screening.

This step lives in the helper method `build_busy_slots()`.

### Code

```python
class CinemaHallMovieSchedule:
    def __init__(self):
        # Opening hours in minutes from midnight: 10 AM to 11 PM
        self.opening_time = 600
        self.closing_time = 1380

    def canSchedule(self, durationInMinutes, movies, screenings):
        busy_slots = self.build_busy_slots(movies, screenings)

        # Try every minute as a possible start time for the new movie
        last_start = self.closing_time - durationInMinutes
        for start in range(self.opening_time, last_start + 1):
            if self.is_free(start, start + durationInMinutes, busy_slots):
                return True
        return False

    def is_free(self, start, end, busy_slots):
        """True if the range [start, end) does not clash with any busy slot."""
        for busy_start, busy_end in busy_slots:
            # Clash: we start before the screening ends AND it starts before we end
            if start < busy_end and busy_start < end:
                return False
        return True

    def build_busy_slots(self, movies, screenings):
        """Turn every screening into a busy slot (start, end).

        The end time comes from the movie's duration in the catalog.
        """
        # title -> duration (titles are matched exactly, including letter case)
        duration_of = {}
        for movie in movies:
            title, minutes = movie.split(",")
            duration_of[title] = int(minutes)

        busy_slots = []
        for screening in screenings:
            title, start = screening.split(",")
            start = int(start)
            busy_slots.append((start, start + duration_of[title]))
        return busy_slots
```

### Complexity

Let `M` be the number of movies and `S` the number of screenings.

- **Time:** O(M + 780 × S). We try at most 780 start minutes, and each try may check all `S` screenings.
- **Space:** O(M + S) for the dictionary and the busy slots.

### Where This Wastes Work

- For the 120-minute movie in Example 1, every start from 600 to 779 clashes with Ocean Trail. The first free start, 780, is found only after 181 tries.
- Moving the start by one minute barely changes anything, yet we check every screening again.
- The work grows with the length of the day, not with the number of screenings. If times were in seconds, there would be up to 46,800 start times to try.

All of this happens because we ignore one simple fact: the movie can only sit inside a free gap. Let's use it.

## Solution 2: Sort and Check Only the Gaps (Efficient)

### Idea

With `S` screenings, there are at most `S + 1` free gaps:

- before the first screening,
- between every two screenings that are next to each other in time,
- after the last screening.

The new movie fits if at least one of these gaps is long enough. So instead of trying every minute, we measure each gap once.

### Why Sort?

The screenings can come in any order. Once the busy slots are sorted by start time, each gap is simply the space between one slot's end and the next slot's start. A single pass from left to right then sees every gap.

Tuples are compared by their first value first, so `busy_slots.sort()` puts the slots in order of start time.

A plain list sorted once is enough here. The method never adds anything to the schedule, so we do not need a structure that stays sorted after inserts.

### Walking Through the Gaps

We keep one variable, `free_from`: the minute from which the screen is free. It starts at opening time, `600`.

For each busy slot in sorted order:

1. The gap before this slot is `busy_start - free_from`. If it is at least `durationInMinutes`, return `True`.
2. Otherwise the screen stays busy until this slot ends, so set `free_from` to `busy_end`.

After the loop, we check the last gap: `1380 - free_from`. If there are no screenings, `free_from` is still `600`, so this checks the whole day.

### Dry Run on Examples 1 and 2

| Busy slot (sorted) | `free_from` | Gap before the slot |
|---|---|---|
| Ocean Trail, 600 to 780 | 600 | 0 |
| Moon Ride, 900 to 1040 | 780 | 120 |
| City Lights, 1080 to 1380 | 1040 | 40 |
| After the last slot | 1380 | 0 |

- For `durationInMinutes = 120`, the gap before Moon Ride is exactly 120, so we return `True` right there.
- For `durationInMinutes = 121`, no gap is long enough, so we return `False`.

### Code

The `build_busy_slots()` helper is the same as in Solution 1.

```python
class CinemaHallMovieSchedule:
    def __init__(self):
        # Opening hours in minutes from midnight: 10 AM to 11 PM
        self.opening_time = 600
        self.closing_time = 1380

    def canSchedule(self, durationInMinutes, movies, screenings):
        busy_slots = self.build_busy_slots(movies, screenings)

        # Sort by start time, so every free gap sits between two neighbouring slots
        busy_slots.sort()

        # The screen is free from this minute until the next screening starts
        free_from = self.opening_time
        for busy_start, busy_end in busy_slots:
            # Gap before this screening
            if busy_start - free_from >= durationInMinutes:
                return True
            free_from = busy_end

        # Gap after the last screening (the whole day if there are no screenings)
        return self.closing_time - free_from >= durationInMinutes

    def build_busy_slots(self, movies, screenings):
        """Turn every screening into a busy slot (start, end).

        The end time comes from the movie's duration in the catalog.
        """
        # title -> duration (titles are matched exactly, including letter case)
        duration_of = {}
        for movie in movies:
            title, minutes = movie.split(",")
            duration_of[title] = int(minutes)

        busy_slots = []
        for screening in screenings:
            title, start = screening.split(",")
            start = int(start)
            busy_slots.append((start, start + duration_of[title]))
        return busy_slots
```

### Complexity

- **Time:** O(M + S log S). Building the dictionary and the slots takes O(M + S), sorting takes O(S log S), and the walk takes O(S).
- **Space:** O(M + S) for the dictionary and the busy slots.

The running time now depends only on how many movies and screenings there are, not on the length of the day or the time unit.

## Edge Cases Handled

- **No screenings:** the whole day, 780 minutes, is one free gap.
- **Movie longer than 780 minutes:** no gap can be that long, so the answer is `False`.
- **Back-to-back screenings:** the gap between them is `0`, so nothing fits there.
- **Exact fit:** a gap equal to the duration is enough. That is why we use `>=` and not `>`.
- **Free time at the very start or end of the day:** checked before the first slot and after the loop.
- **Same movie screened many times:** every screening becomes its own busy slot.
- **Titles that differ only in letter case:** they are different movies, and the dictionary keeps them apart because it matches titles exactly.

## Summary

| Approach | Time | Space |
|---|---|---|
| Brute force (try every start minute) | O(M + 780 × S) | O(M + S) |
| Sort and check gaps | O(M + S log S) | O(M + S) |