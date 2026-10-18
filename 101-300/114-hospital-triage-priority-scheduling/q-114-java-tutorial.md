# Hospital Triage Priority Scheduling in Java

#### Problem Statement
[https://codezym.com/question/114-hospital-triage-priority-scheduling](https://codezym.com/question/114-hospital-triage-priority-scheduling)

---

## Core Idea

We are asked for the treatment position of just **one** patient, the one at index `k`.

The easy way is to build the complete treatment order by sorting all patients and then look up where patient `k` landed.

But think about it: patient `k`'s position is simply **(number of patients treated before `k`) + 1**.

So we do not need the full order at all. We can walk through the list once and count every patient who "beats" patient `k` under the given rules.

We will first write the simple sorting solution, see why it does extra work, and then improve it to a single pass counting solution.

---

## Understanding the Rules

**Basic version.** Patient `i` is treated before patient `k` if:

1. `i` has a higher severity, or
2. `i` has the same severity and arrived earlier (`i < k`).


**Extended version.** Patient `i` is treated before patient `k` if:

1. `i` has a higher severity, or
2. same severity, and `i` is `"F"` while `k` is `"M"`, or
3. same severity, same gender, and `i` arrived earlier (`i < k`).


Notice that arrival time is just the index. A smaller index means the patient came earlier.

---

## Solution 1: Sort All Patients

### Idea

Create a list of patient indexes `0, 1, 2, ... n-1`.

Sort this list using the triage rules. The first index in the sorted list is treated first, the second index is treated second, and so on.

Finally, find where `k` sits in the sorted list and add 1 (because the answer is 1-based).


### Why sort indexes and not severities?

If we sort the severity values directly, we lose track of which value belongs to patient `k`.

For example, with severities `[4, 4]`, after sorting we just see `[4, 4]` and cannot tell which `4` is patient `k`.

Sorting **indexes** keeps every patient's identity safe. We still compare them by looking up `severities.get(index)`.


### Handling gender

To compare genders, we turn them into numbers with a tiny helper:

- `"F"` becomes `0`
- `"M"` becomes `1`

Now "smaller number goes first", exactly like how we compare arrival index. This keeps the comparator clean and easy to read.


### Code

```java
import java.util.*;

public class HospitalTriagePriorityScheduling {

    public HospitalTriagePriorityScheduling() {
    }

    public int treatmentOrder(List<Integer> severities, int k) {
        // Sort patient indexes (not severities) so we never lose track of who is who.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < severities.size(); i++) {
            order.add(i);
        }

        order.sort((a, b) -> {
            int severityA = severities.get(a);
            int severityB = severities.get(b);
            // Rule 1: higher severity goes first
            if (severityA != severityB) {
                return Integer.compare(severityB, severityA);
            }
            // Rule 2: earlier arrival goes first
            return Integer.compare(a, b);
        });

        // Position in the sorted list is 0-based, answer is 1-based
        return order.indexOf(k) + 1;
    }

    public int treatmentOrderWithGenderPriority(List<Integer> severities, List<String> gender, int k) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < severities.size(); i++) {
            order.add(i);
        }

        order.sort((a, b) -> {
            int severityA = severities.get(a);
            int severityB = severities.get(b);
            // Rule 1: higher severity goes first
            if (severityA != severityB) {
                return Integer.compare(severityB, severityA);
            }
            // Rule 2: "F" goes before "M"
            int genderA = genderRank(gender.get(a));
            int genderB = genderRank(gender.get(b));
            if (genderA != genderB) {
                return Integer.compare(genderA, genderB);
            }
            // Rule 3: earlier arrival goes first
            return Integer.compare(a, b);
        });

        return order.indexOf(k) + 1;
    }

    // Smaller rank is treated first. "F" gets 0, "M" gets 1.
    int genderRank(String g) {
        return g.equals("F") ? 0 : 1;
    }
}
```

> **Small but important detail:** we store severities in `int` variables before comparing them.
> `severities.get(a)` returns an `Integer` object, and comparing two `Integer` objects with `!=` checks if they are the same object, not the same value. That breaks for values above 127.

### Complexity

- **Time:** `O(n log n)` because of sorting.
- **Space:** `O(n)` for the list of indexes.


### What is the problem here?

We sort **all** `n` patients, but we only care about **one** of them.

Most of the sorting work, like deciding whether patient 5 goes before patient 9, has nothing to do with patient `k`.

Can we skip all that extra work? Yes.

---

## Solution 2: Just Count Who Goes First (Optimal)

### Key Observation

Imagine you are patient `k` standing in the waiting room.

To know your turn, you do not need to arrange the whole room in a line. You just need to look at every other patient and ask:

> "Will this person be treated before me?"

Count the "yes" answers. Your position is that count plus 1.

Each "yes or no" question uses only the rules we wrote earlier, so one pass over the list is enough.


### Why this works

Our rules always give a clear winner between any two different patients, because no two patients share the same arrival index.

So every patient is either before `k` or after `k`. Nobody is "in between".

That means the number of patients before `k` fully decides `k`'s position. No sorting needed.


### Dry Run (Example 2)

`severities = [3, 1, 4, 4, 2]`, `gender = [M, F, M, F, M]`, `k = 2`

Patient `k` has severity `4` and gender `M`.

| i | severity | gender | Before patient 2? | Reason |
|---|----------|--------|-------------------|--------|
| 0 | 3 | M | No | lower severity |
| 1 | 1 | F | No | lower severity |
| 2 | 4 | M | No | this is patient `k` itself |
| 3 | 4 | F | **Yes** | same severity, `F` beats `M` |
| 4 | 2 | M | No | lower severity |

Patients ahead = `1`. Answer = `1 + 1 = 2`. ✅


### Why patient `k` never counts itself

When `i == k`, severity and gender are equal to patient `k`'s own values, and `i < k` is false.

So none of the conditions match, and we do not need a special check to skip it.


### Code

```java
import java.util.*;

public class HospitalTriagePriorityScheduling {

    public HospitalTriagePriorityScheduling() {
    }

    /**
     * Counts how many patients are treated before patient k.
     * Treatment order of k = (that count) + 1.
     */
    public int treatmentOrder(List<Integer> severities, int k) {
        int targetSeverity = severities.get(k);
        int ahead = 0;

        for (int i = 0; i < severities.size(); i++) {
            int severity = severities.get(i);

            if (severity > targetSeverity) {
                // more serious patient, always goes first
                ahead++;
            } else if (severity == targetSeverity && i < k) {
                // same severity but arrived earlier
                ahead++;
            }
            // patient k itself is never counted because i < k is false for i == k
        }
        return ahead + 1;
    }

    /**
     * Same idea as treatmentOrder(), with one extra tie breaker:
     * for equal severity, "F" is treated before "M".
     */
    public int treatmentOrderWithGenderPriority(List<Integer> severities, List<String> gender, int k) {
        int targetSeverity = severities.get(k);
        int targetGender = genderRank(gender.get(k));
        int ahead = 0;

        for (int i = 0; i < severities.size(); i++) {
            int severity = severities.get(i);
            int genderValue = genderRank(gender.get(i));

            if (severity > targetSeverity) {
                // more serious patient
                ahead++;
            } else if (severity == targetSeverity && genderValue < targetGender) {
                // same severity, but "F" while patient k is "M"
                ahead++;
            } else if (severity == targetSeverity && genderValue == targetGender && i < k) {
                // same severity, same gender, arrived earlier
                ahead++;
            }
        }
        return ahead + 1;
    }

    // Smaller rank is treated first. "F" gets 0, "M" gets 1.
    int genderRank(String g) {
        return g.equals("F") ? 0 : 1;
    }
}
```

### Complexity

- **Time:** `O(n)`, a single pass over the patients.
- **Space:** `O(1)`, just one counter and a few variables.

---

## Comparison

| Approach | Time | Space | Notes |
|----------|------|-------|-------|
| Sort all patients | `O(n log n)` | `O(n)` | Easy to think of, but builds the full order |
| Count patients ahead | `O(n)` | `O(1)` | Only answers the question that was asked |

---

## Key Takeaway

When a problem asks for the **position of one item** in a sorted order, you often do not need to sort.

Just count how many items would come before it, and add 1.