# Generate Palindromic Permutations Of String in Python

#### Problem Statement
[https://codezym.com/question/203-generate-palindromic-permutations](https://codezym.com/question/203-generate-palindromic-permutations)


## Core Idea

A palindrome is like a mirror. Its right half is just its left half reversed. So we never need to arrange the whole string. We only build the left half and mirror it.


First we count every letter. A palindrome is possible only when at most one letter has an odd count, and that letter goes in the middle.


Then we use backtracking to build every left half from the letter counts. Because we pick letters from their counts, the same half is never built twice. Because we try letters from `'a'` to `'z'`, the answer comes out already sorted.


## Solution 1: Brute Force (Try Every Arrangement)

The simplest idea is to build every arrangement of `s` and keep the ones that read the same both ways. A set removes duplicates, and at the end we sort.


```python
# Build every arrangement by picking an unused position each time
def permute(self, s, used, current, found):
    if len(current) == len(s):
        candidate = "".join(current)
        if candidate == candidate[::-1]:
            found.add(candidate)  # the set throws away duplicates
        return
    for i in range(len(s)):
        if used[i]:
            continue
        used[i] = True
        current.append(s[i])
        self.permute(s, used, current, found)
        current.pop()  # undo
        used[i] = False


# At the end: sort the set into a list
result = sorted(found)
```


### Problems With This Approach

1. **Way too many arrangements.** A string of length 16 has `16!` arrangements, which is about 21 trillion. This will never finish in time.

2. **Duplicates.** In `"aabb"`, positions 0 and 1 both hold `'a'`. Picking position 0 first or position 1 first builds the same strings, so every result is built again and again. We waste time on the copies and need a set to throw them away.

3. **Wasted work.** Most arrangements are not palindromes. Even for `"abc"`, where no palindrome exists, we still try all 6 arrangements.


Let's fix these one by one.


## Solution 2: Build Only the Left Half (Optimal)

### Step 1: Can a Palindrome Be Formed?

In a palindrome, every letter on the left has a matching copy on the right. So letters come in pairs, and every count must be even.


The only exception is the middle spot of an odd length string. One letter can sit there alone.


So **if more than one letter has an odd count, we return an empty list right away.** For `"abc"`, all three counts are odd, so the answer is `[]` and we do no other work.


### Step 2: The Left Half Decides Everything

Once the left half is known, the whole palindrome is fixed:

```
palindrome = left + middle + reverse(left)
```

Each letter puts **half of its count** (rounded down) into the left half. The odd letter (if any) also gives one copy to the middle.


For `s = "aabbc"`:

```
counts       : a = 2, b = 2, c = 1
left letters : a, b
middle       : "c"

left = "ab"  ->  "ab" + "c" + "ba"  =  "abcba"
left = "ba"  ->  "ba" + "c" + "ab"  =  "bacab"
```

For a string of length 16 we now arrange only 8 letters. That is at most `8! = 40,320` options instead of 21 trillion. And every left half gives a valid palindrome, so nothing is wasted.


### Step 3: Pick Letters From Counts, Not Positions

Brute force picked **positions**, so two equal letters made the same string twice.


Now we pick **letters**. At each position we loop over the 26 letters and take one only if it still has copies left. All copies of `'a'` share one counter, so `'a'` is tried only once per position. Every left half is built exactly once, and we don't need a set.


This is backtracking: **choose** a letter, **explore** the next position, then **undo** the choice and try the next letter.


Here is the full tree for `s = "aaaabb"`, where the left half must use the letters `a, a, b`:

```
half built so far       letters left
""                      a:2  b:1
|-- "a"                 a:1  b:1
|   |-- "aa"            a:0  b:1
|   |   |-- "aab"       done
|   |-- "ab"            a:1  b:0
|       |-- "aba"       done
|-- "b"                 a:2  b:0
    |-- "ba"            a:1  b:0
        |-- "baa"       done
```

Only 3 halves are built. Picking positions would build 6, with each half repeated twice.


### Step 4: Sorted Order for Free

Look at the tree again. The halves came out as `aab`, `aba`, `baa`, already sorted. That is because at every position we try `'a'` first, then `'b'`, and so on.


All palindromes have the same length and the same middle, and each one starts with its left half. So a smaller left half always means a smaller palindrome. Here they become `aabbaa`, `abaaba`, `baaaab`, still sorted.


So the result list is already in sorted (lexicographical) order. No set and no sorting needed.


### Key Data Structures

- **`count` and `self.half_count` (lists of size 26):** index `0` is `'a'` and index `25` is `'z'`. This is simpler than a dictionary, and looping from 0 to 25 visits letters in alphabetical order, which is what keeps the output sorted.

- **`half` (a list of characters):** holds the left half while we build it. Python strings can't be changed in place, but a list can `append` and `pop` the last letter cheaply, which is exactly what backtracking does.

- **Fields `self.half_count`, `self.half_length`, `self.middle` and `self.result`:** the recursive method needs them at every level, so keeping them on the object keeps its signature short. They are reset at the start of every `generatePalindromes` call.


### Final Code

```python
from typing import List


class PalindromicPermutations:

    def __init__(self):
        self.half_count = []   # letters still free for the left half (index 0 = 'a')
        self.half_length = 0   # length of the left half
        self.middle = ""       # middle letter when the length is odd, "" otherwise
        self.result = []       # palindromes found so far, already in sorted order

    def generatePalindromes(self, s: str) -> List[str]:
        self.result = []

        # Count how many times each letter appears
        count = [0] * 26
        for c in s:
            count[ord(c) - ord('a')] += 1

        # A palindrome allows at most one letter with an odd count.
        # That letter (if any) goes in the middle.
        self.middle = ""
        odd_letters = 0
        for i in range(26):
            if count[i] % 2 == 1:
                odd_letters += 1
                self.middle = chr(ord('a') + i)
        if odd_letters > 1:
            return self.result

        # Each letter gives half of its copies to the left half
        self.half_count = [value // 2 for value in count]
        self.half_length = len(s) // 2

        self.buildHalf([])
        return self.result

    def buildHalf(self, half: List[str]) -> None:
        """
        Backtracking: fills the left half one letter at a time.
        Letters are tried from 'a' to 'z', so the halves (and the
        palindromes) are created in sorted order without duplicates.
        """
        if len(half) == self.half_length:
            left = "".join(half)
            self.result.append(left + self.middle + left[::-1])
            return

        for i in range(26):
            if self.half_count[i] == 0:
                continue  # no copies of this letter left

            # choose
            self.half_count[i] -= 1
            half.append(chr(ord('a') + i))

            # explore the next position
            self.buildHalf(half)

            # undo, so the next letter can be tried here
            half.pop()
            self.half_count[i] += 1
```


### Complexity

Let `n` be the length of `s`.

- **Time:** `O(n · (n/2)!)`. There are at most `(n/2)!` left halves, and building each palindrome takes `O(n)`. For `n = 16` that is at most 40,320 palindromes.

- **Space:** `O(n)` extra, not counting the output list. The recursion goes `n/2` levels deep, and the count lists always have 26 slots.