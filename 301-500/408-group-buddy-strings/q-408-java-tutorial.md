# Group Buddy Strings in Java

#### Problem Statement
[https://codezym.com/question/408-group-buddy-strings](https://codezym.com/question/408-group-buddy-strings)

---

Two strings are buddies when they have the same length and the same distance between neighbouring letters at every position. If you know the first letter and all the distances, you can rebuild the whole string. So two buddies can only differ in their first letter. One is just the other moved forward by a few steps on the alphabet, with `z` wrapping back to `a`.


That gives us a neat trick. We slide every string back until it starts at the very beginning of the alphabet, and call the result its **key**. Buddies always get the same key, and strings that are not buddies never do. A hash map from key to group then builds every group in a single pass.


We will first solve it the simple way, comparing strings pair by pair, and see why that gets slow. Then we switch to keys. The same key helper also solves two of the follow-ups.

---

## Understanding the Problem

The distance between two neighbouring letters is counted **forward** on a circular alphabet:

```
'a' -> 'c'  =  2 steps
'y' -> 'a'  =  2 steps   (y -> z -> a)
```

The formula `(str.charAt(i) - str.charAt(i - 1) + 26) % 26` does exactly this. Adding `26` stops the value from going negative before we take `% 26`.


A few more rules shape the output:

- Strings of different lengths are never buddies.
- A single letter has no distances at all. Here, two single letters are buddies only when they are the **same** letter. So `"m"` and `"m"` share a group, but `"m"` and `"n"` do not.
- Groups are ordered by the position of their first member. Inside a group, strings keep their input order, and duplicates are kept.

---

## Solution 1: Compare Strings Pair by Pair

### Step 1: Check one pair

This is exactly the second follow-up, `areBuddyStrings`. Different lengths mean `false`. Single letters must be equal. Otherwise we compare the distance at every index and stop at the first mismatch.

```java
/** Compares the distances directly and stops at the first mismatch. */
public boolean areBuddyStrings(String first, String second) {
    if (first.length() != second.length()) return false;

    // Two single letters are buddies only when they are the same letter
    if (first.length() == 1) return first.equals(second);

    for (int i = 1; i < first.length(); i++) {
        if (distance(first, i) != distance(second, i)) return false;
    }
    return true;
}

/** Cyclic distance from the letter at index i - 1 to the letter at index i. */
int distance(String str, int i) {
    return (str.charAt(i) - str.charAt(i - 1) + 26) % 26;
}
```

### Step 2: Build the groups

We walk through the strings in order. Each string is compared with the **first member** of every group found so far. It joins the first group that matches, or starts a new group.


Why only the first member? Buddies behave like equal values: two buddies of the same string are also buddies of each other. So a buddy of the first member is a buddy of every member.


This loop also gives the required order for free. Groups are created in the order their first member appears, and strings join a group in input order.

```java
public List<List<String>> groupBuddyStrings(List<String> strs) {
    List<List<String>> groups = new ArrayList<>();

    for (String str : strs) {
        List<String> myGroup = null;

        // All members of a group are buddies of each other,
        // so checking the first member of every group is enough
        for (List<String> group : groups) {
            if (areBuddyStrings(group.get(0), str)) {
                myGroup = group;
                break;
            }
        }

        if (myGroup == null) {
            // No buddy found, so str starts a new group
            myGroup = new ArrayList<>();
            groups.add(myGroup);
        }
        myGroup.add(str);
    }
    return groups;
}
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
key[i] = (str.charAt(i) - str.charAt(0) + alphabetSize) % alphabetSize
```

So `xyz` and `yza` both become `[0, 1, 2]`. Each number fits in a `char`, so the key is a `String` with one `char` per letter. We never print a key, so it does not matter that these chars are not normal letters.


Storing numbers instead of letters lets the same helper handle the last follow-up. We just pass `alphabetSize = 256` instead of `26`.

### The single letter rule

Every single letter would slide to the same key `[0]`. That would put `"m"` and `"n"` in one group, which is wrong here.


So a single letter simply uses **itself** as its key. It can never clash with a longer string, because a key always has the same length as its string.

### Why a HashMap and a List?

- **`HashMap<String, List<String>> groupOfKey`** finds the group of a key in `O(1)` on average. A `String` works as a key out of the box, because Java compares and hashes strings by their content.
- **`List<List<String>> groups`** is the answer. A group is added to its end the moment its first member shows up, so the groups stay in the required order.

Both point to the **same** group lists. Adding a string through the map also updates the answer.

### Dry run on Example 1

| String | Key | Action |
|---|---|---|
| `ace` | `[0, 2, 4]` | new group 1 |
| `bdf` | `[0, 2, 4]` | joins group 1 |
| `xyz` | `[0, 1, 2]` | new group 2 |
| `yza` | `[0, 1, 2]` | joins group 2 |
| `aaa` | `[0, 0, 0]` | new group 3 |
| `ccc` | `[0, 0, 0]` | joins group 3 |
| `abb` | `[0, 1, 1]` | new group 4 |

Result: `[[ace, bdf], [xyz, yza], [aaa, ccc], [abb]]`.

---

## The Follow-ups

**`groupSingleCharacterBuddyStrings`:** every string has one letter, so its key is the string itself. The same grouping code puts equal letters together and keeps different letters apart. No new code is needed.


**`areBuddyStrings`:** the pair check from Solution 1. Comparing two keys would also work, but the direct check needs no extra memory and stops at the first mismatch.


**`groupAsciiBuddyStrings`:** the same grouping with `alphabetSize = 256`. In Example 5, `"!#"` (values 33 and 35) and `"@B"` (values 64 and 66) both get the key `[0, 2]`, while `"AA"` and `"zz"` both get `[0, 0]`.

---

## Complexity

Let `n` be the number of strings and `L` the total number of characters.

| | Pair by Pair | Keys + HashMap |
|---|---|---|
| Time | `O(n * g * k)`, up to `O(n^2 * k)` | `O(L)` on average |
| Extra space | `O(1)` besides the answer | `O(L)` for the keys |


The key version reads every character only a few times: to build its key, and when the map hashes and compares that key. On 10,000 strings that all land in different groups, it finishes in a few milliseconds, while the pair by pair version needs well over a second.

---

## Java Code

```java
import java.util.*;

public class GroupBuddyStrings {

    public GroupBuddyStrings() {
    }

    /** Groups lowercase strings on a cyclic alphabet of 26 letters. */
    public List<List<String>> groupBuddyStrings(List<String> strs) {
        return groupByKey(strs, 26);
    }

    /** Every string has one letter, so its key is the string itself. */
    public List<List<String>> groupSingleCharacterBuddyStrings(List<String> strs) {
        return groupByKey(strs, 26);
    }

    /** Compares the distances directly and stops at the first mismatch. */
    public boolean areBuddyStrings(String first, String second) {
        if (first.length() != second.length()) return false;

        // Two single letters are buddies only when they are the same letter
        if (first.length() == 1) return first.equals(second);

        for (int i = 1; i < first.length(); i++) {
            if (distance(first, i) != distance(second, i)) return false;
        }
        return true;
    }

    /** Same grouping as the main method, on a cyclic alphabet of 256 values. */
    public List<List<String>> groupAsciiBuddyStrings(List<String> strs) {
        return groupByKey(strs, 256);
    }

    /**
     * Puts strings with the same key into the same group.
     * Groups are ordered by their first member, and each group
     * keeps its strings in input order, duplicates included.
     */
    List<List<String>> groupByKey(List<String> strs, int alphabetSize) {
        Map<String, List<String>> groupOfKey = new HashMap<>();
        List<List<String>> groups = new ArrayList<>();

        for (String str : strs) {
            String key = buildKey(str, alphabetSize);
            List<String> group = groupOfKey.get(key);

            if (group == null) {
                // First string with this key: open a new group at the end of the answer
                group = new ArrayList<>();
                groupOfKey.put(key, group);
                groups.add(group);
            }
            group.add(str);
        }
        return groups;
    }

    /**
     * Buddies get the same key, and all other strings get different keys.
     * The key always has exactly one char for every char of str.
     */
    String buildKey(String str, int alphabetSize) {
        // A single letter is a buddy only of the very same letter
        if (str.length() == 1) return str;

        // Slide the string back until its first char becomes 0.
        // key[i] = how many steps char i is ahead of the first char.
        char[] key = new char[str.length()];
        for (int i = 0; i < str.length(); i++) {
            key[i] = (char) ((str.charAt(i) - str.charAt(0) + alphabetSize) % alphabetSize);
        }
        return new String(key);
    }

    /** Cyclic distance from the letter at index i - 1 to the letter at index i. */
    int distance(String str, int i) {
        return (str.charAt(i) - str.charAt(i - 1) + 26) % 26;
    }
}
```