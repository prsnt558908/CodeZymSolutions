# Find Entry and Exit Violations in Office Attendance Log in Java

#### Problem Statement
[https://codezym.com/question/469-office-entry-exit-violations](https://codezym.com/question/469-office-entry-exit-violations)

Every employee must follow one simple flow: `enter → exit → enter → exit`.

To find who broke this flow, we only need to look at each employee's own records, in the order they happened. Records of other people in between do not matter.

We will solve it in two ways. First, a simple way: group the records by employee and check each person's list. Then a better way: read the log only once and remember just **who is inside the office right now**. That is all we need to remember to catch every violation.

---

## Understanding the Rules

Read the records of **one** employee, from first to last.

- **Entry violation:** an `enter` with no `exit` right after it. The next record is `enter` again, or there is no next record (the person never left).
- **Exit violation:** an `exit` with no `enter` just before it. The previous record is `exit` too, or there is no previous record (the person left without coming in).

Here is Example 1 with the records grouped by employee:

| Employee | Records | Result |
|---|---|---|
| Tara | enter → enter → exit | Entry violation (`enter → enter`) |
| Neha | enter | Entry violation (never left) |
| Rohan | exit → enter → exit | Exit violation (starts with `exit`) |
| Pooja | enter → exit → exit | Exit violation (`exit → exit`) |
| Vikram | enter → exit | No violation |

So the answer is `[["Neha", "Tara"], ["Pooja", "Rohan"]]`.

Each list must be sorted and must not repeat a name. The same name can appear in both lists.

---

## Solution 1: Group Records by Employee

A first idea: for every record, scan the log to find the same person's next or previous record.

But one person's records are spread all over the log. In the worst case, each scan walks almost the whole log. With 100,000 records, that adds up to billions of steps. Too slow.

So let's collect each person's records first.

### Idea

**Step 1:** Read the log once. Split each record at the comma: `"Tara,enter"` gives the name `Tara` and the action `enter`. Add the action to that employee's list.

```
Tara  → [enter, enter, exit]
Rohan → [exit, enter, exit]
```

**Step 2:** Walk each employee's list.

- For every `enter`, look at the **next** action. If it is missing or it is `enter`, add the name to entry violations.
- For every `exit`, look at the **previous** action. If it is missing or it is `exit`, add the name to exit violations.

**Step 3:** Sort both groups of names and return them. A small helper, `toSortedList`, copies a set into a list and sorts it.

### Why these data structures?

**`HashMap<String, List<String>>`**: the log mixes everyone together. The map gives us one employee's actions in O(1). The `List` keeps those actions in their original order, so "next" and "previous" are just `i + 1` and `i - 1`.

**`HashSet<String>`** for the violations: one person can break the rules many times, but their name must appear only once. A set ignores duplicates. We sort the names once, at the end.

### Code

```java
import java.util.*;

public class AttendanceViolations {

    public AttendanceViolations() {
    }

    public List<List<String>> findViolations(List<String> records) {
        // Step 1: collect the actions of each employee, in log order.
        // Example: "Tara" -> ["enter", "enter", "exit"]
        Map<String, List<String>> actionsByName = new HashMap<>();

        for (String record : records) {
            String[] parts = record.split(",");
            String name = parts[0];
            String action = parts[1];

            if (!actionsByName.containsKey(name)) {
                actionsByName.put(name, new ArrayList<>());
            }
            actionsByName.get(name).add(action);
        }

        // sets keep each name only once, even if a person breaks the rules many times
        Set<String> entryViolations = new HashSet<>();
        Set<String> exitViolations = new HashSet<>();

        // Step 2: check every employee's own list of actions
        for (String name : actionsByName.keySet()) {
            List<String> actions = actionsByName.get(name);

            for (int i = 0; i < actions.size(); i++) {
                if (actions.get(i).equals("enter")) {
                    // an "enter" must be followed by an "exit"
                    boolean isLast = (i == actions.size() - 1);
                    if (isLast || actions.get(i + 1).equals("enter")) {
                        entryViolations.add(name);
                    }
                } else {
                    // an "exit" must come right after an "enter"
                    boolean isFirst = (i == 0);
                    if (isFirst || actions.get(i - 1).equals("exit")) {
                        exitViolations.add(name);
                    }
                }
            }
        }

        // Step 3: return both lists in sorted order
        List<List<String>> result = new ArrayList<>();
        result.add(toSortedList(entryViolations));
        result.add(toSortedList(exitViolations));
        return result;
    }

    // copies the names into a list and sorts them in ascending order
    List<String> toSortedList(Set<String> names) {
        List<String> list = new ArrayList<>(names);
        Collections.sort(list);
        return list;
    }
}
```

### Complexity

Let `n` be the number of records and `k` the number of different employees.

- **Time:** O(n + k log k). Each record is grouped once and checked once. Sorting the names at the end costs O(k log k).
- **Space:** O(n). The map keeps a copy of every action.

### Can We Do Better?

This solution is correct and fast enough. But it stores the whole log a second time, and it goes over the data twice.

Look at the rules again. `enter → enter` and `exit → exit` are just two actions in a row of the same person.

So when we read a record, we only need to know that person's **last action**. We do not need their whole history.

---

## Solution 2: One Pass with an "Inside" Set

### The Guard's Notebook

Imagine a guard at the office door with a notebook.

- When someone enters, the guard writes their name down.
- When someone exits, the guard crosses their name out.

So the notebook always shows **who is inside right now**. Now the mistakes are easy to spot:

1. Someone **enters**, but their name is already in the notebook. Their last entry never got an exit. **Entry violation.**
2. Someone **exits**, but their name is not in the notebook. This exit has no entry before it. **Exit violation.**
3. When the log ends, any name still in the notebook belongs to someone who never left. **Entry violation.**

In code, the notebook is a set called `inside`. If a name is in it, that person's last action was `enter`. If not, their last action was `exit`, or they have no records yet.

After an `enter`, the person is inside. After an `exit`, they are outside. This is true even for a wrong record. So we always add the name on `enter` and remove it on `exit`.

### Why these data structures?

**`HashSet<String> inside`**: for every record we ask "is this person inside?". A `HashSet` answers that in O(1).

**`HashSet<String>`** for the violations: same reason as before. No repeated names, and we sort once at the end.

### Dry Run (Example 1)

| Record | What happens | Inside after |
|---|---|---|
| `Rohan,exit` | Rohan is not inside: **exit violation** | empty |
| `Tara,enter` | OK | Tara |
| `Vikram,enter` | OK | Tara, Vikram |
| `Tara,enter` | Tara is already inside: **entry violation** | Tara, Vikram |
| `Pooja,enter` | OK | Tara, Vikram, Pooja |
| `Vikram,exit` | OK | Tara, Pooja |
| `Pooja,exit` | OK | Tara |
| `Tara,exit` | OK | empty |
| `Neha,enter` | OK | Neha |
| `Pooja,exit` | Pooja is not inside: **exit violation** | Neha |
| `Rohan,enter` | OK | Neha, Rohan |
| `Rohan,exit` | OK | Neha |

The log ends with Neha still inside: **entry violation**.

Entry violations are `["Neha", "Tara"]` and exit violations are `["Pooja", "Rohan"]`. This matches the expected output.

### Code

```java
import java.util.*;

public class AttendanceViolations {

    public AttendanceViolations() {
    }

    public List<List<String>> findViolations(List<String> records) {
        // employees who are inside the office right now,
        // i.e. their last record was "enter"
        Set<String> inside = new HashSet<>();

        // sets keep each name only once, even if a person breaks the rules many times
        Set<String> entryViolations = new HashSet<>();
        Set<String> exitViolations = new HashSet<>();

        for (String record : records) {
            String[] parts = record.split(",");
            String name = parts[0];
            String action = parts[1];

            if (action.equals("enter")) {
                // already inside: the previous "enter" never got its "exit"
                if (inside.contains(name)) {
                    entryViolations.add(name);
                }
                inside.add(name);
            } else {
                // not inside: this "exit" has no "enter" just before it
                if (!inside.contains(name)) {
                    exitViolations.add(name);
                }
                inside.remove(name);
            }
        }

        // still inside when the log ends: their last "enter" has no "exit"
        entryViolations.addAll(inside);

        List<List<String>> result = new ArrayList<>();
        result.add(toSortedList(entryViolations));
        result.add(toSortedList(exitViolations));
        return result;
    }

    // copies the names into a list and sorts them in ascending order
    List<String> toSortedList(Set<String> names) {
        List<String> list = new ArrayList<>(names);
        Collections.sort(list);
        return list;
    }
}
```

### Complexity

- **Time:** O(n + k log k). One pass over the log with O(1) set operations, then the names are sorted.
- **Space:** O(k). We keep only names, never the full log.

A nice bonus: records can be checked one by one as they arrive, like a live feed from the office gate. Only the "never left" check has to wait for the end of the log.

---

## Comparison

| | Solution 1 | Solution 2 |
|---|---|---|
| Passes over the data | 2 | 1 |
| Time | O(n + k log k) | O(n + k log k) |
| Extra space | O(n) | O(k) |