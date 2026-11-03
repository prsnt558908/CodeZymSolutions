# Merge Working Hours Timeline in Java

#### Problem Statement
[https://codezym.com/question/153-merge-working-hour-intervals-timeline](https://codezym.com/question/153-merge-working-hour-intervals-timeline)


The group of people who are working does not change every minute. It changes only at a few special minutes: when someone starts work, and right after someone finishes. We call these minutes **change points**.

So instead of looking at every minute, we visit only the change points, in time order, and keep a sorted set of the people working right now. From one change point till the minute before the next one, this group stays the same. Each such stretch becomes one line of the answer.

We will first build a simple minute by minute solution, see where it wastes time, and then improve it.


## Turning Time Into Minutes

Times as strings are awkward to work with. For example, one minute after `"10:59"` is `"11:00"`, which is not simple to get from a string.

So we turn every time into **minutes since midnight**: `"hh:mm"` becomes `hh * 60 + mm`. For example, `"10:30"` becomes `630`.

Now every time is just a number. Numbers are easy to sort, and "one minute later" is simply `+ 1`.

When we write the answer, we turn the number back into `"hh:mm"`.


## Solution 1: Brute Force (Check Every Minute)

A day has only 1440 minutes, from `00:00` to `23:59`. So the simplest idea is:

1. For every minute, find everyone working at that minute.
2. Turn that group into a names string, like `"Aman, Bhavna"`.
3. If this string is different from the previous minute, the old group ends and a new group starts.

Assume each entry is already split into three lists: `names`, `starts` and `ends` (in minutes).

```java
List<String> timeline = new ArrayList<>();
String previousGroup = "";
int groupStart = 0;

// go till 1440 (one minute after 23:59) so the last group also gets written
for (int minute = 0; minute <= 1440; minute++) {
    // find everyone working at this minute, sorted by name
    TreeSet<String> working = new TreeSet<>();
    for (int p = 0; p < names.size(); p++) {
        if (starts.get(p) <= minute && minute <= ends.get(p)) {
            working.add(names.get(p));
        }
    }
    String group = String.join(", ", working);

    // group changed: write the old group and start a new one
    if (!group.equals(previousGroup)) {
        if (!previousGroup.isEmpty()) {
            timeline.add(toTime(groupStart) + " to " + toTime(minute - 1) + " -> " + previousGroup);
        }
        previousGroup = group;
        groupStart = minute;
    }
}
```

### What is wrong with it?

It gives the right answer, but it wastes a lot of work:

- It checks **every person for every minute**. With 10,000 people, that is about 14 million checks.
- It builds a full names string for **every minute**, even when nothing changed. If 10,000 people work all day, we build 1440 huge strings just to print one line.
- It only works because a day is short. If times had seconds, or covered many days, it would become much slower.

Most minutes look exactly like the minute before them. The group changes only at a few special minutes. So let us visit only those.


## Solution 2: Visit Only the Change Points

### When does the group change?

A person changes the group at exactly two minutes:

- At `startTime`, they **join** the group.
- At `endTime + 1`, they **leave** the group.

Why `endTime + 1` and not `endTime`? Because the end time is inclusive. In Example 1, Moris works till `11:00`, so at `11:00` Moris is still working. The first minute without Moris is `11:01`.

These minutes are our **change points**. Between one change point and the next, nobody joins or leaves, so the group stays exactly the same. This idea is often called a **sweep line**.

### The data structures we need

- **`joinAt` map**: maps a minute to the list of people who start at that minute. It holds a list because many people can start at the same minute.
- **`leaveAt` map**: maps a minute to the list of people who leave at that minute (their end time + 1).
- **`changes` list**: all change points in increasing order. We first collect them in a set to remove duplicates, then copy them into a list and sort it. With a list, the next change point is simply at index `i + 1`.
- **`working` sorted set (`TreeSet`)**: the people working right now. A sorted set always keeps the names in lexicographical order, so we can print them directly, without sorting for every line.

### Algorithm

1. For each entry, add the name to `joinAt[start]` and `leaveAt[end + 1]`. Both minutes are change points.
2. Sort the change points.
3. Visit the change points in order. At each one, remove the people who leave and add the people who join.
4. If someone is working, write one line: from this change point till one minute before the next change point.

If nobody is working, we write nothing. This is how empty gaps, like `12:01` to `13:59` in Example 1, are skipped.

### Walkthrough (Example 2)

Input: `"Aman,09:00,10:30"`, `"Bhavna,09:00,11:00"`, `"Chirag,10:30,12:00"`

| Person | Joins at | Leaves at |
|---|---|---|
| Aman | 09:00 | 10:31 |
| Bhavna | 09:00 | 11:01 |
| Chirag | 10:30 | 12:01 |

Sorted change points: `09:00, 10:30, 10:31, 11:01, 12:01`

| Change point | Joins | Leaves | Working now | Line written |
|---|---|---|---|---|
| 09:00 | Aman, Bhavna | - | Aman, Bhavna | 09:00 to 10:29 |
| 10:30 | Chirag | - | Aman, Bhavna, Chirag | 10:30 to 10:30 |
| 10:31 | - | Aman | Bhavna, Chirag | 10:31 to 11:00 |
| 11:01 | - | Bhavna | Chirag | 11:01 to 12:00 |
| 12:01 | - | Chirag | nobody | nothing |

Each line ends one minute before the next change point. The group at `10:30` changes again at `10:31`, so it gets a one-minute line: `10:30 to 10:30`.

### Do we need a separate merge step?

No. A new line starts only when someone joins or leaves, so two lines next to each other always have different people. The merge rule from the problem is satisfied automatically.

### Complexity

Let `n` be the number of people.

- **Time:** O(n log n). There are at most `2n` change points to sort, and each person is added to and removed from the sorted set once. Writing the answer strings also takes time equal to the size of the answer.
- **Space:** O(n) for the two maps, the change points and the sorted set.


## Java Code

```java
import java.util.*;

/**
 * Builds the working timeline by visiting only the "change points".
 * A change point is a minute where someone joins or leaves the working group.
 */
public class WorkingHoursTimeline {

    public WorkingHoursTimeline() {
    }

    public List<String> getWorkingTimeline(List<String> workingHours) {
        // minute -> people who start working at that minute
        Map<Integer, List<String>> joinAt = new HashMap<>();
        // minute -> people who are not working any more from that minute
        Map<Integer, List<String>> leaveAt = new HashMap<>();
        // every minute where the group of working people changes (no duplicates)
        Set<Integer> changeSet = new HashSet<>();

        for (String entry : workingHours) {
            String[] parts = entry.split(",");
            String name = parts[0];
            int start = toMinutes(parts[1]);
            // end time is inclusive, so the first minute without this person is end + 1
            int leave = toMinutes(parts[2]) + 1;

            joinAt.computeIfAbsent(start, k -> new ArrayList<>()).add(name);
            leaveAt.computeIfAbsent(leave, k -> new ArrayList<>()).add(name);
            changeSet.add(start);
            changeSet.add(leave);
        }

        // change points in increasing time order
        List<Integer> changes = new ArrayList<>(changeSet);
        Collections.sort(changes);

        // people working right now, always kept in lexicographical order
        TreeSet<String> working = new TreeSet<>();
        List<String> timeline = new ArrayList<>();

        for (int i = 0; i < changes.size(); i++) {
            int minute = changes.get(i);

            for (String name : leaveAt.getOrDefault(minute, List.of())) {
                working.remove(name);
            }
            for (String name : joinAt.getOrDefault(minute, List.of())) {
                working.add(name);
            }

            // Nobody joins or leaves before the next change point, so this group
            // stays the same till then. If someone is working, their leave minute
            // is still ahead, so a next change point always exists here.
            if (!working.isEmpty()) {
                int lastMinute = changes.get(i + 1) - 1;
                String names = String.join(", ", working);
                timeline.add(toTime(minute) + " to " + toTime(lastMinute) + " -> " + names);
            }
        }
        return timeline;
    }

    // "hh:mm" -> minutes since midnight. Example: "10:30" -> 630
    int toMinutes(String time) {
        String[] parts = time.trim().split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    // minutes since midnight -> "hh:mm". Example: 630 -> "10:30"
    String toTime(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}
```