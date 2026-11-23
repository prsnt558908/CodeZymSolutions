# Can Attend All Scheduled Meetings in Java

#### Problem Statement
[https://codezym.com/question/285-can-attend-all-scheduled-meetings](https://codezym.com/question/285-can-attend-all-scheduled-meetings)


One person can attend every meeting only if no two meetings overlap. The core idea is to look at the meetings in the order they start, just like reading your calendar from morning to evening.

Once the meetings are sorted by start time, we only need to compare each meeting with the one right before it. If the schedule has any clash, one of these checks will catch it. So the solution is simple: sort once, then make a single pass over the list.

We will start with a brute force solution that compares every pair of meetings, see why it is slow, and then improve it with sorting.


## When Do Two Meetings Clash?

Each meeting comes as a string like `"2,7"`. We split it at the comma to get two numbers: start `2` and end `7`.

Two meetings clash when **each one starts before the other one ends**.

- `"2,7"` and `"6,9"` clash. The second meeting starts at `6`, while the first one is still running until `7`.
- `"3,8"` and `"8,11"` do **not** clash. The second meeting starts exactly when the first one ends, and the problem allows this.

That is why our clash checks use `<` and not `<=`.


## Solution 1: Brute Force (Check Every Pair)

The most direct idea is to compare every meeting with every other meeting.

Assume we have already converted each string into an array `{start, end}` and stored all of them in a list called `meetings`.

```java
for (int i = 0; i < meetings.size(); i++) {
    for (int j = i + 1; j < meetings.size(); j++) {
        int[] first = meetings.get(i);
        int[] second = meetings.get(j);

        // Clash: each meeting starts before the other one ends
        if (first[0] < second[1] && second[0] < first[1]) {
            return false;
        }
    }
}
return true;
```

This is correct, and it works even when the meetings are not in order.

**The problem:** with `n` meetings there are about `n * n / 2` pairs. For 10,000 meetings, that is around 50 million checks. The time complexity is `O(n²)`.

Most of these checks are wasted. If we knew which meetings come next to each other in time, we would only need to compare those.


## Solution 2: Sort by Start Time (Optimal)

Think about how you check your own calendar. You do not compare every meeting with every other meeting. You go through the day in order and ask one question for each meeting:

**Has the previous meeting ended by the time this one starts?**

In code, this becomes three simple steps:

1. Convert each `"start,end"` string into two numbers.
2. Sort the meetings by start time.
3. Go through the sorted list. If a meeting starts before the previous one ends, return `false`. If no clash is found, return `true`.


### Why Is the Previous Meeting Enough?

Suppose no clash has been found so far. Then every earlier meeting ended before (or exactly when) the next one started. They form a clean chain, one after another.

So among all the earlier meetings, the previous one ends last. If the current meeting does not clash with it, it cannot clash with any earlier meeting either.


### Dry Run

Take Example 2: `["10,14", "1,5", "6,9"]`.

After converting and sorting by start time, we get `{1,5}`, `{6,9}`, `{10,14}`.

| Previous | Current | Current start < Previous end? |
|---|---|---|
| `{1,5}` | `{6,9}` | `6 < 5`: No |
| `{6,9}` | `{10,14}` | `10 < 9`: No |

No clash is found, so the answer is `true`.

For Example 1, `["2,7", "6,9", "12,15"]`, the very first check is `6 < 7`: Yes. So the answer is `false`.


### Data Structures Used

- **`int[]` of size 2:** stores one meeting as `{start, end}`. We need real numbers here, because sorting the raw strings gives the wrong order. As strings, `"10,14"` comes before `"6,9"`, since strings are compared one character at a time and `'1'` is smaller than `'6'`.
- **`List<int[]>`:** stores all the converted meetings. This is the list we sort, so the input list of strings stays untouched.


### Edge Cases

- **Empty list or a single meeting:** there is nothing to compare. The loop never runs and we return `true`.
- **Two meetings with the same start time:** they always clash, because every meeting has `start < end`. After sorting they sit next to each other, so our check catches it.
- **One meeting inside another**, like `"1,10"` and `"2,3"`: after sorting, the check `2 < 10` catches it.


### Complexity

- **Time:** `O(n log n)`. Sorting takes `O(n log n)` and the single pass takes `O(n)`.
- **Space:** `O(n)` for the list of converted meetings.


### Java Code

```java
import java.util.*;

public class CanAttendAllMeetings {

    public CanAttendAllMeetings() {
    }

    /**
     * Returns true if one person can attend every meeting,
     * which means no two meetings overlap.
     * Each interval is a string in the format "start,end".
     */
    public boolean canAttendAllMeetings(List<String> intervals) {
        // 1. Convert every "start,end" string into a {start, end} array
        List<int[]> meetings = new ArrayList<>();
        for (String interval : intervals) {
            meetings.add(parseMeeting(interval));
        }

        // 2. Sort meetings by start time
        meetings.sort((a, b) -> Integer.compare(a[0], b[0]));

        // 3. Each meeting must start at or after the end of the previous one
        for (int i = 1; i < meetings.size(); i++) {
            int previousEnd = meetings.get(i - 1)[1];
            int currentStart = meetings.get(i)[0];

            // Back-to-back meetings are fine, so use '<' and not '<='
            if (currentStart < previousEnd) {
                return false;
            }
        }
        return true;
    }

    /**
     * Converts a string like "2,7" into the array {2, 7}.
     */
    int[] parseMeeting(String interval) {
        String[] parts = interval.split(",");
        int start = Integer.parseInt(parts[0].trim());
        int end = Integer.parseInt(parts[1].trim());
        return new int[]{start, end};
    }
}
```