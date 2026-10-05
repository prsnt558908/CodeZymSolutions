# Design Search Autocomplete System in Python

#### Problem Statement
[https://codezym.com/question/183-design-google-search-autocomplete](https://codezym.com/question/183-design-google-search-autocomplete)


Every time the user types a letter or a space, we have to answer one question: **which 3 saved sentences start with the text typed so far?**


The simple way is to check every saved sentence on every key press. It works, but it repeats a lot of work.


The better way is a **Trie** (prefix tree), where every node stands for one prefix. If every node also keeps its **top 3 sentences** ready, then a key press is just one step down the tree and the answer is already waiting there. When a sentence is finished with `#`, we walk down its path once and refresh those top 3 lists.


We will build the simple version first, see where it is slow, and then improve it step by step.


## Quick Recap of the Rules

- Each typed character is added to the current prefix.
- For a letter or a space, return up to 3 saved sentences that start with the prefix.
- Higher count comes first. For the same count, smaller ASCII order comes first. So `"a b"` comes before `"ab"` (space is smaller than letters), and `"car"` comes before `"cart"` (when one is a prefix of the other, the shorter one comes first).
- For `#`: save the typed sentence with its count increased by 1 (a new sentence gets count 1), clear the prefix and return an empty list. If nothing was typed, nothing is saved.


## Solution 1: Brute Force (Check Every Sentence)

### Idea

Keep every sentence and its count in a dictionary. On every key press:

1. Add the character to the typed text.
2. Collect all sentences that start with the typed text.
3. Sort them: higher count first, then ASCII order.
4. Return the first 3.


On `#`, add 1 to the count of the typed sentence (starting from 0 if it is new) and clear the typed text.

### Data Structures

- **`count_map` (dict)**: sentence to count. It gives the count of any sentence instantly, which we need for sorting and for the `+1` on `#`.
- **`typed` (string)**: the characters typed so far. It is the prefix we search with, and on `#` it is the sentence we save.


The sort key `(-count, sentence)` holds the whole ordering rule. The minus sign puts bigger counts first. For equal counts, Python compares the sentences themselves, character by character using character codes, which is exactly the ASCII order we need.

### Code

```python
class SearchAutocomplete:
    def __init__(self, phrases, counts):
        # sentence -> popularity (how many times it was completed)
        self.count_map = {}
        for i in range(len(phrases)):
            self.count_map[phrases[i]] = counts[i]

        # characters typed so far for the current sentence
        self.typed = ""

    def getSuggestions(self, ch):
        if ch == '#':
            # save the finished sentence (a new sentence starts from 0), then start fresh
            if self.typed:
                self.count_map[self.typed] = self.count_map.get(self.typed, 0) + 1
            self.typed = ""
            return []

        self.typed += ch

        # 1. collect every sentence that starts with the typed prefix
        matches = []
        for sentence in self.count_map:
            if sentence.startswith(self.typed):
                matches.append(sentence)

        # 2. most popular first, ties broken by ASCII order
        matches.sort(key=lambda s: (-self.count_map[s], s))

        # 3. return only the best three
        return matches[:3]
```

### Why Is This Slow?

- **Every key press scans all sentences.** A real search box has millions of them.
- **Every key press sorts all the matches,** even though we only need 3. A single letter like `s` can match a huge part of the history.
- **Work is repeated.** Sentences matching `se` are always a subset of the ones matching `s`, yet we start from zero on every key press.


## Solution 2: Trie with Top 3 Stored at Every Node (Optimal)

We fix these problems in three small steps.

### Step 1: Use a Trie to stop scanning everything

A Trie stores sentences character by character. Every node stands for one prefix and every child is the next character. Sentences that share a prefix share the same nodes.


Here is the Trie for Example 4 (`cat = 3`, `car = 2`, `cart = 2`). The lists on the right are added in Step 2.

```text
root
`-- c                 top 3: cat, car, cart
    `-- a             top 3: cat, car, cart
        |-- t         top 3: cat
        `-- r         top 3: car, cart
            `-- t     top 3: cart
```

Typing a character now just means "move to that child". We keep the node of the typed prefix in a variable `current`, so each key press continues from where the last one stopped.


If the child does not exist, no saved sentence starts with this prefix, and typing more characters can never create a match. So we set `current` to `None` and return empty lists until `#`.

### Step 2: Store the top 3 inside every node

A Trie alone is not enough. To answer the prefix `s`, we would still have to visit every sentence below node `s` and sort them.


So every node keeps its answer ready in a small list, `top_sentences`, which holds the best 3 sentences for its prefix. A key press now just returns a copy of that list. No scanning and no sorting.


This is a good trade because key presses (reads) happen far more often than finished sentences (writes).

### Step 3: Keep the top 3 lists correct

When a sentence is added or its count grows, only the nodes on **its own path** can change. So we walk down its path and at every node:

1. Remove the sentence from the list if it is already there.
2. Add it.
3. Sort the list (it has at most 4 items).
4. If there are 4 items, drop the last one.


The constructor uses the same method to add each initial phrase with its starting count.


For example, the user types `c`, `a`, `r`, `#`. Now `car` has count 3 and we refresh the nodes on its path:

| Node (prefix) | Top 3 before | Top 3 after |
|---|---|---|
| `c` | cat, car, cart | **car**, cat, cart |
| `ca` | cat, car, cart | **car**, cat, cart |
| `car` | car, cart | car, cart |
| `cat` | cat | not on the path, unchanged |
| `cart` | cart | not on the path, unchanged |

`car` and `cat` both have count 3 now, so ASCII order puts `car` first.


**Why is it safe to remember only 3 sentences per node?** Counts only go up and sentences are never removed. When one sentence becomes more popular, all others keep their counts. So any sentence outside a node's top 3 still has 3 better sentences ahead of it and can never sneak in. The new top 3 always comes from the old top 3 plus the updated sentence, and that is exactly the list we sort.

### Important Classes and Data Structures

- **`TrieNode`**: one prefix. `children` is a dict from the next character to the child node. It keeps things simple and treats letters and the space the same way. `top_sentences` is the ready made answer for that prefix.
- **`count_map`**: sentence to count. Nodes only store sentence strings, so sorting looks up the counts here. It also gives the old count when a sentence is typed again.
- **`current`**: the node of the typed prefix. Each key press moves it one step down. `None` means nothing matches anymore.
- **`typed`**: the typed characters. We still need them after falling off the Trie, because `#` must save the sentence even if it had no suggestions. This time it is a list, not a string: appending to a list is cheap, while `+=` on a string builds a new string every time. We join the list into a sentence only on `#`.

### Code

```python
class TrieNode:
    """One node of the Trie. Every node stands for one prefix.
    Example: the path c -> a -> r ends at the node for "car"."""

    def __init__(self):
        # next character -> child node
        self.children = {}

        # up to 3 best sentences that start with this node's prefix, best first
        self.top_sentences = []


class SearchAutocomplete:
    def __init__(self, phrases, counts):
        # sentence -> popularity (how many times it was completed)
        self.count_map = {}
        self.root = TrieNode()

        # characters typed so far for the current sentence
        self.typed = []

        # trie node of the typed prefix, None once no sentence matches it
        self.current = self.root

        for i in range(len(phrases)):
            self.add_sentence(phrases[i], counts[i])

    def getSuggestions(self, ch):
        if ch == '#':
            # save the finished sentence, then start fresh
            if self.typed:
                self.add_sentence("".join(self.typed), 1)
            self.typed = []
            self.current = self.root
            return []

        self.typed.append(ch)

        # move one step down. Once we fall off the trie, we stay off until '#'
        if self.current is not None:
            self.current = self.current.children.get(ch)
        if self.current is None:
            return []

        # the answer is already waiting at this node, return a copy of it
        return list(self.current.top_sentences)

    def add_sentence(self, sentence, times):
        """Adds 'times' to the popularity of a sentence (a new sentence starts from 0)
        and refreshes the top 3 list of every node on its path."""
        self.count_map[sentence] = self.count_map.get(sentence, 0) + times

        node = self.root
        for c in sentence:
            if c not in node.children:
                node.children[c] = TrieNode()
            node = node.children[c]
            self.update_top_sentences(node, sentence)

    def update_top_sentences(self, node, sentence):
        """'sentence' just became more popular, so it is the only one that can
        enter this node's top 3. Remove it if present, add it back, sort, keep 3."""
        top = node.top_sentences
        if sentence in top:
            top.remove(sentence)
        top.append(sentence)
        top.sort(key=lambda s: (-self.count_map[s], s))
        if len(top) > 3:
            top.pop()   # drop the 4th one
```

## Complexity

`N` = number of saved sentences, `L` = maximum sentence length.

| Operation | Brute Force | Trie with Top 3 |
|---|---|---|
| Type a letter or space | O(N × L × log N) | O(1) |
| Type `#` | O(L) | O(L) node updates, each one sorts at most 4 sentences |
| Constructor | O(N × L) | O(N × L) node updates |
| Extra space | O(N × L) | O(N × L) |


With at most 120 sentences, both solutions are fast enough here. But only the Trie version stays fast when the history grows to millions of sentences, because the cost of a key press does not depend on how many sentences are saved.