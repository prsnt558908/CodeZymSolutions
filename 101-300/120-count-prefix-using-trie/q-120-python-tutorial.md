# Count Prefix Using Trie in Python

#### Problem Statement
[https://codezym.com/question/120-count-prefix-using-trie](https://codezym.com/question/120-count-prefix-using-trie)

The main idea is to share the letters of common prefixes in a trie. Each node stores how many inserted words pass through it. To answer a query, follow the prefix and return the counter at its last node. Inserting the same word again increases the counters again, so duplicates are counted correctly.

## Start with a simple approach

Store every inserted word in a list, including duplicates. For each query, scan the list and count the words that start with the prefix.

If there are `N` inserted words and the prefix has length `P`, a query takes up to `O(N × P)` time. Repeated queries keep checking the same letters.

A trie shares those letters and saves the answer for each prefix as words are inserted.

## Store a counter at each node

`TrieNode` represents a prefix. Its `children` dictionary connects the next letter to another node, and `prefix_count` stores how many insertions have reached this node. The root represents the empty prefix and is only the starting point.

For example, inserting `"apple"` visits the nodes for `"a"`, `"ap"`, `"app"`, `"appl"`, and `"apple"`. Increase the counter at each of these nodes.

If another word shares part of that path, reuse the existing nodes. Create a new node only when the next letter is missing. A dictionary makes it easy to find the child for a letter without creating unused children.

The `TrieNode` dataclass groups these two fields together. Using `field(default_factory=dict)` gives every node its own child dictionary.

We do not need an end-of-word flag because a prefix query counts both words that end at that node and words that continue beyond it.

## How the operations work

### Insert a word

Start at the root. For each letter, find its child or create one. Move to that child and increase its counter by one.

Always increase the counter, even when the entire path already exists. This is how repeated insertions are counted.

### Count words starting with a prefix

Start at the root and follow the letters of the prefix. If any child is missing, return `0`.

After following every letter, return the last node's counter. There is no need to visit its descendants because their contribution is already included.

## Walk through an example

Insert `"apple"`, then `"app"`, then `"apple"` again.

- After the first insertion, the counter for `"app"` is `1`, and the counter for `"apple"` is `1`.
- Inserting `"app"` makes its counter `2`. The counter for `"apple"` stays `1`.
- Inserting `"apple"` again makes the counters `3` and `2`, respectively.

Now `countWordsStartingWith("app")` returns `3`, and `countWordsStartingWith("apple")` returns `2`. A query for `"applepie"` returns `0` because that path does not exist.

## Why this works

For every non-root node, its counter equals the number of inserted words that start with the prefix represented by that node.

This is true initially because no words have been inserted. Each insertion visits exactly the nodes for its prefixes and increases each counter once. Every other counter stays unchanged, so the rule remains true after every insertion, including duplicates.

A query reaches the node for its prefix and returns this exact count. If the path is missing, no inserted word can have that prefix.

## Complexity

Let `L` be the inserted word's length, `P` be the queried prefix's length, and `S` be the total length of all inserted words, including repeated insertions.

- Insertion takes `O(L)` time and creates at most `L` nodes.
- A prefix query takes `O(P)` time and `O(1)` extra space.
- Total trie space is `O(S)` in the worst case. Shared prefixes and duplicate words reuse nodes.

These time bounds use constant-time dictionary operations. Each node has at most 26 children because inputs contain only lowercase English letters. Both methods use loops, so long strings do not require recursive calls.

## Python code

```python
from dataclasses import dataclass, field


@dataclass
class TrieNode:
    children: dict = field(default_factory=dict)
    prefix_count: int = 0


class Trie:
    def __init__(self):
        self.root = TrieNode()

    def insert(self, word):
        node = self.root

        for letter in word:
            child = node.children.get(letter)

            if child is None:
                child = TrieNode()
                node.children[letter] = child

            node = child
            # Count every insertion, including duplicate words.
            node.prefix_count += 1

    def countWordsStartingWith(self, prefix):
        node = self.root

        for letter in prefix:
            child = node.children.get(letter)

            if child is None:
                return 0

            node = child

        return node.prefix_count
```
