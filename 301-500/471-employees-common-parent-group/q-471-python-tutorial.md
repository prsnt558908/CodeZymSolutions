# Closest Common Parent Group for Employees in Python

#### Problem Statement

[https://codezym.com/question/471-employees-common-parent-group](https://codezym.com/question/471-employees-common-parent-group)

Map each employee to their direct group. The answer is the **lowest common ancestor** of those groups, the deepest group above all of them. We will start with simple parent walks, then speed them up with precomputed ancestor jumps.

The required hierarchy is a fixed tree. One `EmployeeDirectory` class with maps and lists is enough.

## Start with simple parent walks

For two groups, move the deeper group upward until both are at the same depth. Then move both upward, one parent at a time, until they meet.

A group counts as its own ancestor. If an employee belongs directly to `services` and another belongs to `storage` inside it, the answer is `services`.

For several employees, keep one current answer. Start with the first employee's group. Combine it with each remaining employee's group using the same two-group operation.

This takes up to `O(K × H)` time per query. Here, `K` is the number of employee IDs and `H` is the tree height. A chain of 100,000 groups makes repeated parent walks expensive.

## Improve it with ancestor jumps

Instead of storing only a group's parent, store ancestors that are 1, 2, 4, 8, and more steps above it. This technique is called **binary lifting**.

`ancestor[level][group]` stores the ancestor `2^level` steps above `group`. If a jump would pass the root, store the root instead.

The first row stores direct parents. Every later row combines two jumps from the previous row. For example, jumping 4 steps twice gives an 8-step jump.

With 100,000 groups, we need only 17 rows. Each two-group lookup now takes `O(log G)` time, where `G` is the number of groups.

## Build the directory once

- `employee_groups` maps each employee ID to their direct group's integer index.
- `group_names` maps an integer index back to the original group ID.
- `depth` stores each group's distance from the root.
- `ancestor` stores the precomputed jumps.

Integer indexes let us use ordinary lists for group data. A temporary map gives each group its index. The root always gets index `0`.

Collect every group ID before linking parents and children. This handles relations given in any order.

Use child lists to compute depths. Start with the root in a list, then process that list from left to right while appending children. This avoids recursion, so a deeply nested tree does not overflow the call stack.

Because the group count is positive, `group_count.bit_length()` gives the number of rows needed for the jumps.

After computing depths, fill the ancestor rows. The temporary child lists are no longer needed by queries.

## Answer a query

Read the employee IDs one at a time. Return `""` if any ID is unknown. Otherwise, combine its group with the current answer.

To combine two groups:

1. Put the deeper group first.
2. Try jumps from largest to smallest. Lift it without going above the other group's depth.
3. If the groups are now equal, return that group.
4. Otherwise, try jumps from largest to smallest again. Move both only when their destination ancestors differ.
5. Return their shared direct parent.

In step 4, different destinations mean both groups remain below their lowest common ancestor. If the destinations match, that jump could skip the answer, so try a shorter one.

An empty query returns `""`. A single employee returns their direct group.

Repeated IDs need no separate set. Once the current answer contains an employee's group, combining that same group again cannot change it.

Once the answer becomes the root, further ancestor lookups are unnecessary. **Still check every remaining employee ID.** A later unknown ID must make the entire result `""`.

## Walk through an example

In the statement, `storage` is inside `services`, which is inside `technology`. The `apps` group is also inside `technology`.

For `["e1", "e3", "e4"]`, the direct groups are `storage`, `services`, and `apps`.

- Start with `storage`.
- Combine `storage` and `services`. The current answer becomes `services`.
- Combine `services` and `apps`. The answer becomes `technology`.

For `["e1", "e5", "missing"]`, the first two employees bring the answer to `org`. The last ID is unknown, so the final result is still `""`.

## Why this works

For two groups, matching depths cannot skip their lowest common ancestor. If the groups become equal, that group is the answer.

Otherwise, moving both to different ancestors keeps them below the answer. After all jump lengths are considered, their direct parents match. That parent is the deepest group containing both.

For several groups, any group containing all groups already processed must also contain their lowest common ancestor. Combining the current answer with the next group therefore keeps exactly the answer we need.

## Complexity

Let `G` be the group count, `E` the employee count, `K` the query length, and `L = 1 + floor(log2 G)` the number of ancestor rows.

- Construction takes `O(G × L + E)` time.
- A query takes `O(K × L)` time in the worst case and `O(1)` extra space. An empty query takes `O(1)` time.
- Stored data uses `O(G × L + E)` space. Temporary construction data uses `O(G)` space.

These bounds assume average constant-time map lookups and treat ID lengths as constant.

## Code

The constructor and public method keep the names from the starter code. `group is None` checks for a missing employee. Group index `0` is a valid result.

```python
class EmployeeDirectory:
    def __init__(self, rootGroupId, groupRelations, employeeMemberships):
        # Use integer positions so group data fits in ordinary lists.
        group_index = {rootGroupId: 0}
        self.group_names = [rootGroupId]
        self.employee_groups = {}

        for relation in groupRelations:
            for group_id in relation.split(","):
                if group_id not in group_index:
                    group_index[group_id] = len(self.group_names)
                    self.group_names.append(group_id)

        group_count = len(self.group_names)
        levels = group_count.bit_length()
        self.depth = [0] * group_count
        self.ancestor = [[0] * group_count for _ in range(levels)]
        children = [[] for _ in range(group_count)]

        for relation in groupRelations:
            parent_id, child_id = relation.split(",")
            parent = group_index[parent_id]
            child = group_index[child_id]
            children[parent].append(child)
            self.ancestor[0][child] = parent

        # The root has index 0. Its ancestor entries stay 0.
        # Walk through a growing list instead of using recursion.
        order = [0]
        position = 0
        while position < len(order):
            parent = order[position]
            position += 1
            for child in children[parent]:
                self.depth[child] = self.depth[parent] + 1
                order.append(child)

        # Two jumps of the previous length make one larger jump.
        for level in range(1, levels):
            for group in range(group_count):
                halfway = self.ancestor[level - 1][group]
                self.ancestor[level][group] = self.ancestor[level - 1][halfway]

        for membership in employeeMemberships:
            employee_id, group_id = membership.split(",")
            self.employee_groups[employee_id] = group_index[group_id]

    def getCommonGroupForEmployees(self, employeeIds):
        common_group = -1

        for employee_id in employeeIds:
            group = self.employee_groups.get(employee_id)
            if group is None:
                return ""

            if common_group == -1:
                common_group = group
            elif common_group != 0:
                common_group = self.lowest_common_ancestor(common_group, group)
            # Even after reaching the root, validate later employee IDs.

        return "" if common_group == -1 else self.group_names[common_group]

    def lowest_common_ancestor(self, first, second):
        if self.depth[first] < self.depth[second]:
            first, second = second, first

        # Bring the deeper group to the other group's depth.
        for level in range(len(self.ancestor) - 1, -1, -1):
            candidate = self.ancestor[level][first]
            if self.depth[candidate] >= self.depth[second]:
                first = candidate

        if first == second:
            return first

        # Keep both groups below their common ancestor.
        for level in range(len(self.ancestor) - 1, -1, -1):
            if self.ancestor[level][first] != self.ancestor[level][second]:
                first = self.ancestor[level][first]
                second = self.ancestor[level][second]

        return self.ancestor[0][first]
```
