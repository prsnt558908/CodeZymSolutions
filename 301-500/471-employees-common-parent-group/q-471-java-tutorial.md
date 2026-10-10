# Closest Common Parent Group for Employees in Java

#### Problem Statement

[https://codezym.com/question/471-employees-common-parent-group](https://codezym.com/question/471-employees-common-parent-group)

Map each employee to their direct group. The answer is the **lowest common ancestor** of those groups, the deepest group above all of them. We will start with simple parent walks, then speed them up with precomputed ancestor jumps.

The required hierarchy is a fixed tree. One `EmployeeDirectory` class with maps and arrays is enough.

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

- `employeeGroups` maps each employee ID to their direct group's integer index.
- `groupNames` maps an integer index back to the original group ID.
- `depth` stores each group's distance from the root.
- `ancestor` stores the precomputed jumps.

Integer indexes let us use ordinary arrays for group data. A temporary map gives each group its index. The root always gets index `0`.

Collect every group ID before linking parents and children. This handles relations given in any order.

Use child lists to compute depths. Start with the root in a list, then process that list from left to right while appending children. This avoids recursion, so a deeply nested tree does not overflow the call stack.

The `levels` loop counts how many times we can halve the group count. This gives enough rows for every needed jump.

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

Save the code as `EmployeeDirectory.java`.

```java
import java.util.*;

public class EmployeeDirectory {
    List<String> groupNames = new ArrayList<>();
    Map<String, Integer> employeeGroups = new HashMap<>();
    int[] depth;
    int[][] ancestor;

    public EmployeeDirectory(
            String rootGroupId,
            List<String> groupRelations,
            List<String> employeeMemberships) {

        // Use integer positions so group data fits in ordinary arrays.
        Map<String, Integer> groupIndex = new HashMap<>();
        groupIndex.put(rootGroupId, 0);
        groupNames.add(rootGroupId);

        for (String relation : groupRelations) {
            for (String groupId : relation.split(",")) {
                if (!groupIndex.containsKey(groupId)) {
                    groupIndex.put(groupId, groupNames.size());
                    groupNames.add(groupId);
                }
            }
        }

        int groupCount = groupNames.size();
        int levels = 1;
        for (int size = groupCount; size > 1; size /= 2) {
            levels++;
        }

        depth = new int[groupCount];
        ancestor = new int[levels][groupCount];
        List<List<Integer>> children = new ArrayList<>();
        for (int group = 0; group < groupCount; group++) {
            children.add(new ArrayList<>());
        }

        for (String relation : groupRelations) {
            String[] parts = relation.split(",");
            int parent = groupIndex.get(parts[0]);
            int child = groupIndex.get(parts[1]);
            children.get(parent).add(child);
            ancestor[0][child] = parent;
        }

        // The root has index 0. Its ancestor entries stay 0.
        // Walk through a growing list instead of using recursion.
        List<Integer> order = new ArrayList<>();
        order.add(0);
        for (int position = 0; position < order.size(); position++) {
            int parent = order.get(position);
            for (int child : children.get(parent)) {
                depth[child] = depth[parent] + 1;
                order.add(child);
            }
        }

        // Two jumps of the previous length make one larger jump.
        for (int level = 1; level < levels; level++) {
            for (int group = 0; group < groupCount; group++) {
                int halfway = ancestor[level - 1][group];
                ancestor[level][group] = ancestor[level - 1][halfway];
            }
        }

        for (String membership : employeeMemberships) {
            String[] parts = membership.split(",");
            employeeGroups.put(parts[0], groupIndex.get(parts[1]));
        }
    }

    public String getCommonGroupForEmployees(List<String> employeeIds) {
        int commonGroup = -1;

        for (String employeeId : employeeIds) {
            Integer group = employeeGroups.get(employeeId);
            if (group == null) {
                return "";
            }

            if (commonGroup == -1) {
                commonGroup = group;
            } else if (commonGroup != 0) {
                commonGroup = lowestCommonAncestor(commonGroup, group);
            }
            // Even after reaching the root, validate later employee IDs.
        }

        return commonGroup == -1 ? "" : groupNames.get(commonGroup);
    }

    int lowestCommonAncestor(int first, int second) {
        if (depth[first] < depth[second]) {
            int temporary = first;
            first = second;
            second = temporary;
        }

        // Bring the deeper group to the other group's depth.
        for (int level = ancestor.length - 1; level >= 0; level--) {
            int candidate = ancestor[level][first];
            if (depth[candidate] >= depth[second]) {
                first = candidate;
            }
        }

        if (first == second) {
            return first;
        }

        // Keep both groups below their common ancestor.
        for (int level = ancestor.length - 1; level >= 0; level--) {
            if (ancestor[level][first] != ancestor[level][second]) {
                first = ancestor[level][first];
                second = ancestor[level][second];
            }
        }

        return ancestor[0][first];
    }
}
```
