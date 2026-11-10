# Shortest Word Distance Between Words in Python

#### Problem Statement
[https://codezym.com/question/204-shortest-word-distance-between-words](https://codezym.com/question/204-shortest-word-distance-between-words)


The word list is given only once and it never changes, but `shortest()` can be called many, many times. So the core idea is simple: do the heavy work once in the constructor, and keep every query cheap.

In the constructor, we read the list one time and save every index where each word appears. A query then looks only at the indices of its two words, and walks through them with two pointers to find the closest pair.

We also remember every answer we find, so asking the same pair again is instant.

We will start with a simple brute force, see why it is slow, and then improve it step by step.


## Approach 1: Brute Force (Scan the Whole List)

The constructor just saves the list in `self.words`.

For every query, we walk through the whole list from left to right. We keep the last index where we saw `word1` and the last index where we saw `word2`.

Once both words have been seen, we check the distance between their last seen indices at every step, and keep the smallest one.

When we reach a word, the last seen copy of the other word is its closest copy on the left. So we never miss the best pair.

```python
def shortest(self, word1: str, word2: str) -> int:
    last1, last2 = -1, -1
    best = float("inf")

    for i, word in enumerate(self.words):
        if word == word1:
            last1 = i
        elif word == word2:
            last2 = i

        # both words seen at least once, check their distance
        if last1 != -1 and last2 != -1:
            best = min(best, abs(last1 - last2))

    return best
```

### Why is this slow?

Every query reads all `n` words, even when `word1` and `word2` appear only a few times.

The list never changes, so we keep doing the same work again and again. If the list has 100,000 words and we get 100,000 queries, that is about 10 billion steps.


## Approach 2: Save Word Positions + Two Pointers

### Step 1: Save where every word appears

A query only cares about two words. All the other words in the list are just noise for it.

So in the constructor, we read the list once and build a dictionary from each word to the list of indices where it appears.

```
words = ["alpha", "beta", "gamma", "alpha", "delta", "beta"]

alpha -> [0, 3]
beta  -> [1, 5]
gamma -> [2]
delta -> [4]
```

**Why a dictionary of lists?**

- The dictionary gives us the indices of any word instantly, without scanning the list.
- We add indices while reading from left to right, so every list is already sorted. We get sorted lists for free, and the next step needs them.


### Step 2: Find the closest pair with two pointers

Now a query works on two sorted lists. For `shortest("alpha", "delta")`, they are `[0, 3]` and `[4]`.

We could compare every index in one list with every index in the other. If `word1` appears `a` times and `word2` appears `b` times, that is `a * b` comparisons, which is huge when both words are common.

Since both lists are sorted, two pointers can do it in one pass. Pointer `i` walks `list1` (indices of `word1`) and pointer `j` walks `list2` (indices of `word2`):

1. Find the distance between `list1[i]` and `list2[j]`, and keep the smallest one seen so far.
2. Move forward the pointer that points to the **smaller** index.
3. Stop when either list ends.

**Why move the smaller one?** Say `list1[i]` is smaller. Every value after `list2[j]` is even bigger, so it is even farther from `list1[i]`. So `list1[i]` cannot give a better answer anymore, and we move past it.

Here is a quick run with `list1 = [1, 8]` and `list2 = [4, 10]`:

| `list1[i]` | `list2[j]` | Distance | Best so far | Next move |
|:---:|:---:|:---:|:---:|:---|
| 1 | 4 | 3 | 3 | move `i`, 1 is smaller |
| 8 | 4 | 4 | 3 | move `j`, 4 is smaller |
| 8 | 10 | 2 | 2 | move `i`, `list1` ends, so stop |

The answer is `2`. Each step moves one pointer forward, so we do at most `a + b` steps.


### Step 3: Remember answers we already found

The same pair can be asked many times. There is no need to walk the lists again.

So we keep a second dictionary, `cache`, from a pair of words to its answer.

`shortest("a", "b")` and `shortest("b", "a")` have the same answer, so both should use the same key. We always put the alphabetically smaller word first:

```
shortest("delta", "alpha")  ->  key = "alpha delta"
shortest("alpha", "delta")  ->  key = "alpha delta"
```

Words only have lowercase letters, so the space in the middle can never mix up two different pairs.


### Complexity

Here `n` is the number of words, and `a`, `b` are the counts of the two query words.

| | Brute Force | Optimized |
|:---|:---|:---|
| Constructor | `O(1)` | `O(n)` |
| `shortest()` | `O(n)` | `O(a + b)`, and `O(1)` for a repeated pair |
| Extra space | `O(1)` | `O(n)` for positions, plus one cache entry per pair asked |


### Python Code

```python
from typing import List


class ShortestWordDistance:

    def __init__(self, words: List[str]):
        # word -> all indices where it appears, always in increasing order
        self.positions = {}

        # "word1 word2" -> answer we already found
        self.cache = {}

        # read the list once and save the index of every word
        for i, word in enumerate(words):
            if word not in self.positions:
                self.positions[word] = []
            self.positions[word].append(i)

    def shortest(self, word1: str, word2: str) -> int:
        """Return the smallest distance between word1 and word2."""
        key = self.makeKey(word1, word2)
        if key in self.cache:
            return self.cache[key]

        list1 = self.positions[word1]
        list2 = self.positions[word2]

        i, j = 0, 0
        best = float("inf")

        # two pointers: walk both sorted lists together
        while i < len(list1) and j < len(list2):
            index1, index2 = list1[i], list2[j]
            best = min(best, abs(index1 - index2))

            # the other list only has bigger values ahead, so the
            # smaller index cannot get any closer. Move past it.
            if index1 < index2:
                i += 1
            else:
                j += 1

        self.cache[key] = best
        return best

    def makeKey(self, word1: str, word2: str) -> str:
        """(a, b) and (b, a) must give the same key."""
        if word1 < word2:
            return word1 + " " + word2
        return word2 + " " + word1
```