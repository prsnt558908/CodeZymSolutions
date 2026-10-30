# Group Buddy Strings in Python

#### Problem Statement
[https://codezym.com/question/408-group-buddy-strings](https://codezym.com/question/408-group-buddy-strings)

---

Two strings are buddies when they have the same length and the same distance between neighbouring letters at every position. If you know the first letter and all the distances, you can rebuild the whole string. So two buddies can only differ in their first letter. One is just the other moved forward by a few steps on the alphabet, with `z` wrapping back to `a`.


That gives us a neat trick. We slide every string back until it starts at the very beginning of the alphabet, and call the result its **key**. Buddies always get the same key, and strings that are not buddies never do. A dictionary from key to group then builds every group in a single pass.


We will first solve it the simple way, comparing strings pair by pair, and see why that gets slow. Then we switch to keys. The same key helper also solves two of the follow-ups.

---

## Understanding the Problem

The distance between two neighbouring letters is counted **forward** on a circular alphabet:

```
'a' -> 'c'  =  2 steps
'y' -> 'a'  =  2 steps   (y -> z -> a)
```

In Python this is `(ord(s[i]) - ord(s[i - 1])) % 26`. Python's `%` never returns a negative number when the right side is positive, so `(97 - 121) % 26` is already `2`. We do not need to add `26` first.


A few more rules shape the output:

- Strings of different lengths are never buddies.
- A single letter has no distances at all. Here, two single letters are buddies only when they are the **same** letter. So `"m"` and `"m"` share a group, but `"m"` and `"n"` do not.
- Groups are ordered by the position of their first member. Inside a group, strings keep their input order, and duplicates are kept.

---

## Solution 1: Compare Strings Pair by Pair

### Step 1: Check one pair

This is exactly the second follow-up, `areBuddyStrings`. Different lengths mean `False`. Single letters must be equal. Otherwise we compare the distance at every index and stop at the first mismatch.

```python
def areBuddyStrings(self, first: str, second: str) -> bool:
    """Compares the distances directly and stops at the first mismatch."""
    if len(first) != len(second):
        return False

    # Two single letters are buddies only when they are the same letter
    if len(first) == 1:
        return first == second

    for i in range(1, len(first)):
        if self.distance(first, i) != self.distance(second, i):
            return False
    return True

def distance(self, s, i):
    """Cyclic distance from the letter at index i - 1 to the letter at index i."""
    return (ord(s[i]) - ord(s[i - 1])) % 26
```

### Step 2: Build the groups

We walk through the strings in order. Each string is compared with the **first member** of every group found so far. It joins the first group that matches, or starts a new group.


Why only the first member? Buddies behave like equal values: two buddies of the same string are also buddies of each other. So a buddy of the first member is a buddy of every member.


This loop also gives the required order for free. Groups are created in the order their first member appears, and strings join a group in input order.

```python
def groupBuddyStrings(self, strs: List[str]) -> List[List[str]]:
    groups = []

    for s in strs:
        my_group = None

        # All members of a group are buddies of each other,
        # so checking the first member of every group is enough
        for group in groups:
            if self.areBuddyStrings(group[0], s):
                my_group = group
                break

        if my_group is None:
            # No buddy found, so s starts a new group
            my_group = []
            groups.append(my_group)
        my_group.append(s)

    return groups
```

### What is wrong with it?

A new string may be compared with **every** group we have so far.


If all `10,000` strings land in different groups, string number `i` is compared with `i` groups. That adds up to about `50 million` pair checks, and each check may walk along the whole string.

**Complexity:** Time `O(n * g * k)`, where `n` is the number of strings, `g` the number of groups and `k` the string length. When every string is in its own group, this becomes `O(n^2 * k)`.

---

## Solution 2: Give Every String a Key (Final Solution)

### The sliding idea

Moving every letter of a string forward by the same number of steps does not change any distance. So we can slide each string back until its first letter is `a`:

- `bdf` slides back 1 step and becomes `ace`.
- `xyz` slides back 23 steps and becomes `abc`.
- `yza` slides back 24 steps and also becomes `abc`.

For strings with two or more letters, buddies always end up as the same string. Strings that are not buddies never do, because the result starts at `a` and then follows the original distances. So different distances or different lengths always give a different result.


This result is the string's **key**. Strings with the same key form one group.

### How the key is built

In code we do not store letters. For every position we store **how many steps that letter is ahead of the first letter**:

```
key[i] = (ord(s[i]) - ord(s[0])) % alphabet_size
```

So `xyz` and `yza` both become the tuple `(0, 1, 2)`. A tuple works as a dictionary key, because it cannot be changed and Python compares tuples by their content.


Storing numbers instead of letters lets the same helper handle the last follow-up. We just pass `alphabet_size = 256` instead of `26`.

### The single letter rule

Every single letter would slide to the same key `(0,)`. That would put `"m"` and `"n"` in one group, which is wrong here.


So a single letter simply uses **itself**, the string, as its key. A string is never equal to a tuple, so it can never clash with the key of a longer string.

### Why a dictionary?

`group_of_key` maps each key to the list of strings in its group. Finding the group of a key takes `O(1)` on average.


A Python dictionary also remembers the order in which its keys were first added. A key is added the moment its group's first member shows up. So `list(group_of_key.values())` returns the groups in exactly the required order, and we do not need a separate list for the answer.

### Dry run on Example 1

| String | Key | Action |
|---|---|---|
| `ace` | `(0, 2, 4)` | new group 1 |
| `bdf` | `(0, 2, 4)` | joins group 1 |
| `xyz` | `(0, 1, 2)` | new group 2 |
| `yza` | `(0, 1, 2)` | joins group 2 |
| `aaa` | `(0, 0, 0)` | new group 3 |
| `ccc` | `(0, 0, 0)` | joins group 3 |
| `abb` | `(0, 1, 1)` | new group 4 |

Result: `[["ace", "bdf"], ["xyz", "yza"], ["aaa", "ccc"], ["abb"]]`.

---

## The Follow-ups

**`groupSingleCharacterBuddyStrings`:** every string has one letter, so its key is the string itself. The same grouping code puts equal letters together and keeps different letters apart. No new code is needed.


**`areBuddyStrings`:** the pair check from Solution 1. Comparing two keys would also work, but the direct check needs no extra memory and stops at the first mismatch.


**`groupAsciiBuddyStrings`:** the same grouping with `alphabet_size = 256`. In Example 5, `"!#"` (values 33 and 35) and `"@B"` (values 64 and 66) both get the key `(0, 2)`, while `"AA"` and `"zz"` both get `(0, 0)`.

---

## Complexity

Let `n` be the number of strings and `L` the total number of characters.

| | Pair by Pair | Keys + Dictionary |
|---|---|---|
| Time | `O(n * g * k)`, up to `O(n^2 * k)` | `O(L)` on average |
| Extra space | `O(1)` besides the answer | `O(L)` for the keys |


The key version reads every character only a few times: to build its key, and when the dictionary hashes and compares that key. On 10,000 strings that all land in different groups, it finishes in about 20 milliseconds, while the pair by pair version needs more than a minute.

---

## Python Code

```python
from typing import List


class GroupBuddyStrings:
    def __init__(self):
        pass

    def groupBuddyStrings(self, strs: List[str]) -> List[List[str]]:
        """Groups lowercase strings on a cyclic alphabet of 26 letters."""
        return self.group_by_key(strs, 26)

    def groupSingleCharacterBuddyStrings(self, strs: List[str]) -> List[List[str]]:
        """Every string has one letter, so its key is the string itself."""
        return self.group_by_key(strs, 26)

    def areBuddyStrings(self, first: str, second: str) -> bool:
        """Compares the distances directly and stops at the first mismatch."""
        if len(first) != len(second):
            return False

        # Two single letters are buddies only when they are the same letter
        if len(first) == 1:
            return first == second

        for i in range(1, len(first)):
            if self.distance(first, i) != self.distance(second, i):
                return False
        return True

    def groupAsciiBuddyStrings(self, strs: List[str]) -> List[List[str]]:
        """Same grouping as the main method, on a cyclic alphabet of 256 values."""
        return self.group_by_key(strs, 256)

    def group_by_key(self, strs, alphabet_size):
        """
        Puts strings with the same key into the same group.
        Groups are ordered by their first member, and each group
        keeps its strings in input order, duplicates included.
        """
        group_of_key = {}
        for s in strs:
            key = self.build_key(s, alphabet_size)
            if key not in group_of_key:
                # First string with this key opens a new group
                group_of_key[key] = []
            group_of_key[key].append(s)

        # A dict remembers the order in which keys were first added,
        # so the groups come out ordered by their first member
        return list(group_of_key.values())

    def build_key(self, s, alphabet_size):
        """Buddies get the same key, and all other strings get different keys."""
        # A single letter is a buddy only of the very same letter
        if len(s) == 1:
            return s

        # Slide the string back until its first char becomes 0.
        # Each number = how many steps that char is ahead of the first char.
        first = ord(s[0])
        return tuple((ord(ch) - first) % alphabet_size for ch in s)

    def distance(self, s, i):
        """Cyclic distance from the letter at index i - 1 to the letter at index i."""
        return (ord(s[i]) - ord(s[i - 1])) % 26
```