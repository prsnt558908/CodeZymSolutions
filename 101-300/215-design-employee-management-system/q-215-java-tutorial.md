# Design Employee Management System in Java

#### Problem Statement
[https://codezym.com/question/215-design-employee-management-system](https://codezym.com/question/215-design-employee-management-system)

We need to build a company with one CEO, where every other employee has exactly one direct manager. The system should add employees, find an employee's manager, move an employee to a new manager, and check if one person works under another.

A company like this is a tree. The CEO sits at the top, and every other employee has exactly one manager above them.

The trick is to look at this tree from the bottom. For every employee, we remember just one thing: who their direct manager is. Going up from any employee is then a single straight path to the CEO, so every question becomes a short walk up that path.

The whole solution is one hash map. No design pattern is needed here. Composite and Chain of Responsibility may look like a fit, and we will see why they don't help.

We will first try the natural top-down idea, see why it is slow, and then flip the direction.


## Approach 1: Store Each Manager's Team (Simple but Slow)

Org charts are drawn from the top, so the first idea is to store, for every manager, the list of people who report directly to them.

```java
// managerId -> list of their direct reports
Map<String, List<String>> teamOf = new HashMap<>();
```

Adding an employee is easy: we add them to their manager's list and give them an empty list of their own.

But finding a manager means looking inside every list:

```java
public String getManager(String employeeId) {
    // Look inside every team until we find this employee
    for (String managerId : teamOf.keySet()) {
        if (teamOf.get(managerId).contains(employeeId)) {
            return managerId;
        }
    }
    return "";
}
```

And `worksFor()` has to search everyone below the manager:

```java
public boolean worksFor(String managerId, String employeeId) {
    // id checks are skipped to keep the snippet short
    // Check each direct report, then everyone below them
    for (String report : teamOf.get(managerId)) {
        if (report.equals(employeeId) || worksFor(report, employeeId)) {
            return true;
        }
    }
    return false;
}
```

### Problems with this approach

- `getManager()` looks inside every list. That is O(n), but we need O(1).
- `worksFor()` may visit everyone below the manager. For the CEO, that is the whole company: O(n).
- `changeManager()` needs the same full search to detect a loop.

Now look at this company:

```
CEO
├── Asha
│   ├── Ravi
│   └── Neha
├── Meera
└── Tara
```

A manager can have many people below them, but an employee has only one manager. Going down from the CEO means visiting all 5 employees. Going up from Ravi is just `Ravi -> Asha -> CEO`.

Going up is always one straight line. So let's flip the direction.


## Approach 2: Store Each Employee's Manager (Final Solution)

We keep one map, `managerOf`, from each employee to their direct manager. For the company above, it looks like this:

```
"CEO"   -> ""
"Asha"  -> "CEO"
"Meera" -> "CEO"
"Tara"  -> "CEO"
"Ravi"  -> "Asha"
"Neha"  -> "Asha"
```

The CEO has no manager, so we store an empty string for them.

### Why a single HashMap?

- Finding an id in a `HashMap` takes O(1) time on average.
- One map answers two questions: `containsKey()` tells if an employee exists, and `get()` gives their manager.
- We don't need the team lists anymore, because no method has to look downward.
- We also don't need an `Employee` class, because the only thing we store per employee is their manager.

### addEmployee() and getManager()

`addEmployee()` returns `false` if the employee already exists or the manager doesn't exist. Otherwise it adds one entry to the map.

`getManager()` is a single lookup. The CEO is stored with `""`, and unknown ids get `""` as the default value. So one line handles both special cases:

```java
return managerOf.getOrDefault(employeeId, "");
```

### worksFor(): walk up the chain

Start from the employee's manager and keep moving up. If we meet `managerId` on the way, return `true`. If we go past the CEO without meeting it, return `false`.

Let's trace `worksFor("CEO", "Ravi")`:

1. Ravi's manager is Asha. Asha is not the CEO, so we keep going.
2. Asha's manager is the CEO. Found it, so we return `true`.

This walk visits only the people above the employee. We call that number `h`, the height of the manager chain.

### changeManager(): stop loops

The first checks are simple: all ids must exist, the employee can't be the CEO, and `oldManagerId` must be their current manager.

The interesting rule is that the change must not create a cycle. In the company above, try `changeManager("Asha", "CEO", "Ravi")`. Ravi would become Asha's manager, but Ravi already reports to Asha:

```
Asha -> Ravi -> Asha -> Ravi -> ...
```

The chain goes round in a loop and never reaches the CEO. So this move must fail.

A loop happens only when the new manager is the employee themselves, or someone below the employee. "Someone below the employee" is exactly what `worksFor()` checks, so the loop check is just:

```java
if (newManagerId.equals(employeeId) || worksFor(employeeId, newManagerId)) {
    return false;
}
```

Why is this enough? After the move, the employee's chain continues with the new manager's chain. That chain already reached the CEO before the move. It can only break if it passes through the employee we are moving, and that happens only when the new manager works under them.

If all checks pass, we update just one entry: `managerOf.put(employeeId, newManagerId)`. Everyone below the employee moves along for free, because their chains pass through the employee.

### Why no design pattern?

**Composite** is the textbook pattern for org charts. A manager holds a list of employees, and the same method works on one person or a whole team.

It shines when an operation must cover a whole team, like the total salary of a department. But every method here walks up a single chain. Composite would bring back the downward lists that made Approach 1 slow.

**Chain of Responsibility** may come to mind because of the words "manager chain". That pattern passes a request from one handler to the next until someone handles it. We only read ids from a map, so a plain `while` loop is simpler.

### Complexity

Here `n` is the number of employees and `h` is the height of the manager chain.

| Method | Time |
|---|---|
| `addEmployee` | O(1) average |
| `getManager` | O(1) average |
| `changeManager` | O(h) |
| `worksFor` | O(h) |

The map takes O(n) space.

### Java Code

```java
import java.util.*;

/**
 * Stores the company as one map: employeeId -> direct managerId.
 * Every question is answered by walking up from an employee towards the CEO.
 */
public class EmployeeManagerSystem {

    // employeeId -> id of their direct manager.
    // The CEO is stored with "" as manager, because the CEO has no manager.
    Map<String, String> managerOf;

    String ceoId;

    public EmployeeManagerSystem(String ceoId) {
        this.ceoId = ceoId;
        managerOf = new HashMap<>();
        managerOf.put(ceoId, "");
    }

    /** Every employee, including the CEO, is a key in managerOf. */
    boolean exists(String employeeId) {
        return managerOf.containsKey(employeeId);
    }

    /** Adds employeeId under an existing managerId. */
    public boolean addEmployee(String managerId, String employeeId) {
        if (exists(employeeId) || !exists(managerId)) {
            return false;
        }
        managerOf.put(employeeId, managerId);
        return true;
    }

    /** Returns the direct manager, or "" for the CEO and for unknown ids. */
    public String getManager(String employeeId) {
        return managerOf.getOrDefault(employeeId, "");
    }

    /** Moves employeeId from oldManagerId to newManagerId, if the move is valid. */
    public boolean changeManager(String employeeId, String oldManagerId, String newManagerId) {
        if (!exists(employeeId) || !exists(oldManagerId) || !exists(newManagerId)) {
            return false;
        }
        // The CEO can never get a manager
        if (employeeId.equals(ceoId)) {
            return false;
        }
        // oldManagerId must be the current manager
        if (!managerOf.get(employeeId).equals(oldManagerId)) {
            return false;
        }
        // The new manager can't be the employee or anyone below the employee,
        // otherwise the manager chain turns into a loop
        if (newManagerId.equals(employeeId) || worksFor(employeeId, newManagerId)) {
            return false;
        }
        managerOf.put(employeeId, newManagerId);
        return true;
    }

    /** Returns true if employeeId works directly or indirectly under managerId. */
    public boolean worksFor(String managerId, String employeeId) {
        if (!exists(managerId) || !exists(employeeId) || managerId.equals(employeeId)) {
            return false;
        }
        // Walk up the chain: manager, manager's manager, ... until we pass the CEO
        String current = managerOf.get(employeeId);
        while (!current.isEmpty()) {
            if (current.equals(managerId)) {
                return true;
            }
            current = managerOf.get(current);
        }
        return false;
    }
}
```