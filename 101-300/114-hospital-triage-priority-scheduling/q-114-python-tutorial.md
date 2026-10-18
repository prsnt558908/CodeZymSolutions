# Hospital Triage Priority Scheduling in Python

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

Sorting **indexes** keeps every patient's identity safe. We still compare them by looking up `severities[index]`.


### Building the sort key

Python sorts by a `key`. If the key is a tuple, Python compares it **left to right**, one item at a time. This matches our rules perfectly.

For each patient `i` the key is:

- `-severities[i]`: we use the negative value so that a **higher** severity becomes a **smaller** number and comes first.
- `gender_rank(gender[i])`: `"F"` becomes `0` and `"M"` becomes `1`, so `"F"` comes first.
- `i`: the arrival index, so an earlier arrival comes first.

The basic version simply skips the gender part.


### Code

```python
class HospitalTriagePriorityScheduling:
    def __init__(self):
        pass

    def treatmentOrder(self, severities, k):
        # Sort patient indexes (not severities) so we never lose track of who is who.
        order = list(range(len(severities)))

        # Higher severity first (negated), then earlier arrival
        order.sort(key=lambda i: (-severities[i], i))

        # Position in the sorted list is 0-based, answer is 1-based
        return order.index(k) + 1

    def treatmentOrderWithGenderPriority(self, severities, gender, k):
        order = list(range(len(severities)))

        # Higher severity first, then "F" before "M", then earlier arrival
        order.sort(key=lambda i: (-severities[i], self.gender_rank(gender[i]), i))

        return order.index(k) + 1

    def gender_rank(self, g):
        # Smaller rank is treated first. "F" gets 0, "M" gets 1.
        return 0 if g == "F" else 1
```

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

```python
class HospitalTriagePriorityScheduling:
    def __init__(self):
        pass

    def treatmentOrder(self, severities, k):
        """
        Counts how many patients are treated before patient k.
        Treatment order of k = (that count) + 1.
        """
        target_severity = severities[k]
        ahead = 0

        for i, severity in enumerate(severities):
            if severity > target_severity:
                # more serious patient, always goes first
                ahead += 1
            elif severity == target_severity and i < k:
                # same severity but arrived earlier
                ahead += 1
            # patient k itself is never counted because i < k is False for i == k

        return ahead + 1

    def treatmentOrderWithGenderPriority(self, severities, gender, k):
        """
        Same idea as treatmentOrder(), with one extra tie breaker:
        for equal severity, "F" is treated before "M".
        """
        target_severity = severities[k]
        target_gender = self.gender_rank(gender[k])
        ahead = 0

        for i, severity in enumerate(severities):
            gender_value = self.gender_rank(gender[i])

            if severity > target_severity:
                # more serious patient
                ahead += 1
            elif severity == target_severity and gender_value < target_gender:
                # same severity, but "F" while patient k is "M"
                ahead += 1
            elif severity == target_severity and gender_value == target_gender and i < k:
                # same severity, same gender, arrived earlier
                ahead += 1

        return ahead + 1

    def gender_rank(self, g):
        # Smaller rank is treated first. "F" gets 0, "M" gets 1.
        return 0 if g == "F" else 1
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