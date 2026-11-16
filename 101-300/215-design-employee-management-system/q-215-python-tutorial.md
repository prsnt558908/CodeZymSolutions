# Design Employee Management System in Python

#### Problem Statement
[https://codezym.com/question/215-design-employee-management-system](https://codezym.com/question/215-design-employee-management-system)

We need to build a company with one CEO, where every other employee has exactly one direct manager. The system should add employees, find an employee's manager, move an employee to a new manager, and check if one person works under another.

A company like this is a tree. The CEO sits at the top, and every other employee has exactly one manager above them.

The trick is to look at this tree from the bottom. For every employee, we remember just one thing: who their direct manager is. Going up from any employee is then a single straight path to the CEO, so every question becomes a short walk up that path.

The whole solution is one dictionary. No design pattern is needed here. Composite and Chain of Responsibility may look like a fit, and we will see why they don't help.

We will first try the natural top-down idea, see why it is slow, and then flip the direction.


## Approach 1: Store Each Manager's Team (Simple but Slow)

Org charts are drawn from the top, so the first idea is to store, for every manager, the list of people who report directly to them.

```python
# managerId -> list of their direct reports
self.teamOf: Dict[str, List[str]] = {}
```

Adding an employee is easy: we add them to their manager's list and give them an empty list of their own.

But finding a manager means looking inside every list:

```python
def getManager(self, employeeId: str) -> str:
    # Look inside every team until we find this employee
    for managerId, team in self.teamOf.items():
        if employeeId in team:
            return managerId
    return ""
```

And `worksFor()` has to search everyone below the manager:

```python
def worksFor(self, managerId: str, employeeId: str) -> bool:
    # id checks are skipped to keep the snippet short
    # Check each direct report, then everyone below them
    for report in self.teamOf[managerId]:
        if report == employeeId or self.worksFor(report, employeeId):
            return True
    return False
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

We keep one dictionary, `managerOf`, from each employee to their direct manager. For the company above, it looks like this:

```python
managerOf = {
    "CEO": "",
    "Asha": "CEO",
    "Meera": "CEO",
    "Tara": "CEO",
    "Ravi": "Asha",
    "Neha": "Asha",
}
```

The CEO has no manager, so we store an empty string for them.

### Why a single dictionary?

- Finding a key in a `dict` takes O(1) time on average.
- One dictionary answers two questions: `employeeId in self.managerOf` tells if an employee exists, and `self.managerOf[employeeId]` gives their manager.
- We don't need the team lists anymore, because no method has to look downward.
- We also don't need an `Employee` class, because the only thing we store per employee is their manager.

### addEmployee() and getManager()

`addEmployee()` returns `False` if the employee already exists or the manager doesn't exist. Otherwise it adds one entry to the dictionary.

`getManager()` is a single lookup. The CEO is stored with `""`, and unknown ids get `""` as the default value. So one line handles both special cases:

```python
return self.managerOf.get(employeeId, "")
```

### worksFor(): walk up the chain

Start from the employee's manager and keep moving up. If we meet `managerId` on the way, return `True`. If we go past the CEO without meeting it, return `False`.

Let's trace `worksFor("CEO", "Ravi")`:

1. Ravi's manager is Asha. Asha is not the CEO, so we keep going.
2. Asha's manager is the CEO. Found it, so we return `True`.

This walk visits only the people above the employee. We call that number `h`, the height of the manager chain.

### changeManager(): stop loops

The first checks are simple: all ids must exist, the employee can't be the CEO, and `oldManagerId` must be their current manager.

The interesting rule is that the change must not create a cycle. In the company above, try `changeManager("Asha", "CEO", "Ravi")`. Ravi would become Asha's manager, but Ravi already reports to Asha:

```
Asha -> Ravi -> Asha -> Ravi -> ...
```

The chain goes round in a loop and never reaches the CEO. So this move must fail.

A loop happens only when the new manager is the employee themselves, or someone below the employee. "Someone below the employee" is exactly what `worksFor()` checks, so the loop check is just:

```python
if newManagerId == employeeId or self.worksFor(employeeId, newManagerId):
    return False
```

Why is this enough? After the move, the employee's chain continues with the new manager's chain. That chain already reached the CEO before the move. It can only break if it passes through the employee we are moving, and that happens only when the new manager works under them.

If all checks pass, we update just one entry: `self.managerOf[employeeId] = newManagerId`. Everyone below the employee moves along for free, because their chains pass through the employee.

### Why no design pattern?

**Composite** is the textbook pattern for org charts. A manager holds a list of employees, and the same method works on one person or a whole team.

It shines when an operation must cover a whole team, like the total salary of a department. But every method here walks up a single chain. Composite would bring back the downward lists that made Approach 1 slow.

**Chain of Responsibility** may come to mind because of the words "manager chain". That pattern passes a request from one handler to the next until someone handles it. We only read ids from a dictionary, so a plain `while` loop is simpler.

### Complexity

Here `n` is the number of employees and `h` is the height of the manager chain.

| Method | Time |
|---|---|
| `addEmployee` | O(1) average |
| `getManager` | O(1) average |
| `changeManager` | O(h) |
| `worksFor` | O(h) |

The dictionary takes O(n) space.

### Python Code

```python
from typing import Dict


class EmployeeManagerSystem:
    """
    Stores the company as one dictionary: employeeId -> direct managerId.
    Every question is answered by walking up from an employee to the CEO.
    """

    def __init__(self, ceoId: str):
        self.ceoId = ceoId

        # employeeId -> id of their direct manager.
        # The CEO is stored with "" as manager, because the CEO has no manager.
        self.managerOf: Dict[str, str] = {ceoId: ""}

    def exists(self, employeeId: str) -> bool:
        """Every employee, including the CEO, is a key in managerOf."""
        return employeeId in self.managerOf

    def addEmployee(self, managerId: str, employeeId: str) -> bool:
        """Adds employeeId under an existing managerId."""
        if self.exists(employeeId) or not self.exists(managerId):
            return False
        self.managerOf[employeeId] = managerId
        return True

    def getManager(self, employeeId: str) -> str:
        """Returns the direct manager, or "" for the CEO and unknown ids."""
        return self.managerOf.get(employeeId, "")

    def changeManager(self, employeeId: str, oldManagerId: str,
                      newManagerId: str) -> bool:
        """Moves employeeId to newManagerId, if the move is valid."""
        if (not self.exists(employeeId) or not self.exists(oldManagerId)
                or not self.exists(newManagerId)):
            return False
        # The CEO can never get a manager
        if employeeId == self.ceoId:
            return False
        # oldManagerId must be the current manager
        if self.managerOf[employeeId] != oldManagerId:
            return False
        # The new manager can't be the employee or anyone below the employee,
        # otherwise the manager chain turns into a loop
        if (newManagerId == employeeId
                or self.worksFor(employeeId, newManagerId)):
            return False
        self.managerOf[employeeId] = newManagerId
        return True

    def worksFor(self, managerId: str, employeeId: str) -> bool:
        """Returns True if employeeId is anywhere below managerId."""
        if (not self.exists(managerId) or not self.exists(employeeId)
                or managerId == employeeId):
            return False
        # Walk up: manager, manager's manager, ... until we pass the CEO
        current = self.managerOf[employeeId]
        while current != "":
            if current == managerId:
                return True
            current = self.managerOf[current]
        return False
```