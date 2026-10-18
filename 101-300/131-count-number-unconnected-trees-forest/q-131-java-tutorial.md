# Count Number of Unconnected Trees in a Forest in Java

#### Problem Statement

[https://codezym.com/question/131-count-number-unconnected-trees-forest](https://codezym.com/question/131-count-number-unconnected-trees-forest)

Each tree has exactly one root, so counting trees means counting roots. Store child values and parent values in two sets. A node that appears as a parent but never as a child is a root. This gives a solution without walking through each tree.


## A first idea

Store each child's parent in a map. From every node, follow parent links until reaching a root, then add that root to a set.

This works, but a long chain makes us follow the same links repeatedly. The worst-case time is `O(n^2)` for `n` relations.


## Count roots directly

We only need two sets.

- `children` stores all values that appear before the comma.
- `parents` stores all values that appear after the comma.

Read all relations, then count the values in `parents` that are absent from `children`.

A set keeps each value only once, so a parent with many children is counted once. An internal node can appear in both sets, so it is correctly excluded.

The sets are local to the method, so repeated calls are independent. Empty input returns `0`. Only nodes mentioned in the relations are counted.


## Example

For `relations = List.of("2,1", "3,2", "5,4", "6,4")`.

- `children` contains `{2, 3, 5, 6}`.
- `parents` contains `{1, 2, 4}`.

Node `2` is a parent, but it is also a child. The remaining parents, `1` and `4`, are roots. The answer is `2`.


## Why this works

The input is a valid forest, so every tree has exactly one root. Every tree represented by the relations has at least one edge, so its root appears as a parent. A root never appears as a child.

Conversely, a parent that never appears as a child has no parent of its own, so it is a root. We therefore count every root exactly once, which gives the number of trees.


## Complexity

Let `n` be the number of relations. Building the sets and checking each distinct parent takes `O(n)` expected time with hash sets. The extra space is `O(n)`.


## Java Code

```java
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ForestCounter {

    public ForestCounter() {
    }

    public int countUnconnectedTrees(List<String> relations) {
        Set<Integer> children = new HashSet<>();
        Set<Integer> parents = new HashSet<>();

        for (String relation : relations) {
            String[] nodes = relation.split(",");
            int child = Integer.parseInt(nodes[0]);
            int parent = Integer.parseInt(nodes[1]);

            children.add(child);
            parents.add(parent);
        }

        int treeCount = 0;

        // A root appears as a parent but never as a child.
        for (int parent : parents) {
            if (!children.contains(parent)) {
                treeCount++;
            }
        }

        return treeCount;
    }
}
```
