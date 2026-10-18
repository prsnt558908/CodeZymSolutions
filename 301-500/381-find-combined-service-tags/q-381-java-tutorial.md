# Find Combined Service Tags in Java

#### Problem Statement
[https://codezym.com/question/381-find-combined-service-tags](https://codezym.com/question/381-find-combined-service-tags)

Store all available tags in a prefix tree, then use dynamic programming to find how many component tags can build each prefix. A tag belongs in the answer when it can use at least two components. Check tags in their original order to preserve the required output order.

## Start with a simple approach

Try every place where a tag could be split. Whenever the first piece is an available tag, repeat the process for the remaining characters.

This works, but different splits can lead to the same remaining suffix. Solving that suffix again and again can take exponential time.

Dynamic programming avoids this repeated work. A set can check whether a piece is available, but repeatedly creating and hashing substrings adds work. A prefix tree lets us check possible pieces one character at a time.

## Better approach: prefix tree and dynamic programming

### Store the available tags

A prefix tree, also called a trie, shares the common starting characters of tags.

Each `TagNode` contains a map from a character to the next node. Its `isTag` flag tells us whether a complete available tag ends there. This lets us find every matching component from a position without creating substrings.

### Count components for each prefix

For a tag of length `L`, create an array `parts` of size `L + 1`.

- `parts[i]` is the largest number of available tags that can build the first `i` characters.
- `parts[0] = 0` because the empty prefix needs no tags.
- `parts[i] = -1` means that prefix cannot be built yet.

For each reachable position `start`, follow the tag's characters through the tree. Stop if a character has no matching edge.

Whenever a complete component ends at `end`, update:

```text
parts[end + 1] = max(parts[end + 1], parts[start] + 1)
```

The whole candidate tag is also in the tree, so it can give `parts[L] = 1`. That alone is rejected. A valid split must raise the count to at least `2`.

We keep all tags in the tree, so the same component can be used repeatedly. Add each valid candidate once while walking through the original input list.

## Walk through an example

```text
serviceTags = ["eco", "ship", "ecoship", "plus", "ecoshipplus", "rapid"]
```

For `"ecoship"`, matching `"eco"` sets `parts[3] = 1`. Matching the whole tag sets `parts[7] = 1`.

Starting at position `3`, `"ship"` adds another component. Now `parts[7] = 2`, so `"ecoship"` is valid.

Similarly, `"eco" + "ship" + "plus"` builds `"ecoshipplus"` with three components. The result is:

```text
["ecoship", "ecoshipplus"]
```

## Why it works

Every update adds one complete available tag to a prefix that can already be built. Therefore, every recorded component count describes a valid combination.

Every valid combination begins at position `0` and moves through matching components. The algorithm checks every such next component from each reachable position. Keeping the largest count is safe because future choices depend only on the current position.

Therefore, `parts[L] >= 2` holds exactly when the full tag can be formed from at least two components.

## Complexity

Let `S` be the total number of input characters and `L_i` the length of tag `i`.

Building the tree takes `O(S)` time. Checking one tag takes `O(L_i²)` time in the worst case. Total time is `O(S + Σ L_i²)`, assuming average constant-time map operations.

The tree uses `O(S)` space, and the current array uses `O(L_max)` space. The result holds at most one reference per input tag.

## Java code

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FindCombinedServiceTags {

    public FindCombinedServiceTags() {
    }

    public List<String> findCombinedTags(List<String> serviceTags) {
        TagNode root = new TagNode();

        // Build one prefix tree containing every available tag.
        for (String tag : serviceTags) {
            TagNode node = root;

            for (int i = 0; i < tag.length(); i++) {
                char letter = tag.charAt(i);
                TagNode next = node.children.get(letter);

                if (next == null) {
                    next = new TagNode();
                    node.children.put(letter, next);
                }

                node = next;
            }

            node.isTag = true;
        }

        List<String> result = new ArrayList<>();

        // Checking the original list preserves the required order.
        for (String tag : serviceTags) {
            if (canBuildFromOtherTags(tag, root)) {
                result.add(tag);
            }
        }

        return result;
    }

    boolean canBuildFromOtherTags(String tag, TagNode root) {
        int length = tag.length();
        int[] parts = new int[length + 1];
        Arrays.fill(parts, -1);
        parts[0] = 0;

        for (int start = 0; start < length; start++) {
            if (parts[start] == -1) {
                continue;
            }

            TagNode node = root;

            for (int end = start; end < length; end++) {
                node = node.children.get(tag.charAt(end));

                if (node == null) {
                    break;
                }

                if (node.isTag) {
                    // Extend a buildable prefix by one complete component.
                    parts[end + 1] = Math.max(
                        parts[end + 1], parts[start] + 1
                    );
                }
            }
        }

        // A match using only the candidate itself has count one.
        return parts[length] >= 2;
    }
}

class TagNode {
    Map<Character, TagNode> children = new HashMap<>();
    boolean isTag = false;
}
```
