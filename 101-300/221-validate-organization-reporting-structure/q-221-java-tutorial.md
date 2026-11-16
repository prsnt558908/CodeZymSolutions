# Validate Organization Reporting Structure in Java

#### Problem Statement
[https://codezym.com/question/221-validate-organization-reporting-structure](https://codezym.com/question/221-validate-organization-reporting-structure)


## Core Idea

Picture the organization as an org chart. The head sits at the top, and every other person sits under exactly one manager. This shape is called a **tree**.

So the whole problem is one question: do the reports form one single tree?

We answer it in two parts. While reading the reports, we catch the easy mistakes, like a person reporting to themselves or having two managers. Then we find the single head and walk down to everyone below. If this one walk reaches every person, the organization is valid.

The follow-up reuses the same walk. We start it from the given manager and count the people we reach.


## Approach 1: Climb Up From Every Person (Simple but Slow)

The most direct idea is to check every person one by one.

Store each person's manager in a map `managerOf` (employee -> manager), and reject anyone who shows up with a second manager.

Then, for every person, climb up: go to their manager, then to that manager's manager, and so on, until we reach someone with no manager.

If we meet the same person twice during a climb, we are going in a circle. Also, every climb must end at the same head.

```java
int topPerson = -1;
for (int person : people) {
    Set<Integer> seen = new HashSet<>();
    int current = person;

    // climb up until we reach someone with no manager
    while (managerOf.containsKey(current)) {
        if (seen.contains(current)) {
            return false; // we are going in a circle
        }
        seen.add(current);
        current = managerOf.get(current);
    }

    // every climb must end at the same head
    if (topPerson == -1) {
        topPerson = current;
    } else if (current != topPerson) {
        return false;
    }
}
return true;
```

Counting works the same way. For every person, climb up and check if we pass through `managerId` on the way.

```java
int count = 0;
for (int person : people) {
    int current = person;
    while (managerOf.containsKey(current)) {
        current = managerOf.get(current);
        if (current == managerId) {
            count++;
            break;
        }
    }
}
```

**The problem:** one climb can be very long. Imagine a chain where 2 reports to 1, 3 reports to 2, and so on, up to 200,000 people. The person at the bottom climbs about 200,000 steps, the next one a little less, and so on.

That adds up to around 20 billion steps, which is far too slow.

We keep walking the same paths again and again. So let us flip the direction and visit each person only once.


## Approach 2: Walk Down Once From the Head (Optimal)

Instead of climbing up from every person, we find the head and walk down from there, just once.

### Data we keep

- **`managerOf`** (employee -> manager): tells us who each person reports to. We use it to catch a second manager, and to find the head, because a head is someone missing from this map.

- **`teamOf`** (manager -> list of direct reports): takes us from a manager straight to their team. This is what lets us walk down.

- **`people`** (set): everyone who appears in the reports. The same person can show up in many reports, and a set keeps each one only once. Its size tells us how many people the walk must reach.

### Where each rule is checked

| Rule | Checked in |
|---|---|
| No one reports to themselves | Step 1 |
| A person has at most one direct manager | Step 1 |
| Exactly one head | Step 2 |
| All people are in the same organization | Step 3 |
| Every non-head person reports to the head | Step 3 |
| No cycle | Step 3 |

### Step 1: Read the reports

Split each `"employee,manager"` string into two numbers.

- If `employee == manager`, the person reports to themselves. Return `false`.
- If `employee` is already in `managerOf`, this is their second manager. Return `false`.
- Otherwise, save the relation in `managerOf` and `teamOf`, and add both people to `people`.

### Step 2: Find the head

The head is a person in `people` who is not in `managerOf`.

If there are zero heads, or more than one, return `false`.

### Step 3: Walk down from the head

Put the head in a queue. Take a person out, count them, and add their whole team to the queue. Repeat until the queue is empty.

If the count equals `people.size()`, the organization is valid.

This single check covers the last three rules:

- Reaching a person from the head means they report to the head, directly or indirectly.
- A person from a separate organization is not under the head, so the walk never reaches them.
- A person stuck in a cycle is never reached either. Their manager chain goes round and round and never touches the head.

**Example:** `["2,1", "3,4", "4,3"]`

Person `1` is the only head, so Step 2 passes. But `3` and `4` report to each other, which makes a loop cut off from `1`.

The walk from `1` reaches only `1` and `2`. That is 2 people out of 4, so the answer is `false`.

**Two small notes:**

- We don't need a "visited" set. Each person has only one manager, so there is only one way to reach them. And a cycle can never sit under the head, so the walk never loops.
- We use a queue, not recursion. In a chain of 200,000 people, recursion would go 200,000 calls deep and could crash with a stack overflow.

### Follow-up: Count reports to a manager

1. Build and validate the organization as above. If it is invalid, return `-1`.
2. If `managerId` is not in `people`, return `0`.
3. Otherwise, run the same walk starting from `managerId`. The walk also counts the manager, so return the count minus 1.

### Complexity

Let `N` be the number of reports.

- **Time:** `O(N)`. Each report is read once, and a walk visits each person at most once.
- **Space:** `O(N)` for the maps, the set and the queue.


## Final Code

Both public methods share two helpers: `buildAndValidate` runs Steps 1 to 3, and `countPeopleFrom` is the walk.

```java
import java.util.*;

public class OrganizationValidator {

    // employee -> their direct manager
    Map<Integer, Integer> managerOf = new HashMap<>();

    // manager -> people who report directly to them
    Map<Integer, List<Integer>> teamOf = new HashMap<>();

    // everyone who appears in any report
    Set<Integer> people = new HashSet<>();

    public OrganizationValidator() {
    }

    public boolean isValidOrganization(List<String> reports) {
        return buildAndValidate(reports);
    }

    public int countReportsToManager(List<String> reports, int managerId) {
        if (!buildAndValidate(reports)) {
            return -1;
        }
        if (!people.contains(managerId)) {
            return 0;
        }
        // the walk also counts the manager, so subtract 1
        return countPeopleFrom(managerId) - 1;
    }

    /**
     * Fills managerOf, teamOf and people from the reports.
     * Returns true only if all organization rules hold.
     */
    boolean buildAndValidate(List<String> reports) {
        managerOf = new HashMap<>();
        teamOf = new HashMap<>();
        people = new HashSet<>();

        // Step 1: read the reports and catch the easy mistakes
        for (String report : reports) {
            String[] parts = report.split(",");
            int employee = Integer.parseInt(parts[0].trim());
            int manager = Integer.parseInt(parts[1].trim());

            if (employee == manager) {
                return false; // reports to themselves
            }
            if (managerOf.containsKey(employee)) {
                return false; // second direct manager
            }

            managerOf.put(employee, manager);
            teamOf.putIfAbsent(manager, new ArrayList<>());
            teamOf.get(manager).add(employee);
            people.add(employee);
            people.add(manager);
        }

        // Step 2: there must be exactly one head (a person with no manager)
        int head = -1;
        int headCount = 0;
        for (int person : people) {
            if (!managerOf.containsKey(person)) {
                head = person;
                headCount++;
            }
        }
        if (headCount != 1) {
            return false;
        }

        // Step 3: walking down from the head must reach every person.
        // This proves: one organization, everyone reports to the head, no cycle.
        return countPeopleFrom(head) == people.size();
    }

    /**
     * Walks down the reporting tree starting at 'start'.
     * Returns how many people it reaches, including 'start'.
     */
    int countPeopleFrom(int start) {
        Queue<Integer> queue = new LinkedList<>();
        queue.add(start);
        int count = 0;

        while (!queue.isEmpty()) {
            int person = queue.poll();
            count++;
            List<Integer> team = teamOf.get(person);
            if (team != null) {
                queue.addAll(team);
            }
        }
        return count;
    }
}
```