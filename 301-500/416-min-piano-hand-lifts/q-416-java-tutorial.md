# Minimum Piano Hand Lifts in Java

#### Problem Statement
[https://codezym.com/question/416-min-piano-hand-lifts](https://codezym.com/question/416-min-piano-hand-lifts)


We need to split the notes into the fewest consecutive groups, where in each group the highest note minus the lowest note is at most `k`. Every group is one hand placement.

The core idea is simple: keep the hand down as long as the next note is reachable, and lift it only when there is no other choice. To know if a note is reachable, we only need the lowest and highest note of the current placement. So one pass over the notes with two variables is enough, giving O(n) time.


## Key Idea: Lift the Hand Only When You Must

Lifting the hand early never helps. Here is why.

If a group of notes fits in one placement, any smaller consecutive part of it also fits. Removing notes can only bring the lowest and highest notes closer together.

Now compare our plan with any other plan. Since we keep the hand down as long as possible, after the first placement we have played at least as many notes as the other plan.

For the second placement, look at the other plan's second group. We may have already played some of its notes. The rest is a smaller part of that group, so it also fits in one placement. So our second placement again reaches at least as far as theirs.

This repeats for every placement. We are never behind, so we never need more placements than any other plan.


## Solution 1: Simple Approach (Re-scan the Group)

The most direct way is to keep every note of the current placement in a list.

For each new note, we add it to the list and scan the list for its lowest and highest note. If they differ by more than `k`, the new note is out of reach. We lift the hand and start a new list with only this note.

```java
int placements = 1;
List<Integer> group = new ArrayList<>();

for (int note : notes) {
    group.add(note);

    // scan the whole group to find its lowest and highest note
    int low = Collections.min(group);
    int high = Collections.max(group);

    if (high - low > k) {
        // this note cannot be reached, lift the hand
        placements++;
        group.clear();
        group.add(note);
    }
}
return placements;
```

### Problem with this approach

Every new note makes us scan the whole group again.

If all notes fit in one placement, for example when `k` is very large, the group keeps growing up to 100,000 notes. This takes O(n²) time, which is billions of steps and too slow.


## Solution 2: Optimal Approach (Track Only the Lowest and Highest Note)

Do we really need every note of the group? No.

The width of a group is its highest note minus its lowest note. A new note can only change this width if it goes below the lowest note or above the highest note. Notes in the middle never matter again.

So instead of a list, we keep just two `int` variables: `low` and `high`.

When a note arrives, we compute:

- `newLow = Math.min(low, note)`
- `newHigh = Math.max(high, note)`

If `newHigh - newLow <= k`, the note is reachable. We keep the hand and save the new values.

Otherwise we lift the hand. The new placement starts with only this note, so `low` and `high` both become `note`.

Each note now takes O(1) time instead of a full scan.

### Dry Run

Example 1: `notes = [2, 4, 6, 11, 8, 10]`, `k = 4`

| Note | newHigh - newLow | Action | low, high after |
|------|------------------|--------|-----------------|
| 2 | 2 - 2 = 0 | first placement | 2, 2 |
| 4 | 4 - 2 = 2 | keep hand | 2, 4 |
| 6 | 6 - 2 = 4 | keep hand | 2, 6 |
| 11 | 11 - 2 = 9 | 9 > 4, lift hand | 11, 11 |
| 8 | 11 - 8 = 3 | keep hand | 8, 11 |
| 10 | 11 - 8 = 3 | keep hand | 8, 11 |

The hand was placed 2 times, so the answer is `2`.

### Java Code

```java
import java.util.*;

public class PianoHandPlanner {

    public PianoHandPlanner() {
    }

    /**
     * Returns the minimum number of hand placements needed
     * to play all the notes in the given order.
     */
    public int minimumHandLifts(List<Integer> notes, int k) {
        // the first placement starts at the first note
        int placements = 1;

        // lowest and highest note covered by the current placement
        int low = notes.get(0);
        int high = notes.get(0);

        for (int note : notes) {
            // how the current placement would look if it also plays this note
            int newLow = Math.min(low, note);
            int newHigh = Math.max(high, note);

            if (newHigh - newLow <= k) {
                // note is within reach, keep the hand where it is
                low = newLow;
                high = newHigh;
            } else {
                // note is out of reach, lift the hand and place it again at this note
                placements++;
                low = note;
                high = note;
            }
        }
        return placements;
    }
}
```


## Complexity

- **Time:** O(n), every note is checked once.
- **Space:** O(1), we only store a few integers.


## Edge Cases to Remember

- The first placement is always counted, so the answer is at least `1`.
- `k = 0` is allowed. Then one placement covers a single key, so only the same note repeated back to back can share a placement.
- Notes can be as large as 1,000,000,000, but `newHigh - newLow` is at most 999,999,999. It fits in an `int`, so there is no overflow.