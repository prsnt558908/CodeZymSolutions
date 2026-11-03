# Total Scores of Leaf Domains in Java

#### Problem Statement
[https://codezym.com/question/158-total-scores-leaf-domains](https://codezym.com/question/158-total-scores-leaf-domains)


Domain names form a family tree. `com` is the parent of `test.com`, and `test.com` is the parent of `mail.test.com`.

A leaf is a domain with no input domain below it. Its total score is simply the sum of all scores on its path, from the top down to itself.

The key trick: we never need to search for a parent. Cutting off the first label gives it to us directly.

We will start with a brute force idea, then solve it with a hash map and a set, and finally improve it with a trie that reads every domain just once.


## Picture the Domains as a Tree

Here is Example 1 drawn as a tree. Each leaf adds up the scores on its path.

```text
com (20)
├── mydomain.com (5)
│   ├── test.mydomain.com (10)
│   │   └── mail.test.mydomain.com (15)   leaf: 20 + 5 + 10 + 15 = 50
│   └── www.mydomain.com (10)             leaf: 20 + 5 + 10 = 35
└── test.com (-10)
    ├── mail.test.com (10)
    │   └── xyz.mail.test.com (25)        leaf: 20 - 10 + 10 + 25 = 45
    └── www.test.com (-5)                 leaf: 20 - 10 - 5 = 5
```

`mail.test.com` is not a leaf, because `xyz.mail.test.com` sits below it.


### Tricky Case: Missing Parents

Here, "child" means any domain below, not only one level below.

Say the input is just `com,1` and `a.b.com,2`.

- `com` is **not** a leaf, because `a.b.com` is below it.
- `b.com` is missing, so it adds nothing.
- `a.b.com` is a leaf with total `2 + 1 = 3`.


## Brute Force: Compare Every Pair

The simplest idea is to compare every domain with every other domain.

- If some domain ends with `"." + y`, it sits below `y`, so `y` is not a leaf.
- For a leaf `y`, add its own score and the score of every domain that `y` sits below.

```java
// domains: list of all input domains, scoreOf: domain -> its score

// x is below y if x ends with "." + y
// the dot matters: "atest.com" is not below "test.com"
boolean isBelow(String x, String y) {
    return x.endsWith("." + y);
}

for (String y : domains) {
    // y is not a leaf if any domain is below it
    boolean isLeaf = true;
    for (String x : domains) {
        if (isBelow(x, y)) {
            isLeaf = false;
            break;
        }
    }
    if (!isLeaf) {
        continue;
    }

    // add y's own score and the score of every ancestor of y
    long total = 0;
    for (String x : domains) {
        if (x.equals(y) || isBelow(y, x)) {
            total += scoreOf.get(x);
        }
    }
    // save y with its total
}
```

**Why it is slow:** every domain is checked against all n domains. That is O(n² · L) time, where L is the length of a domain. For 100,000 domains, that is about 10 billion checks.

The waste is easy to see. A domain has only a few ancestors, but we scan all n domains to find them.


## Solution 1: Hash Map + Set

### Idea

We do not need to search for ancestors. We can build them by cutting off the first label again and again.

```text
mail.test.com  ->  test.com  ->  com
```

A domain has only a few labels, so it has only a few ancestors. Now each domain looks only at its own ancestors, instead of all n domains.


### Data Structures

**`Map<String, Long> scoreOf`:** maps a domain to its own score. When we build an ancestor like `test.com`, this map tells us in O(1) if it is in the input, and what its score is.

**`Set<String> hasChild`:** every domain that has some input domain below it. While reading `mail.test.com`, we add `test.com` and `com` to it. An input domain that never lands in this set is a leaf.


### Steps

1. For each entry, save its score in `scoreOf` and add all its ancestors to `hasChild`.
2. Leaves are the input domains that are not in `hasChild`. Sort them.
3. For each leaf, start with its own score and add the score of every ancestor found in `scoreOf`. A missing ancestor adds 0.

Totals are stored as `long`. A long chain of nested domains can push the sum past the `int` limit.


### Code

```java
import java.util.*;

public class TotalScoresOfLeafDomains {

    public TotalScoresOfLeafDomains() {
    }

    public List<String> getLeafDomainsWithTotalScores(List<String> domainScores) {
        // domain -> its own score. Tells us in O(1) if an ancestor is in the input.
        Map<String, Long> scoreOf = new HashMap<>();

        // domains that have at least one input domain below them, so they are not leaves
        Set<String> hasChild = new HashSet<>();

        for (String entry : domainScores) {
            int comma = entry.lastIndexOf(',');
            String domain = entry.substring(0, comma);
            long score = Long.parseLong(entry.substring(comma + 1).trim());
            scoreOf.put(domain, score);

            // every ancestor of this domain has a child, so none of them is a leaf
            hasChild.addAll(getAncestors(domain));
        }

        // a leaf is an input domain that never showed up as someone's ancestor
        List<String> leaves = new ArrayList<>();
        for (String domain : scoreOf.keySet()) {
            if (!hasChild.contains(domain)) {
                leaves.add(domain);
            }
        }
        Collections.sort(leaves);

        List<String> result = new ArrayList<>();
        for (String leaf : leaves) {
            long total = scoreOf.get(leaf);
            for (String ancestor : getAncestors(leaf)) {
                // an ancestor missing from the input adds nothing
                total += scoreOf.getOrDefault(ancestor, 0L);
            }
            result.add(leaf + "," + total);
        }
        return result;
    }

    /**
     * Cuts off the first label again and again.
     * "mail.test.com" gives ["test.com", "com"]
     */
    List<String> getAncestors(String domain) {
        List<String> ancestors = new ArrayList<>();
        int dot = domain.indexOf('.');
        while (dot != -1) {
            domain = domain.substring(dot + 1);
            ancestors.add(domain);
            dot = domain.indexOf('.');
        }
        return ancestors;
    }
}
```


### Complexity

Let n be the number of domains, k the number of labels in a domain, and L its length.

- **Time:** O(n · k · L). Each domain is cut up to k times, and each cut copies up to L characters. Sorting adds O(n log n) comparisons.
- **Space:** O(n · k · L) in the worst case, for the ancestor strings in `hasChild`.

Real domains have only a few labels, so this is fast in practice.


## Solution 2: Trie (Optimal)

### What Can Be Better?

Solution 1 has one weak spot: every cut makes a new copy of the rest of the string.

A domain with k labels has k - 1 ancestors, and each one is a new copy of up to L characters. For one very long domain, that is O(L²) work, and all those copies also sit in `hasChild`.

It also walks up the ancestors twice: once to fill `hasChild`, and again for every leaf.

A trie fixes both. It splits each domain into labels just once, and finds every total in a single walk.


### Idea

A trie is the same tree we drew above, built label by label. Since the top level label is at the end of a name, we read labels from right to left.

```text
mail.test.com  ->  com -> test -> mail
```

Two simple facts make the trie work:

- **Leaf check:** every trie node lies on the path to some input domain. So if an input domain's node has no children, nothing is below it, and it is a leaf.
- **Total score:** a node's total is its parent's total plus its own score. A node that is not in the input has score 0, so it adds nothing.

In the tricky case above, the node for `b.com` is just a step on the path with score 0. And `com` still has a child node, so it is correctly not a leaf.

One walk from the root, top to bottom, finds every leaf with its total.


### The TrieNode Class

Each node stores:

- `children`: next label to child node. This is the tree itself.
- `isDomain` and `domain`: whether this domain is in the input, and its full name for the answer.
- `score`: its own score, 0 if it is not in the input.
- `totalScore`: the sum of input scores from the top down to this node. It is filled during the walk.

We walk the trie with a stack instead of recursion, so a domain with thousands of labels can never overflow the call stack.


### Code

```java
import java.util.*;

/**
 * One trie node is one domain, like "test.com".
 * A node can also be just a step on the path, without being in the input.
 */
class TrieNode {
    Map<String, TrieNode> children = new HashMap<>();   // next label -> child node
    boolean isDomain = false;   // true if this domain is in the input
    String domain = "";         // full name, like "mail.test.com"
    long score = 0;             // own score, stays 0 if not in the input
    long totalScore = 0;        // sum of input scores from the top down to this node
}

public class TotalScoresOfLeafDomains {

    public TotalScoresOfLeafDomains() {
    }

    public List<String> getLeafDomainsWithTotalScores(List<String> domainScores) {
        TrieNode root = new TrieNode();
        for (String entry : domainScores) {
            addDomain(root, entry);
        }

        // walk the trie from the top, using a stack instead of recursion
        List<TrieNode> leaves = new ArrayList<>();
        Deque<TrieNode> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            TrieNode node = stack.pop();

            // nothing below this domain, so it is a leaf
            if (node.isDomain && node.children.isEmpty()) {
                leaves.add(node);
            }

            for (TrieNode child : node.children.values()) {
                // child's total = everything above it + its own score
                child.totalScore = node.totalScore + child.score;
                stack.push(child);
            }
        }

        // sort leaves by domain name
        leaves.sort((a, b) -> a.domain.compareTo(b.domain));

        List<String> result = new ArrayList<>();
        for (TrieNode leaf : leaves) {
            result.add(leaf.domain + "," + leaf.totalScore);
        }
        return result;
    }

    /**
     * Stores "mail.test.com" as the path com -> test -> mail.
     */
    void addDomain(TrieNode root, String entry) {
        int comma = entry.lastIndexOf(',');
        String domain = entry.substring(0, comma);
        long score = Long.parseLong(entry.substring(comma + 1).trim());

        String[] labels = domain.split("\\.");
        TrieNode node = root;

        // the top level label is at the end, so read labels from right to left
        for (int i = labels.length - 1; i >= 0; i--) {
            String label = labels[i];
            if (!node.children.containsKey(label)) {
                node.children.put(label, new TrieNode());
            }
            node = node.children.get(label);
        }

        node.isDomain = true;
        node.domain = domain;
        node.score = score;
    }
}
```


### Complexity

Let S be the total number of characters in all domains, and m the number of leaves.

- **Time:** O(S) to build and walk the trie, plus O(m log m) comparisons to sort the leaves.
- **Space:** O(S) for the trie.


## Summary

| Approach | Time | Best for |
|---|---|---|
| Brute force | O(n² · L) | Understanding the problem |
| Hash map + set | O(n · k · L) | Short, real world domains |
| Trie | O(S) + sorting | Any input, even very long domains |