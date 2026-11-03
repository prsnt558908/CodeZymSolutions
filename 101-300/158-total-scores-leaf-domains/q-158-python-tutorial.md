# Total Scores of Leaf Domains in Python

#### Problem Statement
[https://codezym.com/question/158-total-scores-leaf-domains](https://codezym.com/question/158-total-scores-leaf-domains)


Domain names form a family tree. `com` is the parent of `test.com`, and `test.com` is the parent of `mail.test.com`.

A leaf is a domain with no input domain below it. Its total score is simply the sum of all scores on its path, from the top down to itself.

The key trick: we never need to search for a parent. Cutting off the first label gives it to us directly.

We will start with a brute force idea, then solve it with a dictionary and a set, and finally improve it with a trie that reads every domain just once.


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

```python
# domains: list of all input domains, score_of: domain -> its score

# x is below y if x ends with "." + y
# the dot matters: "atest.com" is not below "test.com"
def isBelow(x: str, y: str) -> bool:
    return x.endswith("." + y)


for y in domains:
    # y is not a leaf if any domain is below it
    if any(isBelow(x, y) for x in domains):
        continue

    # add y's own score and the score of every ancestor of y
    total = sum(score_of[x] for x in domains if x == y or isBelow(y, x))
    # save y with its total
```

**Why it is slow:** every domain is checked against all n domains. That is O(n² · L) time, where L is the length of a domain. For 100,000 domains, that is about 10 billion checks.

The waste is easy to see. A domain has only a few ancestors, but we scan all n domains to find them.


## Solution 1: Dictionary + Set

### Idea

We do not need to search for ancestors. We can build them by cutting off the first label again and again.

```text
mail.test.com  ->  test.com  ->  com
```

A domain has only a few labels, so it has only a few ancestors. Now each domain looks only at its own ancestors, instead of all n domains.


### Data Structures

**`score_of` (dictionary):** maps a domain to its own score. When we build an ancestor like `test.com`, this dictionary tells us in O(1) if it is in the input, and what its score is.

**`has_child` (set):** every domain that has some input domain below it. While reading `mail.test.com`, we add `test.com` and `com` to it. An input domain that never lands in this set is a leaf.


### Steps

1. For each entry, save its score in `score_of` and add all its ancestors to `has_child`.
2. Leaves are the input domains that are not in `has_child`. Sort them.
3. For each leaf, start with its own score and add the score of every ancestor found in `score_of`. A missing ancestor adds 0.


### Code

```python
from typing import List


class TotalScoresOfLeafDomains:

    def __init__(self):
        pass

    def getLeafDomainsWithTotalScores(self, domainScores: List[str]) -> List[str]:
        # domain -> its own score. Tells us in O(1) if an ancestor is in the input.
        score_of = {}

        # domains that have at least one input domain below them, so they are not leaves
        has_child = set()

        for entry in domainScores:
            domain, score = entry.rsplit(",", 1)
            score_of[domain] = int(score)

            # every ancestor of this domain has a child, so none of them is a leaf
            has_child.update(self.getAncestors(domain))

        # a leaf is an input domain that never showed up as someone's ancestor
        leaves = sorted(domain for domain in score_of if domain not in has_child)

        result = []
        for leaf in leaves:
            total = score_of[leaf]
            for ancestor in self.getAncestors(leaf):
                # an ancestor missing from the input adds nothing
                total += score_of.get(ancestor, 0)
            result.append(leaf + "," + str(total))
        return result

    def getAncestors(self, domain: str) -> List[str]:
        """Cuts off the first label again and again.
        "mail.test.com" gives ["test.com", "com"]
        """
        ancestors = []
        dot = domain.find(".")
        while dot != -1:
            domain = domain[dot + 1:]
            ancestors.append(domain)
            dot = domain.find(".")
        return ancestors
```


### Complexity

Let n be the number of domains, k the number of labels in a domain, and L its length.

- **Time:** O(n · k · L). Each domain is cut up to k times, and each cut copies up to L characters. Sorting adds O(n log n) comparisons.
- **Space:** O(n · k · L) in the worst case, for the ancestor strings in `has_child`.

Real domains have only a few labels, so this is fast in practice.


## Solution 2: Trie (Optimal)

### What Can Be Better?

Solution 1 has one weak spot: every cut makes a new copy of the rest of the string.

A domain with k labels has k - 1 ancestors, and each one is a new copy of up to L characters. For one very long domain, that is O(L²) work, and all those copies also sit in `has_child`.

It also walks up the ancestors twice: once to fill `has_child`, and again for every leaf.

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
- `is_domain` and `domain`: whether this domain is in the input, and its full name for the answer.
- `score`: its own score, 0 if it is not in the input.
- `total_score`: the sum of input scores from the top down to this node. It is filled during the walk.

We walk the trie with a stack instead of recursion, so a domain with thousands of labels never hits the recursion limit.


### Code

```python
from typing import List


class TrieNode:
    """One trie node is one domain, like "test.com".
    A node can also be just a step on the path, without being in the input.
    """

    def __init__(self):
        self.children = {}       # next label -> child node
        self.is_domain = False   # True if this domain is in the input
        self.domain = ""         # full name, like "mail.test.com"
        self.score = 0           # own score, stays 0 if not in the input
        self.total_score = 0     # sum of input scores from the top down to this node


class TotalScoresOfLeafDomains:

    def __init__(self):
        pass

    def getLeafDomainsWithTotalScores(self, domainScores: List[str]) -> List[str]:
        root = TrieNode()
        for entry in domainScores:
            self.addDomain(root, entry)

        # walk the trie from the top, using a stack instead of recursion
        leaves = []
        stack = [root]

        while stack:
            node = stack.pop()

            # nothing below this domain, so it is a leaf
            if node.is_domain and not node.children:
                leaves.append(node)

            for child in node.children.values():
                # child's total = everything above it + its own score
                child.total_score = node.total_score + child.score
                stack.append(child)

        # sort leaves by domain name
        leaves.sort(key=lambda leaf: leaf.domain)

        return [leaf.domain + "," + str(leaf.total_score) for leaf in leaves]

    def addDomain(self, root: TrieNode, entry: str) -> None:
        """Stores "mail.test.com" as the path com -> test -> mail."""
        domain, score = entry.rsplit(",", 1)
        node = root

        # the top level label is at the end, so read labels from right to left
        for label in reversed(domain.split(".")):
            if label not in node.children:
                node.children[label] = TrieNode()
            node = node.children[label]

        node.is_domain = True
        node.domain = domain
        node.score = int(score)
```


### Complexity

Let S be the total number of characters in all domains, and m the number of leaves.

- **Time:** O(S) to build and walk the trie, plus O(m log m) comparisons to sort the leaves.
- **Space:** O(S) for the trie.


## Summary

| Approach | Time | Best for |
|---|---|---|
| Brute force | O(n² · L) | Understanding the problem |
| Dictionary + set | O(n · k · L) | Short, real world domains |
| Trie | O(S) + sorting | Any input, even very long domains |